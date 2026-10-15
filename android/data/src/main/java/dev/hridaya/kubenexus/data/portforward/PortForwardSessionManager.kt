package dev.hridaya.kubenexus.data.portforward

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.di.ApplicationScope
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide manager and single source of truth for all active port-forward sessions.
 *
 * Coordinates between the native bridge / repository and presentation layer,
 * keeping track of Pod and Service forward lifecycles and notifying subscribers
 * of state transitions (ready, error, stopped).
 */
@Singleton
class PortForwardSessionManager @Inject constructor(
    private val repository: PortForwardRepository,
    @param:ApplicationScope private val externalScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context? = null,
    private val notificationManager: PortForwardNotificationManager? = null,
) {
    private val _sessions = MutableStateFlow<List<ActivePortForwardSession>>(emptyList())
    val sessions: StateFlow<List<ActivePortForwardSession>> = _sessions.asStateFlow()

    /** Forwards still dialing. The foreground service stays up for them too. */
    private val pendingStarts = MutableStateFlow(0)

    /**
     * True while any tunnel is open or being opened. The foreground service keeps the
     * process alive exactly as long as this holds.
     */
    val needsForegroundService: StateFlow<Boolean> =
        combine(
            _sessions,
            pendingStarts
        ) { list, pending -> pending > 0 || list.any { it.isActive } }
            .distinctUntilChanged()
            .stateIn(externalScope, SharingStarted.Eagerly, false)

    /**
     * Guards [_sessions] together with [earlyUpdates], so an event that races ahead of
     * its session being recorded is never lost.
     */
    private val lock = Any()

    /**
     * Status changes for handles not recorded yet. Go reports a forward's handle only when
     * StartPortForward returns, so a tunnel that dies immediately can report its error or
     * stop before [addSession] runs; those are applied when the session is added.
     */
    private val earlyUpdates =
        mutableMapOf<String, (ActivePortForwardSession) -> ActivePortForwardSession>()

    init {
        externalScope.launch(dispatcherProvider.io) {
            needsForegroundService.collect { needed ->
                if (!needed) notificationManager?.dismissNotification()
            }
        }
    }

    private val internalListener = object : PortForwardListener {
        override fun onPortForwardReady(handleId: String, localPort: Int) {
            updateSession(handleId, recordIfUnknown = true) {
                if (it.isActive) it.copy(status = PortForwardSessionStatus.READY) else it
            }
        }

        override fun onPortForwardError(handleId: String, message: String) {
            updateSession(handleId, recordIfUnknown = true) {
                it.copy(
                    status = PortForwardSessionStatus.ERROR,
                    message = message,
                )
            }
        }

        override fun onPortForwardStopped(handleId: String, reason: String) {
            updateSession(handleId, recordIfUnknown = true) {
                it.copy(status = PortForwardSessionStatus.STOPPED)
            }
        }
    }

    suspend fun startPodForward(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        clusterId: String? = null,
    ): Result<String> = startForward(
        rawKubeconfig = rawKubeconfig,
        namespace = namespace,
        podName = podName,
        localPort = localPort,
        remotePort = remotePort,
    ) { handleId ->
        ActivePortForwardSession(
            handleId = handleId,
            clusterId = clusterId,
            kind = PortForwardTargetKind.Pod,
            namespace = namespace,
            targetName = podName,
            podName = podName,
            localPort = localPort,
            remotePort = remotePort,
            status = PortForwardSessionStatus.READY,
        )
    }

    suspend fun startServiceForward(
        rawKubeconfig: String,
        namespace: String,
        serviceName: String,
        localPort: Int,
        servicePort: Int,
        targetPodName: String,
        targetPodPort: Int,
        clusterId: String? = null,
    ): Result<String> = startForward(
        rawKubeconfig = rawKubeconfig,
        namespace = namespace,
        podName = targetPodName,
        localPort = localPort,
        remotePort = targetPodPort,
    ) { handleId ->
        ActivePortForwardSession(
            handleId = handleId,
            clusterId = clusterId,
            kind = PortForwardTargetKind.Service,
            namespace = namespace,
            targetName = serviceName,
            podName = targetPodName,
            localPort = localPort,
            remotePort = servicePort,
            status = PortForwardSessionStatus.READY,
        )
    }

    /**
     * Opens a tunnel and records it. The foreground service is requested before dialing,
     * while the user's tap still has the app in the foreground: once the dial has taken
     * a while the app may be in the background, where Android refuses to start one.
     *
     * The native call returns a handle only once the local listener is accepting
     * connections, so a recorded session starts out READY.
     */
    private suspend fun startForward(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        session: (handleId: String) -> ActivePortForwardSession,
    ): Result<String> {
        pendingStarts.update { it + 1 }
        try {
            requestForegroundService()
            val result = withContext(dispatcherProvider.io) {
                repository.start(
                    rawKubeconfig = rawKubeconfig,
                    namespace = namespace,
                    podName = podName,
                    localPort = localPort,
                    remotePort = remotePort,
                    listener = internalListener,
                )
            }
            if (result is Result.Success) {
                addSession(session(result.data))
            }
            return result
        } finally {
            synchronized(lock) {
                pendingStarts.update { it - 1 }
                if (pendingStarts.value == 0) earlyUpdates.clear()
            }
        }
    }

    private fun requestForegroundService() {
        val ctx = context ?: return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PortForwardService::class.java))
        } catch (e: Exception) {
            // IllegalStateException / ForegroundServiceStartNotAllowedException: the tunnel
            // still works while the app is visible, it just will not survive backgrounding.
            Log.w(TAG, LogSanitizer.withStackTrace("Could not start the port-forward service", e))
        }
    }

    suspend fun stop(handleId: String): Result<Unit> {
        val result = withContext(dispatcherProvider.io) { repository.stop(handleId) }
        updateSession(handleId) { it.copy(status = PortForwardSessionStatus.STOPPED) }
        return result
    }

    fun stopAll() {
        val activeHandles = _sessions.value.filter { it.isActive }.map { it.handleId }
        // Mark them stopped right away, so the foreground service can stop without waiting
        // for each native teardown.
        activeHandles.forEach { handleId ->
            updateSession(handleId) { it.copy(status = PortForwardSessionStatus.STOPPED) }
        }
        externalScope.launch(dispatcherProvider.io) {
            activeHandles.forEach { handleId ->
                launch { repository.stop(handleId) }
            }
        }
    }

    private fun addSession(session: ActivePortForwardSession) {
        synchronized(lock) {
            val recorded = earlyUpdates.remove(session.handleId)?.invoke(session) ?: session
            _sessions.update { list ->
                val index = list.indexOfFirst { it.handleId == recorded.handleId }
                    .takeIf { it >= 0 }
                    ?: list.indexOfFirst { it.localPort == recorded.localPort && it.isActive }
                if (index < 0) {
                    list + recorded
                } else {
                    list.toMutableList().apply { this[index] = recorded }
                }
            }
        }
    }

    private fun updateSession(
        handleId: String,
        recordIfUnknown: Boolean = false,
        transform: (ActivePortForwardSession) -> ActivePortForwardSession,
    ) {
        synchronized(lock) {
            val list = _sessions.value
            val index = list.indexOfFirst { it.handleId == handleId }
            if (index >= 0) {
                _sessions.value =
                    list.toMutableList().apply { this[index] = transform(this[index]) }
            } else if (recordIfUnknown && pendingStarts.value > 0) {
                val previous = earlyUpdates[handleId]
                earlyUpdates[handleId] =
                    if (previous == null) transform else { s -> transform(previous(s)) }
            }
        }
    }

    private companion object {
        const val TAG = "$APP_LOG_TAG_PREFIX/PortForwardSessionManager"
    }
}

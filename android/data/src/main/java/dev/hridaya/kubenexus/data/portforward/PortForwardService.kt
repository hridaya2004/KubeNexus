package dev.hridaya.kubenexus.data.portforward

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.security.LogSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps Kubernetes port-forward TCP tunnels and native Go
 * worker threads alive when the app transitions into the background.
 *
 * It uses the dataSync type, which Android 15+ limits to 6 hours in every 24. When that
 * budget runs out the system calls [onTimeout], and the service must stop within seconds
 * or the app is crashed, so it closes the tunnels and tells the user why.
 */
class PortForwardService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PortForwardServiceEntryPoint {
        fun sessionManager(): PortForwardSessionManager
        fun notificationManager(): PortForwardNotificationManager
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var lastStartId = 0

    private lateinit var sessionManager: PortForwardSessionManager
    private lateinit var notificationManager: PortForwardNotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PortForwardServiceEntryPoint::class.java,
        )
        sessionManager = entryPoint.sessionManager()
        notificationManager = entryPoint.notificationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Every startForegroundService() must be answered with startForeground(), not only
        // the first one, or the system raises "did not then call startForeground()".
        promoteToForeground()
        // Observed from here rather than onCreate so the start id is known when the
        // service decides to stop itself.
        if (observeJob == null) {
            observeJob = serviceScope.launch {
                combine(
                    sessionManager.sessions,
                    sessionManager.needsForegroundService
                ) { sessions, needed ->
                    sessions.filter { it.isActive } to needed
                }.collect { (activeSessions, needed) ->
                    if (needed) {
                        notificationManager.updateNotification(activeSessions)
                    } else {
                        stop()
                    }
                }
            }
        }
        // After process death the Go tunnels are gone, so there is nothing to restart.
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Port forwarding reached Android's daily limit for background data sync")
        sessionManager.stopAll()
        notificationManager.showTimeLimitReached()
        stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
        serviceScope.cancel()
    }

    private fun promoteToForeground() {
        val activeSessions = sessionManager.sessions.value.filter { it.isActive }
        try {
            ServiceCompat.startForeground(
                this,
                PortForwardNotificationManager.NOTIFICATION_ID,
                notificationManager.buildNotification(activeSessions),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException, for example once the daily dataSync
            // budget is spent. Tunnels keep working while the app is visible.
            Log.w(TAG, LogSanitizer.withStackTrace("Could not promote the port-forward service", e))
            stop()
        }
    }

    /** Stops unless a newer start request has arrived in the meantime. */
    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private companion object {
        const val TAG = "$APP_LOG_TAG_PREFIX/PortForwardService"
    }
}

package dev.hridaya.kubenexus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.domain.model.PodStatus

/**
 * Semantic status colors for Kubernetes workloads and cluster connection states.
 */
@Immutable
data class StatusColors(
    val connected: Color,
    val connecting: Color,
    val disconnected: Color,
    val offline: Color,
    val running: Color,
    val pending: Color,
    val completed: Color,
    val failed: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
) {
    fun forClusterStatus(status: ClusterStatus): Color = when (status) {
        ClusterStatus.CONNECTED -> connected
        ClusterStatus.ERROR -> failed
        ClusterStatus.DISCONNECTED -> disconnected
    }

    fun forConnectionStatus(status: ClusterConnectionStatus): Color = when (status) {
        ClusterConnectionStatus.CONNECTED -> connected
        ClusterConnectionStatus.CONNECTING -> connecting
        ClusterConnectionStatus.DISCONNECTED -> disconnected
        ClusterConnectionStatus.OFFLINE -> offline
    }

    fun forPodStatus(status: PodStatus): Color = when (status) {
        PodStatus.RUNNING -> running
        PodStatus.PENDING -> pending
        PodStatus.COMPLETED -> completed
        PodStatus.FAILED, PodStatus.CRASH_LOOP -> failed
        PodStatus.UNKNOWN -> offline
    }
}

val LightStatusColors = StatusColors(
    connected = Color(0xFF16A34A),
    connecting = Color(0xFFD97706),
    disconnected = Color(0xFFDC2626),
    offline = Color(0xFF64748B),
    running = Color(0xFF16A34A),
    pending = Color(0xFFD97706),
    completed = Color(0xFF2563EB),
    failed = Color(0xFFDC2626),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
)

val DarkStatusColors = StatusColors(
    connected = Color(0xFF4ADE80),
    connecting = Color(0xFFFBBF24),
    disconnected = Color(0xFFF87171),
    offline = Color(0xFF94A3B8),
    running = Color(0xFF4ADE80),
    pending = Color(0xFFFBBF24),
    completed = Color(0xFF60A5FA),
    failed = Color(0xFFF87171),
    successContainer = Color(0xFF14532D),
    onSuccessContainer = Color(0xFFDCFCE7),
    warningContainer = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFEF3C7),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

val statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

/**
 * Semantic log-level colors for the logcat viewer.
 */
@Immutable
data class LogLevelColors(
    val verbose: Color,
    val debug: Color,
    val info: Color,
    val warn: Color,
    val error: Color,
    val fatal: Color,
) {
    fun forLevel(level: LogLevel): Color = when (level) {
        LogLevel.VERBOSE -> verbose
        LogLevel.DEBUG -> debug
        LogLevel.INFO -> info
        LogLevel.WARN -> warn
        LogLevel.ERROR -> error
        LogLevel.FATAL -> fatal
        LogLevel.UNKNOWN -> verbose
    }
}

val LightLogLevelColors = LogLevelColors(
    verbose = Color(0xFF5F6368),
    debug = Color(0xFF0B57D0),
    info = Color(0xFF137333),
    warn = Color(0xFF92400E),
    error = Color(0xFFBA1A1A),
    fatal = Color(0xFF6B21A8),
)

val DarkLogLevelColors = LogLevelColors(
    verbose = Color(0xFF9AA0A6),
    debug = Color(0xFF8AB4F8),
    info = Color(0xFF81C995),
    warn = Color(0xFFFDD663),
    error = Color(0xFFFFB4AB),
    fatal = Color(0xFFD8B4FE),
)

val LocalLogLevelColors = staticCompositionLocalOf { LightLogLevelColors }

val logColors: LogLevelColors
    @Composable
    @ReadOnlyComposable
    get() = LocalLogLevelColors.current

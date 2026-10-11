package dev.hridaya.kubenexus.domain.model

data class ThemeSettings(
    val mode: ThemeMode,
    /** Pure black backgrounds in dark mode. */
    val amoledDark: Boolean,
)

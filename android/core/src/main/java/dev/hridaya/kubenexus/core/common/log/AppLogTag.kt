package dev.hridaya.kubenexus.core.common.log

/**
 * Every log tag KubeNexus writes starts with this, as in `KubeNexus/HomeViewModel`, so the in-app
 * Logcat can show the app's own logs and leave out what the framework logs from inside the app.
 */
const val APP_LOG_TAG_PREFIX = "KubeNexus"

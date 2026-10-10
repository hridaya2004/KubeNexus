package dev.hridaya.kubenexus.presentation.navigation

/**
 * A screen opened over the top-level destinations.
 *
 * Resource screens name their kind by [dev.hridaya.kubenexus.domain.model.resource.ResourceType.key],
 * so one entry type serves every kind and a new kind needs no new navigation state.
 */
sealed interface Screen {

    data class ResourceList(val typeKey: String) : Screen

    data class ResourceDetail(val typeKey: String, val namespace: String, val name: String) : Screen

    data class CreateResource(val typeKey: String) : Screen

    data object ManageClusters : Screen

    data object Logcat : Screen

    data object OpenSourceLicenses : Screen

    /**
     * Identifies the ViewModel store this screen's ViewModels live in. Every key
     * that depends on the cluster names [clusterKey], so a screen opened on one
     * cluster never hands its state, or its actions, to another. Null for screens
     * whose state lives elsewhere.
     */
    fun storeKey(clusterKey: String): String? = when (this) {
        is ResourceList -> "list/$clusterKey/$typeKey"
        is ResourceDetail -> "detail/$clusterKey/$typeKey/$namespace/$name"
        is CreateResource -> "create/$clusterKey/$typeKey"
        Logcat -> "logcat"
        ManageClusters, OpenSourceLicenses -> null
    }
}

/**
 * Converts a [Screen] to and from a string for saved instance state. Fields are
 * joined with a character that cannot occur in a resource type key, namespace
 * or object name.
 */
internal object ScreenSaver {

    private const val SEPARATOR = "|"

    fun save(screen: Screen): String = when (screen) {
        is Screen.ResourceList -> join("list", screen.typeKey)
        is Screen.ResourceDetail -> join("detail", screen.typeKey, screen.namespace, screen.name)
        is Screen.CreateResource -> join("create", screen.typeKey)
        Screen.ManageClusters -> "clusters"
        Screen.Logcat -> "logcat"
        Screen.OpenSourceLicenses -> "licenses"
    }

    fun restore(saved: String): Screen? {
        val parts = saved.split(SEPARATOR)
        return when (parts.first()) {
            "list" -> Screen.ResourceList(typeKey = parts[1])
            "detail" -> Screen.ResourceDetail(typeKey = parts[1], namespace = parts[2], name = parts[3])
            "create" -> Screen.CreateResource(typeKey = parts[1])
            "clusters" -> Screen.ManageClusters
            "logcat" -> Screen.Logcat
            "licenses" -> Screen.OpenSourceLicenses
            else -> null
        }
    }

    private fun join(vararg parts: String): String = parts.joinToString(SEPARATOR)
}

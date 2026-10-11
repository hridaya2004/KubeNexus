package dev.hridaya.kubenexus.domain.model

/** Most resource kinds that can be favourites at once. */
const val MAX_FAVOURITE_RESOURCES = 10

/** What toggling a favourite resource kind did. */
enum class FavouriteToggle {
    ADDED,
    REMOVED,

    /** Not a favourite, and [MAX_FAVOURITE_RESOURCES] are already kept. */
    FULL,
}

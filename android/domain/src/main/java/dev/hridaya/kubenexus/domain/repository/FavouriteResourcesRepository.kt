package dev.hridaya.kubenexus.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The resource kinds kept on the Favourites screen, named by
 * [dev.hridaya.kubenexus.domain.model.resource.ResourceType.key].
 */
interface FavouriteResourcesRepository {

    /** Favourite type keys in the order they were added. */
    fun getFavouritesStream(): Flow<List<String>>

    /**
     * Adds [typeKey] to the end of the favourites. Returns false, and changes nothing,
     * when [MAX_FAVOURITES] are already kept; adding an existing favourite returns true.
     */
    suspend fun addFavourite(typeKey: String): Boolean

    suspend fun removeFavourite(typeKey: String)

    /** Removes [typeKey] if it is a favourite and adds it otherwise, atomically. */
    suspend fun toggleFavourite(typeKey: String): FavouriteToggle

    companion object {
        const val MAX_FAVOURITES = 10
    }
}

/** What [FavouriteResourcesRepository.toggleFavourite] did. */
enum class FavouriteToggle {
    ADDED,
    REMOVED,

    /** Not a favourite, and [FavouriteResourcesRepository.MAX_FAVOURITES] are already kept. */
    FULL,
}

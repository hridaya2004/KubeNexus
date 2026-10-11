package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
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
     * when [MAX_FAVOURITE_RESOURCES] are already kept; adding an existing favourite returns true.
     */
    suspend fun addFavourite(typeKey: String): Boolean

    suspend fun removeFavourite(typeKey: String)
}

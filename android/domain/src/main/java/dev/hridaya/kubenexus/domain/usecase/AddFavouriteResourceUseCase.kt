package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import javax.inject.Inject

class AddFavouriteResourceUseCase @Inject constructor(
    private val repository: FavouriteResourcesRepository,
) {
    /** False when [MAX_FAVOURITE_RESOURCES] are already kept. */
    suspend operator fun invoke(typeKey: String): Boolean = repository.addFavourite(typeKey)
}

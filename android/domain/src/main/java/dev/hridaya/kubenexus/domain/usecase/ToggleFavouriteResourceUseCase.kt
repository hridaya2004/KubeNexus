package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.FavouriteToggle
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import javax.inject.Inject

class ToggleFavouriteResourceUseCase @Inject constructor(
    private val repository: FavouriteResourcesRepository,
) {
    suspend operator fun invoke(typeKey: String): FavouriteToggle = repository.toggleFavourite(typeKey)
}

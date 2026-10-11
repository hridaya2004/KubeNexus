package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import javax.inject.Inject

class RemoveFavouriteResourceUseCase @Inject constructor(
    private val repository: FavouriteResourcesRepository,
) {
    suspend operator fun invoke(typeKey: String) = repository.removeFavourite(typeKey)
}

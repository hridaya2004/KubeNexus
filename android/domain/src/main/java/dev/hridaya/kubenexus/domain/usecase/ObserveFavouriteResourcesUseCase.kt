package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Type keys of the favourite resource kinds, in the order they were added. */
class ObserveFavouriteResourcesUseCase @Inject constructor(
    private val repository: FavouriteResourcesRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.getFavouritesStream()
}

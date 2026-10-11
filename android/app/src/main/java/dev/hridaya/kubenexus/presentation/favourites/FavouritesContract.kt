package dev.hridaya.kubenexus.presentation.favourites

import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation

sealed interface FavouritesUiAction {
    data class AddFavourite(val presentation: ResourcePresentation<*>) : FavouritesUiAction
    data class RemoveFavourite(val presentation: ResourcePresentation<*>) : FavouritesUiAction
}

sealed interface FavouritesUiEffect {
    data class ShowToast(val message: String) : FavouritesUiEffect
}

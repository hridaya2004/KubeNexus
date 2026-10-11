package dev.hridaya.kubenexus.presentation.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.domain.usecase.AddFavouriteResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveFavouriteResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.RemoveFavouriteResourceUseCase
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    observeFavouriteResources: ObserveFavouriteResourcesUseCase,
    private val addFavouriteResource: AddFavouriteResourceUseCase,
    private val removeFavouriteResource: RemoveFavouriteResourceUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    /** Null until loaded, so the screen does not flash its empty state. */
    val favourites: StateFlow<List<ResourcePresentation<*>>?> = observeFavouriteResources()
        .map { keys -> keys.mapNotNull { key -> ResourceCatalog.all.find { it.kind.type.key == key } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _effects = Channel<FavouritesUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAction(action: FavouritesUiAction) {
        when (action) {
            is FavouritesUiAction.AddFavourite -> addFavourite(action.presentation)
            is FavouritesUiAction.RemoveFavourite -> removeFavourite(action.presentation)
        }
    }

    private fun addFavourite(presentation: ResourcePresentation<*>) {
        viewModelScope.launch(dispatcherProvider.main) {
            val message = if (addFavouriteResource(presentation.kind.type.key)) {
                "${presentation.title} added to favourites"
            } else {
                "You can keep up to $MAX_FAVOURITE_RESOURCES favourites. Remove one to add another."
            }
            _effects.send(FavouritesUiEffect.ShowToast(message))
        }
    }

    private fun removeFavourite(presentation: ResourcePresentation<*>) {
        viewModelScope.launch(dispatcherProvider.main) {
            removeFavouriteResource(presentation.kind.type.key)
            _effects.send(FavouritesUiEffect.ShowToast("${presentation.title} removed from favourites"))
        }
    }
}

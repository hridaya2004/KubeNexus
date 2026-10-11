package dev.hridaya.kubenexus.presentation.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.domain.model.FavouriteToggle
import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.domain.usecase.ObserveFavouriteResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.ToggleFavouriteResourceUseCase
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
    private val toggleFavouriteResource: ToggleFavouriteResourceUseCase,
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
            is FavouritesUiAction.ToggleFavourite -> toggleFavourite(action.presentation)
        }
    }

    private fun toggleFavourite(presentation: ResourcePresentation<*>) {
        viewModelScope.launch(dispatcherProvider.main) {
            val message = when (toggleFavouriteResource(presentation.kind.type.key)) {
                FavouriteToggle.ADDED -> "${presentation.title} added to favourites"
                FavouriteToggle.REMOVED -> "${presentation.title} removed from favourites"
                FavouriteToggle.FULL -> "You can keep up to $MAX_FAVOURITE_RESOURCES favourites. Remove one to add another."
            }
            _effects.send(FavouritesUiEffect.ShowToast(message))
        }
    }
}

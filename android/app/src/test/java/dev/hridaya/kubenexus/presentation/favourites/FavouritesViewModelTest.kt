package dev.hridaya.kubenexus.presentation.favourites

import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import dev.hridaya.kubenexus.domain.usecase.AddFavouriteResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveFavouriteResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.RemoveFavouriteResourceUseCase
import dev.hridaya.kubenexus.presentation.resources.kinds.DeploymentsPresentation
import dev.hridaya.kubenexus.presentation.resources.kinds.PodsPresentation
import dev.hridaya.kubenexus.presentation.resources.kinds.ServicesPresentation
import dev.hridaya.kubenexus.presentation.testing.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeFavouriteResourcesRepository()
    private lateinit var viewModel: FavouritesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = favouritesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun favouritesViewModel(repository: FavouriteResourcesRepository) = FavouritesViewModel(
        ObserveFavouriteResourcesUseCase(repository),
        AddFavouriteResourceUseCase(repository),
        RemoveFavouriteResourceUseCase(repository),
        TestDispatcherProvider(testDispatcher),
    )

    @Test
    fun `favourites are unknown until read, so an empty list really means none`() = runTest(testDispatcher) {
        val firstRead = MutableSharedFlow<List<String>>()
        val slowRepository = object : FavouriteResourcesRepository by repository {
            override fun getFavouritesStream(): Flow<List<String>> = firstRead
        }
        val viewModel = favouritesViewModel(slowRepository)
        advanceUntilIdle()
        assertNull(viewModel.favourites.value)

        firstRead.emit(emptyList())
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), viewModel.favourites.value)
    }

    @Test
    fun `favourites keep the stored order and skip kinds the app no longer presents`() = runTest(testDispatcher) {
        repository.keys.value = listOf(
            ServicesPresentation.kind.type.key,
            "example.com/v1/gone",
            PodsPresentation.kind.type.key,
        )
        advanceUntilIdle()

        assertEquals(listOf(ServicesPresentation, PodsPresentation), viewModel.favourites.value)
    }

    @Test
    fun `adding a kind keeps it and removing it drops it`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(FavouritesUiAction.AddFavourite(DeploymentsPresentation))
        advanceUntilIdle()
        assertEquals(listOf(DeploymentsPresentation), viewModel.favourites.value)

        viewModel.onAction(FavouritesUiAction.RemoveFavourite(DeploymentsPresentation))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), viewModel.favourites.value)
    }

    @Test
    fun `adding past the maximum tells the user and changes nothing`() = runTest(testDispatcher) {
        repository.keys.value = (1..MAX_FAVOURITE_RESOURCES).map { "example.com/v1/kind$it" }
        advanceUntilIdle()

        viewModel.onAction(FavouritesUiAction.AddFavourite(PodsPresentation))
        advanceUntilIdle()

        assertEquals(MAX_FAVOURITE_RESOURCES, repository.keys.value.size)
        val effect = viewModel.effects.first() as FavouritesUiEffect.ShowToast
        assertEquals("You can keep up to $MAX_FAVOURITE_RESOURCES favourites. Remove one to add another.", effect.message)
    }

    private class FakeFavouriteResourcesRepository : FavouriteResourcesRepository {
        val keys = MutableStateFlow<List<String>>(emptyList())

        override fun getFavouritesStream(): Flow<List<String>> = keys

        override suspend fun addFavourite(typeKey: String): Boolean {
            if (typeKey in keys.value) return true
            if (keys.value.size >= MAX_FAVOURITE_RESOURCES) return false
            keys.value += typeKey
            return true
        }

        override suspend fun removeFavourite(typeKey: String) {
            keys.value -= typeKey
        }
    }
}

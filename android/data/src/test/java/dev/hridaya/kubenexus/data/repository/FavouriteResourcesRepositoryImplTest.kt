package dev.hridaya.kubenexus.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository.Companion.MAX_FAVOURITES
import dev.hridaya.kubenexus.domain.repository.FavouriteToggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FavouriteResourcesRepositoryImplTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: FavouriteResourcesRepositoryImpl

    private val pods = ResourceKinds.Pods.type.key
    private val deployments = ResourceKinds.Deployments.type.key
    private val services = ResourceKinds.Services.type.key

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tmpFolder.root, "test_app_prefs.preferences_pb") },
        )
        repository = FavouriteResourcesRepositoryImpl(dataStore, TestDispatcherProvider(testDispatcher))
    }

    @Test
    fun `favourites default to the kinds Home listed`() = testScope.runTest {
        assertEquals(listOf(pods, deployments, services), repository.getFavouritesStream().first())
    }

    @Test
    fun `removing every favourite leaves none instead of the defaults`() = testScope.runTest {
        repository.removeFavourite(pods)
        repository.removeFavourite(deployments)
        repository.removeFavourite(services)

        assertEquals(emptyList<String>(), repository.getFavouritesStream().first())
    }

    @Test
    fun `added favourites keep the order they were added in`() = testScope.runTest {
        repository.removeFavourite(pods)
        assertTrue(repository.addFavourite(pods))

        assertEquals(listOf(deployments, services, pods), repository.getFavouritesStream().first())
    }

    @Test
    fun `adding an existing favourite changes nothing`() = testScope.runTest {
        assertTrue(repository.addFavourite(deployments))

        assertEquals(listOf(pods, deployments, services), repository.getFavouritesStream().first())
    }

    @Test
    fun `toggling removes a favourite, adds it back, and refuses one past the maximum`() = testScope.runTest {
        assertEquals(FavouriteToggle.REMOVED, repository.toggleFavourite(pods))
        assertEquals(FavouriteToggle.ADDED, repository.toggleFavourite(pods))
        assertEquals(listOf(deployments, services, pods), repository.getFavouritesStream().first())

        (1..MAX_FAVOURITES - 3).forEach { repository.addFavourite("example.com/v1/kind$it") }
        assertEquals(FavouriteToggle.FULL, repository.toggleFavourite("example.com/v1/onemore"))
        assertEquals(MAX_FAVOURITES, repository.getFavouritesStream().first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a key that cannot be stored is refused`() = testScope.runTest {
        repository.toggleFavourite("apps/v1/deployments,v1/pods")
    }

    @Test
    fun `no more than the maximum favourites are kept`() = testScope.runTest {
        val extra = (1..MAX_FAVOURITES - 3).map { "example.com/v1/kind$it" }
        extra.forEach { assertTrue(repository.addFavourite(it)) }

        assertFalse(repository.addFavourite("example.com/v1/onemore"))
        assertEquals(listOf(pods, deployments, services) + extra, repository.getFavouritesStream().first())
    }
}

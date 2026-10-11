package dev.hridaya.kubenexus.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository.Companion.MAX_FAVOURITES
import dev.hridaya.kubenexus.domain.repository.FavouriteToggle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Stored as one comma-separated preference; defaults to the kinds Home used to list. */
@Singleton
class FavouriteResourcesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatcherProvider: DispatcherProvider,
) : FavouriteResourcesRepository {

    override fun getFavouritesStream(): Flow<List<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences -> preferences.favourites() }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.io)
    }

    override suspend fun addFavourite(typeKey: String): Boolean {
        requireStorable(typeKey)
        return withContext(dispatcherProvider.io) {
            var added = false
            dataStore.edit { preferences ->
                val current = preferences.favourites()
                added = when {
                    typeKey in current -> true
                    current.size >= MAX_FAVOURITES -> false
                    else -> {
                        preferences[KEY_FAVOURITES] = (current + typeKey).joinToString(SEPARATOR)
                        true
                    }
                }
            }
            added
        }
    }

    override suspend fun removeFavourite(typeKey: String) {
        withContext(dispatcherProvider.io) {
            dataStore.edit { preferences ->
                preferences[KEY_FAVOURITES] = (preferences.favourites() - typeKey).joinToString(SEPARATOR)
            }
        }
    }

    override suspend fun toggleFavourite(typeKey: String): FavouriteToggle {
        requireStorable(typeKey)
        return withContext(dispatcherProvider.io) {
            var outcome = FavouriteToggle.FULL
            dataStore.edit { preferences ->
                val current = preferences.favourites()
                outcome = when {
                    typeKey in current -> FavouriteToggle.REMOVED
                    current.size >= MAX_FAVOURITES -> FavouriteToggle.FULL
                    else -> FavouriteToggle.ADDED
                }
                when (outcome) {
                    FavouriteToggle.REMOVED -> preferences[KEY_FAVOURITES] = (current - typeKey).joinToString(SEPARATOR)
                    FavouriteToggle.ADDED -> preferences[KEY_FAVOURITES] = (current + typeKey).joinToString(SEPARATOR)
                    FavouriteToggle.FULL -> Unit
                }
            }
            outcome
        }
    }

    private fun requireStorable(typeKey: String) {
        require(typeKey.isNotBlank() && SEPARATOR !in typeKey) { "Not a resource type key: '$typeKey'" }
    }

    private fun Preferences.favourites(): List<String> {
        val stored = this[KEY_FAVOURITES] ?: return DEFAULT_FAVOURITES
        return stored.split(SEPARATOR).filter { it.isNotBlank() }
    }

    companion object {
        val KEY_FAVOURITES = stringPreferencesKey("key_favourite_resources")
        private const val SEPARATOR = ","

        private val DEFAULT_FAVOURITES = listOf(
            ResourceKinds.Pods,
            ResourceKinds.Deployments,
            ResourceKinds.Services,
        ).map { it.type.key }
    }
}

package dev.hridaya.kubenexus.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
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
                    current.size >= MAX_FAVOURITE_RESOURCES -> false
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

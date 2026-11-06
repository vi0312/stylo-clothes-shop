package com.stylo.shop.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class TokenPair(val accessToken: String, val refreshToken: String)

interface TokenStore {
    suspend fun read(): TokenPair?

    suspend fun save(tokens: TokenPair)

    suspend fun clear()
}

@Singleton
class DataStoreTokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TokenStore {

    override suspend fun read(): TokenPair? {
        val preferences = dataStore.data.first()
        val access = preferences[ACCESS_TOKEN] ?: return null
        val refresh = preferences[REFRESH_TOKEN] ?: return null
        return TokenPair(accessToken = access, refreshToken = refresh)
    }

    override suspend fun save(tokens: TokenPair) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = tokens.accessToken
            preferences[REFRESH_TOKEN] = tokens.refreshToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}

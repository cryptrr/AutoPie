package com.autosec.pie.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber


interface MyPreferences {
    fun getString(key: Preferences.Key<String>) : Flow<String>
    fun getBool(key: Preferences.Key<Boolean>) : Flow<Boolean>
    fun getStringSync(key: Preferences.Key<String>) : String
    fun getBoolSync(key: Preferences.Key<Boolean>) : Boolean
    suspend fun setString(key: Preferences.Key<String>, value: String)
    suspend fun setBool(key: Preferences.Key<Boolean>, value: Boolean)

}

class AppPreferences(private val context: Context) : MyPreferences {
    companion object{
        private val Context.dataStore : DataStore<Preferences> by preferencesDataStore(name = "settings")
        val IS_LOGGED_IN = booleanPreferencesKey("isLoggedIn")
        val IS_FILE_OBSERVERS_OFF = booleanPreferencesKey("isFileObserversOff")
        val ACCESS_TOKEN = stringPreferencesKey("accessToken")
        val REFRESH_TOKEN = stringPreferencesKey("refreshToken")
        val USER_NAME = stringPreferencesKey("username")
        val USER_AVATAR = stringPreferencesKey("userAvatar")
        val CURRENT_THEME = stringPreferencesKey("darkThemeEnabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamicColorsEnabled")
    }

    override fun getString(key: Preferences.Key<String>)  = context.dataStore.data.map {
        it[key] ?: ""
    }

    override fun getBool(key: Preferences.Key<Boolean>) = context.dataStore.data.map{
        it[key] ?: false
    }


    override fun getStringSync(key: Preferences.Key<String>) = runBlocking { context.dataStore.data.first()[key] ?: "" }
    override fun getBoolSync(key: Preferences.Key<Boolean>) = runBlocking { context.dataStore.data.first()[key] ?: false }


    override suspend fun setString(key: Preferences.Key<String>, value: String){
        Timber.d("$key : $value")
        context.dataStore.edit {
            it[key] = value
        }
    }

    override suspend fun setBool(key: Preferences.Key<Boolean>, value: Boolean){
        Timber.d( "$key : $value")
        context.dataStore.edit {
            it[key] = value
        }
    }
}
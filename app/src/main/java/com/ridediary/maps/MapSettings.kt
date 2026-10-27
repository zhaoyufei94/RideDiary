package com.ridediary.maps

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class MapConfig(val enabled: Boolean=false,val apiKey: String="",val styleId: String="",val appearance: MapAppearance=MapAppearance(),val trafficEnabled: Boolean=false) {
    val ready get()=enabled && apiKey.isNotBlank()
    val trafficReady get()=trafficEnabled && apiKey.isNotBlank()
    override fun toString()="MapConfig(enabled=$enabled, trafficEnabled=$trafficEnabled, configured=${apiKey.isNotBlank()})"
}

/** Map credentials never enter the database, SavedState, logs, or source control. */
class MapSettings(context: Context) {
    private val prefs=context.getSharedPreferences("maps",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val saveLock=Mutex()
    val config=MutableStateFlow<MapConfig?>(null)
    private val loaded=scope.async {
        val secret=runCatching {
            val encoded=prefs.getString("credential",null) ?: return@runCatching ""
            val parts=encoded.split(':',limit=2); require(parts.size==2)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            }.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrDefault("")
        val styleId=prefs.getString("style","").orEmpty()
        val appearance=MapAppearance(
            style=MapAppearance.styleFromStored(prefs.getString("display_style",null),styleId.isNotBlank()),
            speedColors=prefs.getBoolean("speed_colors",true),stops=prefs.getBoolean("stops",true),
            endpoints=prefs.getBoolean("endpoints",true),places=prefs.getBoolean("places",false),traffic=prefs.getBoolean("traffic",false))
        config.value=MapConfig(prefs.getBoolean("enabled",false),secret,styleId,appearance,prefs.getBoolean("route_traffic",false))
    }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ridediary-map-key",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("ridediary-map-key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @android.annotation.SuppressLint("UseKtx") // Keep commit's Boolean result to report a persistence failure.
    suspend fun save(value: MapConfig)=withContext(Dispatchers.IO) {
        loaded.await()
        saveLock.withLock {
            require((!value.enabled && !value.trafficEnabled) || value.apiKey.isNotBlank())
            val normalized=value.copy(apiKey=value.apiKey.trim(),styleId=value.styleId.trim(),appearance=config.value?.appearance ?: value.appearance)
            val encrypted=if(normalized.apiKey.isBlank()) "" else Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE,key())
                val bytes=doFinal(normalized.apiKey.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(bytes,Base64.NO_WRAP)
            }
            check(prefs.edit().putBoolean("enabled",normalized.enabled).putBoolean("route_traffic",normalized.trafficEnabled)
                .putString("credential",encrypted).putString("style",normalized.styleId).commit())
            config.value=normalized
        }
    }
    @android.annotation.SuppressLint("UseKtx") // Check persistence before publishing the new display state.
    suspend fun saveAppearance(value: MapAppearance)=withContext(Dispatchers.IO) {
        loaded.await()
        saveLock.withLock {
            val current=checkNotNull(config.value)
            check(prefs.edit().putString("display_style",value.style.name).putBoolean("speed_colors",value.speedColors)
                .putBoolean("stops",value.stops).putBoolean("endpoints",value.endpoints)
                .putBoolean("places",value.places).putBoolean("traffic",value.traffic).commit())
            config.value=current.copy(appearance=value)
        }
    }
}

package com.ridediary.weather

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** User credentials remain local and are encrypted with this installation's Android Keystore. */
class WeatherSettings(context: Context) {
    private val prefs=context.getSharedPreferences("weather",Context.MODE_PRIVATE)
    private val alias="ridediary-weather-key"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): WeatherConfig {
        val secret=runCatching {
            val encoded=prefs.getString("credential",null) ?: return@runCatching ""
            val parts=encoded.split(':',limit=2); require(parts.size==2)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            }.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrDefault("")
        val provider=runCatching { WeatherProvider.valueOf(prefs.getString("provider","OPEN_METEO")!!) }.getOrDefault(WeatherProvider.OPEN_METEO)
        return WeatherConfig(prefs.getBoolean("enabled",true),provider,prefs.getString("host","").orEmpty(),secret)
    }
    fun save(config: WeatherConfig) {
        require(config.provider!=WeatherProvider.QWEATHER || config.ready || !config.enabled)
        val encrypted=if(config.apiKey.isBlank()) "" else Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key())
            val data=doFinal(config.apiKey.trim().toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP)
        }
        check(prefs.edit().apply {
            putBoolean("enabled",config.enabled); putString("provider",config.provider.name)
            putString("host",WeatherConfig.normalizeHost(config.host)); putString("credential",encrypted)
        }.commit())
    }
}

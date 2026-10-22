package com.ridediary

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import com.ridediary.recording.RecordingService
import com.ridediary.ui.*
import java.util.Locale

fun localized(context: Context): Context {
    val language=context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("language","zh-CN")!!
    val config=android.content.res.Configuration(context.resources.configuration)
    config.setLocale(Locale.forLanguageTag(language))
    return context.createConfigurationContext(config)
}
class MainActivity: ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(localized(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RideDiaryTheme { RideDiaryScreen(onStart={
            try { ContextCompat.startForegroundService(this,Intent(this,RecordingService::class.java)) }
            catch (_: IllegalStateException) { RecordingService.quality.value="START_FAILED" }
            catch (_: SecurityException) { RecordingService.quality.value="PERMISSION_LOST" }
        },onStop={ startService(Intent(this,RecordingService::class.java).setAction(RecordingService.STOP)) },onLanguage={
            val prefs=getSharedPreferences("settings",Context.MODE_PRIVATE)
            prefs.edit { putString("language",if(prefs.getString("language","zh-CN")=="zh-CN") "en" else "zh-CN") }
            recreate()
        }) } }
    }
}

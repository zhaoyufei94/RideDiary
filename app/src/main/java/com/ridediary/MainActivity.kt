package com.ridediary

import android.content.*
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ridediary.recording.RecordingService
import com.ridediary.ui.*
import kotlinx.coroutines.launch
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    RecordingService.active.collect { recording ->
                        if(recording) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
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

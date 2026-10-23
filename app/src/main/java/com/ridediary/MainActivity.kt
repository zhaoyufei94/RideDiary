package com.ridediary

import android.content.*
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
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
    private var stopReviewToken by mutableIntStateOf(0)
    private fun readStopIntent(value: Intent?) { if(value?.action==RecordingService.REVIEW_STOP) stopReviewToken++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readStopIntent(intent) }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(localized(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopReviewToken=savedInstanceState?.getInt("stopReviewToken") ?: 0
        if(savedInstanceState==null) readStopIntent(intent)
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
        fun startRecording(routeId: String?=null) {
            try { ContextCompat.startForegroundService(this,Intent(this,RecordingService::class.java).apply {
                routeId?.let { putExtra(RecordingService.EXTRA_ROUTE_ID,it) }
            }) }
            catch (_: IllegalStateException) { RecordingService.quality.value="START_FAILED" }
            catch (_: SecurityException) { RecordingService.quality.value="PERMISSION_LOST" }
        }
        setContent { RideDiaryTheme { RideDiaryScreen(onStart={ startRecording() },onStartRoute={ startRecording(it) },onStop={ startService(Intent(this,RecordingService::class.java).setAction(RecordingService.STOP)) },
            onDiscard={ startService(Intent(this,RecordingService::class.java).setAction(RecordingService.DISCARD)) },stopReviewToken=stopReviewToken,onLanguage={
            val prefs=getSharedPreferences("settings",Context.MODE_PRIVATE)
            prefs.edit { putString("language",if(prefs.getString("language","zh-CN")=="zh-CN") "en" else "zh-CN") }
            recreate()
        }) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("stopReviewToken",stopReviewToken)
        super.onSaveInstanceState(outState)
    }
}

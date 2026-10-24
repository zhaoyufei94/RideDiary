package com.ridediary.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridediary.R
import com.ridediary.RideDiaryApp
import com.ridediary.maps.BaiduRuntime
import com.ridediary.maps.MapStyle
import kotlinx.coroutines.launch
import java.security.MessageDigest

@Composable fun MapSettingsSection() {
    val context=LocalContext.current
    val settings=(context.applicationContext as RideDiaryApp).mapSettings
    val config by settings.config.collectAsStateWithLifecycle()
    val savedConfig=config ?: return
    var enabled by remember(savedConfig) { mutableStateOf(savedConfig.enabled) }
    // Do not save credentials in Compose SavedState.
    var key by remember(savedConfig) { mutableStateOf(savedConfig.apiKey) }
    var style by remember(savedConfig) { mutableStateOf(savedConfig.styleId) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val clipboard=remember(context) { context.getSystemService(android.content.ClipboardManager::class.java) }
    val mapTitle=stringResource(R.string.map_settings)
    val registration=remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            val info=context.packageManager.getPackageInfo(context.packageName,if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val certificates=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
            val sha=certificates?.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }
            }.orEmpty()
            "Package: ${context.packageName}\nSHA1: $sha"
        }.getOrDefault("Package: ${context.packageName}")
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(R.string.map_settings),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        Switch(checked=enabled,onCheckedChange={ enabled=it; saved=false },enabled=!saving,modifier=Modifier.testTag("map-enabled").semantics { contentDescription=mapTitle })
    }
    Text(stringResource(R.string.map_optional_hint),style=MaterialTheme.typography.bodySmall,color=Muted)
    OutlinedTextField(value=key,onValueChange={ key=it.take(256); saved=false },label={ Text(stringResource(R.string.map_ak)) },singleLine=true,
        visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        enabled=!saving,modifier=Modifier.fillMaxWidth().testTag("map-api-key"))
    OutlinedTextField(value=style,onValueChange={ style=it.take(128); saved=false },label={ Text(stringResource(R.string.map_style_id)) },singleLine=true,
        supportingText={ Text(stringResource(R.string.map_style_hint)) },enabled=!saving,modifier=Modifier.fillMaxWidth())
    Text(stringResource(R.string.map_key_hint),style=MaterialTheme.typography.bodySmall,color=Muted)
    TextButton(onClick={ clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Baidu Maps registration",registration)) }) { Text(stringResource(R.string.map_copy_registration)) }
    Text(stringResource(R.string.map_privacy),style=MaterialTheme.typography.bodySmall,color=Muted)
    TextButton(onClick={ context.startActivity(Intent(Intent.ACTION_VIEW,"https://lbsyun.baidu.com/index.php?title=openprivacy".toUri())) }) {
        Text(stringResource(R.string.map_privacy_link))
    }
    val draft=savedConfig.copy(enabled=enabled,apiKey=key.trim(),styleId=style.trim())
    Button(enabled=!saving && (!enabled || draft.ready) && draft!=savedConfig,onClick={
        saving=true; failed=false; saved=false
        scope.launch {
            try {
                settings.save(draft)
                if(!draft.enabled) BaiduRuntime.revoke(context)
                saved=true
            } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(_: Exception) { failed=true }
            finally { saving=false }
        }
    },modifier=Modifier.testTag("map-save")) { Text(stringResource(R.string.map_save)) }
    if(failed) Text(stringResource(R.string.error),color=Danger,style=MaterialTheme.typography.bodySmall)
    if(saved) Text(stringResource(R.string.map_saved),color=Muted,style=MaterialTheme.typography.bodySmall)
}

@Composable fun MapDisplayDialog(onDismiss: ()->Unit) {
    val settings=(LocalContext.current.applicationContext as RideDiaryApp).mapSettings
    val config by settings.config.collectAsStateWithLifecycle()
    val current=config ?: return
    var draft by remember(current.appearance) { mutableStateOf(current.appearance) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={ if(!saving) onDismiss() },containerColor=Surface,title={ Text(stringResource(R.string.map_display_options)) },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.map_basemap_style),style=MaterialTheme.typography.titleSmall)
            MapStyle.entries.forEach { style ->
                val label=when(style) {
                    MapStyle.SIMPLE->R.string.map_style_simple
                    MapStyle.STANDARD->R.string.map_style_standard
                    MapStyle.SATELLITE->R.string.map_style_satellite
                    MapStyle.CUSTOM->R.string.map_style_custom
                }
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=draft.style==style,enabled=!saving,
                    role=Role.RadioButton,onClick={ draft=draft.copy(style=style) }).testTag("map-style-${style.name}"),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=draft.style==style,onClick=null,enabled=!saving)
                    Text(stringResource(label),Modifier.padding(start=12.dp),style=MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(if(draft.style==MapStyle.CUSTOM) R.string.map_custom_style_hint else R.string.map_style_simple_hint),style=MaterialTheme.typography.bodySmall,color=Muted)
            HorizontalDivider()
            Text(stringResource(R.string.map_track_contents),style=MaterialTheme.typography.titleSmall)
            MapDisplayToggle(R.string.map_show_speed,draft.speedColors,!saving) { draft=draft.copy(speedColors=it) }
            MapDisplayToggle(R.string.map_show_stops,draft.stops,!saving) { draft=draft.copy(stops=it) }
            MapDisplayToggle(R.string.map_show_endpoints,draft.endpoints,!saving) { draft=draft.copy(endpoints=it) }
            HorizontalDivider()
            Text(stringResource(R.string.map_basemap_contents),style=MaterialTheme.typography.titleSmall)
            MapDisplayToggle(R.string.map_show_places,draft.places,!saving) { draft=draft.copy(places=it) }
            MapDisplayToggle(R.string.map_show_traffic,draft.traffic,!saving) { draft=draft.copy(traffic=it) }
            Text(stringResource(R.string.map_display_hint),style=MaterialTheme.typography.bodySmall,color=Muted)
            if(failed) Text(stringResource(R.string.error),color=Danger,style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton={ TextButton(enabled=!saving,onClick={
            saving=true; failed=false
            scope.launch {
                try { settings.saveAppearance(draft); onDismiss() }
                catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_: Exception) { failed=true }
                finally { saving=false }
            }
        }) { Text(stringResource(R.string.map_apply)) } },
        dismissButton={ TextButton(enabled=!saving,onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun MapDisplayToggle(label: Int,value: Boolean,enabled: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=value,enabled=enabled,role=Role.Switch,onValueChange=onChange)
        .testTag("map-display-$label"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(checked=value,onCheckedChange=null,enabled=enabled)
    }
}

@Composable fun MapSettingsDialog(onDismiss: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surface,title={ Text(stringResource(R.string.map_settings)) },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MapSettingsSection()
        } },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.close)) } })
}

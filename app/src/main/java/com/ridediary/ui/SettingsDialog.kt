package com.ridediary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridediary.weather.*
import kotlinx.coroutines.launch
import com.ridediary.R
import com.ridediary.data.RideDatabase
import com.ridediary.data.RideDocumentsProvider

@Composable fun SettingsDialog(onDismiss: ()->Unit,onLanguage: ()->Unit,weather: WeatherViewModel) {
    val context=LocalContext.current
    val version=remember(context) { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() }
    val chinese=LocalConfiguration.current.locales[0].language=="zh"
    var baiduSettingsOpen by remember { mutableStateOf(false) }
    AlertDialog(modifier=Modifier.testTag("settings-dialog"),onDismissRequest=onDismiss,containerColor=Surface,tonalElevation=0.dp,
        title={ Text(stringResource(R.string.settings)) },
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_ridediary_logo),contentDescription=null,modifier=Modifier.size(44.dp))
                    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.app_brand),fontSize=16.sp,fontWeight=FontWeight.Medium,color=Ink)
                        Text(stringResource(R.string.app_version,version),fontSize=11.sp,color=Muted)
                    }
                }
                HorizontalDivider(color=Line)
                DataLocationSection()
                WeatherSettingsSection(weather)
                HorizontalDivider(color=Line)
                OutlinedButton(onClick={ baiduSettingsOpen=true },modifier=Modifier.fillMaxWidth().testTag("baidu-settings-entry")) {
                    Text(stringResource(R.string.map_settings))
                }
                HorizontalDivider(color=Line)
                Text(stringResource(R.string.language_setting),style=MaterialTheme.typography.titleMedium,color=Ink)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    listOf(true to R.string.language_chinese,false to R.string.language_english).forEach { (value,label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(selected=chinese==value,role=Role.RadioButton,
                            onClick={ if(chinese!=value) onLanguage() }).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=chinese==value,onClick=null)
                            Text(stringResource(label),Modifier.padding(start=12.dp),color=Ink)
                        }
                    }
                }
            }
        },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.close)) } })
    if(baiduSettingsOpen) MapSettingsDialog { baiduSettingsOpen=false }
}

@Composable internal fun DataLocationSection() {
    val context=LocalContext.current
    val path=remember(context) { context.getDatabasePath(RideDatabase.NAME).absolutePath }
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    var unavailable by remember { mutableStateOf(false) }
    Text(stringResource(R.string.data_location),style=MaterialTheme.typography.titleMedium,color=Ink)
    SelectionContainer {
        Text(path,modifier=Modifier.testTag("data-location-path"),style=MaterialTheme.typography.bodySmall,color=Muted)
    }
    Text(stringResource(R.string.data_location_description),style=MaterialTheme.typography.bodySmall,color=Muted)
    OutlinedButton(modifier=Modifier.testTag("open-data-location"),onClick={
        unavailable=false
        try {
            context.startActivity(RideDocumentsProvider.browseIntent())
        } catch (_: android.content.ActivityNotFoundException) {
            try { browser.launch(RideDocumentsProvider.pickerIntent()) }
            catch (_: android.content.ActivityNotFoundException) { unavailable=true }
            catch (_: SecurityException) { unavailable=true }
        } catch (_: SecurityException) {
            unavailable=true
        }
    }) { Text(stringResource(R.string.open_data_location)) }
    if(unavailable) Text(stringResource(R.string.data_browser_unavailable),style=MaterialTheme.typography.bodySmall,color=Danger)
}

@Composable private fun WeatherSettingsSection(vm: WeatherViewModel) {
    val weatherLabel=stringResource(R.string.weather_title)
    val config by vm.config.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var provider by remember(config) { mutableStateOf(config.provider) }
    var host by remember(config) { mutableStateOf(config.host) }
    // Credentials must never enter SavedState or logs.
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val draft=WeatherConfig(enabled,provider,host,key)
    if(!loaded) return
    HorizontalDivider(color=Line)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(R.string.weather_title),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        Switch(modifier=Modifier.semantics { contentDescription=weatherLabel },checked=enabled,onCheckedChange={ enabled=it; saved=false },enabled=!saving)
    }
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        WeatherProvider.entries.forEach { value ->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=provider==value,role=Role.RadioButton,
                enabled=!saving,onClick={ provider=value; saved=false }),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=provider==value,onClick=null,enabled=!saving)
                Text(if(value==WeatherProvider.OPEN_METEO) "Open-Meteo" else stringResource(R.string.weather_qweather),Modifier.padding(start=12.dp))
            }
        }
    }
    if(provider==WeatherProvider.QWEATHER) {
        OutlinedTextField(value=host,onValueChange={ host=it.take(253); saved=false },label={ Text("API Host") },singleLine=true,
            placeholder={ Text("xxx.qweatherapi.com") },enabled=!saving,modifier=Modifier.fillMaxWidth(),
            isError=host.isNotBlank() && !WeatherConfig.validHost(host),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
        OutlinedTextField(value=key,onValueChange={ key=it.take(512); saved=false },label={ Text("API Key") },singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!saving,modifier=Modifier.fillMaxWidth(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
        Text(stringResource(R.string.weather_qweather_credentials),style=MaterialTheme.typography.bodySmall,color=Muted)
    } else Text(stringResource(R.string.weather_open_terms),style=MaterialTheme.typography.bodySmall,color=Muted)
    Text(stringResource(R.string.weather_privacy),style=MaterialTheme.typography.bodySmall,color=Muted)
    Button(enabled=!saving && (!enabled || draft.ready) && draft!=config,onClick={
        saving=true; failed=false; saved=false
        scope.launch { saved=vm.save(draft); failed=!saved; saving=false }
    }) { Text(stringResource(R.string.weather_save)) }
    if(failed) Text(stringResource(R.string.error),style=MaterialTheme.typography.bodySmall,color=Danger)
    if(saved) Text(stringResource(R.string.weather_saved),style=MaterialTheme.typography.bodySmall,color=Muted)
}

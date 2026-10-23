package com.ridediary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ridediary.R

@Composable fun SettingsDialog(onDismiss: ()->Unit,onLanguage: ()->Unit) {
    val context=LocalContext.current
    val version=remember(context) { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() }
    val chinese=context.resources.configuration.locales[0].language=="zh"
    AlertDialog(modifier=Modifier.testTag("settings-dialog"),onDismissRequest=onDismiss,containerColor=Surface,tonalElevation=0.dp,
        title={ Text(stringResource(R.string.settings)) },
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_ridediary_logo),contentDescription=null,modifier=Modifier.size(72.dp))
                    Text(stringResource(R.string.app_brand),style=MaterialTheme.typography.titleLarge,color=Ink)
                    Text(stringResource(R.string.app_version,version),style=MaterialTheme.typography.bodyMedium,color=Muted)
                    Text(stringResource(R.string.tagline),style=MaterialTheme.typography.bodyMedium,color=Muted)
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
}

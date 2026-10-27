package com.ridediary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.traffic.*
import com.ridediary.ui.*
import com.ridediary.maps.MapSettings
import com.ridediary.maps.MapConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only in-memory traffic snapshots; no network or recording interaction. */
@RunWith(AndroidJUnit4::class)
class TrafficUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now=1_800_000_000_000L
    @Test fun unknownCurrentDataIsLabeledExplicitly() {
        val snapshot=TrafficSnapshot(now,TrafficObservation(now,null,emptyList()))
        compose.setContent { RideDiaryTheme { Column { TrafficSnapshotView(snapshot) } } }
        compose.onNodeWithText(context.getString(R.string.traffic_current,context.getString(R.string.traffic_unknown))).assertExists()
    }
    @Test fun unifiedSettingsReuseTheSavedKeyWithoutASecondField() {
        val preferenceName="unified-baidu-ui-test"
        val isolated=object: android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String,mode: Int)=context.getSharedPreferences(preferenceName,mode)
        }
        val preferences=context.getSharedPreferences(preferenceName,0)
        preferences.edit().clear().commit()
        try {
            val settings=MapSettings(isolated)
            val key="synthetic-existing-android-ak"
            runBlocking { settings.save(MapConfig(apiKey=key)) }
            compose.setContent { RideDiaryTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MapSettingsSection(settings) } } }
            compose.onAllNodesWithTag("map-api-key").assertCountEquals(1)
            compose.onAllNodesWithTag("traffic-key").assertCountEquals(0)
            compose.onNodeWithTag("traffic-enabled").performScrollTo().performClick()
            compose.onNodeWithTag("map-save").performScrollTo().performClick()
            compose.waitUntil(5_000) { settings.config.value?.trafficEnabled==true }
            assertEquals(key,settings.config.value!!.apiKey)
            assertFalse(settings.config.value!!.enabled)
        } finally { preferences.edit().clear().commit() }
    }
}

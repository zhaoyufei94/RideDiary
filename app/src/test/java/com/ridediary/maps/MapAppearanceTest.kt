package com.ridediary.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAppearanceTest {
    @Test fun previewsHideStopsWithoutChangingTheExpandedPreference() {
        val saved=MapAppearance(stops=true,endpoints=false,speedColors=false)
        assertFalse(saved.forSurface(expanded=false).stops)
        assertTrue(saved.stops)
        assertEquals(saved,saved.forSurface(expanded=true))
        assertFalse(saved.forSurface(expanded=false).endpoints)
        assertFalse(saved.forSurface(expanded=false).speedColors)
        assertFalse(saved.copy(stops=false).forSurface(expanded=true).stops)
    }
    @Test fun preexistingCustomStyleRemainsSelectedWithoutNewPreferences() {
        assertEquals(MapStyle.CUSTOM,MapAppearance.styleFromStored(null,hasCustomStyle=true))
        assertEquals(MapStyle.SIMPLE,MapAppearance.styleFromStored(null,hasCustomStyle=false))
        assertEquals(MapStyle.CUSTOM,MapAppearance.styleFromStored("unknown-future-style",hasCustomStyle=true))
    }

    @Test fun explicitChoiceTakesPrecedenceOverHavingACustomStyleId() {
        assertEquals(MapStyle.STANDARD,MapAppearance.styleFromStored("STANDARD",hasCustomStyle=true))
        assertEquals(MapStyle.SATELLITE,MapAppearance.styleFromStored("SATELLITE",hasCustomStyle=true))
        assertEquals(MapStyle.CUSTOM,MapAppearance.styleFromStored("CUSTOM",hasCustomStyle=false))
    }
}

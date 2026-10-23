package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class RouteIdentityTest {
    @Test fun legacyNamesGroupDirectionsWithoutMergingDifferentDestinations() {
        assertEquals("互联网金融中心",RouteIdentity.name(" 互联网金融中心（下班） "))
        assertEquals(RouteIdentity.legacyGroup("互联网金融中心 - 上班"),RouteIdentity.legacyGroup("互联网金融中心 / 下班"))
        assertEquals(RouteIdentity.key("  Office   Park to work"),RouteIdentity.key("office park to home"))
        assertNotEquals(RouteIdentity.legacyGroup("互联网金融中心"),RouteIdentity.legacyGroup("金融街"))
        assertEquals("上班",RouteIdentity.name("上班"))
    }
    @Test fun duplicateReferencesInOneDirectionDoNotMakeOneGroupAmbiguous() {
        val a=Replay.route(); val b=a.copy(id="duplicate")
        assertNotNull(RouteIdentity.match(Replay.points(),listOf(a,b),mapOf(a.id to "office",b.id to "office")))
        assertNull(RouteIdentity.match(Replay.points(),listOf(a,b),mapOf(a.id to "office",b.id to "another")))
    }
    @Test fun reversalSwapsEndpointsAndUsesIncreasingRelativeTime() {
        val original=Replay.route().copy(startRadius=120.0,endRadius=60.0)
        val reverse=RouteIdentity.reversed(original)
        assertEquals("HOME",reverse.direction)
        assertEquals(original.points.last().lat,reverse.points.first().lat,0.0)
        assertEquals(original.points.first().lon,reverse.points.last().lon,0.0)
        assertEquals(60.0,reverse.startRadius,0.0)
        assertEquals(120.0,reverse.endRadius,0.0)
        assertEquals(0L,reverse.points.first().t)
        assertTrue(reverse.points.zipWithNext().all { (a,b) -> b.t>a.t })
        assertNotNull(RouteIdentity.match(reverse.points,listOf(original,reverse),mapOf(original.id to "office",reverse.id to "office")))
        assertNull(Routing.follow(reverse.points,original))
    }
}

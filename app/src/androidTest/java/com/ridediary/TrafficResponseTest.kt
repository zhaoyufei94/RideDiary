package com.ridediary

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.DrivingRouteLine
import com.baidu.mapapi.search.route.DrivingRouteResult
import com.ridediary.core.Fix
import com.ridediary.core.meters
import com.ridediary.traffic.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic SDK objects only; no network or access to the user's saved AK. */
@RunWith(AndroidJUnit4::class)
class TrafficResponseTest {
    private val reference=listOf(Fix(0,30.0,110.0),Fix(0,30.0,110.01),Fix(0,30.01,110.01))
    private fun response(traffic: IntArray?=intArrayOf(2,2),duration: Int=1200,path: List<Fix> = reference): DrivingRouteResult {
        val step=DrivingRouteLine.DrivingStep().apply {
            setPathList(path.map { LatLng(it.lat,it.lon) })
            trafficList=traffic
            distance=path.zipWithNext().sumOf { (a,b)->meters(a,b) }.toInt()
        }
        val route=DrivingRouteLine().apply { setSteps(listOf(step)); setDuration(duration) }
        return DrivingRouteResult().apply { error=SearchResult.ERRORNO.NO_ERROR; setRouteLines(listOf(route)) }
    }
    @Test fun parsesMatchingRouteAndMissingTrafficNeverBecomesClear() {
        val current=TrafficClient.parseObservation(response(),reference,1234)
        assertEquals(1234L,current.departure); assertEquals(1_200_000L,current.duration); assertEquals(2,current.status)
        for(traffic in listOf(null,intArrayOf(1))) {
            assertEquals(0,TrafficClient.parseObservation(response(traffic=traffic),reference,0).status)
        }
    }
    @Test fun reversedAndDifferentRoutesAreRejected() {
        for(path in listOf(reference.reversed(),reference.map { it.copy(lat=it.lat+.01) })) {
            val error=assertThrows(TrafficException::class.java) { TrafficClient.parseObservation(response(path=path),reference,0) }
            assertEquals(TrafficFailure.ROUTE_MISMATCH,error.failure)
        }
    }
    @Test fun failedSdkPermissionIsNotReportedAsClearTraffic() {
        val result=response().apply { error=SearchResult.ERRORNO.KEY_ERROR }
        val error=assertThrows(TrafficException::class.java) { TrafficClient.parseObservation(result,reference,0) }
        assertEquals(TrafficFailure.AUTH,error.failure)
        for(duration in listOf(-1,0,100_000)) assertNull(TrafficClient.parseObservation(response(duration=duration),reference,0).duration)
    }
    @Test fun sameEndpointDetoursReturnTrafficReferenceAndEmptyGeometryIsDataFailure() {
        val detour=listOf(reference.first(),Fix(0,30.003,110.007),reference.last())
        val current=TrafficClient.parseObservation(response(path=detour),reference,0)
        assertNotNull(current.comparison)
        assertTrue(current.comparison!!.usableReference)
        assertFalse(current.comparison!!.strictMatch)
        val empty=DrivingRouteResult().apply { error=SearchResult.ERRORNO.NO_ERROR; setRouteLines(emptyList()) }
        val failure=assertThrows(TrafficException::class.java) { TrafficClient.parseObservation(empty,reference,0) }
        assertEquals(TrafficFailure.DATA,failure.failure)
    }
}

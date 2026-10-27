package com.ridediary.traffic

import android.content.Context
import android.util.Log
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.*
import com.baidu.mapapi.utils.CoordinateConverter
import com.ridediary.RideDiaryApp
import com.ridediary.core.Fix
import com.ridediary.core.good
import com.ridediary.core.meters
import com.ridediary.data.RouteRecord
import com.ridediary.maps.BaiduRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

internal class TrafficException(val failure: TrafficFailure,val comparisons: List<TrafficRouteComparison> = emptyList()): Exception()

/** Uses the existing Android SDK credential. No Web API credential or raw response persistence. */
class TrafficClient(context: Context) {
    private val app=context.applicationContext as RideDiaryApp
    private val lock=Mutex()
    private data class Entry(val at: Long,val result: TrafficResult)
    private val cache=linkedMapOf<String,Entry>()

    suspend fun fetch(config: TrafficConfig,route: RouteRecord,departure: Long,now: Long,freshAfter: Long=0): TrafficResult = withContext(Dispatchers.Default) {
        if(!config.enabled || !TrafficPolicy.eligible(departure,now)) return@withContext TrafficResult()
        if(!config.ready) return@withContext TrafficResult(failure=TrafficFailure.CONFIGURATION)
        val key=digest("${config.apiKey}|${route.id}|${route.shape}|$departure")
        lock.withLock {
            val cached=cache[key]
            if(cached!=null && cached.at>=freshAfter && now-cached.at in 0..(if(cached.result.failure==null) TrafficPolicy.FRESH_MS else 60_000)) return@withLock cached.result
            val result=try {
                val query=queries.incrementAndGet()
                Log.i(TAG,"query=$query direction=${route.direction.takeIf { it in listOf("WORK","HOME","OTHER") } ?: "OTHER"} start")
                val raw=TrafficGeometry.simplify(route.route().points.filter(::good))
                if(raw.size !in 2..100 || raw.zipWithNext().sumOf { (a,b)->meters(a,b) }<150) throw TrafficException(TrafficFailure.ROUTE_COMPLEX)
                val response=withContext(Dispatchers.Main.immediate) {
                    val settings=app.mapSettings.config.value
                    if(settings==null || !settings.trafficReady || settings.apiKey!=config.apiKey) throw TrafficException(TrafficFailure.CONFIGURATION)
                    if(!BaiduRuntime.ensure(app,settings)) throw TrafficException(TrafficFailure.AUTH)
                    val reference=raw.map { point ->
                        val converted=CoordinateConverter().from(CoordinateConverter.CoordType.GPS)
                            .coord(LatLng(point.lat,point.lon)).convert() ?: throw TrafficException(TrafficFailure.DATA)
                        Fix(0,converted.latitude,converted.longitude)
                    }
                    reference to search(reference,query)
                }
                currentCoroutineContext().ensureActive()
                val current=parseObservation(response.second,response.first,now,query)
                val fetched=System.currentTimeMillis()
                TrafficResult(TrafficSnapshot(fetched,current,TrafficPolicy.trend(cached?.result?.snapshot,current,fetched)))
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                val failure=(e as? TrafficException)?.failure ?: TrafficFailure.NETWORK
                Log.i(TAG,"request_failed=$failure")
                TrafficResult(failure=failure,comparisons=(e as? TrafficException)?.comparisons.orEmpty())
            }
            catch(_: LinkageError) { TrafficResult(failure=TrafficFailure.DATA) }
            currentCoroutineContext().ensureActive()
            cache[key]=Entry(result.snapshot?.fetchedAt ?: System.currentTimeMillis(),result)
            while(cache.size>12) cache.remove(cache.keys.first())
            result
        }
    }

    /** Create, submit and destroy on Main; cancellation and timeout always release the SDK search. */
    private suspend fun search(reference: List<Fix>,query: Int): DrivingRouteResult {
        val constrained=searchAttempt(reference,query,true)
        if(constrained.error==SearchResult.ERRORNO.RESULT_NOT_FOUND ||
            (constrained.error==SearchResult.ERRORNO.NO_ERROR && constrained.routeLines.isNullOrEmpty())) {
            Log.i(TAG,"query=$query retry=endpoints_only reason=${constrained.error}")
            return searchAttempt(reference,query,false)
        }
        return constrained
    }
    private suspend fun searchAttempt(reference: List<Fix>,query: Int,withWaypoints: Boolean): DrivingRouteResult {
        val search=RoutePlanSearch.newInstance()
        Log.i(TAG,"query=$query attempt=${if(withWaypoints) "waypoints" else "endpoints_only"}")
        try {
            return withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { continuation ->
                    search.setOnGetRoutePlanResultListener(object: OnGetRoutePlanResultListener {
                        override fun onGetDrivingRouteResult(result: DrivingRouteResult?) {
                            Log.i(TAG,"query=$query callback_sdk=${result?.error} candidates=${result?.routeLines?.size ?: 0} waypoints=$withWaypoints")
                            if(result!=null && continuation.isActive) continuation.resume(result)
                        }
                        override fun onGetWalkingRouteResult(result: WalkingRouteResult?)=Unit
                        override fun onGetTransitRouteResult(result: TransitRouteResult?)=Unit
                        override fun onGetMassTransitRouteResult(result: MassTransitRouteResult?)=Unit
                        override fun onGetIndoorRouteResult(result: IndoorRouteResult?)=Unit
                        override fun onGetBikingRouteResult(result: BikingRouteResult?)=Unit
                        override fun onGetIntegralRouteResult(result: IntegralRouteResult?)=Unit
                    })
                    val anchors=TrafficGeometry.anchors(reference,if(withWaypoints) 5 else 2)
                    fun node(point: Fix)=PlanNode.withLocation(LatLng(point.lat,point.lon))
                    val request=DrivingRoutePlanOption().from(node(anchors.first())).to(node(anchors.last()))
                        .trafficPolicy(DrivingRoutePlanOption.DrivingTrafficPolicy.ROUTE_PATH_AND_TRAFFIC)
                    if(anchors.size>2) request.passBy(anchors.drop(1).dropLast(1).map(::node))
                    if(!search.drivingSearch(request) && continuation.isActive) {
                        continuation.resume(DrivingRouteResult().apply { error=SearchResult.ERRORNO.REQUEST_ERROR })
                    }
                }
            } ?: throw TrafficException(TrafficFailure.NETWORK)
        } finally { search.destroy() }
    }

    private fun digest(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG="RideDiaryTraffic"
        private val queries=AtomicInteger()
        internal fun parseObservation(result: DrivingRouteResult,reference: List<Fix>,time: Long,query: Int=0): TrafficObservation {
            Log.i(TAG,"query=$query sdk=${result.error} candidates=${result.routeLines?.size ?: 0}")
            when(result.error) {
                SearchResult.ERRORNO.NO_ERROR -> Unit
                SearchResult.ERRORNO.KEY_ERROR,SearchResult.ERRORNO.PERMISSION_UNFINISHED,SearchResult.ERRORNO.NO_ADVANCED_PERMISSION -> throw TrafficException(TrafficFailure.AUTH)
                SearchResult.ERRORNO.NETWORK_ERROR,SearchResult.ERRORNO.NETWORK_TIME_OUT,SearchResult.ERRORNO.REQUEST_ERROR -> throw TrafficException(TrafficFailure.NETWORK)
                else -> throw TrafficException(TrafficFailure.DATA)
            }
            val observations=mutableListOf<TrafficObservation>()
            val comparisons=mutableListOf<TrafficRouteComparison>()
            for((index,route) in result.routeLines.orEmpty().take(3).withIndex()) {
                val points=mutableListOf<Fix>(); val bands=mutableListOf<TrafficBand>()
                for(step in route.allStep.orEmpty()) {
                    val path=step.wayPoints.orEmpty()
                    if(points.size+path.size>20_000) throw TrafficException(TrafficFailure.DATA)
                    val fixes=path.map { point ->
                        if(!point.latitude.isFinite() || !point.longitude.isFinite() || point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) throw TrafficException(TrafficFailure.DATA)
                        Fix(0,point.latitude,point.longitude)
                    }
                    points+=fixes
                    val traffic=step.trafficList
                    val geometryDistance=fixes.zipWithNext().sumOf { (a,b)->meters(a,b) }
                    // Missing or misaligned traffic arrays never imply clear traffic.
                    if(traffic==null || traffic.size!=fixes.size-1) {
                        bands+=TrafficBand(0,maxOf(step.distance.toDouble(),geometryDistance))
                    } else {
                        fixes.zipWithNext().forEachIndexed { index,(a,b) -> bands+=TrafficBand(traffic[index],meters(a,b)) }
                        val unknown=(step.distance-geometryDistance).coerceAtLeast(0.0)
                        if(unknown>0) bands+=TrafficBand(0,unknown)
                    }
                }
                val comparison=TrafficGeometry.compare(reference,TrafficGeometry.simplify(points,5.0))
                if(comparison==null) {
                    Log.i(TAG,"query=$query candidate=$index unusable_geometry points=${points.size}")
                    continue
                }
                comparisons+=comparison
                val duration=route.duration.takeIf { it in 1..86_400 }?.let { it*1000L }
                val observation=TrafficObservation(time,duration,bands,comparison)
                Log.i(TAG,String.format(Locale.ROOT,
                    "query=%d candidate=%d saved_m=%.0f planned_m=%.0f start_m=%.0f end_m=%.0f saved50=%.3f planned50=%.3f saved100=%.3f planned100=%.3f ratio=%.3f reversed=%s strict=%s reference=%s traffic_coverage=%.3f duration_s=%d",
                    query,index,comparison.savedLength,comparison.plannedLength,comparison.startOffset,comparison.endOffset,
                    comparison.savedCoverage50,comparison.plannedCoverage50,comparison.savedCoverage100,comparison.plannedCoverage100,
                    comparison.lengthRatio,comparison.reversed,comparison.strictMatch,comparison.usableReference,observation.coverage,route.duration))
                if(comparison.usableReference) observations+=observation
            }
            val selected=observations.maxWithOrNull(compareBy<TrafficObservation> { it.comparison!!.similarity }
                .thenBy { -(it.comparison!!.startOffset+it.comparison.endOffset) })
            if(selected!=null) {
                Log.i(TAG,"query=$query selected_reference=true")
                return selected
            }
            // A successful SDK response with no geometry is a data failure, not evidence of a different route.
            throw TrafficException(if(comparisons.isEmpty()) TrafficFailure.DATA else TrafficFailure.ROUTE_MISMATCH,comparisons)
        }
    }
}

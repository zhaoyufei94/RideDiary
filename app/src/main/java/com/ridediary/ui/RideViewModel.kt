package com.ridediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ridediary.RideDiaryApp
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

class RideViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as RideDiaryApp
    private val repository=app.repository
    val realRides=repository.rides.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val realRoutes=repository.routes.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val demo=MutableStateFlow(false)
    val demoRides=MutableStateFlow<List<RideData>>(emptyList())
    val demoRoutes=MutableStateFlow<List<RouteRecord>>(emptyList())
    val error=MutableStateFlow(false)
    val replaying=MutableStateFlow(false)
    private var replayJob: Job? = null
    private fun task(block: suspend ()->Unit) { viewModelScope.launch { try { app.ready.await(); block() } catch (_: Exception) { error.value=true } } }
    fun toggleDemo() {
        replayJob?.cancel(); replaying.value=false
        demo.value=!demo.value
        if (demo.value) {
            demoRoutes.value=listOf(RouteRecord("demo-route","","WORK","demo-0",RouteRecord.encode(Replay.route().points),100.0,70.0))
            demoRides.value=(1..6).map { day -> fixture("demo-$day",220_000L,day,"COMPLETED") }
        }
    }
    private fun fixture(id: String, at: Long, days: Int, state: String): RideData {
        val start=LocalDate.now().minusDays(days.toLong()).atTime(8,10+days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val points=Replay.points().filter { it.t <= at }
        val match=if(id=="demo-live") Routing.recognize(points,listOf(Replay.route())) else null
        val routeId=if(id=="demo-live") match?.route?.id else "demo-route"
        val record=RideRecord(id,start,ZoneId.systemDefault().id,state,if (state=="COMPLETED") start+at else null,at,start+at,routeId,if(routeId!=null) "WORK" else "OTHER",arrivalConfirmed=state=="COMPLETED")
        return RideData(record,points.mapIndexed { i,f -> PointRecord(id,i,f.t,start+f.t,f.lat,f.lon,f.accuracy,f.speed,null,f.quality) },emptyList())
    }
    fun replay() {
        if (replaying.value) { replayJob?.cancel(); replaying.value=false; return }
        val previous=demoRides.value.firstOrNull { it.ride.id=="demo-live" }
        val begin=if (previous?.ride?.state=="RECORDING") previous.ride.elapsed else 0L
        replaying.value=true
        replayJob=viewModelScope.launch {
            for (at in begin..220_000L step 2_000L) {
                val points=Replay.points().filter { it.t<=at }
                val match=Routing.recognize(points,listOf(Replay.route()))
                val arrived=match!=null && Routing.arrived(points,match,at)
                val data=fixture("demo-live",at,0,if (arrived || at==220_000L) "COMPLETED" else "RECORDING")
                demoRides.value=listOf(data)+demoRides.value.filter { it.ride.id!="demo-live" }
                if(arrived) break
                delay(100)
            }
            replaying.value=false
        }
    }
    fun stopDemo() {
        replayJob?.cancel(); replaying.value=false
        demoRides.value=demoRides.value.map { data ->
            if(data.ride.state!="RECORDING") data else {
                val shape=demoRoutes.value.firstOrNull { it.id==data.ride.routeId }?.route()
                val match=shape?.let { Routing.follow(data.fixes(),it) }
                val partial=shape!=null && (match==null || match.deviated || match.progress<Policy.ARRIVAL_PROGRESS)
                data.copy(ride=data.ride.copy(state="COMPLETED",endUtc=data.ride.startUtc+data.ride.elapsed,
                    excluded=partial,exclusionReason=if(partial) "AUTO_ROUTE_CHECK" else ""))
            }
        }
    }
    fun createRoute(id: String,name: String,direction: String,start: Double,end: Double) {
        if (demo.value) {
            val data=demoRides.value.first { it.ride.id==id }
            val route=RouteRecord("demo-custom-${demoRoutes.value.size}",name,direction,id,RouteRecord.encode(data.sample().points.filter(::good)),start,end)
            demoRoutes.value=demoRoutes.value+route
            demoRides.value=demoRides.value.map { if(it.ride.id==id) it.copy(ride=it.ride.copy(routeId=route.id,direction=direction)) else it }
        } else task { repository.createRoute(id,name,direction,start,end) }
    }
    fun classify(id: String,route: RouteRecord?,direction: String,excluded: Boolean,reason: String) {
        if (demo.value) demoRides.value=demoRides.value.map { if(it.ride.id==id) it.copy(ride=it.ride.copy(routeId=route?.id,direction=route?.direction ?: direction,excluded=excluded,exclusionReason=reason)) else it }
        else task { repository.classify(id,route,direction,excluded,reason) }
    }
    fun boundaries(id: String,start: Long,end: Long) {
        if (demo.value) demoRides.value=demoRides.value.map { if(it.ride.id==id) it.copy(ride=it.ride.copy(boundaryStart=start,boundaryEnd=end)) else it }
        else task { repository.boundaries(id,start,end) }
    }
}

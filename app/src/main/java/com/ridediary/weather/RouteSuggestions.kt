package com.ridediary.weather

import com.ridediary.core.*
import com.ridediary.data.*
import com.ridediary.calendar.WorkCalendarState
import java.time.*

/** Monday is bit 0, Sunday bit 6. These are weekly plans, not a public-holiday calendar. */
object TravelPlans {
    const val WEEKDAYS=31
    const val EVERY_DAY=127
    fun includes(days: Int,date: LocalDate)=days and (1 shl (date.dayOfWeek.value-1))!=0
    fun defaultMinute(direction: String)=if(direction=="HOME") 18*60 else 8*60
    fun learnedMinute(history: List<Sample>,day: LocalDate,zone: ZoneId,calendar: WorkCalendarState=WorkCalendarState()): Int? {
        val sameDayType=history.filter {
            val date=Instant.ofEpochMilli(it.startUtc).atZone(zone).toLocalDate()
            (calendar.workday(date) ?: (date.dayOfWeek.value<=5))==(calendar.workday(day) ?: (day.dayOfWeek.value<=5))
        }
        val timed=if(sameDayType.size>=3) sameDayType else history
        val minutes=timed.map { Instant.ofEpochMilli(it.startUtc).atZone(zone).let { t -> t.hour*60+t.minute } }
        val anchor=minutes.firstOrNull() ?: return null
        return (median(minutes.map { ((it-anchor+2160)%1440-720).toLong() })!!.toInt()+anchor+1440)%1440
    }
    fun history(rides: List<RideData>,routes: List<RouteRecord>,now: Long): List<Sample> {
        val ids=routes.map { it.id }.toSet()
        return rides.filter { it.ride.state=="COMPLETED" && !it.ride.excluded && it.ride.routeId in ids &&
            it.ride.startUtc in (now-60*86_400_000L)..now }.map { it.sample() }.filter(Statistics::valid).sortedByDescending { it.startUtc }
    }
}
data class RouteSuggestion(val route: RouteRecord,val departure: Long,val duration: Long?,val count: Int,val date: LocalDate)
data class PlannedRoutes(val today: List<RouteSuggestion>,val tomorrow: List<RouteSuggestion>)
object RouteSuggestions {
    /** Explicit dates avoid rolling an expired departure into the wrong daily section. */
    fun planned(rides: List<RideData>,routes: List<RouteRecord>,now: Long,zone: ZoneId=ZoneId.systemDefault(),calendar: WorkCalendarState=WorkCalendarState()): PlannedRoutes {
        val history=TravelPlans.history(rides,routes,now)
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun day(date: LocalDate)=routeGroups(routes).flatMap { group -> listOf("WORK","HOME").mapNotNull { direction ->
            val variants=group.direction(direction)
            if(variants.isEmpty()) return@mapNotNull null
            val ids=variants.map { it.id }.toSet()
            val samples=history.filter { it.routeId in ids && it.direction==direction }.take(20)
            val reference=variants.maxWith(compareBy<RouteRecord> { r -> samples.count { it.routeId==r.id } }
                .thenBy { r -> samples.firstOrNull { it.routeId==r.id }?.startUtc ?: 0 })
            val scheduled=if(reference.planMode=="WORKDAYS") calendar.workday(date)==true else TravelPlans.includes(reference.planDays,date)
            if(!scheduled) return@mapNotNull null
            val minute=reference.planMinute ?: TravelPlans.learnedMinute(samples,date,zone,calendar) ?: TravelPlans.defaultMinute(direction)
            val departure=date.atTime(minute/60,minute%60).atZone(zone).toInstant().toEpochMilli()
            if(departure<=now) return@mapNotNull null
            RouteSuggestion(reference,departure,median(samples.map { it.metrics.elapsed }),samples.size,date)
        } }.sortedWith(compareBy<RouteSuggestion> { it.departure }.thenByDescending { it.count }.thenBy { it.route.name })
        return PlannedRoutes(day(today),day(today.plusDays(1)))
    }
}

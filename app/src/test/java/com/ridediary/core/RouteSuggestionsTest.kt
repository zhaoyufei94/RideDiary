package com.ridediary.core

import com.ridediary.calendar.HolidayYear
import com.ridediary.calendar.WorkCalendarState
import com.ridediary.data.*
import com.ridediary.weather.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RouteSuggestionsTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val monday=LocalDate.of(2026,10,26)
    private val calendar=WorkCalendarState(mapOf(2026 to HolidayYear(2026,emptyMap(),emptyList())),loading=false)
    private fun time(day: LocalDate,minute: Int)=day.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    private fun route(direction: String="WORK",minute: Int?=8*60)=RouteRecord(direction,"Office",direction,"source","[]",100.0,70.0,
        groupId="office",planMinute=minute)
    private fun plans(routes: List<RouteRecord>,now: Long,rides: List<RideData> = emptyList())=
        RouteSuggestions.planned(rides,routes,now,zone,calendar)

    @Test fun recommendationIncludesDepartureAndEntireFollowingHour() {
        val route=route(); val departure=time(monday,8*60)
        for(offset in listOf(-1L,0L,1L,30*60_000L,3_600_000L)) {
            val result=plans(listOf(route),departure+offset)
            assertEquals("offset=$offset",departure,result.today.single().departure)
            assertEquals(monday,result.today.single().date)
            assertEquals(time(monday.plusDays(1),8*60),result.tomorrow.single().departure)
        }
        assertTrue(plans(listOf(route),departure+3_600_001).today.isEmpty())
    }

    @Test fun directionsExpireIndependentlyAndDisabledPlansStayHidden() {
        val work=route(); val home=route("HOME",18*60)
        assertEquals(listOf("WORK","HOME"),plans(listOf(home,work),time(monday,8*60+30)).today.map { it.route.direction })
        assertEquals(listOf("HOME"),plans(listOf(home,work),time(monday,9*60)+1).today.map { it.route.direction })
        val disabled=work.copy(planMode="CUSTOM",planDays=0)
        val result=plans(listOf(disabled),time(monday,8*60+30))
        assertTrue(result.today.isEmpty()); assertTrue(result.tomorrow.isEmpty())
    }

    @Test fun gracePeriodDoesNotOverrideWorkingDaysOrCustomWeekdays() {
        val sunday=monday.minusDays(1)
        assertTrue(plans(listOf(route()),time(sunday,8*60+30)).today.isEmpty())
        val custom=route().copy(planMode="CUSTOM",planDays=1 shl 6)
        assertEquals(sunday,plans(listOf(custom),time(sunday,8*60+30)).today.single().date)
        assertTrue(plans(listOf(custom),time(monday,8*60+30)).today.isEmpty())
        val holiday=calendar.copy(years=mapOf(2026 to HolidayYear(2026,mapOf(monday to true),emptyList())))
        assertTrue(RouteSuggestions.planned(emptyList(),listOf(route()),time(monday,8*60+30),zone,holiday).today.isEmpty())
    }

    @Test fun lateNightRecommendationSurvivesMidnightWithOriginalDeparture() {
        val route=route("HOME",23*60+30)
        val departure=time(monday,23*60+30)
        val result=plans(listOf(route),time(monday.plusDays(1),15))
        assertEquals(listOf(departure,time(monday.plusDays(1),23*60+30)),result.today.map { it.departure })
        assertEquals(monday,result.today.first().date)
        assertEquals(departure,plans(listOf(route),departure+3_600_000).today.first().departure)
        assertEquals(1,plans(listOf(route),departure+3_600_001).today.size)
    }

    @Test fun learnedAndDefaultDepartureTimesReceiveTheSameGracePeriod() {
        val route=route(minute=null)
        assertEquals(time(monday,8*60),plans(listOf(route),time(monday,8*60+30)).today.single().departure)
        val start=time(monday.minusDays(3),7*60+45)
        val ride=RideData(RideRecord("history",start,zone.id,state="COMPLETED",elapsed=220_000,routeId=route.id,direction="WORK"),
            Replay.points().mapIndexed { index,p -> PointRecord("history",index,p.t,start+p.t,p.lat,p.lon,p.accuracy,p.speed,null,p.quality) },emptyList())
        val result=plans(listOf(route),time(monday,8*60+30),listOf(ride))
        assertEquals(time(monday,7*60+45),result.today.single().departure)
        assertEquals(1,result.today.single().count)
        assertTrue(plans(listOf(route),time(monday,8*60+45)+1,listOf(ride)).today.isEmpty())
    }
}

package com.ridediary.calendar

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import java.time.LocalDate

enum class CalendarProblem { UNAVAILABLE }
data class HolidayYear(val year: Int,val daysOff: Map<LocalDate,Boolean>,val papers: List<String>)
data class WorkCalendarState(val years: Map<Int,HolidayYear> = emptyMap(),val problems: Map<Int,CalendarProblem> = emptyMap(),val loading: Boolean=true) {
    fun workday(date: LocalDate): Boolean? {
        // The next year's notice may override the end of December in this year.
        years[date.year+1]?.daysOff?.get(date)?.let { return !it }
        val current=years[date.year] ?: return null
        current.daysOff[date]?.let { return !it }
        return date.dayOfWeek.value<=5
    }
    fun missingYear(date: LocalDate)=date.year
}

/** Annual notices ship in the APK; this class has no network or runtime update path. */
class WorkCalendarClient(private val context: Context) {
    suspend fun load(years: Set<Int>): WorkCalendarState=withContext(Dispatchers.IO) {
        val known=mutableMapOf<Int,HolidayYear>(); val problems=mutableMapOf<Int,CalendarProblem>()
        years.forEach { year ->
            val parsed=runCatching {
                val raw=context.assets.open("work-calendar/$year.json").bufferedReader().use { JSONObject(it.readText()) }
                require(raw.getInt("year")==year)
                val papers=raw.getJSONArray("papers"); val days=raw.getJSONArray("days")
                require(papers.length()>0 && days.length() in 15..150)
                val sources=(0 until papers.length()).map { papers.getString(it) }.onEach {
                    val uri=URI(it)
                    require(uri.scheme=="https" && (uri.host=="gov.cn" || uri.host?.endsWith(".gov.cn")==true))
                }
                val overrides=linkedMapOf<LocalDate,Boolean>()
                for(index in 0 until days.length()) {
                    val item=days.getJSONObject(index); val date=LocalDate.parse(item.getString("date"))
                    require(date.year in (year-1)..year && date !in overrides && item.get("isOffDay") is Boolean)
                    overrides[date]=item.getBoolean("isOffDay")
                }
                HolidayYear(year,overrides,sources)
            }.getOrNull()
            if(parsed!=null) known[year]=parsed else problems[year]=CalendarProblem.UNAVAILABLE
        }
        WorkCalendarState(known,problems,loading=false)
    }
}

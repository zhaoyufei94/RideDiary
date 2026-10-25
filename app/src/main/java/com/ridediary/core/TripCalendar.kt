package com.ridediary.core

import java.time.LocalDate
import java.time.YearMonth

/** The journal includes every recorded trip, including interrupted and excluded trips. */
object TripCalendar {
    data class Month(val samples: List<Sample>,val dailyKilometers: Map<LocalDate,Double>) {
        val kilometers get()=dailyKilometers.values.sum()
    }

    fun month(all: List<Sample>,month: YearMonth): Month {
        val dated=all.map { it to Statistics.date(it) }.filter { YearMonth.from(it.second)==month }
        return Month(dated.map { it.first }.sortedByDescending { it.startUtc },
            dated.groupBy { it.second }.mapValues { (_,trips) -> trips.sumOf { it.first.metrics.distance }/1000 })
    }

    /** Monday-first, six-row grid; adjacent months stay blank. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val offset=month.atDay(1).dayOfWeek.value-1
        return List(42) { index ->
            val day=index-offset+1
            if(day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }
}

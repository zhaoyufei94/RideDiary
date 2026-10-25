package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class TripCalendarTest {
    private fun sample(id: String,start: String,meters: Double=1000.0,zone: String="Asia/Shanghai") =
        Sample(id,Instant.parse(start).toEpochMilli(),zone,"COMPLETED",null,"OTHER",false,
            Metrics(60_000,meters,emptyList()),emptyList())

    @Test fun dailyTotalCombinesTripsAndRetainsEveryJournalState() {
        val all=listOf(
            sample("morning","2026-10-01T00:00:00Z",1250.0),
            sample("evening","2026-10-01T10:00:00Z",2750.0).copy(excluded=true),
            sample("interrupted","2026-10-02T00:00:00Z",500.0).copy(state="INTERRUPTED"),
            sample("recording","2026-10-03T00:00:00Z",0.0).copy(state="RECORDING"),
            sample("previous","2026-09-15T00:00:00Z"))
        val month=TripCalendar.month(all,YearMonth.of(2026,10))
        assertEquals(listOf("recording","interrupted","evening","morning"),month.samples.map { it.id })
        assertEquals(4.0,month.dailyKilometers.getValue(LocalDate.of(2026,10,1)),0.00001)
        assertEquals(4.5,month.kilometers,0.00001)
        assertTrue(month.dailyKilometers.containsKey(LocalDate.of(2026,10,3)))
        assertFalse(month.dailyKilometers.containsKey(LocalDate.of(2026,10,4)))
    }

    @Test fun monthBoundariesUseEachTripsDepartureTimeZoneEvenWhenEndingNextMonth() {
        val shanghai=sample("shanghai","2026-09-30T16:00:00Z")
        val losAngeles=shanghai.copy(id="la",zone="America/Los_Angeles")
        val overnight=sample("overnight","2026-10-31T15:59:00Z").copy(
            endUtc=Instant.parse("2026-10-31T17:00:00Z").toEpochMilli())
        val after=sample("november","2026-10-31T16:00:00Z")
        val month=TripCalendar.month(listOf(shanghai,losAngeles,overnight,after),YearMonth.of(2026,10))
        assertEquals(listOf("overnight","shanghai"),month.samples.map { it.id })
        assertEquals(setOf(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)),month.dailyKilometers.keys)
    }

    @Test fun removingOrTrimmingATripRebuildsBothListAndDailyTotals() {
        val before=sample("trimmed","2026-09-30T15:59:00Z",4000.0)
        val after=before.copy(startUtc=before.startUtc+120_000,metrics=before.metrics.copy(distance=1500.0))
        val september=YearMonth.of(2026,9); val october=september.plusMonths(1)
        assertEquals(4.0,TripCalendar.month(listOf(before),september).kilometers,0.00001)
        assertTrue(TripCalendar.month(listOf(after),september).samples.isEmpty())
        assertEquals(1.5,TripCalendar.month(listOf(after),october).kilometers,0.00001)
        val empty=TripCalendar.month(emptyList(),october)
        assertTrue(empty.samples.isEmpty())
        assertTrue(empty.dailyKilometers.isEmpty())
        assertEquals(0.0,empty.kilometers,0.0)
    }

    @Test fun gridAlignsLeapDaySundayStartsAndSixWeekMonths() {
        val leap=TripCalendar.cells(YearMonth.of(2024,2))
        assertEquals(42,leap.size)
        assertNull(leap[2])
        assertEquals(LocalDate.of(2024,2,1),leap[3])
        assertEquals(LocalDate.of(2024,2,29),leap[31])
        assertNull(leap[32])
        val sixWeeks=TripCalendar.cells(YearMonth.of(2026,3))
        assertEquals(LocalDate.of(2026,3,1),sixWeeks[6])
        assertEquals(LocalDate.of(2026,3,31),sixWeeks[36])
        assertEquals(31,sixWeeks.count { it!=null })
        assertEquals(LocalDate.of(2027,2,1),TripCalendar.cells(YearMonth.of(2027,2)).first())
    }
}

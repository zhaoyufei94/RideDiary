package com.ridediary.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.Sample
import com.ridediary.core.TripCalendar
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

internal const val JOURNAL_CENTER_PAGE=Int.MAX_VALUE/2

internal fun journalMonth(anchor: YearMonth,page: Int): YearMonth =
    anchor.plusMonths(page.toLong()-JOURNAL_CENTER_PAGE)

private fun calendarRowCount(month: YearMonth) =
    (month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7

@Composable internal fun TripMonthCalendar(anchor: YearMonth,pager: PagerState,samples: List<Sample>) {
    val scope=rememberCoroutineScope()
    val month=journalMonth(anchor,pager.settledPage)
    val locale=LocalConfiguration.current.locales[0]
    val today=LocalDate.now()
    // Grow cells with accessibility font size while keeping seven equal-width columns.
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cellHeight=(46*fontScale).dp
    // While swiping, make room for every visible month's last week; compact again when settled.
    val gridRows by remember(anchor,pager) { derivedStateOf {
        if(!pager.isScrollInProgress) calendarRowCount(journalMonth(anchor,pager.settledPage))
        else pager.layoutInfo.visiblePagesInfo.maxOfOrNull { calendarRowCount(journalMonth(anchor,it.index)) }
            ?: calendarRowCount(journalMonth(anchor,pager.currentPage))
    } }
    DataCard(Modifier.testTag("trip-calendar").animateContentSize(),padding=8) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                HeaderIconButton(Glyph.LEFT,stringResource(R.string.previous_month)) {
                    scope.launch { pager.animateScrollToPage(pager.settledPage-1) }
                }
                Text(stringResource(R.string.month_label,month.year,month.monthValue),
                    Modifier.weight(1f).testTag("trip-calendar-month"),fontSize=16.sp,
                    fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                HeaderIconButton(Glyph.RIGHT,stringResource(R.string.next_month)) {
                    scope.launch { pager.animateScrollToPage(pager.settledPage+1) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(day.getDisplayName(TextStyle.SHORT,locale),Modifier.weight(1f),
                        fontSize=10.sp,color=Muted,textAlign=TextAlign.Center)
                }
            }
            HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth().height(cellHeight*gridRows).testTag("trip-calendar-pager"),
                key={ it }) { page ->
                val pageMonth=journalMonth(anchor,page)
                val summary=remember(samples,pageMonth) { TripCalendar.month(samples,pageMonth) }
                val cells=remember(pageMonth) { TripCalendar.cells(pageMonth) }
                val maximum=summary.dailyKilometers.values.maxOrNull() ?: 0.0
                Column(Modifier.fillMaxSize().testTag("trip-calendar-grid-$pageMonth")) {
                    cells.chunked(7).filter { week -> week.any { it!=null } }.forEach { week ->
                        Row(Modifier.fillMaxWidth().height(cellHeight)) {
                            week.forEach { date ->
                                val km=summary.dailyKilometers[date]
                                val label=if(date==null) "" else if(km==null) stringResource(R.string.calendar_day_empty,date.toString())
                                    else stringResource(R.string.calendar_day_distance,date.toString(),decimal(km))
                                Column(Modifier.weight(1f).fillMaxHeight()
                                    .then(if(date==null) Modifier else Modifier.testTag("trip-calendar-day-$date"))
                                    .clearAndSetSemantics { contentDescription=label },
                                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    if(date!=null) {
                                        Text(date.dayOfMonth.toString(),fontSize=11.sp,lineHeight=15.sp,
                                            fontWeight=if(date==today) FontWeight.Bold else FontWeight.Normal,color=if(date==today) Ink else Muted)
                                        if(km!=null) {
                                            val intensity=if(maximum>0) (km/maximum).toFloat().coerceIn(0f,1f) else 0f
                                            val circleColor=lerp(Accent,Ink,intensity)
                                            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                                                val diameter=minOf(maxWidth-2.dp,(28*fontScale).dp)
                                                Box(Modifier.size(diameter).background(circleColor,CircleShape),contentAlignment=Alignment.Center) {
                                                    // Fit unusually long distances without truncating the number.
                                                    val value=decimal(km)
                                                    val fontSize=minOf(10f,diameter.value/(value.length*.65f*fontScale)).sp
                                                    Text(value,fontSize=fontSize,lineHeight=fontSize,fontWeight=FontWeight.Medium,
                                                        color=if(circleColor.luminance()>.179f) Color.Black else Color.White,maxLines=1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

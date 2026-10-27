package com.ridediary.core

import java.util.Locale
import java.util.UUID

/** A named commute owns independent outward/return geometries, never a fabricated reversal. */
object RouteIdentity {
    fun name(value: String): String {
        val trimmed=value.trim().replace(Regex("\\s+")," ")
        val base=trimmed.replace(Regex("\\s*[-·/（(]?\\s*(上班|下班|去程|返程|to work|to home)\\s*[）)]?$",RegexOption.IGNORE_CASE),"").trim()
        return base.ifBlank { trimmed }
    }
    fun key(value: String)=name(value).lowercase(Locale.ROOT)
    fun legacyGroup(value: String)="group-"+UUID.nameUUIDFromBytes(key(value).toByteArray(Charsets.UTF_8))
    fun opposite(direction: String)=if(direction=="WORK") "HOME" else "WORK"
    fun reversed(route: RouteShape): RouteShape {
        val end=route.points.lastOrNull()?.t ?: 0
        return route.copy(id="reverse:${route.id}",direction=opposite(route.direction),
            points=route.points.indices.reversed().map { index -> route.points[index].copy(t=end-route.points[index].t,
                breakBefore=route.points.getOrNull(index+1)?.breakBefore==true) },startRadius=route.endRadius,endRadius=route.startRadius)
    }
    data class Report(val candidates: List<Routing.Candidate>,val match: Match?,val ambiguous: Boolean)
    fun report(points: List<Fix>,routes: List<RouteShape>,groups: Map<String,String>): Report {
        val comparator=compareByDescending<Routing.Candidate> { it.overlap }
            .thenBy { it.crossTrack }.thenByDescending { it.matchedDistance }.thenBy { it.route.id }
        val candidates=routes.mapNotNull { Routing.evaluate(points,it) }
            .groupBy { (groups[it.route.id] ?: it.route.id) to it.route.direction }
            .values.map { it.sortedWith(comparator).first() }.sortedWith(comparator)
        val eligible=candidates.filter { it.eligible && !it.match.deviated }
        val first=eligible.firstOrNull(); val second=eligible.getOrNull(1)
        val ambiguous=first!=null && second!=null && first.overlap-second.overlap<0.08 &&
            kotlin.math.abs(first.crossTrack-second.crossTrack)<Policy.MATCH_MARGIN_M
        return Report(candidates,if(ambiguous) null else first?.match,ambiguous)
    }
    fun match(points: List<Fix>,routes: List<RouteShape>,groups: Map<String,String>): Match? = report(points,routes,groups).match
}

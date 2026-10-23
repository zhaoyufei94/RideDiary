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
            points=route.points.asReversed().map { it.copy(t=end-it.t) },startRadius=route.endRadius,endRadius=route.startRadius)
    }
    fun match(points: List<Fix>,routes: List<RouteShape>,groups: Map<String,String>): Match? {
        val candidates=routes.mapNotNull { Routing.follow(points,it) }.filterNot { it.deviated }
            .groupBy { (groups[it.route.id] ?: it.route.id) to it.route.direction }
            .values.map { it.minBy { match -> match.crossTrack } }.sortedBy { it.crossTrack }
        if(candidates.size>1 && candidates[1].crossTrack-candidates[0].crossTrack<Policy.MATCH_MARGIN_M) return null
        return candidates.firstOrNull()
    }
}

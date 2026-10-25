package com.ridediary.data

import com.ridediary.core.RouteIdentity
import com.ridediary.core.Match

/** Ephemeral diagnostics, never written into historical rides. */
data class LiveRouteMatch(val rideId: String,val report: RouteIdentity.Report,
    val computedElapsed: Long,val computedRealtime: Long,val intervalMs: Long,val computationMs: Long,val boundRouteId: String?) {
    /** A saved association or a chosen plan is not evidence of a current route match. */
    val confirmedMatch: Match? get() = (if(boundRouteId==null) report.match else
        report.candidates.firstOrNull { it.route.id==boundRouteId }?.takeIf { it.eligible }?.match)
        ?.takeUnless { it.deviated }
}

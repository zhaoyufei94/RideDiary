package com.ridediary.data

import com.ridediary.core.RouteIdentity

/** Ephemeral diagnostics, never written into historical rides. */
data class LiveRouteMatch(val rideId: String,val report: RouteIdentity.Report,
    val computedElapsed: Long,val computedRealtime: Long,val intervalMs: Long,val computationMs: Long,val boundRouteId: String?)

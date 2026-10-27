package com.ridediary.core

/** Pure deterministic analysis: raw fixes are never removed. Intervals use elapsed realtime. */
object Analyzer {
    fun analyze(points: List<Fix>, elapsed: Long): Metrics {
        val end = elapsed.coerceAtLeast(0)
        val raw = mutableListOf<Segment>()
        var previous: Fix? = null
        var lastTime = 0L
        var stopped = false
        var candidate = -1
        for (f in points) {
            if (f.t < lastTime || f.t > end) continue
            val p = previous
            val dt = f.t-lastTime
            val distance = if (p != null) meters(p,f) else 0.0
            val valid = p != null && !f.breakBefore && good(p) && good(f) && dt in 1..Policy.MAX_GAP_MS && distance / (dt/1000.0) <= Policy.MAX_SPEED
            val speed = if (!valid) null else f.speed?.takeIf { it.isFinite() && it >= 0 && it <= Policy.MAX_SPEED && (f.speedAccuracy == null || f.speedAccuracy <= 2.0) }
                ?: if (distance > (p!!.accuracy + f.accuracy) / 2) distance / (dt/1000.0) else null
            val motion: Motion
            if (speed == null) {
                motion = Motion.UNKNOWN; stopped = false; candidate = -1
            } else if (speed < if (stopped) Policy.STOP_EXIT else Policy.STOP_ENTER) {
                if (candidate < 0) candidate = raw.size
                if (stopped || f.t - (raw.getOrNull(candidate)?.from ?: lastTime) >= Policy.STOP_CONFIRM_MS) {
                    stopped = true
                    for (i in candidate until raw.size) raw[i] = raw[i].copy(motion = Motion.WAITING, distance = 0.0)
                    motion = Motion.WAITING
                } else motion = Motion.MOVING
            } else {
                stopped = false; candidate = -1; motion = Motion.MOVING
            }
            if (dt > 0) raw += Segment(lastTime, f.t, motion,
                if (valid && motion == Motion.MOVING) distance else 0.0)
            previous = f; lastTime = f.t
        }
        // No fix is evidence of neither movement nor waiting.
        if (lastTime < end) raw += Segment(lastTime,end,Motion.UNKNOWN)
        val merged = mutableListOf<Segment>()
        for (s in raw) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.motion == s.motion && prev.to == s.from) merged[merged.lastIndex] = prev.copy(to=s.to,distance=prev.distance+s.distance)
            else merged += s
        }
        return Metrics(end, merged.sumOf { it.distance }, merged)
    }
}

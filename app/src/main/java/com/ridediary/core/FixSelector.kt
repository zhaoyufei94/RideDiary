package com.ridediary.core

/** Select a single usable stream from concurrent GNSS/network callbacks. */
class FixSelector {
    private var lastGps: Long? = null
    private var lastNetwork: Long? = null
    private var lastEmitted: Long? = null
    private var lastWasGood=false
    fun accept(gps: Boolean, now: Long, fix: Fix): Boolean {
        val reliable=good(fix)
        fun fresh(time: Long?)=time!=null && now-time in 0 until 10_000
        if(gps && reliable) lastGps=now
        if(!gps && fresh(lastGps)) return false
        if(!gps && reliable) lastNetwork=now
        if(gps && !reliable && fresh(lastNetwork)) return false
        val previous=lastEmitted
        if(previous!=null && (fix.t<=previous || (fix.t-previous<1_000 && (!reliable || lastWasGood)))) return false
        lastEmitted=fix.t; lastWasGood=reliable
        return true
    }
}

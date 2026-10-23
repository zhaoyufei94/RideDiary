package com.ridediary.core

/** Confined to the service main thread. Wakeups may coalesce; raw fixes never do. */
class RecordingBuffer {
    data class Cutoff(val elapsed: Long,val utc: Long,val discard: Boolean)
    data class Batch(val fixes: List<Fix>,val elapsed: Long,val utc: Long,val cutoff: Cutoff?)
    private val pending=ArrayDeque<Fix>()
    private var elapsed=0L
    private var utc=0L
    var cutoff: Cutoff?=null
        private set
    fun checkpoint(elapsed: Long,utc: Long,fix: Fix?=null) {
        if(cutoff!=null) return
        if(fix!=null) pending.addLast(fix)
        this.elapsed=elapsed; this.utc=utc
    }
    fun stop(elapsed: Long,utc: Long,discard: Boolean) {
        if(cutoff==null) cutoff=Cutoff(elapsed,utc,discard)
    }
    fun batch(): Batch {
        val stop=cutoff
        return Batch(pending.toList(),stop?.elapsed ?: elapsed,stop?.utc ?: utc,stop)
    }
    fun acknowledge(batch: Batch) { repeat(batch.fixes.size) { pending.removeFirst() } }
}

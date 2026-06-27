package com.unciv.logic.automation

import com.unciv.UncivGame
import com.unciv.utils.Log
import com.badlogic.gdx.utils.IntArray as GdxIntArray
import com.unciv.utils.fold
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.sqrt
import kotlin.time.TimeSource.Monotonic.markNow

class Timers {
    val spanTimesInMicroseconds = HashMap<String, GdxIntArray>()
    var timingEnabledTimestamp = NO_TIME_POINT
    var startGcCount = 0

    @Pure
    @Suppress("purity")
    fun startTiming() {
        if (!Log.shouldLog()) return
        synchronized (spanTimesInMicroseconds) {
            for (times in spanTimesInMicroseconds.values)
                times.clear()
        }
        startGcCount = UncivGame.Current?.getGcCount() ?: 0
        timingEnabledTimestamp = markNow()
    }

    @Readonly
    @Suppress("purity")
    fun endTiming() {
        if (timingEnabledTimestamp == TIMING_DISABLED) return
        val automationEndTime = markNow()
        val totalTimingDuration = (automationEndTime - timingEnabledTimestamp).inWholeMicroseconds
        synchronized(spanTimesInMicroseconds) {
            if (spanTimesInMicroseconds.isEmpty()) return
            val gcCount = (UncivGame.Current?.getGcCount() ?: 0) - startGcCount
            val gcCountPerTurn = gcCount.toDouble() / (spanTimesInMicroseconds["GameInfo.nextTurn"]?.size ?: 1)
            Log.debug("Timing took $totalTimingDuration, with $gcCountPerTurn GCs per turn on average")
            Log.debug("Span Timing:\tname\tcount\tmean\t95CiMin\t95CiMax\tsum\tpercent\tstddev\tstderr\tvariance\tmin\tp50\tp90\tp95\tp99\tmax")
            val order = spanTimesInMicroseconds.keys.sorted()
            for (name in order) {
                val times = spanTimesInMicroseconds[name]!!
                synchronized(times) {
                    if (times.isEmpty) continue
                    times.sort()
                    val sqrtCount = sqrt(times.size.toDouble())
                    // Long can sum up to 584,000 years as microseconds, so no risk of overflow
                    val sum = times.fold(0L, Long::plus)
                    val percent = sum.toDouble() / totalTimingDuration
                    val avg = sum.toDouble() / times.size
                    val sumOfSquaredDifferences = times.fold(0.0) { acc, value ->
                        val deltaMean = value - avg
                        acc + deltaMean * deltaMean
                    }
                    val variance = sumOfSquaredDifferences / times.size
                    val stdDev = sqrt(variance)
                    val stdErrMean = stdDev / sqrtCount;
                    val confidence95 = 1.96 * stdDev / sqrtCount;
                    val min = times[0]
                    val p50 = interpolate(times, 0.50)
                    val p90 = interpolate(times, 0.90)
                    val p95 = interpolate(times, 0.95)
                    val p99 = interpolate(times, 0.99)
                    val max = times[times.size - 1]
                    Log.debug("Span Timing(µs):\t$name\t${times.size}\t$avg\t${avg - confidence95}\t${avg + confidence95}\t$sum\t$percent\t$stdDev\t$stdErrMean\t$variance\t$min\t$p50\t$p90\t$p95\t$p99\t$max")
                }
            }
            timingEnabledTimestamp = TIMING_DISABLED
        }
    }

    @Readonly
    private fun interpolate(times: GdxIntArray, percentile: Double): Double {
        val percentileIdxDbl = percentile * times.size 
        val percentileInterpolationPercent = percentileIdxDbl.rem(1.0)
        val leftIdx = percentileIdxDbl.toInt()
        if (leftIdx >= times.size - 1) return times[times.size - 1].toDouble()
        val interpolation = (times[leftIdx + 1] - times[leftIdx]) * percentileInterpolationPercent
        return times[leftIdx] + interpolation        
    }

    @Pure
    @Suppress("purity")
    inline fun <T> timeThis(name:String, block: ()->T): T {
        val times = if (timingEnabledTimestamp == TIMING_DISABLED) NO_TIME_DATA
            else synchronized (spanTimesInMicroseconds) { spanTimesInMicroseconds.getOrPut(name) { GdxIntArray(1024) } }
        val spanStartTime = if (timingEnabledTimestamp == TIMING_DISABLED) NO_TIME_POINT else markNow() // occurs outside of synchronized block
        val r = block()
        if (timingEnabledTimestamp != TIMING_DISABLED) {
            val endTime = markNow() // occurs outside of synchronized block
            // up to 35 minutes per span
            synchronized(times) { times.add((endTime - spanStartTime).inWholeMicroseconds.toInt()) }
        }
        return r
    }
        
    companion object {
        val NO_TIME_POINT = markNow()
        val TIMING_DISABLED = NO_TIME_POINT
        val NO_TIME_DATA = GdxIntArray(0)
        val NO_SPAN = AutoCloseable { }
        
        val singleton = Timers()

        @Pure
        inline fun <T> timeThis(name:String, block: ()->T) = singleton.timeThis(name, block) 
    }
}

package com.unciv.testing

object MeasureMemory {
    /** Force GC and return current JVM heap usage in bytes. */
    fun snapshot(): Long {
        System.gc()
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    /** Measures net heap allocated by [block]. Returns Pair(result, allocatedBytes). */
    fun <T> measure(block: () -> T): Pair<T, Long> {
        val before = snapshot()
        val result = block()
        val after = snapshot()
        return Pair(result, after - before)
    }

    fun Long.toMB(): String = "%.2f MB".format(this / (1024.0 * 1024.0))
}
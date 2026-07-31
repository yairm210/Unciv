package com.unciv.testing

/**
 * Logs an estimated net JVM heap delta (forced `System.gc()` before/after) around the
 * annotated test, or every test in the class if put on the class.
 *
 * Caveat: `System.gc()` is a hint, not a guarantee of a full collection (see JLS/Javadoc),
 * and concurrent GC algorithms collect incrementally. Other threads allocate concurrently
 * too. Treat the number as a rough estimate for spotting large regressions, not as exact.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MeasureMemory

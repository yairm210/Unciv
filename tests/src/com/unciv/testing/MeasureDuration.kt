package com.unciv.testing

/**
 * Logs the duration of the annotated test, or every test in the class if put on the class.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MeasureDuration

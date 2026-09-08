package com.unciv.testing

enum class RedirectPolicy { Show, ShowOnFailure, Discard }

/**
 *  This annotation controls the [BaseTestRunner] feature to redirect and discard console output from tests.
 *
 *  Settings:
 *  * [RedirectPolicy.Discard]: Output is discarded.
 *  * [RedirectPolicy.ShowOnFailure] (**default**): Collected output is written to the console only when the test fails).
 *  * [RedirectPolicy.Show]: Do not redirect, show console output immediately.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RedirectOutput(val policy: RedirectPolicy)

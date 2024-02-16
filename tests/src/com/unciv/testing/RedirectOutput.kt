package com.unciv.testing

enum class RedirectPolicy { Show, ShowOnFailure, Discard }

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
/**
 *  This annotation controls the [GdxTestRunner] feature to redirect and discard console output from tests.
 *
 *  Settings:
 *  * [RedirectPolicy.Discard]: Output is discarded.
 *  * [RedirectPolicy.ShowOnFailure] (**default**): Collected output is written to the console only when the test fails).
 *  * [RedirectPolicy.Show]: Do not redirect, show console output immediately.
 */
annotation class RedirectOutput(val policy: RedirectPolicy)

package org.junit

import org.hamcrest.Matcher

object Assume {
    @JvmStatic
    fun assumeTrue(condition: Boolean) {
        if (!condition) throw AssumptionViolatedException("assumeTrue failed")
    }

    @JvmStatic
    fun <T> assumeThat(actual: T, matcher: Matcher<in T>) {
        if (!matcher.matches(actual)) {
            throw AssumptionViolatedException("assumeThat failed: expected ${matcher.describe()} but was $actual")
        }
    }
}

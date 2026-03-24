package org.junit

import org.hamcrest.Matcher

object Assume {
    @JvmStatic
    fun assumeTrue(condition: Boolean) {
        if (!condition) return
    }

    @JvmStatic
    fun assumeThat(actual: Any?, matcher: Matcher<Any?>) {
        if (!matcher.matches(actual)) return
    }
}

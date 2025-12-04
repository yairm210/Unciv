package com.unciv.testing

import org.junit.Assert


/** @property input Input to your testee function
 *  @property expected Expected output of your testee function
 *  @property actual Not provided by you, filled by [runTestParcours]
 */
class TestCase<T, R>(val input: T, val expected: R, var actual: R? = null) {
    private val quote get() = if (input is String) "\"" else ""
    override fun toString(): String = "Input: $quote$input$quote, Expected: $expected, Actual: $actual"
}

/**
 *  Run a test parcours.
 *
 *  You provide test [cases] containing some input and what running [inputToActual] on that should return.
 *
 *  The function will deal with running the testee on all inputs, comparison, collecting and printing all failures, and failing the JUnit test appropriately.
 */
fun <T, R> runTestParcours(title: String, vararg cases: TestCase<T, R>, inputToActual: (T) -> R) {
    val failures = cases
        .onEach { it.actual = inputToActual(it.input) }
        .filter { it.actual != it.expected }
    if (failures.isEmpty()) return
    println("$title failures:")
    println(failures.joinToString("\n"))
    Assert.assertEquals(0, failures.size)
}

/**
 *  Run a test parcours.
 *
 *  You provide test cases in [items] containing some input and what running [inputToActual] on that should return.
 *  The first, third and so on elements in [items] are input and must be type [T] (or else it throws at runtime).
 *  The second, fourth and so on elements in [items] are expected output and must be type [R] (or else it throws at runtime).
 *
 *  The function will deal with running the testee on all inputs, comparison, collecting and printing all failures, and failing the JUnit test appropriately.
 */
inline fun <reified T, reified R> runTestParcours(title: String, crossinline inputToActual: (T) -> R, vararg items: Any?) {
    val cases = Array(items.size / 2) { index ->
        TestCase(items[index * 2] as T, items[index * 2 + 1] as R)
    }
    runTestParcours(title, *cases) { inputToActual(it) }
}

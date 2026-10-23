package com.unciv.ui

import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.CivilopediaHistory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CivilopediaHistoryTests {
    private val history = CivilopediaHistory
    private val category = CivilopediaCategories.Technology

    @Before
    @After
    fun clearHistory() {
        history.clear()
    }

    @Test
    fun historyNavigationSavesTheLeavingPageWithoutOverwritingTheDestination() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 0f)

        history.back { history.updateScroll(category, "Pottery", 30f) }

        assertEquals("Agriculture", history.current()!!.entry)
        assertEquals(listOf(120f, 30f), history.map { it.scroll })
    }

    @Test
    fun forwardMovesToTheNextEntryAfterGoingBack() {
        history.push(category, "Agriculture", 0f)
        history.push(category, "Pottery", 0f)
        history.back { }

        history.forward { assertEquals("Pottery", it.entry) }

        assertEquals(1, history.position)
        assertEquals("Pottery", history.current()!!.entry)
    }

    @Test
    fun emptyHistoryHasNoNavigation() {
        assertTrue(history.isEmpty())
        assertNull(history.current())
        assertFalse(history.hasBack())
        assertFalse(history.hasForward())
        history.back { error("Empty history must not navigate") }
        history.forward { error("Empty history must not navigate") }
    }

    @Test
    fun aNewVisitAfterGoingBackReplacesOnlyTheForwardBranch() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 30f)
        history.push(category, "Writing", 0f)
        history.back { }

        history.push(category, "Mining", 0f)

        assertEquals(listOf("Agriculture", "Pottery", "Mining"), history.map { it.entry })
        assertEquals(listOf(120f, 30f, 0f), history.map { it.scroll })
        assertFalse(history.hasForward())
    }

    @Test
    fun reselectingTheCurrentEntryPreservesTheForwardBranch() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 0f)
        history.back { }

        history.push(category, "Agriculture", 150f)

        assertEquals(listOf("Agriculture", "Pottery"), history.map { it.entry })
        assertEquals(150f, history.current()!!.scroll, 0f)
        assertTrue(history.hasForward())
    }

    @Test
    fun menuNavigationKeepsTheHistoryChainAndSeparateReadingPositions() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 0f)
        history.push(category, "Mining", 10f)

        history.navigateTo(0, history.first()) { destination ->
            history.updateScroll(category, "Mining", 50f)
            assertEquals(120f, destination.scroll, 0f)
        }

        assertEquals(listOf("Agriculture", "Pottery", "Mining"), history.map { it.entry })
        assertEquals(listOf(120f, 0f, 50f), history.map { it.scroll })
        assertEquals(0, history.position)
        assertTrue(history.hasForward())
    }

    @Test
    fun scrollUpdatesMustMatchTheLeavingCategoryAndEntry() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 0f)

        history.back {
            history.updateScroll(category, "Agriculture", 20f)
            history.updateScroll(CivilopediaCategories.Resource, "Pottery", 30f)
        }

        assertEquals(listOf(120f, 0f), history.map { it.scroll })
    }

    @Test
    fun backAndForwardSkipUnavailableEntriesWithoutChangingTheStack() {
        history.push(category, "Agriculture", 120f)
        history.push(category, "Missing", 0f)
        history.push(category, "Pottery", 30f)
        val original = history.toList()
        val isValid: (CivilopediaHistory.Entry) -> Boolean = { it.entry != "Missing" }

        history.back(isValid) { assertEquals("Agriculture", it.entry) }
        assertEquals(0, history.position)
        history.forward(isValid) { assertEquals("Pottery", it.entry) }

        assertEquals(2, history.position)
        assertEquals(original, history.toList())
    }

    @Test
    fun noValidTargetLeavesThePositionUnchanged() {
        history.push(category, "Missing", 0f)
        history.push(category, "Pottery", 30f)
        val isValid: (CivilopediaHistory.Entry) -> Boolean = { it.entry != "Missing" }

        assertFalse(history.hasBack(isValid))
        history.back(isValid) { error("No valid target") }

        assertEquals(1, history.position)
        assertEquals("Pottery", history.current()!!.entry)
        assertEquals(30f, history.current()!!.scroll, 0f)
    }

    @Test
    fun capacityIncludesCurrentAndForwardEntries() {
        for (index in 0 until 60) history.push(category, "Entry $index", index.toFloat())

        assertEquals(50, history.count())
        assertEquals("Entry 10", history.first().entry)
        assertEquals(49, history.position)
        for (index in 58 downTo 10) history.back { assertEquals("Entry $index", it.entry) }

        assertFalse(history.hasBack())
        assertEquals(50, history.count())
        history.forward { assertEquals("Entry 11", it.entry) }
    }

    @Test
    fun unchangedContextPreservesTheCursorAndReadingPosition() {
        history.setContext(listOf("G&K", "Mod"))
        history.push(category, "Agriculture", 120f)
        history.push(category, "Pottery", 0f)
        history.back { }

        history.setContext(listOf("G&K", "Mod"))

        assertEquals(0, history.position)
        assertEquals(120f, history.current()!!.scroll, 0f)
        assertTrue(history.hasForward())
    }

    @Test
    fun switchingBaseRulesetsClearsHistory() {
        history.setContext(listOf("G&K"))
        history.push(CivilopediaCategories.Resource, "Salt", 0f)

        history.setContext(listOf("Vanilla"))

        assertTrue(history.isEmpty())
        assertNull(history.current())
        assertFalse(history.hasBack())
        assertFalse(history.hasForward())
    }

    @Test
    fun changingModOrderAlsoChangesTheContext() {
        history.setContext(listOf("G&K", "A", "B"))
        history.push(category, "Agriculture", 0f)

        history.setContext(listOf("G&K", "B", "A"))

        assertTrue(history.isEmpty())
    }

    @Test
    fun contextIsASnapshotOfTheCallersList() {
        val names = mutableListOf("G&K", "Mod")
        history.setContext(names)
        history.push(category, "Agriculture", 0f)
        names.add("Another Mod")

        history.setContext(listOf("G&K", "Mod"))
        assertEquals("Agriculture", history.current()!!.entry)
        history.setContext(names)

        assertTrue(history.isEmpty())
    }

    @Test
    fun aFailedCallbackDoesNotLeaveHistoryInNavigationMode() {
        history.push(category, "Agriculture", 0f)
        history.push(category, "Pottery", 0f)
        val failure = IllegalStateException("Callback failed")
        try {
            history.back { throw failure }
            error("Expected callback failure")
        } catch (exception: IllegalStateException) {
            assertEquals(failure, exception)
        }

        history.push(category, "Mining", 0f)

        assertEquals(listOf("Agriculture", "Mining"), history.map { it.entry })
    }
}

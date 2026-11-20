package com.unciv.uniques

import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** A `<for [n] turns>` unique is a trigger, active only through the timed-unique bookkeeping - no UniqueMap query may report it as an active unique. */
@RunWith(BaseTestRunner::class)
class UniqueMapTimedUniquesTests {
    private val timed = Unique("Provides [1] [Coal] <for [3] turns>")
    private val plain = Unique("Provides [1] [Iron]")
    private val tag = timed.placeholderText

    private fun mapOf(vararg uniques: Unique) = UniqueMap().apply { uniques.forEach { addUnique(it) } }

    @Test
    fun timedUniqueIsNotReportedByAnyLookup() {
        val map = mapOf(timed)
        val state = GameContext.EmptyState
        Assert.assertTrue("setup: unique is timed", timed.isTimedTriggerable)
        Assert.assertEquals("setup: same tag", tag, plain.placeholderText)

        Assert.assertFalse("hasUnique", map.hasUnique(UniqueType.ProvidesResources))
        Assert.assertFalse("hasUnique by tag", map.hasUnique(tag))
        Assert.assertTrue("getMatchingUniques", map.getMatchingUniques(UniqueType.ProvidesResources, state).none())
        Assert.assertFalse("hasMatchingUnique", map.hasMatchingUnique(UniqueType.ProvidesResources, state))
        Assert.assertFalse("hasMatchingTagUnique", map.hasMatchingTagUnique(tag, state))
    }

    @Test
    fun plainUniqueNextToATimedOneIsStillFound() {
        val map = mapOf(timed, plain)
        val state = GameContext.EmptyState

        Assert.assertTrue(map.hasMatchingUnique(UniqueType.ProvidesResources, state))
        Assert.assertTrue(map.hasMatchingTagUnique(tag, state))
    }
}

package com.unciv.uniques

import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.IdentityHashMap

@RunWith(GdxTestRunner::class)
class AllObjectsTests {

    @Before
    fun loadRulesets() {
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun `ruleset all functions should not return duplicate object references`() {
        var success = true

        for (baseRuleset in BaseRuleset.entries) {
            val ruleset = RulesetCache[baseRuleset.fullName]!!
            if (!testRuleset(ruleset, ruleset.allRulesetObjects(), "allRulesetObjects") { it.name })
                success = false
            if (!testRuleset(ruleset, ruleset.allICivilopediaText(), "allICivilopediaText", ::civilopediaEntryName))
                success = false
            if (!testRuleset(ruleset, ruleset.allUniques(), "allUniques") { it.text })
                success = false
        }

        assertTrue("Ruleset all-functions should not return the same instance more than once", success)
    }

    private fun civilopediaEntryName(entry: ICivilopediaText) =
        if (entry is EventChoice) entry.text else entry.makeLink()

    private fun <T : Any> testRuleset(
        ruleset: Ruleset,
        entries: Sequence<T>,
        functionName: String,
        entryName: (T) -> String
    ): Boolean {
        val seen = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
        val duplicates = entries.filter { !seen.add(it) }.toList()
        for (duplicate in duplicates) {
            println("Function $functionName delivered duplicate ${duplicate::class.simpleName} ${entryName(duplicate)} for ${ruleset.name}.")
        }
        return duplicates.isEmpty()
    }
}

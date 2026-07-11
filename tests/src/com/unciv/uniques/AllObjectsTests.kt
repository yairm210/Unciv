package com.unciv.uniques

import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestCase
import com.unciv.testing.runTestParcours
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class AllObjectsTests {

    @Before
    fun loadRulesets() {
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun `allRulesetObjects should not return duplicate references`() {
        runTestParcours("allRulesetObjects duplicate references", *baseRulesetTestCases()) { baseRuleset ->
            RulesetCache[baseRuleset.fullName]!!.allRulesetObjects()
                .findDuplicateReferences()
                .map { it.name }
        }
    }

    @Test
    fun `allICivilopediaText should not return duplicate references`() {
        runTestParcours("allICivilopediaText duplicate references", *baseRulesetTestCases()) { baseRuleset ->
            RulesetCache[baseRuleset.fullName]!!.allICivilopediaText()
                .findDuplicateReferences()
                .map { civilopediaEntryName(it) }
        }
    }

    @Test
    fun `allUniques should not return duplicate references`() {
        runTestParcours("allUniques duplicate references", *baseRulesetTestCases()) { baseRuleset ->
            RulesetCache[baseRuleset.fullName]!!.allUniques()
                .findDuplicateReferences()
                .map { it.text }
        }
    }

    private fun baseRulesetTestCases() =
        BaseRuleset.entries.map { TestCase(it, emptyList<String>()) }.toTypedArray()

    private fun civilopediaEntryName(entry: ICivilopediaText) =
        if (entry is EventChoice) entry.text else entry.makeLink()

    private fun <T : Any> Sequence<T>.findDuplicateReferences(): List<T> {
        val seen = mutableListOf<T>()
        val duplicates = mutableListOf<T>()
        for (entry in this) {
            if (seen.any { it === entry }) duplicates.add(entry)
            else seen.add(entry)
        }
        return duplicates
    }
}

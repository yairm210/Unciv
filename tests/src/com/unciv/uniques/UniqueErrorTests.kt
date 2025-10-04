package com.unciv.uniques

import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class UniqueErrorTests {
    @Test
    fun testMultipleUniqueTypesSameText() {
        val textToUniqueType = HashMap<String, UniqueType>()
        var errors = false
        for (uniqueType in UniqueType.entries) {
            if (textToUniqueType.containsKey(uniqueType.placeholderText)) {
                println("UniqueTypes ${uniqueType.name} and ${textToUniqueType[uniqueType.placeholderText]!!.name} have the same text!")
                errors = true
            }
            else textToUniqueType[uniqueType.placeholderText] = uniqueType
        }
        assert(!errors)
    }

    @Test
    fun testCodependantTechs() {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()

        // Create a prerequisite loop
        val techWithPrerequisites = ruleset.technologies.values.first { it.prerequisites.isNotEmpty() }
        val prereq = ruleset.technologies[techWithPrerequisites.prerequisites.first()]!!
        prereq.prerequisites.add(techWithPrerequisites.name)
        ruleset.modOptions.isBaseRuleset = true

        // Check mod links and ensure we don't get a crash, instead we get errors
        val errors = ruleset.getErrorList(false)
        assert(errors.isNotOK())
    }
}

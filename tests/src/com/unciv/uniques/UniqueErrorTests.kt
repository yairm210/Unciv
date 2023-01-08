package com.unciv.uniques

import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class UniqueErrorTests {
    @Test
    fun testCodependantTechs() {
        RulesetCache.loadRulesets()
        val ruleset = RulesetCache.getVanillaRuleset()

        // Create a prerequisite loop
        val techWithPrerequisites = ruleset.technologies.values.first { it.prerequisites.isNotEmpty() }
        val prereq = ruleset.technologies[techWithPrerequisites.prerequisites.first()]!!
        prereq.prerequisites.add(techWithPrerequisites.name)
        ruleset.modOptions.isBaseRuleset = true

        // Check mod links and ensure we don't get a crash, instead we get errors
        val errors = ruleset.checkModLinks(false)
        assert(errors.isNotOK())
    }
}

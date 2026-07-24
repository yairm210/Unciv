package com.unciv.uniques

import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueValidator
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
    
    @Test
    fun testTimedGlobalUniqueAcceptsTriggerConditionsWhenOnUnit(){
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()
        // Since the <for [3] turns> turns this unique into a triggerable, the <upon> trigger condition should be ok
        val uniqueText = "[-5]% Strength <for [3] turns> <upon damaging a [Warrior] unit>"
        
        // Without a unit, this is an error
        val uniqueNoSourceObject = Unique(uniqueText)
        val errorListNoSourceObject = UniqueValidator(ruleset).checkUnique(uniqueNoSourceObject, false, null, true)
        assert(errorListNoSourceObject.getFinalSeverity() == RulesetErrorSeverity.Warning)
        
        // When applied on a unit or promotion etc, this is fine
        val uniqueWithSourceObject = Unique(uniqueText, sourceObjectType = UniqueTarget.Promotion)
        val errorListCorrectUniqueContainer = UniqueValidator(ruleset).checkUnique(uniqueWithSourceObject, false, null, true)
        assert(errorListCorrectUniqueContainer.getFinalSeverity() == RulesetErrorSeverity.OK)
    }
}

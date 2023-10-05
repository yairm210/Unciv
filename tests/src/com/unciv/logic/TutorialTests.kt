package com.unciv.logic

import com.unciv.models.TutorialTrigger
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Tutorial
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.screens.basescreen.TutorialController
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TutorialTests {
    private var tutorials: LinkedHashMap<String, Tutorial>? = null
    private var exception: Throwable? = null
    private val triggerCounts = mutableMapOf<TutorialTrigger, Int>()

    private fun String.getTutorialTrigger() =
        TutorialTrigger.values().firstOrNull { it.name == this }

    init {
         try {
             tutorials = TutorialController.loadTutorialsFromJson(includeMods = false)

             for (tutorial in tutorials!!.values) {
                 for (unique in tutorial.getMatchingUniques(UniqueType.TutorialTrigger)) {
                     // Bad trigger parameter values will be caught by unique validation instead
                     val trigger = unique.params[0].getTutorialTrigger() ?: continue
                     triggerCounts[trigger] = 1 + triggerCounts.getOrPut(trigger) { 0 }
                 }
             }
         } catch (ex: Throwable) {
             exception = ex
         }
    }

    @Test
    fun tutorialsFileIsDeSerializable() {
        if (exception != null)
            fail("Loading Tutorials.json fails with ${exception?.javaClass?.simpleName} \"${exception?.message}\"")
    }

    @Test
    fun tutorialsFileCoversAllTriggers() {
        if (tutorials == null) return
        val missingTutorialTriggers = TutorialTrigger.values()
            .filter {
                it.context != TutorialTrigger.TriggerContext.Unused
                    && it !in triggerCounts
            }
        if (missingTutorialTriggers.isEmpty()) return
        fail("One or more TutorialTriggers have no matching entry in Tutorials.json: " + missingTutorialTriggers.joinToString())
    }

    @Test
    fun noDuplicateTutorialsPerTrigger() {
        if (tutorials == null) return
        val duplicateTriggers = triggerCounts.filter { it.value > 1 }.keys
        if (duplicateTriggers.isEmpty()) return
        fail("One or more TutorialTriggers have duplicate entries in Tutorials.json: " + duplicateTriggers.joinToString())
    }

    @Test
    fun noTutorialsForDisabledTriggers() {
        if (tutorials == null) return
        val usedUnusedTriggers = triggerCounts.filter { it.value > 1 }.keys
        if (usedUnusedTriggers.isEmpty()) return
        fail("One or more TutorialTriggers marked 'Unused' have an entry in Tutorials.json: " + usedUnusedTriggers.joinToString())
    }

    @Test
    fun allTriggeredTutorialsHavePresentation() {
        if (tutorials == null) return
        val badTutorials = tutorials!!.values.filter { it.hasUnique(UniqueType.TutorialTrigger) }
            .filterNot { it.hasUnique(UniqueType.TutorialPresentation) }
            .map { it.name }
        if (badTutorials.isEmpty()) return
        fail("One or more Tutorials have a TutorialTrigger but no TutorialPresentation: " + badTutorials.joinToString())
    }

    @Test
    fun tutorialsPassUniqueValidation() {
        val dummyRuleset = Ruleset()
        val validator = UniqueValidator(dummyRuleset)
        val errors = RulesetErrorList()

        for (tutorial in tutorials!!.values)
            validator.checkUniques(tutorial, errors, UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant, false)

        if (!errors.isNotOK()) return
        println(errors.getErrorText { true })
        fail("One or more Tutorials failed Unique validation")
    }

    @Test
    fun tutorialsAllHaveText() {
        if (tutorials == null) return
        for (tutorial in tutorials!!.values) {
            if (tutorial.steps?.isNotEmpty() != true && tutorial.civilopediaText.isEmpty())
                fail("Tutorial \"$tutorial\" contains no text")
        }
    }
}

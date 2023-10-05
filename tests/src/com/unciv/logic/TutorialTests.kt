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

    init {
         try {
             tutorials = TutorialController.loadTutorialsFromJson(includeMods = false)
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
        for (trigger in TutorialTrigger.values()) {
            val name = trigger.value.replace('_', ' ').trimStart()
            if (name in tutorials!!) continue
            fail("TutorialTrigger $trigger has no matching entry in Tutorials.json")
        }
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

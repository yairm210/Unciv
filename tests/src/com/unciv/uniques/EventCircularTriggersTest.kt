package com.unciv.uniques

import com.unciv.Constants
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.testing.GdxTestRunner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class EventCircularTriggersTest {

    private lateinit var ruleset: Ruleset

    @Before
    fun setup() {
        if (RulesetCache.isEmpty()) RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache.getVanillaRuleset()
        ruleset.modOptions.isBaseRuleset = true
    }

    private fun addEvent(name: String, vararg choiceUniques: List<String>) {
        val event = Event()
        event.name = name
        for ((i, uniqueList) in choiceUniques.withIndex()) {
            val choice = EventChoice()
            choice.name = "$name-choice-$i"
            choice.uniques.addAll(uniqueList)
            event.choices.add(choice)
        }
        ruleset.events[name] = event
    }

    private fun hasCircularWarning() =
        RulesetValidator.create(ruleset).getErrorList()
            .any { "Circular trigger in Events" in it.text }

    @Test
    fun `circular event self-trigger is detected`() {
        addEvent("Loop", listOf("Triggers a [Loop] event"))
        assert(hasCircularWarning())
    }

    @Test
    fun `two events triggering each other is detected`() {
        addEvent("PingEvent", listOf("Triggers a [PongEvent] event"))
        addEvent("PongEvent", listOf("Triggers a [PingEvent] event"))
        assert(hasCircularWarning())
    }

    @Test
    fun `human-only choice suppresses circular trigger warning`() {
        addEvent("HumanLoop", listOf(
            "Only available <for [${Constants.humanPlayer}] Civilizations>",
            "Triggers a [HumanLoop] event"
        ))
        assert(!hasCircularWarning())
    }

    @Test
    fun `AI-unavailable choice suppresses circular trigger warning`() {
        addEvent("AiLoop", listOf(
            "Unavailable <for [${Constants.aiPlayer}] Civilizations>",
            "Triggers a [AiLoop] event"
        ))
        assert(!hasCircularWarning())
    }

    @Test
    fun `human-only trigger event suppresses circular trigger warning`() {
        addEvent("HumanTrigger", listOf(
            "Triggers a [HumanTrigger] event <for [${Constants.humanPlayer}] Civilizations>"
        ))
        assert(!hasCircularWarning())
    }
}

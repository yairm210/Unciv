package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.stats.INamed
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText


class Event : INamed, ICivilopediaText {

    override var name = ""
    var text = ""
    override var civilopediaText = listOf<FormattedLine>()
    override fun makeLink() = "Event/$name"

    // todo: add unrepeatable events

    var choices = ArrayList<EventChoice>()

    /** @return `null` when no choice passes the condition tests, so client code can easily bail using Elvis `?:`. */
    fun getMatchingChoices(stateForConditionals: StateForConditionals) =
        choices.filter { it.matchesConditions(stateForConditionals) }.ifEmpty { null }
}

class EventChoice : ICivilopediaText {
    var text = ""
    override var civilopediaText = listOf<FormattedLine>()
    override fun makeLink() = ""

    /** Keyboard support - not user-rebindable, mod control only. Will be [parsed][KeyCharAndCode.parse], so Gdx key names will work. */
    val keyShortcut = ""

    var triggeredUniques = ArrayList<String>()
    val triggeredUniqueObjects by lazy { triggeredUniques.map { Unique(it) } }

    var conditions = ArrayList<String>()
    val conditionObjects by lazy { conditions.map { Unique(it) } }

    fun matchesConditions(stateForConditionals: StateForConditionals) =
        conditionObjects.all { Conditionals.conditionalApplies(null, it, stateForConditionals) }

    fun triggerChoice(civ: Civilization) {
        for (unique in triggeredUniqueObjects)
            UniqueTriggerActivation.triggerUnique(unique, civ)
    }
}

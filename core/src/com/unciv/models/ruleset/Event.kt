package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText


class Event : RulesetObject() {
    enum class Presentation { /** Does not display a popup, choice chosen randomly */ None, Alert, Floating }
    val presentation = Presentation.Alert
    var text = ""

    override fun getUniqueTarget() = UniqueTarget.Event
    override fun makeLink() = "Event/$name"

    // todo: add unrepeatable events

    var choices = ArrayList<EventChoice>()

    /** @return `null` when no choice passes the condition tests, so client code can easily bail using Elvis `?:`.
     *          An empty list is possible when the Event definition contains no choices and the event's conditions are fulfilled.
     */
    fun getMatchingChoices(stateForConditionals: StateForConditionals): Collection<EventChoice>? {
        if (!isAvailable(stateForConditionals)) return null
        if (choices.isEmpty()) return emptyList()
        return choices.filter { it.matchesConditions(stateForConditionals) }.ifEmpty { null }
    }

    fun isAvailable(stateForConditionals: StateForConditionals) =
        getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals).none { !it.conditionalsApply(stateForConditionals) } &&
        getMatchingUniques(UniqueType.Unavailable, stateForConditionals).none()
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

    fun triggerChoice(civ: Civilization): Boolean {
        var success = false
        val stateForConditionals = StateForConditionals(civ)
        for (unique in triggeredUniqueObjects.flatMap { it.getMultiplied(stateForConditionals) })
            if (UniqueTriggerActivation.triggerUnique(unique, civ)) success = true
        return success
    }
}

package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.*
import com.unciv.ui.components.input.KeyCharAndCode
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

class EventChoice : ICivilopediaText, RulesetObject() {
    var text = ""
    override fun getUniqueTarget() = UniqueTarget.EventChoice
    override fun makeLink() = ""

    /** Keyboard support - not user-rebindable, mod control only. Will be [parsed][KeyCharAndCode.parse], so Gdx key names will work. */
    val keyShortcut = ""
    
    @Deprecated("as of 4.13.4", ReplaceWith("uniques"))
    var triggeredUniques = ArrayList<String>()
    @Deprecated("as of 4.13.4", ReplaceWith("uniqueObjects"))
    val triggeredUniqueObjects by lazy { triggeredUniques.map { Unique(it) } }

    @Deprecated("as of 4.13.4", ReplaceWith("uniques"))
    var conditions = ArrayList<String>()
    @Deprecated("as of 4.13.4", ReplaceWith("uniqueObjects"))
    val conditionObjects by lazy { conditions.map { Unique(it) } }

    fun matchesConditions(stateForConditionals: StateForConditionals): Boolean {
        if (conditionObjects.any { !Conditionals.conditionalApplies(null, it, stateForConditionals) })
            return false
        if (hasUnique(UniqueType.Unavailable, stateForConditionals)) return false
        if (getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                .any { !it.conditionalsApply(stateForConditionals) })
            return false
        return true
    }

    fun triggerChoice(civ: Civilization, unit: MapUnit? = null): Boolean {
        var success = false
        val stateForConditionals = StateForConditionals(civ, unit = unit)
        val triggerUniques = uniqueObjects.filter { it.isTriggerable } + triggeredUniqueObjects 
        for (unique in triggerUniques.flatMap { it.getMultiplied(stateForConditionals) })
            if (UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit)) success = true
        return success
    }
}

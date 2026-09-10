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
    fun getMatchingChoices(gameContext: GameContext): Collection<EventChoice>? {
        if (!isAvailable(gameContext)) return null
        if (choices.isEmpty()) return emptyList()
        return choices.filter { it.isAvailable(gameContext) }.ifEmpty { null }
    }
}

class EventChoice : ICivilopediaText, RulesetObject() {
    var text = ""
    override fun getUniqueTarget() = UniqueTarget.EventChoice
    override fun makeLink() = ""

    /** Keyboard support - not user-rebindable, mod control only. Will be [parsed][KeyCharAndCode.parse], so Gdx key names will work. */
    val keyShortcut = ""
    

    fun triggerChoice(civ: Civilization, unit: MapUnit? = null): Boolean {
        var success = false
        val gameContext = GameContext(civ, unit = unit)
        for (unique in uniqueObjects) {
            if (!unique.isTriggerable || !unique.conditionalsApply(gameContext)) continue
            repeat(unique.getUniqueMultiplier(gameContext)) {
                if (UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit)) success = true
            }
        }
        return success
    }
}

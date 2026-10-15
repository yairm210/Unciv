package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.*
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText


class Event : RulesetObject() {
    /** Controls how an Event is shown to the player when triggered (see [UniqueTriggerActivation.triggerUnique] for [UniqueType.TriggerEvent]).
     *  Note: AI civs always behave as [None] (weighted random) for [Alert]/[Floating] presentations, since they can't see a popup.
     *  See also [UniqueType.OnlyFirstAvailableChoiceIsChosen], which restricts the choice pool to a single entry regardless of presentation. */
    enum class Presentation {
        /** No popup. A choice is picked immediately via weighted random ([EventChoice.getWeightForAiDecision]) and triggered right away. */
        None,
        /** Queues a [com.unciv.logic.civilization.PopupAlert] of type [com.unciv.logic.civilization.AlertType.Event], shown as a blocking modal dialog ([com.unciv.ui.screens.worldscreen.AlertPopup]) the player must resolve before continuing. */
        Alert,
        /** Not yet implemented - triggering an Event with this presentation currently throws [NotImplementedError]. Intended to park the choice non-blockingly instead of forcing an immediate popup. */
        Floating
    }
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
        val matchingChoices = choices.filter { it.isAvailable(gameContext) }.ifEmpty { null } ?: return null
        if (hasUnique(UniqueType.OnlyFirstAvailableChoiceIsChosen, gameContext))
            return listOf(matchingChoices.first())
        return matchingChoices
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

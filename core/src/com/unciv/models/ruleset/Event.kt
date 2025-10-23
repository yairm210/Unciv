package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.*
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import yairm210.purity.annotations.Readonly

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
        return choices.filter { it.matchesConditions(gameContext) }.ifEmpty { null }
    }

    fun isAvailable(gameContext: GameContext) =
        getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals).none { !it.conditionalsApply(gameContext) } &&
        getMatchingUniques(UniqueType.Unavailable, gameContext).none()
}

class EventChoice : ICivilopediaText, RulesetObject() {
    var text = ""
    override fun getUniqueTarget() = UniqueTarget.EventChoice
    override fun makeLink() = ""

    /** Keyboard support - not user-rebindable, mod control only. Will be [parsed][KeyCharAndCode.parse], so Gdx key names will work. */
    val keyShortcut = ""

    @Readonly
    fun matchesConditions(gameContext: GameContext): Boolean {
        if (hasUnique(UniqueType.Unavailable, gameContext)) return false
        if (getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                .any { !it.conditionalsApply(gameContext) })
            return false
        return true
    }

    fun triggerChoice(gameContext: GameContext): Boolean {
        var success = false
        val triggerUniques = uniqueObjects
            .filter { it.isTriggerable && it.conditionalsApply(gameContext) }
        for (unique in triggerUniques.flatMap { it.getMultiplied(gameContext) })
            if (UniqueTriggerActivation.triggerUnique(unique, gameContext)) success = true
        return success
    }
}

package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.stats.INamed



class Event : INamed {

    override var name = ""
    var text = ""
    // todo: add unrepeatable events

    var choices = ArrayList<EventChoice>()
}

class EventChoice {
    var text = ""
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

package com.unciv.models.ruleset.nation

import com.unciv.Constants
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget

class Personality : RulesetObject() {
    var production: Float = 5f
    var food: Float = 5f
    var gold: Float = 5f
    var science: Float = 5f
    var culture: Float = 5f
    var happiness: Float = 5f
    var faith: Float = 5f
    var military: Float = 5f
    var policy = LinkedHashMap<String, Int>()
    var preferredVictoryType: String = Constants.neutralVictoryType
    
    override fun getUniqueTarget() = UniqueTarget.Personailty

    override fun makeLink(): String {
        return ""
    }

}

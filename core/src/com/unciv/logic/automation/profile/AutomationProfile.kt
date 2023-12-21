package com.unciv.logic.automation.profile

import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget

class AutomationProfile : RulesetObject(){
    val policy = PolicyProfile()

    fun clone():AutomationProfile{
        val toReturn = AutomationProfile()
        toReturn.policy.increase = policy.increase.clone() as ArrayList<String>
        toReturn.policy.decrease = policy.decrease.clone() as ArrayList<String>
        return toReturn
    }

    override fun getUniqueTarget(): UniqueTarget {
        TODO("Not yet implemented")
    }

    override fun makeLink(): String {
        TODO("Not yet implemented")
    }
}

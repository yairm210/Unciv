package com.unciv.logic.automation.profile

class AutomationProfile {
    val policy = PolicyProfile()

    fun clone():AutomationProfile{
        val toReturn = AutomationProfile()
        toReturn.policy.increase = policy.increase.clone() as ArrayList<String>
        toReturn.policy.decrease = policy.decrease.clone() as ArrayList<String>
        return toReturn
    }
}

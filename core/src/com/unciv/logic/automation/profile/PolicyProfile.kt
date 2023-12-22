package com.unciv.logic.automation.profile

import com.unciv.models.ruleset.PolicyBranch

class PolicyProfile {
    var increase = ArrayList<String>()
    var decrease = ArrayList<String>()

    fun getPriotiyModifier(policy: PolicyBranch): Int {
        //Half the value of the most basic policy priority
        if (increase.contains(policy.name))
            return 5
        if (decrease.contains(policy.name))
            return -5
        return 0
    }
}

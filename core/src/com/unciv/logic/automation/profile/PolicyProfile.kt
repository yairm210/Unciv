package com.unciv.logic.automation.profile

import com.unciv.models.ruleset.PolicyBranch

class PolicyProfile {
    var increase = ArrayList<String>()
    var decrease = ArrayList<String>()

    fun getPriotiyModifier(policy: PolicyBranch): Int {
        if (increase.contains(policy.name))
            return 1
        if (decrease.contains(policy.name))
            return -1
        return 0
    }
}

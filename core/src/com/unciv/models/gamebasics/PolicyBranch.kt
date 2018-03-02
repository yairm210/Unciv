package com.unciv.models.gamebasics

import com.unciv.models.linq.Linq

class PolicyBranch : Policy() {
    @JvmField var policies: Linq<Policy> = Linq()
}

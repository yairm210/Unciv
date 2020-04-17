package com.unciv.models.ruleset

import com.unciv.models.ruleset.tech.TechEra

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    lateinit var era: String
}

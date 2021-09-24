package com.unciv.models.ruleset

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    lateinit var era: String
}

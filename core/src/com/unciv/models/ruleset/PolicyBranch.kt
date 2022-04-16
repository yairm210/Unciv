package com.unciv.models.ruleset

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    var priorities: HashMap<String, Int> = HashMap<String, Int>()
    lateinit var era: String
}

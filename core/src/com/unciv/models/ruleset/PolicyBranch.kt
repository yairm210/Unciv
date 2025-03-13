package com.unciv.models.ruleset

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    var priorities: HashMap<String, Int> = HashMap()
    var era: String = ""
    var title: String = ""
}

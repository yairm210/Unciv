package com.unciv.models.ruleset

class PolicyTitle {
    var male: String = ""
    var female: String = ""
    var placement: String = "" // beginning/end
}

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    var priorities: HashMap<String, Int> = HashMap()
    var era: String = ""
    lateinit var title: PolicyTitle
}

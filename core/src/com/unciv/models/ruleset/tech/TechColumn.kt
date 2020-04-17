package com.unciv.models.ruleset.tech

import java.util.*

class TechColumn {
    var columnNumber: Int = 0
    lateinit var era: String
    var techs = ArrayList<Technology>()
    var techCost: Int = 0
    var buildingCost: Int = 0
    var wonderCost: Int = 0
}



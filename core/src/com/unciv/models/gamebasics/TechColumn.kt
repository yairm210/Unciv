package com.unciv.models.gamebasics

import java.util.*

class TechColumn {
    var columnNumber: Int = 0
    lateinit var era: TechEra
    var techs = ArrayList<Technology>()
    var techCost: Int = 0
    var buildingCost: Int = 0
    var wonderCost: Int = 0
}


enum class TechEra{
    Ancient,
    Classical,
    Medieval,
    Renaissance,
    Industrial,
    Modern,
    Information,
    Future
}
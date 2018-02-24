package com.unciv.models.gamebasics

import java.util.ArrayList

class TechColumn {
    @JvmField var columnNumber: Int = 0
    @JvmField var era: String? = null
    @JvmField var techs = ArrayList<Technology>()
    @JvmField var techCost: Int = 0
    @JvmField var buildingCost: Int = 0
    @JvmField var wonderCost: Int = 0
}

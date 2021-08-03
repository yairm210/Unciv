package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed


enum class UnitLayer { // The layer in which the unit moves
    Civilian,
    Military,
    Air 
}

enum class UnitMovementType { // The types of tiles the unit can by default enter
    Land, // Only land tiles except when certain techs are researched
    Water, // Only water tiles
    Air // Only city tiles and carrying units
}

class UnitType(
    val movementType: String? = null
) : INamed {
    override lateinit var name: String
    val uniques: ArrayList<String> = ArrayList()
    
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    
    constructor(name: String, domain: String? = null) : this(domain) {
        this.name = name
    }
    
    fun getMovementType() = if (movementType == null) null else UnitMovementType.valueOf(movementType)
    
    fun isLandUnit() = getMovementType() == UnitMovementType.Land
    fun isWaterUnit() = getMovementType() == UnitMovementType.Water
    fun isAirUnit() = getMovementType() == UnitMovementType.Air
    
    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            "Land" -> isLandUnit()
            "Water" -> isWaterUnit()
            "Air" -> isAirUnit()
            else -> {
                if (uniques.contains(filter)) true
                else false
            }
        }
    }
    
    companion object {
        val City = UnitType("City", "Land")
    }
}



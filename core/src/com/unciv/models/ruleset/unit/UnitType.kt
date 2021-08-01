package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed


enum class UnitLayer { // The layer in which the unit moves
    Civilian,
    Military,
    Air 
}

enum class UnitDomain { // The types of tiles the unit can by default enter
    Land, // Only land tiles except when certain techs are researched
    Water, // Only water tiles
    Air // Only city tiles and carrying units
}

class UnitType(
    val domain: String? = null
) : INamed {
    override lateinit var name: String
    val uniques: ArrayList<String> = ArrayList()
    
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    
    constructor(name: String, layer: String? = null, domain: String? = null) : this(domain) {
        this.name = name
    }
    
    fun getDomain() = if (domain == null) null else UnitDomain.valueOf(domain)
    
    fun isLandUnit() = getDomain() == UnitDomain.Land
    fun isWaterUnit() = getDomain() == UnitDomain.Water
    fun isAirUnit() = getDomain() == UnitDomain.Air
    
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
}



package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions.getUnitTypeCivilopediaTextLines
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly


enum class UnitMovementType { // The types of tiles the unit can by default enter
    Land, // Only land tiles except when certain techs are researched
    Water, // Only water tiles
    Air // Only city tiles and carrying units
}

class UnitType() : RulesetObject() {
    internal var movementType: String? = null
    private val unitMovementType: UnitMovementType? by lazy { if (movementType == null) null else UnitMovementType.valueOf(movementType!!) }
    override fun getUniqueTarget() = UniqueTarget.UnitType
    override fun makeLink() = "UnitType/$name"

    constructor(name: String, domain: String? = null) : this() {
        this.name = name
        this.movementType = domain
    }

    @Readonly fun getMovementType() = unitMovementType

    @Pure fun isLandUnit() = unitMovementType == UnitMovementType.Land
    @Pure fun isWaterUnit() = unitMovementType == UnitMovementType.Water
    @Pure fun isAirUnit() = unitMovementType == UnitMovementType.Air

    /** Implements [UniqueParameterType.UnitTypeFilter][com.unciv.models.ruleset.unique.UniqueParameterType.UnitTypeFilter] */
    @Readonly
    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            "Land" -> isLandUnit()
            "Water" -> isWaterUnit()
            "Air" -> isAirUnit()
            else -> hasTagUnique(filter)
        }
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset) = getUnitTypeCivilopediaTextLines(ruleset)
    override fun getSortGroup(ruleset: Ruleset): Int {
        return if (name.startsWith("Domain: ")) 1 else 2
    }

    @Readonly fun isUsed(ruleset: Ruleset) = ruleset.units.values.any { it.unitType == name }

    companion object {
        val City = UnitType("City", "Land")

        fun getCivilopediaIterator(ruleset: Ruleset): Collection<UnitType> {
            return UnitMovementType.entries.map {
                // Create virtual UnitTypes to describe the movement domains - Civilopedia only.
                // It is important that the name includes the [] _everywhere_
                // (here, CivilopediaImageGetters, links, etc.) so translation comes as cheap as possible.
                UnitType("Domain: [${it.name}]", it.name)
            } + ruleset.unitTypes.values.filter {
                it.isUsed(ruleset)
            }
        }
    }
}

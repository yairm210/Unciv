package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions.getUnitTypeCivilopediaTextLines


enum class UnitMovementType { // The types of tiles the unit can by default enter
    Land, // Only land tiles except when certain techs are researched
    Water, // Only water tiles
    Air // Only city tiles and carrying units
}

class UnitType() : RulesetObject() {
    private var movementType: String? = null
    private val unitMovementType: UnitMovementType? by lazy { if (movementType == null) null else UnitMovementType.valueOf(movementType!!) }
    @Transient lateinit var ruleset: Ruleset

    @delegate:Transient
    private val iUniqueObjects: List<Unique> by lazy {
        uniqueObjectsProvider(uniques + ruleset.globalUniques.unitUniques)
    }

    @delegate:Transient
    private val iUniqueMap: UniqueMap by lazy { uniqueMapProvider(iUniqueObjects) } // Has global uniques by the unique objects already

    @Transient
    override var uniqueObjects: List<Unique> = emptyList()
        get() {
            if (::ruleset.isInitialized) return iUniqueObjects
            else if (uniques.isEmpty()) return field
            else if (field.isEmpty()) field = uniqueObjectsProvider()

            return field
        }
        private set

    @Transient
    override var uniqueMap: UniqueMap = UniqueMap()
        get() {
            if (::ruleset.isInitialized) return iUniqueMap
            else if (uniqueObjects.isEmpty()) return field
            else if (field.isEmpty()) field = uniqueMapProvider()

            return field
        }
        private set

    override fun getUniqueTarget() = UniqueTarget.UnitType
    override fun makeLink() = "UnitType/$name"

    constructor(name: String, domain: String? = null) : this() {
        this.name = name
        this.movementType = domain
    }

    fun getMovementType() = unitMovementType

    fun isLandUnit() = unitMovementType == UnitMovementType.Land
    fun isWaterUnit() = unitMovementType == UnitMovementType.Water
    fun isAirUnit() = unitMovementType == UnitMovementType.Air

    /** Implements [UniqueParameterType.UnitTypeFilter][com.unciv.models.ruleset.unique.UniqueParameterType.UnitTypeFilter] */
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

    fun isUsed(ruleset: Ruleset) = ruleset.units.values.any { it.unitType == name }

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

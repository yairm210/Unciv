package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.mapregions.Region
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.stats.Stat
import com.unciv.models.ruleset.Ruleset
import yairm210.purity.annotations.Readonly

data class GameContext(
    val civInfo: Civilization? = null,
    val city: City? = null,
    val unit: MapUnit? = null,
    val tile: Tile? = null,

    val ourCombatant: ICombatant? = null,
    val theirCombatant: ICombatant? = null,
    val attackedTile: Tile? = null,
    val combatAction: CombatAction? = null,

    val region: Region? = null,
    val gameInfo: GameInfo? = civInfo?.gameInfo,

    val ignoreConditionals: Boolean = false,
) {
    constructor(city: City) : this(city.civ, city, tile = city.getCenterTileOrNull())
    constructor(unit: MapUnit) : this(unit.civ, unit = unit, tile = if (unit.hasTile()) unit.getTile() else null)
    constructor(ourCombatant: ICombatant, theirCombatant: ICombatant? = null,
                attackedTile: Tile? = null, combatAction: CombatAction? = null) : this(
        ourCombatant.getCivInfo(),
        (ourCombatant as? CityCombatant)?.city,
        (ourCombatant as? MapUnitCombatant)?.unit,
        ourCombatant.getTile(),
        ourCombatant,
        theirCombatant,
        attackedTile,
        combatAction
    )


    val relevantUnit by lazy {
        if (ourCombatant != null && ourCombatant is MapUnitCombatant) ourCombatant.unit
        else unit
    }

    val relevantTile by lazy { attackedTile
        ?: tile
        // We need to protect against conditionals checking tiles for units pre-placement - see #10425, #10512
        ?: relevantUnit?.run { if (hasTile()) getTile() else null }
        ?: city?.getCenterTileOrNull()
    }

    val relevantCity by lazy {
        if (city != null) return@lazy city
        // Edge case: If we attack a city, the "relevant tile" becomes the attacked tile -
        //  but we DO NOT want that city to become the relevant city because then *our* conditionals get checked against
        //  the *other civ's* cities, leading to e.g. resource amounts being defined as the *other civ's* resource amounts
        val relevantTileForCity = tile ?: relevantUnit?.run { if (hasTile()) getTile() else null }
        val cityForRelevantTile = relevantTileForCity?.getCity()
        if (cityForRelevantTile != null &&
            // ...and we can't use the relevantCiv here either, because that'll cause a loop
            (cityForRelevantTile.civ == civInfo || cityForRelevantTile.civ == relevantUnit?.civ)) return@lazy cityForRelevantTile
        else return@lazy null
    }

    val relevantCiv by lazy {
        civInfo ?:
        relevantCity?.civ ?:
        relevantUnit?.civ
    }

    @Readonly
    fun getResourceAmount(resourceName: String): Int {
        return when {
            relevantCity != null -> relevantCity!!.getAvailableResourceAmount(resourceName)
            relevantCiv != null -> relevantCiv!!.getResourceAmount(resourceName)
            else -> 0
        }
    }

    @Readonly
    fun getStatAmount(stat: Stat) : Int {
        return when {
            relevantCity != null -> relevantCity!!.getStatReserve(stat)
            relevantCiv != null && stat in Stat.statsWithCivWideField -> relevantCiv!!.getStatReserve(stat)
            else -> 0
        }
    }

    companion object {
        val IgnoreConditionals = GameContext(ignoreConditionals = true)
        val EmptyState = GameContext()
        /** When caching uniques, we need to cache them unmultiplied, and apply multiplication only on retrieval from cache
         * This state lets the multiplication function know that it's always 1:1 */
        val IgnoreMultiplicationForCaching = GameContext(ignoreConditionals = true)

        /**
         * Builds out a GameContext from the given string.
         *
         * Currently, this only covers the GameContext civInfo, unit, city, tile, and ignoreConditionals.
         *
         * @param value A string whose values are split by the given splitCharacter. For example: unitId=123&cityId=123
         * @param viewingCiv The acting Civilization.
         * @param separator The string that will be used when splitting t that will be used when spliting the value.
         * @return A GameContext object including the parameters civInfo, city, tile, ignoreConditionals, and unit.
         * @see toSerializedString()
         */
        @Readonly
        fun fromSerializedString(value: String, viewingCiv: Civilization, separator: String = Constants.stringSplitCharacter.toString()) : GameContext {
            val splitString = value.split(separator)
            var civInfo = viewingCiv
            var city: City? = null
            var unit: MapUnit? = null
            var tile: Tile? = null
            var ignoreConditionals = false
            var tileX: Int? = null
            var tileY: Int? = null
            for (entry in splitString) {
                val (key, value) = entry.split("=", limit = 2).let {
                    it[0] to it.getOrElse(1) { "" }
                }
                when (key) {
                    "civName" -> civInfo = viewingCiv.gameInfo.getCivilization(value)
                    "cityId" -> city = viewingCiv.gameInfo.getCities().firstOrNull { it.id == value }
                    "tileX" -> tileX = value.toIntOrNull()
                    "tileY" -> tileY = value.toIntOrNull()
                    "ignoreConditionals" -> ignoreConditionals = true
                    "unitId" -> {
                        val unitId = value.toIntOrNull()
                        if (unitId != null) {
                            unit = viewingCiv.units.getUnitById(unitId)
                        }
                    }
                }
            }
            if (tileX != null && tileY != null) {
                tile = viewingCiv.gameInfo.tileMap.getIfTileExistsOrNull(tileX, tileY)
            }
            else {
                tile = unit?.getTile() ?: city?.getCenterTileOrNull()
            }
            return GameContext(civInfo, city, unit, tile, ignoreConditionals = ignoreConditionals)
        }
    }

    /**
     * Creates a string of the parameters available within the GameContext.
     *
     * Currently, this only covers the GameContext civInfo, unit, city, tile, and ignoreConditionals.
     *
     * @param separator The character which will be used to join the values.
     * @return A string containing all parameters joined by the separator. For example: "unitId=123&cityId=456"
     * @see fromSerializedString()
     */
    @Readonly
    fun toSerializedString(separator: String = Constants.stringSplitCharacter.toString()) : String {
        val output = mutableListOf<String>()
        if (civInfo != null) output.add("civName=${civInfo.civName}")
        if (unit != null) output.add("unitId=${unit.id}")
        if (city != null) output.add("cityId=${city.id}")
        if (ignoreConditionals) output.add("ignoreConditionals")
        if (tile != null) {
            output.add("tileX=${tile.position.x.toInt()}")
            output.add("tileY=${tile.position.y.toInt()}")
        }
        return output.joinToString(separator)
    }

    /**  Used ONLY for stateBasedRandom in [Conditionals.conditionalApplies] to prevent save scumming on [UniqueType.ConditionalChance] */
    @Readonly
    override fun hashCode(): Int {
        fun Civilization?.hash() = this?.civName?.hashCode() ?: 0
        fun City?.hash() = this?.id?.hashCode() ?: 0
        fun Tile?.hash() = this?.position?.hashCode() ?: 0
        fun MapUnit?.hash() = if (this == null) 0 else name.hashCode() + (if (hasTile()) 17 * currentTile.hash() else 0)
        fun ICombatant?.hash() = if (this == null) 0
            else if (this is MapUnitCombatant) unit.hash()  // line only serves as `lateinit currentTile not initialized` guard
            else getName().hashCode() + 17 * getTile().hash()
        fun CombatAction?.hash() = this?.name?.hashCode() ?: 0
        fun Region?.hash() = this?.rect?.hashCode() ?: 0

        var result = relevantCiv.hash()
        result = 31 * result + relevantCity.hash()
        result = 31 * result + relevantUnit.hash()
        result = 31 * result + relevantTile.hash()
        result = 31 * result + ourCombatant.hash()
        result = 31 * result + theirCombatant.hash()
        result = 31 * result + attackedTile.hash()
        result = 31 * result + combatAction.hash()
        result = 31 * result + region.hash()
        result = 31 * result + ignoreConditionals.hashCode()
        return result
    }
}


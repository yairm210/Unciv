package com.unciv.models.ruleset.unique

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
import com.unciv.utils.hashOf
import yairm210.purity.annotations.Readonly
import kotlin.random.Random

data class GameContext(
    val civInfo: Civilization? = null,
    val city: City? = null,
    val unit: MapUnit? = null,
    val tile: Tile? = null,

    val ourCombatant: ICombatant? = null,
    val theirCombatant: ICombatant? = null,
    val attackedTile: Tile? = null,
    val combatAction: CombatAction? = null,

    val otherCiv: Civilization? = null,

    val region: Region? = null,
    // tile and region do not deduce gameInfo because that field is not set during MapGeneration,
    // and querying if its initialized is non-trivial
    val gameInfo: GameInfo? = (civInfo?.gameInfo) ?: (city?.civ?.gameInfo) ?: (unit?.civ?.gameInfo) ?:
        (ourCombatant?.getCivInfo()?.gameInfo) ?: (theirCombatant?.getCivInfo()?.gameInfo) ?: (attackedTile?.tileMap?.gameInfo) ?:
        (otherCiv?.gameInfo),

    val ignoreFieldsBits: Int = CONSIDER_ALL_FIELDS_MASK,
) {
    constructor(city: City) : this(city.civ, city, tile = city.getCenterTileOrNull(), gameInfo = city.civ.gameInfo)
    constructor(unit: MapUnit) : this(unit.civ, unit = unit, tile = if (unit.hasTile()) unit.getTile() else null, gameInfo = unit.civ.gameInfo)
    constructor(civ: Civilization, civ2: Civilization?=null) : this(civ, otherCiv = civ2, gameInfo = civ.gameInfo)
    constructor(ourCombatant: ICombatant, theirCombatant: ICombatant? = null,
                attackedTile: Tile? = null, combatAction: CombatAction? = null) : this(
        ourCombatant.getCivInfo(),
        (ourCombatant as? CityCombatant)?.city,
        (ourCombatant as? MapUnitCombatant)?.unit,
        ourCombatant.getTile(),
        ourCombatant,
        theirCombatant,
        attackedTile,
        combatAction,
        theirCombatant?.getCivInfo(),
        gameInfo = ourCombatant.getCivInfo().gameInfo
    )
    
    val considerCiv get() = ignoreFieldsBits and IGNORE_CIVILIZATION_BIT == 0
    val considerCity get() = ignoreFieldsBits and IGNORE_CITY_BIT == 0
    val considerUnit get() = ignoreFieldsBits and IGNORE_UNIT_BIT == 0
    val considerTile get() = ignoreFieldsBits and IGNORE_TILE_BIT == 0
    val considerOurCombatant get() = ignoreFieldsBits and IGNORE_OUR_COMBATANT_BIT == 0
    val considerTheirCombatant get() = ignoreFieldsBits and IGNORE_THEIR_COMBATANT_BIT == 0
    val considerAttackedTile get() = ignoreFieldsBits and IGNORE_ATTACKED_TILE_BIT == 0
    val considerAction get() = ignoreFieldsBits and IGNORE_ACTION_BIT == 0
    val considerRegion get() = ignoreFieldsBits and IGNORE_REGION_BIT == 0
    val considerGameInfo get() = ignoreFieldsBits and IGNORE_GAME_INFO_BIT == 0
    val ignoreConditionals get() = ignoreFieldsBits == IGNORE_ALL_CONDITIONALS_MASK

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
    fun stateBasedRandom(caller: String, seed: Int=31) =
        Random(hashOf(caller.hashCode(), seed, this.hashCode()))
        
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
        const val IGNORE_CIVILIZATION_BIT       = 1 shl 0
        const val IGNORE_CITY_BIT               = 1 shl 1
        const val IGNORE_UNIT_BIT               = 1 shl 2
        const val IGNORE_TILE_BIT               = 1 shl 3
        const val IGNORE_OUR_COMBATANT_BIT      = 1 shl 4
        const val IGNORE_THEIR_COMBATANT_BIT    = 1 shl 5
        const val IGNORE_ATTACKED_TILE_BIT      = 1 shl 6
        const val IGNORE_ACTION_BIT             = 1 shl 7
        const val IGNORE_REGION_BIT             = 1 shl 8
        const val IGNORE_GAME_INFO_BIT          = 1 shl 9
        const val IGNORE_ALL_CONDITIONALS_MASK  = (1 shl 10) -1
        const val CONSIDER_ALL_FIELDS_MASK      = 0
        const val CONSIDER_ONLY_TILES_MASK      = IGNORE_TILE_BIT.inv()

        val IgnoreConditionals = GameContext(ignoreFieldsBits = IGNORE_ALL_CONDITIONALS_MASK)
        val EmptyState = GameContext()
        /** When caching uniques, we need to cache them unmultiplied, and apply multiplication only on retrieval from cache
         * This state lets the multiplication function know that it's always 1:1 */
        val IgnoreMultiplicationForCaching = GameContext(ignoreFieldsBits = IGNORE_ALL_CONDITIONALS_MASK)
    }

    /**  Used ONLY for stateBasedRandom in [Conditionals.conditionalApplies] to prevent save scumming on [UniqueType.ConditionalChance] */
    @Readonly
    override fun hashCode(): Int {
        fun Civilization?.hash() = this?.civID?.hashCode() ?: 0
        fun City?.hash() = this?.id?.hashCode() ?: 0
        fun Tile?.hash() = this?.position?.hashCode() ?: 0
        fun MapUnit?.hash() = if (this == null) 0 else name.hashCode() +
            (if (hasTile()) 17 * currentTile.hash() else 0) +
            (17 * currentMovement).hashCode()
        fun ICombatant?.hash() = if (this == null) 0
            else if (this is MapUnitCombatant) unit.hash()  // line only serves as `lateinit currentTile not initialized` guard
            else getName().hashCode() + 17 * getTile().hash()
        fun CombatAction?.hash() = this?.name?.hashCode() ?: 0
        fun Region?.hash() = this?.rect?.hashCode() ?: 0

        val hash = hashOf(
            gameInfo?.turns?.hashCode() ?: 0,
            (gameInfo?.gameId?.hashCode() ?: gameInfo?.tileMap?.mapParameters?.seed?.hashCode() ?: 0),
            relevantCiv.hash(),
            relevantCity.hash(),
            relevantUnit.hash(),
            relevantTile.hash(),
            ourCombatant.hash(),
            theirCombatant.hash(),
            attackedTile.hash(),
            combatAction.hash(),
            otherCiv.hash(),
            region.hash(),
            ignoreFieldsBits.hashCode()
        )
        return hash
    }


}


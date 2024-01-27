package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.mapregions.Region
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

data class StateForConditionals(
    val civInfo: Civilization? = null,
    val city: City? = null,
    val unit: MapUnit? = null,
    val tile: Tile? = null,

    val ourCombatant: ICombatant? = null,
    val theirCombatant: ICombatant? = null,
    val attackedTile: Tile? = null,
    val combatAction: CombatAction? = null,

    val region: Region? = null,

    val ignoreConditionals: Boolean = false,
) {
    constructor(city: City) : this(city.civ, city, tile = city.getCenterTile())
    constructor(unit: MapUnit) : this(unit.civ, unit = unit, tile = unit.currentTile)
    constructor(ourCombatant: ICombatant) : this(
        ourCombatant.getCivInfo(),
        unit = (ourCombatant as MapUnitCombatant).unit,
        tile = ourCombatant.getTile(),
        ourCombatant = ourCombatant,
    )
    constructor(ourCombatant: ICombatant, theirCombatant: ICombatant) : this(
        ourCombatant.getCivInfo(),
        unit = (ourCombatant as MapUnitCombatant).unit,
        tile = ourCombatant.getTile(),
        ourCombatant = ourCombatant,
        theirCombatant = theirCombatant,
    )

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
    }
}

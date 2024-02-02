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

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
    }
}

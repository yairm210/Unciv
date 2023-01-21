package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.mapgenerator.Region

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

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
    }
}

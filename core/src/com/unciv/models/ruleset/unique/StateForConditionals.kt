package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.mapgenerator.Region

data class StateForConditionals(
    val civInfo: CivilizationInfo? = null,
    val cityInfo: CityInfo? = null,
    val unit: MapUnit? = null,
    val tile: TileInfo? = null,

    val ourCombatant: ICombatant? = null,
    val theirCombatant: ICombatant? = null,
    val attackedTile: TileInfo? = null,
    val combatAction: CombatAction? = null,

    val region: Region? = null,

    val ignoreConditionals: Boolean = false,
) {

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
    }
}
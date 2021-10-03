package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo

data class StateForConditionals(
    val civInfo: CivilizationInfo? = null,
    val cityInfo: CityInfo? = null,
    val unit: MapUnit? = null,
    
    val attacker: ICombatant? = null,
    val defender: ICombatant? = null,
//    val attackedTile: TileInfo? = null,
    val combatAction: CombatAction? = null,
)
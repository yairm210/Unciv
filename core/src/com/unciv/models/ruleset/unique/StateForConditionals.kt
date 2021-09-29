package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.ICombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo

data class StateForConditionals(
    val civInfo: CivilizationInfo? = null,
    val cityInfo: CityInfo? = null,
    val defender: ICombatant? = null,
//    val attacker: ICombatant? = null,
//    val attackedTile: TileInfo? = null,
//    val combatAction: CombatAction? = null,
)

//enum class CombatAction() {
//    Attack,
//    Defend,
//    Intercept,
//}
package com.unciv.logic.replay

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileMap

/**
 * Maps all state-changing actions
 */
object ActionMapper {

    lateinit var gameInfo: GameInfo
    var tileMap = gameInfo.tileMap

    /**
     * applies recorded [action] to civilization
     */
    fun applyAction(civInfo: CivilizationInfo, action: Action) {
        when (action.type) {
            ActionType.move -> { moveUnit(action.data as MovementData) }
            ActionType.build -> { addToConstructionQue(action.data as ConstructionData) }
            ActionType.research -> { researchTech(civInfo, action.data as String) }
        }
    }

    private fun moveUnit(data: MovementData) {
        val tile = tileMap[data.origin]
        val unit = if (data.unitType.isAirUnit()) tile.airUnits[data.airUnitIndex]
        else tile.getUnits().first { it.type == data.unitType }

        unit.movement.moveToTile(tileMap[data.destination])
    }

    private fun addToConstructionQue(construction: ConstructionData) {
        val city = tileMap[construction.position].getCity()!!
        city.cityConstructions.constructionQueue.add(construction.constructionName)
    }

    private fun researchTech(civInfo: CivilizationInfo, name: String) {
        civInfo.tech.techsToResearch.add(name)
    }


}
package com.unciv.logic.replay

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileMap

/**
 * Maps all state-changing actions
 */
object ActionMapper {

    lateinit var tileMap: TileMap
    /**
     * applies recorded [action] to civilization
     */
    fun apply(civInfo: CivilizationInfo, action: Action) {
        when (action.type) {
            ActionType.move -> { moveUnit(action.data as MovementData) }
            ActionType.build -> { addToConstructionQue(action.data as ConstructionData) }
            ActionType.research -> { researchTech(civInfo, action.data as String) }
        }
    }

    private fun moveUnit(movement: MovementData) {
        val tile = tileMap[movement.origin]
        val unit = tile.getUnits().first { it.type == movement.unitType }
        unit.movement.moveToTile(tileMap[movement.destination])
    }

    private fun addToConstructionQue(construction: ConstructionData) {
        val city = tileMap[construction.position].getCity()!!
        city.cityConstructions.constructionQueue.add(construction.constructionName)
    }

    private fun researchTech(civInfo: CivilizationInfo, name: String) {
        civInfo.tech.techsToResearch.add(name)
    }


}
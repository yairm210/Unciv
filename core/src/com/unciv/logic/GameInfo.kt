package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.UnitType
import com.unciv.ui.utils.getRandom

class GameInfo {

    var notifications = mutableListOf<Notification>()

    var tutorial = mutableListOf<String>()
    var civilizations = mutableListOf<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 1


    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]
    fun getBarbarianCivilization(): CivilizationInfo = civilizations[1]


    fun addNotification(text: String, location: Vector2?) {
        notifications.add(Notification(text, location))
    }

    fun nextTurn() {
        notifications.clear()

        for (civInfo in civilizations) civInfo.nextTurn()

        tileMap.values.filter { it.unit!=null }.map { it.unit!! }.forEach { it.nextTurn() }

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations){
            for (city in civInfo.cities)
                city.cityStats.update()
            civInfo.happiness = civInfo.getHappinessForNextTurn()
            if(!civInfo.isPlayerCivilization())
                automateMoves(civInfo)
        }

        if(turns%10 == 0){ // every 10 turns add a barbarian in a random place
            placeBarbarianUnit()
        }

        turns++
    }

    fun placeBarbarianUnit() {
        val playerViewableTiles = getPlayerCivilization().getViewableTiles().toHashSet()
        val viableTiles = tileMap.values.filterNot { playerViewableTiles.contains(it) || it.unit!=null }
        tileMap.placeUnitNearTile(viableTiles.getRandom().position,"Warrior",getBarbarianCivilization())
    }

    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        for (civInfo in civilizations) {
            civInfo.gameInfo = this
            civInfo.setTransients()
        }

        for (tile in tileMap.values.filter { it.unit!=null })
            tile.unit!!.civInfo = civilizations.first { it.civName == tile.unit!!.owner }


        for (civInfo in civilizations)
            for (cityInfo in civInfo.cities)
                cityInfo.cityStats.update()
    }


    private fun automateMoves(civInfo: CivilizationInfo) {
        for(unit in civInfo.getCivUnits()){

            fun healUnit(){
                // If we're low on health then heal
                // todo: go to a more defensible place if there is one
                val tilesInDistance = unit.getDistanceToTiles().keys
                val unitTile=unit.getTile()

                // Go to friendly tile if within distance - better healing!
                val friendlyTile = tilesInDistance.firstOrNull { it.owner==unit.owner && it.unit==null }
                if(unitTile.owner!=unit.owner && friendlyTile!=null){
                    unit.moveToTile(friendlyTile)
                    return
                }

                // Or at least get out of enemy territory yaknow
                val neutralTile = tilesInDistance.firstOrNull { it.owner==null && it.unit==null }
                if(unitTile.owner!=unit.owner && unitTile.owner!=null && neutralTile!=null){
                    unit.moveToTile(neutralTile)
                    return
                }
            }

            if(unit.health < 50) {
                healUnit()
                continue
            } // do nothing but heal

            // if there is an attackable unit in the vicinity, attack!
            val attackableTiles = civInfo.getViewableTiles()
                    .filter { it.unit != null && it.unit!!.owner != civInfo.civName && !it.isCityCenter }.toHashSet()
            val distanceToTiles = unit.getDistanceToTiles()
            val unitTileToAttack = distanceToTiles.keys.firstOrNull{ attackableTiles.contains(it)}

            if(unitTileToAttack!=null){
                val unitToAttack =unitTileToAttack.unit!!
                if(unitToAttack.getBaseUnit().unitType == UnitType.Civilian){ // kill
                    if(unitToAttack.civInfo == getPlayerCivilization())
                        addNotification("Our "+unitToAttack.name+" was destroyed by an enemy "+unit.name+"!", unitTileToAttack.position)
                    unitTileToAttack.unit=null
                    unit.headTowards(unitTileToAttack.position)
                    continue
                }

                val damageToAttacker = Battle(this).calculateDamageToAttacker(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))
                if(damageToAttacker < unit.health) { // don't attack if we'll die from the attack
                    unit.headTowards(unitTileToAttack.position)
                    Battle(this).attack(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))
                    continue
                }
            }

            if(unit.health < 80){
                healUnit()
                continue
            } // do nothing but heal until 80 health



            // else, if there is a reachable spot from which we can attack this turn
            // (say we're an archer and there's a unit 3 tiles away), go there and attack
            // todo

            // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
            val closestUnit = tileMap.getTilesInDistance(unit.getTile().position, 5)
                    .firstOrNull{ attackableTiles.contains(it) }

            if(closestUnit!=null){
                unit.headTowards(closestUnit.position)
                continue
            }

            // else, go to a random space
            unit.moveToTile(distanceToTiles.keys.toList().getRandom())
        }
    }

}

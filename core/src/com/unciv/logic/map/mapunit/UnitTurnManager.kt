package com.unciv.logic.map.mapunit

import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType

class UnitTurnManager(val unit: MapUnit) {

    fun endTurn() {
        unit.movement.clearPathfindingCache()

        for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponTurnEnd))
            UniqueTriggerActivation.triggerUnique(unique, unit)

        if (unit.hasMovement()
                && unit.getTile().improvementInProgress != null
                && unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!)
        ) {
            val tile = unit.getTile()
            if (tile.doWorkerTurn(unit))
                tile.getCity()?.shouldReassignPopulation = true
        }

        if (!unit.hasUnitMovedThisTurn()
            && (unit.isFortified() || (unit.isGuarding() && unit.canFortify()))
            && unit.turnsFortified < 2) {
            unit.turnsFortified++
        }
        if (!unit.isFortified() && !unit.isGuarding())
            unit.turnsFortified = 0

        if (!unit.hasUnitMovedThisTurn() || unit.hasUnique(UniqueType.HealsEvenAfterAction))
            healUnit()

        if (unit.isPreparingParadrop() || unit.isPreparingAirSweep())
            unit.action = null

        if (unit.hasUnique(UniqueType.ReligiousUnit)
                && unit.getTile().getOwner() != null
                && !unit.getTile().getOwner()!!.isCityState
                && !unit.civ.diplomacyFunctions.canPassThroughTiles(unit.getTile().getOwner()!!)
        ) {
            val lostReligiousStrength =
                    unit.getMatchingUniques(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)
                        .map { it.params[0].toInt() }
                        .minOrNull()
            if (lostReligiousStrength != null)
                unit.religiousStrengthLost += lostReligiousStrength
            if (unit.religiousStrengthLost >= unit.baseUnit.religiousStrength) {
                unit.civ.addNotification("Your [${unit.name}] lost its faith after spending too long inside enemy territory!",
                    unit.getTile().position, NotificationCategory.Units, unit.name)
                unit.destroy()
            }
        }

        doCitadelDamage()
        doTerrainDamage()

        unit.addMovementMemory()

        for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponEndingTurnInTile)
                { unit.getTile().matchesFilter(it.params[0], unit.civ) })
            UniqueTriggerActivation.triggerUnique(unique, unit)
    }


    private fun healUnit() {
        val amountToHealBy = unit.getHealAmountForCurrentTile()
        if (amountToHealBy == 0) return

        unit.healBy(amountToHealBy)
    }


    private fun doCitadelDamage() {
        // Check for Citadel damage - note: 'Damage does not stack with other Citadels'
        val (citadelTile, damage) = unit.currentTile.neighbors
            .filter {
                it.getOwner() != null
                        && it.getUnpillagedImprovement() != null
                        && unit.civ.isAtWarWith(it.getOwner()!!)
            }.map { tile ->
                tile to tile.getTileImprovement()!!.getMatchingUniques(UniqueType.DamagesAdjacentEnemyUnits, tile.stateThisTile)
                    .sumOf { it.params[0].toInt() }
            }.maxByOrNull { it.second }
            ?: return
        if (damage == 0) return
        unit.takeDamage(damage)
        val improvementName = citadelTile.improvement!!  // guarded by `getUnpillagedImprovement() != null` above
        val improvementIcon = "ImprovementIcons/$improvementName"
        val locations = LocationAction(citadelTile.position, unit.currentTile.position)
        if (unit.health <= 0) {
            unit.civ.addNotification(
                "An enemy [$improvementName] has destroyed our [${unit.name}]",
                locations,
                NotificationCategory.War,
                improvementIcon, NotificationIcon.Death, unit.name
            )
            citadelTile.getOwner()?.addNotification(
                "Your [$improvementName] has destroyed an enemy [${unit.name}]",
                locations,
                NotificationCategory.War,
                improvementIcon, NotificationIcon.Death, unit.name
            )
            unit.destroy()
        } else unit.civ.addNotification(
            "An enemy [$improvementName] has attacked our [${unit.name}]",
            locations,
            NotificationCategory.War,
            improvementIcon, NotificationIcon.War, unit.name
        )
    }


    private fun doTerrainDamage() {
        val tileDamage = unit.getDamageFromTerrain()
        if (tileDamage == 0) return

        unit.takeDamage(tileDamage)

        if (unit.isDestroyed) {
            unit.civ.addNotification(
                "Our [${unit.name}] took [$tileDamage] tile damage and was destroyed",
                unit.currentTile.position,
                NotificationCategory.Units,
                unit.name,
                NotificationIcon.Death
            )
        } else unit.civ.addNotification(
            "Our [${unit.name}] took [$tileDamage] tile damage",
            MapUnitAction(unit),
            NotificationCategory.Units,
            unit.name
        )
    }


    fun startTurn() {
        unit.movement.clearPathfindingCache()
        unit.currentMovement = unit.getMaxMovement().toFloat()
        unit.attacksThisTurn = 0
        unit.due = true

        for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponTurnStart))
            UniqueTriggerActivation.triggerUnique(unique, unit)

        // Wake sleeping units if there's an enemy in vision range:
        // Military units always but civilians only if not protected.
        if (unit.isSleeping() && (unit.isMilitary() || (unit.currentTile.militaryUnit == null && !unit.currentTile.isCityCenter())) &&
                unit.currentTile.getTilesInDistance(3).any {
                    it.militaryUnit != null && it in unit.civ.viewableTiles && it.militaryUnit!!.civ.isAtWarWith(unit.civ)
                }
        )  unit.action = null

        if (unit.action != null && unit.health > 99)
            if (unit.isActionUntilHealed()) {
                unit.action = null // wake up when healed
            }

        val tileOwner = unit.getTile().getOwner()
        if (tileOwner != null
                && !unit.cache.canEnterForeignTerrain
                && !unit.civ.diplomacyFunctions.canPassThroughTiles(tileOwner)
                && !tileOwner.isCityState
        ) // if an enemy city expanded onto this tile while I was in it
            unit.movement.teleportToClosestMoveableTile()

        unit.addMovementMemory()
        unit.attacksSinceTurnStart.clear()
        
        for (status in unit.statusMap.values.toList()){
            status.turnsLeft--
            if (status.turnsLeft <= 0) unit.removeStatus(status.name)
        }
        unit.updateUniques()
    }
}

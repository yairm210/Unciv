package com.unciv.logic.map.mapunit

import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.ruleset.unique.UniqueType

class UnitTurnManager(val unit: MapUnit) {

    fun endTurn() {
        unit.movement.clearPathfindingCache()
        if (unit.currentMovement > 0
                && unit.getTile().improvementInProgress != null
                && unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!)
        ) unit.workOnImprovement()
        if (unit.currentMovement == unit.getMaxMovement().toFloat() && unit.isFortified() && unit.turnsFortified < 2) {
            unit.turnsFortified++
        }
        if (!unit.isFortified())
            unit.turnsFortified = 0

        if (unit.currentMovement == unit.getMaxMovement().toFloat() // didn't move this turn
                || unit.hasUnique(UniqueType.HealsEvenAfterAction)
        ) healUnit()

        if (unit.action != null && unit.health > 99)
            if (unit.isActionUntilHealed()) {
                unit.action = null // wake up when healed
            }

        if (unit.isPreparingParadrop() || unit.isPreparingAirSweep())
            unit.action = null

        if (unit.hasUnique(UniqueType.ReligiousUnit)
                && unit.getTile().getOwner() != null
                && !unit.getTile().getOwner()!!.isCityState()
                && !unit.civInfo.diplomacyFunctions.canPassThroughTiles(unit.getTile().getOwner()!!)
        ) {
            val lostReligiousStrength =
                    unit.getMatchingUniques(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)
                        .map { it.params[0].toInt() }
                        .minOrNull()
            if (lostReligiousStrength != null)
                unit.religiousStrengthLost += lostReligiousStrength
            if (unit.religiousStrengthLost >= unit.baseUnit.religiousStrength) {
                unit.civInfo.addNotification("Your [${unit.name}] lost its faith after spending too long inside enemy territory!",
                    unit.getTile().position, NotificationCategory.Units, unit.name)
                unit.destroy()
            }
        }

        doCitadelDamage()
        doTerrainDamage()

        unit.addMovementMemory()
    }


    private fun healUnit() {
        if (unit.isEmbarked()) return // embarked units can't heal
        if (unit.health >= 100) return // No need to heal if at max health
        if (unit.hasUnique(UniqueType.HealOnlyByPillaging, checkCivInfoUniques = true)) return

        val amountToHealBy = unit.rankTileForHealing(unit.getTile())
        if (amountToHealBy == 0) return

        unit.healBy(amountToHealBy)
    }


    private fun doCitadelDamage() {
        // Check for Citadel damage - note: 'Damage does not stack with other Citadels'
        val (citadelTile, damage) = unit.currentTile.neighbors
            .filter {
                it.getOwner() != null
                        && it.getUnpillagedImprovement() != null
                        && unit.civInfo.isAtWarWith(it.getOwner()!!)
            }.map { tile ->
                tile to tile.getTileImprovement()!!.getMatchingUniques(UniqueType.DamagesAdjacentEnemyUnits)
                    .sumOf { it.params[0].toInt() }
            }.maxByOrNull { it.second }
            ?: return
        if (damage == 0) return
        unit.health -= damage
        val locations = LocationAction(citadelTile.position, unit.currentTile.position)
        if (unit.health <= 0) {
            unit.civInfo.addNotification(
                "An enemy [Citadel] has destroyed our [${unit.name}]",
                locations,
                NotificationCategory.War,
                NotificationIcon.Citadel, NotificationIcon.Death, unit.name
            )
            citadelTile.getOwner()?.addNotification(
                "Your [Citadel] has destroyed an enemy [${unit.name}]",
                locations,
                NotificationCategory.War,
                NotificationIcon.Citadel, NotificationIcon.Death, unit.name
            )
            unit.destroy()
        } else unit.civInfo.addNotification(
            "An enemy [Citadel] has attacked our [${unit.name}]",
            locations,
            NotificationCategory.War,
            NotificationIcon.Citadel, NotificationIcon.War, unit.name
        )
    }


    private fun doTerrainDamage() {
        val tileDamage = unit.getDamageFromTerrain()
        unit.health -= tileDamage

        if (unit.health <= 0) {
            unit.civInfo.addNotification(
                "Our [${unit.name}] took [$tileDamage] tile damage and was destroyed",
                unit.currentTile.position,
                NotificationCategory.Units,
                unit.name,
                NotificationIcon.Death
            )
            unit.destroy()
        } else if (tileDamage > 0) unit.civInfo.addNotification(
            "Our [${unit.name}] took [$tileDamage] tile damage",
            unit.currentTile.position,
            NotificationCategory.Units,
            unit.name
        )
    }


    fun startTurn() {
        unit.movement.clearPathfindingCache()
        unit.currentMovement = unit.getMaxMovement().toFloat()
        unit.attacksThisTurn = 0
        unit.due = true

        // Hakkapeliitta movement boost
        if (unit.getTile().getUnits().count() > 1) {
            // For every double-stacked tile, check if our cohabitant can boost our speed
            for (tileUnit in unit.getTile().getUnits())
            {
                if (tileUnit == unit) continue

                if (tileUnit.getMatchingUniques(UniqueType.TransferMovement)
                            .any { tileUnit.matchesFilter(it.params[0]) } )
                    tileUnit.currentMovement = maxOf(tileUnit.getMaxMovement().toFloat(), tileUnit.getMaxMovement().toFloat())
            }
        }

        // Wake sleeping units if there's an enemy in vision range:
        // Military units always but civilians only if not protected.
        if (unit.isSleeping() && (unit.isMilitary() || (unit.currentTile.militaryUnit == null && !unit.currentTile.isCityCenter())) &&
                unit.currentTile.getTilesInDistance(3).any {
                    it.militaryUnit != null && it in unit.civInfo.viewableTiles && it.militaryUnit!!.civInfo.isAtWarWith(unit.civInfo)
                }
        )  unit.action = null

        val tileOwner = unit.getTile().getOwner()
        if (tileOwner != null
                && !unit.canEnterForeignTerrain
                && !unit.civInfo.diplomacyFunctions.canPassThroughTiles(tileOwner)
                && !tileOwner.isCityState()) // if an enemy city expanded onto this tile while I was in it
            unit.movement.teleportToClosestMoveableTile()

        unit.addMovementMemory()
        unit.attacksSinceTurnStart.clear()
    }

}

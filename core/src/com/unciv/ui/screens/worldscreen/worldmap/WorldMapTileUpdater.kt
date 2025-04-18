package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.unit.CityLocationTileRanker
import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Spy
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.colorFromRGB

object WorldMapTileUpdater {

     fun WorldMapHolder.updateTiles(viewingCiv: Civilization) {

        if (isMapRevealEnabled(viewingCiv)) {
            // Only needs to be done once - this is so the minimap will also be revealed
            tileGroups.values.forEach {
                it.tile.setExplored(viewingCiv, true)
                it.isForceVisible = true } // So we can see all resources, regardless of tech
        }

        // General update of all tiles
        val uniqueCache = LocalUniqueCache(true)
        for (tileGroup in tileGroups.values)
            tileGroup.update(viewingCiv, uniqueCache)

        // Update tiles according to selected unit/city
        val unitTable = worldScreen.bottomUnitTable
        when {
            unitTable.selectedSpy != null -> {
                updateTilesForSelectedSpy(unitTable.selectedSpy!!)
            }
            unitTable.selectedCity != null -> {
                val city = unitTable.selectedCity!!
                updateBombardableTilesForSelectedCity(city)
                // We still want to show road paths to the selected city if they are present
                if (unitTable.selectedUnitIsConnectingRoad) {
                    updateTilesForSelectedUnit(unitTable.selectedUnits[0])
                }
            }
            unitTable.selectedUnit != null -> {
                for (unit in unitTable.selectedUnits) {
                    updateTilesForSelectedUnit(unit)
                }
            }
            unitActionOverlays.isNotEmpty() -> {
                removeUnitActionOverlay()
            }
        }

        // Same as below - randomly, tileGroups doesn't seem to contain the selected tile, and this doesn't seem reproducible
        tileGroups[selectedTile]?.layerOverlay?.showHighlight(Color.WHITE)

        zoom(scaleX) // zoom to current scale, to set the size of the city buttons after "next turn"
    }

    private fun WorldMapHolder.updateTilesForSelectedUnit(unit: MapUnit) {

        val tileGroup = tileGroups[unit.getTile()] ?: return

        // Update flags for units which have them
        if (!unit.baseUnit.movesLikeAirUnits) {
            tileGroup.layerUnitFlag.selectFlag(unit)
        }

        // Fade out less relevant images if a military unit is selected
        if (unit.isMilitary()) {
            for (group in tileGroups.values) {

                // Fade out population icons
                group.layerMisc.dimPopulation(true)

                val shownImprovement = group.tile.getShownImprovement(unit.civ)

                // Fade out improvement icons (but not barb camps or ruins)
                if (shownImprovement != null && shownImprovement != Constants.barbarianEncampment
                    && !unit.civ.gameInfo.ruleset.tileImprovements[shownImprovement]!!.isAncientRuinsEquivalent())
                    group.layerImprovement.dimImprovement(true)
            }
        }

        // Z-Layer: 0
        // Highlight suitable tiles in swapping-mode
        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            val unitSwappableTiles = unit.movement.getUnitSwappableTiles()
            val swapUnitsTileOverlayColor = Color.PURPLE
            for (tile in unitSwappableTiles)  {
                tileGroups[tile]!!.layerOverlay.showHighlight(swapUnitsTileOverlayColor,
                    if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
            }
            // In swapping-mode we don't want to show other overlays
            return
        }

        // Z-Layer: 0
        // Highlight suitable tiles in road connecting mode
        if (worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad) {
            if (unit.currentTile.ruleset.roadImprovement == null) return
            val validTiles = unit.civ.gameInfo.tileMap.tileList.filter {
                MapPathing.isValidRoadPathTile(unit, it)
            }
            val connectRoadTileOverlayColor = Color.RED
            for (tile in validTiles)  {
                tileGroups[tile]!!.layerOverlay.showHighlight(connectRoadTileOverlayColor, 0.3f)
            }

            if (unitConnectRoadPaths.containsKey(unit)) {
                for (tile in unitConnectRoadPaths[unit]!!) {
                    tileGroups[tile]!!.layerOverlay.showHighlight(Color.ORANGE, 0.8f)
                }
            }

            // In road connecting mode we don't want to show other overlays
            return
        }

        val isAirUnit = unit.baseUnit.movesLikeAirUnits
        val moveTileOverlayColor = if (unit.isPreparingParadrop()) Color.BLUE else Color.WHITE
        val tilesInMoveRange = unit.movement.getReachableTilesInCurrentTurn()
        // Prepare special Nuke blast radius display
        val nukeBlastRadius = if (unit.isNuclearWeapon() && selectedTile != null && selectedTile != unit.getTile())
            unit.getNukeBlastRadius() else -1

        // Z-Layer: 1
        // Highlight tiles within movement range
        for (tile in tilesInMoveRange) {
            val group = tileGroups[tile]!!

            // Air-units have additional highlights
            if (isAirUnit && !unit.isPreparingAirSweep()) {
                if (nukeBlastRadius >= 0 && tile.aerialDistanceTo(selectedTile!!) <= nukeBlastRadius) {
                    // The tile is within the nuke blast radius
                    group.layerMisc.overlayTerrain(Color.FIREBRICK, 0.6f)
                } else if (tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()) {
                    // The tile is within attack range
                    group.layerMisc.overlayTerrain(Color.RED)
                } else if (tile.isExplored(worldScreen.viewingCiv) && tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()*2) {
                    // The tile is within move range
                    group.layerMisc.overlayTerrain(if (unit.movement.canMoveTo(tile)) Color.WHITE else Color.BLUE)
                }
            }

            // Highlight tile unit can move to
            if (unit.movement.canMoveTo(tile) ||
                unit.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !unit.baseUnit.movesLikeAirUnits
            ) {
                if (UncivGame.Current.settings.useCirclesToIndicateMovableTiles) {
                    val alpha = if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f
                    group.layerOverlay.showHighlight(moveTileOverlayColor, alpha)
                }

                else group.layerMisc.overlayTerrain(moveTileOverlayColor, 0.4f)
            }

        }

        // Z-Layer: 2
        // Add back in the red markers for Air Unit Attack range since they can't move, but can still attack
        if (unit.cache.cannotMove && isAirUnit && !unit.isPreparingAirSweep()) {
            val tilesInAttackRange = unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getRange()))
            for (tile in tilesInAttackRange) {
                // The tile is within attack range
                tileGroups[tile]!!.layerOverlay.showHighlight(Color.RED, 0.3f)
            }
        }

        // Z-Layer: 3
        // Movement paths
        if (unitMovementPaths.containsKey(unit)) {
            for (tile in unitMovementPaths[unit]!!) {
                tileGroups[tile]!!.layerOverlay.showHighlight(Color.SKY, 0.8f)
            }
        }

        // Z-Layer: 4
        // Highlight road path for workers currently connecting roads
        if (unit.isAutomatingRoadConnection()) {
            if (unit.automatedRoadConnectionPath == null) return
            val currTileIndex = unit.automatedRoadConnectionPath!!.indexOf(unit.currentTile.position)
            if (currTileIndex != -1) {
                val futureTiles = unit.automatedRoadConnectionPath!!.filterIndexed { index, _ ->
                    index > currTileIndex
                }.map { tilePos ->
                    tileMap[tilePos]
                }
                for (tile in futureTiles) {
                    tileGroups[tile]!!.layerOverlay.showHighlight(Color.ORANGE, if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
                }
            }
        }

        // Z-Layer: 5
        // Highlight movement destination tile
        if (unit.isMoving()) {
            tileGroups[unit.getMovementDestination()]!!.layerOverlay.showHighlight(Color.WHITE, 0.7f)
        }

        // Z-Layer: 6
        // Highlight attackable tiles
        if (unit.isMilitary()) {

            val attackableTiles: List<AttackableTile> =
                if (nukeBlastRadius >= 0)
                    selectedTile!!.getTilesInDistance(nukeBlastRadius)
                        // Should not display invisible submarine units even if the tile is visible.
                        .filter { targetTile -> (targetTile.isVisible(unit.civ) && targetTile.getUnits().any { !it.isInvisible(unit.civ) })
                                || (targetTile.isCityCenter() && unit.civ.hasExplored(targetTile)) }
                        .map { AttackableTile(unit.getTile(), it, 1f, null) }
                        .toList()
                else TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .filter { it.tileToAttack.isVisible(unit.civ) }
                    .distinctBy { it.tileToAttack }

            for (attackableTile in attackableTiles) {
                val tileGroupToAttack = tileGroups[attackableTile.tileToAttack]!!
                tileGroupToAttack.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
                tileGroupToAttack.layerOverlay.showCrosshair(
                    // the targets which cannot be attacked without movements shown as orange-ish
                    if (attackableTile.tileToAttackFrom != unit.currentTile)
                        0.5f
                    else 1f
                )
                if (attackableTile.tileToAttack == selectedTile)
                    tileGroups[attackableTile.tileToAttackFrom]!!.layerOverlay.showHighlight(Color.SKY, 0.7f)
            }
        }

        // Z-Layer: 7
        // Highlight best tiles for city founding
        if (unit.hasUnique(UniqueType.FoundCity)
            && UncivGame.Current.settings.showSettlersSuggestedCityLocations) {
            CityLocationTileRanker.getBestTilesToFoundCity(unit, 5, minimumValue = 50f).tileRankMap.asSequence()
                .filter { it.key.isExplored(unit.civ) }.sortedByDescending { it.value }.take(3).forEach {
                    tileGroups[it.key]!!.layerOverlay.showGoodCityLocationIndicator()
                }
        }
    }

    private fun WorldMapHolder.updateTilesForSelectedSpy(spy: Spy) {
        for (group in tileGroups.values) {
            group.layerOverlay.reset()
            if (!group.tile.isCityCenter())
                group.layerImprovement.dimImprovement(true)
            group.layerCityButton.moveDown()
        }
        for (city in worldScreen.gameInfo.getCities()) {
            if (spy.canMoveTo(city)) {
                tileGroups[city.getCenterTile()]!!.layerOverlay.showHighlight(Color.CYAN, .7f)
            }
        }
    }

    private fun WorldMapHolder.updateBombardableTilesForSelectedCity(city: City) {
        if (!city.canBombard()) return
        for (attackableTile in TargetHelper.getBombardableTiles(city)) {
            val group = tileGroups[attackableTile]!!
            group.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
            group.layerOverlay.showCrosshair()
        }
    }
}

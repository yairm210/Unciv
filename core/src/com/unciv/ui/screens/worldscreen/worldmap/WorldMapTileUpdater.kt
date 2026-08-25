package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.logic.automation.unit.CityLocationTileRanker
import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.models.Spy
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.view.CivView
import com.unciv.view.MapUnitView

object WorldMapTileUpdater {

    private val WorldMapHolder.tileMapView get() = worldScreen.selectedGameView.tileMapView

     fun WorldMapHolder.updateTiles(civView: CivView) {
        val viewingCiv = civView.getCiv()

        if (isMapRevealEnabled(civView)) {
            // Only needs to be done once - this is so the minimap will also be revealed
            tileGroups.values.forEach {
                it.tile.setExplored(viewingCiv, true)
                it.isForceVisible = true } // So we can see all resources, regardless of tech
        }

        // General update of all tiles
        for (tileGroup in tileGroups.values)
            tileGroup.update(civView)

        // Update tiles according to selected unit/city
        val unitTable = worldScreen.bottomUnitTable
        when {
            unitTable.selectedSpy != null -> {
                updateTilesForSelectedSpy(unitTable.selectedSpy!!)
            }
            unitTable.selectedCity != null -> {
                val city = unitTable.selectedCity!!.getCity()
                updateBombardableTilesForSelectedCity(city)
                // We still want to show road paths to the selected city if they are present
                if (unitTable.selectedUnitIsConnectingRoad) {
                    updateTilesForSelectedUnit(unitTable.selectedUnits[0])
                }
            }
            unitTable.selectedUnit != null -> {
                for (unitView in unitTable.selectedUnits) {
                    updateTilesForSelectedUnit(unitView)
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

    private fun WorldMapHolder.updateTilesForSelectedUnit(unitView: MapUnitView) {
        val tileGroup = tileGroups[unitView.getTile()] ?: return

        // Update flags for units which have them
        if (!unitView.isAirUnit()) {
            tileGroup.layerUnitFlag.selectFlag(unitView.getUnit())
        }

        // Fade out less relevant images if a military unit is selected
        if (unitView.isMilitary()) {
            val unit = unitView.getUnit()
            for (group in tileGroups.values) {

                // Fade out population icons
                group.layerMisc.dimPopulation(true)

                val shownImprovementName = group.tile.getShownImprovement(unit.civ)
                val shownImprovement = unit.civ.gameInfo.ruleset.tileImprovements[shownImprovementName]

                // Fade out improvement icons (but not barb camps or ruins)
                if (shownImprovement != null &&
                    !shownImprovement.isBarbarianCampEquivalent(group.tile.stateThisTile) &&
                    !shownImprovement.isAncientRuinsEquivalent(unit.cache.state))
                    group.layerImprovement.dimImprovement(true)
            }
        }

        // Z-Layer: 0
        // Highlight suitable tiles in swapping-mode
        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            val swapUnitsTileOverlayColor = Color.PURPLE
            for (tileView in unitView.getUnitSwappableTiles())  {
                tileGroups[tileView]!!.layerOverlay.showHighlight(swapUnitsTileOverlayColor,
                    if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
            }
            // In swapping-mode we don't want to show other overlays
            return
        }

        // Z-Layer: 0
        // Highlight suitable tiles in road connecting mode
        if (worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad) {
            if (!unitView.rulesetHasRoadImprovement()) return
            val connectRoadTileOverlayColor = Color.RED
            for (tileView in unitView.getValidRoadConnectionTiles())  {
                tileGroups[tileView]!!.layerOverlay.showHighlight(connectRoadTileOverlayColor, 0.3f)
            }

            if (unitConnectRoadPaths.containsKey(unitView)) {
                for (tileView in unitConnectRoadPaths[unitView]!!) {
                    tileGroups[tileView]!!.layerOverlay.showHighlight(Color.ORANGE, 0.8f)
                }
            }

            // In road connecting mode we don't want to show other overlays
            return
        }

        val isAirUnit = unitView.isAirUnit()
        val moveTileOverlayColor = if (unitView.isPreparingParadrop()) Color.BLUE else Color.WHITE
        val tilesInMoveRange = unitView.getReachableTilesInCurrentTurn()
        // Prepare special Nuke blast radius display
        val nukeBlastRadius = if (unitView.isNuclearWeapon() && selectedTile != null && selectedTile != unitView.getTile())
            unitView.getNukeBlastRadius() else -1

        // Z-Layer: 1
        // Highlight tiles within movement range
        for (tileView in tilesInMoveRange) {
            val group = tileGroups[tileView]!!

            // Air-units have additional highlights
            if (isAirUnit && !unitView.isPreparingAirSweep()) {
                if (nukeBlastRadius >= 0 && tileView.aerialDistanceTo(selectedTile!!) <= nukeBlastRadius) {
                    // The tile is within the nuke blast radius
                    group.layerMisc.overlayTerrain(Color.FIREBRICK, 0.6f)
                } else if (tileView.aerialDistanceTo(unitView.getTile()) <= unitView.getRange()) {
                    // The tile is within attack range
                    group.layerMisc.overlayTerrain(Color.RED)
                } else if (unitView.isExplored(tileView) && tileView.aerialDistanceTo(unitView.getTile()) <= unitView.getRange()*2) {
                    // The tile is within move range
                    group.layerMisc.overlayTerrain(if (unitView.canMoveTo(tileView)) Color.WHITE else Color.BLUE)
                }
            }

            // Highlight tile unit can move to
            if (unitView.canMoveTo(tileView) ||
                unitView.isUnknownTileWeShouldAssumeToBePassable(tileView) && !isAirUnit
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
        if (unitView.cannotMove() && isAirUnit && !unitView.isPreparingAirSweep()) {
            for (tileView in unitView.getTilesInAttackRange()) {
                // The tile is within attack range
                tileGroups[tileView]!!.layerOverlay.showHighlight(Color.RED, 0.3f)
            }
        }

        // Z-Layer: 3
        // Movement paths
        if (unitMovementPaths.containsKey(unitView)) {
            for (tileView in unitMovementPaths[unitView]!!) {
                tileGroups[tileView]!!.layerOverlay.showHighlight(Color.SKY, 0.8f)
            }
        }

        // Z-Layer: 4
        // Highlight road path for workers currently connecting roads
        if (unitView.isAutomatingRoadConnection()) {
            val futureTiles = unitView.getFutureAutomatedRoadConnectionTiles() ?: return
            for (tileView in futureTiles) {
                tileGroups[tileView]!!.layerOverlay.showHighlight(Color.ORANGE, if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
            }
        }

        // Z-Layer: 5
        // Highlight movement destination tile
        if (unitView.isMoving()) {
            tileGroups[unitView.getMovementDestination()]!!.layerOverlay.showHighlight(Color.WHITE, 0.7f)
        }

        // Z-Layer: 6
        // Highlight attackable tiles
        if (unitView.isMilitary()) {
            val unit = unitView.getUnit()

            val attackableTiles: List<AttackableTile> =
                if (nukeBlastRadius >= 0)
                    selectedTile!!.getTile().getTilesInDistance(nukeBlastRadius)
                        // Should not display invisible submarine units even if the tile is visible.
                        .filter { targetTile -> (targetTile.isVisible(unit.civ) && targetTile.getUnits().any { !it.isInvisible(unit.civ) })
                                || (targetTile.isCityCenter() && unit.civ.hasExplored(targetTile)) }
                        .map { AttackableTile(unit.getTile(), it, 1f, null) }
                        .toList()
                else TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .filter { it.tileToAttack.isVisible(unit.civ) }
                    .distinctBy { it.tileToAttack }

            for (attackableTile in attackableTiles) {
                val tileGroupToAttack = tileGroups[tileMapView.getTile(attackableTile.tileToAttack)]!!
                tileGroupToAttack.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
                tileGroupToAttack.layerOverlay.showCrosshair(
                    // the targets which cannot be attacked without movements shown as orange-ish
                    if (attackableTile.tileToAttackFrom != unit.currentTile)
                        0.5f
                    else 1f
                )
                if (attackableTile.tileToAttack == selectedTile?.getTile())
                    tileGroups[tileMapView.getTile(attackableTile.tileToAttackFrom)]!!.layerOverlay.showHighlight(Color.SKY, 0.7f)
            }
        }

        // Z-Layer: 7
        // Highlight best tiles for city founding
        if (unitView.hasUnique(UniqueType.FoundCity)
            && UncivGame.Current.settings.showSettlersSuggestedCityLocations) {
            val unit = unitView.getUnit()
            CityLocationTileRanker.getBestTilesToFoundCity(unit, 5, minimumValue = 50f).tileRankMap.asSequence()
                .filter { it.key.isExplored(unit.civ) }.sortedByDescending { it.value }.take(3).forEach {
                    tileGroups[tileMapView.getTile(it.key)]!!.layerOverlay.showGoodCityLocationIndicator()
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
                tileGroups[tileMapView.getTile(city.getCenterTile())]!!.layerOverlay.showHighlight(Color.CYAN, .7f)
            }
        }
    }

    private fun WorldMapHolder.updateBombardableTilesForSelectedCity(city: City) {
        if (!city.canBombard()) return
        for (tileView in TargetHelper.getBombardableTiles(city).map { tileMapView.getTile(it) }) {
            val group = tileGroups[tileView]!!
            group.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
            group.layerOverlay.showCrosshair()
        }
    }
}

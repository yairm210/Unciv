package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.cityscreen.CityScreen
import kotlin.math.roundToInt

class ImprovementPickerScreen(
    private val tile: Tile,
    private val unit: MapUnit,
    private val onAccept: ()->Unit,
) : PickerScreen() {

    companion object {
        /** Return true if we can report improvements associated with the [problems] (or there are no problems for it at all). */
        fun canReport(problems: Collection<ImprovementBuildingProblem>) = problems.all { it.reportable }
    }

    private var selectedImprovement: TileImprovement? = null
    private val gameInfo = tile.tileMap.gameInfo
    private val ruleSet = gameInfo.ruleset
    private val currentPlayerCiv = gameInfo.getCurrentPlayerCivilization()
    // Support for UniqueType.CreatesOneImprovement
    private val tileMarkedForCreatesOneImprovement = tile.isMarkedForCreatesOneImprovement()

    private fun getRequiredTechColumn(improvement: TileImprovement) =
        ruleSet.technologies[improvement.techRequired]?.column?.columnNumber ?: -1

    fun accept(improvement: TileImprovement?) {
        if (improvement == null || tileMarkedForCreatesOneImprovement) return
        if (improvement.name == Constants.cancelImprovementOrder) {
            tile.stopWorkingOnImprovement()
            // no onAccept() - Worker can stay selected
        } else {
            if (improvement.name != tile.improvementInProgress)
                tile.startWorkingOnImprovement(improvement, currentPlayerCiv, unit)
            unit.action = null // this is to "wake up" the worker if it's sleeping
            onAccept()
        }
        game.popScreen()
    }

    init {
        setDefaultCloseAction()

        rightSideButton.setText("Pick improvement".tr())
        rightSideButton.onClick {
            accept(selectedImprovement)
        }

        val regularImprovements = Table()
        regularImprovements.defaults().pad(5f)

        // clone tileInfo without "top" feature if it could be removed
        // Keep this copy around for speed
        val tileWithoutLastTerrain: Tile = tile.clone()
        tileWithoutLastTerrain.setTerrainTransients()
        if (Constants.remove + tileWithoutLastTerrain.lastTerrain.name in ruleSet.tileImprovements) {
            tileWithoutLastTerrain.removeTerrainFeature(tileWithoutLastTerrain.lastTerrain.name)
        }

        val cityUniqueCache = LocalUniqueCache()

        for (improvement in ruleSet.tileImprovements.values) {
            var suggestRemoval = false
            // canBuildImprovement() would allow e.g. great improvements thus we need to exclude them - except cancel
            if (improvement.turnsToBuild == -1 && improvement.name != Constants.cancelImprovementOrder) continue
            if (improvement.name == tile.improvement) continue // also checked by canImprovementBeBuiltHere, but after more expensive tests
            if (!unit.canBuildImprovement(improvement)) continue

            var unbuildableBecause = tile.improvementFunctions.getImprovementBuildingProblems(improvement, currentPlayerCiv).toSet()
            if (!canReport(unbuildableBecause)) {
                // Try after pretending to have removed the top terrain layer.
                unbuildableBecause = tileWithoutLastTerrain.improvementFunctions.getImprovementBuildingProblems(improvement, currentPlayerCiv).toSet()
                if (!canReport(unbuildableBecause)) continue
                else suggestRemoval = true
            }

            val image = ImageGetter.getImprovementPortrait(improvement.name, 30f)

            // allow multiple key mappings to technologically supersede each other
            var shortcutKey = improvement.shortcutKey
            if (shortcutKey != null) {
                val techLevel = getRequiredTechColumn(improvement)
                val isSuperseded = ruleSet.tileImprovements.values.asSequence()
                    // *other* improvements with same shortcutKey
                    .filter { it.shortcutKey == improvement.shortcutKey && it != improvement }
                    // civ can build it (checks tech researched)
                    .filter { tile.improvementFunctions.canBuildImprovement(it, currentPlayerCiv) }
                    // is technologically more advanced
                    .filter { getRequiredTechColumn(it) > techLevel }
                    .any()
                // another supersedes this - ignore key binding
                if (isSuperseded) shortcutKey = null
            }

            var labelText = improvement.name.tr(true)
            val turnsToBuild = if (tile.improvementInProgress == improvement.name) tile.turnsToImprovement
            else improvement.getTurnsToBuild(currentPlayerCiv, unit)

            if (turnsToBuild > 0) labelText += " - $turnsToBuild${Fonts.turn}"
            val provideResource = tile.hasViewableResource(currentPlayerCiv) && tile.tileResource.isImprovedBy(improvement.name)
            if (provideResource) labelText += "\n" + "Provides [${tile.resource}]".tr()
            val removeImprovement = (!improvement.isRoad()
                    && !improvement.name.startsWith(Constants.remove)
                    && improvement.name != Constants.cancelImprovementOrder)
            if (tile.improvement != null && removeImprovement) labelText += "\n" + "Replaces [${tile.improvement}]".tr()

            val proposedSolutions = mutableListOf<String>()

            if (suggestRemoval)
                proposedSolutions.add("${Constants.remove}[${tile.lastTerrain.name}] first")
            if (ImprovementBuildingProblem.MissingTech in unbuildableBecause)
                proposedSolutions.add("Research [${improvement.techRequired}] first")
            if (ImprovementBuildingProblem.NotJustOutsideBorders in unbuildableBecause)
                proposedSolutions.add("Have this tile close to your borders")
            if (ImprovementBuildingProblem.OutsideBorders in unbuildableBecause)
                proposedSolutions.add("Have this tile inside your empire")
            if (ImprovementBuildingProblem.MissingResources in unbuildableBecause) {
                proposedSolutions.addAll(improvement.getMatchingUniques(UniqueType.ConsumesResources).filter {
                    currentPlayerCiv.getResourceAmount(it.params[1]) < it.params[0].toInt()
                }.map { "Acquire more [$it]" })
            }

            val explanationText = when {
                proposedSolutions.any() -> proposedSolutions.joinToString("}\n{", "{", "}").toLabel()
                tile.improvementInProgress == improvement.name -> "Current construction".toLabel()
                tileMarkedForCreatesOneImprovement -> null
                else -> "Pick now!".toLabel().onClick { accept(improvement) }
            }

            val statIcons = getStatIconsTable(provideResource, removeImprovement)

            // get benefits of the new improvement
            val stats = tile.stats.getStatDiffForImprovement(
                improvement,
                currentPlayerCiv,
                tile.getCity(),
                cityUniqueCache
            )

            //Warn when the current improvement will increase a stat for the tile,
            // but the tile is outside of the range (> 3 tiles from any city center) that can be
            // worked by a city's population
            if (tile.owningCity != null
                && !improvement.isRoad()
                    && stats.values.any { it > 0f }
                    && !improvement.name.startsWith(Constants.remove)
                    && !tile.getTilesInDistance(currentPlayerCiv.modConstants.cityWorkRange)
                        .any { it.isCityCenter() && it.getCity()!!.civ == currentPlayerCiv }
            )
                labelText += "\n" + "Not in city work range".tr()

            val statsTable = getStatsTable(stats)
            statIcons.add(statsTable).padLeft(13f)

            regularImprovements.add(statIcons).align(Align.right)

            val improvementButton = PickerPane.getPickerOptionButton(image, labelText)
            // This is onClick without ActivationTypes.Keystroke equivalence - keys should select *and* close:
            improvementButton.onActivation(type = ActivationTypes.Tap, noEquivalence = true) {
                selectedImprovement = improvement
                pick(improvement.name.tr())
                descriptionLabel.setText(improvement.getDescription(ruleSet))
            }

            improvementButton.onDoubleClick { accept(improvement) }

            if (improvement.name == tile.improvementInProgress) improvementButton.color = Color.GREEN
            if (proposedSolutions.isNotEmpty() || tileMarkedForCreatesOneImprovement) {
                improvementButton.disable()
            } else if (shortcutKey != null) {
                // Shortcut keys trigger what onDoubleClick does, not equivalent to single Click:
                improvementButton.keyShortcuts.add(shortcutKey) { accept(improvement) }
                improvementButton.addTooltip(shortcutKey)
            }

            regularImprovements.add(improvementButton)
            regularImprovements.add(explanationText).padLeft(10f).fillY()
            regularImprovements.row()
        }

        val ownerTable = Table()
        if (tile.getOwner() == null) {
            ownerTable.add("Unowned tile".toLabel())
        } else if (tile.getOwner()!!.isCurrentPlayer()) {
            val button = tile.getCity()!!.name.toTextButton(hideIcons = true)
            button.onClick {
                this.game.pushScreen(CityScreen(tile.getCity()!!, null, tile))
            }
            ownerTable.add("Tile owned by [${tile.getOwner()!!.civName}] (You)".toLabel()).padLeft(10f)
            ownerTable.add(button).padLeft(20f)
        } else {
            ownerTable.add("Tile owned by [${tile.getOwner()!!.civName}] - [${tile.getCity()!!.name}]".toLabel()).padLeft(10f)
        }

        topTable.add(ownerTable)
        topTable.row()
        topTable.add(regularImprovements)
    }

    private fun getStatIconsTable(provideResource: Boolean, removeImprovement: Boolean): Table {
        val statIcons = Table()

        // icon for adding the resource by improvement
        if (provideResource)
            statIcons.add(ImageGetter.getResourcePortrait(tile.resource.toString(), 30f)).pad(3f)

        // icon for removing the resource by replacing improvement
        if (removeImprovement && tile.hasViewableResource(currentPlayerCiv) && tile.improvement != null && tile.tileResource.isImprovedBy(tile.improvement!!)) {
            val resourceIcon = ImageGetter.getResourcePortrait(tile.resource!!, 30f)
            statIcons.add(ImageGetter.getCrossedImage(resourceIcon, 30f))
        }
        return statIcons
    }

    // icons of benefits (food, gold, etc) by improvement
    private fun getStatsTable(stats: Stats): Table {
        val statsTable = Table()
        for ((key, value) in stats) {
            val statValue = value.roundToInt()
            if (statValue == 0) continue

            statsTable.add(ImageGetter.getStatIcon(key.name)).size(20f).padRight(3f)

            val valueLabel = statValue.toLabel()
            valueLabel.color = if (statValue < 0) Color.RED else Color.WHITE

            statsTable.add(valueLabel).padRight(13f)
        }
        return statsTable
    }
}

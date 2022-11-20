package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.roundToInt

class ImprovementPickerScreen(
    private val tileInfo: TileInfo,
    private val unit: MapUnit,
    private val onAccept: ()->Unit,
) : PickerScreen() {

    companion object {
        /** Set of resolvable improvement building problems that this class knows how to report. */
        private val reportableProblems = setOf(
            TileInfo.ImprovementBuildingProblem.MissingTech,
            TileInfo.ImprovementBuildingProblem.NotJustOutsideBorders,
            TileInfo.ImprovementBuildingProblem.OutsideBorders,
            TileInfo.ImprovementBuildingProblem.MissingResources)

        /** Return true if we can report improvements associated with the [problems] (or there are no problems for it at all). */
        fun canReport(problems: Collection<TileInfo.ImprovementBuildingProblem>) = problems.all { it in reportableProblems }
    }

    private var selectedImprovement: TileImprovement? = null
    private val gameInfo = tileInfo.tileMap.gameInfo
    private val ruleSet = gameInfo.ruleSet
    private val currentPlayerCiv = gameInfo.getCurrentPlayerCivilization()
    // Support for UniqueType.CreatesOneImprovement
    private val tileMarkedForCreatesOneImprovement = tileInfo.isMarkedForCreatesOneImprovement()

    private fun getRequiredTechColumn(improvement: TileImprovement) =
        ruleSet.technologies[improvement.techRequired]?.column?.columnNumber ?: -1

    fun accept(improvement: TileImprovement?) {
        if (improvement == null || tileMarkedForCreatesOneImprovement) return
        if (improvement.name == Constants.cancelImprovementOrder) {
            tileInfo.stopWorkingOnImprovement()
            // no onAccept() - Worker can stay selected
        } else {
            if (improvement.name != tileInfo.improvementInProgress)
                tileInfo.startWorkingOnImprovement(improvement, currentPlayerCiv, unit)
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
        val tileInfoWithoutLastTerrain: TileInfo = tileInfo.clone()
        if (Constants.remove + tileInfoWithoutLastTerrain.getLastTerrain().name in ruleSet.tileImprovements) {
            tileInfoWithoutLastTerrain.removeTerrainFeature(tileInfoWithoutLastTerrain.getLastTerrain().name)
        }

        val cityUniqueCache = LocalUniqueCache()

        for (improvement in ruleSet.tileImprovements.values) {
            var suggestRemoval = false
            // canBuildImprovement() would allow e.g. great improvements thus we need to exclude them - except cancel
            if (improvement.turnsToBuild == 0 && improvement.name != Constants.cancelImprovementOrder) continue
            if (improvement.name == tileInfo.improvement) continue // also checked by canImprovementBeBuiltHere, but after more expensive tests
            if (!unit.canBuildImprovement(improvement)) continue

            var unbuildableBecause = tileInfo.getImprovementBuildingProblems(improvement, currentPlayerCiv).toSet()
            if (!canReport(unbuildableBecause)) {
                // Try after pretending to have removed the top terrain layer.
                unbuildableBecause = tileInfoWithoutLastTerrain.getImprovementBuildingProblems(improvement, currentPlayerCiv).toSet()
                if (!canReport(unbuildableBecause)) continue
                else suggestRemoval = true
            }

            val image = ImageGetter.getImprovementIcon(improvement.name, 30f)

            // allow multiple key mappings to technologically supersede each other
            var shortcutKey = improvement.shortcutKey
            if (shortcutKey != null) {
                val techLevel = getRequiredTechColumn(improvement)
                val isSuperseded = ruleSet.tileImprovements.values.asSequence()
                    // *other* improvements with same shortcutKey
                    .filter { it.shortcutKey == improvement.shortcutKey && it != improvement }
                    // civ can build it (checks tech researched)
                    .filter { tileInfo.canBuildImprovement(it, currentPlayerCiv) }
                    // is technologically more advanced
                    .filter { getRequiredTechColumn(it) > techLevel }
                    .any()
                // another supersedes this - ignore key binding
                if (isSuperseded) shortcutKey = null
            }

            var labelText = improvement.name.tr()
            val turnsToBuild = if (tileInfo.improvementInProgress == improvement.name) tileInfo.turnsToImprovement
            else improvement.getTurnsToBuild(currentPlayerCiv, unit)

            if (turnsToBuild > 0) labelText += " - $turnsToBuild${Fonts.turn}"
            val provideResource = tileInfo.hasViewableResource(currentPlayerCiv) && tileInfo.tileResource.isImprovedBy(improvement.name)
            if (provideResource) labelText += "\n" + "Provides [${tileInfo.resource}]".tr()
            val removeImprovement = (!improvement.isRoad()
                    && !improvement.name.startsWith(Constants.remove)
                    && improvement.name != Constants.cancelImprovementOrder)
            if (tileInfo.improvement != null && removeImprovement) labelText += "\n" + "Replaces [${tileInfo.improvement}]".tr()

            val proposedSolutions = mutableListOf<String>()

            if (suggestRemoval)
                proposedSolutions.add("${Constants.remove}[${tileInfo.getLastTerrain().name}] first")
            if (TileInfo.ImprovementBuildingProblem.MissingTech in unbuildableBecause)
                proposedSolutions.add("Research [${improvement.techRequired}] first")
            if (TileInfo.ImprovementBuildingProblem.NotJustOutsideBorders in unbuildableBecause)
                proposedSolutions.add("Have this tile close to your borders")
            if (TileInfo.ImprovementBuildingProblem.OutsideBorders in unbuildableBecause)
                proposedSolutions.add("Have this tile inside your empire")
            if (TileInfo.ImprovementBuildingProblem.MissingResources in unbuildableBecause) {
                proposedSolutions.addAll(improvement.getMatchingUniques(UniqueType.ConsumesResources).filter {
                    currentPlayerCiv.getCivResourcesByName()[it.params[1]]!! < it.params[0].toInt()
                }.map { "Acquire more [$it]" })
            }

            val explanationText = when {
                proposedSolutions.any() -> proposedSolutions.joinToString("}\n{", "{", "}").toLabel()
                tileInfo.improvementInProgress == improvement.name -> "Current construction".toLabel()
                tileMarkedForCreatesOneImprovement -> null
                else -> "Pick now!".toLabel().onClick { accept(improvement) }
            }

            val statIcons = getStatIconsTable(provideResource, removeImprovement)

            // get benefits of the new improvement
            val stats = tileInfo.getImprovementStats(
                improvement,
                currentPlayerCiv,
                tileInfo.getCity(),
                cityUniqueCache
            )
            // subtract the benefits of the replaced improvement, if any
            val existingImprovement = tileInfo.getTileImprovement()
            if (existingImprovement != null && removeImprovement) {
                val existingStats = tileInfo.getImprovementStats(
                    existingImprovement,
                    currentPlayerCiv,
                    tileInfo.getCity(),
                    cityUniqueCache
                )
                stats.add(existingStats.times(-1.0f))
            }

            val statsTable = getStatsTable(stats)
            statIcons.add(statsTable).padLeft(13f)

            regularImprovements.add(statIcons).align(Align.right)

            val improvementButton = PickerPane.getPickerOptionButton(image, labelText)
            improvementButton.onClick {
                selectedImprovement = improvement
                pick(improvement.name.tr())
                descriptionLabel.setText(improvement.getDescription(ruleSet))
            }

            if (improvement.name == tileInfo.improvementInProgress) improvementButton.color = Color.GREEN
            if (proposedSolutions.isNotEmpty() || tileMarkedForCreatesOneImprovement) {
                improvementButton.disable()
            } else if (shortcutKey != null) {
                // FIXME: Different compared to onClick() and therefore no onActivation() here, to preserve pre-existing behavior. Reconsider this?
                improvementButton.keyShortcuts.add(shortcutKey) { accept(improvement) }
                improvementButton.addTooltip(shortcutKey)
            }

            regularImprovements.add(improvementButton)
            regularImprovements.add(explanationText).padLeft(10f).fillY()
            regularImprovements.row()
        }

        topTable.add(regularImprovements)
    }

    private fun getStatIconsTable(provideResource: Boolean, removeImprovement: Boolean): Table {
        val statIcons = Table()

        // icon for adding the resource by improvement
        if (provideResource)
            statIcons.add(ImageGetter.getResourceImage(tileInfo.resource.toString(), 30f)).pad(3f)

        // icon for removing the resource by replacing improvement
        if (removeImprovement && tileInfo.hasViewableResource(currentPlayerCiv) && tileInfo.improvement != null && tileInfo.tileResource.isImprovedBy(tileInfo.improvement!!)) {
            val resourceIcon = ImageGetter.getResourceImage(tileInfo.resource!!, 30f)
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

package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.popups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * Table consisting of the menu button, current civ, some stats and the overview button for the top of [WorldScreen]
 */
//region Fields
class WorldScreenTopBar(val worldScreen: WorldScreen) : Table() {
    //TODO shouldn't most onClick be addActivationAction instead?
    private val turnsLabel = "Turns: 0/400".toLabel()
    private val goldLabel = "0".toLabel(colorFromRGB(225, 217, 71))
    private val scienceLabel = "0".toLabel(colorFromRGB(78, 140, 151))
    private val happinessLabel = "0".toLabel()
    private val cultureLabel = "0".toLabel(colorFromRGB(210, 94, 210))
    private val faithLabel = "0".toLabel(colorFromRGB(168, 196, 241))
    private data class ResourceActors(val resource: TileResource, val Label: Label, val icon: Group)
    private val resourceActors = ArrayList<ResourceActors>(12)
    private val happinessImage = Group()

    // These are all to improve performance IE reduce update time (was 150 ms on my phone, which is a lot!)
    private val malcontentColor = colorFromRGB(239,83,80) // Color.valueOf("ef5350")
    private val happinessColor = colorFromRGB(92, 194, 77) // Color.valueOf("8cc24d")
    private val malcontentGroup = ImageGetter.getStatIcon("Malcontent")
    private val happinessGroup = ImageGetter.getStatIcon("Happiness")

    private val statsTable = getStatsTable()
    private val resourcesWrapper = Table()
    private val resourceTable = getResourceTable()
    private val selectedCivTable = SelectedCivilizationTable(worldScreen)
    private val overviewButton = OverviewAndSupplyTable(worldScreen)
    private val leftFillerCell: Cell<BackgroundActor>
    private val rightFillerCell: Cell<BackgroundActor>

    //endregion

    init {
        // Not the Table, the Cells (all except one) have the background. To avoid gaps, _no_
        // padding except inside the cell actors, and all actors need to _fill_ their cell.
        val backColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        statsTable.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/StatsTable", tintColor = backColor)
        resourceTable.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/ResourceTable", tintColor = backColor)
        add(statsTable).colspan(3).growX().row()
        add(resourceTable).colspan(3).growX().row()
        val leftFillerBG = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/LeftAttachment", BaseScreen.skinStrings.roundedEdgeRectangleShape, backColor)
        leftFillerCell = add(BackgroundActor(leftFillerBG, Align.topLeft))
        add().growX()
        val rightFillerBG = BaseScreen.skinStrings.getUiBackground("WorldScreen/TopBar/RightAttachment", BaseScreen.skinStrings.roundedEdgeRectangleShape, backColor)
        rightFillerCell = add(BackgroundActor(rightFillerBG, Align.topRight))
        pack()
    }

    private fun getStatsTable(): Table {
        val statsTable = Table()
        statsTable.defaults().pad(8f, 3f, 3f, 3f)

        fun addStat(label: Label, icon: String, isLast: Boolean = false, screenFactory: ()-> BaseScreen) {
            val image = ImageGetter.getStatIcon(icon)
            val action = {
                worldScreen.game.pushScreen(screenFactory())
            }
            label.onClick(action)
            image.onClick(action)
            statsTable.add(label)
            statsTable.add(image).padBottom(6f).size(20f).apply {
                if (!isLast) padRight(20f)
            }
        }
        fun addStat(label: Label, icon: String, overviewPage: EmpireOverviewCategories, isLast: Boolean = false) =
            addStat(label, icon, isLast) { EmpireOverviewScreen(worldScreen.selectedCiv, overviewPage) }

        addStat(goldLabel, "Gold", EmpireOverviewCategories.Stats)
        addStat(scienceLabel, "Science") { TechPickerScreen(worldScreen.selectedCiv) }

        statsTable.add(happinessImage).padBottom(6f).size(20f)
        statsTable.add(happinessLabel).padRight(20f)
        val invokeResourcesPage = {
            worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
        }
        happinessImage.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        addStat(cultureLabel, "Culture") { PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState) }
        if (worldScreen.gameInfo.isReligionEnabled()) {
            addStat(faithLabel, "Faith", EmpireOverviewCategories.Religion, isLast = true)
        } else {
            statsTable.add("Religion: Off".toLabel())
        }

        statsTable.pack()
        return statsTable
    }

    private fun getResourceTable(): Table {
        // Since cells with invisible actors still occupy the full actor dimensions, we only prepare
        // the future contents for resourcesWrapper here, they're added to the Table in updateResourcesTable
        val resourceTable = Table()
        resourcesWrapper.defaults().pad(5f, 5f, 10f, 5f)
        resourcesWrapper.touchable = Touchable.enabled

        turnsLabel.onClick {
            if (worldScreen.selectedCiv.isLongCountDisplay()) {
                val gameInfo = worldScreen.selectedCiv.gameInfo
                MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, gameInfo.getYear())
            } else {
                worldScreen.game.pushScreen(VictoryScreen(worldScreen))
            }
        }
        resourcesWrapper.onClick {
            worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
        }

        val strategicResources = worldScreen.gameInfo.ruleset.tileResources.values
            .filter { it.resourceType == ResourceType.Strategic }
        for (resource in strategicResources) {
            val resourceImage = ImageGetter.getResourcePortrait(resource.name, 20f)
            val resourceLabel = "0".toLabel()
            resourceActors += ResourceActors(resource, resourceLabel, resourceImage)
        }

        // in case the icons are configured higher than a label, we add a dummy - height will be measured once before it's updated
        if (resourceActors.isNotEmpty()) {
            resourcesWrapper.add(resourceActors[0].icon)
            resourceTable.add(resourcesWrapper)
        }

        resourceTable.add(turnsLabel).pad(5f, 5f, 10f, 5f)

        return resourceTable
    }

    private class OverviewAndSupplyTable(worldScreen: WorldScreen) : Table(BaseScreen.skin) {
        val unitSupplyImage = ImageGetter.getImage("OtherIcons/ExclamationMark")
            .apply { color = Color.FIREBRICK }
        val unitSupplyCell: Cell<Actor?>

        init {
            unitSupplyImage.onClick {
                worldScreen.openEmpireOverview(EmpireOverviewCategories.Units)
            }

            val overviewButton = "Overview".toTextButton()
            overviewButton.addTooltip('e')
            overviewButton.onClick { worldScreen.openEmpireOverview() }

            unitSupplyCell = add()
            add(overviewButton).pad(10f)
            pack()
        }

        fun update(worldScreen: WorldScreen) {
            val newVisible = worldScreen.selectedCiv.stats.getUnitSupplyDeficit() > 0
            if (newVisible == unitSupplyCell.hasActor()) return
            if (newVisible) unitSupplyCell.setActor(unitSupplyImage)
                .size(50f).padLeft(10f)
            else unitSupplyCell.setActor(null).size(0f).pad(0f)
            invalidate()
            pack()
        }
    }

    private class SelectedCivilizationTable(worldScreen: WorldScreen) : Table(BaseScreen.skin) {
        private var selectedCiv = ""
        private val selectedCivLabel = "".toLabel()
        private val selectedCivIconHolder = Container<Actor>()
        private val menuButton = ImageGetter.getImage("OtherIcons/MenuIcon")

        init {
            left()
            defaults().pad(10f)

            menuButton.color = Color.WHITE
            menuButton.onClick {
                val worldScreenMenuPopup = worldScreen.popups.firstOrNull { it is WorldScreenMenuPopup }
                if (worldScreenMenuPopup != null) worldScreenMenuPopup.close()
                else WorldScreenMenuPopup(worldScreen).open(force = true)
            }

            selectedCivLabel.setFontSize(25)
            selectedCivLabel.onClick {
                val civilopediaScreen = CivilopediaScreen(
                    worldScreen.selectedCiv.gameInfo.ruleset,
                    CivilopediaCategories.Nation,
                    worldScreen.selectedCiv.civName
                )
                worldScreen.game.pushScreen(civilopediaScreen)
            }

            selectedCivIconHolder.onClick {
                worldScreen.openEmpireOverview()
            }

            add(menuButton).size(50f).padRight(0f)
            add(selectedCivLabel).padRight(0f)
            add(selectedCivIconHolder).size(35f)
            pack()
        }

        fun update(worldScreen: WorldScreen) {
            val newCiv = worldScreen.selectedCiv.civName
            if (this.selectedCiv == newCiv) return
            this.selectedCiv = newCiv

            selectedCivLabel.setText(newCiv.tr())
            val nation = worldScreen.gameInfo.ruleset.nations[worldScreen.selectedCiv.civName]!!
            val selectedCivIcon = ImageGetter.getNationPortrait(nation, 35f)
            selectedCivIconHolder.actor = selectedCivIcon
            invalidate()
            pack()
        }
    }

    private fun layoutButtons() {
        removeActor(selectedCivTable)
        removeActor(overviewButton)
        validate()

        val statsWidth = statsTable.minWidth
        val resourceWidth = resourceTable.minWidth
        val overviewWidth = overviewButton.minWidth
        val selectedCivWidth = selectedCivTable.minWidth
        val leftRightNeeded = max(selectedCivWidth, overviewWidth)
        val statsRowHeight = getRowHeight(0)
        val baseHeight = statsRowHeight + getRowHeight(1)

        // Check whether it gets cramped on narrow aspect ratios
        val fillerHeight: Float // Height of the background filler cells
        val buttonY: Float      // Vertical center of Civ+Overview buttons relative to this.y
        when {
            leftRightNeeded * 2f > stage.width - resourceWidth -> {
                // Need to shift buttons down to below both stats and resources
                fillerHeight = baseHeight +1
                buttonY = overviewButton.minHeight / 2f
            }
            leftRightNeeded * 2f > stage.width - statsWidth -> {
                // Shifting buttons down to below stats row is enough
                fillerHeight = statsRowHeight +1
                buttonY = overviewButton.minHeight / 2f
            }
            else -> {
                // Enough space to keep buttons to the left and right of stats and resources
                fillerHeight = 0f
                buttonY = baseHeight / 2f
            }
        }

        val leftFillerWidth = if (fillerHeight > 0f) selectedCivWidth else 0f
        val rightFillerWidth = if (fillerHeight > 0f) overviewWidth else 0f
        if (leftFillerCell.minHeight != fillerHeight
                || leftFillerCell.minWidth != leftFillerWidth
                || rightFillerCell.minWidth != rightFillerWidth) {
            // Gdx fail: containing Table isn't invalidated when setting Cell size
            leftFillerCell.width(leftFillerWidth).height(fillerHeight)
            rightFillerCell.width(rightFillerWidth).height(fillerHeight)
            invalidate()  // Without this all attempts to get a recalculated height are doomed
            pack()  // neither validate nor layout will include the new row height in height
        }

        width = stage.width
        setPosition(0f, stage.height, Align.topLeft)

        selectedCivTable.setPosition(1f, buttonY, Align.left)
        overviewButton.setPosition(stage.width, buttonY, Align.right)
        addActor(selectedCivTable) // needs to be after pack
        addActor(overviewButton)
    }

    internal fun update(civInfo: Civilization) {
        updateStatsTable(civInfo)
        updateResourcesTable(civInfo)
        selectedCivTable.update(worldScreen)
        overviewButton.update(worldScreen)
        layoutButtons()
    }

    private fun rateLabel(value: Float): String {
        return (if (value > 0) "+" else "") + value.roundToInt()
    }

    private fun updateStatsTable(civInfo: Civilization) {
        val nextTurnStats = civInfo.stats.statsForNextTurn
        val goldPerTurn = " (" + rateLabel(nextTurnStats.gold) + ")"
        goldLabel.setText(civInfo.gold.toString() + goldPerTurn)

        scienceLabel.setText(rateLabel(nextTurnStats.science))

        happinessLabel.setText(getHappinessText(civInfo))

        if (civInfo.getHappiness() < 0) {
            happinessLabel.setFontColor(malcontentColor)
            happinessImage.clearChildren()
            happinessImage.addActor(malcontentGroup)
        } else {
            happinessLabel.setFontColor(happinessColor)
            happinessImage.clearChildren()
            happinessImage.addActor(happinessGroup)
        }

        cultureLabel.setText(getCultureText(civInfo, nextTurnStats))
        faithLabel.setText(civInfo.religionManager.storedFaith.toString() +
                " (" + rateLabel(nextTurnStats.faith) + ")")
    }

    private fun updateResourcesTable(civInfo: Civilization) {
        val yearText = YearTextUtil.toYearText(
            civInfo.gameInfo.getYear(), civInfo.isLongCountDisplay()
        )
        turnsLabel.setText(Fonts.turn + "" + civInfo.gameInfo.turns + " | " + yearText)
        resourcesWrapper.clearChildren()
        var firstPadLeft = 20f  // We want a distance from the turns entry to the first resource, but only if any resource is displayed
        val civResources = civInfo.getCivResourcesByName()
        val civResourceSupply = civInfo.getCivResourceSupply()
        for ((resource, label, icon) in resourceActors) {
            if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!))
                continue
            if (resource.hasUnique(UniqueType.NotShownOnWorldScreen)) continue

            resourcesWrapper.add(icon).padLeft(firstPadLeft).padRight(0f)
            firstPadLeft = 5f
            val amount = civResources[resource.name] ?: 0
            if (!resource.isStockpiled())
                label.setText(amount)
            else {
                val perTurn = civResourceSupply.firstOrNull { it.resource == resource }?.amount ?: 0
                if (perTurn == 0) label.setText(amount)
                else label.setText("$amount (${perTurn.toStringSigned()})")
            }
            resourcesWrapper.add(label).padTop(8f)  // digits don't have descenders, so push them down a little
        }

        resourceTable.pack()
    }

    private fun getCultureText(civInfo: Civilization, nextTurnStats: Stats): String {
        var cultureString = rateLabel(nextTurnStats.culture)
        //if (nextTurnStats.culture == 0f) return cultureString // when you start the game, you're not producing any culture

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        cultureString +=  if  (turnsToNextPolicy <= 0f) " (!)"
            else if (nextTurnStats.culture <= 0) " (∞)"
            else " (" + ceil(turnsToNextPolicy).toInt() + ")"
        return cultureString
    }

    private fun getHappinessText(civInfo: Civilization): String {
        var happinessText = civInfo.getHappiness().toString()
        val goldenAges = civInfo.goldenAges
        happinessText +=
            if (goldenAges.isGoldenAge())
                "    {GOLDEN AGE}(${goldenAges.turnsLeftForCurrentGoldenAge})".tr()
            else
                " (${goldenAges.storedHappiness}/${goldenAges.happinessRequiredForNextGoldenAge()})"
        return happinessText
    }

}

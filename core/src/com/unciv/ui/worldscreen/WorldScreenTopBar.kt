package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.popup.popups
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.colorFromRGB
import com.unciv.ui.utils.darken
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.pad
import com.unciv.ui.utils.setFontColor
import com.unciv.ui.utils.setFontSize
import com.unciv.ui.utils.toLabel
import com.unciv.ui.utils.toTextButton
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * Table consisting of the menu button, current civ, some stats and the overview button for the top of [WorldScreen]
 */
//region Fields
class WorldScreenTopBar(val worldScreen: WorldScreen) : Table() {

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
        val backColor = ImageGetter.getBlue().darken(0.5f)
        val backgroundDrawable = ImageGetter.getBackground(backColor)
        statsTable.background = backgroundDrawable
        resourceTable.background = backgroundDrawable
        add(statsTable).colspan(3).growX().row()
        add(resourceTable).colspan(3).growX().row()
        val leftFillerBG = BackgroundActor.getRoundedEdgeRectangle(backColor)
        leftFillerCell = add(BackgroundActor(leftFillerBG, Align.topLeft))
        add().growX()
        val rightFillerBG = BackgroundActor.getRoundedEdgeRectangle(backColor)
        rightFillerCell = add(BackgroundActor(rightFillerBG, Align.topRight))
        pack()
    }

    private fun getResourceTable(): Table {
        // Since cells with invisible actors still occupy the full actor dimensions, we only prepare
        // the future contents for resourcesWrapper here, they're added to the Table in updateResourcesTable
        val resourceTable = Table()
        resourcesWrapper.defaults().pad(0f, 3f)

        turnsLabel.onClick {
            if (worldScreen.selectedCiv.isLongCountDisplay()) {
                val gameInfo = worldScreen.selectedCiv.gameInfo
                MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, gameInfo.getYear())
            } else {
                worldScreen.game.setScreen(VictoryScreen(worldScreen))
            }
        }
        resourcesWrapper.onClick {
            worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Resources"))
        }

        val strategicResources = worldScreen.gameInfo.ruleSet.tileResources.values
                .filter { it.resourceType == ResourceType.Strategic }
        for (resource in strategicResources) {
            val resourceImage = ImageGetter.getResourceImage(resource.name, 20f)
            val resourceLabel = "0".toLabel()
            resourceActors += ResourceActors(resource, resourceLabel, resourceImage)
        }

        // in case the icons are configured higher than a label, we add a dummy - height will be measured once before it's updated
        resourcesWrapper.add(resourceActors[0].icon)
        resourceTable.add(turnsLabel).pad(5f, 3f, 8f, 20f)
        resourceTable.add(resourcesWrapper).padRight(3f)
        return resourceTable
    }

    private fun getStatsTable(): Table {
        val statsTable = Table()
        statsTable.defaults().pad(8f, 3f)//.align(Align.top)

        statsTable.add(goldLabel)
        val goldImage = ImageGetter.getStatIcon("Gold")
        statsTable.add(goldImage).padRight(20f).padBottom(6f).size(20f)
        val invokeStatsPage = {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Stats"))
        }
        goldLabel.onClick(invokeStatsPage)
        goldImage.onClick(invokeStatsPage)

        statsTable.add(scienceLabel) //.apply { setAlignment(Align.center) }).align(Align.top)
        val scienceImage = ImageGetter.getStatIcon("Science")
        statsTable.add(scienceImage).padRight(20f).padBottom(6f).size(20f)
        val invokeTechScreen = {
                worldScreen.game.setScreen(TechPickerScreen(worldScreen.selectedCiv))
        }
        scienceLabel.onClick(invokeTechScreen)
        scienceImage.onClick(invokeTechScreen)

        statsTable.add(happinessImage).padBottom(6f).size(20f)
        statsTable.add(happinessLabel).padRight(20f)
        val invokeResourcesPage = {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Resources"))
        }
        happinessImage.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        statsTable.add(cultureLabel)
        val cultureImage = ImageGetter.getStatIcon("Culture")
        statsTable.add(cultureImage).padBottom(6f).size(20f)
        val invokePoliciesPage = {
                worldScreen.game.setScreen(PolicyPickerScreen(worldScreen, worldScreen.selectedCiv))
        }
        cultureLabel.onClick(invokePoliciesPage)
        cultureImage.onClick(invokePoliciesPage)

        if(worldScreen.gameInfo.isReligionEnabled()) {
            statsTable.add(faithLabel).padLeft(20f)
            val faithImage = ImageGetter.getStatIcon("Faith")
            statsTable.add(faithImage).padBottom(6f).size(20f)

            val invokeFaithOverview = {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Religion"))
            }

            faithLabel.onClick(invokeFaithOverview)
            faithImage.onClick(invokeFaithOverview)
        } else {
            statsTable.add("Religion: Off".toLabel()).padLeft(20f)
        }

        statsTable.pack()
        //statsTable.width = worldScreen.stage.width - 20
        return statsTable
    }

    private class OverviewAndSupplyTable(worldScreen: WorldScreen) : Table(BaseScreen.skin) {
        val unitSupplyImage = ImageGetter.getImage("OtherIcons/ExclamationMark")
            .apply { color = Color.FIREBRICK }
        val unitSupplyCell: Cell<Actor?>

        init {
            unitSupplyImage.onClick {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Units"))
            }

            val overviewButton = "Overview".toTextButton()
            overviewButton.addTooltip('e')
            overviewButton.onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv)) }

            unitSupplyCell = add()
            add(overviewButton).pad(10f)
            pack()
        }

        fun update(worldScreen: WorldScreen) {
            val newVisible = worldScreen.selectedCiv.stats().getUnitSupplyDeficit() > 0
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
                    worldScreen.selectedCiv.gameInfo.ruleSet,
                    worldScreen,
                    CivilopediaCategories.Nation,
                    worldScreen.selectedCiv.civName
                )
                worldScreen.game.setScreen(civilopediaScreen)
            }

            selectedCivIconHolder.onClick {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv))
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
            val nation = worldScreen.gameInfo.ruleSet.nations[worldScreen.selectedCiv.civName]!!
            val selectedCivIcon = ImageGetter.getNationIndicator(nation, 35f)
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
                fillerHeight = baseHeight
                buttonY = overviewButton.minHeight / 2f
            }
            leftRightNeeded * 2f > stage.width - statsWidth -> {
                // Shifting buttons down to below stats row is enough
                fillerHeight = statsRowHeight
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

    internal fun update(civInfo: CivilizationInfo) {
        updateStatsTable(civInfo)
        updateResourcesTable(civInfo)
        selectedCivTable.update(worldScreen)
        overviewButton.update(worldScreen)
        layoutButtons()
    }

    private fun updateStatsTable(civInfo: CivilizationInfo) {
        val nextTurnStats = civInfo.statsForNextTurn
        val goldPerTurn = "(" + (if (nextTurnStats.gold > 0) "+" else "") + nextTurnStats.gold.roundToInt() + ")"
        goldLabel.setText(civInfo.gold.toString() + goldPerTurn)

        scienceLabel.setText("+" + nextTurnStats.science.roundToInt())

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
        faithLabel.setText(civInfo.religionManager.storedFaith.toString() + "(+" + nextTurnStats.faith.roundToInt() + ")")
    }

    private fun updateResourcesTable(civInfo: CivilizationInfo) {
        resourcesWrapper.clear()

        val year = civInfo.gameInfo.getYear()
        val yearText = if (civInfo.isLongCountDisplay()) MayaCalendar.yearToMayaDate(year)
        else "[" + abs(year) + "] " + (if (year < 0) "BC" else "AD")
        turnsLabel.setText(Fonts.turn + "" + civInfo.gameInfo.turns + " | " + yearText.tr())

        val civResources = civInfo.getCivResources()
        for ((resource, label, icon) in resourceActors) {
            if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!))
                continue
            resourcesWrapper.add(icon).padRight(0f)
            val amount = civResources.get(resource, "All")?.amount ?: 0
            label.setText(amount)
            resourcesWrapper.add(label)
        }

        resourceTable.pack()
    }

    private fun getCultureText(civInfo: CivilizationInfo, nextTurnStats: Stats): String {
        var cultureString = "+" + nextTurnStats.culture.roundToInt()
        if (nextTurnStats.culture == 0f) return cultureString // when you start the game, you're not producing any culture

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        cultureString += if (turnsToNextPolicy <= 0f) " (!)"
            else " (" + ceil(turnsToNextPolicy).toInt() + ")"
        return cultureString
    }

    private fun getHappinessText(civInfo: CivilizationInfo): String {
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

package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
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
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setFontColor
import com.unciv.ui.utils.extensions.setFontSize
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
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
        // Not the Table, the Cells (all except one) have the background. To avoid gaps, _no_
        // padding except inside the cell actors, and all actors need to _fill_ their cell.
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

    private fun getStatsTable(): Table {
        val statsTable = Table()
        statsTable.defaults().pad(8f, 3f, 3f, 3f)

        fun addStat(label: Label, icon: String, isLast: Boolean = false, screenFactory: ()->BaseScreen) {
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
        fun addStat(label: Label, icon: String, overviewPage: String, isLast: Boolean = false) =
            addStat(label, icon, isLast) { EmpireOverviewScreen(worldScreen.selectedCiv, overviewPage) }

        addStat(goldLabel, "Gold", "Stats")
        addStat(scienceLabel, "Science") { TechPickerScreen(worldScreen.selectedCiv) }

        statsTable.add(happinessImage).padBottom(6f).size(20f)
        statsTable.add(happinessLabel).padRight(20f)
        val invokeResourcesPage = {
            worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Resources"))
        }
        happinessImage.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        addStat(cultureLabel, "Culture") { PolicyPickerScreen(worldScreen, worldScreen.selectedCiv) }
        if (worldScreen.gameInfo.isReligionEnabled()) {
            addStat(faithLabel, "Faith", "Religion", isLast = true)
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
            worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Resources"))
        }

        val strategicResources = worldScreen.gameInfo.ruleSet.tileResources.values
            .filter { it.resourceType == ResourceType.Strategic }
        for (resource in strategicResources) {
            val resourceImage = ImageGetter.getResourceImage(resource.name, 20f)
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
                worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Units"))
            }

            val overviewButton = "Overview".toTextButton()
            overviewButton.addTooltip('e')
            overviewButton.onClick { worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv)) }

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
                    CivilopediaCategories.Nation,
                    worldScreen.selectedCiv.civName
                )
                worldScreen.game.pushScreen(civilopediaScreen)
            }

            selectedCivIconHolder.onClick {
                worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv))
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
        val year = civInfo.gameInfo.getYear()
        val yearText = if (civInfo.isLongCountDisplay()) MayaCalendar.yearToMayaDate(year)
        else "[" + abs(year) + "] " + (if (year < 0) "BC" else "AD")
        turnsLabel.setText(Fonts.turn + "" + civInfo.gameInfo.turns + " | " + yearText.tr())

        resourcesWrapper.clearChildren()
        var firstPadLeft = 20f  // We want a distance from the turns entry to the first resource, but only if any resource is displayed
        val civResources = civInfo.getCivResources()
        for ((resource, label, icon) in resourceActors) {
            if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!))
                continue
            resourcesWrapper.add(icon).padLeft(firstPadLeft).padRight(0f)
            firstPadLeft = 5f
            val amount = civResources.get(resource, "All")?.amount ?: 0
            label.setText(amount)
            resourcesWrapper.add(label).padTop(8f)  // digits don't have descenders, so push them down a little
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

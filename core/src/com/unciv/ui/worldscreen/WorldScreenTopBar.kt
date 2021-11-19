package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt


/**
 * Table consisting of the menu button, current civ, some stats and the overview button for the top of [WorldScreen]
 */
class WorldScreenTopBar(val worldScreen: WorldScreen) : Table() {

    private var selectedCivLabel = worldScreen.selectedCiv.civName.toLabel()
    private var selectedCivIconHolder = Container<Actor>()

    private val turnsLabel = "Turns: 0/400".toLabel()
    private val goldLabel = "0".toLabel(colorFromRGB(225, 217, 71))
    private val scienceLabel = "0".toLabel(colorFromRGB(78, 140, 151))
    private val happinessLabel = "0".toLabel()
    private val cultureLabel = "0".toLabel(colorFromRGB(210, 94, 210))
    private val faithLabel = "0".toLabel(colorFromRGB(210, 94, 210)) // TODO: This colour should be changed at some point
    private val resourceLabels = HashMap<String, Label>()
    private val resourceImages = HashMap<String, Actor>()
    private val happinessImage = Group()

    // These are all to improve performance IE reduce update time (was 150 ms on my phone, which is a lot!)
    private val malcontentColor = colorFromRGB(239,83,80) // Color.valueOf("ef5350")
    private val happinessColor = colorFromRGB(92, 194, 77) // Color.valueOf("8cc24d")
    private val malcontentGroup = ImageGetter.getStatIcon("Malcontent")
    private val happinessGroup = ImageGetter.getStatIcon("Happiness")

    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        add(getStatsTable()).row()
        add(getResourceTable())

        pad(5f)
        pack()
        addActor(getMenuButton()) // needs to be after pack

        addActor(getSelectedCivilizationTable())

        addActor(getOverviewButton())
    }

    private fun getResourceTable(): Table {
        val resourceTable = Table()
        resourceTable.defaults().pad(5f)
        turnsLabel.onClick {
            if (worldScreen.selectedCiv.isLongCountDisplay()) {
                val gameInfo = worldScreen.selectedCiv.gameInfo
                MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, gameInfo.getYear())
            } else {
                worldScreen.game.setScreen(VictoryScreen(worldScreen))
            }
        }
        resourceTable.add(turnsLabel).padRight(20f)
        val revealedStrategicResources = worldScreen.gameInfo.ruleSet.tileResources.values
                .filter { it.resourceType == ResourceType.Strategic } // && currentPlayerCivInfo.tech.isResearched(it.revealedBy!!) }
        for (resource in revealedStrategicResources) {
            val resourceImage = ImageGetter.getResourceImage(resource.name, 20f)
            resourceImages[resource.name] = resourceImage
            resourceTable.add(resourceImage).padRight(0f)
            val resourceLabel = "0".toLabel()
            resourceLabels[resource.name] = resourceLabel
            resourceTable.add(resourceLabel)
            val invokeResourcesPage = {
                worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Resources"))
            }
            resourceLabel.onClick(invokeResourcesPage)
            resourceImage.onClick(invokeResourcesPage)
        }
        resourceTable.pack()

        return resourceTable
    }

    private fun getStatsTable(): Table {
        val statsTable = Table()
        statsTable.defaults().pad(3f)//.align(Align.top)

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
        statsTable.width = worldScreen.stage.width - 20
        return statsTable
    }

    private fun getMenuButton(): Image {
        val menuButton = ImageGetter.getImage("OtherIcons/MenuIcon")
                .apply { setSize(50f, 50f) }
        menuButton.color = Color.WHITE
        menuButton.onClick {
            val worldScreenMenuPopup = worldScreen.popups.firstOrNull { it is WorldScreenMenuPopup }
            if(worldScreenMenuPopup!=null)
                worldScreenMenuPopup.close()
            else WorldScreenMenuPopup(worldScreen).open(force = true)
        }
        menuButton.centerY(this)
        menuButton.x = menuButton.y
        return menuButton
    }

    private fun getOverviewButton(): Table {
        val rightTable = Table(BaseScreen.skin).apply{ defaults().pad(10f) }

        val unitSupplyImage = ImageGetter.getImage("OtherIcons/ExclamationMark")
            .apply { color = Color.FIREBRICK }
            .onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv, "Units")) }

        val overviewButton = Button(BaseScreen.skin)
        overviewButton.add("Overview".toLabel()).pad(10f)
        overviewButton.addTooltip('e')
        overviewButton.onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv)) }

        if (worldScreen.selectedCiv.stats().getUnitSupplyDeficit() > 0)
            rightTable.add(unitSupplyImage).size(50f)
        rightTable.add(overviewButton)

        rightTable.pack()
        rightTable.centerY(this)
        rightTable.x = worldScreen.stage.width - rightTable.width - 10

        return rightTable
    }

    private fun getSelectedCivilizationTable(): Table {
        val selectedCivTable = Table()
        selectedCivTable.centerY(this)
        selectedCivTable.left()
        selectedCivTable.x = getMenuButton().width + 20f

        selectedCivLabel.setFontSize(25)
        selectedCivLabel.onClick { worldScreen.game.setScreen(CivilopediaScreen( worldScreen.selectedCiv.gameInfo.ruleSet, CivilopediaCategories.Nation, worldScreen.selectedCiv.civName)) }

        val nation = worldScreen.gameInfo.ruleSet.nations[worldScreen.selectedCiv.civName]!!
        val selectedCivIcon = ImageGetter.getNationIndicator(nation, 35f)
        selectedCivIconHolder.actor = selectedCivIcon
        selectedCivIconHolder.onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv)) }

        selectedCivTable.add(selectedCivLabel).padRight(10f)
        selectedCivTable.add(selectedCivIconHolder)
        return selectedCivTable

    }

    internal fun update(civInfo: CivilizationInfo) {
        val revealedStrategicResources = civInfo.gameInfo.ruleSet.tileResources.values
                .filter { it.resourceType == ResourceType.Strategic }
        val civResources = civInfo.getCivResources()
        for (resource in revealedStrategicResources) {
            val isRevealed = resource.revealedBy == null || civInfo.tech.isResearched(resource.revealedBy!!)
            resourceLabels[resource.name]!!.isVisible = isRevealed
            resourceImages[resource.name]!!.isVisible = isRevealed
            if (!civResources.any { it.resource == resource }) resourceLabels[resource.name]!!.setText("0")
            else resourceLabels[resource.name]!!.setText(civResources.first { it.resource == resource }.amount.toString())
        }

        val year = civInfo.gameInfo.getYear()
        val yearText = if (civInfo.isLongCountDisplay()) MayaCalendar.yearToMayaDate(year)
            else "[" + abs(year) + "] " + (if (year < 0) "BC" else "AD")
        turnsLabel.setText(Fonts.turn + "" + civInfo.gameInfo.turns + " | " + yearText.tr())

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

        updateSelectedCivTable()
    }

    private fun updateSelectedCivTable() {
        if (selectedCivLabel.text.toString() == worldScreen.selectedCiv.civName.tr()) return

        selectedCivLabel.setText(worldScreen.selectedCiv.civName.tr())

        val nation = worldScreen.gameInfo.ruleSet.nations[worldScreen.selectedCiv.civName]!!
        val selectedCivIcon = ImageGetter.getNationIndicator(nation, 35f)
        selectedCivIconHolder.actor = selectedCivIcon
        selectedCivIconHolder.onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.selectedCiv)) }
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

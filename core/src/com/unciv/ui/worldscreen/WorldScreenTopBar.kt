package com.unciv.ui.worldscreen

import com.badlogic.gdx.Game
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.EmpireOverviewScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup
import java.time.Year
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class WorldScreenTopBar(val worldScreen: WorldScreen) : Table() {

    private val turnsLabel = "Turns: 0/400".toLabel()
    private val goldLabel = "Gold:".toLabel(colorFromRGB(225, 217, 71))
    private val scienceLabel = "Science:".toLabel(colorFromRGB(78, 140, 151))
    private val happinessLabel = "Happiness:".toLabel()
    private val cultureLabel = "Culture:".toLabel(colorFromRGB(210, 94, 210))
    private val resourceLabels = HashMap<String, Label>()
    private val resourceImages = HashMap<String, Actor>()
    private val happinessImage = Group()

    // These are all to improve performance IE reduce update time (was 150 ms on my phone, which is a lot!)
    private val malcontentColor = Color.valueOf("ef5350")
    private val happinessColor = colorFromRGB(92, 194, 77)
    private val malcontentGroup = ImageGetter.getStatIcon("Malcontent")
    private val happinessGroup = ImageGetter.getStatIcon("Happiness")

    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        add(getStatsTable()).row()
        add(getResourceTable())

        pad(5f)
        pack()
        addActor(getMenuButton()) // needs to be after pack

        val overviewButton = "Overview".toTextButton()
        overviewButton.labelCell.pad(10f)
        overviewButton.pack()
        overviewButton.onClick { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.viewingCiv)) }
        overviewButton.center(this)
        overviewButton.x = worldScreen.stage.width - overviewButton.width - 10
        addActor(overviewButton)
    }

    private fun getResourceTable(): Table {
        val resourceTable = Table()
        resourceTable.defaults().pad(5f)
        resourceTable.add(turnsLabel).padRight(20f)
        val revealedStrategicResources = worldScreen.gameInfo.ruleSet.tileResources.values
                .filter { it.resourceType == ResourceType.Strategic } // && currentPlayerCivInfo.tech.isResearched(it.revealedBy!!) }
        for (resource in revealedStrategicResources) {
            val resourceImage = ImageGetter.getResourceImage(resource.name, 20f)
            resourceImages[resource.name] = resourceImage
            resourceTable.add(resourceImage)
            val resourceLabel = "0".toLabel()
            resourceLabels[resource.name] = resourceLabel
            resourceTable.add(resourceLabel)
            val invokeResourcesPage = { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.viewingCiv, "Resources")) }
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
        statsTable.add(goldImage).padRight(20f).size(20f)
        val invokeStatsPage = { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.viewingCiv, "Stats")) }
        goldLabel.onClick(invokeStatsPage)
        goldImage.onClick(invokeStatsPage)

        statsTable.add(scienceLabel) //.apply { setAlignment(Align.center) }).align(Align.top)
        val scienceImage = ImageGetter.getStatIcon("Science")
        statsTable.add(scienceImage).padRight(20f).size(20f)
        val invokeTechScreen = { worldScreen.game.setScreen(TechPickerScreen(worldScreen.viewingCiv)) }
        scienceLabel.onClick(invokeTechScreen)
        scienceImage.onClick(invokeTechScreen)

        statsTable.add(happinessImage).size(20f)
        statsTable.add(happinessLabel).padRight(20f)//.apply { setAlignment(Align.center) }).align(Align.top)
        val invokeResourcesPage = { worldScreen.game.setScreen(EmpireOverviewScreen(worldScreen.viewingCiv, "Resources")) }
        happinessImage.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        statsTable.add(cultureLabel)//.apply { setAlignment(Align.center) }).align(Align.top)
        val cultureImage = ImageGetter.getStatIcon("Culture")
        statsTable.add(cultureImage).size(20f)
        val invokePoliciesPage = { worldScreen.game.setScreen(PolicyPickerScreen(worldScreen)) }
        cultureLabel.onClick(invokePoliciesPage)
        cultureImage.onClick(invokePoliciesPage)

        statsTable.pack()
        statsTable.width = worldScreen.stage.width - 20
        return statsTable
    }

    private fun getMenuButton(): Image {
        val menuButton = ImageGetter.getImage("OtherIcons/MenuIcon")
                .apply { setSize(50f, 50f) }
        menuButton.color = Color.WHITE
        menuButton.onClick {
            if (worldScreen.popups.none { it is WorldScreenMenuPopup })
                WorldScreenMenuPopup(worldScreen).open(force = true)
        }
        menuButton.centerY(this)
        menuButton.x = menuButton.y
        return menuButton
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

        val marathon = listOf<YearsToTurn>(YearsToTurn(100, 15.0), YearsToTurn(400, 10.0), YearsToTurn(570, 5.0), YearsToTurn(771, 2.0), YearsToTurn(900, 1.0), YearsToTurn(1000, 0.5), YearsToTurn(1500, 0.25))
        val epic     = listOf<YearsToTurn>(YearsToTurn(140, 25.0), YearsToTurn(230, 15.0), YearsToTurn(270, 10.0), YearsToTurn(360, 5.0), YearsToTurn(430, 2.0), YearsToTurn(530, 1.0), YearsToTurn(1500, 0.5))
        val standard = listOf<YearsToTurn>(YearsToTurn(75, 40.0), YearsToTurn(135, 25.0), YearsToTurn(160, 15.0), YearsToTurn(211, 10.0), YearsToTurn(270, 5.0), YearsToTurn(315, 2.0), YearsToTurn(440, 1.0))
        val quick    = listOf<YearsToTurn>(YearsToTurn(50, 60.0), YearsToTurn(80, 40.0), YearsToTurn(100, 25.0), YearsToTurn(130, 15.0), YearsToTurn(155, 10.0), YearsToTurn(195, 5.0), YearsToTurn(260, 2.0))

        val turns = civInfo.gameInfo.turns
        val gameSpeed: List<YearsToTurn> = when (civInfo.gameInfo.gameParameters.gameSpeed) {
            GameSpeed.Marathon -> marathon
            GameSpeed.Epic -> epic
            GameSpeed.Standard -> standard
            GameSpeed.Quick -> quick
        }

        val year = getYear(gameSpeed, turns).toInt()

        val yearText = "[" + abs(year) + "] " + if (year < 0) "BC" else "AD"
        turnsLabel.setText("Turn".tr() + " " + civInfo.gameInfo.turns + " | " + yearText.tr())
        turnsLabel.onClick { worldScreen.game.setScreen(VictoryScreen(worldScreen)) }

        val nextTurnStats = civInfo.statsForNextTurn
        val goldPerTurn = "(" + (if (nextTurnStats.gold > 0) "+" else "") + nextTurnStats.gold.roundToInt() + ")"
        goldLabel.setText(civInfo.gold.toFloat().roundToInt().toString() + goldPerTurn)

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
    }

    private fun getCultureText(civInfo: CivilizationInfo, nextTurnStats: Stats): String {
        var cultureString = "+" + Math.round(nextTurnStats.culture)
        if (nextTurnStats.culture == 0f) return cultureString // when you start the game, you're not producing any culture

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        if (turnsToNextPolicy > 0) cultureString += " (" + ceil(turnsToNextPolicy).toInt() + ")"
        else cultureString += " (!)"
        return cultureString
    }

    private fun getHappinessText(civInfo: CivilizationInfo): String {
        var happinessText = civInfo.getHappiness().toString()
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += "    " + "GOLDEN AGE".tr() + "(${civInfo.goldenAges.turnsLeftForCurrentGoldenAge})"
        else
            happinessText += (" (" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")")
        return happinessText
    }

    private class YearsToTurn(val toTurn: Int, val yearInterval: Double) // enum class with lists for each value group potentially more efficient?



    private fun getYear(speed: List<YearsToTurn>, turn: Int): Float {
        var year: Float = -4000f
        var i: Int = 0;
        var yearsPerTurn: Float
        // if macros are ever added to kotlin, this is one hell of a place for em'
        while (i < turn) {
            yearsPerTurn = speed.firstOrNull { turn < it.toTurn }?.yearInterval?.toFloat() ?: 0.5f
            year += yearsPerTurn;
            ++i;
        }

        return year
    }
}

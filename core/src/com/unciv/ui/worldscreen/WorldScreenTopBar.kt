package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tr
import com.unciv.models.stats.Stats
import com.unciv.ui.EmpireOverviewScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.WorldScreenMenuTable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class WorldScreenTopBar(val worldScreen: WorldScreen) : Table() {

    private val turnsLabel = "Turns: 0/400".toLabel()
    private val goldLabel = "Gold:".toLabel(colorFromRGB(225, 217, 71) )
    private val scienceLabel = "Science:".toLabel(colorFromRGB(78, 140, 151) )
    private val happinessLabel = "Happiness:".toLabel()
    private val cultureLabel = "Culture:".toLabel(colorFromRGB(210, 94, 210) )
    private val resourceLabels = HashMap<String, Label>()
    private val resourceImages = HashMap<String, Actor>()
    private val happinessImage = Group()
    // These are all to improve performance IE reduce update time (was 150 ms on my phone, which is a lot!)
    private val malcontentColor = Color.valueOf("ef5350")
    val happinessColor = colorFromRGB(92, 194, 77)
    val malcontentGroup = ImageGetter.getStatIcon("Malcontent")
    val happinessGroup = ImageGetter.getStatIcon("Happiness")

    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        add(getStatsTable()).row()
        add(getResourceTable())

        pad(5f)
        pack()
        addActor(getMenuButton()) // needs to be after pack

        val overviewButton = TextButton("Overview".tr(),CameraStageBaseScreen.skin)
        overviewButton.labelCell.pad(10f)
        overviewButton.pack()
        overviewButton.onClick { UncivGame.Current.setScreen(EmpireOverviewScreen(worldScreen.viewingCiv)) }
        overviewButton.center(this)
        overviewButton.x = worldScreen.stage.width-overviewButton.width-10
        addActor(overviewButton)
    }

    private fun getResourceTable(): Table {
        val resourceTable = Table()
        resourceTable.defaults().pad(5f)
        val revealedStrategicResources = worldScreen.gameInfo.ruleSet.TileResources.values
                .filter { it.resourceType == ResourceType.Strategic } // && currentPlayerCivInfo.tech.isResearched(it.revealedBy!!) }
        for (resource in revealedStrategicResources) {
            val resourceImage = ImageGetter.getResourceImage(resource.name,20f)
            resourceImages[resource.name] = resourceImage
            resourceTable.add(resourceImage)
            val resourceLabel = "0".toLabel()
            resourceLabels[resource.name] = resourceLabel
            resourceTable.add(resourceLabel)
        }
        resourceTable.pack()
        return resourceTable
    }

    private fun getStatsTable(): Table {
        val statsTable = Table()
        statsTable.defaults().pad(3f)//.align(Align.top)
        statsTable.add(turnsLabel).padRight(20f)
        statsTable.add(goldLabel)
        statsTable.add(ImageGetter.getStatIcon("Gold")).padRight(20f).size(20f)
        statsTable.add(scienceLabel) //.apply { setAlignment(Align.center) }).align(Align.top)
        statsTable.add(ImageGetter.getStatIcon("Science")).padRight(20f).size(20f)

        statsTable.add(happinessImage).size(20f)
        statsTable.add(happinessLabel).padRight(20f)//.apply { setAlignment(Align.center) }).align(Align.top)

        statsTable.add(cultureLabel)//.apply { setAlignment(Align.center) }).align(Align.top)
        statsTable.add(ImageGetter.getStatIcon("Culture")).size(20f)
        statsTable.pack()
        statsTable.width = worldScreen.stage.width - 20
        return statsTable
    }

    internal fun getMenuButton(): Image {
        val menuButton = ImageGetter.getImage("OtherIcons/MenuIcon")
                .apply { setSize(50f, 50f) }
        menuButton.color = Color.WHITE
        menuButton.onClick {
            if(worldScreen.stage.actors.none { it is WorldScreenMenuTable })
                WorldScreenMenuTable(worldScreen)
        }
        menuButton.centerY(this)
        menuButton.x = menuButton.y
        return menuButton
    }


    internal fun update(civInfo: CivilizationInfo) {
        val revealedStrategicResources = civInfo.gameInfo.ruleSet.TileResources.values
                .filter { it.resourceType == ResourceType.Strategic }
        val civResources = civInfo.getCivResources()
        for (resource in revealedStrategicResources) {
            val isRevealed = civInfo.tech.isResearched(resource.revealedBy!!)
            resourceLabels[resource.name]!!.isVisible = isRevealed
            resourceImages[resource.name]!!.isVisible = isRevealed
            if (!civResources.any { it.resource==resource}) resourceLabels[resource.name]!!.setText("0")
            else resourceLabels[resource.name]!!.setText(civResources.first { it.resource==resource }.amount.toString())
        }

        val turns = civInfo.gameInfo.turns
        val year = when{
            turns<=75 -> -4000+turns*40
            turns<=135 -> -1000+(turns-75)*25
            turns<=160 -> 500+(turns-135)*20
            turns<=210 -> 1000+(turns-160)*10
            turns<=270 -> 1500+(turns-210)*5
            turns<=320 -> 1800+(turns-270)*2
            turns<=440 -> 1900+(turns-320)
            else -> 2020+(turns-440)/2
        }

        val yearText = "["+ abs(year)+"] "+ if (year<0) "BC" else "AD"
        turnsLabel.setText("Turn".tr()+" " + civInfo.gameInfo.turns + " | "+ yearText.tr())

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
        if(nextTurnStats.culture==0f) return cultureString // when you start the game, you're not producing any culture

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        if (turnsToNextPolicy > 0) cultureString += " (" + ceil(turnsToNextPolicy).toInt() + ")"
        else cultureString += " (!)"
        return cultureString
    }

    private fun getHappinessText(civInfo: CivilizationInfo): String {
        var happinessText = civInfo.getHappiness().toString()
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += "    "+"GOLDEN AGE".tr()+"(${civInfo.goldenAges.turnsLeftForCurrentGoldenAge})"
        else
            happinessText += (" (" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")")
        return happinessText
    }

}

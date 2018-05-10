package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class CivStatsTable(val screen: WorldScreen) : Table() {

    private val turnsLabel = Label("Turns: 0/400", CameraStageBaseScreen.skin)
    private val goldLabel = Label("Gold:",CameraStageBaseScreen.skin)
    private val scienceLabel = Label("Science:",CameraStageBaseScreen.skin)
    private val happinessLabel = Label("Happiness:",CameraStageBaseScreen.skin)
    private val cultureLabel = Label("Culture:",CameraStageBaseScreen.skin)
    private val resourceLabels = HashMap<String, Label>()
    private val resourceImages = HashMap<String, Image>()

    init{
        val civBackground = ImageGetter.getDrawable("skin/civTableBackground.png")
        background = civBackground.tint(Color(0x004085e0))

        val resourceTable = Table()
        resourceTable.defaults().pad(5f)
        val revealedStrategicResources = GameBasics.TileResources.values
                .filter { it.resourceType== ResourceType.Strategic} // && civInfo.tech.isResearched(it.revealedBy!!) }
        for(resource in revealedStrategicResources){
            val fileName = "ResourceIcons/${resource.name}_(Civ5).png"
            val resourceImage = ImageGetter.getImage(fileName)
            resourceImages.put(resource.name,resourceImage)
            resourceTable.add(resourceImage).size(20f)
            val resourceLabel = Label("0",CameraStageBaseScreen.skin)
            resourceLabels.put(resource.name, resourceLabel)
            resourceTable.add(resourceLabel)
        }
        resourceTable.pack()
        add(resourceTable).row()


        val statsTable = Table()
        statsTable.defaults().padRight(20f).padBottom(10f)
        statsTable.add(getMenuButton())
        statsTable.add(turnsLabel)
        statsTable.add(goldLabel)
        statsTable.add(scienceLabel.apply { setAlignment(Align.center) })
        statsTable.add(happinessLabel.apply { setAlignment(Align.center) })
        statsTable.add(cultureLabel.apply { setAlignment(Align.center) })

        statsTable.pack()
        statsTable.width = screen.stage.width - 20
        add(statsTable)
        pack()
        width = screen.stage.width - 20
    }

    internal fun getMenuButton(): TextButton {
        val menuButton = TextButton("Menu", CameraStageBaseScreen.skin)
        menuButton.addClickListener {
            screen.optionsTable.isVisible = !screen.optionsTable.isVisible
        }
        return menuButton
    }


    internal fun update() {
        val civInfo = screen.civInfo

        val revealedStrategicResources = GameBasics.TileResources.values
                .filter { it.resourceType== ResourceType.Strategic} // &&  }
        val civResources = civInfo.getCivResources()
        for(resource in revealedStrategicResources){
            val isRevealed = civInfo.tech.isResearched(resource.revealedBy!!)
            resourceLabels[resource.name]!!.isVisible = isRevealed
            resourceImages[resource.name]!!.isVisible = isRevealed
            if(!civResources.containsKey(resource)) resourceLabels[resource.name]!!.setText("0")
            else resourceLabels[resource.name]!!.setText(civResources[resource]!!.toString())
        }

        turnsLabel.setText("Turns: " + civInfo.gameInfo.turns + "/400")

        val nextTurnStats = civInfo.getStatsForNextTurn()

        val goldPerTurn = "(" + (if (nextTurnStats.gold > 0) "+" else "") + Math.round(nextTurnStats.gold) + ")"
        goldLabel.setText("Gold: " + Math.round(civInfo.gold.toFloat()) + goldPerTurn)

        scienceLabel.setText("Science: +" + Math.round(nextTurnStats.science)
                + "\r\n" + civInfo.tech.getAmountResearchedText())

        var happinessText = "Happiness: " + civInfo.happiness+"\r\n"
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += "GOLDEN AGE (${civInfo.goldenAges.turnsLeftForCurrentGoldenAge})"
        else
            happinessText += ("(" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")")

        happinessLabel.setText(happinessText)

        val cultureString = "Culture: " + "+" + Math.round(nextTurnStats.culture) + "\r\n" +
                "(" + civInfo.policies.storedCulture + "/" + civInfo.policies.getCultureNeededForNextPolicy() + ")"

        cultureLabel.setText(cultureString)
    }

}
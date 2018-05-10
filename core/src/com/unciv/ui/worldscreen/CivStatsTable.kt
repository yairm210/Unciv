package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.fromRGB
import kotlin.math.ceil

class CivStatsTable(val screen: WorldScreen) : Table() {

    val labelStyle = Label.LabelStyle(Label("",CameraStageBaseScreen.skin).style)
            .apply { fontColor = Color.valueOf("f5f5f5ff") }

    private val turnsLabel = Label("Turns: 0/400", labelStyle)
    private val goldLabel = Label("Gold:",labelStyle).apply { color = Color().fromRGB(225,217,71) }
    private val scienceLabel = Label("Science:",labelStyle).apply { color = Color().fromRGB(78,140,151) }
    private val happinessLabel = Label("Happiness:",labelStyle).apply { color = Color().fromRGB(92,194,77) }
    private val cultureLabel = Label("Culture:",labelStyle).apply { color = Color().fromRGB(200,60,200) }
    private val resourceLabels = HashMap<String, Label>()
    private val resourceImages = HashMap<String, Image>()

    init{
        background = ImageGetter.getDrawable("skin/whiteDot.png").tint(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))

        //add(Table().apply {
            add(getStatsTable()).row()
            add(getResourceTable())
//            pack()
//        })

        pad(5f)
        pack()
        addActor(getMenuButton()) // needs to be after pack
    }

    private fun getResourceTable(): Table {
        val resourceTable = Table()
        resourceTable.defaults().pad(5f)
        val revealedStrategicResources = GameBasics.TileResources.values
                .filter { it.resourceType == ResourceType.Strategic } // && civInfo.tech.isResearched(it.revealedBy!!) }
        for (resource in revealedStrategicResources) {
            val fileName = "ResourceIcons/${resource.name}_(Civ5).png"
            val resourceImage = ImageGetter.getImage(fileName)
            resourceImages.put(resource.name, resourceImage)
            resourceTable.add(resourceImage).size(20f)
            val resourceLabel = Label("0", labelStyle)
            resourceLabels.put(resource.name, resourceLabel)
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
        statsTable.add(ImageGetter.getStatIcon("Gold")).padRight(20f)
        statsTable.add(scienceLabel) //.apply { setAlignment(Align.center) }).align(Align.top)
        statsTable.add(ImageGetter.getStatIcon("Science")).padRight(20f)

        statsTable.add(ImageGetter.getStatIcon("Happiness"))
        statsTable.add(happinessLabel).padRight(20f)//.apply { setAlignment(Align.center) }).align(Align.top)

        statsTable.add(cultureLabel)//.apply { setAlignment(Align.center) }).align(Align.top)
        statsTable.add(ImageGetter.getStatIcon("Culture"))
        statsTable.pack()
        statsTable.width = screen.stage.width - 20
        return statsTable
    }

    internal fun getMenuButton(): Image {
        val menuButton = ImageGetter.getImage("skin/menuIcon.png")
                .apply { setSize(50f,50f) }
        menuButton.color = Color.WHITE
        menuButton.addClickListener {
            screen.optionsTable.isVisible = !screen.optionsTable.isVisible
        }
        menuButton.y = this.height/2-menuButton.height/2
        menuButton.x = menuButton.y
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
        goldLabel.setText("" + Math.round(civInfo.gold.toFloat()) + goldPerTurn)

        scienceLabel.setText("+" + Math.round(nextTurnStats.science))

        var happinessText = civInfo.happiness.toString()
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += " GOLDEN AGE (${civInfo.goldenAges.turnsLeftForCurrentGoldenAge})"
        else
            happinessText += ("(" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")")
        happinessLabel.setText(happinessText)

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        var cultureString = "+" + Math.round(nextTurnStats.culture)
        if(turnsToNextPolicy>0) cultureString+= " ("+ ceil(turnsToNextPolicy)+")"
        else cultureString += " (!)"
        cultureLabel.setText(cultureString)
    }

}
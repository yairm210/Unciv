package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class CivStatsTable : Table {

    private val turnsLabel = Label("Turns: 0/400", CameraStageBaseScreen.skin)
    private val goldLabel = Label("Gold:",CameraStageBaseScreen.skin)
    private val scienceLabel = Label("Science:",CameraStageBaseScreen.skin)
    private val happinessLabel = Label("Happiness:",CameraStageBaseScreen.skin)
    private val cultureLabel = Label("Culture:",CameraStageBaseScreen.skin)


    internal constructor(screen: WorldScreen){
        val civBackground = ImageGetter.getDrawable("skin/civTableBackground.png")
        background = civBackground.tint(Color(0x004085bf))
        addCivilopediaButton(screen)
        add(turnsLabel)
        add(goldLabel)
        scienceLabel.setAlignment(Align.center)
        add(scienceLabel)
        happinessLabel.setAlignment(Align.center)
        add(happinessLabel)
        cultureLabel.setAlignment(Align.center)
        add(cultureLabel)

        pack()
        width = screen.stage.width - 20
    }

    internal fun addCivilopediaButton(screen: WorldScreen){
        row().pad(15f)
        val civilopediaButton = TextButton("Menu", CameraStageBaseScreen.skin)
        civilopediaButton.addClickListener {
            screen.optionsTable.isVisible = !screen.optionsTable.isVisible
        }
        civilopediaButton.label.setFontScale(screen.buttonScale)
        add(civilopediaButton)
                .size(civilopediaButton.width * screen.buttonScale, civilopediaButton.height * screen.buttonScale)
    }


    internal fun update(screen: WorldScreen) {
        val civInfo = screen.civInfo

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
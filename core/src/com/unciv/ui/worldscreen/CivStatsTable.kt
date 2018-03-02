package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class CivStatsTable internal constructor() : Table() {
    init {
        val civBackground = ImageGetter.getDrawable("skin/civTableBackground.png")
        background = civBackground.tint(Color(0x004085bf))
    }

    internal fun update(screen: WorldScreen) {
        val civInfo = screen.civInfo
        val skin = CameraStageBaseScreen.skin
        clear()
        row().pad(15f)

        val civilopediaButton = TextButton("Menu", skin)
        civilopediaButton.addClickListener {
                screen.optionsTable.isVisible = !screen.optionsTable.isVisible
            }


        civilopediaButton.label.setFontScale(screen.buttonScale)
        add(civilopediaButton)
                .size(civilopediaButton.width * screen.buttonScale, civilopediaButton.height * screen.buttonScale)

        add(Label("Turns: " + civInfo.gameInfo.turns + "/400", skin))

        val nextTurnStats = civInfo.getStatsForNextTurn()

        add(Label("Gold: " + Math.round(civInfo.gold.toFloat())
                + "(" + (if (nextTurnStats.gold > 0) "+" else "") + Math.round(nextTurnStats.gold) + ")", skin))

        val scienceLabel = Label("Science: +" + Math.round(nextTurnStats.science)
                + "\r\n" + civInfo.tech.getAmountResearchedText(), skin)
        scienceLabel.setAlignment(Align.center)
        add(scienceLabel)
        var happinessText = "Happiness: " + civInfo.happiness
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += "\r\n GOLDEN AGE (" + civInfo.goldenAges.turnsLeftForCurrentGoldenAge + ")"
        else
            happinessText += ("\r\n (" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")")
        val happinessLabel = Label(happinessText, skin)
        happinessLabel.setAlignment(Align.center)
        add(happinessLabel)
        val cultureString = ("Culture: " + "+" + Math.round(nextTurnStats.culture) + "\r\n"
                + "(" + civInfo.policies.storedCulture + "/" + civInfo.policies.getCultureNeededForNextPolicy() + ")")
        val cultureLabel = Label(cultureString, skin)
        cultureLabel.setAlignment(Align.center)
        add(cultureLabel)

        pack()

        setPosition(10f, screen.stage.height - 10f - height)
        width = screen.stage.width - 20

    }

}
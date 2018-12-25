package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick

class VictoryScreen : PickerScreen() {

    // todo translate this screen

    val playerCivInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()

    init {
        topTable.skin=skin
        topTable.defaults().pad(10f)
        topTable.add("Science victory")
        topTable.add("Cultural victory")
        topTable.add("Conquest victory")
        topTable.row()
        topTable.add(scienceVictoryColumn())
        topTable.add(culturalVictoryColumn())
        topTable.add(conquestVictoryColumn())
        topTable.row()
        topTable.add("Complete all the spaceship parts\n to win!")
        topTable.add("Complete 4 policy branches\n to win!")
        topTable.add("Destroy all enemies\n to win!")

        rightSideButton.isVisible=false

        if(playerCivInfo.victoryManager.hasWonScientificVictory()){
            won("You have won a scientific victory!")
        }
        else if(playerCivInfo.victoryManager.hasWonCulturalVictory()){
            won("You have won a cultural victory!")
        }
        else if(playerCivInfo.victoryManager.hasWonConquestVictory()){
            won("You have won a conquest victory!")
        }

        else setDefaultCloseAction()
    }

    fun won(description: String) {
        descriptionLabel.setText(description)

        rightSideButton.setText("Start new game".tr())
        rightSideButton.isVisible = true
        rightSideButton.enable()
        rightSideButton.onClick { UnCivGame.Current.startNewGame() }

        closeButton.setText("One more turn...!")
        closeButton.onClick {
            playerCivInfo.gameInfo.oneMoreTurnMode = true
            UnCivGame.Current.setWorldScreen()
        }
    }

    fun scienceVictoryColumn():Table{
        val t = Table()
        t.defaults().pad(5f)
        t.add(getMilestone("Built Apollo Program",playerCivInfo.getBuildingUniques().contains("Enables construction of Spaceship parts"))).row()

        val victoryManager= playerCivInfo.victoryManager

        for (key in victoryManager.requiredSpaceshipParts.keys)
            for (i in 0 until victoryManager.requiredSpaceshipParts[key]!!)
                t.add(getMilestone(key, victoryManager.currentsSpaceshipParts[key]!! > i)).row()     //(key, builtSpaceshipParts)

        return t
    }

    fun culturalVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for(branch in GameBasics.PolicyBranches.values) {
            val finisher = branch.policies.last().name
            t.add(getMilestone(finisher, playerCivInfo.policies.isAdopted(finisher))).row()
        }
        return t
    }

    fun conquestVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for (civ in playerCivInfo.gameInfo.civilizations) {
            if (civ.isPlayerCivilization() || civ.isBarbarianCivilization()) continue
            val civName =
                    if (playerCivInfo.diplomacy.containsKey(civ.civName)) civ.civName
                    else "???"
            t.add(getMilestone("Destroy $civName", civ.isDefeated())).row()
        }
        return t
    }

    fun getMilestone(text:String, achieved:Boolean): TextButton {
        val textButton = TextButton(text,skin)
        if(achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }


}
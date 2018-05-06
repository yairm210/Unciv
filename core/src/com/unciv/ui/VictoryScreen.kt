package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PickerScreen

class VictoryScreen : PickerScreen() {

    val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()

    init {
        topTable.skin=skin
        topTable.defaults().pad(10f)
        topTable.add("Science victory")
        topTable.add("Cultural victory")
        topTable.row()
        topTable.add(scienceVictoryColumn())
        topTable.add(culturalVictoryColumn())
        topTable.row()
        topTable.add("Complete all the spaceship parts to win!")
        topTable.add("Complete 4 policy branches to win!")

        rightSideButton.isVisible=false

        if(civInfo.scienceVictory.hasWon()){
            rightSideButton.setText("Start new game")
            rightSideButton.isVisible=true
            closeButton.isVisible=false
            descriptionLabel.setText("You have won a scientific victory!")
        }

        if(civInfo.policies.adoptedPolicies.count{it.endsWith("Complete")} > 3){
            descriptionLabel.setText("You have won a cultural victory!")
        }
    }

    fun won(){
        rightSideButton.setText("Start new game")
        rightSideButton.isVisible=true
        closeButton.isVisible=false
        rightSideButton.addClickListener { UnCivGame.Current.startNewGame(true) }
    }

    fun scienceVictoryColumn():Table{
        val t = Table()
        t.defaults().pad(5f)
        t.add(getMilestone("Built Apollo Program",civInfo.buildingUniques.contains("ApolloProgram"))).row()

        val scienceVictory = civInfo.scienceVictory

        for (key in scienceVictory.requiredParts.keys)
            for (i in 0 until scienceVictory.requiredParts[key]!!)
                t.add(getMilestone(key, scienceVictory.currentParts[key]!! > i)).row()     //(key, builtSpaceshipParts)

        return t
    }

    fun culturalVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for(branch in GameBasics.PolicyBranches.values) {
            val finisher = branch.policies.last().name
            t.add(getMilestone(finisher, civInfo.policies.isAdopted(finisher))).row()
        }
        return t
    }

    fun getMilestone(text:String, achieved:Boolean): TextButton {
        val TB = TextButton(text,skin)
        if(achieved) TB.setColor(Color.GREEN)
        else TB.setColor(Color.GRAY)
        return TB
    }


}
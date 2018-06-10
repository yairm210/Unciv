package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.addClickListener

class VictoryScreen : PickerScreen() {

    val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()

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

        if(civInfo.scienceVictory.hasWon()){
            descriptionLabel.setText("You have won a scientific victory!")
            won()
        }

        if(civInfo.policies.adoptedPolicies.count{it.endsWith("Complete")} > 3){
            descriptionLabel.setText("You have won a cultural victory!")
            won()
        }

        if(civInfo.gameInfo.civilizations.all { it.isPlayerCivilization() || it.isDefeated() }){
            descriptionLabel.setText("You have won a conquest victory!")
            won()
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
        t.add(getMilestone("Built Apollo Program",civInfo.buildingUniques.contains("Allows the building of spaceship parts"))).row()

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

    fun conquestVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for (civ in civInfo.gameInfo.civilizations){
            if(civ.isPlayerCivilization() || civ.isBarbarianCivilization()) continue
            t.add(getMilestone("Destroy "+civ.civName, civ.isDefeated())).row()
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
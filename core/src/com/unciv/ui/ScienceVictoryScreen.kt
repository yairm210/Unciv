package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.linq.Linq
import com.unciv.models.linq.LinqCounter
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen

class ScienceVictoryScreen(internal val civInfo: CivilizationInfo) : PickerScreen() {

    init {
        val scienceVictory = civInfo.scienceVictory
        val builtSpaceshipParts = scienceVictory.currentParts.clone()

        for (key in scienceVictory.requiredParts.keys)
        // can't take the keyset because we would be modifying it!
            for (i in 0 until scienceVictory.requiredParts[key]!!)
                addPartButton(key, builtSpaceshipParts)

        rightSideButton.isVisible = false

        if (!civInfo.buildingUniques.contains("ApolloProgram"))
            descriptionLabel.setText("You must build the Apollo Program before you can build spaceship parts!")
        else
            descriptionLabel.setText("Apollo program is built - you may construct spaceship parts in your cities!")

        val tutorial = Linq<String>()
        tutorial.add("This is the science victory screen, where you" +
                "\r\n  can see your progress towards constructing a " +
                "\r\n  spaceship to propel you towards the stars.")
        tutorial.add("There are 6 spaceship parts you must build, " + "\r\n  and they all require advanced technologies")
        if (!civInfo.buildingUniques.contains("ApolloProgram"))
            tutorial.add("You can start constructing spaceship parts" + "\r\n  only after you have finished the Apollo Program")
        displayTutorials("ScienceVictoryScreenEntered", tutorial)
    }

    private fun addPartButton(partName: String, parts: LinqCounter<String>) {
        topTable.row()
        val button = TextButton(partName, CameraStageBaseScreen.skin)
        button.touchable = Touchable.disabled
        if (!civInfo.buildingUniques.contains("ApolloProgram"))
            button.color = Color.GRAY
        else if (parts[partName]!! > 0) {
            button.color = Color.GREEN
            parts.add(partName, -1)
        }
        topTable.add<TextButton>(button).pad(10f)
    }
}



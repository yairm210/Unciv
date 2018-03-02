package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileImprovement
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class ImprovementPickerScreen(tileInfo: TileInfo) : PickerScreen() {
    private var selectedImprovement: TileImprovement? = null

    init {
        val civInfo = game.gameInfo.getPlayerCivilization()

        rightSideButton.setText("Pick improvement")
        rightSideButton.addClickListener {
                tileInfo.startWorkingOnImprovement(selectedImprovement!!, civInfo)
                game.setWorldScreen()
                dispose()
            }

        val regularImprovements = VerticalGroup()
        regularImprovements.space(10f)
        for (improvement in GameBasics.TileImprovements.values) {
            if (!tileInfo.canBuildImprovement(improvement, civInfo) || improvement.name == tileInfo.improvement) continue
            val improvementTextButton = TextButton(
                    improvement.name + "\r\n" + improvement.getTurnsToBuild(civInfo) + " turns",
                    CameraStageBaseScreen.skin
            )

            improvementTextButton.addClickListener {
                    selectedImprovement = improvement
                    pick(improvement.name)
                    descriptionLabel.setText(improvement.description)
                }
            regularImprovements.addActor(improvementTextButton)
        }
        topTable.add(regularImprovements)
    }
}
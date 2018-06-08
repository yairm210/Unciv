package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.setFontColor

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
            val improvementButton = Button(skin)


            if(improvement.name.startsWith("Remove"))
                improvementButton.add(ImageGetter.getImage("OtherIcons/Stop.png")).size(30f).pad(10f)
            else  improvementButton.add(ImageGetter.getImprovementIcon(improvement.name)).size(30f).pad(10f)

            improvementButton.add(Label(improvement.name + " - " + improvement.getTurnsToBuild(civInfo) + " turns",skin)
                    .setFontColor(Color.WHITE)).pad(10f)

            improvementButton.addClickListener {
                    selectedImprovement = improvement
                    pick(improvement.name)
                    descriptionLabel.setText(improvement.description)
                }
            regularImprovements.addActor(improvementButton)
        }
        topTable.add(regularImprovements)
    }
}
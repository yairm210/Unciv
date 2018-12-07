package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontColor

class ImprovementPickerScreen(tileInfo: TileInfo) : PickerScreen() {
    private var selectedImprovement: TileImprovement? = null

    init {
        val civInfo = game.gameInfo.getPlayerCivilization()

        rightSideButton.setText("Pick improvement")
        rightSideButton.onClick {
            tileInfo.startWorkingOnImprovement(selectedImprovement!!, civInfo)
            if(tileInfo.civilianUnit!=null) tileInfo.civilianUnit!!.action=null // this is to "wake up" the worker if it's sleeping
            game.setWorldScreen()
            dispose()
        }

        val regularImprovements = VerticalGroup()
        regularImprovements.space(10f)
        for (improvement in GameBasics.TileImprovements.values) {
            if (!tileInfo.canBuildImprovement(improvement, civInfo)) continue
            if(improvement.name == tileInfo.improvement) continue
            if(improvement.name==tileInfo.improvementInProgress) continue

            val improvementButton = Button(skin)

            if(improvement.name.startsWith("Remove"))
                improvementButton.add(ImageGetter.getImage("OtherIcons/Stop.png")).size(30f).pad(10f)
            else improvementButton.add(ImageGetter.getImprovementIcon(improvement.name,30f)).pad(10f)

            improvementButton.add(Label(improvement.name + " - " + improvement.getTurnsToBuild(civInfo) + " {turns}".tr(),skin)
                    .setFontColor(Color.WHITE)).pad(10f)

            improvementButton.onClick {
                    selectedImprovement = improvement
                    pick(improvement.name)
                    descriptionLabel.setText(improvement.description)
                }
            regularImprovements.addActor(improvementButton)
        }
        topTable.add(regularImprovements)
    }
}


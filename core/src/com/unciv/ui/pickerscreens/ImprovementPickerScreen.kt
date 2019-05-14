package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
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
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()
        setDefaultCloseAction()

        fun accept(improvement: TileImprovement?) {
            if (improvement != null) {
                tileInfo.startWorkingOnImprovement(improvement, currentPlayerCiv)
                if (tileInfo.civilianUnit != null) tileInfo.civilianUnit!!.action = null // this is to "wake up" the worker if it's sleeping
                game.setWorldScreen()
                dispose()
            }
        }

        rightSideButton.setText("Pick improvement".tr())
        rightSideButton.onClick {
            accept(selectedImprovement)
        }

        val regularImprovements = Table()

        for (improvement in GameBasics.TileImprovements.values) {
            if (!tileInfo.canBuildImprovement(improvement, currentPlayerCiv)) continue
            if(improvement.name == tileInfo.improvement) continue
            if(improvement.name==tileInfo.improvementInProgress) continue

            val improvementButton = Button(skin)

            if(improvement.name.startsWith("Remove"))
                improvementButton.add(ImageGetter.getImage("OtherIcons/Stop.png")).size(30f).pad(10f)
            else improvementButton.add(ImageGetter.getImprovementIcon(improvement.name,30f)).pad(10f)

            improvementButton.add(Label(improvement.name.tr() + " - " + improvement.getTurnsToBuild(currentPlayerCiv) + " {turns}".tr(),skin)
                    .setFontColor(Color.WHITE)).pad(10f)

            improvementButton.onClick {
                accept(improvement)
            }
            regularImprovements.add(improvementButton)

            val helpButton = Button(skin)
            helpButton.add(Label("?", skin)).pad(15f)

            helpButton.onClick {
                selectedImprovement = improvement
                pick(improvement.name)
                descriptionLabel.setText(improvement.description)
            }
            regularImprovements.add(helpButton).pad(5f).row()

        }
        topTable.add(regularImprovements)
    }
}


package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addSeparatorVertical
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class ImprovementPickerScreen(tileInfo: TileInfo, onAccept: ()->Unit) : PickerScreen() {
    private var selectedImprovement: TileImprovement? = null

    init {
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()
        setDefaultCloseAction()

        fun accept(improvement: TileImprovement?) {
            if (improvement != null) {
                tileInfo.startWorkingOnImprovement(improvement, currentPlayerCiv)
                if (tileInfo.civilianUnit != null) tileInfo.civilianUnit!!.action = null // this is to "wake up" the worker if it's sleeping
                onAccept()
                game.setWorldScreen()
                dispose()
            }
        }

        rightSideButton.setText("Pick improvement".tr())
        rightSideButton.onClick {
            accept(selectedImprovement)
        }

        val regularImprovements = VerticalGroup()
        regularImprovements.space(10f)

        for (improvement in tileInfo.tileMap.gameInfo.ruleSet.TileImprovements.values) {
            if (!tileInfo.canBuildImprovement(improvement, currentPlayerCiv)) continue
            if(improvement.name == tileInfo.improvement) continue
            if(improvement.name==tileInfo.improvementInProgress) continue

            val group = Table()

            val image = ImageGetter.getImprovementIcon(improvement.name,30f)

            group.add(image).size(30f).pad(10f)

            var labelText = improvement.name.tr() + " - " + improvement.getTurnsToBuild(currentPlayerCiv) + " {turns}"
            if(tileInfo.hasViewableResource(currentPlayerCiv) && tileInfo.getTileResource().improvement == improvement.name)
                labelText += "\n"+"Provides [${tileInfo.resource}]".tr()
            if(tileInfo.improvement!=null && improvement.name!=RoadStatus.Road.name
                    && improvement.name!=RoadStatus.Railroad.name && !improvement.name.startsWith("Remove"))
                labelText += "\n" + "Replaces [${tileInfo.improvement}]".tr()

            group.add(labelText.toLabel()).pad(10f)

            group.touchable = Touchable.enabled
            group.onClick {
                selectedImprovement = improvement
                pick(improvement.name.tr())
                val ruleSet = tileInfo.tileMap.gameInfo.ruleSet
                descriptionLabel.setText(improvement.getDescription(ruleSet))
            }

            val pickNow = "Pick now!".toLabel()
            pickNow.onClick {
                accept(improvement)
            }

            val improvementButton = Button(skin)
            improvementButton.add(group).padRight(10f).fillY()
            improvementButton.addSeparatorVertical()
            improvementButton.add(pickNow).padLeft(10f).fill()
            regularImprovements.addActor(improvementButton)

        }
        topTable.add(regularImprovements)
    }
}


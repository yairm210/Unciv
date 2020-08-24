package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.round

class ImprovementPickerScreen(val tileInfo: TileInfo, val onAccept: ()->Unit) : PickerScreen() {
    private var selectedImprovement: TileImprovement? = null
    val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()

    fun accept(improvement: TileImprovement?) {
        if (improvement == null) return
        if (improvement.name == Constants.cancelImprovementOrder) {
            tileInfo.stopWorkingOnImprovement()
            // no onAccept() - Worker can stay selected
        } else {
            tileInfo.startWorkingOnImprovement(improvement, currentPlayerCiv)
            if (tileInfo.civilianUnit != null) tileInfo.civilianUnit!!.action = null // this is to "wake up" the worker if it's sleeping
            onAccept()
        }
        game.setWorldScreen()
        dispose()
    }

    init {
        setDefaultCloseAction()

        rightSideButton.setText("Pick improvement".tr())
        rightSideButton.onClick {
            accept(selectedImprovement)
        }

        val regularImprovements = Table()
        regularImprovements.defaults().pad(5f)

        for (improvement in tileInfo.tileMap.gameInfo.ruleSet.tileImprovements.values) {
            if (!tileInfo.canBuildImprovement(improvement, currentPlayerCiv)) continue
            if (improvement.name == tileInfo.improvement) continue

            val improvementButtonTable = Table()

            val image = ImageGetter.getImprovementIcon(improvement.name, 30f)

            improvementButtonTable.add(image).size(30f).pad(10f)

            var labelText = improvement.name.tr()
            val turnsToBuild = improvement.getTurnsToBuild(currentPlayerCiv)
            if (turnsToBuild > 0) labelText += " - $turnsToBuild ${Fonts.turn}"
            val provideResource = tileInfo.hasViewableResource(currentPlayerCiv) && tileInfo.getTileResource().improvement == improvement.name
            if (provideResource) labelText += "\n" + "Provides [${tileInfo.resource}]".tr()
            val removeImprovement = (improvement.name != RoadStatus.Road.name
                    && improvement.name != RoadStatus.Railroad.name && !improvement.name.startsWith("Remove") && improvement.name != Constants.cancelImprovementOrder)
            if (tileInfo.improvement != null && removeImprovement) labelText += "\n" + "Replaces [${tileInfo.improvement}]".tr()

            improvementButtonTable.add(labelText.toLabel()).pad(10f)

            improvementButtonTable.touchable = Touchable.enabled
            improvementButtonTable.onClick {
                selectedImprovement = improvement
                pick(improvement.name.tr())
                val ruleSet = tileInfo.tileMap.gameInfo.ruleSet
                descriptionLabel.setText(improvement.getDescription(ruleSet))
            }

            val pickNow = "Pick now!".toLabel().onClick { accept(improvement) }

            val statIcons = Table()

            // get benefits of the new improvement
            val stats = tileInfo.getImprovementStats(improvement, currentPlayerCiv, tileInfo.getCity())
            // subtract the benefits of the replaced improvement, if any
            val existingImprovement = tileInfo.getTileImprovement()
            if (existingImprovement != null && removeImprovement) {
                val existingStats = tileInfo.getImprovementStats(existingImprovement, currentPlayerCiv, tileInfo.getCity())
                stats.add(existingStats.times(-1.0f))
            }

            // icons of benefits (food, gold, etc) by improvement
            val statsTable = Table()
            statsTable.defaults()
            for (stat in stats.toHashMap()) {
                val statValue = round(stat.value).toInt()
                if (statValue == 0) continue

                statsTable.add(ImageGetter.getStatIcon(stat.key.name)).size(20f).padRight(3f)

                val valueLabel = statValue.toString().toLabel()
                valueLabel.color = if (statValue < 0) Color.RED else Color.WHITE

                statsTable.add(valueLabel).padRight(13f)
            }

            // icon for adding the resource by improvement
            if (provideResource)
                statIcons.add(ImageGetter.getResourceImage(tileInfo.resource.toString(), 30f)).pad(3f)

            // icon for removing the resource by replacing improvement
            if (removeImprovement && tileInfo.hasViewableResource(currentPlayerCiv) && tileInfo.getTileResource().improvement == tileInfo.improvement) {
                val crossedResource = Group()
                val cross = ImageGetter.getImage("OtherIcons/Close")
                cross.setSize(30f, 30f)
                cross.color = Color.RED
                val resourceIcon = ImageGetter.getResourceImage(tileInfo.resource.toString(), 30f)
                crossedResource.addActor(resourceIcon)
                crossedResource.addActor(cross)
                statIcons.add(crossedResource).padTop(30f).padRight(33f)
            }

            statIcons.add(statsTable).padLeft(13f)
            regularImprovements.add(statIcons).align(Align.right)

            val improvementButton = Button(skin)
            improvementButton.add(improvementButtonTable).pad(5f).fillY()
            if (improvement.name == tileInfo.improvementInProgress) improvementButton.color= Color.GREEN
            regularImprovements.add(improvementButton)
            regularImprovements.add(pickNow).padLeft(10f)
            regularImprovements.row()
        }

        topTable.add(regularImprovements)
    }
}


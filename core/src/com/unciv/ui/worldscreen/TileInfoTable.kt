package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class TileInfoTable(private val worldScreen: WorldScreen, internal val civInfo: CivilizationInfo) : Table() {

    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x004085bf))
        tileTableBackground.minHeight = 0f
        tileTableBackground.minWidth = 0f
        background = tileTableBackground
    }

    internal fun updateTileTable(tile: TileInfo) {
        clearChildren()
        val stats = tile.getTileStats(civInfo)
        pad(20f)
        columnDefaults(0).padRight(10f)

        val skin = CameraStageBaseScreen.skin

        if (tile.explored) {
            add(Label(tile.toString(), skin)).colspan(2)
            row()


            for (entry in stats.toHashMap().filterNot { it.value == 0f }) {
                add(ImageGetter.getStatIcon(entry.key.toString())).align(Align.right)
                add(Label(entry.value.toInt().toString(), skin)).align(Align.left)
                row()
            }
        }


        if (tile.unit != null) {
            val tileMapHolder = worldScreen.tileMapHolder
            val buttonText = if (tileMapHolder.unitTile == tile)"Stop movement" else "Move to"
            var moveUnitButton = TextButton(buttonText, skin)
            moveUnitButton.label.setFontScale(worldScreen.buttonScale)
            if (tile.unit!!.currentMovement == 0f) {
                moveUnitButton.color = Color.GRAY
                moveUnitButton.touchable = Touchable.disabled
            }
            moveUnitButton.addClickListener {
                if (tileMapHolder.unitTile != null) {
                    tileMapHolder.unitTile = null
                    tileMapHolder.updateTiles()
                    return@addClickListener
                }
                tileMapHolder.unitTile = tile

                // Set all tiles transparent except those in unit range
                for (TG in tileMapHolder.tileGroups.linqValues()) TG.setColor(0f, 0f, 0f, 0.3f)

                val distanceToTiles = civInfo.gameInfo.tileMap.getDistanceToTilesWithinTurn(
                        tileMapHolder.unitTile!!.position,
                        tileMapHolder.unitTile!!.unit!!.currentMovement,
                        civInfo.tech.isResearched("Machinery"))

                for (tile in distanceToTiles.keys) {
                    tileMapHolder.tileGroups[tile.position.toString()]!!.color = Color.WHITE
                }

                worldScreen.update()
            }

            add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.width * worldScreen.buttonScale, moveUnitButton.height * worldScreen.buttonScale).row()

            for (button in UnitActions().getUnitActions(tile.unit!!,tile,civInfo))
                add(button).colspan(2)
                        .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()

            pack()

            setPosition(worldScreen.stage.width - 10f - width, 10f)
        }
    }
}


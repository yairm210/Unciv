package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addBorderAllowOpacity
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.DebugUtils

class TileInfoTable(private val worldScreen: WorldScreen) : Table(BaseScreen.skin) {
    var selectedCiv = worldScreen.selectedCiv
    var position by worldScreen.game.settings::tileInfoPosition

    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/TileInfoTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        touchable = Touchable.enabled
        addListener(MoveDragListener())
    }

    internal fun updateTileTable(tile: Tile?) {
        clearChildren()
        if (tile == null || !(DebugUtils.VISIBLE_MAP || selectedCiv.hasExplored(tile))) {
            isVisible = false
            return
        }

        pad(5f)

        add(getStatsTable(tile)).left().row()
        add(MarkupRenderer.render(TileDescription.toMarkup(tile, selectedCiv), padding = 0f, iconDisplay = IconDisplay.None) {
            worldScreen.openCivilopedia(it)
        } ).padTop(5f).row()
        if (DebugUtils.VISIBLE_MAP) add(tile.position.toPrettyString().toLabel()).colspan(2).pad(5f)
        if (DebugUtils.SHOW_TILE_IMAGE_LOCATIONS){
            val imagesString = "Images: " + worldScreen.mapHolder.tileGroups[tile]!!.layerTerrain.tileBaseImages.joinToString{"\n"+it.name}
            add(imagesString.toLabel())
        }

        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    internal fun setPosition() {
        val x: Float
        val y: Float
        val isBottom = position == GameSettings.WidgetPosition.Bottom
        if (worldScreen.game.settings.showMinimap) {
            x = stage.width - (if (isBottom) worldScreen.minimapWrapper.width + 5f else 0f)
            y = if (isBottom) 0f else worldScreen.minimapWrapper.height + 5f
        } else {
            x = stage.width
            y = 0f
        }
        setPosition(x, y, Align.bottomRight)
    }

    private fun getStatsTable(tile: Tile): Table {
        val table = Table()
        table.defaults().pad(2f)
        
        for ((key, value) in tile.stats.getTileStats(selectedCiv)) {
            table.add((key.character + value.toInt().toString()).toLabel())
                .align(Align.left).padRight(5f)
        }
        table.touchable = Touchable.enabled
        table.onClick {
            Popup(worldScreen).apply {
                for ((name, stats) in tile.stats.getTileStatsBreakdown(tile.getCity(), selectedCiv))
                    add("${name.tr()}: {${stats.clone()}}".toLabel()).row()
                addCloseButton()
            }.open()
        }
        return table
    }

    private inner class MoveDragListener : DragListener() {
        private var dragged = false
        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
            super.touchDragged(event, x, y, pointer)
            if (!isDragging) return
            dragged = true
            this@TileInfoTable.setPosition(event.stageX, event.stageY, Align.center)
        }
        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
            if (dragged) event.stop()
            super.touchUp(event, x, y, pointer, button)
            if (dragged) {
                position = if (event.stageY > stage.width - event.stageX) GameSettings.WidgetPosition.Right
                    else GameSettings.WidgetPosition.Bottom
                setPosition()
                GUI.setUpdateWorldOnNextRender() // full update so the notification scroll can adapt
            }
        }
    }
}

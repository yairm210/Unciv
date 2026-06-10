package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.BasicTileInfo
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.DebugUtils
import kotlin.math.abs
import com.unciv.view.CivView
import com.unciv.view.TileView

class TileInfoTable(
    private val worldScreen: WorldScreen,
    override var civView: CivView = worldScreen.selectedGameView.civView
) : BasicTileInfo(
    worldScreen,
    civView,
    null,
    false,
    "WorldScreen/TileInfoTable", 1f, "WorldScreen/TileInfoTableBorder"
) {
    // For pick-up by UiElementDocsWriter
    // skinStrings.getUiBackground("WorldScreen/TileInfoTable")
    // skinStrings.getUiBackground("WorldScreen/TileInfoTableBorder")
    companion object {
        private const val attentionKey = "Tile info draggable get attention"
    }

    var position by worldScreen.game.settings::tileInfoPosition
    val indicator = ImageGetter.getImage("OtherIcons/Increase")
    val attentionAnimation = attentionKey !in worldScreen.game.settings.tutorialsShown

    init {
        touchable = Touchable.enabled
        indicator.touchable = Touchable.disabled
        indicator.rotation = -90f
        indicator.setSize(16f)
        indicator.setOrigin(Align.center)
        indicator.color.a = 0f

        addListener(MoveDragListener())
    }

    override fun shouldBeVisible(tileView: TileView) = DebugUtils.VISIBLE_MAP || civView.hasExplored(tileView)

    override fun Table.addAdditionalContent(tileView: TileView) {
        if (DebugUtils.VISIBLE_MAP)
            add(tileView.position().toPrettyString()).colspan(2).pad(5f) // No toLabel() to avoid tr(), eats comma
        if (DebugUtils.SHOW_TILE_IMAGE_LOCATIONS) {
            val imagesString = "Images: " + worldScreen.mapHolder.tileGroups[tileView]!!.layerTerrain.tileBaseImages.joinToString{"\n"+it.name}
            add(imagesString.toLabel())
        }
        addActor(indicator)
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

        if (!worldScreen.game.settings.showMinimap) {
            indicator.color.a = 0f
            return
        }
        indicator.setPosition(width - 5f, height - 5f, Align.topRight)
        if (indicator.color.a > 0f) return
        if (attentionAnimation) {
            indicator.addAction(AttentionAction())
            worldScreen.game.settings.tutorialsShown += attentionKey
        } else {
            indicator.color.a = 1f
        }
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

    private class AttentionAction : TemporalAction(4f) {
        private lateinit var color: Color
        private val tempColor = Color()
        override fun begin() {
            color = target.color
        }
        override fun update(percent: Float) {
            val t = percentToTriangle5x(percent)
            color.set(
                if (percent == 0f) Color.CLEAR
                else if (percent == 1f) Color.WHITE
                else if (percent < 0.1f) tempLerp(Color.CLEAR, Color.RED, t)
                else tempLerp(Color.WHITE, Color.RED, t)
            )
            target.setScale(1f + t)
        }
        private fun percentToTriangle5x(percent: Float) =
            1f - abs((percent * 5f) % 1f * 2f - 1f)
        private fun tempLerp(a: Color, b: Color, t: Float) =
            tempColor.set(a).lerp(b, t)
    }
}

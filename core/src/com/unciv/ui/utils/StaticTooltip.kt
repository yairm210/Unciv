package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager
import com.unciv.UncivGame
import com.unciv.ui.utils.KeyPressDispatcher.Companion.keyboardAvailable

/**
 * Modify Gdx [Tooltip] to place the tip over the top right corner of its target
 * 
 * Usage: [table][Table].addStaticTip([key][Char])
 *
 * Note: This is currently limited to displaying a single character in a circle of hardcoded size, 
 * displayed half-overlapping, partially out of the parent's bounding box, over the top right part
 * of a Table-based Button. Adapting to new usecases shouldn't be too hard, though.
 * 
 * @param contents The actor to display as Tooltip
 * @param manager The [TooltipManager] to use - suggested: [tooltipManager]
 */
class StaticTooltip(contents: Actor, manager: TooltipManager) : Tooltip<Actor>(contents,manager) {
    init {
        // Neither this nor tooltipManager.animations = false actually make the tip appear
        // instantly. However, they hide the bug that the very first appearance is misplaced.
        setInstant(true)
    }

    // mark event as handled while Tooltip is shown, ignore otherwise
    override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
        if (container.hasParent()) return false
        return super.mouseMoved(event, x, y)
    }

    // put the tip in a fixed place relative to the target actor
    // event.listenerActor is our button, and x/y are relative to its bottom left edge
    override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
        super.enter(event, event.listenerActor.width, event.listenerActor.height, pointer, fromActor)
    }
    
    companion object {
        /** Sizes the character height relative to the surrounding circle size */
        const val charHeightToCircleSize = 28f / 32f
        
        /** A factory for the default [TooltipManager] with a few altered properties */
        fun tooltipManager(size: Float): TooltipManager = 
            TooltipManager.getInstance().apply {
                initialTime = 0f
                offsetX = -0.75f * size // less than the tip actor width so it overshoots a little which looks nice
                offsetY = 0f
                animations = false
            }

        /** Extension adds a circled single character as Tooltip over the top right part of a receiver Table */
        fun Table.addStaticTip (key: Char, size: Float = 26f) {
            if (!keyboardAvailable || key == Char.MIN_VALUE) return
            val displayKey = if (key in "iI") 'i' else key.toUpperCase()

            // Todo: Inefficient.
            // The pixels have likely already been fetched from the font implementation
            // and cached in a TextureRegion - but I'm lacking the skills to get them from there.
            val keyPixmap = UncivGame.Current.fontImplementation!!.getCharPixmap(displayKey)
            val height = size * charHeightToCircleSize
            val width = height * keyPixmap.width / keyPixmap.height
            val keyImage = Image(Texture(keyPixmap)).apply {
                setSize(width, height)
                color = ImageGetter.getBlue()
            }.surroundWithCircle(size, resizeActor = false, color = Color.LIGHT_GRAY)

            addListener(StaticTooltip(keyImage, tooltipManager(size)))
        }
    }
}

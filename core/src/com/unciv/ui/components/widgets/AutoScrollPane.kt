package com.unciv.ui.components.widgets

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

/*
        ** Problem **
        Standard LibGdx ScrollPane widgets support vertical scrolling by a mouse wheel.
        That works once they have 'Scroll focus' (there's keyboard focus, too) - e.g. once they
        are dragged ot clicked once. However, the user expects the mouse wheel to affect
        a scrollable widget as soon as the mouse points to it.

        ** Approach **
        Listen to enter and exit events and set focus as needed.
        The old focus is saved on enter and restored on exit to make this as side-effect free as possible.

        ** Implementation **
        The listener is attached per widget (and not, say, to an upper container or the screen, where
        one listener would suffice but we'd have to do coordinate to target resolution ourselves).
        This is accomplished by subclassing the ScrollPane and replacing usages,
        which in turn can be done either by using this class as drop-in replacement per widget
        or by importing this using an import alias per file.

        ** Notes **
        This should not be used in cases where the mouse wheel should do something else,
        e.g. zooming. For panes scrolling only horizontally, using this class is redundant.
        To make the signature identical, including parameter names, all constructors have
        been replicated functionally by checking the Gdx sources for which defaults to use.

        ** Commented-out code **
        The FocusLossDetector listener and related lines are intentionally left in.
        See #9612 - "AutoScrollPanes disable scrolling after clicking on them"
        Actually, what happens is due to nested ScrollPanes. In the described case, it is
        PickerPane.scrollPane, disabled in landscape mode, that "steals" the focus.
        That is solved by removing the MouseOverListener when scrolling is disabled.
        But there may be other similar situations - in such cases the FocusLossDetector might be a
        solution, or at least it is valuable as debugging tool - ***who*** are we losing focus to?
 */

open class AutoScrollPane(
    widget: Actor?,
    style: ScrollPaneStyle = ScrollPaneStyle()
) : ScrollPane(widget, style) {
    constructor(widget: Actor?, skin: Skin) : this(widget, skin.get(ScrollPaneStyle::class.java))
    constructor(widget: Actor?, skin: Skin, styleName: String) : this(widget, skin.get(styleName,ScrollPaneStyle::class.java))

    private var savedFocus: Actor? = null
//     private var isInMouseOverListener = false

    /** This listener "grabs" focus on mouse-over */
    private class MouseOverListener : ClickListener() {
        // Note - using listenerActor is a bit more verbose than making this an inner class, but seems cleaner
        override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            val thisScroll = event.listenerActor as AutoScrollPane
            val stage = thisScroll.stage ?: return
            if (fromActor?.isDescendantOf(thisScroll) == true) return
//             thisScroll.isInMouseOverListener = true
            if (thisScroll.savedFocus == null) thisScroll.savedFocus = stage.scrollFocus
            stage.scrollFocus = thisScroll
//             thisScroll.isInMouseOverListener = false
        }
        override fun exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            val thisScroll = event.listenerActor as AutoScrollPane
            val stage = thisScroll.stage ?: return
            if (toActor?.isDescendantOf(thisScroll) == true) return
//             thisScroll.isInMouseOverListener = true
            if (stage.scrollFocus == thisScroll) stage.scrollFocus = thisScroll.savedFocus
            thisScroll.savedFocus = null
//             thisScroll.isInMouseOverListener = false
        }
    }

    /** A listener with the sole task to prevent losing focus un-wanted-ly. */
    /*
        private class FocusLossDetector : FocusListener() {
            override fun scrollFocusChanged(event: FocusEvent, targetActor: Actor, focused: Boolean) {
                // Here, targetActor is the old focus, relatedActor is the new one something wants to set (but hasn't yet)
                // So we only want to react to focus losses to someone else...
                // Note: super is empty, so no need to pass through
                if (focused || targetActor != event.listenerActor)
                    return
                // if (event.listenerActor == event.relatedActor) return  // empirically shown to be redundant
                if ((event.listenerActor as AutoScrollPane).isInMouseOverListener) return
                val mouse = event.stage.screenToStageCoordinates(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
                val hitActor = event.stage.hit(mouse.x, mouse.y, false)
                val hitScroll = hitActor.firstAscendant(ScrollPane::class.java)
                if (hitScroll == event.relatedActor) return
                // if (hitScroll != event.listenerActor) return  // empirically shown to be redundant

                // Now we're sure it is an unwanted focus stealing attempt
                event.cancel()
            }
        }
    */

    init {
        ensureListener()
//         addListener(FocusLossDetector())
    }

    override fun setScrollingDisabled(x: Boolean, y: Boolean) {
        super.setScrollingDisabled(x, y)
        ensureListener()
    }

    private fun ensureListener() {
        val existingListener = listeners.firstOrNull { it is MouseOverListener }
        if (isScrollingDisabledX && isScrollingDisabledY) {
            if (existingListener != null)
                removeListener(existingListener)
        } else {
            if (existingListener == null)
                addListener(MouseOverListener())
        }
    }
}

package com.unciv.ui.utils

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
        The old focus is saved on eneter and restored on exit to make this as side-effect free as possible.

        ** Implementation **
        The listener is attached per widget (and not, say, to an upper container or the screen, where
        one listener would suffice but we'd have to do coordinate to target resolution outselves).
        This is accomplished by subclassing the ScrollPane and replacing usages,
        which in turn can be done either by using this class as drop-in replacement per widget
        or by importing this using an import alias per file.

        ** Notes **
        This should not be used in cases where the mouse wheel should do something else,
        e.g. zooming. For panes scrolling only horizontally, using this class is redundant.
        To make the signature identical, including parameter names, all constructors have
        been replicated functionally by checking the Gdx sources for which defaults to use.
 */

open class AutoScrollPane(widget: Actor?, style: ScrollPaneStyle = ScrollPaneStyle()): ScrollPane(widget,style) {
    constructor(widget: Actor?, skin: Skin) : this(widget,skin.get(ScrollPaneStyle::class.java))
    constructor(widget: Actor?, skin: Skin, styleName: String) : this(widget,skin.get(styleName,ScrollPaneStyle::class.java))

    private var savedFocus: Actor? = null

    init {
        this.addListener (object : ClickListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (stage == null) return
                if (fromActor?.isDescendantOf(this@AutoScrollPane) == true) return
                if (savedFocus == null) savedFocus = stage.scrollFocus
                stage.scrollFocus = this@AutoScrollPane
            }
            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (stage == null) return
                if (toActor?.isDescendantOf(this@AutoScrollPane) == true) return
                if (stage.scrollFocus == this@AutoScrollPane) stage.scrollFocus = savedFocus
                savedFocus = null
            }
        })
    }
}
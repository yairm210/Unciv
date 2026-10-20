package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable

class StatusButtons(
    val nextTurnButton: NextTurnButton
) : Table(), Disposable {
    var autoPlayStatusButton: AutoPlayStatusButton? = null
    var multiplayerStatusButton: MultiplayerStatusButton? = null
    var smallUnitButton: SmallUnitButton? = null
    private val padXSpace = 10f
    private val padYSpace = 5f

    // (verticalWrap, autoPlayStatusButton, multiplayerStatusButton, smallUnitButton) as last laid out.
    // clear()+re-add()ing children (even the same instances) cancels any in-progress touch/gesture
    // on them - e.g. a click on nextTurnButton - so skip the rebuild when nothing actually changed.
    private var lastLayout: List<Any?>? = null

    init {
        add(nextTurnButton)
    }
    
    fun update(verticalWrap: Boolean) {
        val layout = listOf(verticalWrap, autoPlayStatusButton, multiplayerStatusButton, smallUnitButton)
        if (layout == lastLayout) return
        lastLayout = layout

        clear()
        if(verticalWrap) {
            add(nextTurnButton)
            smallUnitButton?.let {
                row()
                add(it).padTop(padYSpace).right()
            }
            autoPlayStatusButton?.let {
                row()
                add(it).padTop(padYSpace).right()
            }
            multiplayerStatusButton?.let {
                row()
                add(it).padTop(padYSpace).right()
            }
        } else {
            multiplayerStatusButton?.let { add(it).padRight(padXSpace).top() }
            autoPlayStatusButton?.let { add(it).padRight(padXSpace).top() }
            smallUnitButton?.let { add(it).padRight(padXSpace).top() }
            add(nextTurnButton)
        }
        pack()
    }

    override fun dispose() {
        autoPlayStatusButton?.dispose()
        multiplayerStatusButton?.dispose()
    }
}

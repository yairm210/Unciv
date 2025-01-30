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
    
    init {
        add(nextTurnButton)
    }
    
    fun update(portrait: Boolean) {
        clear()
        if(portrait) {
            add(nextTurnButton)
            if (smallUnitButton != null) {
                row()
                add(smallUnitButton).padTop(padYSpace).right()
            }
            if (autoPlayStatusButton != null) {
                row()
                add(autoPlayStatusButton).padTop(padYSpace).right()
            }
            if (multiplayerStatusButton != null) {
                row()
                add(multiplayerStatusButton).padTop(padYSpace).right()
            }
        } else {
            if (multiplayerStatusButton != null)
                add(multiplayerStatusButton).padRight(padXSpace).top()
            if (autoPlayStatusButton != null)
                add(autoPlayStatusButton).padRight(padXSpace).top()
            if (smallUnitButton != null)
                add(smallUnitButton).padRight(padXSpace).top()
            add(nextTurnButton)
        }
        pack()
    }

    override fun dispose() {
        autoPlayStatusButton?.dispose()
        multiplayerStatusButton?.dispose()
    }
}

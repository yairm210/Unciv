package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.utils.Disposable

class StatusButtons(
    val nextTurnButton: NextTurnButton
) : HorizontalGroup(), Disposable {
    var autoPlayStatusButton: AutoPlayStatusButton? = null
        set(button) {
            autoPlayStatusButton?.remove()
            field = button
            if (button != null) {
                addActorAt(0, button)
            }
        }
    var multiplayerStatusButton: MultiplayerStatusButton? = null
        set(button) {
            multiplayerStatusButton?.remove()
            field = button
            if (button != null) {
                addActorAt(0, button)
            }
        }
    var unitWaitButton: UnitWaitButton? = null
        set(button) {
            unitWaitButton?.remove()
            field = button
            if (button != null) {
                // insert next to next-turn-button
                addActorAt(children.indexOf(nextTurnButton, true), button)
            }
        }
    
    init {
        space(10f)
        right()
        wrapReverse()
        wrapSpace(10f)
        rowRight()
        if (autoPlayStatusButton != null) {
            addActor(autoPlayStatusButton)
        }
        if (multiplayerStatusButton != null) {
            addActor(multiplayerStatusButton)
        }
        addActor(nextTurnButton)
    }

    override fun dispose() {
        autoPlayStatusButton?.dispose()
        multiplayerStatusButton?.dispose()
    }
}

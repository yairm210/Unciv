package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.utils.Disposable

class StatusButtons(
    nextTurnButton: NextTurnButton,
    autoPlayStatusButton: AutoPlayStatusButton? = null,
    multiplayerStatusButton: MultiplayerStatusButton? = null
) : HorizontalGroup(), Disposable {
    var autoPlayStatusButton: AutoPlayStatusButton? = autoPlayStatusButton
        set(button) {
            autoPlayStatusButton?.remove()
            field = button
            if (button != null) {
                addActorAt(0, button)
            }
        }
    var multiplayerStatusButton: MultiplayerStatusButton? = multiplayerStatusButton
        set(button) {
            multiplayerStatusButton?.remove()
            field = button
            if (button != null) {
                addActorAt(0, button)
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

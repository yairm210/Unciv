package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.utils.Disposable

class StatusButtons(
    nextTurnButton: NextTurnButton,
    multiplayerStatusButton: MultiplayerStatusButton? = null
) : HorizontalGroup(), Disposable {
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
        if (multiplayerStatusButton != null) {
            addActor(multiplayerStatusButton)
        }
        addActor(nextTurnButton)
    }

    override fun dispose() {
        multiplayerStatusButton?.dispose()
    }
}

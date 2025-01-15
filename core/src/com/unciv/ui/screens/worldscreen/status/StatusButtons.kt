package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Container
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
    var smallUnitButton: SmallUnitButton? = null
        set(button) {
            // wait button is wrapped in container, remove that container
            smallUnitButton?.parent?.remove()
            field = button
            if (button != null) {
                // fix uneven spacing applied by HorizontalGroup.wrap()
                val container = Container(button)
                container.padBottom(nextTurnButton.height - button.height)
                // insert next to next-turn-button
                addActorAt(children.indexOf(nextTurnButton, true), container)
            }
        }
    
    init {
        space(10f)
        right()
        wrapReverse()
        wrapSpace(10f)
        rowRight()
        addActor(nextTurnButton)
    }

    override fun dispose() {
        autoPlayStatusButton?.dispose()
        multiplayerStatusButton?.dispose()
    }
}

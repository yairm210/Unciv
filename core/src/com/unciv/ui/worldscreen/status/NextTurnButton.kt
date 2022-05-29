package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.models.translations.tr
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyPressDispatcher
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setFontSize

class NextTurnButton(
    keyPressDispatcher: KeyPressDispatcher
) : TextButton("", BaseScreen.skin) {
    private lateinit var nextTurnAction: NextTurnAction
    init {
        label.setFontSize(30)
        labelCell.pad(10f)
        val action = { nextTurnAction.action() }
        onClick(action)
        keyPressDispatcher[Input.Keys.SPACE] = action
        keyPressDispatcher['n'] = action
    }

    fun update(isSomethingOpen: Boolean,
               isPlayersTurn: Boolean,
               waitingForAutosave: Boolean,
               isNextTurnUpdateRunning: Boolean,
               nextTurnAction: NextTurnAction? = null) {
        if (nextTurnAction != null) {
            this.nextTurnAction = nextTurnAction
            setText(nextTurnAction.text.tr())
            label.color = nextTurnAction.color
            pack()
        }

        isEnabled = !isSomethingOpen && isPlayersTurn && !waitingForAutosave && !isNextTurnUpdateRunning
    }
}

class NextTurnAction(val text: String, val color: Color, val action: () -> Unit)

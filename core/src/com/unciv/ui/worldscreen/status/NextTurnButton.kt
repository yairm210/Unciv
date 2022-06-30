package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.models.translations.tr
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.KeyShortcut
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.setFontSize

class NextTurnButton(
) : TextButton("", BaseScreen.skin) {
    private var nextTurnAction = NextTurnAction("", Color.BLACK) {}

    init {
        label.setFontSize(30)
        labelCell.pad(10f)
        onActivation { nextTurnAction.action() }
        keyShortcuts.add(Input.Keys.SPACE)
        keyShortcuts.add('n')
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyShortcut(KeyCharAndCode('z'), -100))
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

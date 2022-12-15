package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.unciv.models.translations.tr
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.KeyShortcut
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.setSize

class NextTurnButton(
) : IconTextButton("", null, 30) {
    private var nextTurnAction = NextTurnAction("", Color.BLACK) {}

    init {
//         label.setFontSize(30)
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
            label.setText(nextTurnAction.text.tr())
            label.color = nextTurnAction.color
            if (nextTurnAction.icon != null && ImageGetter.imageExists(nextTurnAction.icon))
                iconCell.setActor(ImageGetter.getImage(nextTurnAction.icon).apply { setSize(30f) })
            else
                iconCell.clearActor()
            pack()
        }

        isEnabled = !isSomethingOpen && isPlayersTurn && !waitingForAutosave && !isNextTurnUpdateRunning
    }
}

class NextTurnAction(val text: String, val color: Color, val icon: String? = null, val action: () -> Unit)

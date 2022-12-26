package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.models.translations.tr
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.KeyShortcut
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.setSize

class NextTurnAction(
    val text: String,
    val color: Color,
    val icon: String? = null,
    val action: () -> Unit)

class NextTurnButton : IconTextButton("", null, 30) {

    private var action = NextTurnAction("", Color.BLACK) {}

    init {
        labelCell.pad(10f)
        onActivation { action.action() }
        keyShortcuts.add(Input.Keys.SPACE)
        keyShortcuts.add('n')
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyShortcut(KeyCharAndCode('z'), -100))
    }

    fun updateIcon(icon: Image) {
        iconCell.setActor(icon)
    }

    fun update(enabled: Boolean = true, newAction: NextTurnAction? = null) {
        if (newAction != null) {
            action = newAction
            label.setText(newAction.text.tr())
            label.color = newAction.color
            if (newAction.icon != null && ImageGetter.imageExists(newAction.icon))
                iconCell.setActor(ImageGetter.getImage(action.icon).apply { setSize(30f) })
            else
                iconCell.clearActor()
            pack()
        }
        isEnabled = enabled
    }
}

package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.UncivSound

// If there are other buttons that require special clicks then we'll have an onclick that will accept a string parameter, no worries
fun Actor.onClick(sound: UncivSound = UncivSound.Click, tapCount: Int = 1, tapInterval: Float = 0.0f, function: () -> Unit) {
    onClickEvent(sound, tapCount, tapInterval) { _, _, _ -> function() }
}

/** same as [onClick], but sends the [InputEvent] and coordinates along */
fun Actor.onClickEvent(sound: UncivSound = UncivSound.Click,
                       tapCount: Int = 1,
                       tapInterval: Float = 0.0f,
                       function: (event: InputEvent?, x: Float, y: Float) -> Unit) {
    val previousListener = this.listeners.firstOrNull { it is OnClickListener }
    if (previousListener != null && previousListener is OnClickListener) {
        previousListener.addClickFunction(sound, tapCount, function)
        previousListener.setTapCountInterval(tapInterval)
    } else {
        this.addListener(OnClickListener(sound, function, tapCount, tapInterval))
    }
}

fun Actor.onClick(function: () -> Unit): Actor {
    onClick(UncivSound.Click, 1, 0f, function)
    return this
}

fun Actor.onDoubleClick(sound: UncivSound = UncivSound.Click, tapInterval: Float = 0.25f, function: () -> Unit): Actor {
    onClick(sound, 2, tapInterval, function)
    return this
}

class OnChangeListener(val function: (event: ChangeEvent?) -> Unit): ChangeListener(){
    override fun changed(event: ChangeEvent?, actor: Actor?) {
        function(event)
    }
}

fun Actor.onChange(function: (event: ChangeListener.ChangeEvent?) -> Unit): Actor {
    this.addListener(OnChangeListener(function))
    return this
}

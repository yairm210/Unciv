package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.utils.Concurrency

class OnClickListener(val sound: UncivSound = UncivSound.Click,
                      val function: (event: InputEvent?, x: Float, y: Float) -> Unit,
                      tapCount: Int = 1,
                      tapInterval: Float = 0.0f): ClickListener() {
    class ClickListenerInstance(val sound: UncivSound, val function: (event: InputEvent?, x: Float, y: Float) -> Unit, val tapCount: Int)

    private val clickFunctions = mutableMapOf<Int, ClickListenerInstance>()

    init {
        setTapCountInterval(tapInterval)
        clickFunctions[tapCount] = ClickListenerInstance(sound, function, tapCount)
    }

    fun addClickFunction(sound: UncivSound = UncivSound.Click, tapCount: Int, function: (event: InputEvent?, x: Float, y: Float) -> Unit) {
        clickFunctions[tapCount] = ClickListenerInstance(sound, function, tapCount)
    }

    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        var effectiveTapCount = tapCount
        if (clickFunctions[effectiveTapCount] == null) {
            effectiveTapCount = clickFunctions.keys.filter { it < tapCount }.maxOrNull() ?: return // happens if there's a double (or more) click function but no single click
        }
        val clickInstance = clickFunctions[effectiveTapCount]!!
        Concurrency.runOnGLThread("Sound") { SoundPlayer.play(clickInstance.sound) }
        val func = clickInstance.function
        func(event, x, y)
    }
}

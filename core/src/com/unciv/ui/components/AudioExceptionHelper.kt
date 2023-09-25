package com.unciv.ui.components

import com.badlogic.gdx.audio.Music

interface AudioExceptionHelper {
    fun installHooks(
        updateCallback: (()->Unit)?,
        exceptionHandler: ((Throwable, Music)->Unit)?
    )
}

package com.unciv.ui.utils

import com.badlogic.gdx.audio.Music

interface AudioExceptionHelper {
    fun installHooks(
        updateCallback: (()->Unit)?,
        exceptionHandler: ((Throwable, Music)->Unit)?
    )
}

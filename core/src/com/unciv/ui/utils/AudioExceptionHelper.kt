package com.unciv.ui.utils

import com.badlogic.gdx.audio.Music

interface AudioExceptionHelper {
    fun installHooks(exceptionHandler: ((Throwable, Music)->Unit)?)
}

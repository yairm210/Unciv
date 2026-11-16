package com.unciv.ui.filedrop

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame

object FileDrop {
    private fun target() = UncivGame.Current.screen as? FileDropTarget

    fun accepts(name: String) = target()?.acceptsDroppedFile(name) == true

    fun dispatch(files: List<DroppedFile>) = Gdx.app.postRunnable {
        val target = target() ?: return@postRunnable
        if (files.isNotEmpty())
            target.onFilesDropped(files)
    }
}

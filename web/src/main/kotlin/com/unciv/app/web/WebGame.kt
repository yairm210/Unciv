package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame

class WebGame : UncivGame() {
    override fun installAudioHooks() {
        // Phase-1: no platform-specific audio hook wiring required.
    }

    override fun notifyTurnStarted() {
        // No system turn notifications on web phase-1.
    }

    override fun dispose() {
        // Web lifecycle can tear down abruptly; avoid JVM-only shutdown paths.
        Gdx.input.inputProcessor = null
        try {
            musicController.pause(onShutdown = true)
        } catch (_: UninitializedPropertyAccessException) {
        }
        try {
            settings.save()
        } catch (_: UninitializedPropertyAccessException) {
        }
    }
}

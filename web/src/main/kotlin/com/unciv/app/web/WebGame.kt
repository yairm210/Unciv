package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame

class WebGame : UncivGame() {
    override fun create() {
        super.create()
        enforceWebInputDefaults()
        val startedJsTests = WebJsTestRunner.maybeStart()
        val startedMpProbe = if (!startedJsTests) WebMultiplayerProbeRunner.maybeStart(this) else false
        if (!startedJsTests && !startedMpProbe) {
            WebValidationRunner.maybeStart(this)
        }
    }

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

    private fun enforceWebInputDefaults() {
        var changed = false
        if (!settings.singleTapMove) {
            settings.singleTapMove = true
            changed = true
        }
        if (changed) settings.save()
    }
}

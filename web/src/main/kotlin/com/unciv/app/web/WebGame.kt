package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings.ScreenSize

class WebGame : UncivGame() {
    override fun create() {
        WebValidationInterop.publishRunnerSelection("none", "bootstrap-start")
        WebValidationInterop.appendBootstrapTrace("create:start", "WebGame.create entered")
        val jsRequested = WebJsTestInterop.isEnabled()
        val uiRequested = WebUiProbeInterop.isEnabled()
        val mpRequested = WebMultiplayerProbeInterop.isEnabled()
        val clickOpsRequested = WebClickOpsInterop.isEnabled()
        WebValidationInterop.appendBootstrapTrace(
            "create:request-flags",
            "jsRequested=$jsRequested uiRequested=$uiRequested mpRequested=$mpRequested clickOpsRequested=$clickOpsRequested",
        )

        super.create()
        applyWebUxDefaults()
        WebValidationInterop.appendBootstrapTrace("create:super", "super.create completed")

        if (clickOpsRequested) {
            WebValidationInterop.publishRunnerSelection("clickOps", "clickOps query flag enabled")
            val startedClickOps = WebClickOpsCollector.maybeStart(this)
            WebValidationInterop.appendBootstrapTrace("create:clickops", "started=$startedClickOps")
            if (!startedClickOps) {
                WebValidationInterop.publishRunnerSelection("none", "clickOps requested but collector did not start")
                WebClickOpsInterop.publishError("clickOps was requested but startup chain did not launch WebClickOpsCollector.")
            }
            return
        }

        if (uiRequested) {
            WebValidationInterop.publishRunnerSelection("uiProbe", "uiProbe query flag enabled")
            val startedUiProbe = WebUiProbeRunner.maybeStart(this)
            WebValidationInterop.appendBootstrapTrace("create:uiprobe", "started=$startedUiProbe")
            if (!startedUiProbe) {
                WebValidationInterop.publishRunnerSelection("none", "uiProbe requested but runner did not start")
                WebUiProbeInterop.publishError("uiProbe was requested but startup chain did not launch WebUiProbeRunner.")
            }
            return
        }

        if (jsRequested) {
            WebValidationInterop.publishRunnerSelection("jstests", "jstests query flag enabled")
            val startedJsTests = WebJsTestRunner.maybeStart()
            WebValidationInterop.appendBootstrapTrace("create:jstests", "started=$startedJsTests")
            if (!startedJsTests) {
                WebValidationInterop.publishRunnerSelection("none", "jstests requested but runner did not start")
            }
            return
        }

        if (mpRequested) {
            WebValidationInterop.publishRunnerSelection("mpProbe", "mpProbe query flag enabled")
            val startedMpProbe = WebMultiplayerProbeRunner.maybeStart(this)
            WebValidationInterop.appendBootstrapTrace("create:mpprobe", "started=$startedMpProbe")
            if (!startedMpProbe) {
                WebValidationInterop.publishRunnerSelection("none", "mpProbe requested but runner did not start")
                WebMultiplayerProbeInterop.publishError("mpProbe was requested but startup chain did not launch WebMultiplayerProbeRunner.")
            }
            return
        }

        WebValidationInterop.publishRunnerSelection("validation", "default validation path")
        val startedValidation = WebValidationRunner.maybeStart(this)
        WebValidationInterop.appendBootstrapTrace("create:validation", "started=$startedValidation")
        if (!startedValidation) {
            WebValidationInterop.publishRunnerSelection("none", "validation path selected but runner did not start")
        }
    }

    override fun render() {
        super.render()
        WebClickOpsCollector.captureFrame(this)
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

    private fun applyWebUxDefaults() {
        val isMobile = WebRuntimeInterop.isLikelyMobileDevice()
        settings.webRuntimeMobile = isMobile
        WebRuntimeInterop.applyDeviceClass(isMobile)

        val targetVersion = 2
        if (settings.webUxProfileVersion >= targetVersion) return

        var changed = false

        if (isMobile) {
            if (!settings.singleTapMove) {
                settings.singleTapMove = true
                changed = true
            }
        } else {
            if (settings.screenSize == ScreenSize.Small) {
                settings.screenSize = ScreenSize.Medium
                changed = true
            }
            if (settings.singleTapMove) {
                settings.singleTapMove = false
                changed = true
            }
        }

        if (settings.webUxProfileVersion != targetVersion) {
            settings.webUxProfileVersion = targetVersion
            changed = true
        }
        if (changed) {
            settings.save()
        }
    }
}

package com.unciv.app.web

import com.badlogic.gdx.ApplicationAdapter
import com.unciv.platform.PlatformCapabilities
import com.unciv.utils.Log

/**
 * Lightweight application listener for browser JS test mode.
 * Avoids full Unciv startup paths while keeping a valid GL/app context.
 */
class WebJsTestsGame : ApplicationAdapter() {
    override fun create() {
        PlatformCapabilities.setCurrent(PlatformCapabilities.webPhase1())
        Log.backend = WebLogBackend()
        WebJsTestRunner.maybeStart()
    }
}

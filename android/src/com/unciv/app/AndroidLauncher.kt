package com.unciv.app

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }
        val game = UncivGame(BuildConfig.VERSION_NAME,
                            CrashReportSenderAndroid(this))
                            {this.finish()}
        initialize(game, config)
    }
}
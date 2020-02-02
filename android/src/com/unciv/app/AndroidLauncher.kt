package com.unciv.app

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame
import java.io.File

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mod directory in the internal app data (where Gdx.files.local looks)
        val internalModsDir = File("${filesDir.path}/mods")

        // Mod directory in the shared app data (where the user can see and modify)
        val externalModsDir = File("${getExternalFilesDir(null)?.path}/mods")

        // Empty out the mods directory so it can be replaced by the external one
        // Done to ensure it only contains mods in the external dir (so users can delete some)
        if (internalModsDir.exists()) internalModsDir.deleteRecursively()

        // Copy external mod directory (with data user put in it) to internal (where it can be read)
        if (!externalModsDir.exists()) externalModsDir.mkdirs()
        externalModsDir.copyRecursively(internalModsDir)

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }
        val game = UncivGame(BuildConfig.VERSION_NAME,
                            CrashReportSenderAndroid(this))
                            {this.finish()}
        initialize(game, config)
    }
}
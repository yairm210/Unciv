package com.unciv.app

import android.os.Build
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame
import java.io.File

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

		// Only allow mods on KK+, to avoid READ_EXTERNAL_STORAGE permission earlier versions need
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			copyMods()
		}

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }
        val game = UncivGame(BuildConfig.VERSION_NAME,
                            CrashReportSenderAndroid(this))
                            {this.finish()}
        initialize(game, config)
    }

	/**
	 * Copies mods from external data directory (where users can access) to the private one (where
	 * libGDX reads from). Note: deletes all files currently in the private mod directory and
	 * replaces them with the ones in the external folder!)
	 */
	private fun copyMods() {
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
	}
}
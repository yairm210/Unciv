package com.unciv.app

import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.GameSaver
import java.io.File

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiplayerTurnCheckWorker.createNotificationChannels(applicationContext)

		// Only allow mods on KK+, to avoid READ_EXTERNAL_STORAGE permission earlier versions need
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			copyMods()
            val externalfilesDir = getExternalFilesDir(null)
            if(externalfilesDir!=null) GameSaver.externalFilesDirForAndroid = externalfilesDir.path
		}

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }
        val androidParameters = UncivGameParameters(
                version = BuildConfig.VERSION_NAME,
                crashReportSender = CrashReportSenderAndroid(this),
                exitEvent = this::finish,
                fontImplementation = NativeFontAndroid(45)
        )
        val game = UncivGame ( androidParameters )
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
		if (!externalModsDir.exists()) externalModsDir.mkdirs() // this can fail sometimes, which is why we check if it exists again in the next line
        if (externalModsDir.exists()) externalModsDir.copyRecursively(internalModsDir)
	}

    override fun onPause() {
        if (UncivGame.Companion.isCurrentInitialized()
                && UncivGame.Current.isGameInfoInitialized()
                && UncivGame.Current.settings.multiplayerTurnCheckerEnabled
                && UncivGame.Current.gameInfo.gameParameters.isOnlineMultiplayer) {
            MultiplayerTurnCheckWorker.startTurnChecker(applicationContext, UncivGame.Current.gameInfo, UncivGame.Current.settings)
        }
        super.onPause()
    }

    override fun onResume() {
        try { // Sometimes this fails for no apparent reason - the multiplayer checker failing to cancel should not be enough of a reason for the game to crash!
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(MultiplayerTurnCheckWorker.WORK_TAG)
            with(NotificationManagerCompat.from(this)) {
                cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_INFO)
                cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_SERVICE)
            }
        }
        catch (ex:Exception){}
        super.onResume()
    }
}
package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame
import java.io.File

class AndroidLauncher : AndroidApplication() {
    private lateinit var importExport: ImportExportAndroid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MultiplayerTurnCheckWorker.createNotificationChannels(applicationContext)

		// Only allow mods on KK+, to avoid READ_EXTERNAL_STORAGE permission earlier versions need
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			copyMods()
		}

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }

        importExport = ImportExportAndroid(this)

        val game = UncivGame (
                version = BuildConfig.VERSION_NAME,
                crashReportSender = CrashReportSenderAndroid(this),
                exitEvent = this::finish,
                importExport = importExport
            )
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
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag(MultiplayerTurnCheckWorker.WORK_TAG)
        with(NotificationManagerCompat.from(this)) {
            cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_INFO)
            cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_SERVICE)
        }
        super.onResume()
    }

    override fun onActivityResult( requestCode: Int, resultCode: Int, resultData: Intent? ) {
        resultData?.data?.also { uri -> importExport.activityResult(resultCode, requestCode, uri) }
    }
}
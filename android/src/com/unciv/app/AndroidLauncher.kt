package com.unciv.app

import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.app.turncheck.Common
import com.unciv.app.turncheck.WorkerV1
import com.unciv.app.turncheck.WorkerV2
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.ui.components.Fonts
import com.unciv.utils.Display
import com.unciv.utils.Log
import java.io.File

open class AndroidLauncher : AndroidApplication() {

    private var game: AndroidGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup Android logging
        Log.backend = AndroidLogBackend()

        // Setup Android display
        Display.platform = AndroidDisplay(this)

        // Setup Android fonts
        Fonts.fontImplementation = AndroidFont()

        // Setup Android custom saver-loader
        UncivFiles.saverLoader = AndroidSaverLoader(this)
        UncivFiles.preferExternalStorage = true

        // Create notification channels for Multiplayer notificator
        Common.createNotificationChannels(applicationContext)

        copyMods()

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true }
        val settings = UncivFiles.getSettingsForPlatformLaunchers(filesDir.path)

        // Setup orientation and display cutout
        Display.setOrientation(settings.displayOrientation)
        Display.setCutout(settings.androidCutout)

        game = AndroidGame(this)
        initialize(game, config)

        game!!.setDeepLinkedGame(intent)
        game!!.addScreenObscuredListener()
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

        // Copy external mod directory (with data user put in it) to internal (where it can be read)
        if (!externalModsDir.exists()) externalModsDir.mkdirs() // this can fail sometimes, which is why we check if it exists again in the next line
        if (externalModsDir.exists()) externalModsDir.copyRecursively(internalModsDir, true)
    }

    override fun onPause() {
        val game = this.game!!
        if (game.isInitialized) {
            if (game.onlineMultiplayer.isInitialized() && game.onlineMultiplayer.apiVersion == ApiVersion.APIv2) {
                try {
                    WorkerV2.start(applicationContext, game.files, game.gameInfo, game.onlineMultiplayer, game.settings.multiplayer)
                } catch (e: Exception) {
                    android.util.Log.e(Common.LOG_TAG, "Error during WorkverV2.start of $this: $e\nMessage: ${e.localizedMessage}\n${e.stackTraceToString()}")
                }
            } else if (game.gameInfo != null && game.settings.multiplayer.turnCheckerEnabled && game.files.getMultiplayerSaves().any()) {
                WorkerV1.startTurnChecker(applicationContext, game.files, game.gameInfo!!, game.settings.multiplayer)
            }
        }
        if (game.onlineMultiplayer.isInitialized() && game.onlineMultiplayer.apiVersion == ApiVersion.APIv2) {
            game.onlineMultiplayer.api.disableReconnecting()
        }
        super.onPause()
    }

    override fun onResume() {
        if (game?.onlineMultiplayer?.isInitialized() == true && game?.onlineMultiplayer?.apiVersion == ApiVersion.APIv2) {
            game?.onlineMultiplayer?.api?.enableReconnecting()
        }
        try {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(Common.WORK_TAG)
            with(NotificationManagerCompat.from(this)) {
                cancel(Common.NOTIFICATION_ID_INFO)
                cancel(Common.NOTIFICATION_ID_SERVICE)
            }
        } catch (ignore: Exception) {
            /* Sometimes this fails for no apparent reason - the multiplayer checker failing to
               cancel should not be enough of a reason for the game to crash! */
        }
        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null)
            return
        game?.setDeepLinkedGame(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val saverLoader = UncivFiles.saverLoader as AndroidSaverLoader
        saverLoader.onActivityResult(requestCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}

class AndroidTvLauncher:AndroidLauncher()

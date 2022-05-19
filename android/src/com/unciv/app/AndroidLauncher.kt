package com.unciv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.GameSaver
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.utils.Fonts
import java.io.File

open class AndroidLauncher : AndroidApplication() {
    private var customSaveLocationHelper: CustomSaveLocationHelperAndroid? = null
    private var game: UncivGame? = null
    private var deepLinkedMultiplayerGame: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customSaveLocationHelper = CustomSaveLocationHelperAndroid(this)
        MultiplayerTurnCheckWorker.createNotificationChannels(applicationContext)

        copyMods()
        val externalFilesDir = getExternalFilesDir(null)
        if (externalFilesDir != null) GameSaver.externalFilesDirForAndroid = externalFilesDir.path

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }

        val settings = GameSettings.getSettingsForPlatformLaunchers(filesDir.path)
        val fontFamily = settings.fontFamily

        // Manage orientation lock
        val platformSpecificHelper = PlatformSpecificHelpersAndroid(this)
        platformSpecificHelper.allowPortrait(settings.allowAndroidPortrait)

        val androidParameters = UncivGameParameters(
            version = BuildConfig.VERSION_NAME,
            crashReportSysInfo = CrashReportSysInfoAndroid,
            fontImplementation = NativeFontAndroid(Fonts.ORIGINAL_FONT_SIZE.toInt(), fontFamily),
            customSaveLocationHelper = customSaveLocationHelper,
            platformSpecificHelper = platformSpecificHelper
        )

        game = UncivGame(androidParameters)
        initialize(game, config)

        setDeepLinkedGame(intent)
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
        if (UncivGame.isCurrentInitialized()
                && UncivGame.Current.isGameInfoInitialized()
                && UncivGame.Current.settings.multiplayerTurnCheckerEnabled
                && GameSaver.getSaves(true).any()) {
            MultiplayerTurnCheckWorker.startTurnChecker(applicationContext, GameSaver, UncivGame.Current.gameInfo, UncivGame.Current.settings)
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
        } catch (ex: Exception) {
        }

        if (deepLinkedMultiplayerGame != null) {
            game?.deepLinkedMultiplayerGame = deepLinkedMultiplayerGame
            deepLinkedMultiplayerGame = null
        }

        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null)
            return

        setDeepLinkedGame(intent)
    }

    private fun setDeepLinkedGame(intent: Intent) {
        // This is needed in onCreate _and_ onNewIntent to open links and notifications
        // correctly even if the app was not running
        deepLinkedMultiplayerGame = if (intent.action != Intent.ACTION_VIEW) null else {
            val uri: Uri? = intent.data
            uri?.getQueryParameter("id")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        customSaveLocationHelper?.handleIntentData(requestCode, data?.data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}

class AndroidTvLauncher:AndroidLauncher()

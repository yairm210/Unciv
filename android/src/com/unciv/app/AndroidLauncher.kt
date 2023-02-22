package com.unciv.app

import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.badlogic.gdx.math.Rectangle
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.event.EventBus
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import java.io.File

open class AndroidLauncher : AndroidApplication() {
    private var customFileLocationHelper: CustomFileLocationHelperAndroid? = null
    private var game: UncivGame? = null
    private var deepLinkedMultiplayerGame: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.backend = AndroidLogBackend()
        customFileLocationHelper = CustomFileLocationHelperAndroid(this)
        MultiplayerTurnCheckWorker.createNotificationChannels(applicationContext)

        copyMods()

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }

        val settings = UncivFiles.getSettingsForPlatformLaunchers(filesDir.path)

        // Manage orientation lock and display cutout
        val platformSpecificHelper = PlatformSpecificHelpersAndroid(this)
        platformSpecificHelper.allowPortrait(settings.allowAndroidPortrait)

        platformSpecificHelper.toggleDisplayCutout(settings.androidCutout)

        val androidParameters = UncivGameParameters(
            crashReportSysInfo = CrashReportSysInfoAndroid,
            fontImplementation = FontAndroid(),
            customFileLocationHelper = customFileLocationHelper,
            platformSpecificHelper = platformSpecificHelper
        )

        game = UncivGame(androidParameters)
        initialize(game, config)

        setDeepLinkedGame(intent)

        val glView = (Gdx.graphics as AndroidGraphics).view as GLSurfaceView

        addScreenObscuredListener(glView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            addScreenRefreshRateListener(glView)
    }

    /** Request the best available device frame rate for
     *  the game, as soon as OpenGL surface is created */
    private fun addScreenRefreshRateListener(surfaceView: GLSurfaceView) {
        surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val modes = display?.supportedModes ?: return
                    val bestRefreshRate = modes.maxOf { it.refreshRate }
                    holder.surface.setFrameRate(bestRefreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val display = windowManager.defaultDisplay
                    val modes = display?.supportedModes ?: return
                    val bestMode =  modes.maxBy { it.refreshRate }
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    private fun addScreenObscuredListener(surfaceView: GLSurfaceView) {
        surfaceView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            /** [onGlobalLayout] gets triggered not only when the [windowVisibleDisplayFrame][View.getWindowVisibleDisplayFrame] changes, but also on other things.
             * So we need to check if that was actually the thing that changed. */
            private var lastVisibleDisplayFrame: Rect? = null

            override fun onGlobalLayout() {
                if (!UncivGame.isCurrentInitialized() || UncivGame.Current.screen == null) {
                    return
                }
                val r = Rect()
                surfaceView.getWindowVisibleDisplayFrame(r)
                if (r.equals(lastVisibleDisplayFrame)) return
                lastVisibleDisplayFrame = r

                val stage = (UncivGame.Current.screen as BaseScreen).stage

                val horizontalRatio = stage.width / surfaceView.width
                val verticalRatio = stage.height / surfaceView.height

                val visibleStage = Rectangle(
                    r.left * horizontalRatio,
                    (surfaceView.height - r.bottom)  * verticalRatio, // Android coordinate system has the origin in the top left, while GDX uses bottom left
                    r.width() * horizontalRatio,
                    r.height() * verticalRatio
                )
                Concurrency.runOnGLThread {
                    EventBus.send(UncivStage.VisibleAreaChanged(visibleStage))
                }
            }
        })
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
                && UncivGame.Current.gameInfo != null
                && UncivGame.Current.settings.multiplayer.turnCheckerEnabled
                && UncivGame.Current.files.getMultiplayerSaves().any()
        ) {
            MultiplayerTurnCheckWorker.startTurnChecker(
                applicationContext, UncivGame.Current.files,
                UncivGame.Current.gameInfo!!, UncivGame.Current.settings.multiplayer
            )
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
        customFileLocationHelper?.onActivityResult(requestCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}

class AndroidTvLauncher:AndroidLauncher()

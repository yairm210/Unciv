package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.formatShort
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import java.time.Duration
import java.time.Instant

object MultiplayerHelpers {

    fun loadMultiplayerGame(screen: BaseScreen, selectedGame: OnlineMultiplayerGame) {
        val loadingGamePopup = Popup(screen)
        loadingGamePopup.addGoodSizedLabel("Loading latest game state...")
        loadingGamePopup.open()

        Concurrency.run("JoinMultiplayerGame") {
            try {
                UncivGame.Current.onlineMultiplayer.loadGame(selectedGame)
            } catch (ex: Exception) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                launchOnGLThread {
                    loadingGamePopup.reuseWith(message, true)
                }
            }
        }
    }

    fun buildDescriptionText(multiplayerGame: OnlineMultiplayerGame): StringBuilder {
        val descriptionText = StringBuilder()
        val ex = multiplayerGame.error
        if (ex != null) {
            val (message) = LoadGameScreen.getLoadExceptionMessage(ex, "Error while refreshing:")
            descriptionText.appendLine(message)
        }
        val lastUpdate = multiplayerGame.getLastUpdate()
        descriptionText.appendLine("Last refresh: [${Duration.between(lastUpdate, Instant.now()).formatShort()}] ago".tr())
        val preview = multiplayerGame.preview
        if (preview?.currentPlayer != null) {
            val currentTurnStartTime = Instant.ofEpochMilli(preview.currentTurnStartTime)
            descriptionText.appendLine("Current Turn: [${preview.currentPlayer}] since [${Duration.between(currentTurnStartTime, Instant.now()).formatShort()}] ago".tr())
        }
        return descriptionText
    }

    fun showDropboxWarning(screen: BaseScreen) {
        if (!OnlineMultiplayer.usesDropbox() || UncivGame.Current.settings.multiplayer.hideDropboxWarning) return

        val dropboxWarning = Popup(screen)
        dropboxWarning.addGoodSizedLabel(
            "You're currently using the default multiplayer server, which is based on a free Dropbox account. " +
            "Because a lot of people use this, it is uncertain if you'll actually be able to access it consistently. " +
            "Consider using a custom server instead."
        ).colspan(2).row()
        dropboxWarning.addButton("Open Documentation") {
            Gdx.net.openURI("${Constants.wikiURL}Other/Multiplayer/#hosting-a-multiplayer-server")
        }.colspan(2).row()

        val checkBox = "Don't show again".toCheckBox()
        dropboxWarning.add(checkBox)
        dropboxWarning.addCloseButton {
            UncivGame.Current.settings.multiplayer.hideDropboxWarning = checkBox.isChecked
        }
        dropboxWarning.open()
    }
}

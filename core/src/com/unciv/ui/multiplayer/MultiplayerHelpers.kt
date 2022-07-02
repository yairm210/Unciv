package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.formatShort
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
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
        val lastUpdate = multiplayerGame.lastUpdate
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
            Gdx.net.openURI("https://yairm210.github.io/Unciv/Other/Multiplayer/#hosting-a-multiplayer-server")
        }.colspan(2).row()

        val checkBox = "Don't show again".toCheckBox()
        dropboxWarning.add(checkBox)
        dropboxWarning.addCloseButton {
            UncivGame.Current.settings.multiplayer.hideDropboxWarning = checkBox.isChecked
        }
        dropboxWarning.open()
    }
}

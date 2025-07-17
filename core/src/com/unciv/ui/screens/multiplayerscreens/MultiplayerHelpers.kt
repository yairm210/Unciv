package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.MultiplayerGamePreview
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.formatShort
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import java.time.Duration
import java.time.Instant

object MultiplayerHelpers {

    fun loadMultiplayerGame(screen: BaseScreen, selectedGame: MultiplayerGamePreview) {
        val loadingGamePopup = Popup(screen)
        loadingGamePopup.addGoodSizedLabel("Loading latest game state...")
        loadingGamePopup.open()

        Concurrency.run("JoinMultiplayerGame") {
            try {
                UncivGame.Current.onlineMultiplayer.downloadGame(selectedGame)
            } catch (ex: Exception) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                launchOnGLThread {
                    loadingGamePopup.reuseWith(message, true)
                }
            }
        }
    }

    fun buildDescriptionText(multiplayerGamePreview: MultiplayerGamePreview): String {
        val descriptionText = StringBuilder()
        val ex = multiplayerGamePreview.error
        if (ex != null) {
            val (message) = LoadGameScreen.getLoadExceptionMessage(ex, "Error while refreshing:")
            descriptionText.appendLine(message)
        }
        val lastUpdate = multiplayerGamePreview.getLastUpdate()
        descriptionText.appendLine("Last refresh: [${Duration.between(lastUpdate, Instant.now()).formatShort()}] ago".tr())
        val preview = multiplayerGamePreview.preview
        if (preview?.currentPlayer != null) {
            val currentTurnStartTime = Instant.ofEpochMilli(preview.currentTurnStartTime)
            val currentPlayer = preview.getCurrentPlayerCiv()
            val playerDescriptor = if (currentPlayer.playerId == UncivGame.Current.settings.multiplayer.getUserId()) {
                "You"
            } else {
                val friend = UncivGame.Current.settings.multiplayer.friendList
                    .firstOrNull{ it.playerID == currentPlayer.playerId }
                friend?.name ?: "Unknown"
            }
            val playerText = "{${preview.currentPlayer}}{ }({$playerDescriptor})"

            descriptionText.appendLine("Current Turn: [$playerText] since [${Duration.between(currentTurnStartTime, Instant.now()).formatShort()}] ago".tr())

            val playerCivName = preview.civilizations
                .firstOrNull{ it.playerId == UncivGame.Current.settings.multiplayer.getUserId() }?.civName ?: "Unknown"

            descriptionText.appendLine("{$playerCivName}, ${preview.difficulty.tr()}, ${Fonts.turn}${preview.turns}")
            descriptionText.appendLine("{Base ruleset:} ${preview.gameParameters.baseRuleset}")
            if (preview.gameParameters.mods.isNotEmpty())
                descriptionText.appendLine("{Mods:} " + preview.gameParameters.mods.joinToString())

        }
        return descriptionText.toString().tr()
    }

    fun showDropboxWarning(screen: BaseScreen) {
        if (!Multiplayer.usesDropbox() || UncivGame.Current.settings.multiplayer.hideDropboxWarning) return

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

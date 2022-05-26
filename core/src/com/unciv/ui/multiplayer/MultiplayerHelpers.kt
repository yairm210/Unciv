package com.unciv.ui.multiplayer

import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant

object MultiplayerHelpers {
    fun getLoadExceptionMessage(ex: Throwable) = when (ex) {
        is FileStorageRateLimitReached -> "Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds"
        is FileNotFoundException -> "File could not be found on the multiplayer server"
        is UncivShowableException -> ex.message!! // some of these seem to be translated already, but not all
        else -> "Unhandled problem, [${ex::class.simpleName}] ${ex.message}"
    }

    fun loadMultiplayerGame(screen: BaseScreen, selectedGame: OnlineMultiplayerGame) {
        val loadingGamePopup = Popup(screen)
        loadingGamePopup.addGoodSizedLabel("Loading latest game state...")
        loadingGamePopup.open()

        launchCrashHandling("JoinMultiplayerGame") {
            try {
                UncivGame.Current.onlineMultiplayer.loadGame(selectedGame)
            } catch (ex: Exception) {
                val message = getLoadExceptionMessage(ex)
                postCrashHandlingRunnable {
                    loadingGamePopup.reuseWith(message, true)
                }
            }
        }
    }

    fun buildDescriptionText(multiplayerGame: OnlineMultiplayerGame): StringBuilder {
        val descriptionText = StringBuilder()
        val ex = multiplayerGame.error
        if (ex != null) {
            descriptionText.append("Error while refreshing:".tr()).append(' ')
            val message = getLoadExceptionMessage(ex)
            descriptionText.appendLine(message.tr())
        }
        val lastUpdate = multiplayerGame.lastUpdate
        descriptionText.appendLine("Last refresh: ${formattedElapsedTime(lastUpdate)} ago".tr())
        val preview = multiplayerGame.preview
        if (preview?.currentPlayer != null) {
            val currentTurnStartTime = Instant.ofEpochMilli(preview.currentTurnStartTime)
            descriptionText.appendLine("Current Turn: [${preview.currentPlayer}] since ${formattedElapsedTime(currentTurnStartTime)} ago".tr())
        }
        return descriptionText
    }

    private fun formattedElapsedTime(lastUpdate: Instant): String {
        val durationToNow = Duration.between(lastUpdate, Instant.now())
        val elapsedMinutes = durationToNow.toMinutes()
        if (elapsedMinutes < 120) return "[$elapsedMinutes] [Minutes]"
        val elapsedHours = durationToNow.toHours()
        if (elapsedHours < 48) {
            return "[${elapsedHours}] [Hours]"
        } else {
            return "[${durationToNow.toDays()}] [Days]"
        }
    }
}

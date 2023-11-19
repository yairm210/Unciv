package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.UncivGame

class DesktopGame(config: Lwjgl3ApplicationConfiguration) : UncivGame() {

    private val audio = HardenGdxAudio()
    private var discordUpdater = DiscordUpdater()
    private val windowListener = UncivWindowListener()

    init {
        config.setWindowListener(windowListener)

        discordUpdater.setOnUpdate {

            if (!isInitialized)
                return@setOnUpdate null

            val info = DiscordGameInfo()
            val game = gameInfo

            if (game != null) {
                info.gameTurn = game.turns
                info.gameLeader = game.getCurrentPlayerCivilization().nation.leaderName
                info.gameNation = game.getCurrentPlayerCivilization().nation.name
            }

            return@setOnUpdate info

        }

        discordUpdater.startUpdates()
    }

    override fun installAudioHooks() {
        audio.installHooks(
            musicController.getAudioLoopCallback(),
            musicController.getAudioExceptionHandler()
        )
    }

    override fun notifyTurnStarted() {
        windowListener.turnStarted()
    }

    override fun dispose() {
        discordUpdater.stopUpdates()
        super.dispose()
    }

}

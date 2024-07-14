package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.RulesetValidator
import kotlin.system.exitProcess

class DesktopGame(config: Lwjgl3ApplicationConfiguration) : UncivGame() {

    private var discordUpdater = DiscordUpdater()
    private val windowListener = UncivWindowListener()
    var isModCi = false

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

    override fun create() {
        // The uniques checker requires the file system to be seet up, which happens after lwjgw initializes it
        if (isModCi) {
            ImagePacker.packImagesPerMod(".", ".")
            val ruleset = Ruleset()
            ruleset.folderLocation = Gdx.files.local("jsons")
            ruleset.load(ruleset.folderLocation!!)
            val errors = RulesetValidator(ruleset).getErrorList(true)
            println(errors.getErrorText(true))
            exitProcess(if (errors.any { it.errorSeverityToReport == RulesetErrorSeverity.Error }) 1 else 0)
        }

        super.create()
    }

    override fun installAudioHooks() {
        (Gdx.app as HardenGdxAudio).installHooks(
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

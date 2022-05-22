package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.MissingModsException
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.pickerscreens.Github
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*
import java.util.concurrent.CancellationException

class LoadGameScreen(previousScreen:BaseScreen) : LoadOrSaveScreen() {
    private val copySavedGameToClipboardButton = getCopyExistingSaveToClipboardButton()
    private val errorLabel = "".toLabel(Color.RED).apply { isVisible = false }
    private val loadMissingModsButton = getLoadMissingModsButton()
    private var missingModsToLoad = ""

    companion object {
        private const val loadGame = "Load game"
        private const val loadFromCustomLocation = "Load from custom location"
        private const val loadFromClipboard = "Load copied data"
        private const val copyExistingSaveToClipboard = "Copy saved game to clipboard"
        private const val downloadMissingMods = "Download missing mods"
    }

    init {
        setDefaultCloseAction(previousScreen)
        rightSideTable.initRightSideTable()
        rightSideButton.onClick(::onLoadGame)
        keyPressDispatcher[KeyCharAndCode.RETURN] = ::onLoadGame
    }

    override fun resetWindowState() {
        super.resetWindowState()
        copySavedGameToClipboardButton.disable()
        rightSideButton.setText(loadGame.tr())
        rightSideButton.disable()
    }

    override fun onExistingSaveSelected(saveGameFile: FileHandle) {
        copySavedGameToClipboardButton.enable()
        rightSideButton.setText("Load [$selectedSave]".tr())
        rightSideButton.enable()
    }

    private fun Table.initRightSideTable() {
        add(getLoadFromClipboardButton()).row()
        addLoadFromCustomLocationButton()
        add(errorLabel).row()
        add(loadMissingModsButton).row()
        add(deleteSaveButton).row()
        add(copySavedGameToClipboardButton).row()
        add(showAutosavesCheckbox).row()
    }

    private fun onLoadGame() {
        if (selectedSave.isEmpty()) return
        val loadingPopup = Popup( this)
        loadingPopup.addGoodSizedLabel(Constants.loading)
        loadingPopup.open()
        launchCrashHandling(loadGame) {
            try {
                // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                val loadedGame = game.gameSaver.loadGameByName(selectedSave)
                postCrashHandlingRunnable { game.loadGame(loadedGame) }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    loadingPopup.close()
                    val cantLoadGamePopup = Popup(this@LoadGameScreen)
                    cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                    if (ex is UncivShowableException) {
                        // thrown exceptions are our own tests and can be shown to the user
                        cantLoadGamePopup.addGoodSizedLabel(ex.message).row()
                    } else {
                        cantLoadGamePopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                        cantLoadGamePopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                        cantLoadGamePopup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
                        ex.printStackTrace()
                    }
                    cantLoadGamePopup.addCloseButton()
                    cantLoadGamePopup.open()
                }
            }
        }
    }

    private fun getLoadFromClipboardButton(): TextButton {
        val button = loadFromClipboard.toTextButton()
        button.onClick {
            launchCrashHandling(loadFromClipboard) {
                try {
                    val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                    val loadedGame = GameSaver.gameInfoFromString(clipboardContentsString)
                    postCrashHandlingRunnable { game.loadGame(loadedGame) }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable { handleLoadGameException("Could not load game from clipboard!", ex) }
                }
            }
        }
        return button
    }

    private fun Table.addLoadFromCustomLocationButton() {
        if (!game.gameSaver.canLoadFromCustomSaveLocation()) return
        val button = loadFromCustomLocation.toTextButton()
        button.onClick {
            game.gameSaver.loadGameFromCustomLocation { loadedGame, exception ->
                if (loadedGame != null) {
                    postCrashHandlingRunnable { game.loadGame(loadedGame) }
                } else if (exception !is CancellationException)
                    handleLoadGameException("Could not load game from custom location!", exception)
            }
        }
        add(button).row()
    }

    private fun getCopyExistingSaveToClipboardButton(): TextButton {
        val button = copyExistingSaveToClipboard.toTextButton()
        button.onClick {
            launchCrashHandling(copyExistingSaveToClipboard) {
                try {
                    val gameText = game.gameSaver.getSave(selectedSave).readString()
                    Gdx.app.clipboard.contents = if (gameText[0] == '{') Gzip.zip(gameText) else gameText
                } catch (_: Throwable) {
                }
            }
        }
        button.disable()
        return button
    }

    private fun getLoadMissingModsButton(): TextButton {
        val button = downloadMissingMods.toTextButton()
        button.onClick {
            loadMissingMods()
        }
        button.isVisible = false
        return button
    }

    private fun handleLoadGameException(primaryText: String, ex: Exception?) {
        var errorText = primaryText.tr()
        if (ex is UncivShowableException) errorText += "\n${ex.message}"
        errorLabel.setText(errorText)
        errorLabel.isVisible = true
        ex?.printStackTrace()
        if (ex is MissingModsException) {
            loadMissingModsButton.isVisible = true
            missingModsToLoad = ex.missingMods
        }
    }

    private fun loadMissingMods() {
        loadMissingModsButton.isEnabled = false
        descriptionLabel.setText(Constants.loading.tr())
        launchCrashHandling(downloadMissingMods, runAsDaemon = false) {
            try {
                val mods = missingModsToLoad.replace(' ', '-').lowercase().splitToSequence(",-")
                for (modName in mods) {
                    val repos = Github.tryGetGithubReposWithTopic(10, 1, modName)
                        ?: throw UncivShowableException("Could not download mod list.".tr())
                    val repo = repos.items.firstOrNull { it.name.lowercase() == modName }
                        ?: throw UncivShowableException("Could not find a mod named \"[$modName]\".".tr())
                    val modFolder = Github.downloadAndExtract(
                        repo.html_url, repo.default_branch,
                        Gdx.files.local("mods")
                    )
                        ?: throw Exception() // downloadAndExtract returns null for 404 errors and the like -> display something!
                    Github.rewriteModOptions(repo, modFolder)
                }
                postCrashHandlingRunnable {
                    RulesetCache.loadRulesets()
                    missingModsToLoad = ""
                    loadMissingModsButton.isVisible = false
                    errorLabel.isVisible = false
                    ToastPopup("Missing mods are downloaded successfully.", this@LoadGameScreen)
                }
            } catch (ex: Exception) {
                handleLoadGameException("Could not load the missing mods!", ex)
            } finally {
                loadMissingModsButton.isEnabled = true
                descriptionLabel.setText("")
            }
        }
    }

}

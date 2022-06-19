package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.SerializationException
import com.unciv.Constants
import com.unciv.logic.GameSaver
import com.unciv.logic.MissingModsException
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.Github
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import java.io.FileNotFoundException

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
        setDefaultCloseAction()
        rightSideTable.initRightSideTable()
        rightSideButton.onActivation { onLoadGame() }
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)
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
        Concurrency.run(loadGame) {
            try {
                // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                val loadedGame = game.gameSaver.loadGameByName(selectedSave)
                game.loadGame(loadedGame)
            } catch (ex: Exception) {
                launchOnGLThread {
                    loadingPopup.close()
                    if (ex is MissingModsException) {
                        handleLoadGameException("Could not load game", ex)
                        return@launchOnGLThread
                    }
                    val cantLoadGamePopup = Popup(this@LoadGameScreen)
                    cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                    if (ex is SerializationException)
                        cantLoadGamePopup.addGoodSizedLabel("The file data seems to be corrupted.").row()
                    if (ex.cause is FileNotFoundException && (ex.cause as FileNotFoundException).message?.contains("Permission denied") == true) {
                        cantLoadGamePopup.addGoodSizedLabel("You do not have sufficient permissions to access the file.").row()
                    } else if (ex is UncivShowableException) {
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
        val pasteButton = loadFromClipboard.toTextButton()
        pasteButton.onActivation {
            Concurrency.run(loadFromClipboard) {
                try {
                    val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                    val loadedGame = GameSaver.gameInfoFromString(clipboardContentsString)
                    game.loadGame(loadedGame)
                } catch (ex: Exception) {
                    launchOnGLThread { handleLoadGameException("Could not load game from clipboard!", ex) }
                }
            }
        }
        val ctrlV = KeyCharAndCode.ctrl('v')
        pasteButton.keyShortcuts.add(ctrlV)
        pasteButton.addTooltip(ctrlV)
        return pasteButton
    }

    private fun Table.addLoadFromCustomLocationButton() {
        if (!game.gameSaver.canLoadFromCustomSaveLocation()) return
        val loadFromCustomLocation = loadFromCustomLocation.toTextButton()
        loadFromCustomLocation.onClick {
            errorLabel.isVisible = false
            loadFromCustomLocation.setText(Constants.loading.tr())
            loadFromCustomLocation.disable()
            Concurrency.run(Companion.loadFromCustomLocation) {
                game.gameSaver.loadGameFromCustomLocation { result ->
                    if (result.isError()) {
                        handleLoadGameException("Could not load game from custom location!", result.exception)
                    } else if (result.isSuccessful()) {
                        Concurrency.run {
                            game.loadGame(result.gameData!!)
                        }
                    }
                }
            }
        }
        add(loadFromCustomLocation).row()
    }

    private fun getCopyExistingSaveToClipboardButton(): TextButton {
        val copyButton = copyExistingSaveToClipboard.toTextButton()
        copyButton.onActivation {
            Concurrency.run(copyExistingSaveToClipboard) {
                try {
                    val gameText = game.gameSaver.getSave(selectedSave).readString()
                    Gdx.app.clipboard.contents = if (gameText[0] == '{') Gzip.zip(gameText) else gameText
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    ToastPopup("Could not save game to clipboard!", this@LoadGameScreen)
                }
            }
        }
        copyButton.disable()
        val ctrlC = KeyCharAndCode.ctrl('c')
        copyButton.keyShortcuts.add(ctrlC)
        copyButton.addTooltip(ctrlC)
        return copyButton
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
        if (ex is UncivShowableException) errorText += "\n${ex.localizedMessage}"
        ex?.printStackTrace()
        Concurrency.runOnGLThread {
            errorLabel.setText(errorText)
            errorLabel.isVisible = true
            if (ex is MissingModsException) {
                loadMissingModsButton.isVisible = true
                missingModsToLoad = ex.missingMods
            }
        }
    }

    private fun loadMissingMods() {
        loadMissingModsButton.isEnabled = false
        descriptionLabel.setText(Constants.loading.tr())
        Concurrency.runOnNonDaemonThreadPool(downloadMissingMods) {
            try {
                val mods = missingModsToLoad.replace(' ', '-').lowercase().splitToSequence(",-")
                for (modName in mods) {
                    val repos = Github.tryGetGithubReposWithTopic(10, 1, modName)
                        ?: throw UncivShowableException("Could not download mod list.")
                    val repo = repos.items.firstOrNull { it.name.lowercase() == modName }
                        ?: throw UncivShowableException("Could not find a mod named \"[$modName]\".")
                    val modFolder = Github.downloadAndExtract(
                        repo.html_url, repo.default_branch,
                        Gdx.files.local("mods")
                    )
                        ?: throw Exception() // downloadAndExtract returns null for 404 errors and the like -> display something!
                    Github.rewriteModOptions(repo, modFolder)
                    val labelText = descriptionLabel.text // Surprise - a StringBuilder
                    labelText.appendLine()
                    labelText.append("[${repo.name}] Downloaded!".tr())
                    launchOnGLThread { descriptionLabel.setText(labelText) }
                }
                launchOnGLThread {
                    RulesetCache.loadRulesets()
                    missingModsToLoad = ""
                    loadMissingModsButton.isVisible = false
                    errorLabel.isVisible = false
                    rightSideTable.pack()
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

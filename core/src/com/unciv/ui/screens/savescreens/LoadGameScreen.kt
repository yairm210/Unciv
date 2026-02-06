package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.MissingModsException
import com.unciv.logic.MissingNationException
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.github.GithubAPI
import com.unciv.logic.github.GithubAPI.downloadAndExtract
import com.unciv.platform.PlatformCapabilities
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.LoadingPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.CoroutineScope

class LoadGameScreen : LoadOrSaveScreen() {
    private val copySavedGameToClipboardButton = getCopyExistingSaveToClipboardButton()
    
    /** Inheriting here again exposes [getLoadExceptionMessage] and [loadMissingMods] to
     *  other clients (WorldScreen, QuickSave, Multiplayer) without needing to rewrite many imports 
     */
    companion object : Helpers {
        private const val loadGame = "Load game"
        private const val loadFromCustomLocation = "Load from custom location"
        private const val loadFromClipboard = "Load copied data"
        private const val copyExistingSaveToClipboard = "Copy saved game to clipboard"
        internal const val downloadMissingMods = "Download missing mods"
    }

    init {
        errorLabel.isVisible = false
        errorLabel.wrap = true

        setDefaultCloseAction()
        rightSideTable.initRightSideTable()
        rightSideButton.onActivation { onLoadGame(selectedSave) }
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        rightSideButton.isVisible = false
        pickerPane.bottomTable.background = skinStrings.getUiBackground("LoadGameScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("LoadGameScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)
    }

    override fun resetWindowState() {
        super.resetWindowState()
        copySavedGameToClipboardButton.disable()
        rightSideButton.setText(loadGame.tr())
        rightSideButton.disable()
    }

    override fun onExistingSaveSelected(saveGameFile: FileHandle) {
        copySavedGameToClipboardButton.enable()
        rightSideButton.isVisible = true
        rightSideButton.setText("Load [${saveGameFile.name()}]".tr())
        rightSideButton.enable()
    }

    override fun doubleClickAction(saveGameFile: FileHandle) {
        onLoadGame(saveGameFile)
    }

    private fun Table.initRightSideTable() {
        add(getLoadFromClipboardButton()).row()
        if (PlatformCapabilities.current.customFileChooser)
            addLoadFromCustomLocationButton()
        add(errorLabel).width(stage.width / 2).center().row()
        add(deleteSaveButton).row()
        add(copySavedGameToClipboardButton).row()
        add(showAutosavesCheckbox).row()
    }

    private fun onLoadGame(saveGameFile: FileHandle?) {
        if (saveGameFile == null) return
        val loadingPopup = LoadingPopup(this)
        Concurrency.run(loadGame) {
            try {
                // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                val loadedGame = game.files.loadGameFromFile(saveGameFile)
                game.loadGame(loadedGame, callFromLoadScreen = true)
            } catch (notAPlayer: UncivShowableException) {
                launchOnGLThread {
                    val (message) = getLoadExceptionMessage(notAPlayer)
                    loadingPopup.reuseWith(message, true)
                    handleLoadGameException(notAPlayer)
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    loadingPopup.close()
                    handleLoadGameException(ex)
                }
            }
        }
    }

    private fun getLoadFromClipboardButton(): TextButton {
        val pasteButton = loadFromClipboard.toTextButton()
        pasteButton.onActivation {
            if (!Gdx.app.clipboard.hasContents()) return@onActivation
            pasteButton.setText(Constants.working.tr())
            pasteButton.disable()
            Concurrency.run(loadFromClipboard) {
                try {
                    val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                    val loadedGame = UncivFiles.gameInfoFromString(clipboardContentsString)
                    game.loadGame(loadedGame, callFromLoadScreen = true)
                } catch (ex: Exception) {
                    launchOnGLThread { handleLoadGameException(ex, "Could not load game from clipboard!") }
                } finally {
                    launchOnGLThread {
                        pasteButton.setText(loadFromClipboard.tr())
                        pasteButton.enable()
                    }
                }
            }
        }
        val ctrlV = KeyCharAndCode.ctrl('v')
        pasteButton.keyShortcuts.add(ctrlV)
        pasteButton.addTooltip(ctrlV)
        return pasteButton
    }

    private fun Table.addLoadFromCustomLocationButton() {
        val loadFromCustomLocationButton = loadFromCustomLocation.toTextButton()
        loadFromCustomLocationButton.onClick {
            errorLabel.isVisible = false
            loadFromCustomLocationButton.setText(Constants.loading.tr())
            loadFromCustomLocationButton.disable()
            Concurrency.run(loadFromCustomLocation) {
                game.files.loadGameFromCustomLocation(
                    {
                        Concurrency.run { game.loadGame(it, callFromLoadScreen = true) }
                    },
                    {
                        if (it !is PlatformSaverLoader.Cancelled)
                            handleLoadGameException(it, "Could not load game from custom location!")
                        loadFromCustomLocationButton.setText(loadFromCustomLocation.tr())
                        loadFromCustomLocationButton.enable()
                    }
                )
            }
        }
        add(loadFromCustomLocationButton).row()
    }

    private fun getCopyExistingSaveToClipboardButton(): TextButton {
        val copyButton = copyExistingSaveToClipboard.toTextButton()
        copyButton.onActivation {
            val file = selectedSave ?: return@onActivation
            Concurrency.run(copyExistingSaveToClipboard) {
                copySaveToClipboard(file)
            }
        }
        copyButton.disable()
        val ctrlC = KeyCharAndCode.ctrl('c')
        copyButton.keyShortcuts.add(ctrlC)
        copyButton.addTooltip(ctrlC)
        return copyButton
    }

    private fun CoroutineScope.copySaveToClipboard(file: FileHandle) {
        val gameText = try {
            file.readString()
        } catch (ex: Throwable) {
            val (errorText, isUserFixable) = getLoadExceptionMessage(ex, saveToClipboardErrorMessage)
            if (!isUserFixable)
                Log.error(saveToClipboardErrorMessage, ex)
            launchOnGLThread {
                ToastPopup(errorText, this@LoadGameScreen)
            }
            return
        }
        try {
            Gdx.app.clipboard.contents = if (gameText[0] == '{') Gzip.zip(gameText) else gameText
            launchOnGLThread {
                ToastPopup("'[${file.name()}]' copied to clipboard!", this@LoadGameScreen)
            }
        } catch (ex: Throwable) {
            Log.error(saveToClipboardErrorMessage, ex)
            launchOnGLThread {
                ToastPopup(saveToClipboardErrorMessage, this@LoadGameScreen)
            }
        }
    }

    private fun handleLoadGameException(ex: Exception, primaryText: String = "Could not load game!") {
        val isUserFixable = handleException(ex, primaryText)
        if (!isUserFixable) {
            val cantLoadGamePopup = Popup(this@LoadGameScreen)
            cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
            cantLoadGamePopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
            cantLoadGamePopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
            cantLoadGamePopup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
            cantLoadGamePopup.addCloseButton()
            cantLoadGamePopup.open()
        }

        if (ex is MissingModsException && PlatformCapabilities.current.onlineModDownloads) {
            loadMissingModsAsync(ex.missingMods)
        }
        if (ex is MissingNationException){
            redownloadUnupdatedMods(ex.modNames)
        }
    }

    /** If any nation is missing from a saved game, chances are that one of the mods needs to be redownloaded
     * Since we don't know which one, we check all mods that
     * A. Have at least one nation
     * B. Are outdated
     * */
    private fun redownloadUnupdatedMods(modNames: LinkedHashSet<String>) {
        descriptionLabel.setText("Downloading unupdated mods...")
        Concurrency.runOnNonDaemonThreadPool("redownloadUnupdatedMods") { 
            val modsToCheck = modNames.mapNotNull { RulesetCache[it] }
                .filter { it.nations.any() && it.modOptions.modUrl.isNotEmpty()  }
            
            val reposToUpdate = modsToCheck
                .mapNotNull { 
                    val repo = GithubAPI.Repo.parseUrl(it.modOptions.modUrl) ?: return@mapNotNull null
                    if (it.modOptions.lastUpdated == repo.pushed_at) return@mapNotNull null
                    repo
                }
            
            Concurrency.runOnGLThread {
                ToastPopup("Updating mods: $reposToUpdate", this@LoadGameScreen)
                descriptionLabel.setText("Downloading unupdated mods - 0/${reposToUpdate.size}")
            }

            for ((index, repo) in reposToUpdate.withIndex()) {
                repo.downloadAndExtract()
                Concurrency.runOnGLThread {
                    ToastPopup("Downloaded ${repo.name}", this@LoadGameScreen)
                    descriptionLabel.setText("Downloading unupdated mods - ${index+1}/${reposToUpdate.size}")
                }
            }
        }
    }

    private fun loadMissingModsAsync(missingMods: Iterable<String>) {
        descriptionLabel.setText(Constants.loading.tr())
        Concurrency.runOnNonDaemonThreadPool(downloadMissingMods) {
            try {
                loadMissingMods(missingMods,
                    onModDownloaded = {
                        val labelText = descriptionLabel.text // A Gdx CharArray that has StringBuilder-like methods
                        labelText.appendLine()
                        labelText.append("[$it] Downloaded!".tr())
                        launchOnGLThread { descriptionLabel.setText(labelText) }
                    },
                    onCompleted = {
                        launchOnGLThread {
                            RulesetCache.loadRulesets()
                            errorLabel.isVisible = false
                            rightSideTable.pack()
                            ToastPopup("Missing mods are downloaded successfully.", this@LoadGameScreen)
                        }
                    }
                )
            } catch (ex: Exception) {
                launchOnGLThread {
                    handleLoadGameException(ex, "Could not load the missing mods!")
                }
            } finally {
                launchOnGLThread {
                    descriptionLabel.setText("")
                }
            }
        }
    }
}

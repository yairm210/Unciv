package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.SerializationException
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.MissingModsException
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.github.Github
import com.unciv.logic.github.Github.folderNameToRepoName
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
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
import java.io.FileNotFoundException
import java.nio.file.attribute.DosFileAttributes
import kotlin.io.path.Path
import kotlin.io.path.readAttributes

class LoadGameScreen : LoadOrSaveScreen() {
    private val copySavedGameToClipboardButton = getCopyExistingSaveToClipboardButton()
    private val errorLabel = "".toLabel(Color.RED)
    private val loadMissingModsButton = getLoadMissingModsButton()
    private var missingModsToLoad: Iterable<String> = emptyList()

    companion object {
        private const val loadGame = "Load game"
        private const val loadFromCustomLocation = "Load from custom location"
        private const val loadFromClipboard = "Load copied data"
        private const val copyExistingSaveToClipboard = "Copy saved game to clipboard"
        internal const val downloadMissingMods = "Download missing mods"

        /** Gets a translated exception message to show to the user.
         * @param file Optional, used for detection of local read-only files, only relevant for write operations on Windows desktops.
         * @return The first returned value is the message, the second is signifying if the user can likely fix this problem. */
        fun getLoadExceptionMessage(ex: Throwable, primaryText: String = "Could not load game!", file: FileHandle? = null): Pair<String, Boolean> {
            val errorText = StringBuilder(primaryText.tr())
            errorText.appendLine()
            var cause = ex
            while (cause.cause != null && cause is GdxRuntimeException) cause = cause.cause!!

            fun FileHandle.isReadOnly(): Boolean {
                try {
                    val attr = Path(file().absolutePath).readAttributes<DosFileAttributes>()
                    return attr.isReadOnly
                } catch (_: Throwable) { return false }
            }

            val isUserFixable = when (cause) {
                is UncivShowableException -> {
                    errorText.append(ex.localizedMessage)
                    true
                }
                is SerializationException -> {
                    errorText.append("The file data seems to be corrupted.".tr())
                    false
                }
                is FileNotFoundException -> {
                    // This is thrown both for chmod/ACL denials and for writing to a file with the read-only attribute set
                    // On Windows, `message` is already (and illegally) partially localized.
                    val localizedMessage = UncivGame.Current.getSystemErrorMessage(5)
                    val isPermissionDenied = cause.message?.run {
                        contains("Permission denied") || (localizedMessage != null && contains(localizedMessage))
                    } == true
                    if (isPermissionDenied) {
                        if (file != null && file.isReadOnly())
                            errorText.append("The file is marked read-only.".tr())
                        else
                            errorText.append("You do not have sufficient permissions to access the file.".tr())
                    }
                    isPermissionDenied
                }
                else -> {
                    errorText.append("Unhandled problem, [${ex::class.simpleName} ${ex.stackTraceToString()}]".tr())
                    false
                }
            }
            return Pair(errorText.toString(), isUserFixable)
        }

        fun loadMissingMods(missingMods: Iterable<String>, onModDownloaded:(String)->Unit, onCompleted:()->Unit) {
            for (rawName in missingMods) {
                val modName = rawName.folderNameToRepoName().lowercase()
                val repos = Github.tryGetGithubReposWithTopic(10, 1, modName)
                    ?: throw UncivShowableException("Could not download mod list.")
                val repo = repos.items.firstOrNull { it.name.lowercase() == modName }
                    ?: throw UncivShowableException("Could not find a mod named \"[$modName]\".")
                val modFolder = Github.downloadAndExtract(
                    repo,
                    UncivGame.Current.files.getModsFolder()
                )
                    ?: throw Exception("Unexpected 404 error") // downloadAndExtract returns null for 404 errors and the like -> display something!
                Github.rewriteModOptions(repo, modFolder)
                onModDownloaded(repo.name)
            }
            onCompleted()
        }
    }

    init {
        errorLabel.isVisible = false
        errorLabel.wrap = true

        setDefaultCloseAction()
        rightSideTable.initRightSideTable()
        rightSideButton.onActivation { onLoadGame() }
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

    override fun doubleClickAction() {
        onLoadGame()
    }

    private fun Table.initRightSideTable() {
        add(getLoadFromClipboardButton()).row()
        addLoadFromCustomLocationButton()
        add(errorLabel).width(stage.width / 2).row()
        add(loadMissingModsButton).row()
        add(deleteSaveButton).row()
        add(copySavedGameToClipboardButton).row()
        add(showAutosavesCheckbox).row()
    }

    private fun onLoadGame() {
        if (selectedSave == null) return
        val loadingPopup = LoadingPopup(this)
        Concurrency.run(loadGame) {
            try {
                // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                val loadedGame = game.files.loadGameFromFile(selectedSave!!)
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
                try {
                    val gameText = file.readString()
                    Gdx.app.clipboard.contents = if (gameText[0] == '{') Gzip.zip(gameText) else gameText
                    launchOnGLThread {
                        ToastPopup("'[${file.name()}]' copied to clipboard!", this@LoadGameScreen)
                    }
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    launchOnGLThread {
                        ToastPopup("Could not save game to clipboard!", this@LoadGameScreen)
                    }
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

    private fun handleLoadGameException(ex: Exception, primaryText: String = "Could not load game!") {
        Log.error("Error while loading game", ex)
        val (errorText, isUserFixable) = getLoadExceptionMessage(ex, primaryText)

        Concurrency.runOnGLThread {
            if (!isUserFixable) {
                val cantLoadGamePopup = Popup(this@LoadGameScreen)
                cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                cantLoadGamePopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                cantLoadGamePopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                cantLoadGamePopup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
                cantLoadGamePopup.addCloseButton()
                cantLoadGamePopup.open()
            }

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
                Companion.loadMissingMods(missingModsToLoad,
                    onModDownloaded = {
                        val labelText = descriptionLabel.text // Surprise - a StringBuilder
                        labelText.appendLine()
                        labelText.append("[$it] Downloaded!".tr())
                        launchOnGLThread { descriptionLabel.setText(labelText) }
                    },
                    onCompleted = {
                        launchOnGLThread {
                            RulesetCache.loadRulesets()
                            missingModsToLoad = emptyList()
                            loadMissingModsButton.isVisible = false
                            errorLabel.isVisible = false
                            rightSideTable.pack()
                            ToastPopup("Missing mods are downloaded successfully.", this@LoadGameScreen)
                        }
                    }
                )
            } catch (ex: Exception) {
                handleLoadGameException(ex, "Could not load the missing mods!")
            } finally {
                launchOnGLThread {
                    loadMissingModsButton.isEnabled = true
                    descriptionLabel.setText("")
                }
            }
        }
    }
}

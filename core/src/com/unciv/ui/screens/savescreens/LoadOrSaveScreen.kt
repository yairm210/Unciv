package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.SerializationException
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.github.Github
import com.unciv.logic.github.Github.folderNameToRepoName
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.UncivDateFormat.formatDate
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import java.io.FileNotFoundException
import java.nio.file.attribute.DosFileAttributes
import java.util.Date
import kotlin.io.path.Path
import kotlin.io.path.readAttributes


abstract class LoadOrSaveScreen(
    fileListHeaderText: String? = null
) : PickerScreen(disableScroll = true) {

    abstract fun onExistingSaveSelected(saveGameFile: FileHandle)
    abstract fun doubleClickAction()

    protected var selectedSave: FileHandle? = null
        private set

    private val savesScrollPane = VerticalFileListScrollPane()
    protected val rightSideTable = Table()
    protected val deleteSaveButton = "Delete save".toTextButton(skin.get("negative", TextButton.TextButtonStyle::class.java))
    protected val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)
    protected val errorLabel = "".toLabel(Color.RED, alignment = Align.center)

    companion object : Helpers {
        internal const val saveToClipboardErrorMessage = "Could not save game to clipboard!"
    }

    init {
        savesScrollPane.onChange(::selectExistingSave)
        savesScrollPane.onDoubleClick { doubleClickAction() }

        rightSideTable.defaults().pad(5f, 10f)

        showAutosavesCheckbox.isChecked = UncivGame.Current.settings.showAutosaves
        showAutosavesCheckbox.onChange {
            updateShownSaves(showAutosavesCheckbox.isChecked)
            UncivGame.Current.settings.showAutosaves = showAutosavesCheckbox.isChecked
        }
        val ctrlA = KeyCharAndCode.ctrl('a')
        showAutosavesCheckbox.keyShortcuts.add(ctrlA) { showAutosavesCheckbox.toggle() }
        showAutosavesCheckbox.addTooltip(ctrlA)

        deleteSaveButton.disable()
        deleteSaveButton.onActivation { onDeleteClicked() }
        deleteSaveButton.keyShortcuts.add(KeyCharAndCode.DEL)
        deleteSaveButton.addTooltip(KeyCharAndCode.DEL)

        if (fileListHeaderText != null)
            topTable.add(fileListHeaderText.toLabel()).pad(10f).row()

        updateShownSaves(showAutosavesCheckbox.isChecked)

        topTable.add(savesScrollPane)
        topTable.add(rightSideTable)
        topTable.pack()
    }

    open fun resetWindowState() {
        updateShownSaves(showAutosavesCheckbox.isChecked)
        deleteSaveButton.disable()
        descriptionLabel.setText("")
    }

    private fun onDeleteClicked() {
        if (selectedSave == null) return
        val name = selectedSave!!.name()
        ConfirmPopup(this, "Are you sure you want to delete this save?", "Delete save") {
            val result = try {
                if (game.files.deleteSave(selectedSave!!)) {
                    resetWindowState()
                    "[$name] deleted successfully."
                } else {
                    "Failed to delete [$name]."
                }
            } catch (_: SecurityException) {
                "Insufficient permissions to delete [$name]."
            } catch (_: Throwable) {
                "Failed to delete [$name]."
            }
            descriptionLabel.setText(result.tr())
        }.open()
    }

    private fun updateShownSaves(showAutosaves: Boolean) {
        savesScrollPane.updateSaveGames(game.files, showAutosaves)
    }

    private fun selectExistingSave(saveGameFile: FileHandle) {
        errorLabel.isVisible = false
        deleteSaveButton.enable()

        selectedSave = saveGameFile
        showSaveInfo(saveGameFile)
        rightSideButton.isVisible = true
        onExistingSaveSelected(saveGameFile)
    }

    private fun showSaveInfo(saveGameFile: FileHandle) {
        descriptionLabel.setText(Constants.loading.tr())
        Concurrency.run("LoadMetaData") { // Even loading the game to get its metadata can take a long time on older phones
            val textToSet = try {
                val savedAt = Date(saveGameFile.lastModified())
                val game = game.files.loadGamePreviewFromFile(saveGameFile)
                val playerCivNames = game.civilizations
                    .filter { it.isPlayerCivilization() }.joinToString { it.civName.tr() }
                val mods = if (game.gameParameters.mods.isEmpty()) ""
                    else "\n{Mods:} " + game.gameParameters.mods.joinToString()

                // Format result for textToSet
                "${saveGameFile.name()}\n{Saved at}: ${savedAt.formatDate()}\n" +
                "$playerCivNames, ${game.difficulty.tr()}, ${Fonts.turn}${game.turns}\n" +
                "{Base ruleset:} ${game.gameParameters.baseRuleset}$mods"
            } catch (_: Exception) {
                "\n{Could not load game}!"
            }

            launchOnGLThread {
                descriptionLabel.setText(textToSet.tr())
            }
        }
    }

    /** Show appropriate message for an exception and log the severe cases
     *  @return isUserFixable */
    protected fun handleException(ex: Exception, primaryText: String, file: FileHandle? = null): Boolean {
        val (errorText, isUserFixable) = getLoadExceptionMessage(ex, primaryText, file)
        if (!isUserFixable)
            Log.error(primaryText, ex)
        errorLabel.setText(errorText)
        errorLabel.isVisible = true
        return isUserFixable
    }

    interface Helpers {
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
}

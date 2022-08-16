package com.unciv.ui.saves

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.UncivDateFormat.formatDate
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import java.util.*


abstract class LoadOrSaveScreen(
    fileListHeaderText: String? = null
) : PickerScreen(disableScroll = true) {

    abstract fun onExistingSaveSelected(saveGameFile: FileHandle)

    protected var selectedSave = ""
        private set

    private val savesScrollPane = VerticalFileListScrollPane()
    protected val rightSideTable = Table()
    protected val deleteSaveButton = "Delete save".toTextButton(skin.get("negative", TextButton.TextButtonStyle::class.java))
    protected val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)

    init {
        savesScrollPane.onChange(::selectExistingSave)

        rightSideTable.defaults().pad(5f, 10f)

        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
            updateShownSaves(showAutosavesCheckbox.isChecked)
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

        updateShownSaves(false)

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
        if (selectedSave.isEmpty()) return
        ConfirmPopup(this, "Are you sure you want to delete this save?", "Delete save") {
            val result = try {
                if (game.files.deleteSave(selectedSave)) {
                    resetWindowState()
                    "[$selectedSave] deleted successfully."
                } else {
                    "Failed to delete [$selectedSave]."
                }
            } catch (ex: SecurityException) {
                "Insufficient permissions to delete [$selectedSave]."
            } catch (ex: Throwable) {
                "Failed to delete [$selectedSave]."
            }
            descriptionLabel.setText(result.tr())
        }.open()
    }

    private fun updateShownSaves(showAutosaves: Boolean) {
        savesScrollPane.updateSaveGames(game.files, showAutosaves)
    }

    private fun selectExistingSave(saveGameFile: FileHandle) {
        deleteSaveButton.enable()

        selectedSave = saveGameFile.name()
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
            } catch (ex: Exception) {
                "\n{Could not load game}!"
            }

            launchOnGLThread {
                descriptionLabel.setText(textToSet.tr())
            }
        }
    }
}

package com.unciv.ui.saves

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.UncivDateFormat.formatDate
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import java.util.Date


abstract class LoadOrSaveScreen(
    fileListHeaderText: String? = null
) : PickerScreen(disableScroll = true) {

    abstract fun onExistingSaveSelected(saveGameFile: FileHandle)

    protected var selectedSave = ""
        private set

    private val savesScrollPane = VerticalFileListScrollPane(keyPressDispatcher)
    protected val rightSideTable = Table()
    protected val deleteSaveButton = "Delete save".toTextButton()
    protected val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)

    init {
        savesScrollPane.onChange(::selectExistingSave)

        rightSideTable.defaults().pad(5f, 10f)

        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
            updateShownSaves(showAutosavesCheckbox.isChecked)
        }
        val ctrlA = KeyCharAndCode.ctrl('a')
        keyPressDispatcher[ctrlA] = { showAutosavesCheckbox.toggle() }
        showAutosavesCheckbox.addTooltip(ctrlA)

        deleteSaveButton.disable()
        deleteSaveButton.onClick(::onDeleteClicked)
        keyPressDispatcher[KeyCharAndCode.DEL] = ::onDeleteClicked
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
        val result = try {
            if (game.gameSaver.deleteSave(selectedSave)) {
                resetWindowState()
                "[$selectedSave] deleted successfully."
            } else "Failed to delete [$selectedSave]."
        } catch (ex: SecurityException) {
            "Insufficient permissions to delete [$selectedSave]."
        } catch (ex: Throwable) {
            "Failed to delete [$selectedSave]."
        }
        descriptionLabel.setText(result)
    }

    private fun updateShownSaves(showAutosaves: Boolean) {
        savesScrollPane.updateSaveGames(game.gameSaver, showAutosaves)
    }

    private fun selectExistingSave(saveGameFile: FileHandle) {
        deleteSaveButton.enable()
        deleteSaveButton.color = Color.RED

        selectedSave = saveGameFile.name()
        showSaveInfo(saveGameFile)
        onExistingSaveSelected(saveGameFile)
    }

    private fun showSaveInfo(saveGameFile: FileHandle) {
        descriptionLabel.setText(Constants.loading.tr())
        launchCrashHandling("LoadMetaData") { // Even loading the game to get its metadata can take a long time on older phones
            val textToSet = try {
                val savedAt = Date(saveGameFile.lastModified())
                val game = game.gameSaver.loadGamePreviewFromFile(saveGameFile)
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

            postCrashHandlingRunnable {
                descriptionLabel.setText(textToSet.tr())
            }
        }
    }
}

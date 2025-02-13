package com.unciv.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LanguageTable
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.PickerScreen

/** A [PickerScreen] to select a language, used once on the initial run after a fresh install.
 *  After that, [OptionsPopup] provides the functionality.
 *  Reusable code is in [LanguageTable] and [addLanguageTables].
 */
class UniqueBuilderScreen(ruleset: Ruleset) : PickerScreen() {

    private val mainUniqueTable = UniqueTable(true, ruleset, stage) { updateCurrentUniqueText() }
    private val modifierTables = mutableListOf<UniqueTable>()
    private var currentUniqueText = ""

    init {
        setDefaultCloseAction()
        rightSideButton.isVisible = false
        rightSideButton.setText("Copy to clipboard")
        rightSideButton.onClick { Gdx.app.clipboard.contents = currentUniqueText }
        topTable.defaults().pad(5f)

        mainUniqueTable.initialize()
        topTable.add(mainUniqueTable).row()

        val modifierTableHolder = Table().apply { defaults().pad(5f) }
        topTable.add(modifierTableHolder).row()

        val addModifierButton = "Add Modifier".toTextButton()
        addModifierButton.onClick {
            val modifierTable = UniqueTable(false, ruleset, stage){ updateCurrentUniqueText() }
            modifierTables.add(modifierTable)
            modifierTableHolder.add(modifierTable).row()
            modifierTable.initialize()
        }
        topTable.add(addModifierButton)
    }

    private fun updateCurrentUniqueText() {
        currentUniqueText = mainUniqueTable.uniqueTextField.text +
                modifierTables.joinToString("") { " <"+it.uniqueTextField.text+">" }
        descriptionLabel.setText(currentUniqueText)
        rightSideButton.isVisible = true
        rightSideButton.enable()
    }
}

class UniqueTable(isMainUnique: Boolean, val ruleset: Ruleset, stage: Stage,
                  val onUniqueChange : () -> Unit) :Table() {
    private val parameterSelectBoxTable = Table().apply { defaults().pad(5f) }
    private val uniqueSelectBoxTable = Table()
    private val uniqueSearchTextField = TextField("", BaseScreen.skin).apply { messageText = "Search" }
    private val uniqueErrorTable = Table().apply { defaults().pad(5f) }

    val uniqueTextField = TextField("Unique", BaseScreen.skin)
    private var uniqueTargetsSelectBox: SelectBox<UniqueTarget>

    init {
        this.stage = stage // required for width
        // Main unique should be non-modifier
        val uniqueTargets = if (isMainUnique) UniqueTarget.entries
            .filter { it.modifierType == UniqueTarget.ModifierType.None }
        // Additional ones should be modifiers
        else UniqueTarget.entries
            .filter { it.modifierType != UniqueTarget.ModifierType.None }

        defaults().pad(10f)
        background = ImageGetter.getWhiteDotDrawable().tint(ImageGetter.CHARCOAL.cpy().apply { a=0.3f })
        val uniqueTargetSelectBoxTable = Table().apply { defaults().pad(5f) }
        uniqueTargetsSelectBox = SelectBox<UniqueTarget>(BaseScreen.skin)
        uniqueTargetsSelectBox.setItems(uniqueTargets)
        uniqueTargetsSelectBox.selected = UniqueTarget.Global
        uniqueTargetSelectBoxTable.add(uniqueTargetsSelectBox)

        uniqueTargetSelectBoxTable.add(uniqueSelectBoxTable).row()
        // Row 2
        uniqueTargetSelectBoxTable.add()
        uniqueTargetSelectBoxTable.add(uniqueSearchTextField).row()
        add(uniqueTargetSelectBoxTable).row()

        uniqueTargetsSelectBox.onChange {
            onUniqueTargetChange(uniqueTargetsSelectBox, ruleset)
        }
        if (!isMainUnique) onUniqueTargetChange(uniqueTargetsSelectBox, ruleset)

        row()
        add(uniqueTextField).width(stage.width * 0.9f).row()
        add(parameterSelectBoxTable).row()


        uniqueTextField.onChange {
            updateUnique(ruleset, uniqueTextField)
        }

        uniqueErrorTable.defaults().pad(5f)
        uniqueErrorTable.background = ImageGetter.getWhiteDotDrawable().tint(Color.DARK_GRAY)
        add(uniqueErrorTable).row()
    }

    private fun onUniqueTargetChange(
        uniqueTargetsSelectBox: SelectBox<UniqueTarget>,
        ruleset: Ruleset,
    ) {
        val selected = uniqueTargetsSelectBox.selected
        val uniqueSelectBox = SelectBox<UniqueType>(BaseScreen.skin)

        fun setUniqueSelectBoxItems(){
            val uniquesForTarget = UniqueType.entries.filter { it.canAcceptUniqueTarget(selected) }
            val uniquesFiltered = uniquesForTarget.filter { it.getDeprecationAnnotation() == null }
                .filter { it.text.contains(uniqueSearchTextField.text, ignoreCase = true) }
            uniqueSelectBox.setItems(uniquesFiltered)
        }

        setUniqueSelectBoxItems()

        uniqueSelectBox.onChange {
            onUniqueSelected(uniqueSelectBox, uniqueTextField, ruleset, parameterSelectBoxTable)
        }
        onUniqueSelected(uniqueSelectBox, uniqueTextField, ruleset, parameterSelectBoxTable)

        // Unique search
        uniqueSearchTextField.onChange {
            setUniqueSelectBoxItems()
        }

        uniqueSelectBoxTable.clear()
        uniqueSelectBoxTable.add(uniqueSelectBox).width(stage.width * 0.5f).row()
    }

    private fun onUniqueSelected(
        uniqueSelectBox: SelectBox<UniqueType>,
        uniqueTextField: TextField,
        ruleset: Ruleset,
        parameterSelectBoxTable: Table
    ) {
        val uniqueType = uniqueSelectBox.selected ?: return

        uniqueTextField.text = uniqueType.text
        updateUnique(ruleset, uniqueTextField)

        parameterSelectBoxTable.clear()
        for ((index, parameter) in uniqueType.text.getPlaceholderParameters().withIndex()) {
            val paramTable = Table().apply { defaults().pad(10f) }
            paramTable.background = ImageGetter.getWhiteDotDrawable().tint(Color.DARK_GRAY)
            paramTable.add("Parameter ${index+1}: $parameter".toLabel()).row()
            val knownParamValues = uniqueType.parameterTypeMap[index]
                .flatMap { it.getKnownValuesForAutocomplete(ruleset) }.toSet()

            if (knownParamValues.isNotEmpty()) {
                val paramSelectBox = SelectBox<String>(BaseScreen.skin)
                paramSelectBox.setItems(knownParamValues)
                paramSelectBox.onChange {
                    val currentParams = uniqueTextField.text.getPlaceholderParameters().toMutableList()
                    currentParams[index] = paramSelectBox.selected ?: return@onChange
                    val newText = uniqueType.text.fillPlaceholders(*currentParams.toTypedArray())
                    uniqueTextField.text = newText
                    updateUnique(ruleset, uniqueTextField)
                }
                paramTable.add(paramSelectBox).row()

                val paramSearchTable = TextField("", BaseScreen.skin).apply { messageText = "Search" }
                paramSearchTable.onChange {
                    val filteredValues = knownParamValues.filter { it.contains(paramSearchTable.text, ignoreCase = true) }
                    paramSelectBox.setItems(filteredValues)
                }
                paramTable.add(paramSearchTable).row()
            } else paramTable.add("No known values".toLabel())

            parameterSelectBoxTable.add(paramTable).fillY()
        }
    }

    private fun updateUnique(ruleset: Ruleset, uniqueTextField: TextField) {
        uniqueErrorTable.clear()
        uniqueErrorTable.add("Errors:".toLabel()).row()

        val uniqueErrors = UniqueValidator(ruleset)
            .checkUnique(Unique(uniqueTextField.text), true, null, true)
        for (error in uniqueErrors)
            uniqueErrorTable.add(error.text.toLabel().apply { wrap = true }).width(stage.width/2).row()
        if (uniqueErrors.isEmpty())
            uniqueErrorTable.add("No errors!".toLabel())

        onUniqueChange()
    }

    /** Needs to come AFTER the UniqueTable is registered in the UniqueBuilderScreen,
     * because it needs to update the final unique text */
    fun initialize() {
        onUniqueTargetChange(uniqueTargetsSelectBox, ruleset)
    }
}

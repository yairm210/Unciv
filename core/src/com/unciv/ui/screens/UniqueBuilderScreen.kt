package com.unciv.ui.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.LanguageTable
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen

/** A [PickerScreen] to select a language, used once on the initial run after a fresh install.
 *  After that, [OptionsPopup] provides the functionality.
 *  Reusable code is in [LanguageTable] and [addLanguageTables].
 */
class UniqueBuilderScreen(ruleset: Ruleset) : PickerScreen() {

    private val parameterSelectBoxTable = Table().apply { defaults().pad(5f) }
    private val uniqueSelectBoxTable = Table()

    private val uniqueErrorTable = Table().apply { defaults().pad(5f) }
    private val uniqueText = TextField("Unique", skin)

    init {
        setDefaultCloseAction()
        rightSideButton.isVisible = false
        topTable.defaults().pad(5f)
        val uniqueTargets = UniqueTarget.entries.map { it.name }

        val uniqueTargetSelectBoxTable = Table().apply { defaults().pad(5f) }
        val uniqueTargetsSelectBox = TranslatedSelectBox(uniqueTargets, UniqueTarget.Global.name)
        uniqueTargetSelectBoxTable.add(uniqueTargetsSelectBox)

        uniqueTargetSelectBoxTable.add(uniqueSelectBoxTable).row()
        topTable.add(uniqueTargetSelectBoxTable).row()

        uniqueTargetsSelectBox.onChange {
            onUniqueTargetChange(uniqueTargetsSelectBox, ruleset, parameterSelectBoxTable)
        }
        onUniqueTargetChange(uniqueTargetsSelectBox, ruleset, parameterSelectBoxTable)

        topTable.row()
        topTable.add(uniqueText).width(stage.width * 0.9f).row()
        topTable.add(parameterSelectBoxTable).row()


        uniqueText.onChange {
            updateUniqueErrors(ruleset)
        }

        uniqueErrorTable.defaults().pad(5f)
        uniqueErrorTable.background = ImageGetter.getWhiteDotDrawable().tint(Color.DARK_GRAY)
        topTable.add(uniqueErrorTable).row()
    }

    private fun onUniqueTargetChange(
        uniqueTargetsSelectBox: TranslatedSelectBox,
        ruleset: Ruleset,
        parameterSelectBoxTable: Table,
    ) {
        val selected = UniqueTarget.entries.first { it.name == uniqueTargetsSelectBox.selected.value }
        val uniquesForTarget = UniqueType.entries.filter { it.canAcceptUniqueTarget(selected) }
        val uniqueSelectBox = TranslatedSelectBox(uniquesForTarget.map { it.name }, uniquesForTarget.first().name)
        uniqueSelectBox.onChange {
            onUniqueSelected(uniqueSelectBox, ruleset, parameterSelectBoxTable)
        }
        onUniqueSelected(uniqueSelectBox, ruleset, parameterSelectBoxTable)
        uniqueSelectBoxTable.clear()
        uniqueSelectBoxTable.add(uniqueSelectBox)
    }

    private fun onUniqueSelected(
        uniqueSelectBox: TranslatedSelectBox,
        ruleset: Ruleset,
        parameterSelectBoxTable: Table
    ) {
        val uniqueType = UniqueType.entries.first { it.name == uniqueSelectBox.selected.value }
        uniqueText.text = uniqueType.text
        updateUniqueErrors(ruleset)

        parameterSelectBoxTable.clear()
        for ((index, parameter) in uniqueType.text.getPlaceholderParameters().withIndex()) {
            val paramTable = Table().apply { defaults().pad(10f) }
            paramTable.background = ImageGetter.getWhiteDotDrawable().tint(Color.DARK_GRAY)
            paramTable.add("Parameter ${index+1}: $parameter".toLabel()).row()
            val knownParamValues = uniqueType.parameterTypeMap[index]
                .flatMap { it.getKnownValuesForAutocomplete(ruleset) }.toSet()

            if (knownParamValues.isNotEmpty()) {
                val paramSelectBox = TranslatedSelectBox(knownParamValues.toList(), knownParamValues.first())
                paramSelectBox.onChange {
                    val currentParams = uniqueText.text.getPlaceholderParameters().toMutableList()
                    currentParams[index] = paramSelectBox.selected.value
                    val newText = uniqueType.text.fillPlaceholders(*currentParams.toTypedArray())
                    uniqueText.text = newText
                    updateUniqueErrors(ruleset)
                }
                paramTable.add(paramSelectBox)
            } else paramTable.add("No known values".toLabel())

            parameterSelectBoxTable.add(paramTable).fillY()
        }
    }

    private fun updateUniqueErrors(ruleset: Ruleset) {
        uniqueErrorTable.clear()
        uniqueErrorTable.add("Errors:".toLabel()).row()

        val uniqueErrors = UniqueValidator(ruleset)
            .checkUnique(Unique(uniqueText.text), true, null, true)
        for (error in uniqueErrors)
            uniqueErrorTable.add(error.text.toLabel().apply { wrap = true }).width(stage.width/2).row()
        if (uniqueErrors.isEmpty())
            uniqueErrorTable.add("No errors!".toLabel())
    }
}

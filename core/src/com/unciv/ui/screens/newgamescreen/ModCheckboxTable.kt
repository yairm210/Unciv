package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.ModCompatibility
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.screens.modmanager.ModManagementScreen
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

/**
 * A widget containing one expander for extension mods.
 * Manages compatibility checks, warns or prevents incompatibilities.
 *
 * @param mods **Reference**: In/out set of active mods, modified in place: If this needs to change, call [changeGameParameters]
 * @param initialBaseRuleset The selected base Ruleset, only for running mod checks against. Use [setBaseRuleset] to change on the fly.
 * @param screen Parent screen, used only to show [ToastPopup]s
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class ModCheckboxTable(
    private var mods: LinkedHashSet<String>,
    initialBaseRuleset: String,
    private val screen: BaseScreen,
    isPortrait: Boolean = false,
    private val onUpdate: (String) -> Unit
): Table() {
    private var baseRulesetName = ""
    private lateinit var baseRuleset: Ruleset

    private class ModWithCheckBox(val mod: Ruleset, val widget: CheckBox)
    private val modWidgets = ArrayList<ModWithCheckBox>()

    /** Saved result from any complex mod check unless the causing selection has already been reverted.
     *  In other words, this can contain the text for an "Error" level check only if the Widget was
     *  initialized with such an invalid mod combination.
     *  This Widget reverts User changes that cause an Error severity immediately and this field is nulled.
     */
    var savedModcheckResult: String? = null

    private var disableChangeEvents = false

    private val expanderPadTop = if (isPortrait) 0f else 16f

    init {
        val modRulesets = RulesetCache.values.filter {
            ModCompatibility.isExtensionMod(it)
        }

        for (mod in modRulesets.sortedBy { it.name }) {
            val checkBox = mod.name.toCheckBox(mod.name in mods)
            checkBox.setText(ModManagementScreen.cleanModName(mod.name))
            checkBox.onChange { 
                // Checks are run in parallel thread to avoid ANRs
                Concurrency.run { checkBoxChanged(checkBox, mod) }
            }
            checkBox.left()
            modWidgets += ModWithCheckBox(mod, checkBox)
        }

        setBaseRuleset(initialBaseRuleset)
    }

    fun updateSelection() {
        savedModcheckResult = null
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.widget.isChecked = mod.mod.name in mods
        }
        disableChangeEvents = false
        deselectIncompatibleMods(null)
    }

    fun setBaseRuleset(newBaseRulesetName: String) {
        val newBaseRuleset = RulesetCache[newBaseRulesetName]
            // We're calling this from init, baseRuleset is lateinit, and the mod may have been deleted: Must make sure baseRuleset is initialized
            ?: return setBaseRuleset(BaseRuleset.Civ_V_GnK.fullName)
        baseRulesetName = newBaseRulesetName
        baseRuleset = newBaseRuleset
        savedModcheckResult = null
        clear()
        mods.clear()  // We'll regenerate this from checked widgets

        val compatibleMods = modWidgets
            .filter { ModCompatibility.meetsBaseRequirements(it.mod, baseRuleset) }

        if (compatibleMods.none()) return

        for (mod in compatibleMods) {
            if (mod.widget.isChecked) mods += mod.mod.name
        }

        add(ExpanderTab("Extension mods", persistenceID = "NewGameExpansionMods", defaultPad = 0f) {
            it.defaults().pad(5f,0f)

            val searchModsTextField = UncivTextField("Search mods")
            
            if (compatibleMods.size > 10) 
                it.add(searchModsTextField).row()
            
            val modsTable = Table()
            modsTable.defaults().pad(5f)
            it.add(modsTable)
            
            fun populateModsTable(){
                modsTable.clear()
                val searchText = searchModsTextField.text.lowercase()
                for (mod in compatibleMods)
                    if (searchText.isEmpty() || mod.mod.name.lowercase().contains(searchText))
                        modsTable.add(mod.widget).left().row()
            }
            populateModsTable()
            searchModsTextField.onChange { populateModsTable() }
        }).padTop(expanderPadTop).growX().row()

        disableIncompatibleMods()

        Concurrency.run { complexModCheckReturnsErrors() }
    }

    fun disableAllCheckboxes() {
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.widget.isChecked = false
        }
        mods.clear()
        disableChangeEvents = false

        savedModcheckResult = null
        disableIncompatibleMods()
        onUpdate("-")  // should match no mod
    }

    /** Runs in parallel thread */ 
    private fun complexModCheckReturnsErrors(): Boolean {
        // Check over complete combination of selected mods
        val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRulesetName)
        if (!complexModLinkCheck.isWarnUser()){
            savedModcheckResult = null
            return false
        }
        savedModcheckResult = complexModLinkCheck.getErrorText()
        complexModLinkCheck.showWarnOrErrorToast(screen)
        return complexModLinkCheck.isError()
    }

    /** Runs in parallel thread so as not to block main thread - running complex mod check can be expensive */
    private fun checkBoxChanged(
        checkBox: CheckBox,
        mod: Ruleset
    ) {
        if (disableChangeEvents) return

        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.getErrorList()
            if (modLinkErrors.isError()) {
                modLinkErrors.showWarnOrErrorToast(screen)
                Concurrency.runOnGLThread { checkBox.isChecked = false } // Cancel event to reset to previous state
                return
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            if (complexModCheckReturnsErrors()) {
                // Cancel event to reset to previous state
                Concurrency.runOnGLThread { checkBox.isChecked = false } // Cancel event to reset to previous state
                mods.remove(mod.name)
                savedModcheckResult = null  // we just fixed it
                return
            }

        } else {
            /**
             * Turns out we need to check ruleset when REMOVING a mod as well, since if mod A references something in mod B (like a promotion),
             *   and then we remove mod B, then the entire ruleset is now broken!
             */

            mods.remove(mod.name)

            if (complexModCheckReturnsErrors()) {
                // Cancel event to reset to previous state
                Concurrency.runOnGLThread { checkBox.isChecked = true }
                mods.add(mod.name)
                savedModcheckResult = null  // we just fixed it
                return
            }

        }

        Concurrency.runOnGLThread { 
            disableIncompatibleMods()
            onUpdate(mod.name) // Only run if we can the checks and they succeeded
        }
    }

    /** Deselect incompatible mods after [skipCheckBox] was selected.
     *
     *  Note: Inactive - we don't even allow a conflict to be turned on using [disableIncompatibleMods].
     *  But if we want the alternative UX instead - use this in [checkBoxChanged] near `mods.add` and skip disabling...
     */
    private fun deselectIncompatibleMods(skipCheckBox: CheckBox?) {
        disableChangeEvents = true
        for (modWidget in modWidgets) {
            if (modWidget.widget == skipCheckBox) continue
            if (!ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())) {
                modWidget.widget.isChecked = false
                mods.remove(modWidget.mod.name)
            }
        }
        disableChangeEvents = false
    }

    /** Disable incompatible mods - those that could not be turned on with the current selection */
    private fun disableIncompatibleMods() {
        for (modWidget in modWidgets) {
            val enable = ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())
            if (!enable && modWidget.widget.isChecked) modWidget.widget.isChecked = false  // mod widgets can't, but selecting a map can cause this situation
            modWidget.widget.isDisabled = !enable  // isEnabled is only for TextButtons
        }
    }

    private fun getSelectedMods() =
        modWidgets.asSequence()
             .filter { it.widget.isChecked }
             .map { it.mod }
             .asIterable()

    fun changeGameParameters(newGameParameters: GameParameters) {
        mods = newGameParameters.mods
    }
}

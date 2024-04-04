package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.ModCompatibility
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A widget containing one expander for extension mods.
 * Manages compatibility checks, warns or prevents incompatibilities.
 *
 * @param mods In/out set of active mods, modified in place
 * @param initialBaseRuleset The selected base Ruleset, only for running mod checks against. Use [setBaseRuleset] to change on the fly.
 * @param screen Parent screen, used only to show [ToastPopup]s
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class ModCheckboxTable(
    private val mods: LinkedHashSet<String>,
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
    private val expanderPadOther = if (isPortrait) 0f else 10f

    init {
        val modRulesets = RulesetCache.values.filter {
            ModCompatibility.isExtensionMod(it)
        }

        for (mod in modRulesets.sortedBy { it.name }) {
            val checkBox = mod.name.toCheckBox(mod.name in mods)
            checkBox.onChange {
                if (checkBoxChanged(checkBox, it!!, mod)) {
                    onUpdate(mod.name)
                }
            }
            checkBox.left()
            modWidgets += ModWithCheckBox(mod, checkBox)
        }

        setBaseRuleset(initialBaseRuleset)
    }

    fun setBaseRuleset(newBaseRuleset: String) {
        baseRulesetName = newBaseRuleset
        savedModcheckResult = null
        clear()
        mods.clear()  // We'll regenerate this from checked widgets
        baseRuleset = RulesetCache[newBaseRuleset] ?: return

        val compatibleMods = modWidgets
            .filter { ModCompatibility.meetsBaseRequirements(it.mod, baseRuleset) }

        if (compatibleMods.none()) return

        for (mod in compatibleMods) {
            if (mod.widget.isChecked) mods += mod.mod.name
        }

        add(ExpanderTab("Extension mods", persistenceID = "NewGameExpansionMods") {
            it.defaults().pad(5f,0f)
            for (mod in compatibleMods) {
                it.add(mod.widget).row()
            }
        }).pad(expanderPadOther).padTop(expanderPadTop).growX().row()

        disableIncompatibleMods()

        runComplexModCheck()
    }

    fun disableAllCheckboxes() {
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.widget.isChecked = false
        }
        mods.clear()
        disableChangeEvents = false

        disableIncompatibleMods()
        onUpdate("-")  // should match no mod
    }

    private fun runComplexModCheck(): Boolean {
        // Disable user input to avoid ANRs
        val currentInputProcessor = Gdx.input.inputProcessor
        Gdx.input.inputProcessor = null

        // Check over complete combination of selected mods
        val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRulesetName)
        if (!complexModLinkCheck.isWarnUser()){
            savedModcheckResult = null
            Gdx.input.inputProcessor = currentInputProcessor
            return false
        }
        savedModcheckResult = complexModLinkCheck.getErrorText()
        complexModLinkCheck.showWarnOrErrorToast(screen)

        Gdx.input.inputProcessor = currentInputProcessor
        return complexModLinkCheck.isError()
    }

    private fun checkBoxChanged(
        checkBox: CheckBox,
        changeEvent: ChangeListener.ChangeEvent,
        mod: Ruleset
    ): Boolean {
        if (disableChangeEvents) return false

        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.checkModLinks()
            if (modLinkErrors.isError()) {
                modLinkErrors.showWarnOrErrorToast(screen)
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                return false
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            if (runComplexModCheck()) {
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                mods.remove(mod.name)
                savedModcheckResult = null  // we just fixed it
                return false
            }

        } else {
            /**
             * Turns out we need to check ruleset when REMOVING a mod as well, since if mod A references something in mod B (like a promotion),
             *   and then we remove mod B, then the entire ruleset is now broken!
             */

            mods.remove(mod.name)

            if (runComplexModCheck()) {
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                mods.add(mod.name)
                savedModcheckResult = null  // we just fixed it
                return false
            }

        }

        disableIncompatibleMods()

        return true
    }

    /** Deselect incompatible mods after [skipCheckBox] was selected.
     *
     *  Note: Inactive - we don'n even allow a conflict to be turned on using [disableIncompatibleMods].
     *  But if we want the alternative UX instead - use this in [checkBoxChanged] near `mods.add` and skip disabling...
     */
    @Suppress("unused")
    private fun deselectIncompatibleMods(skipCheckBox: CheckBox) {
        disableChangeEvents = true
        for (modWidget in modWidgets) {
            if (modWidget.widget == skipCheckBox) continue
            if (!ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())) {
                modWidget.widget.isChecked = false
                mods.remove(modWidget.mod.name)
            }
        }
        disableChangeEvents = true
    }

    /** Disable incompatible mods - those that could not be turned on with the current selection */
    private fun disableIncompatibleMods() {
        for (modWidget in modWidgets) {
            val enable = ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())
            assert(enable || !modWidget.widget.isChecked) { "Mod compatibility conflict: Trying to disable ${modWidget.mod.name} while it is selected" }
            modWidget.widget.isDisabled = !enable  // isEnabled is only for TextButtons
        }
    }

    private fun getSelectedMods() =
        modWidgets.asSequence()
             .filter { it.widget.isChecked }
             .map { it.mod }
             .asIterable()
}

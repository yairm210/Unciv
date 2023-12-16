package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
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

    init {
        val modRulesets = RulesetCache.values.filterNot {
            it.modOptions.isBaseRuleset
            || it.name.isBlank()
            || it.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)
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
            .filterNot { isIncompatible(it.mod, baseRuleset) }

        if (compatibleMods.none()) return

        for (mod in compatibleMods) {
            if (mod.widget.isChecked) mods += mod.mod.name
        }

        add(ExpanderTab("Extension mods", persistenceID = "NewGameExpansionMods") {
            it.defaults().pad(5f,0f)
            for (mod in compatibleMods) {
                it.add(mod.widget).row()
            }
        }).pad(10f).padTop(expanderPadTop).growX().row()
        // I think it's not necessary to uncheck the imcompatible (now invisible) checkBoxes

        runComplexModCheck()
    }

    fun disableAllCheckboxes() {
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.widget.isChecked = false
        }
        mods.clear()
        disableChangeEvents = false
        onUpdate("-")  // should match no mod
    }

    private fun runComplexModCheck(): Boolean {
        // Disable user input to avoid ANRs
        val currentInputProcessor = Gdx.input.inputProcessor
        Gdx.input.inputProcessor = null

        // Check over complete combination of selected mods
        val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRulesetName)
        if (!complexModLinkCheck.isWarnUser()){
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

        return true
    }

    private fun modNameFilter(modName: String, filter: String): Boolean {
        if (modName == filter) return true
        if (filter.length < 3 || !filter.startsWith('*') || !filter.endsWith('*')) return false
        val partialName = filter.substring(1, filter.length - 1).lowercase()
        return partialName in modName.lowercase()
    }

    private fun isIncompatibleWith(mod: Ruleset, otherMod: Ruleset) =
        mod.modOptions.getMatchingUniques(UniqueType.ModIncompatibleWith)
            .any { modNameFilter(otherMod.name, it.params[0]) }
    private fun isIncompatible(mod: Ruleset, otherMod: Ruleset) =
        isIncompatibleWith(mod, otherMod) || isIncompatibleWith(otherMod, mod)

}

package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.newgamescreen.TranslatedSelectBox
import com.unciv.ui.utils.*

class MapEditorModsTab(
    private val editorScreen: MapEditorScreenV2
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private var mods = editorScreen.newMapParameters.mods
    private var modsTable: ModCheckboxTable
    private val modsTableCell: Cell<ModCheckboxTable>
    private val applyButton = "Change ruleset".toTextButton()
    private val baseRulesetSelectBox: TranslatedSelectBox

    init {
        val rulesetName = editorScreen.newMapParameters.baseRuleset
        // Out dirty flag `modsTabNeedsRefresh` will be true on first activation,
        // so this will be replaced and can now be minimal
        modsTable = ModCheckboxTable(linkedSetOf(), rulesetName, editorScreen, false) {}

        val baseRulesets = RulesetCache.getSortedBaseRulesets()
        baseRulesetSelectBox = TranslatedSelectBox(baseRulesets, rulesetName, BaseScreen.skin)
        baseRulesetSelectBox.onChange {
            editorScreen.newMapParameters.baseRuleset = baseRulesetSelectBox.selected.value
            enableApplyButton()
        }

        top()
        pad(5f)

        add(Table().apply {
            add("{Base Ruleset}:".toLabel())
            add(baseRulesetSelectBox).fillX()
        }).fillX().padBottom(10f).row()
        add(applyButton).pad(10f).row()
        modsTableCell = add(modsTable)
        row()

        applyButton.onClick {
            val newRuleset = RulesetCache.getComplexRuleset(mods, editorScreen.newMapParameters.baseRuleset)
            val incompatibilities = getIncompatibilities(newRuleset)
            if (incompatibilities.isEmpty()) {
                editorScreen.applyRuleset(newRuleset, editorScreen.newMapParameters.baseRuleset, mods)
                enableApplyButton()
            } else {
                AskFitMapToRulesetPopup(editorScreen, incompatibilities) {
                    fitMapToRuleset(newRuleset)
                    editorScreen.applyRuleset(newRuleset, editorScreen.newMapParameters.baseRuleset, mods)
                    enableApplyButton()
                }
            }
        }
    }

    private fun enableApplyButton() {
        val currentParameters = editorScreen.tileMap.mapParameters
        applyButton.isEnabled =
            currentParameters.mods != mods ||
            currentParameters.baseRuleset != baseRulesetSelectBox.selected.value
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        enableApplyButton()
        if (!editorScreen.modsTabNeedsRefresh) return
        val currentParameters = editorScreen.tileMap.mapParameters
        baseRulesetSelectBox.setSelected(currentParameters.baseRuleset)
        mods = currentParameters.mods
        modsTable = ModCheckboxTable(mods, currentParameters.baseRuleset, editorScreen, false) {
            enableApplyButton()
        }
        modsTableCell.setActor(modsTable)
    }

    private fun getIncompatibilities(newRuleset: Ruleset): List<String> {
        val incompatibilities = HashSet<String>()
        for (tile in editorScreen.tileMap.values) {
            incompatibilities += tile.getRulesetIncompatibility(newRuleset)
        }
        incompatibilities.remove("")
        return incompatibilities.sorted()
    }

    private class AskFitMapToRulesetPopup(
        editorScreen: BaseScreen,
        incompatibilities: List<String>,
        onOK: () -> Unit
    ): Popup(editorScreen) {
        init {
            val incompatibilityTable = Table().apply {
                for (inc in incompatibilities)
                    add(inc.toLabel()).row()
            }
            add(ScrollPane(incompatibilityTable)).colspan(2)
                .maxHeight(screen.stage.height * 0.8f).row()
            addGoodSizedLabel("Change map to fit selected ruleset?", 24).colspan(2).row()
            addButtonInRow(Constants.yes, 'y') {
                onOK()
                close()
            }
            addButtonInRow(Constants.no, 'n') { close() }
            equalizeLastTwoButtonWidths()
            open(true)
        }
    }

    private fun fitMapToRuleset(newRuleset: Ruleset) {
        for (tile in editorScreen.tileMap.values)
            tile.normalizeToRuleset(newRuleset)
    }
}

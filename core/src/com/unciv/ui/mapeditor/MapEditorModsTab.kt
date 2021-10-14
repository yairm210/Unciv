package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.utils.*

class MapEditorModsTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private var mods = editorScreen.newMapParameters.mods
    private var modsTable: ModCheckboxTable
    private val modsTableCell: Cell<ModCheckboxTable>
    private val applyButton = "Change ruleset".toTextButton()

    init {
        modsTable = ModCheckboxTable(mods, editorScreen, false) {
            enableApplyButton()
        }

        top()
        pad(5f)
        add(applyButton).pad(10f).row()
        modsTableCell = add(modsTable)
        row()

        applyButton.onClick {
            val newRuleset = RulesetCache.getComplexRuleset(mods)
            val incompatibilities = getIncompatibilities(newRuleset)
            if (incompatibilities.isEmpty()) {
                editorScreen.applyRuleset(newRuleset)
            } else {
                AskFitMapToRulesetPopup(editorScreen, incompatibilities) {
                    fitMapToRuleset(newRuleset)
                    editorScreen.applyRuleset(newRuleset)
                }
            }
        }
    }

    private fun enableApplyButton() {
        applyButton.isEnabled = editorScreen.tileMap.mapParameters.mods != mods
    }
    override fun activated(index: Int) {
        enableApplyButton()
        if (!editorScreen.modsTabNeedsRefresh) return
        mods = editorScreen.tileMap.mapParameters.mods
        modsTable = ModCheckboxTable(mods, editorScreen, false) {
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
        editorScreen: CameraStageBaseScreen,
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

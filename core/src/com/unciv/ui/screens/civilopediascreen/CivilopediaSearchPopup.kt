package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.TutorialController
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.badlogic.gdx.utils.Array as GdxArray

class CivilopediaSearchPopup(
    private val pediaScreen: CivilopediaScreen,
    private val tutorialController: TutorialController,
    private val linkAction: (String) -> Unit
) : Popup(pediaScreen) {
    private var ruleset = pediaScreen.ruleset
    private val searchText = UncivTextField.create("Search text")
    private val modSelect = ModSelectBox()
    private lateinit var resultExpander: ExpanderTab
    private val resultCell: Cell<Actor?>
    private val searchButton: TextButton

    private var searchJob: Job? = null
    private var checkLine: (String) -> Boolean = { _ -> false }

    init {
        searchText.maxLength = 100

        add("{Search text}:".toLabel())
        add(searchText).growX().row()
        add("Mod filter:".toLabel())
        add(modSelect).growX().row()
        resultCell = add().colspan(2).growX()
        row()

        searchButton = addButton("Search!", KeyCharAndCode.RETURN) {
            startSearch(searchText.text)
        }.actor
        addCloseButton()
        showListeners.add {
            keyboardFocus = searchText
            searchText.selectAll()
        }
        closeListeners.add {
            if (isSearchRunning()) searchJob!!.cancel()
        }
    }

    private fun isSearchRunning() = searchJob?.isActive == true

    private fun startSearch(text: String) {
        searchButton.disable()

        @Suppress("LiftReturnOrAssignment")
        if (text.isEmpty()) {
            checkLine = { true }
        } else if (".*" in text || '\\' in text || '|' in text) {
            try {
                val regex = Regex(text, RegexOption.IGNORE_CASE)
                checkLine = { regex.containsMatchIn(it) }
            } catch (ex: Exception) {
                ToastPopup("That's an invalid regular expression.", pediaScreen, 4000).open(true)
                searchButton.enable()
                return
            }
        } else {
            val words = text.split(' ').toSet()
            checkLine = { line -> words.all { line.contains(it, ignoreCase = true) } }
        }

        ruleset = modSelect.selectedRuleset() ?: return

        if (::resultExpander.isInitialized) {
            resultExpander.innerTable.clear()
        } else {
            resultExpander = ExpanderTab("Results") {}
            resultCell.setActor(resultExpander)
            resultExpander.innerTable.defaults().left().growX().pad(2f)
        }

        searchJob = Concurrency.run("PediaSearch") {
            searchLoop()
        }.apply {
            invokeOnCompletion {
                searchJob = null
                Concurrency.runOnGLThread {
                    if (resultExpander.innerTable.cells.isEmpty)
                        resultExpander.innerTable.add(
                            FormattedLine("Nothing found!", color = "#f53", header = 3, centered = true)
                            .render(0f))
                    searchButton.enable()
                }
            }
        }
    }

    private fun CoroutineScope.searchLoop() {
        for (category in CivilopediaCategories.values()) {
            if (!isActive) break
            if (category.hide) continue
            if (!ruleset.modOptions.isBaseRuleset && category == CivilopediaCategories.Tutorial) continue
            for (entry in category.getCategoryIterator(ruleset, tutorialController)) {
                if (!isActive) break
                if (entry !is INamed) continue
                if (!ruleset.modOptions.isBaseRuleset) {
                    val sort = entry.getSortGroup(ruleset)
                    if (category == CivilopediaCategories.UnitType && sort < 2) continue
                    if (category == CivilopediaCategories.Belief && sort == 0) continue
                }
                searchEntry(entry)
            }
        }
    }

    private fun CoroutineScope.searchEntry(entry: ICivilopediaText) {
        val scope = sequence {
            entry.getCivilopediaTextHeader()?.let { yield(it) }
            yieldAll(entry.civilopediaText)
            yieldAll(entry.getCivilopediaTextLines(ruleset))
        }
        for (line in scope) {
            if (!isActive) break
            val lineText = line.text.tr(hideIcons = true)
            if (!checkLine(lineText)) continue
            addResult(entry)
            break
        }
    }

    private fun CoroutineScope.addResult(entry: ICivilopediaText) {
        launchOnGLThread {
            val actor = entry.getIconName().toLabel(alignment = Align.left)
            val link = entry.makeLink()
            resultExpander.innerTable.add(actor).row()
            actor.onClick {
                linkAction(link)
                close()
            }
        }
    }

    class ModSelectEntry(val key: String, val translate: Boolean = false) {
        override fun toString() = if (translate) key.tr() else key
    }
    private inner class ModSelectBox : SelectBox<ModSelectEntry>(BaseScreen.skin) {
        init {
            val mods = pediaScreen.ruleset.mods
            val entries = GdxArray<ModSelectEntry>(mods.size + 1)
            entries.add(ModSelectEntry("-Combined-", true))
            for (mod in mods) entries.add(ModSelectEntry(mod))
            items = entries
            selectedIndex = 0
        }

        fun selectedRuleset(): Ruleset? =
            if (selectedIndex == 0) pediaScreen.ruleset
            else RulesetCache[selected.key]
    }
}

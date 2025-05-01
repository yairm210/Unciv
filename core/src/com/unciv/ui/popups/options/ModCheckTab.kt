package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueAutoUpdater
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.UniqueBuilderScreen
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread


private const val MOD_CHECK_WITHOUT_BASE = "-none-"

class ModCheckTab(
    val screen: BaseScreen
) : Table(), TabbedPager.IPageExtensions {
    private val fixedContent = Table()

    // marker for automatic first run on selecting the tab
    private var modCheckFirstRun = true

    private var modCheckBaseSelect: TranslatedSelectBox? = null
    private val searchModsTextField: UncivTextField
    private val modCheckResultTable = Table()

    init {
        defaults().pad(10f).align(Align.top)

        fixedContent.defaults().pad(10f).align(Align.top)
        val reloadModsButton = "Reload mods".toTextButton().onClick(::runAction)
        fixedContent.add(reloadModsButton).row()
        searchModsTextField = UncivTextField("Seach for mods")
        searchModsTextField.onChange { runAction() }
        fixedContent.add(searchModsTextField).fillX() .row()

        val labeledBaseSelect = Table().apply {
            add("Check extension mods based on:".toLabel()).padRight(10f)
            val baseMods = listOf(MOD_CHECK_WITHOUT_BASE) + RulesetCache.getSortedBaseRulesets()
            modCheckBaseSelect = TranslatedSelectBox(baseMods, MOD_CHECK_WITHOUT_BASE).apply {
                selectedIndex = 0
                onChange { runAction() }
            }
            add(modCheckBaseSelect)
        }
        fixedContent.add(labeledBaseSelect).row()

        add(modCheckResultTable)
    }

    private fun runAction() {
        if (modCheckFirstRun) runModChecker()
        else runModChecker(modCheckBaseSelect!!.selected.value)
    }

    override fun getFixedContent() = fixedContent

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        runAction()
    }

    private fun runModChecker(base: String = MOD_CHECK_WITHOUT_BASE) {

        modCheckFirstRun = false
        if (modCheckBaseSelect == null) return

        val openedExpanderTitles = modCheckResultTable.children.filterIsInstance<ExpanderTab>()
            .filter { it.isOpen }.map { it.title }.toSet()

        modCheckResultTable.clear()

        val rulesetErrors = RulesetCache.loadRulesets()
        if (rulesetErrors.isNotEmpty()) {
            val errorTable = Table().apply { defaults().pad(2f) }
            for (rulesetError in rulesetErrors)
                errorTable.add(rulesetError.toLabel()).width(stage.width / 2).row()
            modCheckResultTable.add(errorTable)
        }

        modCheckResultTable.add("Checking mods for errors...".toLabel()).row()
        modCheckBaseSelect!!.isDisabled = true

        Concurrency.run("ModChecker") {
            for (mod in RulesetCache.values
                .filter { it.name.lowercase().contains(searchModsTextField.text.lowercase()) }
                .sortedBy { it.name }.sortedByDescending { it.name in openedExpanderTitles }) {
                if (base != MOD_CHECK_WITHOUT_BASE && mod.modOptions.isBaseRuleset) continue

                val modLinks =
                    if (base == MOD_CHECK_WITHOUT_BASE) mod.getErrorList(tryFixUnknownUniques = true)
                    else RulesetCache.checkCombinedModLinks(linkedSetOf(mod.name), base, tryFixUnknownUniques = true)
                modLinks.sortByDescending { it.errorSeverityToReport }
                val noProblem = !modLinks.isNotOK()
                if (modLinks.isNotEmpty()) modLinks.add("", RulesetErrorSeverity.OK, sourceObject = null)
                if (noProblem) modLinks.add("No problems found.".tr(), RulesetErrorSeverity.OK, sourceObject = null)

                launchOnGLThread {
                    // When the options popup is already closed before this postRunnable is run,
                    // Don't add the labels, as otherwise the game will crash
                    if (stage == null) return@launchOnGLThread
                    // Don't just render text, since that will make all the conditionals in the mod replacement messages move to the end, which makes it unreadable
                    // Don't use .toLabel() either, since that activates translations as well, which is what we're trying to avoid,
                    // Instead, some manual work needs to be put in.

                    val iconColor = modLinks.getFinalSeverity().color
                    val iconName = when (iconColor) {
                        Color.RED -> "OtherIcons/Stop"
                        Color.YELLOW -> "OtherIcons/ExclamationMark"
                        else -> "OtherIcons/Checkmark"
                    }
                    val icon = ImageGetter.getImage(iconName)
                        .apply { color = ImageGetter.CHARCOAL }
                        .surroundWithCircle(30f, color = iconColor)

                    val expanderTab = ExpanderTab(mod.name, icon = icon, startsOutOpened = mod.name in openedExpanderTitles) {
                        it.defaults().align(Align.left)
                        it.defaults().pad(10f)

                        val openUniqueBuilderButton = "Open unique builder".toTextButton()
                        val ruleset = if (base == MOD_CHECK_WITHOUT_BASE) mod
                        else RulesetCache.getComplexRuleset(linkedSetOf(mod.name), base)
                        openUniqueBuilderButton.onClick { UncivGame.Current.pushScreen(UniqueBuilderScreen(ruleset)) }
                        it.add(openUniqueBuilderButton).row()

                        if (!noProblem && mod.folderLocation != null) {
                            val replaceableUniques = UniqueAutoUpdater.getDeprecatedReplaceableUniques(mod)
                            if (replaceableUniques.isNotEmpty())
                                it.add("Autoupdate mod uniques".toTextButton()
                                    .onClick { autoUpdateUniques(screen, mod, replaceableUniques) }).row()
                        }
                        for (line in modLinks) {
                            val label = Label(line.text, BaseScreen.skin)
                                .apply { color = line.errorSeverityToReport.color }
                            label.wrap = true
                            it.add(label).width(stage.width / 2).row()
                        }
                        if (!noProblem)
                            it.add("Copy to clipboard".toTextButton().onClick {
                                Gdx.app.clipboard.contents = modLinks
                                    .joinToString("\n") { line -> line.text }
                            }).row()
                    }
                    expanderTab.header.left()

                    val loadingLabel = modCheckResultTable.children.last()
                    modCheckResultTable.removeActor(loadingLabel)
                    modCheckResultTable.add(expanderTab).row()
                    modCheckResultTable.add(loadingLabel).row()
                }
            }

            // done with all mods!
            launchOnGLThread {
                modCheckResultTable.removeActor(modCheckResultTable.children.last())
                modCheckBaseSelect!!.isDisabled = false
            }
        }
    }


    private fun autoUpdateUniques(screen: BaseScreen, mod: Ruleset, replaceableUniques: HashMap<String, String>) {
        UniqueAutoUpdater.autoupdateUniques(mod, replaceableUniques)
        val toastText = "Uniques updated!"
        ToastPopup(toastText, screen)
        runModChecker()
    }

}


package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueAutoUpdater
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.UniqueBuilderScreen
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive


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
    private val loadingImage = LoadingImage(48f, LoadingImage.Style(loadingColor = Color.SCARLET))
    /** Used to repopulate modCheckResultTable when searching */
    private val modResultExpanderTabs = ArrayList<ExpanderTab>()

    private var runningCheck: Job? = null
    private var checkedFilter = ""
    private var currentFilter = ""

    init {
        defaults().pad(10f).align(Align.top)

        fixedContent.defaults().pad(10f).align(Align.top)
        val reloadModsButton = "Reload mods".toTextButton().onClick(::runAction)
        fixedContent.add().width(48f) // placeholder to balance out loadingImage
        fixedContent.add(reloadModsButton).center().expandX()
        fixedContent.add(loadingImage).row()

        searchModsTextField = UncivTextField("Search mods")
        searchModsTextField.onChange { changeSearch() }

        if (RulesetCache.size > 10)
            fixedContent.add(searchModsTextField).colspan(3).fillX().row()

        val labeledBaseSelect = Table().apply {
            add("Check extension mods based on:".toLabel()).padRight(10f)
            val baseMods = listOf(MOD_CHECK_WITHOUT_BASE) + RulesetCache.getSortedBaseRulesets()
            modCheckBaseSelect = TranslatedSelectBox(baseMods, MOD_CHECK_WITHOUT_BASE).apply {
                selectedIndex = 0
                onChange { runAction() }
            }
            add(modCheckBaseSelect)
        }
        fixedContent.add(labeledBaseSelect).colspan(3).row()

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

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        cancelJob()
    }

    private fun cancelJob() {
        val job = runningCheck ?: return
        runningCheck = null
        loadingImage.hide()
        job.cancel()
    }

    private fun runModChecker(base: String = MOD_CHECK_WITHOUT_BASE) {
        cancelJob()

        modCheckFirstRun = false
        if (modCheckBaseSelect == null) return

        loadingImage.show()

        val openedExpanderTitles = modResultExpanderTabs
            .filter { it.isOpen }.map { it.title }.toSet()

        modCheckResultTable.clear()
        modResultExpanderTabs.clear()

        val loadingErrors = RulesetCache.loadRulesets()
        if (loadingErrors.isNotEmpty()) {
            val errorTable = Table().apply { defaults().pad(2f) }
            for (loadingError in loadingErrors)
                errorTable.add(loadingError.toLabel()).width(stage.width / 2).row()
            modCheckResultTable.add(errorTable)
        }

        runningCheck = Concurrency.run("ModChecker") {
            checkedFilter = searchModsTextField.text
            currentFilter = checkedFilter
            val modsToCheck = RulesetCache.values
                .filter { it.name.contains(searchModsTextField.text, ignoreCase = true) }
                .sortedWith(
                    compareByDescending<Ruleset> { it.name in openedExpanderTitles }
                        .thenBy { it.name }
                )
            for (mod in modsToCheck) {
                if (base != MOD_CHECK_WITHOUT_BASE && mod.modOptions.isBaseRuleset) continue
                if (!isActive) break

                val modLinks =
                    if (base == MOD_CHECK_WITHOUT_BASE) mod.getErrorList(tryFixUnknownUniques = true)
                    else RulesetCache.checkCombinedModLinks(linkedSetOf(mod.name), base, tryFixUnknownUniques = true)
                if (!isActive) break

                modLinks.sortByDescending { it.errorSeverityToReport }
                if (modLinks.isNotEmpty()) modLinks.add("", RulesetErrorSeverity.OK, sourceObject = null)
                if (!modLinks.isNotOK()) modLinks.add("No problems found.".tr(), RulesetErrorSeverity.OK, sourceObject = null)

                launchOnGLThread {
                    addNextModResult(mod, base, modLinks, mod.name in openedExpanderTitles)
                }
            }

            // done with all mods!
            launchOnGLThread {
                loadingImage.hide()
            }
        }
    }

    private fun changeSearch() {
        val searchFilter = searchModsTextField.text
        if (searchFilter.contains(checkedFilter, ignoreCase = true)) {
            // The last check, whether finished or not, included all mods we want to filter
            synchronized(modCheckResultTable) {
                modCheckResultTable.clear()
                for (expanderTab in modResultExpanderTabs) {
                    if (expanderTab.title.contains(searchFilter, ignoreCase = true))
                        modCheckResultTable.add(expanderTab).row()
                }
            }
        } else {
            // the filter is wider than the last check - rerun
            runAction()
        }
    }

    private fun addNextModResult(mod: Ruleset, base: String, modLinks: RulesetErrorList, startsOutOpened: Boolean) {
        // When the options popup is already closed before this postRunnable is run,
        // Don't add the labels, as otherwise the game will crash
        if (stage == null) return
        // Don't just render text, since that will make all the conditionals in the mod replacement messages move to the end, which makes it unreadable
        // Don't use .toLabel() either, since that activates translations as well, which is what we're trying to avoid,
        // Instead, some manual work needs to be put in.

        val severity = modLinks.getFinalSeverity()
        val icon = ImageGetter.getImage(severity.iconName)
            .apply { color = ImageGetter.CHARCOAL }
            .surroundWithCircle(30f, color = severity.color)

        val expanderTab = ExpanderTab(mod.name, icon = icon, startsOutOpened = startsOutOpened) {
            it.defaults().align(Align.left)
            it.defaults().pad(10f)

            val openUniqueBuilderButton = "Open unique builder".toTextButton()
            openUniqueBuilderButton.onClick { openUniqueBuilder(mod, base) }
            it.add(openUniqueBuilderButton).row()

            if (severity != RulesetErrorSeverity.OK && mod.folderLocation != null) {
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
            if (severity != RulesetErrorSeverity.OK)
                it.add("Copy to clipboard".toTextButton().onClick {
                    Gdx.app.clipboard.contents = modLinks
                        .joinToString("\n") { line -> line.text }
                }).row()
        }
        expanderTab.header.left()

        synchronized(modCheckResultTable) {
            modResultExpanderTabs.add(expanderTab)
            modCheckResultTable.add(expanderTab).row()
        }
    }

    private fun openUniqueBuilder(mod: Ruleset, base: String) {
        val ruleset = if (base == MOD_CHECK_WITHOUT_BASE) mod
            else RulesetCache.getComplexRuleset(linkedSetOf(mod.name), base)
        UncivGame.Current.pushScreen(UniqueBuilderScreen(ruleset))
    }

    private fun autoUpdateUniques(screen: BaseScreen, mod: Ruleset, replaceableUniques: HashMap<String, String>) {
        UniqueAutoUpdater.autoupdateUniques(mod, replaceableUniques)
        val toastText = "Uniques updated!"
        ToastPopup(toastText, screen)
        runAction()
    }

}

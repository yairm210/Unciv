package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.ModCompatibility
import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueAutoUpdater
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.setSize
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
private const val MOD_CHECK_DYNAMIC_BASE = "-declared requirements-"

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

    /** The search filter the check was started with*/
    private var checkedFilter = ""
    /** The current search filter, kept up to date in the `TextField.onChange` event*/
    private var currentFilter = ""

    private val emptyRuleset = Ruleset()

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
            val baseMods = listOf(MOD_CHECK_WITHOUT_BASE, MOD_CHECK_DYNAMIC_BASE) + RulesetCache.getSortedBaseRulesets()
            modCheckBaseSelect = TranslatedSelectBox(baseMods, MOD_CHECK_DYNAMIC_BASE).apply {
                selectedIndex = 1
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
        endJob()
        job.cancel()
    }

    private fun endJob() {
        runningCheck = null
        loadingImage.hide()
    }

    private fun runModChecker(base: String = MOD_CHECK_DYNAMIC_BASE) {
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
                .filter { it.name.filterApplies() }
                .sortedWith(
                    compareByDescending<Ruleset> { it.name in openedExpanderTitles }
                        .thenBy { it.name }
                )
            for (mod in modsToCheck) {
                val dynamicBase = base == MOD_CHECK_DYNAMIC_BASE
                val baseForThisMod = if (dynamicBase) getBaseForMod(mod) else base
                val shouldCheck = if (baseForThisMod == null) false else shouldCheckMod(mod, baseForThisMod)
                if (baseForThisMod == null || dynamicBase && !shouldCheck) {
                    // Don't check, but since this is the default view, show greyed out, so people don't wonder where their mods are
                    launchOnGLThread {
                        addDisabledPlaceholder(mod)
                    }
                    continue
                }
                if (!shouldCheck) continue
                if (!isActive) break

                val modLinks =
                    if (baseForThisMod == MOD_CHECK_WITHOUT_BASE) mod.getErrorList(tryFixUnknownUniques = true)
                    else RulesetCache.checkCombinedModLinks(linkedSetOf(mod.name), baseForThisMod, tryFixUnknownUniques = true)
                if (!isActive) break

                modLinks.sortByDescending { it.errorSeverityToReport }
                if (modLinks.isNotEmpty()) modLinks.add("", RulesetErrorSeverity.OK, sourceObject = null)
                if (!modLinks.isNotOK()) modLinks.add("No problems found.".tr(), RulesetErrorSeverity.OK, sourceObject = null)

                launchOnGLThread {
                    addModResult(mod, baseForThisMod, modLinks, mod.name in openedExpanderTitles)
                }
            }

            // done with all mods!
            launchOnGLThread {
                endJob()
            }
        }
    }

    private fun changeSearch() {
        currentFilter = searchModsTextField.text
        if (currentFilter.contains(checkedFilter, ignoreCase = true)) {
            // The last check, whether finished or not, included all mods we want to filter
            synchronized(modCheckResultTable) {
                modCheckResultTable.clear()
                for (expanderTab in modResultExpanderTabs) {
                    if (expanderTab.title.filterApplies())
                        modCheckResultTable.add(expanderTab).row()
                }
            }
        } else {
            // the filter is wider than the last check - rerun
            runAction()
        }
    }

    private fun String.filterApplies() = contains(currentFilter, ignoreCase = true)

    /** Use the declarative mod compatibility Uniques to omit meaningless check combos */
    private fun shouldCheckMod(mod: Ruleset, base: String): Boolean {
        if (mod.modOptions.isBaseRuleset) return base == MOD_CHECK_WITHOUT_BASE
        if (ModCompatibility.isAudioVisualMod(mod)) return true
        // Very borderline case: DO check mods with invalid requirements. This duplicates a tiny part of RulesetValidator,
        // but ends up simpler than calling the full validator and filtering for a specific message.
        if (mod.modOptions.getMatchingUniques(UniqueType.ModRequires).any {
                UniqueParameterType.ModName.getErrorSeverity(it.params[0], mod) != null
            }) return true
        val baseRuleset = RulesetCache[base] ?: emptyRuleset  // MOD_CHECK_WITHOUT_BASE compares compatibility against an empty Ruleset
        return ModCompatibility.meetsBaseRequirements(mod, baseRuleset)  // yes this returns true for mods ignoring declarative compatibility
    }

    private fun getBaseForMod(mod: Ruleset): String? {
        if (mod.modOptions.isBaseRuleset || ModCompatibility.isAudioVisualMod(mod) || ModCompatibility.isConstantsOnly(mod))
            return MOD_CHECK_WITHOUT_BASE
        if (!mod.modOptions.hasUnique(UniqueType.ModRequires)) return null
        return RulesetCache.values
            .filter { it.modOptions.isBaseRuleset }
            .firstOrNull { ModCompatibility.meetsBaseRequirements(mod, it) }
            ?.name
    }

    private fun addModResult(mod: Ruleset, base: String, modLinks: RulesetErrorList, startsOutOpened: Boolean) {
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
            if (mod.name.filterApplies())
                modCheckResultTable.add(expanderTab).row()
        }
    }

    private fun addDisabledPlaceholder(mod: Ruleset) {
        if (stage == null) return
        val table = Table(BaseScreen.skin).apply {
            defaults().pad(8f)
            background = BaseScreen.skinStrings.getUiBackground("General/DisabledBox", tintColor = Color.DARK_GRAY)
            add(ImageGetter.getImage("OtherIcons/Question").apply { setSize(33f) }).padRight(10f).growY()
            add(mod.name.toLabel(Color.LIGHT_GRAY, Constants.headingFontSize, alignment = Align.left)).left().grow()
            addTooltip("Requirements could not be determined.\nChoose a base to check this Mod.", 16f, targetAlign = Align.top)
        }
        synchronized(modCheckResultTable) {
            modCheckResultTable.add(table).growX().row()
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

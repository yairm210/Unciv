package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.IconCircleButton
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

/** Screen displaying the Civilopedia
 * @param ruleset [Ruleset] to display items from
 * @param historyEntry Navigates to that entry without adding to the history. Passing null has undefined effect on navigation.
 */
class CivilopediaScreen private constructor(
    val ruleset: Ruleset,
    historyEntry: CivilopediaHistory.Entry?
) : BaseScreen(), RecreateOnResize {
    /** Screen displaying the Civilopedia
     * @param ruleset [Ruleset] to display items from
     * @param category [CivilopediaCategories] key to select category
     * @param link alternate selector to select category and/or entry. Can have the form `category/entry`
     *             overriding the [category] parameter, or just `entry` to complement it.
     */
    constructor(ruleset: Ruleset, category: CivilopediaCategories = CivilopediaCategories.Tutorial, link: String = "" ) : this(ruleset, null) {
        navigateToDefault(category, link)
    }

    //region !Fields
    /** Container collecting data per Civilopedia entry
     * @property name From [Ruleset] object [INamed.name]
     * @property image Icon for button
     * @property flavour Original [ICivilopediaText] reference allowing to render its [ICivilopediaText.civilopediaText]
     * @property sortBy Optional, enables overriding alphabetical order. Ususally supplied by [ICivilopediaText.getSortGroup]
     * @property subCategory Optional, enablesg grouping with sub-category labels. Ususally supplied by [ICivilopediaText.getSubCategory]
     */
    private class CivilopediaEntry (
        val name: String,
        val image: Actor? = null,
        val flavour: ICivilopediaText? = null,
        val sortBy: Int = 0,
        val subCategory: String? = null
    ) {
        constructor(ruleset: Ruleset, item: ICivilopediaText, category: CivilopediaCategories, imageSize: Float) : this(
            (item as INamed).name,
            category.getImage?.invoke(item.getIconName(), imageSize),
            flavour = item,
            sortBy = item.getSortGroup(ruleset),
            subCategory = item.getSubCategory(ruleset)
        )
    }

    private val categoryToEntries = LinkedHashMap<CivilopediaCategories, Collection<CivilopediaEntry>>()
    private class CategoryButtonInfo(val button: Button, val x: Float, val width: Float)
    private val categoryToButtons = LinkedHashMap<CivilopediaCategories, CategoryButtonInfo>()
    private lateinit var entryIndex: IndexedMap<String, CivilopediaEntry>

    private val buttonTableScroll: ScrollPane

    private val entrySelectTable = Table().apply { defaults().pad(6f).left() }
    private val entrySelectScroll: ScrollPane
    private val flavourTable = Table()
    private val flavourScroll: ScrollPane

    private var currentCategory: CivilopediaCategories = CivilopediaCategories.Tutorial
    private val currentEntry get() = currentEntryPerCategory[currentCategory] ?: ""
    private val currentEntryPerCategory = HashMap<CivilopediaCategories, String>()
    private val backButton: IconCircleButton

    private val searchPopup by lazy { CivilopediaSearchPopup(this) {
        navigateToLink(it)
    } }

    //endregion

    init {
        CivilopediaHistory.setContext(ruleset)
        val imageSize = 50f

        val religionEnabled = showReligionInCivilopedia(ruleset) // To filter the Belief Category only

        // do not confuse with IConstruction.shouldBeDisplayed - that one tests all prerequisites for building
        fun shouldBeDisplayed(obj: ICivilopediaText) =
            obj !is IHasUniques || !obj.isHiddenFromCivilopedia(game.gameInfo, ruleset)

        for (loopCategory in CivilopediaCategories.entries) {
            if (!religionEnabled && loopCategory == CivilopediaCategories.Belief) continue
            categoryToEntries[loopCategory] =
                loopCategory.getCategoryIterator(ruleset, game.gameInfo)
                    .filter(::shouldBeDisplayed)
                    .map { CivilopediaEntry(ruleset, it, loopCategory, imageSize) }
        }

        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)

        var currentX = 10f  // = padLeft
        for ((categoryKey, entries) in categoryToEntries) {
            if (entries.isEmpty()) continue
            val icon = if (categoryKey.headerIcon.isNotEmpty()) ImageGetter.getImage(categoryKey.headerIcon) else null
            val button = IconTextButton(categoryKey.label, icon)
            button.onActivation(binding = categoryKey.binding) { navigateCategory(categoryKey) }
            val cell = buttonTable.add(button)
            categoryToButtons[categoryKey] = CategoryButtonInfo(button, currentX, cell.prefWidth)
            currentX += cell.prefWidth + 20f
        }

        buttonTable.pack()
        buttonTableScroll = ScrollPane(buttonTable)
        buttonTableScroll.setScrollingDisabled(x = false, y = true)

        backButton = "OtherIcons/ArrowLeft".toImageButton(imageSize - 16f, imageSize, skinStrings.skinConfig.baseColor, Color.ROYAL)
        backButton.onActivation(binding = KeyboardBinding.PediaBack) {
            CivilopediaHistory.back(::isValidHistoryEntry) { navigateToHistoryEntry(it) }
        }
        fun openHistory() {
            CivilopediaHistoryMenu(stage, backButton, ::isValidHistoryEntry) { navigateToHistoryEntry(it) }
        }
        backButton.onActivation(ActivationTypes.RightClick, action = ::openHistory)
        backButton.keyShortcuts.add(KeyboardBinding.PediaHistory, action = ::openHistory)
        updateNavigationState()

        val searchButton = "OtherIcons/Search".toImageButton(imageSize - 16f, imageSize, skinStrings.skinConfig.baseColor, Color.GOLD)
        searchButton.onActivation(binding = KeyboardBinding.PediaSearch) { searchPopup.open(true) }

        val closeButton = getCloseButton(imageSize) {
            updateCurrentHistoryScrollY()
            game.popScreen()
        }

        val topTable = Table()
        topTable.add(buttonTableScroll).growX()
        topTable.add(backButton).padLeft(10f)
        topTable.add(searchButton).padLeft(10f)
        topTable.add(closeButton).padLeft(10f).padRight(10f)
        topTable.width = stage.width
        topTable.layout()

        val entryTable = Table()
        val splitPane = SplitPane(topTable, entryTable, true, skin)
        splitPane.splitAmount = topTable.prefHeight / stage.height
        entryTable.height = stage.height - topTable.prefHeight
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        entrySelectScroll = ScrollPane(entrySelectTable)
        entrySelectTable.top()
        entrySelectScroll.setOverscroll(false, false)
        val descriptionTable = Table()
        descriptionTable.add(flavourTable).padTop(7f).padBottom(5f).row()  // 2f of that 7f is used up by Portrait painting e.g. a Nation's outer border *outside its bounds*
        flavourScroll = ScrollPane(descriptionTable)
        val entrySplitPane = SplitPane(entrySelectScroll, flavourScroll, false, skin)
        entrySplitPane.splitAmount = 0.3f
        entryTable.addActor(entrySplitPane)
        entrySplitPane.setFillParent(true)
        entrySplitPane.pack()  // ensure selectEntry has correct entrySelectScroll.height and maxY

        globalShortcuts.add(Input.Keys.LEFT) { navigateCategories(-1) }
        globalShortcuts.add(Input.Keys.RIGHT) { navigateCategories(1) }
        globalShortcuts.add(Input.Keys.UP) { navigateEntries(-1) }
        globalShortcuts.add(Input.Keys.DOWN) { navigateEntries(1) }
        globalShortcuts.add(Input.Keys.PAGE_UP) { navigateEntries(-10) }
        globalShortcuts.add(Input.Keys.PAGE_DOWN) { navigateEntries(10) }
        globalShortcuts.add(Input.Keys.HOME) { navigateEntries(Int.MIN_VALUE) }
        globalShortcuts.add(Input.Keys.END) { navigateEntries(Int.MAX_VALUE) }

        splitPane.validate()
        entrySplitPane.validate()
        navigateToHistoryEntry(historyEntry)
    }

    override fun recreate(): BaseScreen {
        updateCurrentHistoryScrollY()
        return CivilopediaScreen(ruleset, CivilopediaHistory.current())
    }

    //region Navigation helpers
    private fun isValidHistoryEntry(entry: CivilopediaHistory.Entry): Boolean =
        categoryToEntries[entry.category]?.any { it.name == entry.entry } == true

    private fun updateNavigationState() {
        backButton.isDisabled = !CivilopediaHistory.hasBack(::isValidHistoryEntry)
    }

    private fun navigateToHistoryEntry(historyEntry: CivilopediaHistory.Entry?): Boolean {
        if (historyEntry == null) return false
        return navigateTo(historyEntry.category, historyEntry.entry, historyEntry.scroll, updateHistory = false)
    }

    private fun navigateToDefault(category: CivilopediaCategories, link: String) {
        if ('/' in link) return navigateToLink(link)
        val entry = link.ifEmpty {
            // Generic open (e.g. keyboard shortcut, menu button) - resume where we left off
            val resume = CivilopediaHistory.current()
            if (resume != null && navigateToHistoryEntry(resume)) return
            if (CivilopediaHistory.hasBack(::isValidHistoryEntry)) {
                CivilopediaHistory.back(::isValidHistoryEntry) { navigateToHistoryEntry(it) }
                return
            }
            if (category == CivilopediaCategories.Tutorial) chooseDefaultTutorial() else ""
        }
        navigateTo(category, entry, 0f, updateHistory = true)
    }

    private fun chooseDefaultTutorial(): String {
        val keys = categoryToEntries[CivilopediaCategories.Tutorial]?.mapTo(mutableSetOf()) { it.name } ?: return ""
        val name = ruleset.mods.asSequence()
            .filter { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
            .plus("Civilopedia")
            .firstOrNull { it in keys }
            ?: return ""
        return name
    }

    /** Jump to a "link" selecting both category and entry
     *
     * Determines category from the substring before the first '/' (does nothing if not found),
     * and the entry to select from the substring after the first '/' (selects first entry in the category if empty).
     *
     * @param link Link in the form Category/Entry
     */
    private fun navigateToLink(link: String) {
        val parts = link.split('/', limit = 2)
        if (parts.isEmpty()) return
        val category = CivilopediaCategories.fromLink(parts[0])
            ?: return       // silently ignore unknown category names in links
        val entry = if (parts.size >= 2) parts[1] else ""
        navigateTo(category, entry, 0f, updateHistory = true)
    }

    /** Main navigation API
     *  @param category      Category to select
     *  @param entry         Name of an entry so select within [category], or blank to select the last one seen in that category, defaulting to the first one (which depends on translations)
     *  @param scrollY       The scroll position for the content ([flavourScroll])
     *  @param updateHistory If `true`, adds a record to [CivilopediaHistory]
     *  @return `true`is navigation was succesful. `false` means [category] was missing from [categoryToEntries] or [entry] was not empty and not found in [categoryToEntries].
     */
    private fun navigateTo(category: CivilopediaCategories, entry: String, scrollY: Float, updateHistory: Boolean): Boolean {
        updateCurrentHistoryScrollY()
        val categoryEntries = categoryToEntries[category] ?: return false // defense, allowing buggy panes to remain empty while others work
        if (categoryEntries.isEmpty()) return false
        if (entry.isNotEmpty() && categoryEntries.none { it.name == entry }) return false

        val noEntryScrollAnimation = category != currentCategory
        val noFlavourScrollAnimation = noEntryScrollAnimation || currentEntry != entry
        updateCategory(category)
        // Select the first entry if an entry hasn't already been selected - needs entryIndex, which updateCategory has just built
        val entry = entry.ifEmpty {
            currentEntryPerCategory[category] ?: entryIndex.keys.firstOrNull() ?: ""
        }

        selectEntry(entry, noEntryScrollAnimation)
        flavourScroll.validate()
        flavourScroll.scrollY = scrollY
        if (noFlavourScrollAnimation)
            flavourScroll.updateVisualScroll()

        if (updateHistory) {
            CivilopediaHistory.push(category, entry, flavourScroll.scrollY) // scrollY may have been clamped
        }
        updateNavigationState()
        return true
    }

    /** Rebuild left side buttons for a specified category
     * @param category Category key
     */
    private fun updateCategory(category: CivilopediaCategories) {
        currentCategory = category
        entrySelectTable.clear()
        val newEntryIndex = linkedMapOf<String, CivilopediaEntry>()
        flavourTable.clear()

        for (button in categoryToButtons.values) button.button.color = Color.WHITE
        val buttonInfo = categoryToButtons[category]
            ?: return        // defense against being passed a bad selector
        buttonInfo.button.color = Color.BLUE
        buttonTableScroll.scrollX = buttonInfo.x + (buttonInfo.width - buttonTableScroll.width) / 2

        val entries = categoryToEntries[category]!!
            // Sort by [CivilopediaEntry.sortBy], then alphabetical order of localized names, using system default locale
            // Categories that should not sort alphabetically, e.g. Difficulties, will override `getSortGroup`.
            .sortedWith(
                compareBy<CivilopediaEntry> { it.sortBy }
                .thenBy (UncivGame.Current.settings.getCollatorFromLocale()) {
                    // In order for the extra icons on Happiness and Faith to not affect sort order
                    it.name.tr(hideIcons = true, hideStats = true)
                }
            )

        var currentSubCategory: String? = null

        for (entry in entries) {
            val entryButton = Table().apply {
                background = skinStrings.getUiBackground(
                    "CivilopediaScreen/EntryButton",
                    tintColor = colorFromRGB(50, 75, 125)
                )
                touchable = Touchable.enabled
            }
            if (entry.image != null)
                if (category == CivilopediaCategories.Terrain)
                    entryButton.add(entry.image).padLeft(20f).padRight(10f)
                else
                    entryButton.add(entry.image).padLeft(10f)
            entryButton.left().add(entry.name.toLabel(Color.WHITE, 25, hideIcons = true)).pad(10f)
            entryButton.onClick { navigateEntry(entry) }
            entryButton.name = entry.name  // mark button so updateEntry can color the selected one

            if (currentSubCategory != entry.subCategory) {
                if (entry.subCategory != null)
                    entrySelectTable.add(SubCategoryTable(entry.subCategory)).fillX().row()
                currentSubCategory = entry.subCategory
            }

            entrySelectTable.add(entryButton).height(75f).growX()
            entrySelectTable.row()
            newEntryIndex[entry.name] = entry
        }

        entryIndex = IndexedMap(newEntryIndex)
        entrySelectScroll.layout()
    }

    /** Select a specified entry within the current category. Unknown strings are ignored!
     * @param name Entry (Ruleset object) name
     * @param noScrollAnimation Disable scroll animation
     */
    private fun selectEntry(name: String, noScrollAnimation: Boolean = false) {
        val entry = entryIndex[name] ?: return
        entrySelectScroll.validate()
        entrySelectTable.validate()
        val row = entrySelectTable.children.first { it.name == name }
        entrySelectScroll.scrollTo(row.x, row.y, row.width, row.height, false, true)
        if (noScrollAnimation)
            entrySelectScroll.updateVisualScroll()     // snap without animation on fresh pedia open
        updateEntry(entry)
    }

    /** Rebuilds the [flavourTable] for a given [entry] */
    private fun updateEntry(entry: CivilopediaEntry) {
        currentEntryPerCategory[currentCategory] = entry.name
        flavourTable.clear()
        if (entry.flavour != null) {
            flavourTable.isVisible = true
            flavourTable.add(
                entry.flavour.assembleCivilopediaText(ruleset)
                    .renderCivilopediaText(stage.width * 0.5f) { navigateToLink(it) }
            )
        } else {
            flavourTable.isVisible = false
        }
        entrySelectTable.children.forEach {
            it.color = if (it.name == entry.name) Color.BLUE else Color.WHITE
        }
    }

    private fun navigateCategory(category: CivilopediaCategories) {
        navigateTo(category, "", 0f, updateHistory = true)
    }

    private fun navigateCategories(direction: Int) {
        val categoryKeys = categoryToButtons.keys
        val currentIndex = categoryKeys.indexOf(currentCategory)
        val newIndex = (currentIndex + categoryKeys.size + direction) % categoryKeys.size
        navigateCategory(categoryKeys.elementAt(newIndex))
    }

    private fun navigateEntry(entry: CivilopediaEntry, noScrollAnimation: Boolean = false) {
        if (entry.name == currentEntry) return
        updateCurrentHistoryScrollY()
        selectEntry(entry.name, noScrollAnimation)
        flavourScroll.validate()
        flavourScroll.scrollY = 0f
        flavourScroll.updateVisualScroll()
        CivilopediaHistory.push(currentCategory, entry.name, flavourScroll.scrollY)
        updateNavigationState()
    }

    private fun navigateEntries(direction: Int) {
        if (entryIndex.isEmpty()) return
        val index = entryIndex.indexOfKey(currentEntry)
        if (index < 0) return navigateEntry(entryIndex.values.first(), true)
        val newIndex = when (direction) {
            Int.MIN_VALUE -> 0
            Int.MAX_VALUE -> entryIndex.size - 1
            else -> (index + direction).mod(entryIndex.size)
        }
        navigateEntry(entryIndex.valueAt(newIndex))
    }

    private fun updateCurrentHistoryScrollY() {
        CivilopediaHistory.updateScroll(currentCategory, currentEntry, flavourScroll.scrollY)
    }
    //endregion

    private class IndexedMap<K, V>(private val source: LinkedHashMap<K, V>) : Map<K, V> by source {
        private val valueList = ArrayList(source.values)
        private val indexByKey: Map<K, Int> =
            source.keys.withIndex().associate { it.value to it.index }
        fun valueAt(index: Int): V = valueList[index]
        fun indexOfKey(key: K): Int = indexByKey[key] ?: -1
    }

    companion object {
        /** Test whether to show Religion-specific items, does not require a game to be running
         *  - Do not make public - use IHasUniques.isHiddenFromCivilopedia if possible!
         */
        private fun showReligionInCivilopedia(ruleset: Ruleset? = null): Boolean {
            val gameInfo = UncivGame.getGameInfoOrNull()
            return when {
                gameInfo != null -> gameInfo.isReligionEnabled()
                ruleset != null -> ruleset.beliefs.isNotEmpty()
                else -> true
            }
        }
    }
}

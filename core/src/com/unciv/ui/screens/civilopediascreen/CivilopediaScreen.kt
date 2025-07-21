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
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.CursorHoverInputListener
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

/** Screen displaying the Civilopedia
 * @param ruleset [Ruleset] to display items from
 * @param category [CivilopediaCategories] key to select category
 * @param link alternate selector to select category and/or entry. Can have the form `category/entry`
 *             overriding the [category] parameter, or just `entry` to complement it.
 */
class CivilopediaScreen(
    val ruleset: Ruleset,
    category: CivilopediaCategories = CivilopediaCategories.Tutorial,
    link: String = ""
) : BaseScreen(), RecreateOnResize {

    /** Container collecting data per Civilopedia entry
     * @param name From [Ruleset] object [INamed.name]
     * @param image Icon for button
     * @param flavour [ICivilopediaText]
     * @param y Y coordinate for scrolling to
     * @param height Cell height
     */
    private class CivilopediaEntry (
        val name: String,
        val image: Actor? = null,
        val flavour: ICivilopediaText? = null,
        val y: Float = 0f,              // coordinates of button cell used to scroll to entry
        val height: Float = 0f,
        val sortBy: Int = 0             // optional, enabling overriding alphabetical order
    ) {
        fun withCoordinates(y: Float, height: Float) = CivilopediaEntry(name, image, flavour, y, height, sortBy)
    }

    private val categoryToEntries = LinkedHashMap<CivilopediaCategories, Collection<CivilopediaEntry>>()
    private class CategoryButtonInfo(val button: Button, val x: Float, val width: Float)
    private val categoryToButtons = LinkedHashMap<CivilopediaCategories, CategoryButtonInfo>()
    private val entryIndex = LinkedHashMap<String, CivilopediaEntry>()

    private val buttonTableScroll: ScrollPane

    private val entrySelectTable = Table().apply { defaults().pad(6f).left() }
    private val entrySelectScroll: ScrollPane
    private val flavourTable = Table()

    private var currentCategory: CivilopediaCategories = CivilopediaCategories.Tutorial
    private var currentEntry: String = ""
    private val currentEntryPerCategory = HashMap<CivilopediaCategories, String>()

    private val searchPopup by lazy { CivilopediaSearchPopup(this, tutorialController) {
        selectLink(it)
    } }


    /** Jump to a "link" selecting both category and entry
     *
     * Calls [selectCategory] with the substring before the first '/',
     *
     * and [selectEntry] with the substring after the first '/'
     *
     * @param link Link in the form Category/Entry
     */
    private fun selectLink(link: String) {
        val parts = link.split('/', limit = 2)
        if (parts.isEmpty()) return
        selectCategory(parts[0])
        if (parts.size >= 2) selectEntry(parts[1], noScrollAnimation = true)
    }

    /** Select a specified category
     * @param name Category name or label
     */
    private fun selectCategory(name: String) {
        val category = CivilopediaCategories.fromLink(name)
            ?: return       // silently ignore unknown category names in links
        selectCategory(category)
    }

    /** Select a specified category - unselects entry, rebuilds left side buttons.
     * @param category Category key
     */
    private fun selectCategory(category: CivilopediaCategories) {
        currentCategory = category
        entrySelectTable.clear()
        entryIndex.clear()
        flavourTable.clear()

        for (button in categoryToButtons.values) button.button.color = Color.WHITE
        val buttonInfo = categoryToButtons[category]
            ?: return        // defense against being passed a bad selector
        buttonInfo.button.color = Color.BLUE
        buttonTableScroll.scrollX = buttonInfo.x + (buttonInfo.width - buttonTableScroll.width) / 2

        if (category !in categoryToEntries) return        // defense, allowing buggy panes to remain empty while others work
        var entries = categoryToEntries[category]!!
        if (category != CivilopediaCategories.Difficulty) // this is the only case where we need them in order
            // Alphabetical order of localized names, using system default locale
            entries = entries.sortedWith(
                compareBy<CivilopediaEntry>{ it.sortBy }
                    .thenBy (UncivGame.Current.settings.getCollatorFromLocale()) {
                        // In order for the extra icons on Happiness and Faith to not affect sort order
                        it.name.tr(true, true)})

        var currentY = -1f

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
            entryButton.left().add(entry.name
                .toLabel(Color.WHITE, 25, hideIcons=true)).pad(10f)
            entryButton.onClick { selectEntry(entry) }
            entryButton.name = entry.name               // make button findable
            entryButton.addListener(CursorHoverInputListener())
            val cell = entrySelectTable.add(entryButton).height(75f).expandX().fillX()
            entrySelectTable.row()
            if (currentY < 0f) currentY = cell.padTop
            entryIndex[entry.name] = entry.withCoordinates(currentY, cell.prefHeight)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
        }

        entrySelectScroll.layout()          // necessary for positioning in selectRow to work

        val entry = currentEntryPerCategory[category]
        if (entry != null) selectEntry(entry)
    }

    /** Select a specified entry within the current category. Unknown strings are ignored!
     * @param name Entry (Ruleset object) name
     * @param noScrollAnimation Disable scroll animation
     */
    private fun selectEntry(name: String, noScrollAnimation: Boolean = false) {
        val entry = entryIndex[name] ?: return
        // fails: entrySelectScroll.scrollTo(0f, entry.y, 0f, entry.h, false, true)
        entrySelectScroll.scrollY = (entry.y + (entry.height - entrySelectScroll.height) / 2)
        if (noScrollAnimation)
            entrySelectScroll.updateVisualScroll()     // snap without animation on fresh pedia open
        selectEntry(entry)
    }
    private fun selectEntry(entry: CivilopediaEntry) {
        currentEntry = entry.name
        currentEntryPerCategory[currentCategory] = entry.name
        flavourTable.clear()
        if (entry.flavour != null) {
            flavourTable.isVisible = true
            flavourTable.add(
                entry.flavour.assembleCivilopediaText(ruleset)
                    .renderCivilopediaText(stage.width * 0.5f) { selectLink(it) })
        } else {
            flavourTable.isVisible = false
        }
        entrySelectTable.children.forEach {
            it.color = if (it.name == entry.name) Color.BLUE else Color.WHITE
        }
    }
    private fun selectDefaultEntry() {
        val name = ruleset.mods.asSequence()
                .filter { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
                .plus("Civilopedia")
                .firstOrNull { it in entryIndex.keys }
                ?: return
        selectEntry(name , noScrollAnimation = true)
    }

    init {
        val imageSize = 50f

        val religionEnabled = showReligionInCivilopedia(ruleset) // To filter the Belief Category only

        // do not confuse with IConstruction.shouldBeDisplayed - that one tests all prerequisites for building
        fun shouldBeDisplayed(obj: ICivilopediaText) =
            obj !is IHasUniques || !obj.isHiddenFromCivilopedia(game.gameInfo, ruleset)

        for (loopCategory in CivilopediaCategories.entries) {
            if (!religionEnabled && loopCategory == CivilopediaCategories.Belief) continue
            categoryToEntries[loopCategory] =
                loopCategory.getCategoryIterator(ruleset, tutorialController)
                    .filter(::shouldBeDisplayed)
                    .map { CivilopediaEntry(
                        (it as INamed).name,
                        loopCategory.getImage?.invoke(it.getIconName(), imageSize),
                        flavour = it,
                        sortBy = it.getSortGroup(ruleset)
                    ) }
        }

        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)

        var currentX = 10f  // = padLeft
        for ((categoryKey, entries) in categoryToEntries) {
            if (entries.isEmpty()) continue
            val icon = if (categoryKey.headerIcon.isNotEmpty()) ImageGetter.getImage(categoryKey.headerIcon) else null
            val button = IconTextButton(categoryKey.label, icon)
            button.onActivation(binding = categoryKey.binding) { selectCategory(categoryKey) }
            button.addListener(CursorHoverInputListener())
            val cell = buttonTable.add(button)
            categoryToButtons[categoryKey] = CategoryButtonInfo(button, currentX, cell.prefWidth)
            currentX += cell.prefWidth + 20f
        }

        buttonTable.pack()
        buttonTableScroll = ScrollPane(buttonTable)
        buttonTableScroll.setScrollingDisabled(x = false, y = true)

        val searchButton = "OtherIcons/Search".toImageButton(imageSize - 16f, imageSize, skinStrings.skinConfig.baseColor, Color.GOLD)
        searchButton.onActivation(binding = KeyboardBinding.PediaSearch) { searchPopup.open(true) }

        val closeButton = getCloseButton(imageSize) { game.popScreen() }

        val topTable = Table()
        topTable.add(buttonTableScroll).growX()
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
        val entrySplitPane = SplitPane(entrySelectScroll, ScrollPane(descriptionTable), false, skin)
        entrySplitPane.splitAmount = 0.3f
        entryTable.addActor(entrySplitPane)
        entrySplitPane.setFillParent(true)
        entrySplitPane.pack()  // ensure selectEntry has correct entrySelectScroll.height and maxY

        if (link.isEmpty() || '/' !in link)
            selectCategory(category)
        // show a default entry when opened without a target
        if (link.isEmpty() && category == CivilopediaCategories.Tutorial)
            selectDefaultEntry()
        if (link.isNotEmpty())
            if ('/' in link)
                selectLink(link)
            else
                selectEntry(link, noScrollAnimation = true)

        globalShortcuts.add(Input.Keys.LEFT) { navigateCategories(-1) }
        globalShortcuts.add(Input.Keys.RIGHT) { navigateCategories(1) }
        globalShortcuts.add(Input.Keys.UP) { navigateEntries(-1) }
        globalShortcuts.add(Input.Keys.DOWN) { navigateEntries(1) }
        globalShortcuts.add(Input.Keys.PAGE_UP) { navigateEntries(-10) }
        globalShortcuts.add(Input.Keys.PAGE_DOWN) { navigateEntries(10) }
        globalShortcuts.add(Input.Keys.HOME) { navigateEntries(Int.MIN_VALUE) }
        globalShortcuts.add(Input.Keys.END) { navigateEntries(Int.MAX_VALUE) }
    }

    private fun navigateCategories(direction: Int) {
        val categoryKeys = categoryToEntries.keys
        val currentIndex = categoryKeys.indexOf(currentCategory)
        val newIndex = (currentIndex + categoryKeys.size + direction) % categoryKeys.size
        selectCategory(categoryKeys.elementAt(newIndex))
    }

    private fun navigateEntries(direction: Int) {
        //todo this is abusing a Map as Array - there must be a collection allowing both easy positional and associative access
        val index = entryIndex.keys.indexOf(currentEntry)
        if (index < 0) return selectEntry(entryIndex.keys.first(), true)
        val newIndex = when (direction) {
            Int.MIN_VALUE -> 0
            Int.MAX_VALUE -> entryIndex.size - 1
            else -> (index + entryIndex.size + direction) % entryIndex.size
        }
        selectEntry(entryIndex.keys.drop(newIndex).first())
    }

    override fun recreate(): BaseScreen = CivilopediaScreen(ruleset, currentCategory, currentEntry)

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

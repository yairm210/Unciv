package com.unciv.ui.civilopedia

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import java.text.Collator
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/** Screen displaying the Civilopedia
 * @param ruleset [Ruleset] to display items from
 * @param category [CivilopediaCategories] key to select category
 * @param link alternate selector to select category and/or entry. Can have the form `category/entry`
 *             overriding the [category] parameter, or just `entry` to complement it.
 */
class CivilopediaScreen(
    val ruleset: Ruleset
    , category: CivilopediaCategories = CivilopediaCategories.Tutorial
    , link: String = ""
) : CameraStageBaseScreen() {

    /** Container collecting data per Civilopedia entry
     * @param name From [Ruleset] object [INamed.name]
     * @param description Multiline text
     * @param image Icon for button
     * @param flavour [CivilopediaText]
     * @param y Y coordinate for scrolling to
     * @param height Cell height
     */
    private class CivilopediaEntry (
        val name: String,
        val description: String,
        val image: Actor? = null,
        val flavour: ICivilopediaText? = null,
        val y: Float = 0f,              // coordinates of button cell used to scroll to entry
        val height: Float = 0f
    ) {
        fun withCoordinates(y: Float, height: Float) = CivilopediaEntry(name, description, image, flavour, y, height)
    }

    private val categoryToEntries = LinkedHashMap<CivilopediaCategories, Collection<CivilopediaEntry>>()
    private val categoryToButtons = LinkedHashMap<CivilopediaCategories, Button>()
    private val entryIndex = LinkedHashMap<String, CivilopediaEntry>()

    private val entrySelectTable = Table().apply { defaults().pad(6f).left() }
    private val entrySelectScroll: ScrollPane
    private val descriptionLabel = "".toLabel()
    private val flavourTable = Table()

    private var currentCategory: CivilopediaCategories = CivilopediaCategories.Tutorial
    private var currentEntry: String = ""

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
    fun selectCategory(name: String) {
        val category = CivilopediaCategories.fromLink(name)
            ?: return       // silently ignore unknown category names in links
        selectCategory(category)
    }

    /** Select a specified category - unselects entry, rebuilds left side buttons.
     * @param category Category key
     */
    fun selectCategory(category: CivilopediaCategories) {
        currentCategory = category
        entrySelectTable.clear()
        entryIndex.clear()
        descriptionLabel.setText("")
        flavourTable.clear()

        for (button in categoryToButtons.values) button.color = Color.WHITE
        if (category !in categoryToButtons) return        // defense against being passed a bad selector
        categoryToButtons[category]!!.color = Color.BLUE

        if (category !in categoryToEntries) return        // defense, allowing buggy panes to remain empty while others work
        var entries = categoryToEntries[category]!!
        if (category != CivilopediaCategories.Difficulty) // this is the only case where we need them in order
            // Alphabetical order of localized names, using system default locale
            entries = entries.sortedWith(compareBy(Collator.getInstance(), { it.name.tr() }))

        var currentY = -1f

        for (entry in entries) {
            val entryButton = Table().apply {
                background = ImageGetter.getBackground(colorFromRGB(50, 75, 125))
                touchable = Touchable.enabled
            }
            if (entry.image != null)
                if (category == CivilopediaCategories.Terrain)
                    entryButton.add(entry.image).padLeft(20f).padRight(10f)
                else
                    entryButton.add(entry.image).padLeft(10f)
            entryButton.left().add(entry.name.toLabel(Color.WHITE, 25)).pad(10f)
            entryButton.onClick { selectEntry(entry) }
            entryButton.name = entry.name               // make button findable
            val cell = entrySelectTable.add(entryButton).height(75f).expandX().fillX()
            entrySelectTable.row()
            if (currentY < 0f) currentY = cell.padTop
            entryIndex[entry.name] = entry.withCoordinates(currentY, cell.prefHeight)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
        }

        entrySelectScroll.layout()          // necessary for positioning in selectRow to work
    }

    /** Select a specified entry within the current category. Unknown strings are ignored!
     * @param name Entry (Ruleset object) name
     * @param noScrollAnimation Disable scroll animation
     */
    fun selectEntry(name: String, noScrollAnimation: Boolean = false) {
        val entry = entryIndex[name] ?: return
        // fails: entrySelectScroll.scrollTo(0f, entry.y, 0f, entry.h, false, true)
        entrySelectScroll.let {
            it.scrollY = (entry.y + (entry.height - it.height) / 2).coerceIn(0f, it.maxY)
        }
        if (noScrollAnimation)
            entrySelectScroll.updateVisualScroll()     // snap without animation on fresh pedia open
        selectEntry(entry)
    }
    private fun selectEntry(entry: CivilopediaEntry) {
        currentEntry = entry.name
        if(entry.flavour != null && entry.flavour.replacesCivilopediaDescription()) {
            descriptionLabel.setText("")
            descriptionLabel.isVisible = false
        } else {
            descriptionLabel.setText(entry.description)
            descriptionLabel.isVisible = true
        }
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

    init {
        val imageSize = 50f
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        val hideReligionItems = !game.gameInfo.hasReligionEnabled()
        val noCulturalVictory = VictoryType.Cultural !in game.gameInfo.gameParameters.victoryTypes

        categoryToEntries[CivilopediaCategories.Building] = ruleset.buildings.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques
                        && !(hideReligionItems && "Hidden when religion is disabled" in it.uniques)
                        && !(noCulturalVictory && "Hidden when cultural victory is disabled" in it.uniques)
                        && !it.isAnyWonder() }
                .map {
                    CivilopediaEntry(
                        it.name,
                        "",
                        CivilopediaCategories.Building.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Wonder] = ruleset.buildings.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques
                        && !(hideReligionItems && "Hidden when religion is disabled" in it.uniques)
                        && !(noCulturalVictory && "Hidden when cultural victory is disabled" in it.uniques)
                        && it.isAnyWonder() }
                .map {
                    CivilopediaEntry(
                        it.name,
                        "",
                        CivilopediaCategories.Wonder.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Resource] = ruleset.tileResources.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        CivilopediaCategories.Resource.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Terrain] = ruleset.terrains.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        CivilopediaCategories.Terrain.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Improvement] = ruleset.tileImprovements.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset, false),
                        CivilopediaCategories.Improvement.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Unit] = ruleset.units.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques }
                .map {
                    CivilopediaEntry(
                        it.name,
                        "",
                        CivilopediaCategories.Unit.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Nation] = ruleset.nations.values
                .filter { it.isMajorCiv() }
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getUniqueString(ruleset, false),
                        CivilopediaCategories.Nation.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Technology] = ruleset.technologies.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        CivilopediaCategories.Technology.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Promotion] = ruleset.unitPromotions.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset.unitPromotions.values, true, ruleset),
                        CivilopediaCategories.Promotion.getImage?.invoke(it.name, imageSize),
                        (it as? ICivilopediaText).takeUnless { ct -> ct==null || ct.isCivilopediaTextEmpty() }
                    )
                }

        categoryToEntries[CivilopediaCategories.Tutorial] = tutorialController.getCivilopediaTutorials()
                .map {
                    CivilopediaEntry(
                        it.key.replace("_", " "),
                        "",
//                        CivilopediaCategories.Tutorial.getImage?.invoke(it.name, imageSize)
                        flavour = SimpleCivilopediaText(
                            sequenceOf(FormattedLine(extraImage = it.key)),
                            it.value.asSequence(), true)
                    )
                }

        categoryToEntries[CivilopediaCategories.Difficulty] = ruleset.difficulties.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(),
//                        CivilopediaCategories.Difficulty.getImage?.invoke(it.name, imageSize)
                    )
                }

        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)

        for (categoryKey in categoryToEntries.keys) {
            val button = categoryKey.label.toTextButton()
            button.style = TextButton.TextButtonStyle(button.style)
            categoryToButtons[categoryKey] = button
            button.onClick { selectCategory(categoryKey) }
            buttonTable.add(button)
        }

        buttonTable.pack()
        buttonTable.width = stage.width
        val buttonTableScroll = ScrollPane(buttonTable)
        buttonTableScroll.setScrollingDisabled(false, true)

        val goToGameButton = Constants.close.toTextButton()
        goToGameButton.onClick {
            game.setWorldScreen()
            dispose()
        }

        val topTable = Table()
        topTable.add(goToGameButton).pad(10f)
        topTable.add(buttonTableScroll)
        topTable.pack()

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
        descriptionTable.add(flavourTable).row()
        descriptionLabel.wrap = true            // requires explicit cell width!
        descriptionTable.add(descriptionLabel).width(stage.width * 0.5f).padTop(10f).row()
        val entrySplitPane = SplitPane(entrySelectScroll, ScrollPane(descriptionTable), false, skin)
        entrySplitPane.splitAmount = 0.3f
        entryTable.addActor(entrySplitPane)
        entrySplitPane.setFillParent(true)
        entrySplitPane.pack()  // ensure selectEntry has correct entrySelectScroll.height and maxY

        if (link.isEmpty() || '/' !in link)
            selectCategory(category)
        if (link.isNotEmpty())
            if ('/' in link)
                selectLink(link)
            else
                selectEntry(link, noScrollAnimation = true)
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(CivilopediaScreen(game.worldScreen.gameInfo.ruleSet, currentCategory, currentEntry))
        }
    }
}

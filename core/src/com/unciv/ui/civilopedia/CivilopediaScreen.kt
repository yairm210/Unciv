package com.unciv.ui.civilopedia

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.translations.tr
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import kotlin.collections.LinkedHashMap
import com.unciv.models.stats.INamed

/** Screen displaying the Civilopedia
 * @param ruleset [Ruleset] to display items from
 * @param category [CivilopediaCategories] key to select category
 * @param entry Item's [INamed.name] to select
 */
class CivilopediaScreen(
    ruleset: Ruleset
    , category: CivilopediaCategories = CivilopediaCategories.Tutorial
    , entry: String = ""
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
        val flavour: CivilopediaText? = null,
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


    /** Jump to a "link" selecting both category and entry
     *
     * Calls [selectCategory] with the substring before the first '/',
     *
     * and [selectEntry] with the substring after the first '/'
     *
     * @param link Link in the form Category/Entry
     * @param noScrollAnimation Disable scroll animation
     */
    fun selectLink(link: String, noScrollAnimation: Boolean = false) {
        val parts = link.split('/', limit = 2)
        if (parts.isEmpty()) return
        selectCategory(parts[0])
        if (parts.size >= 2) selectEntry(parts[1], noScrollAnimation)
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
        entrySelectTable.clear()
        entryIndex.clear()
        descriptionLabel.setText("")
        flavourTable.clear()

        for (button in categoryToButtons.values) button.color = Color.WHITE
        if (category !in categoryToButtons) return        // defense against being passed a bad selector
        categoryToButtons[category]!!.color = Color.BLUE

        if (category !in categoryToEntries) return        // defense, allowing buggy panes to remain emtpy while others work
        var entries = categoryToEntries[category]!!
        if (category != CivilopediaCategories.Difficulty) // this is the only case where we need them in order
            entries = entries.sortedBy { it.name.tr() }   // Alphabetical order of localized names
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
        descriptionLabel.setText(
            if(entry.flavour != null && entry.flavour.replacesCivilopediaDescription()) ""
                else entry.description
        )
        flavourTable.clear()
        if (entry.flavour != null) {
            flavourTable.add(
                entry.flavour.assembleCivilopediaText()
                    .renderCivilopediaText(skin, stage.width * 0.5f) { selectLink(it) })
        }
        entrySelectTable.children.forEach {
            it.color = if (it.name == entry.name) Color.BLUE else Color.WHITE
        }
    }

    init {
        val imageSize = 50f
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        categoryToEntries[CivilopediaCategories.Building] = ruleset.buildings.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques && !(it.isWonder || it.isNationalWonder) }
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(false, null, ruleset),
                        ImageGetter.getConstructionImage(it.name).surroundWithCircle(imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Wonder] = ruleset.buildings.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques && (it.isWonder || it.isNationalWonder) }
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(false, null, ruleset),
                        ImageGetter.getConstructionImage(it.name).surroundWithCircle(imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Resource] = ruleset.tileResources.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        ImageGetter.getResourceImage(it.name, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Terrain] = ruleset.terrains.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        terrainImage(it, ruleset, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Improvement] = ruleset.tileImprovements.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset, false),
                        ImageGetter.getImprovementIcon(it.name, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Unit] = ruleset.units.values
                .filter { "Will not be displayed in Civilopedia" !in it.uniques }
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(false),
                        ImageGetter.getConstructionImage(it.name).surroundWithCircle(imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Nation] = ruleset.nations.values
                .filter { it.isMajorCiv() }
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getUniqueString(ruleset, false),
                        ImageGetter.getNationIndicator(it, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Technology] = ruleset.technologies.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset),
                        ImageGetter.getTechIconGroup(it.name, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }
        categoryToEntries[CivilopediaCategories.Promotion] = ruleset.unitPromotions.values
                .map {
                    CivilopediaEntry(
                        it.name,
                        it.getDescription(ruleset.unitPromotions.values, true, ruleset),
                        ImageGetter.getPromotionIcon(it.name, imageSize),
                        (it as? CivilopediaText).takeUnless { ct -> ct==null || ct.isEmpty() }
                    )
                }

        categoryToEntries[CivilopediaCategories.Tutorial] = tutorialController.getCivilopediaTutorials()
                .map { CivilopediaEntry(it.key.replace("_", " "), it.value.joinToString("\n\n") { line -> line.tr() }) }

        categoryToEntries[CivilopediaCategories.Difficulty] = ruleset.difficulties.values
                .map { CivilopediaEntry(it.name, it.getDescription()) }

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

        descriptionLabel.wrap = true            // requires explicit cell width!

        entrySelectScroll = ScrollPane(entrySelectTable)
        entrySelectTable.top()
        entrySelectScroll.setOverscroll(false, false)
        val descriptionTable = Table()
        descriptionTable.add(descriptionLabel).width(stage.width * 0.5f).row()
        descriptionTable.add(flavourTable)
        val entrySplitPane = SplitPane(entrySelectScroll, ScrollPane(descriptionTable), false, skin)
        entrySplitPane.splitAmount = 0.3f
        entryTable.addActor(entrySplitPane)
        entrySplitPane.setFillParent(true)

        selectCategory(category)
        selectEntry(entry)
    }

    // Todo: potential synergy with map editor
    private fun terrainImage(terrain: Terrain, ruleset: Ruleset, imageSize: Float): Actor {
        val tileInfo = TileInfo()
        tileInfo.ruleset = ruleset
        when (terrain.type) {
            TerrainType.NaturalWonder -> {
                tileInfo.naturalWonder = terrain.name
                tileInfo.baseTerrain = terrain.turnsInto ?: Constants.grassland
            }
            TerrainType.TerrainFeature -> {
                tileInfo.terrainFeatures.add(terrain.name)
                tileInfo.baseTerrain = terrain.occursOn.lastOrNull() ?: Constants.grassland
            }
            else ->
                tileInfo.baseTerrain = terrain.name
        }
        tileInfo.setTerrainTransients()
        val group = TileGroup(tileInfo, TileSetStrings(), imageSize)
        group.showEntireMap = true
        group.forMapEditorIcon = true
        group.update()
        return group
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(CivilopediaScreen(game.worldScreen.gameInfo.ruleSet))
        }
    }
}

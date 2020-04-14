package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.CivilopediaCategories
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.translations.tr
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import java.util.*
import kotlin.collections.LinkedHashMap

class CivilopediaScreen(
        private val ruleset: Ruleset,
        initialPage: CivilopediaCategories = CivilopediaCategories.Tutorials,
        initialEntry: String? = null
) : CameraStageBaseScreen() {
    private class CivilopediaEntry(val description: String, val image: Actor? = null, var button: Button? = null)
    private class CivilopediaCategoryMap(var button: Button? = null, val entries: LinkedHashMap<String,CivilopediaEntry> = LinkedHashMap<String,CivilopediaEntry>())

    private val categoryMaps = LinkedHashMap<CivilopediaCategories, CivilopediaCategoryMap>()
    private var currentCategory = CivilopediaCategories.Unselected
    private var highlightedButton: Button? = null

    private val buttonTableScroll: ScrollPane
    private val entrySelectScroll: ScrollPane
    private val entrySelectTable = Table().apply { defaults().pad(6f) }
    val description = "".toLabel()

    fun select(category: CivilopediaCategories, selectEntry: String? = null) {
        val categoryMap = categoryMaps[category] ?: return
        if (category != currentCategory) {
            entrySelectTable.clear()
            entrySelectScroll.scrollTo(0f,0f,0f,0f)
            entrySelectScroll.updateVisualScroll()
            currentCategory = category
            highlightedButton = null

            for ( (name,entry) in categoryMap.entries) {
                    //.sortedBy { it.name.tr() }) {  // Alphabetical order of localized names
                val entryButton = Button(skin)
                entry.button = entryButton
                if (entry.image != null)
                    if (category == CivilopediaCategories.Terrains)
                        entryButton.add(entry.image).padRight(24f)
                    else
                        entryButton.add(entry.image).size(50f).padRight(10f)
                entryButton.add(name.toLabel())
                entryButton.onClick {
                    select (category, name)
                }
                entrySelectTable.add(entryButton).left().row()
            }
            entrySelectTable.pack()

            categoryMap.button?.let {
                buttonTableScroll.scrollTo(it.x,it.y,it.width,it.height,false,true)
                it.highlight()              // Gone once entry selected, but that actually looks OK
            }
        }

        description.clearListeners()

        val selectedEntry = categoryMap.entries[selectEntry]
        if (selectedEntry == null) {
            description.setText("")
        } else {
            selectedEntry.button?.let {
                println("Civilopedia select ${category.label}.$selectEntry: (${it.x},${it.y},${it.width},${it.height}), scroll.y=${entrySelectScroll.y}, table.y/h=${entrySelectTable.y},${entrySelectTable.height}")
                entrySelectScroll.scrollTo(it.x, it.y, it.width, it.height, true, false)
                it.highlight()
            }
            description.setText(selectedEntry.description)

            //// TESTING CODE
            // right now, the description is just a big text label, we'd need to split that to make
            // portions clickable individually...
            var targetCategory: CivilopediaCategories = category
            var targetEntry: String? = null
            if (category == CivilopediaCategories.Units) {
                val unit = ruleset.units[selectEntry]
                when {
                    unit?.uniqueTo != null -> { targetCategory = CivilopediaCategories.Nations; targetEntry = unit.uniqueTo }
                    unit?.upgradesTo != null -> { targetEntry = unit.upgradesTo }
                }
            }
            if (category == CivilopediaCategories.Buildings) {
                val building = ruleset.buildings[selectEntry]
                when {
                    building?.uniqueTo != null -> { targetCategory = CivilopediaCategories.Nations; targetEntry = building.uniqueTo }
                    building?.requiredTech != null -> { targetCategory = CivilopediaCategories.Technologies; targetEntry = building.requiredTech }
                }
            }
            if (targetCategory != category || targetEntry != null) {
                description.onClick { select(targetCategory, targetEntry) }
            }
        }

    }

    private fun Button.highlight (highlight: Boolean = true) {
        if (highlight) {
            highlightedButton?.highlight(false)
            highlightedButton = null
        }
        val newColor = if (highlight) Color.GOLDENROD else Color.WHITE
        //(this.children.firstOrNull { it is Image } as Image?)?.color = newColor
        (this.children.firstOrNull { it is Label } as Label?)?.color = newColor
        if (highlight)
            highlightedButton = this
    }

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        categoryMaps[CivilopediaCategories.Buildings] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.buildings.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(false, null,ruleset),
                        ImageGetter.getConstructionImage(it.key)))
                }.sortedBy { it.first }
            )
        }
        categoryMaps[CivilopediaCategories.Resources] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.tileResources.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(ruleset),
                        ImageGetter.getResourceImage(it.key,50f)))
                }.sortedBy { it.first }
            )
        }

        val tileSetStrings = TileSetStrings()
        categoryMaps[CivilopediaCategories.Terrains] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.terrains.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(ruleset),
                            terrainImage(it.value, ruleset, tileSetStrings)))
                }.sortedBy { it.first }
            )
        }

        categoryMaps[CivilopediaCategories.Improvements] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.tileImprovements.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(ruleset,false),
                        ImageGetter.getImprovementIcon(it.key,50f)))
                }.sortedBy { it.first }
            )
        }

        categoryMaps[CivilopediaCategories.Units] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.units.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(false),
                        ImageGetter.getConstructionImage(it.key)))
                }.sortedBy { it.first }
            )
        }
        categoryMaps[CivilopediaCategories.Nations] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.nations.filter { it.value.isMajorCiv() }.map {
                    Pair (it.key, CivilopediaEntry(it.value.getUniqueString(ruleset,false),
                        ImageGetter.getNationIndicator(it.value,50f)))
                }.sortedBy { it.first }
            )
        }
        categoryMaps[CivilopediaCategories.Technologies] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.technologies.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(ruleset),
                        ImageGetter.getTechIconGroup(it.key,50f)))
                }.sortedBy { it.first }
            )
        }
        categoryMaps[CivilopediaCategories.Promotions] = CivilopediaCategoryMap().apply {
            entries.putAll (
                ruleset.unitPromotions.map {
                    Pair (it.key, CivilopediaEntry(it.value.getDescription(ruleset.unitPromotions.values,true, ruleset),
                        Table().apply { add(ImageGetter.getPromotionIcon(it.key)) }))
                }.sortedBy { it.first }
            )
        }
        categoryMaps[CivilopediaCategories.Tutorials] = CivilopediaCategoryMap().apply {
            entries.putAll (
                tutorialController.getCivilopediaTutorials().map {
                    Pair (it.key.replace("_"," "), CivilopediaEntry(it.value.joinToString("\n\n") { line -> line.tr() }))
                }.sortedBy { it.first }
            )
        }

        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)

        for ( (category,map) in categoryMaps) {
            val button = TextButton(category.label.tr(), skin)
            button.style = TextButton.TextButtonStyle(button.style)
            map.button = button
            button.onClick { select(category) }
            buttonTable.add(button)
        }

        buttonTable.pack()
        buttonTable.width = stage.width
        buttonTableScroll = ScrollPane(buttonTable)

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

        description.setWrap(true)

        entrySelectScroll = ScrollPane(entrySelectTable)
        entrySelectScroll.setupOverscroll(5f, 1f, 200f)
        entryTable.add(entrySelectScroll)
                .width(Value.percentWidth(0.25f, entryTable))
                .fillY()
                .pad(Value.percentWidth(0.02f, entryTable))
        entryTable.add(ScrollPane(description)).colspan(4)
                .width(Value.percentWidth(0.65f, entryTable))
                .fillY()
                .pad(Value.percentWidth(0.02f, entryTable))
        // Simply changing these to x*width, y*height won't work

        select(initialPage, initialEntry)
    }

    private fun terrainImage (terrain: Terrain, ruleset: Ruleset, tileSetStrings: TileSetStrings ): Actor? {
        val tileInfo = TileInfo()
        tileInfo.ruleset = ruleset
        when(terrain.type) {
            TerrainType.NaturalWonder -> {
                tileInfo.naturalWonder = terrain.name
                tileInfo.baseTerrain = terrain.turnsInto ?: Constants.grassland
            }
            TerrainType.TerrainFeature -> {
                tileInfo.terrainFeature = terrain.name
                tileInfo.baseTerrain = terrain.occursOn?.last() ?: Constants.grassland
            }
            else ->
                tileInfo.baseTerrain = terrain.name
        }
        tileInfo.setTransients()
        val group = TileGroup(tileInfo, TileSetStrings())
        group.showEntireMap = true
        group.forMapEditorIcon = true
        group.update()
        return group

    }
}


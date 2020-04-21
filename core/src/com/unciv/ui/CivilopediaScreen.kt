package com.unciv.ui

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.scenes.scene2d.Actor
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
import java.util.*

class CivilopediaScreen(ruleset: Ruleset) : CameraStageBaseScreen() {
    class CivilopediaEntry(var name: String, var description: String, var image: Actor? = null)

    private val categoryToEntries = LinkedHashMap<String, Collection<CivilopediaEntry>>()
    private val categoryToButtons = LinkedHashMap<String, Button>()

    private val entrySelectTable = Table().apply { defaults().pad(6f) }
    val description = "".toLabel()


    fun select(category: String) {
        entrySelectTable.clear()
        for (entry in categoryToEntries[category]!!
                .sortedBy { it.name.tr() }){  // Alphabetical order of localized names
            val entryButton = Button(skin)
            if(entry.image!=null)
                if (category=="Terrains")
                    entryButton.add(entry.image).padRight(24f)
                else
                    entryButton.add(entry.image).size(50f).padRight(10f)
            entryButton.add(entry.name.toLabel())
            entryButton.onClick {
                description.setText(entry.description)
            }
            entrySelectTable.add(entryButton).left().row()
        }
    }

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        val tileSetStrings = TileSetStrings()

        categoryToEntries["Buildings"] = ruleset.buildings.values
                .map { CivilopediaEntry(it.name,it.getDescription(false, null,ruleset),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Resources"] = ruleset.tileResources.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getResourceImage(it.name,50f)) }
        categoryToEntries["Terrains"] = ruleset.terrains.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        terrainImage(it, ruleset, tileSetStrings) ) }
        categoryToEntries["Tile Improvements"] = ruleset.tileImprovements.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset,false),
                        ImageGetter.getImprovementIcon(it.name,50f)) }
        categoryToEntries["Units"] = ruleset.units.values
                .map { CivilopediaEntry(it.name,it.getDescription(false),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Nations"] = ruleset.nations.values
                .filter { it.isMajorCiv() }
                .map { CivilopediaEntry(it.name,it.getUniqueString(ruleset,false),
                        ImageGetter.getNationIndicator(it,50f)) }
        categoryToEntries["Technologies"] = ruleset.technologies.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getTechIconGroup(it.name,50f)) }
        categoryToEntries["Promotions"] = ruleset.unitPromotions.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset.unitPromotions.values, true, ruleset),
                        Table().apply { add(ImageGetter.getPromotionIcon(it.name)) }) }

        categoryToEntries["Tutorials"] = tutorialController.getCivilopediaTutorials()
                .map { CivilopediaEntry(it.key.replace("_"," "), it.value.joinToString("\n\n") { line -> line.tr() }) }

        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)

        for (category in categoryToEntries.keys) {
            val button = category.toTextButton()
            button.style = TextButton.TextButtonStyle(button.style)
            categoryToButtons[category] = button
            button.onClick { select(category) }
            buttonTable.add(button)
        }

        buttonTable.pack()
        buttonTable.width = stage.width
        val buttonTableScroll = ScrollPane(buttonTable)

        val goToGameButton = Constants.close.toTextButton()
        goToGameButton.onClick {
            game.setWorldScreen()
            dispose()
        }

        val topTable = Table()
        topTable.add(goToGameButton).pad(10f)
        topTable.add(buttonTableScroll)
        topTable.pack()
        //buttonTable.height = topTable.height

        val entryTable = Table()
        val splitPane = SplitPane(topTable, entryTable, true, skin)
        splitPane.splitAmount = topTable.prefHeight / stage.height
        entryTable.height = stage.height - topTable.prefHeight
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        description.setWrap(true)

        val entrySelectScroll = ScrollPane(entrySelectTable)
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

        select("Tutorials")
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
//        val wrapper = Table()
//        wrapper.add(group).pad(24f)
//        wrapper.pad(2f,24f,2f,24f)
//        wrapper.debug = true
//        return wrapper
    }
}


package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tr
import com.unciv.ui.utils.*
import java.util.*

class CivilopediaScreen(ruleset: Ruleset) : CameraStageBaseScreen() {
    class CivilopediaEntry {
        var name: String
        var description: String
        var image: Actor?=null

        constructor(name: String, description: String, image: Actor?=null) {
            this.name = name
            this.description = description
            this.image = image
        }

        constructor() : this("","") // Needed for GameBasics json deserializing
    }

    val categoryToEntries = LinkedHashMap<String, Collection<CivilopediaEntry>>()
    val categoryToButtons = LinkedHashMap<String, Button>()

    val entrySelectTable = Table().apply { defaults().pad(5f) }
    val description = "".toLabel()

    fun select(category: String) {
        entrySelectTable.clear()
        for (entry in categoryToEntries[category]!!
                .sortedBy { it.name.tr() }){  // Alphabetical order of localized names
            val entryButton = Button(skin)
            if(entry.image!=null)
                entryButton.add(entry.image).size(50f).padRight(10f)
            entryButton.add(entry.name.toLabel())
            entryButton.onClick {
                description.setText(entry.description)
            }
            entrySelectTable.add(entryButton).row()
        }
    }

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }
        val buttonTable = Table()
        buttonTable.pad(15f)
        buttonTable.defaults().pad(10f)
        val buttonTableScroll = ScrollPane(buttonTable)

        val goToGameButton = TextButton("Close".tr(), skin)
        goToGameButton.onClick {
            game.setWorldScreen()
            dispose()
        }

        val topTable = Table()
        topTable.add(goToGameButton).pad(10f)
        topTable.add(buttonTableScroll)

        val entryTable = Table()
        val splitPane = SplitPane(topTable, entryTable, true, skin)
        splitPane.splitAmount = 0.2f
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        description.setWrap(true)

        categoryToEntries["Buildings"] = ruleset.Buildings.values
                .map { CivilopediaEntry(it.name,it.getDescription(false, null,ruleset),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Resources"] = ruleset.TileResources.values
                .map { CivilopediaEntry(it.name,it.getDescription(),
                        ImageGetter.getResourceImage(it.name,50f)) }
        categoryToEntries["Terrains"] = ruleset.Terrains.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset)) }
        categoryToEntries["Tile Improvements"] = ruleset.TileImprovements.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getImprovementIcon(it.name,50f)) }
        categoryToEntries["Units"] = ruleset.Units.values
                .map { CivilopediaEntry(it.name,it.getDescription(false),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Technologies"] = ruleset.Technologies.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getTechIconGroup(it.name,50f)) }

        categoryToEntries["Tutorials"] = Tutorials().getTutorialsOfLanguage("English").keys
                .filter { !it.startsWith("_") }
                .map { CivilopediaEntry(it.replace("_"," "),
                        Tutorials().getTutorials(it, UncivGame.Current.settings.language)
                                .joinToString("\n\n")) }

        for (category in categoryToEntries.keys) {
            val button = TextButton(category.tr(), skin)
            button.style = TextButton.TextButtonStyle(button.style)
            categoryToButtons[category] = button
            button.onClick { select(category) }
            buttonTable.add(button)
        }
        select("Basics")
        val sp = ScrollPane(entrySelectTable)
        sp.setupOverscroll(5f, 1f, 200f)
        entryTable.add(sp).width(Value.percentWidth(0.25f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        entryTable.add(ScrollPane(description)).colspan(4)
                .width(Value.percentWidth(0.65f, entryTable))
                .height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        // Simply changing these to x*width, y*height won't work

        buttonTable.width = stage.width
    }

}


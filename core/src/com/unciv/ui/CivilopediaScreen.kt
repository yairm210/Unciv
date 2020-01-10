package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.getUniqueString
import com.unciv.models.translations.tr
import com.unciv.ui.tutorials.TutorialMiner
import com.unciv.ui.utils.*
import java.util.*

class CivilopediaScreen(ruleset: Ruleset) : CameraStageBaseScreen() {
    class CivilopediaEntry(var name: String, var description: String, var image: Actor? = null)

    private val categoryToEntries = LinkedHashMap<String, Collection<CivilopediaEntry>>()
    private val categoryToButtons = LinkedHashMap<String, Button>()

    private val entrySelectTable = Table().apply { defaults().pad(5f) }
    val description = "".toLabel()

    private val tutorialMiner = TutorialMiner(JsonParser())

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

        categoryToEntries["Buildings"] = ruleset.buildings.values
                .map { CivilopediaEntry(it.name,it.getDescription(false, null,ruleset),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Resources"] = ruleset.tileResources.values
                .map { CivilopediaEntry(it.name,it.getDescription(),
                        ImageGetter.getResourceImage(it.name,50f)) }
        categoryToEntries["Terrains"] = ruleset.terrains.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset)) }
        categoryToEntries["Tile Improvements"] = ruleset.tileImprovements.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getImprovementIcon(it.name,50f)) }
        categoryToEntries["Units"] = ruleset.units.values
                .map { CivilopediaEntry(it.name,it.getDescription(false),
                        ImageGetter.getConstructionImage(it.name)) }
        categoryToEntries["Nations"] = ruleset.nations.values
                .filter { it.isMajorCiv() }
                .map { CivilopediaEntry(it.name, it.getUniqueString(ruleset),
                        ImageGetter.getNationIndicator(it,50f)) }
        categoryToEntries["Technologies"] = ruleset.technologies.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset),
                        ImageGetter.getTechIconGroup(it.name,50f)) }
        categoryToEntries["Promotions"] = ruleset.unitPromotions.values
                .map { CivilopediaEntry(it.name,it.getDescription(ruleset.unitPromotions.values, true),
                        Table().apply { add(ImageGetter.getPromotionIcon(it.name)) }) }

        categoryToEntries["Tutorials"] = tutorialMiner.getCivilopediaTutorials(UncivGame.Current.settings.language)
                .map { CivilopediaEntry(it.key.value.replace("_"," "), it.value.joinToString("\n\n")) }

        for (category in categoryToEntries.keys) {
            val button = TextButton(category.tr(), skin)
            button.style = TextButton.TextButtonStyle(button.style)
            categoryToButtons[category] = button
            button.onClick { select(category) }
            buttonTable.add(button)
        }
        select("Tutorials")
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


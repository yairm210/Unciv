package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.BasicHelp
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.Tutorials
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import java.util.*
import kotlin.math.max

class CivilopediaScreen : CameraStageBaseScreen() {

    val categoryToInfos = LinkedHashMap<String, Collection<ICivilopedia>>()
    val categoryToButtons = LinkedHashMap<String, Button>()
    val civPediaEntries = Array<ICivilopedia>()

    val nameList = List<String>(skin)
    val description = "".toLabel()

    fun select(category: String, entry: String? = null) {
        val nameItems=Array<String>()
        civPediaEntries.clear()
        for (civilopediaEntry in categoryToInfos[category]!!.sortedBy { it.toString().tr() }){  // Alphabetical order of localized names
            civPediaEntries.add(civilopediaEntry)
            nameItems.add(civilopediaEntry.toString().tr())
        }
        nameList.setItems(nameItems)
        val index = max(0, nameList.items.indexOf(entry?.tr()))
        nameList.selected = nameList.items.get(index)
        description.setText(civPediaEntries.get(index).description)
        for (btn in categoryToButtons.values) btn.isChecked = false
        categoryToButtons[category]?.isChecked = true
    }

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        val buttonTable = Table()
        buttonTable.pad(15f)
        val entryTable = Table()
        val splitPane = SplitPane(buttonTable, entryTable, true, skin)
        splitPane.splitAmount = 0.2f
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        description.setWrap(true)

        val goToGameButton = TextButton("Close".tr(), skin)
        goToGameButton.onClick {
                game.setWorldScreen()
                dispose()
            }
        buttonTable.add(goToGameButton)



        val language = UnCivGame.Current.settings.language.replace(" ","_")
        val basicHelpFileName = if(Gdx.files.internal("jsons/BasicHelp/BasicHelp_$language.json").exists())"BasicHelp/BasicHelp_$language"
        else "BasicHelp/BasicHelp"

        categoryToInfos["Basics"] = GameBasics.getFromJson(kotlin.Array<BasicHelp>::class.java, basicHelpFileName).toList()
        categoryToInfos["Buildings"] = GameBasics.Buildings.values
        categoryToInfos["Resources"] = GameBasics.TileResources.values
        categoryToInfos["Terrains"] = GameBasics.Terrains.values
        categoryToInfos["Tile Improvements"] = GameBasics.TileImprovements.values
        categoryToInfos["Units"] = GameBasics.Units.values
        categoryToInfos["Technologies"] = GameBasics.Technologies.values

        class Tutorial(var name:String, override var description:String):ICivilopedia{
            override fun toString() = name
        }
        categoryToInfos["Tutorials"] = Tutorials().getTutorialsOfLanguage("English").keys
                .filter { !it.startsWith("_") }
                .map { Tutorial(it.replace("_"," "),
                        Tutorials().getTutorials(it, UnCivGame.Current.settings.language).joinToString("\n\n")) }

        nameList.onClick {
            if(nameList.selected!=null) description.setText(civPediaEntries.get(nameList.selectedIndex).description)
        }
        nameList.style = List.ListStyle(nameList.style)
        nameList.style.fontColorSelected = Color.BLACK

        for (category in categoryToInfos.keys) {
            val button = TextButton(category.tr(), skin)
            button.style = TextButton.TextButtonStyle(button.style)
            button.style.checkedFontColor = Color.YELLOW
            categoryToButtons[category] = button
            button.onClick { select(category) }
            buttonTable.add(button)
        }
        select("Basics")

        val sp = ScrollPane(nameList)
        sp.setupOverscroll(5f, 1f, 200f)
        entryTable.add(sp).width(Value.percentWidth(0.25f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        entryTable.add(description).colspan(4).width(Value.percentWidth(0.65f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        // Simply changing these to x*width, y*height won't work

        buttonTable.width = stage.width
    }

}


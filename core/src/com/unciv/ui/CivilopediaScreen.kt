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
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import java.util.*

class CivilopediaScreen : CameraStageBaseScreen() {
    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        val buttonTable = Table()
        buttonTable.pad(15f)
        val entryTable = Table()
        val splitPane = SplitPane(buttonTable, entryTable, true, CameraStageBaseScreen.skin)
        splitPane.splitAmount = 0.2f
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        val label = "".toLabel()
        label.setWrap(true)

        val goToGameButton = TextButton("Close".tr(), CameraStageBaseScreen.skin)
        goToGameButton.onClick {
                game.setWorldScreen()
                dispose()
            }
        buttonTable.add(goToGameButton)

        val categoryToInfos = LinkedHashMap<String, Collection<ICivilopedia>>()

        val language = UnCivGame.Current.settings.language.replace(" ","_")
        val basicHelpFileName = if(Gdx.files.internal("jsons/BasicHelp_$language.json").exists())"BasicHelp_$language"
        else "BasicHelp"

        categoryToInfos["Basics"] = GameBasics.getFromJson(kotlin.Array<BasicHelp>::class.java, basicHelpFileName).toList()
        categoryToInfos["Buildings"] = GameBasics.Buildings.values
        categoryToInfos["Resources"] = GameBasics.TileResources.values
        categoryToInfos["Terrains"] = GameBasics.Terrains.values
        categoryToInfos["Tile Improvements"] = GameBasics.TileImprovements.values
        categoryToInfos["Units"] = GameBasics.Units.values
        categoryToInfos["Technologies"] = GameBasics.Technologies.values

        val nameList = List<ICivilopedia>(CameraStageBaseScreen.skin)

        val nameListClickListener = {
            if(nameList.selected!=null) label.setText(nameList.selected.description)
        }
        nameList.onClick (nameListClickListener)

        nameList.style = List.ListStyle(nameList.style)
        nameList.style.fontColorSelected = Color.BLACK

        val buttons = ArrayList<Button>()
        var first = true
        for (str in categoryToInfos.keys) {
            val button = TextButton(str.tr(), CameraStageBaseScreen.skin)
            button.style = TextButton.TextButtonStyle(button.style)
            button.style.checkedFontColor = Color.BLACK
            buttons.add(button)
            val buttonClicked = {
                val newArray = Array<ICivilopedia>()
                for (civilopediaEntry in categoryToInfos[str]!!.sortedBy { it.toString() })  // Alphabetical order
                    newArray.add(civilopediaEntry)
                nameList.setItems(newArray)
                nameList.selected = nameList.items.get(0)
                label.setText(nameList.selected.description)

                for (btn in buttons) btn.isChecked = false
                button.isChecked = true
            }
            button.onClick(buttonClicked)
            if (first) {// Fake-click the first button so that the user sees results immediately
                first = false
                buttonClicked()
            }

            buttonTable.add(button)
        }

        val sp = ScrollPane(nameList)
        sp.setupOverscroll(5f, 1f, 200f)
        entryTable.add(sp).width(Value.percentWidth(0.25f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        entryTable.add(label).colspan(4).width(Value.percentWidth(0.65f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable))
        // Simply changing these to x*width, y*height won't work

        buttonTable.width = stage.width
    }

}


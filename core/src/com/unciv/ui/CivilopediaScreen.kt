package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.utils.Array
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addClickListener
import java.util.*

class CivilopediaScreen : CameraStageBaseScreen() {
    init {
        val buttonTable = Table()
        buttonTable.pad(15f)
        val entryTable = Table()
        val splitPane = SplitPane(buttonTable, entryTable, true, CameraStageBaseScreen.skin)
        splitPane.setSplitAmount(0.2f)
        splitPane.setFillParent(true)

        stage.addActor(splitPane)

        val label = Label("", CameraStageBaseScreen.skin)
        label.setWrap(true)

        val goToGameButton = TextButton("Return \r\nto game", CameraStageBaseScreen.skin)
        goToGameButton.addClickListener {
                game.setWorldScreen()
                dispose()
            }
        buttonTable.add(goToGameButton)

        val map = LinkedHashMap<String, Collection<ICivilopedia>>()

        map["Basics"] = GameBasics.Helps.values
        map["Buildings"] = GameBasics.Buildings.values
        map["Resources"] = GameBasics.TileResources.values
        map["Terrains"] = GameBasics.Terrains.values
        map["Tile Improvements"] = GameBasics.TileImprovements.values
        map["Units"] = GameBasics.Units.values
        map["Technologies"] = GameBasics.Technologies.values

        val nameList = List<ICivilopedia>(CameraStageBaseScreen.skin)

        val nameListClickListener = {
            if(nameList.selected!=null) label.setText(nameList.selected.description)
        }
        nameList.addClickListener (nameListClickListener)

        nameList.style = List.ListStyle(nameList.style)
        nameList.style.fontColorSelected = Color.BLACK

        val buttons = ArrayList<Button>()
        var first = true
        for (str in map.keys) {
            val button = TextButton(str, CameraStageBaseScreen.skin)
            button.style = TextButton.TextButtonStyle(button.style)
            button.style.checkedFontColor = Color.BLACK
            buttons.add(button)
            val buttonClicked = {
                val newArray = Array<ICivilopedia>()
                for (civilopediaEntry in map[str]!!) newArray.add(civilopediaEntry)
                nameList.setItems(newArray)
                nameList.selected = nameList.items.get(0)
                label.setText(nameList.selected.description)

                for (btn in buttons) btn.isChecked = false
                button.isChecked = true
            }
            button.addClickListener(buttonClicked)
            if (first) {// Fake-click the first button so that the user sees results immediately
                first = false
                buttonClicked()
            }

            buttonTable.add(button).width(button.width * 0.7f)
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


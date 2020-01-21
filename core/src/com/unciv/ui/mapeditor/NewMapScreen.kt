package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.MapGenerator
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.MapParametersTable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

/** New map generation screen */
class NewMapScreen : PickerScreen() {

    private val mapParameters = MapParameters()
    private var generatedMap: TileMap? = null

    init {
        setDefaultCloseAction()

        val newMapScreenOptionsTable = Table(skin).apply {
            pad(10f)
            add("Map options".toLabel(fontSize = 24)).colspan(2).row()
            add(MapParametersTable(mapParameters)).colspan(2).row()
            pack()
        }


        topTable.apply {
            add(ScrollPane(newMapScreenOptionsTable)).height(topTable.parent.height)
            pack()
            setFillParent(true)
        }

        rightButtonSetEnabled(true)
        rightSideButton.onClick {
            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightButtonSetEnabled(false)

            thread(name = "MapGenerator") {
                try {
                    // Creating a new game can take a while and we don't want ANRs
                    val ruleset = RulesetCache.getBaseRuleset()
                    generatedMap = MapGenerator().generateMap(mapParameters, ruleset)
                } catch (exception: Exception) {
                    rightButtonSetEnabled(true)
                    val cantMakeThatMapPopup = Popup(this)
                    cantMakeThatMapPopup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr())
                        .row()
                    cantMakeThatMapPopup.addCloseButton()
                    cantMakeThatMapPopup.open()
                    Gdx.input.inputProcessor = stage
                }
            }

        }
    }

    override fun render(delta: Float) {
        if (generatedMap != null) {
            UncivGame.Current.setScreen(MapEditorScreen(generatedMap!!))
        }
        super.render(delta)
    }

    /** Changes the state and the text of the [rightSideButton] */
    private fun rightButtonSetEnabled(enabled: Boolean) {
        if (enabled) {
            rightSideButton.enable()
            rightSideButton.setText("Create".tr())
        } else {
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())
        }
    }
}
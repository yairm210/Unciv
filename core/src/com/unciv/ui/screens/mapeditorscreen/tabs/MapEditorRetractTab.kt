package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.mapgenerator.MapRegression
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Continents
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Landmass
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.NaturalWonders
import com.unciv.utils.Log
import kotlin.concurrent.thread

class MapEditorRetractTab(
    private val editorScreen: MapEditorScreen
) : Table(BaseScreen.skin) {

    private var isProcessing = false
    private var title: Label

    init {
        top()
        pad(4f)
        defaults().pad(2.5f)

        title = TITLE_NORMAL.toLabel(fontSize = 24)
        add(title).row()
        for (steps in MapGeneratorSteps.values()) {
            if (steps <= Landmass || steps == Continents) continue

            val label = steps.label.toLabel(fontSize = 18)
            val button = "Retract".toTextButton().onClick {
                if (isProcessing) {
                    ToastPopup("please wait", stage)
                } else {
                    retract(steps)
                }
            }

            add(label)
            add(button)
            row()
        }
    }

    private fun retract(step: MapGeneratorSteps) {
        if (step <= Landmass) return
        Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
        isProcessing = true
        title.setText(TITLE_PROCESS)

        thread(name = "MapRegression", isDaemon = true) {
            try {
                MapRegression.retractStep(step, editorScreen.tileMap, editorScreen.ruleset)
                Gdx.app.postRunnable {
                    if (step == NaturalWonders) editorScreen.naturalWondersNeedRefresh = true
                    editorScreen.mapHolder.updateTileGroups()
                    editorScreen.isDirty = true
                }

            } catch (exception: Exception) {
                Log.error("Exception while regress map", exception)
                Gdx.app.postRunnable {
                    Popup(editorScreen).apply {
                        addGoodSizedLabel("Failed to retract steps!") // todo: translate
                        row()
                        addCloseButton()
                    }.open()
                }
            } finally {
                Gdx.app.postRunnable {
                    isProcessing = false
                    Gdx.input.inputProcessor = editorScreen.stage
                    title.setText(TITLE_NORMAL)
                }
            }
        }
    }

    companion object {
        private const val TITLE_NORMAL = "Retract"
        private const val TITLE_PROCESS = "Processing"
    }

}

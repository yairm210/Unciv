package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.utils.*

class MapEditorScreen(): CameraStageBaseScreen() {
    val ruleset = RulesetCache.getBaseRuleset()
    var mapName = ""

    var tileMap = TileMap()
    lateinit var mapHolder: EditorMapHolder

    val tileEditorOptions = TileEditorOptionsTable(this)

    private val showHideEditorOptionsButton = ">".toTextButton()


    constructor(mapNameToLoad: String?) : this() {
        var mapToLoad = mapNameToLoad
        if (mapToLoad == null) {
            val existingSaves = MapSaver.getMaps()
            if (existingSaves.isNotEmpty())
                mapToLoad = existingSaves.first()
        }

        if (mapToLoad != null) {
            mapName = mapToLoad
            tileMap = MapSaver.loadMap(mapName)
        }

        initialize()
    }

    constructor(map: TileMap) : this() {
        tileMap = map
        initialize()
    }

    fun initialize() {
        tileMap.setTransients(ruleset,false)

        mapHolder = EditorMapHolder(this, tileMap)
        mapHolder.addTiles(stage.width)
        stage.addActor(mapHolder)
        stage.scrollFocus = mapHolder

        stage.addActor(tileEditorOptions)
        tileEditorOptions.setPosition(stage.width - tileEditorOptions.width, 0f)

        showHideEditorOptionsButton.labelCell.pad(10f)
        showHideEditorOptionsButton.pack()
        showHideEditorOptionsButton.onClick {
            if (showHideEditorOptionsButton.text.toString() == ">") {
                tileEditorOptions.addAction(Actions.moveTo(stage.width, 0f, 0.5f))
                showHideEditorOptionsButton.setText("<")
            } else {
                tileEditorOptions.addAction(Actions.moveTo(stage.width - tileEditorOptions.width, 0f, 0.5f))
                showHideEditorOptionsButton.setText(">")
            }
        }
        showHideEditorOptionsButton.setPosition(stage.width - showHideEditorOptionsButton.width - 10f,
                stage.height - showHideEditorOptionsButton.height - 10f)
        stage.addActor(showHideEditorOptionsButton)


        val optionsMenuButton = "Menu".toTextButton()
        optionsMenuButton.onClick {
            if (popups.any { it is MapEditorMenuPopup })
                return@onClick // already open
            MapEditorMenuPopup(this).open(force = true)
        }
        optionsMenuButton.label.setFontSize(24)
        optionsMenuButton.labelCell.pad(20f)
        optionsMenuButton.pack()
        optionsMenuButton.x = 30f
        optionsMenuButton.y = 30f
        stage.addActor(optionsMenuButton)

        mapHolder.addCaptureListener(object : InputListener() {
            var isDragging = false
            var isPainting = false
            var touchDownTime = System.currentTimeMillis()
            var lastDrawnTiles = HashSet<TileInfo>()

            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                touchDownTime = System.currentTimeMillis()
                return true
            }

            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                if (!isDragging) {
                    isDragging = true
                    val deltaTime = System.currentTimeMillis() - touchDownTime
                    if (deltaTime > 400) {
                        isPainting = true
                        stage.cancelTouchFocusExcept(this, mapHolder)
                    }
                }

                if (isPainting) {

                    for (tileInfo in lastDrawnTiles)
                        mapHolder.tileGroups[tileInfo]!!.hideCircle()
                    lastDrawnTiles.clear()

                    val stageCoords = mapHolder.actor.stageToLocalCoordinates(Vector2(event!!.stageX, event.stageY))
                    val centerTileInfo = mapHolder.getClosestTileTo(stageCoords)
                    if (centerTileInfo != null) {
                        val distance = tileEditorOptions.brushSize - 1

                        for (tileInfo in tileMap.getTilesInDistance(centerTileInfo.position, distance)) {
                            tileEditorOptions.updateTileWhenClicked(tileInfo)

                            tileInfo.setTransients()
                            mapHolder.tileGroups[tileInfo]!!.update()
                            mapHolder.tileGroups[tileInfo]!!.showCircle(Color.WHITE)

                            lastDrawnTiles.add(tileInfo)
                        }
                    }
                }
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                // Reset the whole map
                if (isPainting) {
                    mapHolder.updateTileGroups()
                    mapHolder.setTransients()
                }

                isDragging = false
                isPainting = false
            }
        })
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(MapEditorScreen(mapHolder.tileMap))
        }
    }
}



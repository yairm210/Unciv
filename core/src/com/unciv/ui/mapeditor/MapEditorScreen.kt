package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class MapEditorScreen(): BaseScreen() {
    var mapName = ""
    var tileMap = TileMap()
    var ruleset = Ruleset().apply { add(RulesetCache.getBaseRuleset()) }

    var gameSetupInfo = GameSetupInfo()
    lateinit var mapHolder: EditorMapHolder

    lateinit var mapEditorOptionsTable: MapEditorOptionsTable

    private val showHideEditorOptionsButton = ">".toTextButton()


    constructor(map: TileMap) : this() {
        tileMap = map
        checkAndFixMapSize()
        ruleset = RulesetCache.getComplexRuleset(map.mapParameters.mods, map.mapParameters.baseRuleset)
        initialize()
    }

    private fun initialize() {
        ImageGetter.setNewRuleset(ruleset)
        tileMap.setTransients(ruleset,false)
        tileMap.setStartingLocationsTransients()
        UncivGame.Current.translations.translationActiveMods = ruleset.mods

        mapHolder = EditorMapHolder(this, tileMap)
        mapHolder.addTiles(stage.width, stage.height)
        stage.addActor(mapHolder)
        stage.scrollFocus = mapHolder

        mapEditorOptionsTable = MapEditorOptionsTable(this)
        stage.addActor(mapEditorOptionsTable)
        mapEditorOptionsTable.setPosition(stage.width - mapEditorOptionsTable.width, 0f)

        showHideEditorOptionsButton.labelCell.pad(10f)
        showHideEditorOptionsButton.pack()
        showHideEditorOptionsButton.onClick {
            if (showHideEditorOptionsButton.text.toString() == ">") {
                mapEditorOptionsTable.addAction(Actions.moveTo(stage.width, 0f, 0.5f))
                showHideEditorOptionsButton.setText("<")
            } else {
                mapEditorOptionsTable.addAction(Actions.moveTo(stage.width - mapEditorOptionsTable.width, 0f, 0.5f))
                showHideEditorOptionsButton.setText(">")
            }
        }
        showHideEditorOptionsButton.setPosition(stage.width - showHideEditorOptionsButton.width - 10f,
                stage.height - showHideEditorOptionsButton.height - 10f)
        stage.addActor(showHideEditorOptionsButton)

        val openOptionsMenu = {
            if (popups.none { it is MapEditorMenuPopup })
                MapEditorMenuPopup(this).open(force = true)
        }
        val optionsMenuButton = "Menu".toTextButton()
        optionsMenuButton.onClick(openOptionsMenu)
        keyPressDispatcher[KeyCharAndCode.BACK] = openOptionsMenu
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
                        mapHolder.tileGroups[tileInfo]!!.forEach { it.hideCircle() }
                    lastDrawnTiles.clear()

                    val stageCoords = mapHolder.actor.stageToLocalCoordinates(Vector2(event!!.stageX, event.stageY))
                    val centerTileInfo = mapHolder.getClosestTileTo(stageCoords)
                    if (centerTileInfo != null) {
                        val distance = mapEditorOptionsTable.brushSize - 1

                        for (tileInfo in tileMap.getTilesInDistance(centerTileInfo.position, distance)) {
                            mapEditorOptionsTable.updateTileWhenClicked(tileInfo)

                            tileInfo.setTerrainTransients()
                            mapHolder.tileGroups[tileInfo]!!.forEach {
                                it.update()
                                it.showCircle(Color.WHITE)
                            }

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

    private fun checkAndFixMapSize() {
        val areaFromTiles = tileMap.values.size
        tileMap.mapParameters.run {
            val areaFromSize = getArea()
            if (areaFromSize == areaFromTiles) return
            Gdx.app.postRunnable {
                val message = ("Invalid map: Area ([$areaFromTiles]) does not match saved dimensions ([" +
                        displayMapDimensions() + "]).").tr() +
                        "\n" + "The dimensions have now been fixed for you.".tr()
                ToastPopup(message, this@MapEditorScreen, 4000L )
            }
            if (shape == MapShape.hexagonal) {
                mapSize = MapSizeNew(HexMath.getHexagonalRadiusForArea(areaFromTiles).toInt())
                return
            }

            // These mimic tileMap.max* without the abs()
            val minLatitude = (tileMap.values.map { it.latitude }.minOrNull() ?: 0f).toInt()
            val minLongitude = (tileMap.values.map { it.longitude }.minOrNull() ?: 0f).toInt()
            val maxLatitude = (tileMap.values.map { it.latitude }.maxOrNull() ?: 0f).toInt()
            val maxLongitude = (tileMap.values.map { it.longitude }.maxOrNull() ?: 0f).toInt()
            mapSize = MapSizeNew((maxLongitude - minLongitude + 1), (maxLatitude - minLatitude + 1) / 2)
        }
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(MapEditorScreen(mapHolder.tileMap))
        }
    }
}

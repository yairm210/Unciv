package com.unciv.ui.mapeditor

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ZoomableScrollPane
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.onClick


/**
 * This MapHolder is used both for the Map Editor and the Main Menu background!
 * @param parentScreen a MapEditorScreen or a MainMenuScreen
 */
class EditorMapHolder(
    parentScreen: BaseScreen,
    internal val tileMap: TileMap,
    private val onTileClick: (TileInfo) -> Unit
): ZoomableScrollPane(20f, 20f) {
    val editorScreen = parentScreen as? MapEditorScreen

    val tileGroups = HashMap<TileInfo, List<TileGroup>>()
    private lateinit var tileGroupMap: TileGroupMap<TileGroup>
    private val allTileGroups = ArrayList<TileGroup>()

    private var blinkAction: Action? = null

    private var savedCaptureListeners = emptyList<EventListener>()
    private var savedListeners = emptyList<EventListener>()

    init {
        if (editorScreen == null) touchable = Touchable.disabled
        continuousScrollingX = tileMap.mapParameters.worldWrap
        addTiles(parentScreen.stage)
        if (editorScreen != null) addCaptureListener(getDragPaintListener())
    }

    internal fun addTiles(stage: Stage) {

        val tileSetStrings = TileSetStrings()
        val daTileGroups = tileMap.values.map { TileGroup(it, tileSetStrings) }

        tileGroupMap = TileGroupMap(
            daTileGroups,
            continuousScrollingX)
        actor = tileGroupMap
        val mirrorTileGroups = tileGroupMap.getMirrorTiles()

        for (tileGroup in daTileGroups) {
            if (continuousScrollingX){
                val mirrorTileGroupLeft = mirrorTileGroups[tileGroup.tileInfo]!!.first
                val mirrorTileGroupRight = mirrorTileGroups[tileGroup.tileInfo]!!.second

                allTileGroups.add(tileGroup)
                allTileGroups.add(mirrorTileGroupLeft)
                allTileGroups.add(mirrorTileGroupRight)

                tileGroups[tileGroup.tileInfo] = listOf(tileGroup, mirrorTileGroupLeft, mirrorTileGroupRight)
            } else {
                tileGroups[tileGroup.tileInfo] = listOf(tileGroup)
                allTileGroups.add(tileGroup)
            }
        }

        for (tileGroup in allTileGroups) {

/* revisit when Unit editing is re-implemented
            // This is a hack to make the unit icons render correctly on the game, even though the map isn't part of a game
            // and the units aren't assigned to any "real" CivInfo
            //to do make safe the !!
            //to do worse - don't create a whole Civ instance per unit
            tileGroup.tileInfo.getUnits().forEach {
                it.civInfo = CivilizationInfo().apply {
                    nation = ruleset.nations[it.owner]!!
                }
            }
*/
            tileGroup.showEntireMap = true
            tileGroup.update()
            if (touchable != Touchable.disabled)
                tileGroup.onClick { onTileClick(tileGroup.tileInfo) }
        }

        setSize(stage.width, stage.height)

        layout()

        scrollPercentX = .5f
        scrollPercentY = .5f
        updateVisualScroll()
    }

    fun updateTileGroups() {
        for (tileGroup in allTileGroups)
            tileGroup.update()
    }

    fun setTransients() {
        for (tileInfo in tileGroups.keys)
            tileInfo.setTerrainTransients()
    }

    // This emulates `private TileMap.getOrNull(Int,Int)` and should really move there
    // still more efficient than `if (rounded in tileMap) tileMap[rounded] else null`
    private fun TileMap.getOrNull(pos: Vector2): TileInfo? {
        val x = pos.x.toInt()
        val y = pos.y.toInt()
        if (contains(x, y)) return get(x, y)
        return null
    }

    /**
     * Copy-pasted from [com.unciv.ui.worldscreen.WorldMapHolder.setCenterPosition]
     * TODO remove code duplication
     */
    fun setCenterPosition(vector: Vector2, blink: Boolean = false) {
        val tileGroup = allTileGroups.firstOrNull { it.tileInfo.position == vector } ?: return

        // The Y axis of [scrollY] is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        if (!scrollTo(tileGroup.x + tileGroup.width / 2, maxY - (tileGroup.y + tileGroup.width / 2)))
            return

        if (!blink) return

        removeAction(blinkAction) // so we don't have multiple blinks at once
        blinkAction = Actions.repeat(3, Actions.sequence(
            Actions.run { tileGroup.highlightImage.isVisible = false },
            Actions.delay(.3f),
            Actions.run { tileGroup.highlightImage.isVisible = true },
            Actions.delay(.3f)
        ))
        addAction(blinkAction) // Don't set it on the group because it's an actionless group
    }

    /*
    The ScrollPane interferes with the dragging listener of MapEditorToolsDrawer.
    Once the ZoomableScrollPane super is initialized, there are 3 listeners + 1 capture listener:
    listeners[0] = ZoomableScrollPane.getFlickScrollListener()
    listeners[1] = ZoomableScrollPane.addZoomListeners: override fun scrolled (MouseWheel)
    listeners[2] = ZoomableScrollPane.addZoomListeners: override fun zoom (Android pinch)
    captureListeners[0] = ScrollPane.addCaptureListener: touchDown, touchUp, touchDragged, mouseMoved
    Clearing and putting back the captureListener _should_ suffice, but in practice it doesn't.
    Therefore, save all listeners when they're hurting us, and put them back when needed.
    */
    internal fun killListeners() {
        savedCaptureListeners = captureListeners.toList()
        savedListeners = listeners.toList()
        clearListeners()
    }
    internal fun resurrectListeners() {
        val captureListenersToAdd = savedCaptureListeners
        savedCaptureListeners = emptyList()
        val listenersToAdd = savedListeners
        savedListeners = emptyList()
        for (listener in listenersToAdd) addListener(listener)
        for (listener in captureListenersToAdd) addCaptureListener(listener)
    }

    /** Factory to create the listener that does "paint by dragging"
     *  Should only be called if this MapHolder is used from MapEditorScreen
     */
    private fun getDragPaintListener(): InputListener {
        return object : InputListener() {
            var isDragging = false
            var isPainting = false
            var touchDownTime = System.currentTimeMillis()

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
                        stage.cancelTouchFocusExcept(this, this@EditorMapHolder)
                    }
                }
                if (!isPainting) return

                editorScreen!!.hideSelection()
                val stageCoords = actor?.stageToLocalCoordinates(Vector2(event!!.stageX, event.stageY)) ?: return
                val centerTileInfo = getClosestTileTo(stageCoords)
                    ?: return
                editorScreen.tabs.edit.paintTilesWithBrush(centerTileInfo)
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                // Reset the whole map
                if (isPainting) {
                    updateTileGroups()
                    setTransients()
                }

                isDragging = false
                isPainting = false
            }
        }
    }

    fun getClosestTileTo(stageCoords: Vector2): TileInfo? {
        val positionalCoords = tileGroupMap.getPositionalVector(stageCoords)
        val hexPosition = HexMath.world2HexCoords(positionalCoords)
        val rounded = HexMath.roundHexCoords(hexPosition)

        if (!tileMap.mapParameters.worldWrap)
            return tileMap.getOrNull(rounded)
        val wrapped = HexMath.getUnwrappedNearestTo(rounded, Vector2.Zero, tileMap.maxLongitude)
        //todo this works, but means getUnwrappedNearestTo fails - on the x-y == maxLongitude vertical
        return tileMap.getOrNull(wrapped) ?: tileMap.getOrNull(rounded)
    }
}

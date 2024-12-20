package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.BFS
import com.unciv.logic.map.mapgenerator.MapGenerationRandomness
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.logic.map.tile.TileNormalizer
import com.unciv.ui.screens.mapeditorscreen.tabs.MapEditorOptionsTab.TileMatchFuzziness
import com.unciv.utils.Log

class MapEditorEditTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val subTabs: TabbedPager
    private val brushTable = Table(skin)
    private val brushSlider: UncivSlider
    private val brushLabel = "Brush ([1]):".toLabel()
    private val brushCell: Cell<Actor>

    private var ruleset = editorScreen.ruleset
    internal val randomness = MapGenerationRandomness()  // for auto river

    enum class BrushHandlerType { None, Direct, Tile, Road, River, RiverFromTo }
    private var brushHandlerType = BrushHandlerType.None

    /** This applies the current brush to one tile **without** validation or transient updates */
    private var brushAction: (Tile)->Unit = {}
    /** Brush size: 1..5 means hexagon of radius x, -1 means floodfill */
    internal var brushSize = 1
        set(value) {
            field = value
            brushSlider.value = if (value < 0) 6f else value.toFloat()
        }
    /** Copy of same field in [MapEditorOptionsTab] */
    private var tileMatchFuzziness = TileMatchFuzziness.CompleteMatch

    /** Tile to run a river _from_ (both ends are set with the same tool, so we need the memory) */
    private var riverStartTile: Tile? = null
    /** Tile to run a river _to_ */
    private var riverEndTile: Tile? = null

    private enum class AllEditSubTabs(
        val caption: String,
        val key: Char,
        val icon: String,
        val instantiate: (MapEditorEditTab, Ruleset)->Table
    ) {
        Terrain("Terrain", 't', "OtherIcons/Terrains", { parent, ruleset -> MapEditorEditTerrainTab(parent, ruleset) }),
        TerrainFeatures("Features", 'f', "OtherIcons/Star", { parent, ruleset -> MapEditorEditFeaturesTab(parent, ruleset) }),
        NaturalWonders("Wonders", 'w', "OtherIcons/Star", { parent, ruleset -> MapEditorEditWondersTab(parent, ruleset) }),
        Resources("Resources", 'r', "OtherIcons/Resources", { parent, ruleset -> MapEditorEditResourcesTab(parent, ruleset) }),
        Improvements("Improvements", 'i', "OtherIcons/Improvements", { parent, ruleset -> MapEditorEditImprovementsTab(parent, ruleset) }),
        Rivers("Rivers", 'v', "OtherIcons/Star", { parent, ruleset -> MapEditorEditRiversTab(parent, ruleset) }),
        StartingLocations("Starting locations", 's', "OtherIcons/Nations", { parent, ruleset -> MapEditorEditStartsTab(parent, ruleset) }),
        // Units("Units", 'u', "OtherIcons/Shield", { parent, ruleset -> MapEditorEditUnitsTab(parent, ruleset) }),
    }

    init {
        top()

        brushTable.apply {
            pad(5f)
            defaults().pad(10f).left()
            add(brushLabel)
            brushCell = add().padLeft(0f)
            brushSlider = UncivSlider(
                "Brush",
                1f,6f,1f,
                initial = 1f,
                getTipText = { getBrushTip(it).tr() },
                tipType = UncivSlider.TipType.Auto
            ) {
                brushSize = if (it > 5f) -1 else it.toInt()
                brushLabel.setText("Brush ([${getBrushTip(it, true)}]):".tr())
            }
            add(brushSlider).padLeft(0f)
        }

        // TabbedPager parameters specify content page area. Assume subTabs will have the same headerHeight
        // as the master tabs, the 2f is for the separator, and the 10f for reduced header padding:
        val subTabsHeight = editorScreen.stage.height - 2 * headerHeight - brushTable.prefHeight - 2f + 10f
        val subTabsWidth = editorScreen.getToolsWidth()
        subTabs = TabbedPager(
            minimumHeight = subTabsHeight,
            maximumHeight = subTabsHeight,
            minimumWidth = subTabsWidth,
            maximumWidth = subTabsWidth,
            headerPadding = 5f,
            capacity = AllEditSubTabs.entries.size
        )

        for (page in AllEditSubTabs.entries) {
            // Empty tabs with placeholders, filled when activated()
            subTabs.addPage(page.caption, Group(), ImageGetter.getImage(page.icon), 20f,
                shortcutKey = KeyCharAndCode(page.key), disabled = true)
        }
        subTabs.selectPage(0)

        add(brushTable).fillX().row()
        addSeparator(Color.GRAY)
        add(subTabs).left().fillX().row()

        keyShortcuts.add('t') { selectPage(0) }
        keyShortcuts.add('f') { selectPage(1) }
        keyShortcuts.add('w') { selectPage(2) }
        keyShortcuts.add('r') { selectPage(3) }
        keyShortcuts.add('i') { selectPage(4) }
        keyShortcuts.add('v') { selectPage(5) }
        keyShortcuts.add('s') { selectPage(6) }
        keyShortcuts.add('u') { selectPage(7) }
        keyShortcuts.add('1') { brushSize = 1 }
        keyShortcuts.add('2') { brushSize = 2 }
        keyShortcuts.add('3') { brushSize = 3 }
        keyShortcuts.add('4') { brushSize = 4 }
        keyShortcuts.add('5') { brushSize = 5 }
        keyShortcuts.add(KeyCharAndCode.ctrl('f')) { brushSize = -1 }
    }

    private fun selectPage(index: Int) = subTabs.selectPage(index)

    private fun linkCivilopedia(brushActor: Actor, link: String) {
        if (link.isEmpty()) return
        brushActor.touchable = Touchable.enabled
        // As so often, doing the binding separately to avoid the tooltip
        brushActor.onActivation {
            editorScreen.openCivilopedia(link)
        }
        brushActor.keyShortcuts.add(KeyboardBinding.Civilopedia)
    }

    // "Normal" setBrush overload, using named RulesetObject icon
    fun setBrush(name: String, icon: String, handlerType: BrushHandlerType = BrushHandlerType.Tile,
                 pediaLink: String = icon, isRemove: Boolean = false,
                 applyAction: (Tile)->Unit) {
        brushHandlerType = handlerType
        val brushActor = FormattedLine(name, icon = icon, iconCrossed = isRemove).render(0f)
        linkCivilopedia(brushActor, pediaLink)
        brushCell.setActor(brushActor)
        brushAction = applyAction
    }
    
    // Helper overload for brushes using icons not existing as RulesetObject
    fun setBrush(handlerType: BrushHandlerType, name: String, icon: Actor, pediaLink: String, applyAction: (Tile)->Unit) {
        brushHandlerType = handlerType
        val brushActor = Table().apply {
            add(icon).padRight(10f)
            add(name.toLabel())
        }
        linkCivilopedia(brushActor, pediaLink)
        brushCell.setActor(brushActor)
        brushAction = applyAction
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (editorScreen.editTabsNeedRefresh) {
            // ruleset has changed
            ruleset = editorScreen.ruleset
            ImageGetter.setNewRuleset(ruleset)
            for (page in AllEditSubTabs.entries) {
                val tab = page.instantiate(this, ruleset)
                subTabs.replacePage(page.caption, tab)
                subTabs.setPageDisabled(page.caption, (tab as IMapEditorEditSubTabs).isDisabled())
            }
            brushHandlerType = BrushHandlerType.None
            editorScreen.editTabsNeedRefresh = false
        }

        editorScreen.tileClickHandler = this::tileClickHandler
        pager.setScrollDisabled(true)
        tileMatchFuzziness = editorScreen.tileMatchFuzziness
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        editorScreen.tileClickHandler = null
    }

    private fun tileClickHandler(tile: Tile) {
        if (brushSize < -1 || brushSize > 5 || brushHandlerType == BrushHandlerType.None) return
        if (editorScreen.mapHolder.isPanning || editorScreen.mapHolder.isZooming()) return
        editorScreen.hideSelection()

        when (brushHandlerType) {
            BrushHandlerType.None -> Unit
            BrushHandlerType.RiverFromTo ->
                selectRiverFromOrTo(tile)
            else ->
                paintTilesWithBrush(tile)
        }
    }

    private fun selectRiverFromOrTo(tile: Tile) {
        val tilesToHighlight = mutableSetOf(tile)
        if (tile.isLand) {
            // Land means river from. Start the river if we have a 'to', choose a 'to' if not.
            riverStartTile = tile
            if (riverEndTile != null) return paintRiverFromTo()
            val riverGenerator = RiverGenerator(editorScreen.tileMap, randomness, ruleset)
            riverEndTile = riverGenerator.getClosestWaterTile(tile)
            if (riverEndTile != null) tilesToHighlight += riverEndTile!!
        } else {
            // Water means river to. Start the river if we have a 'from'
            riverEndTile = tile
            if (riverStartTile != null) return paintRiverFromTo()
        }
        for (tileToHighlight in tilesToHighlight) editorScreen.highlightTile(tileToHighlight, Color.BLUE)
    }
    private fun paintRiverFromTo() {
        val resultingTiles = mutableSetOf<Tile>()
        randomness.seedRNG(editorScreen.newMapParameters.seed)
        try {
            val riverGenerator = RiverGenerator(editorScreen.tileMap, randomness, ruleset)
            riverGenerator.spawnRiver(riverStartTile!!, riverEndTile!!, resultingTiles)
            MapGenerator(ruleset).convertTerrains(resultingTiles)
        } catch (ex: Exception) {
            Log.error("Exception while generating rivers", ex)
            ToastPopup("River generation failed!", editorScreen)
        }
        riverStartTile = null
        riverEndTile = null
        editorScreen.isDirty = true
        for (tile in resultingTiles) editorScreen.updateAndHighlight(tile, Color.SKY)
    }

    internal fun paintTilesWithBrush(tile: Tile) {
        val tiles =
            if (brushSize == -1) {
                val bfs = BFS(tile) { it.isSimilarEnough(tile) }
                bfs.stepToEnd()
                bfs.getReachedTiles().toSet()
            } else {
                tile.getTilesInDistance(brushSize - 1).toSet()
            }
        
        for (tileToPaint in tiles) {
            when (brushHandlerType) {
                BrushHandlerType.Direct -> directPaintTile(tileToPaint)
                BrushHandlerType.River -> directPaintTile(tileToPaint)
                BrushHandlerType.Tile -> paintTile(tileToPaint)
                BrushHandlerType.Road -> paintTile(tileToPaint)
                else -> {} // other cases can't reach here
            }
        }
        
        // Adjacent tiles could have images changed as well, due to rivers/edge tiles/roads
        val tilesToUpdate = tiles.flatMap { it.neighbors + it }.toSet()
        for (tileToUpdate in tilesToUpdate) editorScreen.updateTile(tileToUpdate)
    }

    /** Used for starting locations - no temp tile as brushAction needs to access tile.tileMap */
    private fun directPaintTile(tile: Tile) {
        brushAction(tile)
        editorScreen.isDirty = true
        editorScreen.highlightTile(tile)
    }

    /** apply brush to a single tile */
    private fun paintTile(tile: Tile): Boolean {
        // Approach is "Try - matches - leave or revert" because an off-map simulation would fail some tile filters
        val savedTile = tile.clone()
        val paintedTile = tile.clone()
        brushAction(paintedTile)
        paintedTile.ruleset = ruleset
        try {
            paintedTile.setTerrainTransients()
        } catch (ex: Exception) {
            val message = ex.message ?: throw ex
            if (!message.endsWith("not exist in this ruleset!")) throw ex
            ToastPopup(message, editorScreen)
        }

        brushAction(tile)
        tile.setTerrainTransients()
        TileNormalizer.normalizeToRuleset(tile, ruleset)
        if (!paintedTile.isSimilarEnough(tile)) {
            // revert tile to original state
            tile.applyFrom(savedTile)
            return false
        }

        if (tile.naturalWonder != savedTile.naturalWonder)
            editorScreen.naturalWondersNeedRefresh = true
        editorScreen.isDirty = true
        editorScreen.highlightTile(tile)
        return true
    }

    private fun Tile.isSimilarEnough(other: Tile) = when {
        tileMatchFuzziness <= TileMatchFuzziness.CompleteMatch &&
                improvement != other.improvement ||
                roadStatus != other.roadStatus -> false
        tileMatchFuzziness <= TileMatchFuzziness.NoImprovement &&
                resource != other.resource -> false
        tileMatchFuzziness <= TileMatchFuzziness.BaseAndFeatures &&
                terrainFeatures.toSet() != other.terrainFeatures.toSet() -> false
        tileMatchFuzziness <= TileMatchFuzziness.BaseTerrain &&
                baseTerrain != other.baseTerrain -> false
        tileMatchFuzziness <= TileMatchFuzziness.LandOrWater &&
                isLand != other.isLand -> false
        else -> naturalWonder == other.naturalWonder
    }

    private fun Tile.applyFrom(other: Tile) {
        // 90% copy w/o position, improvement times or transients. Add units once Unit paint is in.
        baseTerrain = other.baseTerrain
        setTerrainFeatures(other.terrainFeatures)
        resource = other.resource
        improvement = other.improvement
        naturalWonder = other.naturalWonder
        roadStatus = other.roadStatus
        hasBottomLeftRiver = other.hasBottomLeftRiver
        hasBottomRightRiver = other.hasBottomRightRiver
        hasBottomRiver = other.hasBottomRiver
        setTerrainTransients()
    }

    companion object {
        private fun getBrushTip(value: Float, abbreviate: Boolean = false) = when {
            value <= 5f -> value.toInt().tr()
            abbreviate -> "Floodfill_Abbreviation"
            else -> "Floodfill"
        }
    }
}

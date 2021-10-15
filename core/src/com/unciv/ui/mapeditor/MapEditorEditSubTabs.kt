package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.mapeditor.MapEditorEditTab.BrushHandlerType
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*

internal interface IMapEditorEditSubTabs {
    fun isDisabled(): Boolean
}


/** Implements the Map editor Edit-Terrains UI Tab */
class MapEditorEditTerrainTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        add(MarkupRenderer.render(
            getTerrains(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                tile.baseTerrain = it
                tile.naturalWonder = null
            }
        }).fillX().row()
    }

    private fun allTerrains() = ruleset.terrains.values.asSequence()
        .filter { it.type.isBaseTerrain }
    private fun getTerrains() = allTerrains()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .toList()

    override fun isDisabled() = false // allTerrains().none() // wanna see _that_ mod...
}


/** Implements the Map editor Edit-Features UI Tab */
class MapEditorEditFeaturesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        allowedFeatures().firstOrNull()?.let { addFeatures(it) }
    }

    private fun addFeatures(firstFeature: Terrain) {
        val eraserIcon = "Terrain/${firstFeature.name}"
        val eraser = FormattedLine("Remove features", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove feature", eraserIcon) { tile ->
                tile.terrainFeatures.clear()
            }
        } }).fillX().left().row()
        add(MarkupRenderer.render(
            getFeatures(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                if (it !in tile.terrainFeatures)
                    tile.terrainFeatures.add(it)
            }
        }).fillX().left().row()
    }

    private fun allowedFeatures() = ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.TerrainFeature }
    private fun getFeatures() = allowedFeatures()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .toList()

    override fun isDisabled() = allowedFeatures().none()
}


/** Implements the Map editor Edit-NaturalWonders UI Tab */
class MapEditorEditWondersTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()

        add(MarkupRenderer.render(
            getWonders(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                // Normally the caller would ensure compliance, but here we make an exception - place it no matter what
                tile.baseTerrain = ruleset.terrains[it]!!.turnsInto!!
                tile.terrainFeatures.clear()
                tile.naturalWonder = it
            }
        }).fillX().row()
    }

    private fun allowedWonders() = ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.NaturalWonder }
    private fun getWonders() = allowedWonders()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .toList()

    override fun isDisabled() = allowedWonders().none()
}


/** Implements the Map editor Edit-Resources UI Tab */
class MapEditorEditResourcesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        allowedResources().firstOrNull()?.let { addResources(it) }
    }

    private fun addResources(firstResource: TileResource) {
        val eraserIcon = "Resource/${firstResource.name}"
        val eraser = FormattedLine("Remove resource", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove resource", eraserIcon) { tile ->
                tile.resource = null
            }
        } }).fillX().left().row()
        add(MarkupRenderer.render(
            getResources(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Resource/$it") { tile ->
                tile.resource = it
            }
        }).fillX().left().row()
    }

    private fun allowedResources() = ruleset.tileResources.values.asSequence()
        .filter { !it.hasUnique("Can only be created by Mercantile City-States") }  //todo type-i-fy
    private fun getResources() = allowedResources()
        .map { FormattedLine(it.name, it.name, "Resource/${it.name}", size = 32) }
        .toList()

    override fun isDisabled() = allowedResources().none()
}


/** Implements the Map editor Edit-Improvements UI Tab */
class MapEditorEditImprovementsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        allowedImprovements().firstOrNull()?.let { addImprovements(it) }
    }

    private fun addImprovements(firstImprovement: TileImprovement) {
        val eraserIcon = "Improvement/${firstImprovement.name}"
        val eraser = FormattedLine("Remove improvement", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove improvement", eraserIcon) { tile ->
                tile.improvement = null
                tile.roadStatus = RoadStatus.None
            }
        } }).fillX().left().row()
        add(MarkupRenderer.render(
            getImprovements(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            val road = RoadStatus.values().firstOrNull { r -> r.name == it }
            if (road != null)
                editTab.setBrush(BrushHandlerType.Road, it, "Improvement/$it") { tile ->
                    tile.roadStatus = road
                }
            else
                editTab.setBrush(it, "Improvement/$it") { tile ->
                    tile.improvement = it
                }
        }).fillX().left().row()
    }

    private fun allowedImprovements() = ruleset.tileImprovements.values.asSequence()
        .filter { improvement ->
            //todo This should really be easier, the attributes should allow such a test in one go
            disallowImprovements.none { improvement.name.startsWith(it) }
        }
    private fun getImprovements() = allowedImprovements()
        .map { FormattedLine(it.name, it.name, "Improvement/${it.name}", size = 32) }
        .toList()

    override fun isDisabled() = allowedImprovements().none()

    companion object {
        private val disallowImprovements = listOf(
            "Remove ", "Cancel improvement", "City center", Constants.barbarianEncampment
        )
    }
}


/** Implements the Map editor Edit-StartingLocations UI Tab */
class MapEditorEditStartsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    private val collator = UncivGame.Current.settings.getCollatorFromLocale()

    init {
        top()
        allowedNations().firstOrNull()?.let { addNations(it) }
    }

    private fun addNations(firstNation: Nation) {
        val eraserIcon = "Nation/${firstNation.name}"
        val eraser = FormattedLine("Remove starting locations", icon = eraserIcon, size = 24, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush(BrushHandlerType.Direct, "Remove starting locations", eraserIcon) { tile ->
                tile.tileMap.removeStartingLocations(tile.position)
            }
        } }).fillX().left().row()
        add(MarkupRenderer.render(
            getNations(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(BrushHandlerType.Direct, it, "Nation/$it") { tile ->
                // toggle the starting location here, note this allows
                // both multiple locations per nation and multiple nations per tile
                if (!tile.tileMap.addStartingLocation(it, tile))
                    tile.tileMap.removeStartingLocation(it, tile)
            }
        }).fillX().left().row()
    }

    private fun allowedNations() = ruleset.nations.values.asSequence()
        .filter { it.name !in disallowNations }
    private fun getNations() = allowedNations()
        .sortedWith(compareBy<Nation>{ it.isCityState() }.thenBy(collator, { it.name.tr() }))
        .map { FormattedLine("[${it.name}] starting location", it.name, "Nation/${it.name}", size = 24) }
        .toList()

    override fun isDisabled() = allowedNations().none()

    companion object {
        private val disallowNations = setOf(Constants.spectator, Constants.barbarians)
    }
}


/** Implements the Map editor Edit-Rivers UI Tab */
class MapEditorEditRiversTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs, TabbedPager.IPageActivation {
    private val iconSize = 50f
    private val showOnTerrain = ruleset.terrains.values.asSequence()
        .filter { it.type.isBaseTerrain && !it.isRough() }
        .sortedByDescending { it.production * 2 + it.food }
        .firstOrNull()
        ?: ruleset.terrains[Constants.plains]
        ?: ruleset.terrains.values.first()

    init {
        defaults().pad(10f).left()
        val removeLine = Table().apply {
            add(getRemoveRiverIcon()).padRight(10f)
            add("Remove rivers".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.River,"Remove rivers", getRemoveRiverIcon()) { tile ->
                    tile.hasBottomLeftRiver = false
                    tile.hasBottomRightRiver = false
                    tile.hasBottomRiver = false
                    // User probably expects all six edges to be cleared
                    val x = tile.position.x.toInt()
                    val y = tile.position.y.toInt()
                    tile.tileMap.getIfTileExistsOrNull(x, y + 1)?.hasBottomLeftRiver = false
                    tile.tileMap.getIfTileExistsOrNull(x + 1, y)?.hasBottomRightRiver = false
                    tile.tileMap.getIfTileExistsOrNull(x + 1, y + 1)?.hasBottomRiver = false
                }
            }
        }
        add(removeLine).row()

        val leftRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.Left)).padRight(10f)
            add("Bottom left river".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.Direct,"Bottom left river", getTileGroupWithRivers(RiverEdge.Left)) { tile ->
                    tile.hasBottomLeftRiver = !tile.hasBottomLeftRiver
                }
            }
        }
        add(leftRiverLine).row()

        val bottomRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.Bottom)).padRight(10f)
            add("Bottom river".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.Direct,"Bottom river", getTileGroupWithRivers(RiverEdge.Bottom)) { tile ->
                    tile.hasBottomRiver = !tile.hasBottomRiver
                }
            }
        }
        add(bottomRiverLine).row()

        val rightRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.Right)).padRight(10f)
            add("Bottom right river".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.Direct,"Bottom right river", getTileGroupWithRivers(RiverEdge.Right)) { tile ->
                    tile.hasBottomRightRiver = !tile.hasBottomRightRiver
                }
            }
        }
        add(rightRiverLine).row()

        //todo this needs a better icon
        val spawnRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.All)).padRight(10f)
            add("Spawn river from/to".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(
                    BrushHandlerType.RiverFromTo,
                    name = "Spawn river from/to",
                    icon = getTileGroupWithRivers(RiverEdge.All),
                    applyAction = {}  // Actual effect done via BrushHandlerType
                )
            }
        }
        add(spawnRiverLine).row()
    }

    override fun isDisabled() = false

    override fun activated(index: Int) {
        editTab.brushSize = 1
    }

    private fun TileInfo.makeTileGroup(): TileGroup {
        ruleset = this@MapEditorEditRiversTab.ruleset
        setTerrainTransients()
        return TileGroup(this, TileSetStrings(), iconSize * 36f/54f).apply {
            showEntireMap = true
            forMapEditorIcon = true
            update()
        }
    }

    private enum class RiverEdge { Left, Bottom, Right, All }
    private fun getTileGroupWithRivers(edge: RiverEdge) =
        TileInfo().apply {
            baseTerrain = showOnTerrain.name
            when (edge) {
                RiverEdge.Left -> hasBottomLeftRiver = true
                RiverEdge.Bottom -> hasBottomRiver = true
                RiverEdge.Right -> hasBottomRightRiver = true
                RiverEdge.All -> {
                    hasBottomLeftRiver = true
                    hasBottomRightRiver = true
                    hasBottomRiver = true
                }
            }
        }.makeTileGroup()
    private fun getRemoveRiverIcon() = Group().apply {
        isTransform = false
        setSize(iconSize, iconSize)
        val tileGroup = getTileGroupWithRivers(RiverEdge.All)
        tileGroup.center(this)
        addActor(tileGroup)
        val cross = ImageGetter.getRedCross(iconSize * 0.7f, 1f)
        cross.center(this)
        addActor(cross)
    }
    private fun getRiverIcon(edge: RiverEdge) = Group().apply {
        // wrap same as getRemoveRiverIcon so the icons align the same (using getTileGroupWithRivers directly works but looks ugly - reason unknown to me)
        isTransform = false
        setSize(iconSize, iconSize)
        val tileGroup = getTileGroupWithRivers(edge)
        tileGroup.center(this)
        addActor(tileGroup)
    }
}


/** Implements the Map editor Edit-Units UI Tab */
class MapEditorEditUnitsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin), IMapEditorEditSubTabs {
    init {
        add("Work in progress".toLabel(Color.FIREBRICK, 24))
    }

    override fun isDisabled() = true
}

package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tile.*
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.screens.mapeditorscreen.tabs.MapEditorEditTab.BrushHandlerType

internal interface IMapEditorEditSubTabs {
    fun isDisabled(): Boolean
}


/** Implements the Map editor Edit-Terrains UI Tab */
class MapEditorEditTerrainTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).fillX().left()
        add(
            MarkupRenderer.render(
            getTerrains(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                tile.baseTerrain = it
                tile.naturalWonder = null
            }
        }).row()
    }

    private fun allTerrains() = ruleset.terrains.values.asSequence()
        .filter { it.type.isBaseTerrain }
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor, StateForConditionals.IgnoreConditionals) }
    private fun getTerrains() = allTerrains()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .asIterable()

    override fun isDisabled() = false // allTerrains().none() // wanna see _that_ mod...
}


/** Implements the Map editor Edit-Features UI Tab */
class MapEditorEditFeaturesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).fillX().left()
        allowedFeatures().firstOrNull()?.let { addFeatures(it) }
    }

    private fun addFeatures(firstFeature: Terrain) {
        val eraserIcon = "Terrain/${firstFeature.name}"
        val eraser = FormattedLine("Remove features", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove features", eraserIcon, pediaLink = "", isRemove = true) { tile ->
                tile.removeTerrainFeatures()
            }
        } }).padBottom(0f).row()
        add(
            MarkupRenderer.render(
            getFeatures(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                if (it !in tile.terrainFeatures)
                    tile.addTerrainFeature(it)
            }
        }).padTop(0f).row()
    }

    private fun allowedFeatures() = ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.TerrainFeature }
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor, StateForConditionals.IgnoreConditionals) }
    private fun getFeatures() = allowedFeatures()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .asIterable()

    override fun isDisabled() = allowedFeatures().none()
}


/** Implements the Map editor Edit-NaturalWonders UI Tab */
class MapEditorEditWondersTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).fillX().left()
        add(
            MarkupRenderer.render(
            getWonders(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                // Normally the caller would ensure compliance, but here we make an exception - place it no matter what
                if (ruleset.terrains[it]!!.turnsInto != null)
                    tile.baseTerrain = ruleset.terrains[it]!!.turnsInto!!
                tile.removeTerrainFeatures()
                tile.naturalWonder = it
            }
        }).row()
    }

    private fun allowedWonders() = ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.NaturalWonder }
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor, StateForConditionals.IgnoreConditionals) }
    private fun getWonders() = allowedWonders()
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
        .asIterable()

    override fun isDisabled() = allowedWonders().none()
}


/** Implements the Map editor Edit-Resources UI Tab */
class MapEditorEditResourcesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).fillX().left()
        allowedResources().firstOrNull()?.let { addResources(it) }
    }

    private fun addResources(firstResource: TileResource) {
        val eraserIcon = "Resource/${firstResource.name}"
        val eraser = FormattedLine("Remove resource", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove resource", eraserIcon, pediaLink = "", isRemove = true) { tile ->
                tile.resource = null
                tile.resourceAmount = 0
            }
        } }).padBottom(0f).row()
        add(
            MarkupRenderer.render(
            getResources(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) { resourceName ->
            val resource = ruleset.tileResources[resourceName]!!
            editTab.setBrush(resourceName, resource.makeLink()) {
                if (it.resource == resourceName && resource.resourceType == ResourceType.Strategic)
                    it.resourceAmount = (it.resourceAmount + 1).coerceAtMost(42)
                else
                    it.setTileResource(resource, rng = editTab.randomness.RNG)
            }
        }).padTop(0f).row()
    }

    private fun allowedResources() = ruleset.tileResources.values.asSequence()
        .filterNot { it.hasUnique(UniqueType.CityStateOnlyResource) }
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor, StateForConditionals.IgnoreConditionals) }
    private fun getResources(): Iterable<FormattedLine> = sequence {
        var lastGroup = ResourceType.Bonus
        for (resource in allowedResources()) {
            val name = resource.name
            if (resource.resourceType != lastGroup) {
                lastGroup = resource.resourceType
                yield(FormattedLine(separator = true, color = "#888"))
            }
            yield (FormattedLine(name, name, "Resource/$name", size = 32))
        }
    }.asIterable()

    override fun isDisabled() = allowedResources().none()
}


/** Implements the Map editor Edit-Improvements UI Tab */
class MapEditorEditImprovementsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).fillX().left()
        allowedImprovements().firstOrNull()?.let { addImprovements(it) }
    }

    private fun addImprovements(firstImprovement: TileImprovement) {
        val eraserIcon = "Improvement/${firstImprovement.name}"
        val eraser = FormattedLine("Remove improvement", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove improvement", eraserIcon, pediaLink = "", isRemove = true) { tile ->
                tile.removeImprovement()
                tile.removeRoad()
            }
        } }).padBottom(0f).row()
        add(
            MarkupRenderer.render(
            getImprovements(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            val road = RoadStatus.entries.firstOrNull { r -> r.name == it }
            if (road != null)
                editTab.setBrush(it, "Improvement/$it", handlerType = BrushHandlerType.Road) { tile ->
                    tile.roadStatus = if (tile.roadStatus == road) RoadStatus.None else road
                }
            else
                editTab.setBrush(it, "Improvement/$it") { tile ->
                    tile.setImprovement(it)
                }
        }).padTop(0f).row()
    }

    private fun allowedImprovements() = ruleset.tileImprovements.values.asSequence()
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor, StateForConditionals.IgnoreConditionals) }
    private fun getImprovements(): Iterable<FormattedLine> = sequence {
        var lastGroup = 0
        for (improvement in allowedImprovements()) {
            val name = improvement.name
            val group = improvement.group()
            if (group != lastGroup) {
                lastGroup = group
                yield(FormattedLine(separator = true, color = "#888"))
            }
            yield (FormattedLine(name, name, "Improvement/$name", size = 32))
        }
    }.asIterable()

    override fun isDisabled() = allowedImprovements().none()

    companion object {
        private fun TileImprovement.group() = when {
            RoadStatus.entries.any { it.name == name } -> 2
            "Great Improvement" in uniques -> 3
            uniqueTo != null -> 4
            "Unpillagable" in uniques -> 5
            else -> 0
        }
    }
}


/** Implements the Map editor Edit-StartingLocations UI Tab */
class MapEditorEditStartsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    private val collator = UncivGame.Current.settings.getCollatorFromLocale()
    private val usageOptionGroup = ButtonGroup<CheckBox>()

    init {
        top()
        defaults().pad(10f).fillX().left()
        allowedNations().firstOrNull()?.let { addNations(it) }
    }

    private fun String.spectatorToAnyCiv() = if (this == Constants.spectator) "Any Civ" else this

    private fun addNations(firstNation: Nation) {
        val eraserIcon = "Nation/${firstNation.name}"
        val eraser = FormattedLine("Remove starting locations", icon = eraserIcon, size = 24, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove", eraserIcon, handlerType = BrushHandlerType.Direct, pediaLink = "", isRemove = true) { tile ->
                tile.tileMap.removeStartingLocations(tile.position)
            }
        } }).padBottom(0f).row()

        addUsage()

        // Create the nation list with the spectator nation included, and shown/interpreted as "Any Civ" starting location.
        // We use Nation/Spectator because it hasn't been used yet and we need an icon within the Nation.
        add(
            MarkupRenderer.render(
                getNations(),
                iconDisplay = FormattedLine.IconDisplay.NoLink
            ) {
                UncivGame.Current.musicController.chooseTrack(it, MusicMood.Theme, MusicTrackChooserFlags.setSpecific)
                val icon = "Nation/$it"
                val pediaLink = if (it == Constants.spectator) "" else icon
                val isMajorCiv = ruleset.nations[it]?.isMajorCiv ?: false
                val selectedUsage = if (isMajorCiv) TileMap.StartingLocation.Usage.entries[usageOptionGroup.checkedIndex]
                    else TileMap.StartingLocation.Usage.Normal
                editTab.setBrush(it.spectatorToAnyCiv(), icon, BrushHandlerType.Direct, pediaLink) { tile ->
                    // toggle the starting location here, note this allows
                    // both multiple locations per nation and multiple nations per tile
                    if (!tile.tileMap.addStartingLocation(it, tile, selectedUsage))
                        tile.tileMap.removeStartingLocation(it, tile)
                }
            }
        ).padTop(0f).row()
    }

    private fun allowedNations() = ruleset.nations.values.asSequence()
        .filterNot { it.hasUnique(UniqueType.ExcludedFromMapEditor) }
    private fun getNations() = allowedNations()
        .sortedWith(
            compareBy<Nation> { !it.isSpectator }
                .thenBy { it.isCityState }
                .thenBy(collator) { it.name.tr(hideIcons = true) }
        ).map {
            FormattedLine("[${it.name.spectatorToAnyCiv()}] starting location", link = it.name, icon = "Nation/${it.name}", size = 24)
        }.asIterable()

    private fun addUsage() {
        val table = Table()
        table.defaults().pad(5f)
        table.add("Use for new game \"Select players\" button:".toLabel()).colspan(3).row()
        val defaultUsage = TileMap.StartingLocation.Usage.default
        for (usage in TileMap.StartingLocation.Usage.entries) {
            val checkBox = CheckBox(usage.label.tr(), skin)
            table.add(checkBox)
            usageOptionGroup.add(checkBox)
            checkBox.isChecked = usage == defaultUsage
        }
        add(table).row()
    }

    override fun isDisabled() = allowedNations().none()
}


/** Implements the Map editor Edit-Rivers UI Tab */
class MapEditorEditRiversTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs, TabbedPager.IPageExtensions {
    private val iconSize = 50f
    private val showOnTerrain = ruleset.terrains.values.asSequence()
        .filter { it.type.isBaseTerrain && !it.isRough() }
        .sortedByDescending { it.production * 2 + it.food }
        .firstOrNull()
        ?: ruleset.terrains[Constants.plains]
        ?: ruleset.terrains.values.first()

    init {
        val pediaLink = "Terrain/River"

        top()
        defaults().pad(10f).left()
        val removeLine = Table().apply {
            add(getRemoveRiverIcon()).padRight(10f)
            add("Remove rivers".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.River,"Remove rivers", getRemoveRiverIcon(), pediaLink) { tile ->
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
                editTab.setBrush(BrushHandlerType.Direct,"Bottom left river", getTileGroupWithRivers(
                    RiverEdge.Left
                ), pediaLink) { tile ->
                    tile.hasBottomLeftRiver = !tile.hasBottomLeftRiver
                }
            }
        }
        add(leftRiverLine).row()

        val bottomRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.Bottom)).padRight(10f)
            add("Bottom river".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.Direct,"Bottom river", getTileGroupWithRivers(
                    RiverEdge.Bottom
                ), pediaLink) { tile ->
                    tile.hasBottomRiver = !tile.hasBottomRiver
                }
            }
        }
        add(bottomRiverLine).row()

        val rightRiverLine = Table().apply {
            add(getRiverIcon(RiverEdge.Right)).padRight(10f)
            add("Bottom right river".toLabel(fontSize = 32))
            onClick {
                editTab.setBrush(BrushHandlerType.Direct,"Bottom right river", getTileGroupWithRivers(
                    RiverEdge.Right
                ), pediaLink) { tile ->
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
                    pediaLink = pediaLink,
                    applyAction = {}  // Actual effect done via BrushHandlerType
                )
            }
        }
        add(spawnRiverLine).row()
    }

    override fun isDisabled() = false

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        editTab.brushSize = 1
    }

    private fun Tile.makeTileGroup(): TileGroup {
        ruleset = this@MapEditorEditRiversTab.ruleset
        setTerrainTransients()
        return TileGroup(this, TileSetStrings(ruleset, UncivGame.Current.settings), iconSize * 36f/54f).apply {
            isForceVisible = true
            isForMapEditorIcon = true
            update()
        }
    }

    private enum class RiverEdge { Left, Bottom, Right, All }
    private fun getTileGroupWithRivers(edge: RiverEdge) =
        Tile().apply {
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
    private fun getRemoveRiverIcon() =
        ImageGetter.getCrossedImage(getTileGroupWithRivers(RiverEdge.All), iconSize)
    private fun getRiverIcon(edge: RiverEdge) = NonTransformGroup().apply {
        // wrap same as getRemoveRiverIcon so the icons align the same (using getTileGroupWithRivers directly works but looks ugly - reason unknown to me)
        setSize(iconSize, iconSize)
        val tileGroup = getTileGroupWithRivers(edge)
        tileGroup.center(this)
        addActor(tileGroup)
    }
}


/** Implements the Map editor Edit-Units UI Tab */
@Suppress("unused")
class MapEditorEditUnitsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(BaseScreen.skin), IMapEditorEditSubTabs {
    init {
        top()
        defaults().pad(10f).left()
        add("Work in progress".toLabel(Color.FIREBRICK, 24))
    }

    override fun isDisabled() = true
}

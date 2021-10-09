package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class MapEditorEditTerrainTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

        add(MarkupRenderer.render(
            getTerrains(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                tile.baseTerrain = it
            }
        }).fillX().row()
    }

    private fun getTerrains() = ruleset.terrains.values
        .filter { it.type.isBaseTerrain }
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
}

class MapEditorEditFeaturesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

        ruleset.terrains.values
            .firstOrNull { it.type == TerrainType.TerrainFeature }
            ?.let { addFeatures(it) }
    }

    private fun addFeatures(firstFeature: Terrain) {
        val eraserIcon = "Terrain/${firstFeature.name}"
        val eraser = FormattedLine("Remove feature", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove feature", eraserIcon) { tile ->
                tile.terrainFeatures.clear()
            }
        } }).fillX().row()
        add(MarkupRenderer.render(
            getFeatures(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Terrain/$it") { tile ->
                if (it !in tile.terrainFeatures)
                    tile.terrainFeatures.add(it)
            }
        }).fillX().row()
    }

    private fun getFeatures() = ruleset.terrains.values
        .filter { it.type == TerrainType.TerrainFeature }
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
}

class MapEditorEditWondersTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

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

    private fun getWonders() = ruleset.terrains.values
        .filter { it.type == TerrainType.NaturalWonder }
        .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", size = 32) }
}

class MapEditorEditResourcesTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

        allowedResources().firstOrNull()?.let { addResources(it) }
    }

    private fun addResources(firstResource: TileResource) {
        val eraserIcon = "Resource/${firstResource.name}"
        val eraser = FormattedLine("Remove resource", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove resource", eraserIcon) { tile ->
                tile.resource = null
            }
        } }).fillX().row()
        add(MarkupRenderer.render(
            getResources().toList(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Resource/$it") { tile ->
                tile.resource = it
            }
        }).fillX().row()
    }

    private fun allowedResources() = ruleset.tileResources.values.asSequence()
        .filter { !it.hasUnique("Can only be created by Mercantile City-States") }
    private fun getResources() = allowedResources()
        .map { FormattedLine(it.name, it.name, "Resource/${it.name}", size = 32) }
}

class MapEditorEditImprovementsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

        allowedImprovements().firstOrNull()?.let { addImprovements(it) }
    }

    private fun addImprovements(firstImprovement: TileImprovement) {
        val eraserIcon = "Improvement/${firstImprovement.name}"
        val eraser = FormattedLine("Remove improvement", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove improvement", eraserIcon) { tile ->
                tile.improvement = null
            }
        } }).fillX().row()
        add(MarkupRenderer.render(
            getImprovements().toList(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Improvement/$it") { tile ->
                tile.improvement = it
            }
        }).fillX().row()
    }

    private fun allowedImprovements() = ruleset.tileImprovements.values.asSequence()
        .filter { improvement ->
            //todo This should really be easier, the attributes should allow such a test in one go
            disallowImprovements.none { improvement.name.startsWith(it) }
        }
    private fun getImprovements() = allowedImprovements()
        .map { FormattedLine(it.name, it.name, "Improvement/${it.name}", size = 32) }

    companion object {
        private val disallowImprovements = listOf(
            "Remove ", "Cancel improvement", "City center", Constants.barbarianEncampment
        )
    }
}

class MapEditorEditStartsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        top()
        pad(10f)

        allowedNations().firstOrNull()?.let { addNations(it) }
    }

    private fun addNations(firstNation: Nation) {
        val eraserIcon = "Nation/${firstNation.name}"
        val eraser = FormattedLine("Remove starting locations", icon = eraserIcon, size = 32, iconCrossed = true)
        add(eraser.render(0f).apply { onClick {
            editTab.setBrush("Remove starting locations", eraserIcon) { tile ->
                tile.tileMap.removeStartingLocations(tile.position)
            }
        } }).fillX().row()
        add(MarkupRenderer.render(
            getNations().toList(),
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            editTab.setBrush(it, "Nation/$it") { tile ->
                // toggle the starting location here, note this allows
                // both multiple locations per nation and multiple nations per tile
                if (!tile.tileMap.addStartingLocation(it, tile))
                    tile.tileMap.removeStartingLocation(it, tile)
            }
        }).fillX().row()
    }

    private fun allowedNations() = ruleset.nations.values.asSequence()
        .filter { it.name !in disallowNations }
    private fun getNations() = allowedNations()
        .map { FormattedLine("[${it.name}] starting location", it.name, "Nation/${it.name}", size = 32) }

    companion object {
        private val disallowNations = setOf(Constants.spectator, Constants.barbarians)
    }
}

class MapEditorEditUnitsTab(
    private val editTab: MapEditorEditTab,
    private val ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        add("Work in progress".toLabel(Color.FIREBRICK, 24))
    }
}

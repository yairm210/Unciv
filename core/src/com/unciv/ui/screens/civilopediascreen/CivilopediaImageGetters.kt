package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter

/** Encapsulates the knowledge on how to get an icon for each of the Civilopedia categories */
internal object CivilopediaImageGetters {
    private const val policyIconFolder = "PolicyIcons"
    private const val policyBranchIconFolder = "PolicyBranchIcons"
    private const val policyInnerSize = 0.25f

    // Todo: potential synergy with map editor
    internal fun terrainImage(terrain: Terrain, ruleset: Ruleset,
                              imageSize: Float, tileSetStrings: TileSetStrings? = null): Group {
        val tile = Tile()
        tile.ruleset = ruleset
        
        val baseTerrainFromOccursOn =
            terrain.occursOn.mapNotNull { ruleset.terrains[it] }.lastOrNull { it.type.isBaseTerrain }?.name
            ?: ruleset.terrains.values.firstOrNull { it.type == TerrainType.Land }?.name
            ?: ruleset.terrains.keys.first()
        
        when (terrain.type) {
            TerrainType.NaturalWonder -> {
                tile.naturalWonder = terrain.name
                tile.baseTerrain = if (terrain.turnsInto != null && ruleset.terrains.containsKey(terrain.turnsInto)) terrain.turnsInto!!
                    else baseTerrainFromOccursOn
            }
            TerrainType.TerrainFeature -> {
                tile.baseTerrain = baseTerrainFromOccursOn
                tile.setTerrainTransients()
                tile.addTerrainFeature(terrain.name)
            }
            else ->
                tile.baseTerrain = terrain.name
        }
        tile.setTerrainTransients()
        val group = TileGroup(tile, tileSetStrings ?: TileSetStrings(ruleset, UncivGame.Current.settings),
                imageSize * 36f / 54f)  // TileGroup normally spills out of its bounding box
        group.isForceVisible = true
        group.isForMapEditorIcon = true
        group.update()
        return group
    }

    val construction = { name: String, size: Float ->
        ImageGetter.getConstructionPortrait(name, size)
    }
    val improvement = { name: String, size: Float ->
        ImageGetter.getImprovementPortrait(name, size)
    }
    val nation = { name: String, size: Float ->
        val nation = ImageGetter.ruleset.nations[name]
        if (nation == null) null
        else ImageGetter.getNationPortrait(nation, size)
    }
    val policy = fun(name: String, size: Float): IconCircleGroup? {
        // result is nullable: policy branch complete have no icons but are linked -> nonexistence must be passed down
        fun tryImage(path: String, color: Color): IconCircleGroup? {
            if (ImageGetter.imageExists(path)) return ImageGetter.getImage(path).apply {
                setSize(size * policyInnerSize,size * policyInnerSize)
                this.color = color
            }.surroundWithCircle(size)
            return null
        }
        return tryImage("$policyBranchIconFolder/$name", ImageGetter.CHARCOAL)
            ?: tryImage("$policyIconFolder/$name", Color.BROWN)
    }
    val greatPerson = { name: String, size: Float ->
        val greatPerson = ImageGetter.ruleset.greatPeople[name]
        if (greatPerson == null || greatPerson.units.isEmpty()) null
        else ImageGetter.getConstructionPortrait(greatPerson.units.first(), size)
    }
    val resource = { name: String, size: Float ->
        ImageGetter.getResourcePortrait(name, size)
    }
    val technology = { name: String, size: Float ->
        ImageGetter.getTechIconPortrait(name, size)
    }
    val promotion = { name: String, size: Float ->
        ImageGetter.getPromotionPortrait(name, size)
    }
    val terrain = { name: String, size: Float ->
        val terrain = ImageGetter.ruleset.terrains[name]
        if (terrain == null) null
        else terrainImage(terrain, ImageGetter.ruleset, size)
    }
    val belief = { name: String, size: Float ->
        ImageGetter.getReligionPortrait(name, size)
    }
    val victoryType = { name: String, size: Float ->
        ImageGetter.getVictoryTypeIcon(name, size)
    }
    val unitType = { name: String, size: Float ->
        val path = UnitMovementType.entries.firstOrNull { "Domain: [${it.name}]" == name }
            ?.let {"UnitTypeIcons/Domain${it.name}" }
            ?: "UnitTypeIcons/$name"
        if (ImageGetter.imageExists(path)) ImageGetter.getImage(path).apply { setSize(size) }
        else null
    }
}

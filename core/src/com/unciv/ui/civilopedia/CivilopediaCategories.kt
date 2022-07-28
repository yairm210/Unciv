package com.unciv.ui.civilopedia

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.surroundWithCircle
import java.io.File


/** Encapsulates the knowledge on how to get an icon for each of the Civilopedia categories */
object CivilopediaImageGetters {
    private const val policyIconFolder = "PolicyIcons"
    private const val policyInnerSize = 0.25f

    // Todo: potential synergy with map editor
    fun terrainImage(terrain: Terrain, ruleset: Ruleset, imageSize: Float): Actor {
        val tileInfo = TileInfo()
        tileInfo.ruleset = ruleset
        when (terrain.type) {
            TerrainType.NaturalWonder -> {
                tileInfo.naturalWonder = terrain.name
                tileInfo.baseTerrain = terrain.turnsInto ?: Constants.grassland
            }
            TerrainType.TerrainFeature -> {
                tileInfo.baseTerrain =
                    if (terrain.occursOn.isEmpty() || terrain.occursOn.contains(Constants.grassland))
                        Constants.grassland
                    else
                        terrain.occursOn.lastOrNull()!!
                tileInfo.setTerrainTransients()
                tileInfo.addTerrainFeature(terrain.name)
            }
            else ->
                tileInfo.baseTerrain = terrain.name
        }
        tileInfo.setTerrainTransients()
        val group = TileGroup(tileInfo, TileSetStrings(), imageSize * 36f/54f)  // TileGroup normally spills out of its bounding box
        group.showEntireMap = true
        group.forMapEditorIcon = true
        group.update()
        return Container(group)
    }

    val construction = { name: String, size: Float ->
        ImageGetter.getConstructionImage(name)
            .surroundWithCircle(size)
    }
    val improvement = { name: String, size: Float ->
        ImageGetter.getImprovementIcon(name, size)
    }
    val nation = { name: String, size: Float ->
        val nation = ImageGetter.ruleset.nations[name]
        if (nation == null) null
        else ImageGetter.getNationIndicator(nation, size)
    }
    val policy = { name: String, size: Float ->
        // policy branch start and complete have no icons but are linked -> nonexistence must be passed down
        val fileName = policyIconFolder + File.separator + name
        if (ImageGetter.imageExists(fileName))
            ImageGetter.getImage(fileName)
                .apply {
                    setSize(size * policyInnerSize,size * policyInnerSize)
                    color = Color.BROWN
                }
                .surroundWithCircle(size)
        else null
    }
    val resource = { name: String, size: Float ->
        ImageGetter.getResourceImage(name, size)
    }
    val technology = { name: String, size: Float ->
        ImageGetter.getTechIconGroup(name, size)
    }
    val promotion = { name: String, size: Float ->
        ImageGetter.getPromotionIcon(name, size)
    }
    val terrain = { name: String, size: Float ->
        val terrain = ImageGetter.ruleset.terrains[name]
        if (terrain == null) null
        else terrainImage(terrain, ImageGetter.ruleset, size)
    }
    val belief = { name: String, size: Float ->
        // Kludge until we decide how exactly to show Religions
        fun getInvertedCircledReligionIcon(iconName: String, size: Float) =
            ImageGetter.getCircledReligionIcon(iconName, size).apply {
                circle.color = Color.WHITE
                actor.color = Color.BLACK
            }
        if (ImageGetter.religionIconExists(name)) {
            getInvertedCircledReligionIcon(name, size)
        } else {
            val typeName = ImageGetter.ruleset.beliefs[name]?.type?.name
            if (typeName != null && ImageGetter.religionIconExists(typeName))
                getInvertedCircledReligionIcon(typeName, size)
            else null
        }
    }
}

/** Enum used as keys for Civilopedia "pages" (categories).
 *
 *  Note names are singular on purpose - a "link" allows both key and label
 *  Order of values determines ordering of the categories in the Civilopedia top bar
 *
 * @param label Translatable caption for the Civilopedia button
 */
enum class CivilopediaCategories (
        val label: String,
        val hide: Boolean,      // Omitted on CivilopediaScreen
        val getImage: ((name: String, size: Float) -> Actor?)?,
        val key: KeyCharAndCode = KeyCharAndCode.UNKNOWN,
        val headerIcon: String
    ) {
    Building ("Buildings", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('B'),
        "OtherIcons/Cities"
    ),
    Wonder ("Wonders", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('W'),
        "OtherIcons/Wonders"
    ),
    Resource ("Resources", false,
        CivilopediaImageGetters.resource,
        KeyCharAndCode('R'),
        "OtherIcons/Resources"
    ),
    Terrain ("Terrains", false,
        CivilopediaImageGetters.terrain,
        KeyCharAndCode('T'),
        "OtherIcons/Terrains"
    ),
    Improvement ("Tile Improvements", false,
        CivilopediaImageGetters.improvement,
        KeyCharAndCode('T'),
        "OtherIcons/Improvements"
    ),
    Unit ("Units", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('U'),
        "OtherIcons/Shield"
    ),
    Nation ("Nations", false,
        CivilopediaImageGetters.nation,
        KeyCharAndCode('N'),
        "OtherIcons/Nations"
    ),
    Technology ("Technologies", false,
        CivilopediaImageGetters.technology,
        KeyCharAndCode('T'),
        "TechIcons/Philosophy"
    ),
    Promotion ("Promotions", false,
        CivilopediaImageGetters.promotion,
        KeyCharAndCode('P'),
        "UnitPromotionIcons/Mobility"
    ),
    Policy ("Policies", false,
        CivilopediaImageGetters.policy,
        KeyCharAndCode('P'),
        "PolicyIcons/Constitution"
    ),
    Belief("Religions and Beliefs", false,
        CivilopediaImageGetters.belief,
        KeyCharAndCode('R'),
        "ReligionIcons/Religion"
    ),
    Tutorial ("Tutorials", false,
        getImage = null,
        KeyCharAndCode(Input.Keys.F1),
        "OtherIcons/ExclamationMark"
    ),
    Difficulty ("Difficulty levels", false,
        getImage = null,
        KeyCharAndCode('D'),
        "OtherIcons/Quickstart"
    ),
    Era ("Eras", false,
        getImage = null,
        KeyCharAndCode('D'),
        "OtherIcons/Tyrannosaurus"
    ),
    Speed ("Speeds", false,
        getImage = null,
        KeyCharAndCode('S'),
        "OtherIcons/Timer"
    );

    fun getByOffset(offset: Int) = values()[(ordinal + count + offset) % count]

    fun nextForKey(key: KeyCharAndCode): CivilopediaCategories {
        for (i in 1..count) {
            val next = getByOffset(i)
            if (next.key == key) return next
        }
        return this
    }

    companion object {
        private val count = values().size

        fun fromLink(name: String): CivilopediaCategories? =
            values().firstOrNull { it.name == name }
            ?: values().firstOrNull { it.label == name }

    }
}

package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.TutorialController
import com.unciv.models.ruleset.Belief as BaseBelief
import com.unciv.models.ruleset.unit.UnitType as BaseUnitType

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
    val headerIcon: String,
    val getCategoryIterator: (ruleset: Ruleset, tutorialController: TutorialController) -> Collection<ICivilopediaText>
) {
    Building ("Buildings", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('B'),
        "OtherIcons/Cities",
        { ruleset, _ -> ruleset.buildings.values.filter { !it.isAnyWonder() } }
    ),
    Wonder ("Wonders", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('W'),
        "OtherIcons/Wonders",
        { ruleset, _ -> ruleset.buildings.values.filter { it.isAnyWonder() } }
    ),
    Resource ("Resources", false,
        CivilopediaImageGetters.resource,
        KeyCharAndCode('R'),
        "OtherIcons/Resources",
        { ruleset, _ -> ruleset.tileResources.values }
    ),
    Terrain ("Terrains", false,
        CivilopediaImageGetters.terrain,
        KeyCharAndCode('T'),
        "OtherIcons/Terrains",
        { ruleset, _ -> ruleset.terrains.values }
    ),
    Improvement ("Tile Improvements", false,
        CivilopediaImageGetters.improvement,
        KeyCharAndCode('T'),
        "OtherIcons/Improvements",
        { ruleset, _ -> ruleset.tileImprovements.values }
    ),
    Unit ("Units", false,
        CivilopediaImageGetters.construction,
        KeyCharAndCode('U'),
        "OtherIcons/Shield",
        { ruleset, _ -> ruleset.units.values }
    ),
    UnitType ("Unit types", false,
        CivilopediaImageGetters.unitType,
        KeyCharAndCode('U'),
        "UnitTypeIcons/UnitTypes",
        { ruleset, _ -> BaseUnitType.getCivilopediaIterator(ruleset) }
    ),
    Nation ("Nations", false,
        CivilopediaImageGetters.nation,
        KeyCharAndCode('N'),
        "OtherIcons/Nations",
        { ruleset, _ -> ruleset.nations.values.filter { !it.isSpectator } }
    ),
    Technology ("Technologies", false,
        CivilopediaImageGetters.technology,
        KeyCharAndCode('T'),
        "TechIcons/Philosophy",
        { ruleset, _ -> ruleset.technologies.values }
    ),
    Promotion ("Promotions", false,
        CivilopediaImageGetters.promotion,
        KeyCharAndCode('P'),
        "UnitPromotionIcons/Mobility",
        { ruleset, _ -> ruleset.unitPromotions.values }
    ),
    Policy ("Policies", false,
        CivilopediaImageGetters.policy,
        KeyCharAndCode('P'),
        "PolicyIcons/Constitution",
        { ruleset, _ -> ruleset.policies.values }
    ),
    Belief("Religions and Beliefs", false,
        CivilopediaImageGetters.belief,
        KeyCharAndCode('R'),
        "ReligionIcons/Religion",
        { ruleset, _ -> (ruleset.beliefs.values.asSequence() +
            BaseBelief.getCivilopediaReligionEntry(ruleset)).toList() }
    ),
    Tutorial ("Tutorials", false,
        getImage = null,
        KeyCharAndCode(Input.Keys.F1),
        "OtherIcons/ExclamationMark",
        { _, tutorialController -> tutorialController.getCivilopediaTutorials() }
    ),
    Difficulty ("Difficulty levels", false,
        getImage = null,
        KeyCharAndCode('D'),
        "OtherIcons/Quickstart",
        { ruleset, _ -> ruleset.difficulties.values }
    ),
    Era ("Eras", false,
        getImage = null,
        KeyCharAndCode('D'),
        "OtherIcons/Tyrannosaurus",
        { ruleset, _ -> ruleset.eras.values }
    ),
    Speed ("Speeds", false,
        getImage = null,
        KeyCharAndCode('S'),
        "OtherIcons/Timer",
        { ruleset, _ -> ruleset.speeds.values }
    );

    private fun getByOffset(offset: Int) = values()[(ordinal + count + offset) % count]

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

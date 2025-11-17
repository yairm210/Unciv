package com.unciv.ui.screens.civilopediascreen

import com.unciv.UncivGame
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.screens.basescreen.TutorialController
import com.unciv.models.ruleset.Belief as BaseBelief
import com.unciv.models.ruleset.unit.UnitType as BaseUnitType
import yairm210.purity.annotations.Readonly

/** Enum used as keys for Civilopedia "pages" (categories).
 *
 *  Note names are singular on purpose - a "link" allows both key and label
 *  Order of values determines ordering of the categories in the Civilopedia top bar
 *
 * @param label Translatable caption for the Civilopedia button
 */
enum class CivilopediaCategories (
    val label: String,
    val getImage: ((name: String, size: Float) -> Actor?)?,
    val binding: KeyboardBinding,
    val headerIcon: String,
    val getCategoryIterator: (ruleset: Ruleset, tutorialController: TutorialController) -> Collection<ICivilopediaText>
) {
    Building ("Buildings",
        CivilopediaImageGetters.construction,
        KeyboardBinding.PediaBuildings,
        "OtherIcons/Cities",
        { ruleset, _ -> ruleset.buildings.values.filter { !it.isAnyWonder() } }
    ),
    Wonder ("Wonders",
        CivilopediaImageGetters.construction,
        KeyboardBinding.PediaWonders,
        "OtherIcons/Wonders",
        { ruleset, _ -> ruleset.buildings.values.filter { it.isAnyWonder() } }
    ),
    Resource ("Resources",
        CivilopediaImageGetters.resource,
        KeyboardBinding.PediaResources,
        "OtherIcons/Resources",
        { ruleset, _ -> ruleset.tileResources.values }
    ),
    Terrain ("Terrains",
        CivilopediaImageGetters.terrain,
        KeyboardBinding.PediaTerrains,
        "OtherIcons/Terrains",
        { ruleset, _ -> ruleset.terrains.values }
    ),
    Improvement ("Tile Improvements",
        CivilopediaImageGetters.improvement,
        KeyboardBinding.PediaImprovements,
        "OtherIcons/Improvements",
        { ruleset, _ -> ruleset.tileImprovements.values }
    ),
    Unit ("Units",
        CivilopediaImageGetters.construction,
        KeyboardBinding.PediaUnits,
        "OtherIcons/Shield",
        { ruleset, _ -> ruleset.units.values }
    ),
    UnitType ("Unit types",
        CivilopediaImageGetters.unitType,
        KeyboardBinding.PediaUnitTypes,
        "UnitTypeIcons/UnitTypes",
        { ruleset, _ -> BaseUnitType.getCivilopediaIterator(ruleset) }
    ),
    Nation ("Nations",
        CivilopediaImageGetters.nation,
        KeyboardBinding.PediaNations,
        "OtherIcons/Nations",
        { ruleset, _ -> ruleset.nations.values.filter { !it.isSpectator } }
    ),
    Technology ("Technologies",
        CivilopediaImageGetters.technology,
        KeyboardBinding.PediaTechnologies,
        "TechIcons/Philosophy",
        { ruleset, _ -> ruleset.technologies.values }
    ),
    Promotion ("Promotions",
        CivilopediaImageGetters.promotion,
        KeyboardBinding.PediaPromotions,
        "UnitPromotionIcons/Mobility",
        { ruleset, _ -> ruleset.unitPromotions.values }
    ),
    Policy ("Policies",
        CivilopediaImageGetters.policy,
        KeyboardBinding.PediaPolicies,
        "PolicyIcons/Constitution",
        { ruleset, _ -> ruleset.policies.values }
    ),
    Belief("Religions and Beliefs",
        CivilopediaImageGetters.belief,
        KeyboardBinding.PediaBeliefs,
        "ReligionIcons/Religion",
        { ruleset, _ -> (ruleset.beliefs.values.asSequence() +
            BaseBelief.getCivilopediaReligionEntry(ruleset)).toList() }
    ),
    Tutorial ("Tutorials",
        getImage = null,
        KeyboardBinding.PediaTutorials,
        "OtherIcons/ExclamationMark",
        { _, tutorialController -> tutorialController.getCivilopediaTutorials() }
    ),
    Victory ("Victory Types",
        CivilopediaImageGetters.victoryType,
        KeyboardBinding.PediaVictoryTypes,
        "OtherIcons/Score",
        { ruleset, _ -> ruleset.victories.values }
    ),
    UnitNameGroup("Unit Names",
        CivilopediaImageGetters.unitNameGroup,
        KeyboardBinding.PediaUnitNameGroups,
        "OtherIcons/UnitNameGroups",
        { ruleset, _ -> ruleset.unitNameGroups.values
            .filter {
                it.unitNames.isNotEmpty() && it.getUnits(ruleset).isNotEmpty()
            }
        }
    ),
    Difficulty ("Difficulty levels",
        getImage = null,
        KeyboardBinding.PediaDifficulties,
        "OtherIcons/Quickstart",
        { ruleset, _ -> ruleset.difficulties.values }
    ),
    Era ("Eras",
        getImage = null,
        KeyboardBinding.PediaEras,
        "OtherIcons/Tyrannosaurus",
        { ruleset, _ -> ruleset.eras.values }
    ),
    Speed ("Speeds",
        getImage = null,
        KeyboardBinding.PediaSpeeds,
        "OtherIcons/Timer",
        { ruleset, _ -> ruleset.speeds.values }
    );

    companion object {
        @Readonly fun fromLink(name: String): CivilopediaCategories? =
            entries.firstOrNull { it.name == name }
            ?: entries.firstOrNull { it.label == name }
    }
}

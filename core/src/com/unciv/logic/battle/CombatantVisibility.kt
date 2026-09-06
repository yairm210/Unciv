package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Whether this combatant can be identified when an attack is recorded.
 * The civ's invisible-unit tile cache depends on earlier occupants and may not have been updated
 * after an enemy moved or a detection condition changed. Check current detectors and this
 * combatant's filters instead; querying the recorded history must never repeat this check.
 */
@Readonly
internal fun MapUnitCombatant.isVisibleTo(civ: Civilization): Boolean {
    if (getCivInfo() == civ) return true
    val tile = getTile()
    if (tile !in civ.viewableTiles) return false
    if (!isInvisible(civ)) return true

    return civ.units.getCivUnits().any { detector ->
        tile in detector.viewableTiles
            && detector.getMatchingUniques(UniqueType.CanSeeInvisibleUnits)
                .any { matchesFilter(it.params[0]) }
    }
}

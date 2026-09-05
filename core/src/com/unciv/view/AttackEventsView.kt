package com.unciv.view

import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/**
 * Read-only attack history bound to one GameView's perspective.
 *
 * Deliberately does not extend View: another View must not be able to unwrap the private history.
 * Construction is confined to GameInfo by AttackEventsViewBoundaryTest. Engine serialization and
 * cloning initialize the backing collection before this View is created; expiry mutates it in place.
 */
class AttackEventsView internal constructor(
    private val events: List<AttackEvent>,
    private val viewer: Civilization,
    val spectatorMode: Boolean
) {
    /**
     * A detached snapshot of the endpoints observed when each attack happened.
     * Selection matches only known endpoints or participants identified at attack time.
     * Spectator mode alone never grants knowledge belonging to an unrestricted spectator.
     */
    @Readonly
    fun getObservedAttacks(selectedUnit: MapUnitView? = null): List<ObservedAttack> {
        val selectedPosition = selectedUnit?.getTile()?.position()
        val selectedUnitId = selectedUnit?.unit?.id
        return events.mapNotNull { attack ->
            val source = attack.source.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsSource }
            val target = attack.target.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsTarget }
            if (source == null && target == null) return@mapNotNull null
            if (selectedUnitId != null && selectedPosition != source && selectedPosition != target
                && !isKnownParticipant(attack.attacker, selectedUnitId)
                && attack.targets.none { isKnownParticipant(it, selectedUnitId) }) return@mapNotNull null
            ObservedAttack(attack.turn, source, target)
        }
    }

    @Readonly
    private fun isKnownParticipant(participant: AttackParticipant?, unitId: Int): Boolean =
        participant != null && participant.unitId == unitId
            && (viewer.isSpectator() || viewer.civID in participant.knownBy)
}

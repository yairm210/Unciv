package com.unciv.logic.battle

import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.notifications.AttackNotifications
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.math.min
import kotlin.random.Random

object BattleUnitCapture {

    fun tryCaptureMilitaryUnit(attacker: ICombatant, defender: ICombatant, attackedTile: Tile, attackRecorder: AttackRecorder? = null): Boolean {
        // https://forums.civfanatics.com/threads/prize-ships-for-land-units.650196/
        // https://civilization.fandom.com/wiki/Module:Data/Civ5/GK/Defines\
        // There are 3 ways of capturing a unit, we separate them for cleaner code but we also need to ensure a unit isn't captured twice

        if (defender !is MapUnitCombatant || attacker !is MapUnitCombatant) return false
        if (defender.hasUnique(UniqueType.Uncapturable, GameContext(unit = defender.unit,
                ourCombatant = defender, theirCombatant = attacker, attackedTile = attackedTile)))
            return false

        if (!defender.isDefeated() || defender.unit.isCivilian()) return false

        // Due to the way OR operators short-circuit, calling just A() || B() means B isn't called if A is true.
        // Therefore we run all functions before checking if one is true.
        val wasUnitCaptured = listOf(
            unitCapturedPrizeShipsUnique(attacker, defender),
            unitGainFromDefeatingUnit(attacker, defender)
        ).any { it }

        if (!wasUnitCaptured) return false

        // This is called after takeDamage and so the defeated defender is already destroyed and
        // thus removed from the tile - but MapUnit.destroy() will not clear the unit's currentTile.
        // Therefore placeUnitNearTile _will_ place the new unit exactly where the defender was
        return spawnCapturedUnit(defender, attacker, attackRecorder)
    }

    
    @Readonly
    private fun unitCapturedPrizeShipsUnique(attacker: MapUnitCombatant, defender: MapUnitCombatant): Boolean {
        if (attacker.unit.getMatchingUniques(UniqueType.KillUnitCapture)
                .none { defender.matchesFilter(it.params[0]) }
        ) return false

        val captureChance = min(
            0.8f,
            0.1f + attacker.getAttackingStrength(defender).toFloat() / defender.getDefendingStrength(attacker)
                .toFloat() * 0.4f
        )
        /** Between 0 and 1.  Defaults to turn and location-based random to avoid save scumming */
        val random = Random((attacker.getCivInfo().gameInfo.turns * defender.getTile().position.toVector2().hashCode()).toLong())
        return random.nextFloat() <= captureChance
    }


    private fun unitGainFromDefeatingUnit(attacker: MapUnitCombatant, defender: MapUnitCombatant): Boolean {
        if (!attacker.isMelee()) return false
        var unitCaptured = false
        val state = GameContext(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender)
        for (unique in attacker.getMatchingUniques(UniqueType.GainFromDefeatingUnit, state, true)) {
            if (defender.unit.matchesFilter(unique.params[0])) {
                attacker.getCivInfo().addGold(unique.params[1].toInt())
                unitCaptured = true
            }
        }
        return unitCaptured
    }

    /** Places a [unitName] unit near [tile] after being attacked by [attacker].
     * Adds a notification to [attacker]'s civInfo and returns whether the captured unit could be placed */
    private fun spawnCapturedUnit(defender: MapUnitCombatant, attacker: MapUnitCombatant, attackRecorder: AttackRecorder? = null): Boolean {
        val defenderTile = defender.getTile()
        val defenderCiv = defender.getCivInfo()
        val captureEvent = AttackEvent(attacker, defenderTile).apply {
            targets.add(AttackParticipant(defender))
        }
        val civilianBeforePlacement = defenderTile.civilianUnit
        val civilianOwnerBeforePlacement = civilianBeforePlacement?.civ
        if (civilianBeforePlacement != null)
            attackRecorder?.snapshotTarget(MapUnitCombatant(civilianBeforePlacement), retainIfUnaffected = false)
        val addedUnit = attacker.getCivInfo().units.placeUnitNearTile(defenderTile.position, defender.getName()) ?: return false
        // Placement may capture a stacked civilian through ordinary movement code. Classify
        // that capture here without passing the recorder through placement or movement.
        if (civilianBeforePlacement != null &&
            (civilianBeforePlacement.civ != civilianOwnerBeforePlacement || civilianBeforePlacement.isDestroyed)) {
            val outcome = if (civilianBeforePlacement.civ != civilianOwnerBeforePlacement &&
                civilianBeforePlacement.civ.units.getUnitById(civilianBeforePlacement.id) != null)
                AttackParticipantOutcome.Captured else AttackParticipantOutcome.Destroyed
            attackRecorder?.recordCapture(civilianBeforePlacement, outcome)
        }
        attackRecorder?.recordCapture(defender.unit, AttackParticipantOutcome.Captured)
        addedUnit.currentMovement = 0f
        addedUnit.health = 50
        attacker.getCivInfo().addNotification("An enemy [${defender.getName()}] has joined us!", MapUnitAction(addedUnit), NotificationCategory.War, defender.getName())

        publishCaptureNotification(captureEvent, defenderCiv, AttackParticipantOutcome.Captured)

        val civilianUnit = defenderTile.civilianUnit
        // placeUnitNearTile might not have spawned the unit in exactly this tile, in which case no capture would have happened on this tile. So we need to do that here.
        if (addedUnit.getTile() != defenderTile && civilianUnit != null) {
            captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit), attackRecorder = attackRecorder)
        }
        return true
    }


    /**
     * @throws IllegalArgumentException if the [attacker] and [defender] belong to the same civ.
     */
    fun captureCivilianUnit(attacker: ICombatant, defender: MapUnitCombatant, checkDefeat: Boolean = true, attackRecorder: AttackRecorder? = null): AttackParticipantOutcome {
        require(attacker.getCivInfo() != defender.getCivInfo()) {
            "Can't capture our own unit!"
        }

        // need to save this because if the unit is captured its owner will be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = defender.unit
        val capturedUnitTile = capturedUnit.getTile()
        val captureEvent = AttackEvent(attacker, capturedUnitTile).apply {
            targets.add(AttackParticipant(defender))
        }
        attackRecorder?.markUnitAffected(capturedUnit)
        // Stop current action
        capturedUnit.action = null
        capturedUnit.automated = false

        val originalOwner = capturedUnit.originalOwningCiv

        var wasDestroyedInstead = false
        when {
            // Uncapturable units are destroyed
            defender.unit.hasUnique(UniqueType.Uncapturable) -> {
                capturedUnit.destroy(attackRecorder = attackRecorder)
                wasDestroyedInstead = true
            }
            // City states can never capture settlers at all
            // Same with puppet city sttlers
             attacker.getCivInfo().isCityState && (capturedUnit.hasUnique(UniqueType.FoundCity, GameContext.IgnoreConditionals) ||
                 capturedUnit.hasUnique(UniqueType.FoundPuppetCity, GameContext.IgnoreConditionals)) -> {
                capturedUnit.destroy(attackRecorder = attackRecorder)
                wasDestroyedInstead = true
            }
            // Is it our old unit?
            attacker.getCivInfo() == originalOwner -> {
                // Then it is recaptured without converting settlers to workers
                capturedUnit.capturedBy(attacker.getCivInfo())
            }
            // Return captured civilian to its original owner?
            defender.getCivInfo().isBarbarian
                && originalOwner != null
                && !originalOwner.isBarbarian
                && attacker.getCivInfo() != originalOwner
                && attacker.getCivInfo().knows(originalOwner)
                && originalOwner.isAlive()
                && !attacker.getCivInfo().isAtWarWith(originalOwner)
                && attacker.getCivInfo().playerType == PlayerType.Human // Only humans get the choice
            -> {
                capturedUnit.capturedBy(attacker.getCivInfo())
                attacker.getCivInfo().popupAlerts.add(
                    PopupAlert(
                        AlertType.RecapturedCivilian,
                        capturedUnit.currentTile.position.toPrettyString()
                    )
                )
            }

            else ->
                if (captureOrConvertToWorker(capturedUnit, attacker.getCivInfo()) == null)
                    wasDestroyedInstead = true
        }

        val outcome = if (wasDestroyedInstead) AttackParticipantOutcome.Destroyed else AttackParticipantOutcome.Captured
        publishCaptureNotification(captureEvent, defenderCiv, outcome)
        if (wasDestroyedInstead)
            Battle.triggerDefeatUniques(defender, attacker, capturedUnitTile, attackRecorder)

        if (checkDefeat)
            Battle.destroyIfDefeated(defenderCiv, attacker.getCivInfo())
        capturedUnit.updateVisibleTiles()
        attackRecorder?.recordCapture(capturedUnit, outcome)
        return outcome
    }

    /** Standalone movement captures use the same privacy projection without adding attack history. */
    private fun publishCaptureNotification(event: AttackEvent, recipient: Civilization, outcome: AttackParticipantOutcome) {
        val target = event.targets.single()
        target.captureAttempted = true
        target.outcome = outcome
        event.resolution = AttackResolution.Completed
        val view = recipient.gameInfo.createAttackEventView(event, recipient)
        for (notification in AttackNotifications.createCapture(view))
            recipient.addNotification(notification.text, notification.actions, notification.category,
                *notification.icons.toTypedArray())
    }

    /**
     *  Capture wrapper that also implements the rule that non-barbarians get a Worker as replacement for a captured Settler.
     *  @return position the captured unit is in afterwards - can rarely be a different tile if the unit is no longer allowed where it originated.
     *          Returns `null` if there is no Worker replacement for a Settler in the ruleset or placeUnitNearTile couldn't place it.
     *  @see MapUnit.capturedBy
     */
    fun captureOrConvertToWorker(capturedUnit: MapUnit, capturingCiv: Civilization): HexCoord? {
        // Captured settlers are converted to workers unless captured by barbarians (so they can be returned later).
        if (!capturedUnit.hasUnique(UniqueType.FoundCity, GameContext.IgnoreConditionals) || capturingCiv.isBarbarian) {
            capturedUnit.capturedBy(capturingCiv)
            return capturedUnit.currentTile.position // if capturedBy has moved the unit, this is updated
        }

        capturedUnit.destroy()
        // This is so that future checks which check if a unit has been captured are caught give the right answer
        //  For example, in postBattleMoveToAttackedTile
        capturedUnit.civ = capturingCiv
        capturedUnit.cache.state = GameContext(capturedUnit)

        val workerTypeUnit = capturingCiv.gameInfo.ruleset.units.values
            .firstOrNull { it.isCivilian() && it.getMatchingUniques(UniqueType.BuildImprovements, GameContext.IgnoreConditionals)
            .any { unique -> unique.params[0] == "Land" } }
            ?: return null
        return capturingCiv.units.placeUnitNearTile(capturedUnit.currentTile.position, workerTypeUnit, capturedUnit.id)
            ?.currentTile?.position
    }

}

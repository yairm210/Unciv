package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.math.min
import kotlin.random.Random

object BattleUnitCapture {

    fun tryCaptureMilitaryUnit(attacker: ICombatant, defender: ICombatant, attackedTile: Tile): Boolean {
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
            unitCapturedFromEncampment(attacker, defender, attackedTile),
            unitGainFromDefeatingUnit(attacker, defender)
        ).any { it }

        if (!wasUnitCaptured) return false

        // This is called after takeDamage and so the defeated defender is already destroyed and
        // thus removed from the tile - but MapUnit.destroy() will not clear the unit's currentTile.
        // Therefore placeUnitNearTile _will_ place the new unit exactly where the defender was
        return spawnCapturedUnit(defender, attacker)
    }

    
    @Readonly
    private fun unitCapturedPrizeShipsUnique(attacker: MapUnitCombatant, defender: MapUnitCombatant): Boolean {
        if (attacker.unit.getMatchingUniques(UniqueType.KillUnitCapture)
                .none { defender.matchesFilter(it.params[0]) }
        ) return false

        val captureChance = min(
            0.8f,
            0.1f + attacker.getAttackingStrength().toFloat() / defender.getDefendingStrength()
                .toFloat() * 0.4f
        )
        /** Between 0 and 1.  Defaults to turn and location-based random to avoid save scumming */
        val random = Random((attacker.getCivInfo().gameInfo.turns * defender.getTile().position.hashCode()).toLong())
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

    private fun unitCapturedFromEncampment(attacker: MapUnitCombatant, defender: MapUnitCombatant, attackedTile: Tile): Boolean {
        if (!defender.getCivInfo().isBarbarian) return false
        if (attackedTile.improvement != Constants.barbarianEncampment) return false

        var unitCaptured = false
        // German unique - needs to be checked before we try to move to the enemy tile, since the encampment disappears after we move in

        for (unique in attacker.getCivInfo()
            .getMatchingUniques(UniqueType.GainFromEncampment)) {
            attacker.getCivInfo().addGold(unique.params[0].toInt())
            unitCaptured = true
        }
        return unitCaptured
    }

    /** Places a [unitName] unit near [tile] after being attacked by [attacker].
     * Adds a notification to [attacker]'s civInfo and returns whether the captured unit could be placed */
    private fun spawnCapturedUnit(defender: MapUnitCombatant, attacker: MapUnitCombatant): Boolean {
        val defenderTile = defender.getTile()
        val addedUnit = attacker.getCivInfo().units.placeUnitNearTile(defenderTile.position, defender.getName()) ?: return false
        addedUnit.currentMovement = 0f
        addedUnit.health = 50
        attacker.getCivInfo().addNotification("An enemy [${defender.getName()}] has joined us!", MapUnitAction(addedUnit), NotificationCategory.War, defender.getName())

        defender.getCivInfo().addNotification(
            "An enemy [${attacker.getName()}] has captured our [${defender.getName()}]",
            defender.getTile().position, NotificationCategory.War, attacker.getName(),
            NotificationIcon.War, defender.getName()
        )

        val civilianUnit = defenderTile.civilianUnit
        // placeUnitNearTile might not have spawned the unit in exactly this tile, in which case no capture would have happened on this tile. So we need to do that here.
        if (addedUnit.getTile() != defenderTile && civilianUnit != null) {
            captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit))
        }
        return true
    }


    /**
     * @throws IllegalArgumentException if the [attacker] and [defender] belong to the same civ.
     */
    fun captureCivilianUnit(attacker: ICombatant, defender: MapUnitCombatant, checkDefeat: Boolean = true) {
        require(attacker.getCivInfo() != defender.getCivInfo()) {
            "Can't capture our own unit!"
        }

        // need to save this because if the unit is captured its owner will be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = defender.unit
        // Stop current action
        capturedUnit.action = null
        capturedUnit.automated = false

        val capturedUnitTile = capturedUnit.getTile()
        val originalOwner = if (capturedUnit.originalOwner != null)
            capturedUnit.civ.gameInfo.getCivilization(capturedUnit.originalOwner!!)
        else null

        var wasDestroyedInstead = false
        when {
            // Uncapturable units are destroyed
            defender.unit.hasUnique(UniqueType.Uncapturable) -> {
                capturedUnit.destroy()
                wasDestroyedInstead = true
            }
            // City states can never capture settlers at all
            capturedUnit.hasUnique(UniqueType.FoundCity, GameContext.IgnoreConditionals) && attacker.getCivInfo().isCityState -> {
                capturedUnit.destroy()
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
                        capturedUnit.currentTile.position.toString()
                    )
                )
            }

            else ->
                if (captureOrConvertToWorker(capturedUnit, attacker.getCivInfo()) == null)
                    wasDestroyedInstead = true
        }

        if (!wasDestroyedInstead)
            defenderCiv.addNotification(
                "An enemy [${attacker.getName()}] has captured our [${defender.getName()}]",
                defender.getTile().position, NotificationCategory.War, attacker.getName(),
                NotificationIcon.War, defender.getName()
            )
        else {
            defenderCiv.addNotification(
                "An enemy [${attacker.getName()}] has destroyed our [${defender.getName()}]",
                defender.getTile().position, NotificationCategory.War, attacker.getName(),
                NotificationIcon.War, defender.getName()
            )
            Battle.triggerDefeatUniques(defender, attacker, capturedUnitTile)
        }

        if (checkDefeat)
            Battle.destroyIfDefeated(defenderCiv, attacker.getCivInfo())
        capturedUnit.updateVisibleTiles()
    }

    /**
     *  Capture wrapper that also implements the rule that non-barbarians get a Worker as replacement for a captured Settler.
     *  @return position the captured unit is in afterwards - can rarely be a different tile if the unit is no longer allowed where it originated.
     *          Returns `null` if there is no Worker replacement for a Settler in the ruleset or placeUnitNearTile couldn't place it.
     *  @see MapUnit.capturedBy
     */
    fun captureOrConvertToWorker(capturedUnit: MapUnit, capturingCiv: Civilization): Vector2? {
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

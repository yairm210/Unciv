package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random

object AirInterception {

    // Should draw an Interception if available on the tile from any Civ
    // Land Units deal 0 damage, and no XP for either party
    // Air Interceptors do Air Combat as if Melee (mutual damage) but using Ranged Strength. 5XP to both
    // But does not use the Interception mechanic bonuses/promotions
    // Counts as an Attack for both units
    // Will always draw out an Interceptor's attack (they cannot miss)
    // This means the combat against Air Units will execute and always deal damage
    // Random Civ at War will Intercept, prioritizing Air Units,
    // sorted by highest Intercept chance (same as regular Intercept)
    fun airSweep(attacker: MapUnitCombatant, attackedTile: Tile) {
        // Air Sweep counts as an attack, even if nothing else happens
        attacker.unit.attacksThisTurn++
        // copied and modified from reduceAttackerMovementPointsAndAttacks()
        // use up movement
        if (attacker.unit.hasUnique(UniqueType.CanMoveAfterAttacking) || attacker.unit.maxAttacksPerTurn() > attacker.unit.attacksThisTurn) {
            // if it was a melee attack and we won, then the unit ALREADY got movement points deducted,
            // for the movement to the enemy's tile!
            // and if it's an air unit, it only has 1 movement anyway, so...
            if (!attacker.unit.baseUnit.movesLikeAirUnits)
                attacker.unit.useMovementPoints(1f)
        } else attacker.unit.currentMovement = 0f
        val attackerName = attacker.getName()

        // Make giant sequence of all potential Interceptors from all Civs isAtWarWith()
        var potentialInterceptors = sequence<MapUnit> {  }
        for (interceptingCiv in attacker.getCivInfo().gameInfo.civilizations
            .filter {attacker.getCivInfo().isAtWarWith(it)}) {
            potentialInterceptors += interceptingCiv.units.getCivUnits()
                .filter { it.canIntercept(attackedTile) }
        }

        // first priority, only Air Units
        if (potentialInterceptors.any { it.baseUnit.isAirUnit() })
            potentialInterceptors = potentialInterceptors.filter { it.baseUnit.isAirUnit() }

        // Pick highest chance interceptor
        for (interceptor in potentialInterceptors
            .shuffled()  // randomize Civ
            .sortedByDescending { it.interceptChance() }) {
            // No chance of Interceptor to miss (unlike regular Interception). Always want to deal damage
            // pairs of LocationAction for Notification
            val locations = LocationAction(
                interceptor.currentTile.position,
                attacker.unit.currentTile.position
            )
            interceptor.attacksThisTurn++  // even if you miss, you took the shot
            if (!interceptor.baseUnit.isAirUnit()) {
                val interceptorName = interceptor.name
                // Deal no damage (moddable in future?) and no XP
                val attackerText =
                    "Our [$attackerName] ([-0] HP) was attacked by an intercepting [$interceptorName] ([-0] HP)"
                val interceptorText =
                    "Our [$interceptorName] ([-0] HP) intercepted and attacked an enemy [$attackerName] ([-0] HP)"
                attacker.getCivInfo().addNotification(
                    attackerText, locations, NotificationCategory.War,
                    attackerName, NotificationIcon.War, interceptorName
                )
                interceptor.civ.addNotification(
                    interceptorText, locations, NotificationCategory.War,
                    interceptorName, NotificationIcon.War, attackerName
                )
                attacker.unit.action = null
                return
            }

            // Damage if Air v Air should work similar to Melee
            val damageDealt: Battle.DamageDealt = Battle.takeDamage(attacker, MapUnitCombatant(interceptor))

            // 5 XP to both
            Battle.addXp(MapUnitCombatant(interceptor), 5, attacker)
            Battle.addXp(attacker, 5, MapUnitCombatant(interceptor))

            val locationsInterceptorUnknown =
                LocationAction(attackedTile.position, attacker.unit.currentTile.position)

            addAirSweepInterceptionNotifications(
                attacker,
                interceptor,
                damageDealt,
                locationsInterceptorUnknown,
                locations
            )
            attacker.unit.action = null
            return
        }

        // No Interceptions available
        val attackerText = "Nothing tried to intercept our [$attackerName]"
        attacker.getCivInfo().addNotification(attackerText, NotificationCategory.War, attackerName)
        attacker.unit.action = null
    }

    // TODO: Check overlap with addInterceptionNotifications, and unify what we can
    private fun addAirSweepInterceptionNotifications(
        attacker: MapUnitCombatant,
        interceptor: MapUnit,
        damageDealt: Battle.DamageDealt,
        locationsInterceptorUnknown: Sequence<LocationAction>,
        locations: Sequence<LocationAction>
    ) {
        val attackerName = attacker.getName()
        val interceptorName = interceptor.name

        val attackerText =
            if (attacker.isDefeated()) {
                if (interceptor.getTile() in attacker.getCivInfo().viewableTiles)
                    "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was destroyed by an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"
                else "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was destroyed by an unknown interceptor"
            } else if (MapUnitCombatant(interceptor).isDefeated()) {
                "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) destroyed an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"
            } else "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was attacked by an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"

        attacker.getCivInfo().addNotification(
            attackerText, locationsInterceptorUnknown, NotificationCategory.War,
            attackerName, NotificationIcon.War, NotificationIcon.Question
        )

        val interceptorText =
            if (attacker.isDefeated())
                "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and destroyed an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
            else if (MapUnitCombatant(interceptor).isDefeated()) {
                if (attacker.getTile() in interceptor.civ.viewableTiles) "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
                else "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an unknown enemy"
            } else "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and attacked an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"

        interceptor.civ.addNotification(
            interceptorText, locations, NotificationCategory.War,
            interceptorName, NotificationIcon.War, attackerName
        )
    }

    internal fun tryInterceptAirAttack(
        attacker: MapUnitCombatant,
        attackedTile: Tile,
        interceptingCiv: Civilization,
        defender: ICombatant?
    ): Battle.DamageDealt {
        if (attacker.unit.hasUnique(UniqueType.CannotBeIntercepted, GameContext(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, attackedTile = attackedTile)))
            return Battle.DamageDealt.None

        // Pick highest chance interceptor
        val interceptor = interceptingCiv.units.getCivUnits()
            .filter { it.canIntercept(attackedTile) }
            .sortedByDescending { it.interceptChance() }
            .firstOrNull { unit ->
                // Can't intercept if we have a unique preventing it
                val conditionalState = GameContext(interceptingCiv, ourCombatant = MapUnitCombatant(unit), theirCombatant = attacker, combatAction = CombatAction.Intercept, attackedTile = attackedTile)
                unit.getMatchingUniques(UniqueType.CannotInterceptUnits, conditionalState)
                    .none { attacker.matchesFilter(it.params[0]) }
                    // Defender can't intercept either
                    && unit != (defender as? MapUnitCombatant)?.unit
            }
            ?: return Battle.DamageDealt.None

        interceptor.attacksThisTurn++  // even if you miss, you took the shot
        // Does Intercept happen? If not, exit
        if (Random.Default.nextFloat() > interceptor.interceptChance() / 100f)
            return Battle.DamageDealt.None

        var damage = BattleDamage.calculateDamageToDefender(
            MapUnitCombatant(interceptor),
            attacker
        )

        var damageFactor = 1f + interceptor.interceptDamagePercentBonus().toFloat() / 100f
        damageFactor *= attacker.unit.receivedInterceptDamageFactor()

        damage = (damage.toFloat() * damageFactor).toInt().coerceAtMost(attacker.unit.health)

        attacker.takeDamage(damage)
        if (damage > 0)
            Battle.addXp(MapUnitCombatant(interceptor), 2, attacker)

        addInterceptionNotifications(attacker, interceptor, damage)

        return Battle.DamageDealt(0, damage)
    }

    private fun addInterceptionNotifications(
        attacker: MapUnitCombatant,
        interceptor: MapUnit,
        damage: Int
    ) {
        val attackerName = attacker.getName()
        val interceptorName = interceptor.name

        val locations = LocationAction(interceptor.currentTile.position, attacker.unit.currentTile.position)
        val attackerText = if (!attacker.isDefeated())
            "Our [$attackerName] ([-$damage] HP) was attacked by an intercepting [$interceptorName] ([-0] HP)"
        else if (interceptor.getTile() in attacker.getCivInfo().viewableTiles)
            "Our [$attackerName] ([-$damage] HP) was destroyed by an intercepting [$interceptorName] ([-0] HP)"
        else "Our [$attackerName] ([-$damage] HP) was destroyed by an unknown interceptor"

        attacker.getCivInfo().addNotification(
            attackerText, interceptor.currentTile.position, NotificationCategory.War,
            attackerName, NotificationIcon.War, interceptorName
        )

        val interceptorText = if (attacker.isDefeated())
            "Our [$interceptorName] ([-0] HP) intercepted and destroyed an enemy [$attackerName] ([-$damage] HP)"
        else "Our [$interceptorName] ([-0] HP) intercepted and attacked an enemy [$attackerName] ([-$damage] HP)"
        interceptor.civ.addNotification(
            interceptorText, locations, NotificationCategory.War,
            interceptorName, NotificationIcon.War, attackerName
        )
    }

}

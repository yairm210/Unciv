package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.SpecificUnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.PromoteUnitAction
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.worldscreen.bottombar.BattleTable
import com.unciv.utils.debug
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ulp
import kotlin.random.Random

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
object Battle {

    /**
     * Moves [attacker] to [attackableTile], handles siege setup then attacks if still possible
     * (by calling [attack] or [NUKE]). Does _not_ play the attack sound!
     *
     * Currently not used by UI, only by automation via [BattleHelper.tryAttackNearbyEnemy][com.unciv.logic.automation.unit.BattleHelper.tryAttackNearbyEnemy]
     */
    fun moveAndAttack(attacker: ICombatant, attackableTile: AttackableTile) {
        if (!movePreparingAttack(attacker, attackableTile)) return
        attackOrNuke(attacker, attackableTile)
    }

    /**
     * Moves [attacker] to [attackableTile], handles siege setup and returns `true` if an attack is still possible.
     *
     * This is a logic function, not UI, so e.g. sound needs to be handled after calling this.
     */
    fun movePreparingAttack(attacker: ICombatant, attackableTile: AttackableTile): Boolean {
        if (attacker !is MapUnitCombatant) return true
        attacker.unit.movement.moveToTile(attackableTile.tileToAttackFrom)
        /**
         * When calculating movement distance, we assume that a hidden tile is 1 movement point,
         * which can lead to EXCEEDINGLY RARE edge cases where you think
         * that you can attack a tile by passing through a HIDDEN TILE,
         * but the hidden tile is actually IMPASSIBLE so you stop halfway!
         */
        if (attacker.getTile() != attackableTile.tileToAttackFrom) return false
        /** Rarely, a melee unit will target a civilian then move through the civilian to get
         * to attackableTile.tileToAttackFrom, meaning that they take the civilian.
         * This can lead to:
         * A. the melee unit from trying to capture their own unit (see #7282)
         * B. The civilian unit disappearing entirely (e.g. Great Person) and trying to capture a non-existent unit (see #8563) */
        val combatant = getMapCombatantOfTile(attackableTile.tileToAttack)
        if (combatant == null || combatant.getCivInfo() == attacker.getCivInfo()) return false
        /** Alternatively, maybe we DID reach that tile, but it turned out to be a hill or something,
         * so we expended all of our movement points! */
        if (attacker.hasUnique(UniqueType.MustSetUp)
                && !attacker.unit.isSetUpForSiege()
                && attacker.unit.currentMovement > 0f
        ) {
            attacker.unit.action = UnitActionType.SetUp.value
            attacker.unit.useMovementPoints(1f)
        }
        return (attacker.unit.currentMovement > 0f)
    }

    /**
     * This is meant to be called only after all prerequisite checks have been done.
     */
    fun attackOrNuke(attacker: ICombatant, attackableTile: AttackableTile): DamageDealt {
        return if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isNuclearWeapon()) {
            NUKE(attacker, attackableTile.tileToAttack)
            DamageDealt.None
        } else {
            attack(attacker, getMapCombatantOfTile(attackableTile.tileToAttack)!!)
        }
    }

    fun attack(attacker: ICombatant, defender: ICombatant): DamageDealt {
        debug("%s %s attacked %s %s", attacker.getCivInfo().civName, attacker.getName(), defender.getCivInfo().civName, defender.getName())
        val attackedTile = defender.getTile()
        if (attacker is MapUnitCombatant) {
            attacker.unit.attacksSinceTurnStart.add(Vector2(attackedTile.position))
        } else {
            attacker.getCivInfo().attacksSinceTurnStart.add(Civilization.HistoricalAttackMemory(
                null,
                Vector2(attacker.getTile().position),
                Vector2(attackedTile.position)
            ))
        }

        val interceptDamage: DamageDealt
        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isAirUnit()) {
            interceptDamage = tryInterceptAirAttack(attacker, attackedTile, defender.getCivInfo(), defender)
            if (attacker.isDefeated()) return interceptDamage
        } else interceptDamage = DamageDealt.None

        // Withdraw from melee ability
        if (attacker is MapUnitCombatant && attacker.isMelee() && defender is MapUnitCombatant) {
            val withdrawUniques = defender.unit.getMatchingUniques(UniqueType.MayWithdraw)
            val combinedProbabilityToStayPut = withdrawUniques.fold(100) { probabilityToStayPut, unique -> probabilityToStayPut * (100-unique.params[0].toInt()) / 100 }
            val baseWithdrawChance = 100 - combinedProbabilityToStayPut
            // If a mod allows multiple withdraw properties, they stack multiplicatively
            if (baseWithdrawChance != 0 && doWithdrawFromMeleeAbility(attacker, defender, baseWithdrawChance))
                return DamageDealt.None
        }

        val isAlreadyDefeatedCity = defender is CityCombatant && defender.isDefeated()

        val damageDealt = takeDamage(attacker, defender)

        // check if unit is captured by the attacker (prize ships unique)
        // As ravignir clarified in issue #4374, this only works for aggressor
        val captureMilitaryUnitSuccess = tryCaptureUnit(attacker, defender, attackedTile)

        if (!captureMilitaryUnitSuccess) // capture creates a new unit, but `defender` still is the original, so this function would still show a kill message
            postBattleNotifications(attacker, defender, attackedTile, attacker.getTile(), damageDealt)

        if (defender.getCivInfo().isBarbarian() && attackedTile.improvement == Constants.barbarianEncampment)
            defender.getCivInfo().gameInfo.barbarians.campAttacked(attackedTile.position)

        // This needs to come BEFORE the move-to-tile, because if we haven't conquered it we can't move there =)
        if (defender.isDefeated() && defender is CityCombatant && attacker is MapUnitCombatant
                && attacker.isMelee() && !attacker.unit.hasUnique(UniqueType.CannotCaptureCities)) {
            // Barbarians can't capture cities
            if (attacker.unit.civ.isBarbarian()) {
                defender.takeDamage(-1) // Back to 2 HP
                val ransom = min(200, defender.city.civ.gold)
                defender.city.civ.addGold(-ransom)
                defender.city.civ.addNotification("Barbarians raided [${defender.city.name}] and stole [$ransom] Gold from your treasury!", defender.city.location, NotificationCategory.War, NotificationIcon.War)
                attacker.unit.destroy() // Remove the barbarian
            } else
                conquerCity(defender.city, attacker)
        }

        // Exploring units surviving an attack should "wake up"
        if (!defender.isDefeated() && defender is MapUnitCombatant && defender.unit.isExploring())
            defender.unit.action = null

        fun triggerVictoryUniques(ourUnit:MapUnitCombatant, enemy:MapUnitCombatant){
            val stateForConditionals = StateForConditionals(civInfo = ourUnit.getCivInfo(),
                ourCombatant = ourUnit, theirCombatant=enemy, tile = attackedTile)
            for (unique in ourUnit.unit.getTriggeredUniques(UniqueType.TriggerUponDefeatingUnit, stateForConditionals))
                if (unique.conditionals.any { it.type == UniqueType.TriggerUponDefeatingUnit
                                && enemy.unit.matchesFilter(it.params[0]) })
                    UniqueTriggerActivation.triggerUnitwideUnique(unique, ourUnit.unit, triggerNotificationText = "due to our [${ourUnit.getName()}] defeating a [${enemy.getName()}]")
        }

        // Add culture when defeating a barbarian when Honor policy is adopted, gold from enemy killed when honor is complete
        // or any enemy military unit with Sacrificial captives unique (can be either attacker or defender!)
        if (defender.isDefeated() && defender is MapUnitCombatant && !defender.unit.isCivilian()) {
            tryEarnFromKilling(attacker, defender)
            tryHealAfterKilling(attacker)

            if (attacker is MapUnitCombatant) triggerVictoryUniques(attacker, defender)
            triggerDefeatUniques(defender, attacker, attackedTile)

        } else if (attacker.isDefeated() && attacker is MapUnitCombatant && !attacker.unit.isCivilian()) {
            tryEarnFromKilling(defender, attacker)
            tryHealAfterKilling(defender)

            if (defender is MapUnitCombatant) triggerVictoryUniques(defender, attacker)
            triggerDefeatUniques(attacker, defender, attackedTile)
        }

        if (attacker is MapUnitCombatant) {
            if (attacker.unit.hasUnique(UniqueType.SelfDestructs))
                attacker.unit.destroy()
            else if (attacker.unit.isMoving())
                attacker.unit.action = null
            doDestroyImprovementsAbility(attacker, attackedTile, defender)
        }

        // Should be called after tryCaptureUnit(), as that might spawn a unit on the tile we go to
        if (!captureMilitaryUnitSuccess)
            postBattleMoveToAttackedTile(attacker, defender, attackedTile)

        reduceAttackerMovementPointsAndAttacks(attacker, defender)

        if (!isAlreadyDefeatedCity) postBattleAddXp(attacker, defender)

        if (attacker is CityCombatant){
            val cityCanBombardNotification = attacker.getCivInfo().notifications
                .firstOrNull { it.text == "Your city [${attacker.getName()}] can bombard the enemy!" }
            attacker.getCivInfo().notifications.remove(cityCanBombardNotification)
        }

        return damageDealt + interceptDamage
    }

    private fun triggerDefeatUniques(ourUnit: MapUnitCombatant, enemy: ICombatant, attackedTile: Tile){
        val stateForConditionals = StateForConditionals(civInfo = ourUnit.getCivInfo(),
            ourCombatant = ourUnit, theirCombatant=enemy, tile = attackedTile)
        for (unique in ourUnit.unit.getTriggeredUniques(UniqueType.TriggerUponDefeat, stateForConditionals))
            UniqueTriggerActivation.triggerUnitwideUnique(unique, ourUnit.unit, triggerNotificationText = "due to our [${ourUnit.getName()}] being defeated by a [${enemy.getName()}]")
    }

    private fun tryEarnFromKilling(civUnit: ICombatant, defeatedUnit: MapUnitCombatant) {
        val unitStr = max(defeatedUnit.unit.baseUnit.strength, defeatedUnit.unit.baseUnit.rangedStrength)
        val unitCost = defeatedUnit.unit.baseUnit.cost

        val bonusUniques = ArrayList<Unique>()

        val stateForConditionals = StateForConditionals(civInfo = civUnit.getCivInfo(), ourCombatant = civUnit, theirCombatant = defeatedUnit)
        if (civUnit is MapUnitCombatant) {
            bonusUniques.addAll(civUnit.getMatchingUniques(UniqueType.KillUnitPlunder, stateForConditionals, true))
        } else {
            bonusUniques.addAll(civUnit.getCivInfo().getMatchingUniques(UniqueType.KillUnitPlunder, stateForConditionals))
        }

        val cityWithReligion =
            civUnit.getTile().getTilesInDistance(4).firstOrNull {
                it.isCityCenter() && it.getCity()!!.getMatchingUniques(UniqueType.KillUnitPlunderNearCity, stateForConditionals).any()
            }?.getCity()
        if (cityWithReligion != null) {
            bonusUniques.addAll(cityWithReligion.getMatchingUniques(UniqueType.KillUnitPlunderNearCity, stateForConditionals))
        }

        for (unique in bonusUniques) {
            if (!defeatedUnit.matchesCategory(unique.params[1])) continue

            val yieldPercent = unique.params[0].toFloat() / 100
            val defeatedUnitYieldSourceType = unique.params[2]
            val yieldTypeSourceAmount =
                if (defeatedUnitYieldSourceType == "Cost") unitCost else unitStr
            val yieldAmount = (yieldTypeSourceAmount * yieldPercent).toInt()

            val stat = Stat.valueOf(unique.params[3])
            civUnit.getCivInfo().addStat(stat, yieldAmount)
        }

        // CS friendship from killing barbarians
        if (defeatedUnit.matchesCategory("Barbarian") && defeatedUnit.matchesCategory("Military") && civUnit.getCivInfo().isMajorCiv()) {
            for (cityState in UncivGame.Current.gameInfo!!.getAliveCityStates()) {
                if (civUnit.getCivInfo().knows(cityState) && defeatedUnit.unit.threatensCiv(cityState)) {
                    cityState.cityStateFunctions.threateningBarbarianKilledBy(civUnit.getCivInfo())
                }
            }
        }

        // CS war with major pseudo-quest
        for (cityState in UncivGame.Current.gameInfo!!.getAliveCityStates()) {
            cityState.questManager.militaryUnitKilledBy(civUnit.getCivInfo(), defeatedUnit.getCivInfo())
        }
    }

    private fun tryCaptureUnit(attacker: ICombatant, defender: ICombatant, attackedTile: Tile): Boolean {
        // https://forums.civfanatics.com/threads/prize-ships-for-land-units.650196/
        // https://civilization.fandom.com/wiki/Module:Data/Civ5/GK/Defines\
        // There are 3 ways of capturing a unit, we separate them for cleaner code but we also need to ensure a unit isn't captured twice

        if (defender !is MapUnitCombatant || attacker !is MapUnitCombatant) return false

        if (!defender.isDefeated() || defender.unit.isCivilian()) return false

        fun unitCapturedPrizeShipsUnique(): Boolean {
            if (attacker.unit.getMatchingUniques(UniqueType.KillUnitCapture)
                        .none { defender.matchesCategory(it.params[0]) }
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

        fun unitGainFromEncampment(): Boolean {
            if (!defender.getCivInfo().isBarbarian()) return false
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


        fun unitGainFromDefeatingUnit(): Boolean {
            if (!attacker.isMelee()) return false
            var unitCaptured = false
            val state = StateForConditionals(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender)
            for (unique in attacker.getMatchingUniques(UniqueType.GainFromDefeatingUnit, state, true)) {
                if (defender.unit.matchesFilter(unique.params[0])) {
                    attacker.getCivInfo().addGold(unique.params[1].toInt())
                    unitCaptured = true
                }
            }
            return unitCaptured
        }

        // Due to the way OR operators short-circuit, calling just A() || B() means B isn't called if A is true.
        // Therefore we run all functions before checking if one is true.
        val wasUnitCaptured = listOf(
            unitCapturedPrizeShipsUnique(),
            unitGainFromEncampment(),
            unitGainFromDefeatingUnit()
        ).any { it }

        if (!wasUnitCaptured) return false

        // This is called after takeDamage and so the defeated defender is already destroyed and
        // thus removed from the tile - but MapUnit.destroy() will not clear the unit's currentTile.
        // Therefore placeUnitNearTile _will_ place the new unit exactly where the defender was
        return spawnCapturedUnit(defender.getName(), attacker, defender.getTile())
    }

    /** Places a [unitName] unit near [tile] after being attacked by [attacker].
     * Adds a notification to [attacker]'s civInfo and returns whether the captured unit could be placed */
    private fun spawnCapturedUnit(unitName: String, attacker: ICombatant, tile: Tile): Boolean {
        val addedUnit = attacker.getCivInfo().units.placeUnitNearTile(tile.position, unitName) ?: return false
        addedUnit.currentMovement = 0f
        addedUnit.health = 50
        attacker.getCivInfo().addNotification("An enemy [${unitName}] has joined us!", addedUnit.getTile().position, NotificationCategory.War, unitName)

        val civilianUnit = tile.civilianUnit
        // placeUnitNearTile might not have spawned the unit in exactly this tile, in which case no capture would have happened on this tile. So we need to do that here.
        if (addedUnit.getTile() != tile && civilianUnit != null) {
            captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit))
        }
        return true
    }

    /** Holder for battle result - actual damage.
     *  @param attackerDealt Damage done by attacker to defender
     *  @param defenderDealt Damage done by defender to attacker
     */
    data class DamageDealt(val attackerDealt: Int, val defenderDealt: Int) {
        operator fun plus(other: DamageDealt) =
            DamageDealt(attackerDealt + other.attackerDealt, defenderDealt + other.defenderDealt)
        companion object {
            val None = DamageDealt(0, 0)
        }
    }

    private fun takeDamage(attacker: ICombatant, defender: ICombatant): DamageDealt {
        var potentialDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender)
        var potentialDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender)

        val attackerHealthBefore = attacker.getHealth()
        val defenderHealthBefore = defender.getHealth()

        if (defender is MapUnitCombatant && defender.unit.isCivilian() && attacker.isMelee()) {
            captureCivilianUnit(attacker, defender)
        } else if (attacker.isRanged() && !attacker.isAirUnit()) {  // Air Units are Ranged, but take damage as well
            defender.takeDamage(potentialDamageToDefender) // straight up
        } else {
            //melee attack is complicated, because either side may defeat the other midway
            //so...for each round, we randomize who gets the attack in. Seems to be a good way to work for now.

            while (potentialDamageToDefender + potentialDamageToAttacker > 0) {
                if (Random.Default.nextInt(potentialDamageToDefender + potentialDamageToAttacker) < potentialDamageToDefender) {
                    potentialDamageToDefender--
                    defender.takeDamage(1)
                    if (defender.isDefeated()) break
                } else {
                    potentialDamageToAttacker--
                    attacker.takeDamage(1)
                    if (attacker.isDefeated()) break
                }
            }
        }

        val defenderDamageDealt = attackerHealthBefore - attacker.getHealth()
        val attackerDamageDealt = defenderHealthBefore - defender.getHealth()

        if (attacker is MapUnitCombatant)
            for (unique in attacker.unit.getTriggeredUniques(UniqueType.TriggerUponLosingHealth))
                if (unique.conditionals.any { it.params[0].toInt() <= defenderDamageDealt })
                    UniqueTriggerActivation.triggerUnitwideUnique(unique, attacker.unit, triggerNotificationText = "due to losing [$defenderDamageDealt] HP")

        if (defender is MapUnitCombatant)
            for (unique in defender.unit.getTriggeredUniques(UniqueType.TriggerUponLosingHealth))
                if (unique.conditionals.any { it.params[0].toInt() <= attackerDamageDealt })
                    UniqueTriggerActivation.triggerUnitwideUnique(unique, defender.unit, triggerNotificationText = "due to losing [$attackerDamageDealt] HP")

        plunderFromDamage(attacker, defender, attackerDamageDealt)
        return DamageDealt(attackerDamageDealt, defenderDamageDealt)
    }

    private fun plunderFromDamage(
        plunderingUnit: ICombatant,
        plunderedUnit: ICombatant,
        damageDealt: Int
    ) {
        // implementation based on the description of the original civilopedia, see issue #4374
        if (plunderingUnit !is MapUnitCombatant) return
        val plunderedGoods = Stats()

        for (unique in plunderingUnit.unit.getMatchingUniques(UniqueType.DamageUnitsPlunder, checkCivInfoUniques = true)) {
            if (plunderedUnit.matchesCategory(unique.params[1])) {
                val percentage = unique.params[0].toFloat()
                plunderedGoods.add(Stat.valueOf(unique.params[2]), percentage / 100f * damageDealt)
            }
        }

        val civ = plunderingUnit.getCivInfo()
        for ((key, value) in plunderedGoods) {
            val plunderedAmount = value.toInt()
            civ.addStat(key, plunderedAmount)
            civ.addNotification(
                "Your [${plunderingUnit.getName()}] plundered [${plunderedAmount}] [${key.name}] from [${plunderedUnit.getName()}]",
                plunderedUnit.getTile().position,
                NotificationCategory.War,
                plunderingUnit.getName(), NotificationIcon.War, "StatIcons/${key.name}",
                if (plunderedUnit is CityCombatant) NotificationIcon.City else plunderedUnit.getName()
            )
        }
    }

    private fun postBattleNotifications(
        attacker: ICombatant,
        defender: ICombatant,
        attackedTile: Tile,
        attackerTile: Tile? = null,
        damageDealt: DamageDealt? = null
    ) {
        if (attacker.getCivInfo() != defender.getCivInfo()) {
            // If what happened was that a civilian unit was captured, that's dealt with in the captureCivilianUnit function
            val (whatHappenedIcon, whatHappenedString) = when {
                attacker !is CityCombatant && attacker.isDefeated() ->
                    NotificationIcon.War to " was destroyed while attacking"
                !defender.isDefeated() ->
                    NotificationIcon.War to " has attacked"
                defender.isCity() && attacker.isMelee() && attacker.getCivInfo().isBarbarian() ->
                    NotificationIcon.War to " has raided"
                defender.isCity() && attacker.isMelee() ->
                    NotificationIcon.War to " has captured"
                else ->
                    NotificationIcon.Death to " has destroyed"
            }
            val attackerString =
                    if (attacker.isCity()) "Enemy city [" + attacker.getName() + "]"
                    else "An enemy [" + attacker.getName() + "]"
            val defenderString =
                    if (defender.isCity())
                        if (defender.isDefeated() && attacker.isRanged()) " the defence of [" + defender.getName() + "]"
                        else " [" + defender.getName() + "]"
                    else " our [" + defender.getName() + "]"
            val attackerHurtString = if (damageDealt != null) " ([-${damageDealt.defenderDealt}] HP)" else ""
            val defenderHurtString = if (damageDealt != null) " ([-${damageDealt.attackerDealt}] HP)" else ""
            val notificationString = attackerString + attackerHurtString + whatHappenedString + defenderString + defenderHurtString
            val attackerIcon = if (attacker is CityCombatant) NotificationIcon.City else attacker.getName()
            val defenderIcon = if (defender is CityCombatant) NotificationIcon.City else defender.getName()
            val locations = LocationAction(attackedTile.position, attackerTile?.position)
            defender.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, attackerIcon, whatHappenedIcon, defenderIcon)
        }
    }

    private fun tryHealAfterKilling(attacker: ICombatant) {
        if (attacker is MapUnitCombatant)
            for (unique in attacker.unit.getMatchingUniques(UniqueType.HealsAfterKilling, checkCivInfoUniques = true)) {
                val amountToHeal = unique.params[0].toInt()
                attacker.unit.healBy(amountToHeal)
            }
    }


    private fun postBattleMoveToAttackedTile(attacker: ICombatant, defender: ICombatant, attackedTile: Tile) {
        if (!attacker.isMelee()) return
        if (!defender.isDefeated() && defender.getCivInfo() != attacker.getCivInfo()) return
        if (attacker is MapUnitCombatant && attacker.unit.cache.cannotMove) return

        // This is so that if we attack e.g. a barbarian in enemy territory that we can't enter, we won't enter it
        if ((attacker as MapUnitCombatant).unit.movement.canMoveTo(attackedTile)) {
            // Units that can move after attacking are not affected by zone of control if the
            // movement is caused by killing a unit. Effectively, this means that attack movements
            // are exempt from zone of control, since units that cannot move after attacking already
            // lose all remaining movement points anyway.
            attacker.unit.movement.moveToTile(attackedTile, considerZoneOfControl = false)
            attacker.unit.mostRecentMoveType = UnitMovementMemoryType.UnitAttacked
        }
    }

    private fun postBattleAddXp(attacker: ICombatant, defender: ICombatant) {
        if (attacker.isAirUnit()) {
            addXp(attacker, 4, defender)
            addXp(defender, 2, attacker)
        } else if (attacker.isRanged()) { // ranged attack
            if(defender.isCity())
                addXp(attacker, 3, defender)
            else
                addXp(attacker, 2, defender)
            addXp(defender, 2, attacker)
        } else if (!defender.isCivilian()) // unit was not captured but actually attacked
        {
            addXp(attacker, 5, defender)
            addXp(defender, 4, attacker)
        }
    }

    private fun reduceAttackerMovementPointsAndAttacks(attacker: ICombatant, defender: ICombatant) {
        if (attacker is MapUnitCombatant) {
            val unit = attacker.unit
            // If captured this civilian, doesn't count as attack
            // And we've used a movement already
            if(defender.isCivilian() && attacker.getTile() == defender.getTile()){
                return
            }
            unit.attacksThisTurn += 1
            if (unit.hasUnique(UniqueType.CanMoveAfterAttacking) || unit.maxAttacksPerTurn() > unit.attacksThisTurn) {
                // if it was a melee attack and we won, then the unit ALREADY got movement points deducted,
                // for the movement to the enemy's tile!
                // and if it's an air unit, it only has 1 movement anyway, so...
                if (!attacker.unit.baseUnit.movesLikeAirUnits() && !(attacker.isMelee() && defender.isDefeated()))
                    unit.useMovementPoints(1f)
            } else unit.currentMovement = 0f
            if (unit.isFortified() || unit.isSleeping())
                attacker.unit.action = null // but not, for instance, if it's Set Up - then it should definitely keep the action!
        } else if (attacker is CityCombatant) {
            attacker.city.attackedThisTurn = true
        }
    }

    // XP!
    private fun addXp(thisCombatant: ICombatant, amount: Int, otherCombatant: ICombatant) {
        if (thisCombatant !is MapUnitCombatant) return
        val civ = thisCombatant.getCivInfo()
        val otherIsBarbarian = otherCombatant.getCivInfo().isBarbarian()
        val promotions = thisCombatant.unit.promotions
        val modConstants = civ.gameInfo.ruleset.modOptions.constants

        if (otherIsBarbarian && promotions.totalXpProduced() >= modConstants.maxXPfromBarbarians)
            return
        val unitCouldAlreadyPromote = promotions.canBePromoted()

        val stateForConditionals = StateForConditionals(civInfo = civ, ourCombatant = thisCombatant, theirCombatant = otherCombatant)

        val baseXP = amount + thisCombatant
            .getMatchingUniques(UniqueType.FlatXPGain, stateForConditionals, true)
            .sumOf { it.params[0].toInt() }

        val xpBonus = thisCombatant
            .getMatchingUniques(UniqueType.PercentageXPGain, stateForConditionals, true)
            .sumOf { it.params[0].toDouble() }
        val xpModifier = 1.0 + xpBonus / 100

        val xpGained = (baseXP * xpModifier).toInt()
        promotions.XP += xpGained

        if (!otherIsBarbarian && civ.isMajorCiv()) { // Can't get great generals from Barbarians
            val greatGeneralPointsBonus = thisCombatant
                .getMatchingUniques(UniqueType.GreatPersonEarnedFaster, stateForConditionals, true)
                .filter { unique ->
                    val unitName = unique.params[0]
                    // From the unique we know this unit exists
                    val unit = civ.gameInfo.ruleset.units[unitName]!!
                    unit.uniques.contains("Great Person - [War]")
                }
                .sumOf { it.params[1].toDouble() }
            val greatGeneralPointsModifier = 1.0 + greatGeneralPointsBonus / 100

            val greatGeneralPointsGained = (xpGained * greatGeneralPointsModifier).toInt()
            civ.greatPeople.greatGeneralPoints += greatGeneralPointsGained
        }

        if (!thisCombatant.isDefeated() && !unitCouldAlreadyPromote && promotions.canBePromoted()) {
            val pos = thisCombatant.getTile().position
            civ.addNotification("[${thisCombatant.unit.displayName()}] can be promoted!",
                listOf(MapUnitAction(pos), PromoteUnitAction(thisCombatant.getName(), pos)),
                NotificationCategory.Units, thisCombatant.unit.name)
        }
    }

    private fun conquerCity(city: City, attacker: MapUnitCombatant) {
        val attackerCiv = attacker.getCivInfo()

        attackerCiv.addNotification("We have conquered the city of [${city.name}]!", city.location, NotificationCategory.War, NotificationIcon.War)

        city.hasJustBeenConquered = true
        city.getCenterTile().apply {
            if (militaryUnit != null) militaryUnit!!.destroy()
            if (civilianUnit != null) captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit!!), checkDefeat = false)
            for (airUnit in airUnits.toList()) airUnit.destroy()
        }

        val stateForConditionals = StateForConditionals(civInfo = attackerCiv, city=city, unit = attacker.unit, ourCombatant = attacker, attackedTile = city.getCenterTile())
        for (unique in attacker.getMatchingUniques(UniqueType.CaptureCityPlunder, stateForConditionals, true)) {
            attackerCiv.addStat(
                Stat.valueOf(unique.params[2]),
                unique.params[0].toInt() * city.cityStats.currentCityStats[Stat.valueOf(unique.params[1])].toInt()
            )
        }

        if (attackerCiv.isBarbarian() || attackerCiv.isOneCityChallenger()) {
            city.destroyCity(true)
            return
        }

        if (city.isOriginalCapital && city.foundingCiv == attackerCiv.civName) {
            // retaking old capital
            city.puppetCity(attackerCiv)
            //Although in Civ5 Venice is unable to re-annex their capital, that seems a bit silly. No check for May not annex cities here.
            city.annexCity()
        } else if (attackerCiv.isHuman()) {
            // we're not taking our former capital
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.id))
        } else {
            NextTurnAutomation.onConquerCity(attackerCiv, city)
        }

        if (attackerCiv.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Conquer a city")

        for (unique in attackerCiv.getTriggeredUniques(UniqueType.TriggerUponConqueringCity, stateForConditionals)
                + attacker.unit.getTriggeredUniques(UniqueType.TriggerUponConqueringCity, stateForConditionals))
            UniqueTriggerActivation.triggerCivwideUnique(unique, attackerCiv, city)
    }

    fun getMapCombatantOfTile(tile: Tile): ICombatant? {
        if (tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if (tile.militaryUnit != null) return MapUnitCombatant(tile.militaryUnit!!)
        if (tile.civilianUnit != null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    /**
     * @throws IllegalArgumentException if the [attacker] and [defender] belong to the same civ.
     */
    fun captureCivilianUnit(attacker: ICombatant, defender: MapUnitCombatant, checkDefeat: Boolean = true) {
        require(attacker.getCivInfo() != defender.getCivInfo()) {
            "Can't capture our own unit!"
        }

        // need to save this because if the unit is captured its owner wil be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = defender.unit
        // Stop current action
        capturedUnit.action = null

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
            capturedUnit.hasUnique(UniqueType.FoundCity) && attacker.getCivInfo().isCityState() -> {
                capturedUnit.destroy()
                wasDestroyedInstead = true
            }
            // Is it our old unit?
            attacker.getCivInfo() == originalOwner -> {
                // Then it is recaptured without converting settlers to workers
                capturedUnit.capturedBy(attacker.getCivInfo())
            }
            // Return captured civilian to its original owner?
            defender.getCivInfo().isBarbarian()
                    && originalOwner != null
                    && !originalOwner.isBarbarian()
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
                        capturedUnitTile.position.toString()
                    )
                )
            }

            else -> captureOrConvertToWorker(capturedUnit, attacker.getCivInfo())
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
            triggerDefeatUniques(defender, attacker, capturedUnitTile)
        }

        if (checkDefeat)
            destroyIfDefeated(defenderCiv, attacker.getCivInfo())
        capturedUnit.updateVisibleTiles()
    }

    fun captureOrConvertToWorker(capturedUnit: MapUnit, capturingCiv: Civilization){
        // Captured settlers are converted to workers unless captured by barbarians (so they can be returned later).
        if (capturedUnit.hasUnique(UniqueType.FoundCity) && !capturingCiv.isBarbarian()) {
            capturedUnit.destroy()
            // This is so that future checks which check if a unit has been captured are caught give the right answer
            //  For example, in postBattleMoveToAttackedTile
            capturedUnit.civ = capturingCiv

            val workerTypeUnit = capturingCiv.gameInfo.ruleset.units.values
                .firstOrNull { it.isCivilian() && it.getMatchingUniques(UniqueType.BuildImprovements)
                    .any { unique -> unique.params[0] == "Land" } }

            if (workerTypeUnit != null)
                capturingCiv.units.placeUnitNearTile(capturedUnit.currentTile.position, workerTypeUnit)
        }
        else capturedUnit.capturedBy(capturingCiv)
    }

    fun destroyIfDefeated(attackedCiv: Civilization, attacker: Civilization) {
        if (attackedCiv.isDefeated()) {
            if (attackedCiv.isCityState())
                attackedCiv.cityStateFunctions.cityStateDestroyed(attacker)
            attackedCiv.destroy()
            attacker.popupAlerts.add(PopupAlert(AlertType.Defeated, attackedCiv.civName))
        }
    }

    /**
     *  Checks whether [nuke] is allowed to nuke [targetTile]
     *  - Not if we would need to declare war on someone we can't.
     *  - Disallow nuking the tile the nuke is in, as per Civ5 (but not nuking your own tiles/units otherwise)
     *
     *  Both [BattleTable.simulateNuke] and [SpecificUnitAutomation.automateNukes] check range, so that check is omitted here.
     */
    fun mayUseNuke(nuke: MapUnitCombatant, targetTile: Tile): Boolean {
        if (nuke.getTile() == targetTile) return false
        // Can only nuke visible Tiles
        if (!targetTile.isVisible(nuke.getCivInfo())) return false

        var canNuke = true
        val attackerCiv = nuke.getCivInfo()
        fun checkDefenderCiv(defenderCiv: Civilization?) {
            if (defenderCiv == null) return
            // Allow nuking yourself! (Civ5 source: CvUnit::isNukeVictim)
            if (defenderCiv == attackerCiv || defenderCiv.isDefeated()) return
            // Gleaned from Civ5 source - this disallows nuking unknown civs even in invisible tiles
            // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvUnit.cpp#L5056
            // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvTeam.cpp#L986
            if (attackerCiv.knows(defenderCiv) && attackerCiv.getDiplomacyManager(defenderCiv).canAttack())
                return
            canNuke = false
        }

        val blastRadius = nuke.unit.getNukeBlastRadius()
        for (tile in targetTile.getTilesInDistance(blastRadius)) {
            checkDefenderCiv(tile.getOwner())
            checkDefenderCiv(getMapCombatantOfTile(tile)?.getCivInfo())
        }
        return canNuke
    }

    @Suppress("FunctionName")   // Yes we want this name to stand out
    fun NUKE(attacker: MapUnitCombatant, targetTile: Tile) {
        val attackingCiv = attacker.getCivInfo()
        val notifyDeclaredWarCivs = ArrayList<Civilization>()
        fun tryDeclareWar(civSuffered: Civilization) {
            if (civSuffered != attackingCiv
                && civSuffered.knows(attackingCiv)
                && civSuffered.getDiplomacyManager(attackingCiv).diplomaticStatus != DiplomaticStatus.War
            ) {
                attackingCiv.getDiplomacyManager(civSuffered).declareWar()
                if (!notifyDeclaredWarCivs.contains(civSuffered)) notifyDeclaredWarCivs.add(civSuffered)
            }
        }

        val nukeStrength = attacker.unit.getMatchingUniques(UniqueType.NuclearWeapon)
            .firstOrNull()?.params?.get(0)?.toInt() ?: return

        val blastRadius = attacker.unit.getMatchingUniques(UniqueType.BlastRadius)
            .firstOrNull()?.params?.get(0)?.toInt() ?: 2

        // Calculate the tiles that are hit
        val hitTiles = targetTile.getTilesInDistance(blastRadius)
        
        val hitCivsTerritory = ArrayList<Civilization>()
        // Declare war on the owners of all hit tiles
        for (hitCiv in hitTiles.mapNotNull { it.getOwner() }.distinct()) {
            hitCivsTerritory.add(hitCiv)
            tryDeclareWar(hitCiv)
        }

        // Declare war on all potentially hit units. They'll try to intercept the nuke before it drops
        for (civWhoseUnitWasAttacked in hitTiles
            .flatMap { it.getUnits() }
            .map { it.civ }.distinct()
            .filter { it != attackingCiv }) {
                tryDeclareWar(civWhoseUnitWasAttacked)
                if (attacker.unit.baseUnit.isAirUnit() && !attacker.isDefeated()) {
                    tryInterceptAirAttack(attacker, targetTile, civWhoseUnitWasAttacked, null)
            }
        }
        val nukeNotificationAction = sequenceOf( LocationAction(targetTile.position), CivilopediaAction("Units/" + attacker.getName()))
        // If the nuke has been intercepted and destroyed then it fails to detonate
        if (attacker.isDefeated()) {
            // Notify attacker that they are now at war for the attempt
            for (defendingCiv in notifyDeclaredWarCivs)
                attackingCiv.addNotification("After an attempted attack by our [${attacker.getName()}], [${defendingCiv}] has declared war on us!", nukeNotificationAction, NotificationCategory.Diplomacy, defendingCiv.civName, NotificationIcon.War, attacker.getName())
            return
        }

        // Notify attacker that they are now at war
        for (defendingCiv in notifyDeclaredWarCivs)
            attackingCiv.addNotification("After being hit by our [${attacker.getName()}], [${defendingCiv}] has declared war on us!", nukeNotificationAction, NotificationCategory.Diplomacy, defendingCiv.civName, NotificationIcon.War, attacker.getName())

        attacker.unit.attacksSinceTurnStart.add(Vector2(targetTile.position))

        for (tile in hitTiles) {
            // Handle complicated effects
            doNukeExplosionForTile(attacker, tile, nukeStrength, targetTile == tile)
        }

        // Message all other civs
        for (otherCiv in attackingCiv.gameInfo.civilizations) {
            if (!otherCiv.isAlive() || otherCiv == attackingCiv) continue
            if (hitCivsTerritory.contains(otherCiv))
                otherCiv.addNotification("A(n) [${attacker.getName()}] from [${attackingCiv.civName}] has exploded in our territory!",
                    nukeNotificationAction, NotificationCategory.War, attackingCiv.civName, NotificationIcon.War, attacker.getName())
            else if (otherCiv.knows(attackingCiv))
                otherCiv.addNotification("A(n) [${attacker.getName()}] has been detonated by [${attackingCiv.civName}]!",
                    nukeNotificationAction, NotificationCategory.War, attackingCiv.civName, NotificationIcon.War, attacker.getName())
            else
                otherCiv.addNotification("A(n) [${attacker.getName()}] has been detonated by an unkown civilization!",
                    nukeNotificationAction, NotificationCategory.War, NotificationIcon.War, attacker.getName())
        }
        
        // Instead of postBattleAction() just destroy the unit, all other functions are not relevant
        if (attacker.unit.hasUnique(UniqueType.SelfDestructs)) attacker.unit.destroy()

        // It's unclear whether using nukes results in a penalty with all civs, or only affected civs.
        // For now I'll make it give a diplomatic penalty to all known civs, but some testing for this would be appreciated
        for (civ in attackingCiv.getKnownCivs()) {
            civ.getDiplomacyManager(attackingCiv).setModifier(DiplomaticModifiers.UsedNuclearWeapons, -50f)
        }

        if (!attacker.isDefeated()) {
            attacker.unit.attacksThisTurn += 1
        }
    }

    private fun doNukeExplosionForTile(
        attacker: MapUnitCombatant,
        tile: Tile,
        nukeStrength: Int,
        isGroundZero: Boolean
    ) {
        // https://forums.civfanatics.com/resources/unit-guide-modern-future-units-g-k.25628/
        // https://www.carlsguides.com/strategy/civilization5/units/aircraft-nukes.ph
        // Testing done by Ravignir
        // original source code: GenerateNuclearExplosionDamage(), ApplyNuclearExplosionDamage()

        var damageModifierFromMissingResource = 1f
        val civResources = attacker.getCivInfo().getCivResourcesByName()
        for (resource in attacker.unit.baseUnit.getResourceRequirementsPerTurn().keys) {
            if (civResources[resource]!! < 0 && !attacker.getCivInfo().isBarbarian())
                damageModifierFromMissingResource *= 0.5f // I could not find a source for this number, but this felt about right
                // - Original Civ5 does *not* reduce damage from missing resource, from source inspection
        }

        var buildingModifier = 1f  // Strange, but in Civ5 a bunker mitigates damage to garrison, even if the city is destroyed by the nuke

        // Damage city and reduce its population
        val city = tile.getCity()
        if (city != null && tile.position == city.location) {
            buildingModifier = city.getAggregateModifier(UniqueType.GarrisonDamageFromNukes)
            doNukeExplosionDamageToCity(city, nukeStrength, damageModifierFromMissingResource)
            postBattleNotifications(attacker, CityCombatant(city), city.getCenterTile())
            destroyIfDefeated(city.civ, attacker.getCivInfo())
        }

        // Damage and/or destroy units on the tile
        for (unit in tile.getUnits().toList()) { // toList so if it's destroyed there's no concurrent modification
            val damage = (when {
                    isGroundZero || nukeStrength >= 2 -> 100
                    // The following constants are NUKE_UNIT_DAMAGE_BASE / NUKE_UNIT_DAMAGE_RAND_1 / NUKE_UNIT_DAMAGE_RAND_2 in Civ5
                    nukeStrength == 1 -> 30 + Random.Default.nextInt(40) + Random.Default.nextInt(40)
                    // Level 0 does not exist in Civ5 (it treats units same as level 2)
                    else -> 20 + Random.Default.nextInt(30)
                } * buildingModifier * damageModifierFromMissingResource + 1f.ulp).toInt()
            val defender = MapUnitCombatant(unit)
            if (unit.isCivilian()) {
                if (unit.health - damage <= 40) unit.destroy()  // Civ5: NUKE_NON_COMBAT_DEATH_THRESHOLD = 60
            } else {
                defender.takeDamage(damage)
            }
            postBattleNotifications(attacker, defender, defender.getTile())
            destroyIfDefeated(defender.getCivInfo(), attacker.getCivInfo())
        }

        // Pillage improvements, pillage roads, add fallout
        if (tile.isCityCenter()) return  // Never touch city centers - if they survived
        fun applyPillageAndFallout() {
            if (tile.getUnpillagedImprovement() != null && !tile.getTileImprovement()!!.hasUnique(UniqueType.Irremovable)) {
                if (tile.getTileImprovement()!!.hasUnique(UniqueType.Unpillagable)) {
                    tile.removeImprovement()
                } else {
                    tile.setPillaged()
                }
            }
            if (tile.getUnpillagedRoad() != RoadStatus.None)
                tile.setPillaged()
            if (tile.isWater || tile.isImpassible() || tile.terrainFeatures.contains("Fallout")) return
            tile.addTerrainFeature("Fallout")
        }

        if (tile.terrainHasUnique(UniqueType.DestroyableByNukesChance)) {
            // Note: Safe from concurrent modification exceptions only because removeTerrainFeature
            // *replaces* terrainFeatureObjects and the loop will continue on the old one
            for (terrainFeature in tile.terrainFeatureObjects) {
                for (unique in terrainFeature.getMatchingUniques(UniqueType.DestroyableByNukesChance)) {
                    val chance = unique.params[0].toFloat() / 100f
                    if (!(chance > 0f && isGroundZero) && Random.Default.nextFloat() >= chance) continue
                    tile.removeTerrainFeature(terrainFeature.name)
                    applyPillageAndFallout()
                }
            }
        } else if (isGroundZero || Random.Default.nextFloat() < 0.5f) {  // Civ5: NUKE_FALLOUT_PROB
            applyPillageAndFallout()
        }
    }

    /** @return the "protection" modifier from buildings (Bomb Shelter, UniqueType.PopulationLossFromNukes) */
    private fun doNukeExplosionDamageToCity(targetedCity: City, nukeStrength: Int, damageModifierFromMissingResource: Float) {
        // Original Capitals must be protected, `canBeDestroyed` is responsible for that check.
        // The `justCaptured = true` parameter is what allows other Capitals to suffer normally.
        if ((nukeStrength > 2 || nukeStrength > 1 && targetedCity.population.population < 5)
                && targetedCity.canBeDestroyed(true)) {
            targetedCity.destroyCity()
            return
        }

        val cityCombatant = CityCombatant(targetedCity)
        cityCombatant.takeDamage((cityCombatant.getHealth() * 0.5f * damageModifierFromMissingResource).toInt())

        // Difference to original: Civ5 rounds population loss down twice - before and after bomb shelters
        val populationLoss = (
                targetedCity.population.population *
                    targetedCity.getAggregateModifier(UniqueType.PopulationLossFromNukes) *
                when (nukeStrength) {
                    0 -> 0f
                    1 -> (30 + Random.Default.nextInt(20) + Random.Default.nextInt(20)) / 100f
                    2 -> (60 + Random.Default.nextInt(10) + Random.Default.nextInt(10)) / 100f
                    else -> 1f  // hypothetical nukeStrength 3 -> always to 1 pop
                }
            ).toInt().coerceAtMost(targetedCity.population.population - 1)
        targetedCity.population.addPopulation(-populationLoss)
    }

    private fun City.getAggregateModifier(uniqueType: UniqueType): Float {
        var modifier = 1f
        for (unique in getMatchingUniques(uniqueType)) {
            if (!matchesFilter(unique.params[1])) continue
            modifier *= unique.params[0].toPercent()
        }
        return modifier
    }

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
            if (!attacker.unit.baseUnit.movesLikeAirUnits())
                attacker.unit.useMovementPoints(1f)
        } else attacker.unit.currentMovement = 0f
        val attackerName = attacker.getName()

        // Make giant sequence of all potential Interceptors from all Civs isAtWarWith()
        var potentialInterceptors = sequence<MapUnit> {  }
        for (interceptingCiv in UncivGame.Current.gameInfo!!.civilizations
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
            val interceptingCiv = interceptor.civ
            val interceptorName = interceptor.name
            // pairs of LocationAction for Notification
            val locations = LocationAction(
                interceptor.currentTile.position,
                attacker.unit.currentTile.position
            )
            val locationsInterceptorUnknown =
                    LocationAction(attackedTile.position, attacker.unit.currentTile.position)

            interceptor.attacksThisTurn++  // even if you miss, you took the shot
            val damageDealt: DamageDealt
            if (!interceptor.baseUnit.isAirUnit()) {
                // Deal no damage (moddable in future?) and no XP
                val attackerText =
                        "Our [$attackerName] ([-0] HP) was attacked by an intercepting [$interceptorName] ([-0] HP)"
                val interceptorText =
                        "Our [$interceptorName] ([-0] HP) intercepted and attacked an enemy [$attackerName] ([-0] HP)"
                attacker.getCivInfo().addNotification(
                    attackerText, locations, NotificationCategory.War,
                    attackerName, NotificationIcon.War, interceptorName
                )
                interceptingCiv.addNotification(
                    interceptorText, locations, NotificationCategory.War,
                    interceptorName, NotificationIcon.War, attackerName
                )
                attacker.unit.action = null
                return
            } else {
                // Damage if Air v Air should work similar to Melee
                damageDealt = takeDamage(attacker, MapUnitCombatant(interceptor))

                // 5 XP to both
                addXp(MapUnitCombatant(interceptor), 5, attacker)
                addXp(attacker, 5, MapUnitCombatant(interceptor))
            }

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
                        if (attacker.getTile() in interceptingCiv.viewableTiles) "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
                        else "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an unknown enemy"
                    } else "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and attacked an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"

            interceptingCiv.addNotification(
                interceptorText, locations, NotificationCategory.War,
                interceptorName, NotificationIcon.War, attackerName
            )
            attacker.unit.action = null
            return
        }

        // No Interceptions available
        val attackerText = "Nothing tried to intercept our [$attackerName]"
        attacker.getCivInfo().addNotification(attackerText, NotificationCategory.War, attackerName)
        attacker.unit.action = null
    }

    private fun tryInterceptAirAttack(
        attacker: MapUnitCombatant,
        attackedTile: Tile,
        interceptingCiv: Civilization,
        defender: ICombatant?
    ): DamageDealt {
        if (attacker.unit.hasUnique(UniqueType.CannotBeIntercepted, StateForConditionals(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, attackedTile = attackedTile)))
            return DamageDealt.None

        // Pick highest chance interceptor
        val interceptor = interceptingCiv.units.getCivUnits()
            .filter { it.canIntercept(attackedTile) }
            .sortedByDescending { it.interceptChance() }
            .firstOrNull { unit ->
                // Can't intercept if we have a unique preventing it
                val conditionalState = StateForConditionals(interceptingCiv, ourCombatant = MapUnitCombatant(unit), theirCombatant = attacker, combatAction = CombatAction.Intercept, attackedTile = attackedTile)
                unit.getMatchingUniques(UniqueType.CannotInterceptUnits, conditionalState)
                    .none { attacker.matchesCategory(it.params[0]) }
                // Defender can't intercept either
                && unit != (defender as? MapUnitCombatant)?.unit
            }
            ?: return DamageDealt.None

        interceptor.attacksThisTurn++  // even if you miss, you took the shot
        // Does Intercept happen? If not, exit
        if (Random.Default.nextFloat() > interceptor.interceptChance() / 100f)
            return DamageDealt.None

        var damage = BattleDamage.calculateDamageToDefender(
            MapUnitCombatant(interceptor),
            attacker
        )

        var damageFactor = 1f + interceptor.interceptDamagePercentBonus().toFloat() / 100f
        damageFactor *= attacker.unit.receivedInterceptDamageFactor()

        damage = (damage.toFloat() * damageFactor).toInt().coerceAtMost(attacker.unit.health)

        attacker.takeDamage(damage)
        if (damage > 0)
            addXp(MapUnitCombatant(interceptor), 2, attacker)

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
        interceptingCiv.addNotification(interceptorText, locations, NotificationCategory.War,
                interceptorName, NotificationIcon.War, attackerName)

        return DamageDealt(0, damage)
    }

    private fun doWithdrawFromMeleeAbility(attacker: ICombatant, defender: ICombatant, baseWithdrawChance: Int): Boolean {
        if (baseWithdrawChance == 0) return false
        // Some notes...
        // unit.getUniques() is a union of BaseUnit uniques and Promotion effects.
        // according to some strategy guide the Slinger's withdraw ability is inherited on upgrade,
        // according to the Ironclad entry of the wiki the Caravel's is lost on upgrade.
        // therefore: Implement the flag as unique for the Caravel and Destroyer, as promotion for the Slinger.
        if (attacker !is MapUnitCombatant) return false         // allow simple access to unit property
        if (defender !is MapUnitCombatant) return false
        if (defender.unit.isEmbarked()) return false
        if (defender.unit.cache.cannotMove) return false
        // Promotions have no effect as per what I could find in available documentation
        val attackBaseUnit = attacker.unit.baseUnit
        val defendBaseUnit = defender.unit.baseUnit
        val fromTile = defender.getTile()
        val attTile = attacker.getTile()
        fun canNotWithdrawTo(tile: Tile): Boolean { // if the tile is what the defender can't withdraw to, this fun will return true
           return !defender.unit.movement.canMoveTo(tile)
                   || defendBaseUnit.isLandUnit() && !tile.isLand // forbid retreat from land to sea - embarked already excluded
                   || tile.isCityCenter() && tile.getOwner() != defender.getCivInfo() // forbid retreat into the city which doesn't belong to the defender
        }
        /* Calculate success chance: Base chance from json, calculation method from https://www.bilibili.com/read/cv2216728
        In general, except attacker's tile, 5 tiles neighbors the defender :
        2 of which are also attacker's neighbors ( we call them 2-Tiles) and the other 3 aren't (we call them 3-Tiles).
        Withdraw chance depends on 2 factors : attacker's movement and how many tiles in 3-Tiles the defender can't withdraw to.
        If the defender can withdraw, at first we choose a tile as toTile from 3-Tiles the defender can withdraw to.
        If 3-Tiles the defender can withdraw to is null, we choose this from 2-Tiles the defender can withdraw to.
        If 2-Tiles the defender can withdraw to is also null, we return false.
        */
        val percentChance = baseWithdrawChance - max(0, (attackBaseUnit.movement-2)) * 20 -
                fromTile.neighbors.filterNot { it == attTile || it in attTile.neighbors }.count { canNotWithdrawTo(it) } * 20
        // Get a random number in [0,100) : if the number <= percentChance, defender will withdraw from melee
        if (Random( // 'randomness' is consistent for turn and tile, to avoid save-scumming
                    (attacker.getCivInfo().gameInfo.turns * defender.getTile().hashCode()).toLong()
        ).nextInt(100) > percentChance) return false
        val firstCandidateTiles = fromTile.neighbors.filterNot { it == attTile || it in attTile.neighbors }
                .filterNot { canNotWithdrawTo(it) }
        val secondCandidateTiles = fromTile.neighbors.filter { it in attTile.neighbors }
                .filterNot { canNotWithdrawTo(it) }
        val toTile: Tile = when {
            firstCandidateTiles.any() -> firstCandidateTiles.toList().random()
            secondCandidateTiles.any() -> secondCandidateTiles.toList().random()
            else -> return false
        }
        // Withdraw success: Do it - move defender to toTile for no cost
        // NOT defender.unit.movement.moveToTile(toTile) - we want a free teleport
        defender.unit.removeFromTile()
        defender.unit.putInTile(toTile)
        defender.unit.mostRecentMoveType = UnitMovementMemoryType.UnitWithdrew
        // and count 1 attack for attacker but leave it in place
        reduceAttackerMovementPointsAndAttacks(attacker, defender)

        val attackingUnit = attackBaseUnit.name; val defendingUnit = defendBaseUnit.name
        val notificationString = "[$defendingUnit] withdrew from a [$attackingUnit]"
        val locations = LocationAction(toTile.position, attacker.getTile().position)
        defender.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, defendingUnit, NotificationIcon.War, attackingUnit)
        attacker.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, defendingUnit, NotificationIcon.War, attackingUnit)
        return true
    }

    private fun doDestroyImprovementsAbility(attacker: MapUnitCombatant, attackedTile: Tile, defender: ICombatant) {
        if (attackedTile.improvement == null) return

        val conditionalState = StateForConditionals(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, combatAction = CombatAction.Attack, attackedTile = attackedTile)
        if (!attackedTile.getTileImprovement()!!.hasUnique(UniqueType.Unpillagable)
            && attacker.hasUnique(UniqueType.DestroysImprovementUponAttack, conditionalState)
        ) {
            val currentTileImprovement = attackedTile.improvement
            attackedTile.removeImprovement()
            defender.getCivInfo().addNotification(
                "An enemy [${attacker.unit.baseUnit.name}] has destroyed our tile improvement [${currentTileImprovement}]",
                LocationAction(attackedTile.position, attacker.getTile().position),
                NotificationCategory.War, attacker.unit.baseUnit.name,
                NotificationIcon.War)
        }
    }
}

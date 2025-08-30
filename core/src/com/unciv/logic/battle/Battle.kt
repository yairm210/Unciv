package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.PromoteUnitAction
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.stats.SubStat
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsPillage
import com.unciv.utils.debug
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
object Battle {

    /**
     * Moves [attacker] to [attackableTile], handles siege setup then attacks if still possible
     * (by calling [attack] or [Nuke.NUKE]). Does _not_ play the attack sound!
     *
     * Currently not used by UI, only by automation via [BattleHelper.tryAttackNearbyEnemy][com.unciv.logic.automation.unit.BattleHelper.tryAttackNearbyEnemy]
     */
    fun moveAndAttack(attacker: ICombatant, attackableTile: AttackableTile) {
        if (!movePreparingAttack(attacker, attackableTile, true)) return
        attackOrNuke(attacker, attackableTile)
    }

    /**
     * Moves [attacker] to [attackableTile], handles siege setup and returns `true` if an attack is still possible.
     *
     * This is a logic function, not UI, so e.g. sound needs to be handled after calling this.
     */
    fun movePreparingAttack(attacker: ICombatant, attackableTile: AttackableTile, tryHealPillage: Boolean = false): Boolean {
        if (attacker !is MapUnitCombatant) return true
        val tilesMovedThrough = attacker.unit.movement.getDistanceToTiles().getPathToTile(attackableTile.tileToAttackFrom)
        attacker.unit.movement.moveToTile(attackableTile.tileToAttackFrom)
        /**
         * When calculating movement distance, we assume that a hidden tile is 1 movement point,
         * which can lead to EXCEEDINGLY RARE edge cases where you think
         * that you can attack a tile by passing through a HIDDEN TILE,
         * but the hidden tile is actually IMPASSIBLE, so you stop halfway!
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
                && attacker.unit.hasMovement()
        ) {
            attacker.unit.action = UnitActionType.SetUp.value
            attacker.unit.useMovementPoints(1f)
        }

        if (tryHealPillage) {
            // Now lets retroactively see if we can pillage any improvement on the path improvement to heal
            // while still being able to attack
            for (tileToPillage in tilesMovedThrough) {
                if (attacker.unit.currentMovement <= 1f || attacker.unit.health > 90) break // We are done pillaging

                if (UnitActionsPillage.canPillage(attacker.unit, tileToPillage)
                    && tileToPillage.canPillageTileImprovement()) {
                    UnitActionsPillage.getPillageAction(attacker.unit, tileToPillage)?.action?.invoke()
                }
            }
        }
        return attacker.unit.hasMovement()
    }

    /**
     * This is meant to be called only after all prerequisite checks have been done.
     */
    fun attackOrNuke(attacker: ICombatant, attackableTile: AttackableTile): DamageDealt {
        return if (attacker is MapUnitCombatant && attacker.unit.isNuclearWeapon()) {
            Nuke.NUKE(attacker, attackableTile.tileToAttack)
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
            interceptDamage = AirInterception.tryInterceptAirAttack(attacker, attackedTile, defender.getCivInfo(), defender)
            if (attacker.isDefeated()) return interceptDamage
        } else interceptDamage = DamageDealt.None

        if (hasWithdrawnFromMeelee(attacker, defender, attackedTile)) return DamageDealt.None

        val isAlreadyDefeatedCity = defender is CityCombatant && defender.isDefeated()

        triggerCombatUniques(attacker, defender, attackedTile)

        val damageDealt = takeDamage(attacker, defender)

        // check if unit is captured by the attacker (prize ships unique)
        // As ravignir clarified in issue #4374, this only works for aggressor
        val captureMilitaryUnitSuccess = BattleUnitCapture.tryCaptureMilitaryUnit(attacker, defender, attackedTile)

        if (!captureMilitaryUnitSuccess) // capture creates a new unit, but `defender` still is the original, so this function would still show a kill message
            postBattleNotifications(attacker, defender, attackedTile, attacker.getTile(), damageDealt)

        if (defender.getCivInfo().isBarbarian && attackedTile.improvement == Constants.barbarianEncampment)
            defender.getCivInfo().gameInfo.barbarians.campAttacked(attackedTile.position)

        // This needs to come BEFORE the move-to-tile, because if we haven't conquered it we can't move there =)
        handleCityDefeated(defender, attacker)

        
        // Add culture when defeating a barbarian when Honor policy is adopted, gold from enemy killed when honor is complete
        // or any enemy military unit with Sacrificial captives unique (can be either attacker or defender!)
        triggerPostKillingUniques(defender, attacker, attackedTile)
        
        if (attacker is MapUnitCombatant && defender is MapUnitCombatant){
            triggerDamageUniquesForUnit(attacker, defender, attackedTile, CombatAction.Attack)
            if (!attacker.isRanged()) triggerDamageUniquesForUnit(defender, attacker, attackedTile, CombatAction.Defend)
        }

        // Exploring units surviving an attack should "wake up"
        if (!defender.isDefeated() && defender is MapUnitCombatant && defender.unit.isExploring())
            defender.unit.action = null

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

        reduceAttackerMovementPointsAndAttacks(attacker, defender, attackedTile)

        if (!isAlreadyDefeatedCity) postBattleAddXp(attacker, defender)

        if (attacker is CityCombatant) {
            val cityCanBombardNotification = attacker.getCivInfo().notifications
                .firstOrNull { it.text == "Your city [${attacker.getName()}] can bombard the enemy!" }
            attacker.getCivInfo().notifications.remove(cityCanBombardNotification)
        }

        return damageDealt + interceptDamage
    }

    private fun triggerPostKillingUniques(
        defender: ICombatant,
        attacker: ICombatant,
        attackedTile: Tile
    ) {
        if (defender.isDefeated() && defender is MapUnitCombatant && !defender.unit.isCivilian()) {
            tryEarnFromKilling(attacker, defender)
            tryHealAfterKilling(attacker)

            if (attacker is MapUnitCombatant) triggerVictoryUniques(attacker, defender, attackedTile)
            triggerDefeatUniques(defender, attacker, attackedTile)

        } else if (attacker.isDefeated() && attacker is MapUnitCombatant && !attacker.unit.isCivilian()) {
            tryEarnFromKilling(defender, attacker)
            tryHealAfterKilling(defender)

            if (defender is MapUnitCombatant) triggerVictoryUniques(defender, attacker, attackedTile)
            triggerDefeatUniques(attacker, defender, attackedTile)
        }
    }

    private fun handleCityDefeated(defender: ICombatant, attacker: ICombatant) {
        if (!defender.isDefeated() || defender !is CityCombatant || attacker !is MapUnitCombatant || !attacker.isMelee()
            || attacker.unit.hasUnique(UniqueType.CannotCaptureCities, checkCivInfoUniques = true)
        ) return
        
        // Barbarians can't capture cities, instead raiding them for gold
        if (attacker.unit.civ.isBarbarian) {
            defender.takeDamage(-1) // Back to 2 HP
            val ransom = min(200, defender.city.civ.gold)
            defender.city.civ.addGold(-ransom)
            defender.city.civ.addNotification(
                "Barbarians raided [${defender.city.name}] and stole [$ransom] Gold from your treasury!",
                defender.city.location,
                NotificationCategory.War,
                NotificationIcon.War
            )
            attacker.unit.destroy() // Remove the barbarian
        } else
            conquerCity(defender.city, attacker)
    }

    private fun triggerCombatUniques(attacker: ICombatant, defender: ICombatant, attackedTile: Tile) {
        val attackerContext = GameContext(attacker.getCivInfo(),
            ourCombatant = attacker, theirCombatant = defender, tile = attackedTile, combatAction = CombatAction.Attack)
        if (attacker is MapUnitCombatant)
            for (unique in attacker.unit.getTriggeredUniques(UniqueType.TriggerUponCombat, attackerContext)) {
                val unit = if (unique.params[0] == Constants.targetUnit && defender is MapUnitCombatant)
                    defender.unit
                else attacker.unit
                UniqueTriggerActivation.triggerUnique(unique, unit)
            }
        val defenderContext = GameContext(defender.getCivInfo(),
            ourCombatant = defender, theirCombatant = attacker, tile = attackedTile, combatAction = CombatAction.Defend)
        if (defender is MapUnitCombatant)
            for (unique in defender.unit.getTriggeredUniques(UniqueType.TriggerUponCombat, defenderContext)) {
                val unit = if (unique.params[0] == Constants.targetUnit && attacker is MapUnitCombatant)
                attacker.unit
                else defender.unit
                UniqueTriggerActivation.triggerUnique(unique, unit)
            }
    }


    private fun triggerVictoryUniques(ourUnit: MapUnitCombatant, enemy: MapUnitCombatant, attackedTile: Tile) {
        val gameContext = GameContext(civInfo = ourUnit.getCivInfo(),
            ourCombatant = ourUnit, theirCombatant = enemy, tile = attackedTile)
        for (unique in ourUnit.unit.getTriggeredUniques(UniqueType.TriggerUponDefeatingUnit, gameContext)
        { enemy.unit.matchesFilter(it.params[0]) })
            UniqueTriggerActivation.triggerUnique(unique, ourUnit.unit, triggerNotificationText = "due to our [${ourUnit.getName()}] defeating a [${enemy.getName()}]")
    }

    private fun triggerDamageUniquesForUnit(triggeringUnit: MapUnitCombatant, enemy: MapUnitCombatant, attackedTile: Tile, combatAction: CombatAction){
        val gameContext = GameContext(civInfo = triggeringUnit.getCivInfo(),
            ourCombatant = triggeringUnit, theirCombatant = enemy, tile = attackedTile, combatAction = combatAction)

        for (unique in triggeringUnit.unit.getTriggeredUniques(UniqueType.TriggerUponDamagingUnit, gameContext)
        { enemy.matchesFilter(it.params[0]) }){
            if (unique.params[0] == Constants.targetUnit){
                UniqueTriggerActivation.triggerUnique(unique, enemy.unit, triggerNotificationText = "due to our [${enemy.getName()}] being damaged by a [${triggeringUnit.getName()}]")
            } else {
                UniqueTriggerActivation.triggerUnique(unique, triggeringUnit.unit, triggerNotificationText = "due to our [${triggeringUnit.getName()}] damaging a [${enemy.getName()}]")
            }
        }
    }

    internal fun triggerDefeatUniques(ourUnit: MapUnitCombatant, enemy: ICombatant, attackedTile: Tile) {
        val gameContext = GameContext(civInfo = ourUnit.getCivInfo(),
            ourCombatant = ourUnit, theirCombatant=enemy, tile = attackedTile)
        for (unique in ourUnit.unit.getTriggeredUniques(UniqueType.TriggerUponDefeat, gameContext))
            UniqueTriggerActivation.triggerUnique(unique, ourUnit.unit, triggerNotificationText = "due to our [${ourUnit.getName()}] being defeated by a [${enemy.getName()}]")
    }

    private fun tryEarnFromKilling(civUnit: ICombatant, defeatedUnit: MapUnitCombatant) {
        val unitStr = max(defeatedUnit.unit.baseUnit.strength, defeatedUnit.unit.baseUnit.rangedStrength)
        val unitCost = defeatedUnit.unit.baseUnit.cost

        val bonusUniques = getKillUnitPlunderUniques(civUnit, defeatedUnit)

        for (unique in bonusUniques) {
            if (!defeatedUnit.matchesFilter(unique.params[1])) continue

            val yieldPercent = unique.params[0].toFloat() / 100
            val defeatedUnitYieldSourceType = unique.params[2]
            val yieldTypeSourceAmount =
                if (defeatedUnitYieldSourceType == "Cost") unitCost else unitStr
            val yieldAmount = (yieldTypeSourceAmount * yieldPercent).toInt()

            val resource = civUnit.getCivInfo().gameInfo.ruleset.getGameResource(unique.params[3])
                ?: continue
            civUnit.getCivInfo().addGameResource(resource, yieldAmount)
        }

        // CS friendship from killing barbarians
        if (defeatedUnit.getCivInfo().isBarbarian && !defeatedUnit.isCivilian() && civUnit.getCivInfo().isMajorCiv()) {
            for (cityState in defeatedUnit.getCivInfo().gameInfo.getAliveCityStates()) {
                if (civUnit.getCivInfo().knows(cityState) && defeatedUnit.unit.threatensCiv(cityState)) {
                    cityState.cityStateFunctions.threateningBarbarianKilledBy(civUnit.getCivInfo())
                }
            }
        }

        // CS war with major pseudo-quest
        for (cityState in defeatedUnit.getCivInfo().gameInfo.getAliveCityStates()) {
            cityState.questManager.militaryUnitKilledBy(civUnit.getCivInfo(), defeatedUnit.getCivInfo())
        }
    }

    /** See [UniqueType.KillUnitPlunder] for params */
    @Readonly
    private fun getKillUnitPlunderUniques(civUnit: ICombatant, defeatedUnit: MapUnitCombatant): ArrayList<Unique> {
        val bonusUniques = ArrayList<Unique>()

        val gameContext = GameContext(civInfo = civUnit.getCivInfo(), ourCombatant = civUnit, theirCombatant = defeatedUnit)
        if (civUnit is MapUnitCombatant) {
            bonusUniques.addAll(civUnit.getMatchingUniques(UniqueType.KillUnitPlunder, gameContext, true))
        } else {
            bonusUniques.addAll(civUnit.getCivInfo().getMatchingUniques(UniqueType.KillUnitPlunder, gameContext))
        }

        val cityWithReligion =
            civUnit.getTile().getTilesInDistance(4).firstOrNull {
                it.isCityCenter() && it.getCity()!!.getMatchingUniques(UniqueType.KillUnitPlunderNearCity, gameContext).any()
            }?.getCity()
        if (cityWithReligion != null) {
            bonusUniques.addAll(cityWithReligion.getMatchingUniques(UniqueType.KillUnitPlunderNearCity, gameContext))
        }
        return bonusUniques
    }


    /** Holder for battle result - actual damage.
     *  @param attackerDealt Damage done by attacker to defender
     *  @param defenderDealt Damage done by defender to attacker
     */
    data class DamageDealt(val attackerDealt: Int, val defenderDealt: Int) {
        @Pure operator fun plus(other: DamageDealt) =
            DamageDealt(attackerDealt + other.attackerDealt, defenderDealt + other.defenderDealt)
        companion object {
            val None = DamageDealt(0, 0)
        }
    }

    internal fun takeDamage(attacker: ICombatant, defender: ICombatant): DamageDealt {
        var potentialDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender)
        var potentialDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender)

        val attackerHealthBefore = attacker.getHealth()
        val defenderHealthBefore = defender.getHealth()

        if (defender is MapUnitCombatant && defender.unit.isCivilian() && attacker.isMelee()) {
            BattleUnitCapture.captureCivilianUnit(attacker, defender)
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
            for (unique in attacker.unit.getTriggeredUniques(UniqueType.TriggerUponLosingHealth)
                    { it.params[0].toInt() <= defenderDamageDealt }) {
                val unit = if (unique.params[0] == Constants.targetUnit && defender is MapUnitCombatant)
                    defender.unit
                else attacker.unit
                UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = "due to losing [$defenderDamageDealt] HP")
            }

        if (defender is MapUnitCombatant)
            for (unique in defender.unit.getTriggeredUniques(UniqueType.TriggerUponLosingHealth)
                    { it.params[0].toInt() <= attackerDamageDealt }) {
                val unit = if (unique.params[0] == Constants.targetUnit && attacker is MapUnitCombatant)
                attacker.unit
                else defender.unit
                UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = "due to losing [$attackerDamageDealt] HP")
            }

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
        val civ = plunderingUnit.getCivInfo()
        val plunderedGoods = Stats()

        for (unique in plunderingUnit.unit.getMatchingUniques(UniqueType.DamageUnitsPlunder, checkCivInfoUniques = true)) {
            if (!plunderedUnit.matchesFilter(unique.params[1])) continue

            val percentage = unique.params[0].toFloat()
            val amount = percentage / 100f * damageDealt
            val resourceName = unique.params[2]
            val resource = plunderedUnit.getCivInfo().gameInfo.ruleset.getGameResource(resourceName)
                ?: continue

            if (resource is Stat) {
                plunderedGoods.add(resource, amount)
                continue // Notification and adding to the civ happens all at once for stats further down
            }

            val plunderedAmount = amount.roundToInt()
            civ.addGameResource(resource, plunderedAmount)
            val icon = if (resource is SubStat) resource.icon else "ResourceIcons/$resourceName"
            civ.addNotification(
                "Your [${plunderingUnit.getName()}] plundered [${plunderedAmount}] [${resourceName}] from [${plunderedUnit.getName()}]",
                plunderedUnit.getTile().position,
                NotificationCategory.War,
                plunderingUnit.getName(), NotificationIcon.War, icon,
                if (plunderedUnit is CityCombatant) NotificationIcon.City else plunderedUnit.getName()
            )
        }

        for ((key, value) in plunderedGoods) {
            val plunderedAmount = value.toInt()
            if (plunderedAmount == 0) continue
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

    internal fun postBattleNotifications(
        attacker: ICombatant,
        defender: ICombatant,
        attackedTile: Tile,
        attackerTile: Tile? = null,
        damageDealt: DamageDealt? = null
    ) {
        if (attacker.getCivInfo() == defender.getCivInfo()) return
        
        // If what happened was that a civilian unit was captured, that's dealt with in the captureCivilianUnit function
        val (battleActionIcon, battleActionString) = when {
            attacker !is CityCombatant && attacker.isDefeated() ->
                NotificationIcon.War to "was destroyed while attacking"
            !defender.isDefeated() ->
                NotificationIcon.War to "has attacked"
            defender.isCity() && attacker.isMelee() && attacker.getCivInfo().isBarbarian ->
                NotificationIcon.War to "has raided"
            defender.isCity() && attacker.isMelee() ->
                NotificationIcon.War to "has captured"
            else ->
                NotificationIcon.Death to "has destroyed"
        }
        val attackerString =
                if (attacker.isCity()) "Enemy city [" + attacker.getName() + "]"
                else "An enemy [" + attacker.getName() + "]"

        val defenderString =
                if (defender.isCity())
                    if (defender.isDefeated() && attacker.isRanged()) " the defence of [" + defender.getName() + "]"
                    else " [" + defender.getName() + "]"
                else " our [" + defender.getName() + "]"

        val attackerHurtString = if (damageDealt != null && damageDealt.defenderDealt != 0) " ([-${damageDealt.defenderDealt}] HP)" else ""
        val defenderHurtString = if (damageDealt != null) " ([-${damageDealt.attackerDealt}] HP)" else ""
        val notificationString = "[{$attackerString}{$attackerHurtString}] [$battleActionString] [{$defenderString}{$defenderHurtString}]"
        val attackerIcon = if (attacker is CityCombatant) NotificationIcon.City else attacker.getName()
        val defenderIcon = if (defender is CityCombatant) NotificationIcon.City else defender.getName()
        val locations = LocationAction(attackedTile.position, attackerTile?.position)
        defender.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, attackerIcon, battleActionIcon, defenderIcon)
    }

    private fun tryHealAfterKilling(attacker: ICombatant) {
        if (attacker !is MapUnitCombatant) return
        
        for (unique in attacker.unit.getMatchingUniques(UniqueType.HealsAfterKilling, checkCivInfoUniques = true)) {
            val amountToHeal = unique.params[0].toInt()
            attacker.unit.healBy(amountToHeal)
        }
    }


    private fun postBattleMoveToAttackedTile(attacker: ICombatant, defender: ICombatant, attackedTile: Tile) {
        if (!attacker.isMelee()) return
        if (!defender.isDefeated() && defender.getCivInfo() != attacker.getCivInfo()) return
        if (attacker !is MapUnitCombatant) return
        if (attacker.unit.cache.cannotMove) return
        // Example: If the unit we attacked made us lose movement points
        if (!attacker.unit.movement.canReachInCurrentTurn(attackedTile)) return
        // This is so that if we attack e.g. a barbarian in enemy territory that we can't enter, we won't enter it
        if (!attacker.unit.movement.canMoveTo(attackedTile)) return

        // Units that can move after attacking are not affected by zone of control if the
        // movement is caused by killing a unit. Effectively, this means that attack movements
        // are exempt from zone of control, since units that cannot move after attacking already
        // lose all remaining movement points anyway.
        attacker.unit.movement.moveToTile(attackedTile, considerZoneOfControl = false)
        attacker.unit.mostRecentMoveType = UnitMovementMemoryType.UnitAttacked
    }

    private fun postBattleAddXp(attacker: ICombatant, defender: ICombatant) {
        fun addXp(attackerXp: Int, defenderXp: Int){
            addXp(attacker, attackerXp, defender)
            addXp(defender, defenderXp, attacker)
        }
        
        if (attacker.isAirUnit()) addXp(4, 2)
        else if (attacker.isRanged()) { // ranged attack
            if (defender.isCity()) addXp(3,2)
            else addXp(2, 2)
        } else if (!defender.isCivilian()) // Captured units don't grant XP
            addXp(5, 4)
    }
    
    // XP!
    internal fun addXp(thisCombatant: ICombatant, amount: Int, otherCombatant: ICombatant) {
        if (thisCombatant !is MapUnitCombatant) return
        val civ = thisCombatant.getCivInfo()
        val otherIsBarbarian = otherCombatant.getCivInfo().isBarbarian
        val promotions = thisCombatant.unit.promotions
        val modConstants = civ.gameInfo.ruleset.modOptions.constants

        if (otherIsBarbarian && promotions.totalXpProduced() >= modConstants.maxXPfromBarbarians)
            return
        val unitCouldAlreadyPromote = promotions.canBePromoted()

        val gameContext = GameContext(civInfo = civ, ourCombatant = thisCombatant, theirCombatant = otherCombatant)

        val baseXP = amount + thisCombatant
            .getMatchingUniques(UniqueType.FlatXPGain, gameContext, true)
            .sumOf { it.params[0].toInt() }

        val xpBonus = thisCombatant
            .getMatchingUniques(UniqueType.PercentageXPGain, gameContext, true)
            .sumOf { it.params[0].toDouble() }
        val xpModifier = 1.0 + xpBonus / 100

        val xpGained = (baseXP * xpModifier).toInt()
        promotions.XP += xpGained

        if (!otherIsBarbarian && civ.isMajorCiv()) { // Can't get great generals from Barbarians
            var greatGeneralUnits = civ.gameInfo.ruleset.greatGeneralUnits
                .filter { it.hasUnique(UniqueType.GreatPersonFromCombat, gameContext) &&
                        // Check if the unit is allowed for the Civ, ignoring build constrants
                        it.getRejectionReasons(civ).none { reason ->
                            !reason.isConstructionRejection() &&
                                    // Allow Generals even if not allowed via tech
                                    !reason.techPolicyEraWonderRequirements() }
                }.asSequence()
            // For compatibility with older rulesets
            if (civ.gameInfo.ruleset.greatGeneralUnits.isEmpty() &&
                civ.gameInfo.ruleset.units["Great General"] != null)
                greatGeneralUnits += civ.gameInfo.ruleset.units["Great General"]!!

            for (unit in greatGeneralUnits) {
                val greatGeneralPointsBonus = thisCombatant
                    .getMatchingUniques(UniqueType.GreatPersonEarnedFaster, gameContext, true)
                    .filter { unit.matchesFilter(it.params[0], gameContext) }
                    .sumOf { it.params[1].toDouble() }
                val greatGeneralPointsModifier = 1.0 + greatGeneralPointsBonus / 100

                val greatGeneralPointsGained = (xpGained * greatGeneralPointsModifier).toInt()
                civ.greatPeople.greatGeneralPointsCounter[unit.name] += greatGeneralPointsGained
            }
        }

        if (!thisCombatant.isDefeated() && !unitCouldAlreadyPromote && promotions.canBePromoted()) {
            val pos = thisCombatant.getTile().position
            civ.addNotification("[${thisCombatant.unit.displayName()}] can be promoted!",
                listOf(MapUnitAction(pos), PromoteUnitAction(thisCombatant.getName(), pos)),
                NotificationCategory.Units, thisCombatant.unit.name)
        }
    }


    private fun reduceAttackerMovementPointsAndAttacks(attacker: ICombatant, defender: ICombatant, attackedTile: Tile) {
        if (attacker !is MapUnitCombatant) {
            if (attacker is CityCombatant) attacker.city.attackedThisTurn = true
            return
        }
        
        val unit = attacker.unit
        // If captured this civilian, doesn't count as attack
        // And we've used a movement already
        if (defender.isCivilian() && attacker.getTile() == defender.getTile()) return

        val gameContext = GameContext(attacker, defender, attackedTile, CombatAction.Attack)
        unit.attacksThisTurn += 1
        if (unit.hasUnique(UniqueType.CanMoveAfterAttacking, gameContext)
            || unit.maxAttacksPerTurn() > unit.attacksThisTurn) {
            // if it was a melee attack, and we won, then the unit ALREADY got movement points deducted,
            // for the movement to the enemy's tile!
            // and if it's an air unit, it only has 1 movement anyway, so...
            if (!attacker.unit.baseUnit.movesLikeAirUnits && !(attacker.isMelee() && defender.isDefeated()))
                unit.useMovementPoints(1f)
        } else unit.currentMovement = 0f
        
        if (unit.isFortified() || unit.isSleeping() || unit.isGuarding())
            attacker.unit.action = null // but not, for instance, if it's Set Up - then it should definitely keep the action!
    }

    private fun conquerCity(city: City, attacker: MapUnitCombatant) {
        val attackerCiv = attacker.getCivInfo()

        attackerCiv.addNotification("We have conquered the city of [${city.name}]!", city.location, NotificationCategory.War, NotificationIcon.War)

        city.hasJustBeenConquered = true
        city.getCenterTile().apply {
            if (militaryUnit != null) militaryUnit!!.destroy()
            if (civilianUnit != null) BattleUnitCapture.captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit!!), checkDefeat = false)
            for (airUnit in airUnits.toList()) airUnit.destroy()
        }

        val gameContext = GameContext(civInfo = attackerCiv, city=city, unit = attacker.unit, ourCombatant = attacker, attackedTile = city.getCenterTile())
        for (unique in attacker.getMatchingUniques(UniqueType.CaptureCityPlunder, gameContext, true)) {
            val resource = attacker.getCivInfo().gameInfo.ruleset.getGameResource(unique.params[2])
                ?: continue
            attackerCiv.addGameResource(
                resource,
                unique.params[0].toInt() * city.cityStats.currentCityStats[Stat.valueOf(unique.params[1])].toInt()
            )
        }

        if (attackerCiv.isBarbarian || attackerCiv.isOneCityChallenger()) {
            city.destroyCity(true)
            return
        }

        if (city.isOriginalCapital && city.foundingCiv == attackerCiv.civName) {
            // retaking old capital
            city.puppetCity(attackerCiv)
            //Although in Civ5 Venice is unable to re-annex their capital, that seems a bit silly. No check for May not annex cities here.
            city.annexCity()
        } else if (attackerCiv.isHuman() && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false) {
            // we're not taking our former capital
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.id))
        } else automateCityConquer(attackerCiv, city)

        if (attackerCiv.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Conquer a city")

        for (unique in attackerCiv.getTriggeredUniques(UniqueType.TriggerUponConqueringCity, gameContext)
                + attacker.unit.getTriggeredUniques(UniqueType.TriggerUponConqueringCity, gameContext))
            UniqueTriggerActivation.triggerUnique(unique, attacker.unit)
    }

    /** Handle decision-making after city conquest, namely whether the AI should liberate, puppet,
     * or raze a city */
    private fun automateCityConquer(civInfo: Civilization, city: City) {
        if (!city.hasDiplomaticMarriage()) {
            val foundingCiv = civInfo.gameInfo.getCivilization(city.foundingCiv)
            var valueAlliance = NextTurnAutomation.valueCityStateAlliance(civInfo, foundingCiv)
            if (civInfo.getHappiness() < 0)
                valueAlliance -= civInfo.getHappiness() // put extra weight on liberating if unhappy
            if (foundingCiv.isCityState && city.civ != civInfo && foundingCiv != civInfo
                && !civInfo.isAtWarWith(foundingCiv)
                && valueAlliance > 0) {
                city.liberateCity(civInfo)
                return
            }
        }

        city.puppetCity(civInfo)
        if ((city.population.population < 4 || civInfo.isCityState)
            && city.foundingCiv != civInfo.civName && city.canBeDestroyed(justCaptured = true)) {
            // raze if attacker is a city state
            if (!civInfo.hasUnique(UniqueType.MayNotAnnexCities)) city.annexCity()
            city.isBeingRazed = true
        }
    }
    
    @Readonly
    fun getMapCombatantOfTile(tile: Tile): ICombatant? {
        if (tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if (tile.militaryUnit != null) return MapUnitCombatant(tile.militaryUnit!!)
        if (tile.civilianUnit != null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    fun destroyIfDefeated(attackedCiv: Civilization, attacker: Civilization, notificationLocation: Vector2? = null) {
        if (attackedCiv.isDefeated()) {
            if (attackedCiv.isCityState)
                attackedCiv.cityStateFunctions.cityStateDestroyed(attacker)
            attackedCiv.destroy(notificationLocation)
            attacker.popupAlerts.add(PopupAlert(AlertType.Defeated, attackedCiv.civName))
        }
    }
    
    private fun hasWithdrawnFromMeelee(attacker: ICombatant, defender: ICombatant, attackedTile: Tile): Boolean {
        return (attacker is MapUnitCombatant && attacker.isMelee() && defender is MapUnitCombatant
                && defender.unit.hasUnique(UniqueType.WithdrawsBeforeMeleeCombat, gameContext = GameContext(
            civInfo = defender.getCivInfo(),
            ourCombatant = defender,
            theirCombatant = attacker,
            tile = attackedTile
        )) 
                && doWithdrawFromMeleeAbility(attacker, defender))
    }

    private fun doWithdrawFromMeleeAbility(attacker: MapUnitCombatant, defender: MapUnitCombatant): Boolean {
        if (defender.unit.isEmbarked()) return false
        if (defender.unit.cache.cannotMove) return false
        if (defender.unit.isEscorting()) return false // running away and leaving the escorted unit defeats the purpose of escorting
        if (defender.unit.isGuarding()) return false // guarding this post and will fight to the death!
        
        // Promotions have no effect as per what I could find in available documentation
        val fromTile = defender.getTile()
        val attackerTile = attacker.getTile()

        @Readonly
        fun canNotWithdrawTo(tile: Tile): Boolean { // if the tile is what the defender can't withdraw to, this fun will return true
           return !defender.unit.movement.canMoveTo(tile)
                   || defender.isLandUnit() && !tile.isLand // forbid retreat from land to sea - embarked already excluded
                   || tile.isCityCenter() && tile.getOwner() != defender.getCivInfo() // forbid retreat into the city which doesn't belong to the defender
        }

        val firstCandidateTiles = fromTile.neighbors.filterNot { it == attackerTile || it in attackerTile.neighbors }
                .filterNot { canNotWithdrawTo(it) }
        val secondCandidateTiles = fromTile.neighbors.filter { it in attackerTile.neighbors }
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
        reduceAttackerMovementPointsAndAttacks(attacker, defender, fromTile)

        val attackerName = attacker.getName()
        val defenderName = defender.getName()
        val notificationString = "[$defenderName] withdrew from a [$attackerName]"
        val locations = LocationAction(toTile.position, attacker.getTile().position)
        defender.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, defenderName, NotificationIcon.War, attackerName)
        attacker.getCivInfo().addNotification(notificationString, locations, NotificationCategory.War, defenderName, NotificationIcon.War, attackerName)
        return true
    }

    private fun doDestroyImprovementsAbility(attacker: MapUnitCombatant, attackedTile: Tile, defender: ICombatant) {
        if (attackedTile.improvement == null) return

        val conditionalState = GameContext(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, combatAction = CombatAction.Attack, attackedTile = attackedTile)
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

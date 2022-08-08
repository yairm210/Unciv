package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile
import com.unciv.models.UnitActionType
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.utils.debug
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
object Battle {

    /**
     * Moves [attacker] to [attackableTile], handles siege setup then attacks if still possible
     * (by calling [attack] or [NUKE]). Does _not_ play the attack sound!
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
         * to attackableTile.tileToAttackFrom, meaning that they take the civilian. This check stops
         * the melee unit from trying to capture their own unit if this happens */
        if (getMapCombatantOfTile(attackableTile.tileToAttack)!!.getCivInfo() == attacker.getCivInfo()) return false
        /** Alternatively, maybe we DID reach that tile, but it turned out to be a hill or something,
         * so we expended all of our movement points!
         */
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
    fun attackOrNuke(attacker: ICombatant, attackableTile: AttackableTile) {
        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isNuclearWeapon())
            NUKE(attacker, attackableTile.tileToAttack)
        else
            attack(attacker, getMapCombatantOfTile(attackableTile.tileToAttack)!!)
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        debug("%s %s attacked %s %s", attacker.getCivInfo().civName, attacker.getName(), defender.getCivInfo().civName, defender.getName())
        val attackedTile = defender.getTile()
        if (attacker is MapUnitCombatant) {
            attacker.unit.attacksSinceTurnStart.add(Vector2(attackedTile.position))
        } else {
            attacker.getCivInfo().attacksSinceTurnStart.add(CivilizationInfo.HistoricalAttackMemory(
                null,
                Vector2(attacker.getTile().position),
                Vector2(attackedTile.position)
            ))
        }

        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isAirUnit()) {
            tryInterceptAirAttack(attacker, attackedTile, defender.getCivInfo(), defender)
            if (attacker.isDefeated()) return
        }

        // Withdraw from melee ability
        if (attacker is MapUnitCombatant && attacker.isMelee() && defender is MapUnitCombatant) {
            val withdrawUniques = defender.unit.getMatchingUniques(UniqueType.MayWithdraw)
            val combinedProbabilityToStayPut = withdrawUniques.fold(100) { probabilityToStayPut, unique -> probabilityToStayPut * (100-unique.params[0].toInt()) / 100 }
            val baseWithdrawChance = 100 - combinedProbabilityToStayPut
            // If a mod allows multiple withdraw properties, they stack multiplicatively
            if (baseWithdrawChance != 0 && doWithdrawFromMeleeAbility(attacker, defender, baseWithdrawChance)) return
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
            if (attacker.unit.civInfo.isBarbarian()) {
                defender.takeDamage(-1) // Back to 2 HP
                val ransom = min(200, defender.city.civInfo.gold)
                defender.city.civInfo.addGold(-ransom)
                defender.city.civInfo.addNotification("Barbarians raided [${defender.city.name}] and stole [$ransom] Gold from your treasury!", defender.city.location, NotificationIcon.War)
                attacker.unit.destroy() // Remove the barbarian
            } else
                conquerCity(defender.city, attacker)
        }

        // Exploring units surviving an attack should "wake up"
        if (!defender.isDefeated() && defender is MapUnitCombatant && defender.unit.isExploring())
            defender.unit.action = null

        // Add culture when defeating a barbarian when Honor policy is adopted, gold from enemy killed when honor is complete
        // or any enemy military unit with Sacrificial captives unique (can be either attacker or defender!)
        if (defender.isDefeated() && defender is MapUnitCombatant && !defender.unit.isCivilian()) {
            tryEarnFromKilling(attacker, defender)
            tryHealAfterKilling(attacker)
        } else if (attacker.isDefeated() && attacker is MapUnitCombatant && !attacker.unit.isCivilian()) {
            tryEarnFromKilling(defender, attacker)
            tryHealAfterKilling(defender)
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
                it.isCityCenter() && it.getCity()!!.getLocalMatchingUniques(UniqueType.KillUnitPlunderNearCity, stateForConditionals).any()
            }?.getCity()
        if (cityWithReligion != null) {
            bonusUniques.addAll(cityWithReligion.getLocalMatchingUniques(UniqueType.KillUnitPlunderNearCity, stateForConditionals))
        }

        for (unique in bonusUniques) {
            if (!defeatedUnit.matchesCategory(unique.params[1])) continue

            val yieldPercent = unique.params[0].toFloat() / 100
            val defeatedUnitYieldSourceType = unique.params[2]
            val yieldTypeSourceAmount =
                if (defeatedUnitYieldSourceType == "Cost") unitCost else unitStr
            val yieldAmount = (yieldTypeSourceAmount * yieldPercent).toInt()

            // This should be unnecessary as we check this for uniques when reading them in
            try {
                val stat = Stat.valueOf(unique.params[3])
                civUnit.getCivInfo().addStat(stat, yieldAmount)
            } catch (ex: Exception) {
            } // parameter is not a stat
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

    private fun tryCaptureUnit(attacker: ICombatant, defender: ICombatant, attackedTile: TileInfo): Boolean {
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
            return Random().nextFloat() <= captureChance
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
            for (unique in attacker.getCivInfo()
                .getMatchingUniques(UniqueType.GainFromDefeatingUnit)) {
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
    private fun spawnCapturedUnit(unitName: String, attacker: ICombatant, tile: TileInfo): Boolean {
        val addedUnit = attacker.getCivInfo().placeUnitNearTile(tile.position, unitName) ?: return false
        addedUnit.currentMovement = 0f
        addedUnit.health = 50
        attacker.getCivInfo().addNotification("An enemy [${unitName}] has joined us!", addedUnit.getTile().position, unitName)

        val civilianUnit = tile.civilianUnit
        // placeUnitNearTile might not have spawned the unit in exactly this tile, in which case no capture would have happened on this tile. So we need to do that here.
        if (addedUnit.getTile() != tile && civilianUnit != null) {
            captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit))
        }
        return true
    }

    private data class DamageDealt(val attackerDealt: Int, val defenderDealt: Int) {}

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
                if (Random().nextInt(potentialDamageToDefender + potentialDamageToAttacker) < potentialDamageToDefender) {
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
                plunderingUnit.getName(), NotificationIcon.War, "StatIcons/${key.name}",
                if (plunderedUnit is CityCombatant) NotificationIcon.City else plunderedUnit.getName()
            )
        }
    }

    private fun postBattleNotifications(
        attacker: ICombatant,
        defender: ICombatant,
        attackedTile: TileInfo,
        attackerTile: TileInfo? = null,
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
            defender.getCivInfo().addNotification(notificationString, locations, attackerIcon, whatHappenedIcon, defenderIcon)
        }
    }

    private fun tryHealAfterKilling(attacker: ICombatant) {
        if (attacker is MapUnitCombatant)
            for (unique in attacker.unit.getMatchingUniques(UniqueType.HealsAfterKilling, checkCivInfoUniques = true)) {
                val amountToHeal = unique.params[0].toInt()
                attacker.unit.healBy(amountToHeal)
            }
    }


    private fun postBattleMoveToAttackedTile(attacker: ICombatant, defender: ICombatant, attackedTile: TileInfo) {
        if (!attacker.isMelee()) return
        if (!defender.isDefeated() && defender.getCivInfo() != attacker.getCivInfo()) return

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
        var baseXP = amount
        if (thisCombatant !is MapUnitCombatant) return
        val modConstants = thisCombatant.unit.civInfo.gameInfo.ruleSet.modOptions.constants
        if (thisCombatant.unit.promotions.totalXpProduced() >= modConstants.maxXPfromBarbarians
            && otherCombatant.getCivInfo().isBarbarian()
        ) {
            return
        }

        val stateForConditionals = StateForConditionals(civInfo = thisCombatant.getCivInfo(), ourCombatant = thisCombatant, theirCombatant = otherCombatant)

        for (unique in thisCombatant.getMatchingUniques(UniqueType.FlatXPGain, stateForConditionals, true))
            baseXP += unique.params[0].toInt()

        var xpModifier = 1f

        for (unique in thisCombatant.getMatchingUniques(UniqueType.PercentageXPGain, stateForConditionals, true))
            xpModifier += unique.params[0].toFloat() / 100

        val xpGained = (baseXP * xpModifier).toInt()
        thisCombatant.unit.promotions.XP += xpGained


        if (thisCombatant.getCivInfo().isMajorCiv() && !otherCombatant.getCivInfo().isBarbarian()) { // Can't get great generals from Barbarians
            var greatGeneralPointsModifier = 1f
            for (unique in thisCombatant.getMatchingUniques(UniqueType.GreatPersonEarnedFaster, stateForConditionals, true)) {
                val unitName = unique.params[0]
                // From the unique we know this unit exists
                val unit = thisCombatant.getCivInfo().gameInfo.ruleSet.units[unitName]!!
                if (unit.uniques.contains("Great Person - [War]"))
                    greatGeneralPointsModifier += unique.params[1].toFloat() / 100
            }

            val greatGeneralPointsGained = (xpGained * greatGeneralPointsModifier).toInt()
            thisCombatant.getCivInfo().greatPeople.greatGeneralPoints += greatGeneralPointsGained
        }
    }

    private fun conquerCity(city: CityInfo, attacker: MapUnitCombatant) {
        val attackerCiv = attacker.getCivInfo()


        attackerCiv.addNotification("We have conquered the city of [${city.name}]!", city.location, NotificationIcon.War)

        city.hasJustBeenConquered = true
        city.getCenterTile().apply {
            if (militaryUnit != null) militaryUnit!!.destroy()
            if (civilianUnit != null) captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit!!), checkDefeat = false)
            for (airUnit in airUnits.toList()) airUnit.destroy()
        }

        val stateForConditionals = StateForConditionals(civInfo = attackerCiv, unit = attacker.unit, ourCombatant = attacker, attackedTile = city.getCenterTile())
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
            city.annexCity()
        } else if (attackerCiv.isPlayerCivilization()) {
            // we're not taking our former capital
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.id))
        } else {
            NextTurnAutomation.onConquerCity(attackerCiv, city)
        }

        if (attackerCiv.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Conquer a city")
    }

    fun getMapCombatantOfTile(tile: TileInfo): ICombatant? {
        if (tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if (tile.militaryUnit != null) return MapUnitCombatant(tile.militaryUnit!!)
        if (tile.civilianUnit != null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    /**
     * @throws IllegalArgumentException if the [attacker] and [defender] belong to the same civ.
     */
    fun captureCivilianUnit(attacker: ICombatant, defender: MapUnitCombatant, checkDefeat: Boolean = true) {
        if (attacker.getCivInfo() == defender.getCivInfo()) {
            throw IllegalArgumentException("Can't capture our own unit!")
        }

        // need to save this because if the unit is captured its owner wil be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = defender.unit
        // Stop current action
        capturedUnit.action = null

        val capturedUnitTile = capturedUnit.getTile()
        val originalOwner = if (capturedUnit.originalOwner != null)
            capturedUnit.civInfo.gameInfo.getCivilization(capturedUnit.originalOwner!!)
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
                attacker.getCivInfo().popupAlerts.add(PopupAlert(AlertType.RecapturedCivilian, capturedUnitTile.position.toString()))
            }

            // Captured settlers are converted to workers unless captured by barbarians (so they can be returned later).
            capturedUnit.hasUnique(UniqueType.FoundCity) && !attacker.getCivInfo().isBarbarian() -> {
                capturedUnit.destroy()
                // This is so that future checks which check if a unit has been captured are caught give the right answer
                //  For example, in postBattleMoveToAttackedTile
                capturedUnit.civInfo = attacker.getCivInfo()
                attacker.getCivInfo().placeUnitNearTile(capturedUnitTile.position, Constants.worker)
            }
            else -> capturedUnit.capturedBy(attacker.getCivInfo())
        }

        if (!wasDestroyedInstead)
            defenderCiv.addNotification("An enemy [" + attacker.getName() + "] has captured our [" + defender.getName() + "]",
                defender.getTile().position, attacker.getName(), NotificationIcon.War, defender.getName())
        else
            defenderCiv.addNotification("An enemy [" + attacker.getName() + "] has destroyed our [" + defender.getName() + "]",
                defender.getTile().position, attacker.getName(), NotificationIcon.War, defender.getName())

        if (checkDefeat)
            destroyIfDefeated(defenderCiv, attacker.getCivInfo())
        capturedUnit.updateVisibleTiles()
    }

    fun destroyIfDefeated(attackedCiv: CivilizationInfo, attacker: CivilizationInfo) {
        if (attackedCiv.isDefeated()) {
            if (attackedCiv.isCityState())
                attackedCiv.cityStateFunctions.cityStateDestroyed(attacker)
            attackedCiv.destroy()
            attacker.popupAlerts.add(PopupAlert(AlertType.Defeated, attackedCiv.civName))
        }
    }

    fun mayUseNuke(nuke: MapUnitCombatant, targetTile: TileInfo): Boolean {
        val blastRadius =
            if (!nuke.hasUnique(UniqueType.BlastRadius)) 2
            // Don't check conditionals as these are not supported
            else nuke.unit.getMatchingUniques(UniqueType.BlastRadius).first().params[0].toInt()

        var canNuke = true
        val attackerCiv = nuke.getCivInfo()
        for (tile in targetTile.getTilesInDistance(blastRadius)) {
            val defendingTileCiv = tile.getCity()?.civInfo
            if (defendingTileCiv != null && attackerCiv.knows(defendingTileCiv)) {
                canNuke = canNuke && attackerCiv.getDiplomacyManager(defendingTileCiv).canAttack()
            }

            val defender = getMapCombatantOfTile(tile) ?: continue
            val defendingUnitCiv = defender.getCivInfo()
            if (attackerCiv.knows(defendingUnitCiv)) {
                canNuke = canNuke && attackerCiv.getDiplomacyManager(defendingUnitCiv).canAttack()
            }
        }
        return canNuke
    }

    @Suppress("FunctionName")   // Yes we want this name to stand out
    fun NUKE(attacker: MapUnitCombatant, targetTile: TileInfo) {
        val attackingCiv = attacker.getCivInfo()
        fun tryDeclareWar(civSuffered: CivilizationInfo) {
            if (civSuffered != attackingCiv
                && civSuffered.knows(attackingCiv)
                && civSuffered.getDiplomacyManager(attackingCiv).diplomaticStatus != DiplomaticStatus.War
            ) {
                attackingCiv.getDiplomacyManager(civSuffered).declareWar()
                attackingCiv.addNotification("After being hit by our [${attacker.getName()}], [${civSuffered}] has declared war on us!", targetTile.position, NotificationIcon.War)
            }
        }

        val strength = attacker.unit.getMatchingUniques(UniqueType.NuclearWeapon)
            .firstOrNull()?.params?.get(0)?.toInt() ?: return

        val blastRadius = attacker.unit.getMatchingUniques(UniqueType.BlastRadius)
            .firstOrNull()?.params?.get(0)?.toInt() ?: 2

        // Calculate the tiles that are hit
        val hitTiles = targetTile.getTilesInDistance(blastRadius)

        // Declare war on the owners of all hit tiles
        for (hitCiv in hitTiles.mapNotNull { it.getOwner() }.distinct()) {
            hitCiv.addNotification("A(n) [${attacker.getName()}] exploded in our territory!", targetTile.position, NotificationIcon.War)
            tryDeclareWar(hitCiv)
        }

        // Declare war on all potentially hit units. They'll try to intercept the nuke before it drops
        for(civWhoseUnitWasAttacked in hitTiles
            .flatMap { it.getUnits() }
            .map { it.civInfo }.distinct()
            .filter{it != attackingCiv}) {
                tryDeclareWar(civWhoseUnitWasAttacked)
                if (attacker.unit.baseUnit.isAirUnit() && !attacker.isDefeated()) {
                    tryInterceptAirAttack(attacker, targetTile, civWhoseUnitWasAttacked, null)
            }
        }
        if (attacker.isDefeated()) return

        attacker.unit.attacksSinceTurnStart.add(Vector2(targetTile.position))

        // Destroy units on the target tile
        // Needs the toList() because if we're destroying the units, they're no longer part of the sequence
        for (defender in targetTile.getUnits().filter { it != attacker.unit }.toList()) {
            defender.destroy()
            postBattleNotifications(attacker, MapUnitCombatant(defender), defender.getTile())
            destroyIfDefeated(defender.civInfo, attacker.getCivInfo())
        }

        for (tile in hitTiles) {
            // Handle complicated effects
            doNukeExplosionForTile(attacker, tile, strength)
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

    private fun doNukeExplosionForTile(attacker: MapUnitCombatant, tile: TileInfo, nukeStrength: Int) {
        // https://forums.civfanatics.com/resources/unit-guide-modern-future-units-g-k.25628/
        // https://www.carlsguides.com/strategy/civilization5/units/aircraft-nukes.ph
        // Testing done by Ravignir
        // original source code: GenerateNuclearExplosionDamage(), ApplyNuclearExplosionDamage()

        var damageModifierFromMissingResource = 1f
        val civResources = attacker.getCivInfo().getCivResourcesByName()
        for (resource in attacker.unit.baseUnit.getResourceRequirements().keys) {
            if (civResources[resource]!! < 0 && !attacker.getCivInfo().isBarbarian())
                damageModifierFromMissingResource *= 0.5f // I could not find a source for this number, but this felt about right
        }

        // Damage city and reduce its population
        val city = tile.getCity()
        if (city != null && tile.position == city.location) {
            doNukeExplosionDamageToCity(city, nukeStrength, damageModifierFromMissingResource)
            postBattleNotifications(attacker, CityCombatant(city), city.getCenterTile())
            destroyIfDefeated(city.civInfo, attacker.getCivInfo())
        }

        // Damage and/or destroy units on the tile
        for (unit in tile.getUnits().toList()) { // toList so if it's destroyed there's no concurrent modification
            val defender = MapUnitCombatant(unit)
            if (defender.unit.isCivilian() || nukeStrength >= 2) {
                unit.destroy()
            } else if (nukeStrength == 1) {
                defender.takeDamage(((40 + Random().nextInt(60)) * damageModifierFromMissingResource).toInt())
            } else if (nukeStrength == 0) {
                defender.takeDamage(((20 + Random().nextInt(30)) * damageModifierFromMissingResource).toInt())
            }
            postBattleNotifications(attacker, defender, defender.getTile())
            destroyIfDefeated(defender.getCivInfo(), attacker.getCivInfo())
        }

        // Pillage improvements, remove roads, add fallout
        if (tile.improvement != null && !tile.getTileImprovement()!!.hasUnique(UniqueType.Irremovable)) {
            if (tile.getTileImprovement()!!.hasUnique(UniqueType.Unpillagable)) {
                tile.improvement = null
            } else {
                tile.setPillaged()
            }
        }
        tile.roadStatus = RoadStatus.None
        if (tile.isLand && !tile.isImpassible() && !tile.isCityCenter()) {
            if (tile.terrainHasUnique(UniqueType.DestroyableByNukesChance)) {
                for (terrainFeature in tile.terrainFeatureObjects) {
                    for (unique in terrainFeature.getMatchingUniques(UniqueType.DestroyableByNukesChance)) {
                        if (Random().nextFloat() >= unique.params[0].toFloat() / 100f) continue
                        tile.removeTerrainFeature(terrainFeature.name)
                        if (!tile.terrainFeatures.contains("Fallout"))
                            tile.addTerrainFeature("Fallout")
                    }
                }
            } else if (Random().nextFloat() < 0.5f && !tile.terrainFeatures.contains("Fallout")) {
                tile.addTerrainFeature("Fallout")
            }
        }
    }

    private fun doNukeExplosionDamageToCity(targetedCity: CityInfo, nukeStrength: Int, damageModifierFromMissingResource: Float) {
        if (nukeStrength > 1 && targetedCity.population.population < 5 && targetedCity.canBeDestroyed(true)) {
            targetedCity.destroyCity()
            return
        }
        val cityCombatant = CityCombatant(targetedCity)
        cityCombatant.takeDamage((cityCombatant.getHealth() * 0.5f * damageModifierFromMissingResource).toInt())

        var populationLoss = targetedCity.population.population *
            when (nukeStrength) {
                0 -> 0f
                1 -> (30 + Random().nextInt(40)) / 100f
                2 -> (60 + Random().nextInt(20)) / 100f
                else -> 1f
            }
        for (unique in targetedCity.getMatchingUniques(UniqueType.PopulationLossFromNukes)) {
            if (!targetedCity.matchesFilter(unique.params[1])) continue
            populationLoss *= unique.params[0].toPercent()
        }
        targetedCity.population.addPopulation(-populationLoss.toInt())
        if (targetedCity.population.population < 1) targetedCity.population.setPopulation(1)
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
    fun airSweep(attacker: MapUnitCombatant, attackedTile: TileInfo) {
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
            potentialInterceptors += interceptingCiv.getCivUnits()
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
            val interceptingCiv = interceptor.civInfo
            val interceptorName = interceptor.name
            // pairs of LocationAction for Notification
            val locations = LocationAction(
                interceptor.currentTile.position,
                attacker.unit.currentTile.position
            )
            val locationsAttackerUnknown =
                    LocationAction(interceptor.currentTile.position, attackedTile.position)
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
                    attackerText, locations,
                    attackerName, NotificationIcon.War, interceptorName
                )
                interceptingCiv.addNotification(
                    interceptorText, locations,
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

            if (attacker.isDefeated()) {
                if (interceptor.getTile() in attacker.getCivInfo().viewableTiles) {
                    val attackerText =
                            "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was destroyed by an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"
                    attacker.getCivInfo().addNotification(
                        attackerText, locations,
                        attackerName, NotificationIcon.War, interceptorName
                    )
                } else {
                    val attackerText =
                            "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was destroyed by an unknown interceptor"
                    attacker.getCivInfo().addNotification(
                        attackerText, locationsInterceptorUnknown,
                        attackerName, NotificationIcon.War, NotificationIcon.Question
                    )
                }
                val interceptorText =
                        "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and destroyed an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
                interceptingCiv.addNotification(
                    interceptorText, locations,
                    interceptorName, NotificationIcon.War, attackerName
                )
            } else if (MapUnitCombatant(interceptor).isDefeated()) {
                val attackerText =
                        "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) destroyed an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"
                attacker.getCivInfo().addNotification(
                    attackerText, locations,
                    attackerName, NotificationIcon.War, interceptorName
                )
                if (attacker.getTile() in interceptingCiv.viewableTiles) {
                    val interceptorText =
                            "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
                    interceptingCiv.addNotification(
                        interceptorText, locations,
                        interceptorName, NotificationIcon.War, attackerName
                    )
                } else {
                    val interceptorText =
                            "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and was destroyed by an unknown enemy"
                    interceptingCiv.addNotification(
                        interceptorText, locationsAttackerUnknown,
                        interceptorName, NotificationIcon.War, NotificationIcon.Question
                    )
                }
            } else {
                val attackerText =
                        "Our [$attackerName] ([-${damageDealt.defenderDealt}] HP) was attacked by an intercepting [$interceptorName] ([-${damageDealt.attackerDealt}] HP)"
                val interceptorText =
                        "Our [$interceptorName] ([-${damageDealt.attackerDealt}] HP) intercepted and attacked an enemy [$attackerName] ([-${damageDealt.defenderDealt}] HP)"
                attacker.getCivInfo().addNotification(
                    attackerText, locations,
                    attackerName, NotificationIcon.War, interceptorName
                )
                interceptingCiv.addNotification(
                    interceptorText, locations,
                    interceptorName, NotificationIcon.War, attackerName
                )
            }
            attacker.unit.action = null
            return
        }

        // No Interceptions available
        val attackerText = "Nothing tried to intercept our [$attackerName]"
        attacker.getCivInfo().addNotification(attackerText, attackerName)
        attacker.unit.action = null
    }

    private fun tryInterceptAirAttack(attacker: MapUnitCombatant, attackedTile: TileInfo, interceptingCiv: CivilizationInfo, defender: ICombatant?) {
        if (attacker.unit.hasUnique(UniqueType.CannotBeIntercepted, StateForConditionals(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, attackedTile = attackedTile)))
            return
        // Pick highest chance interceptor
        for (interceptor in interceptingCiv.getCivUnits()
            .filter { it.canIntercept(attackedTile) }
            .sortedByDescending { it.interceptChance() }
        ) {
            // Can't intercept if we have a unique preventing it
            val conditionalState = StateForConditionals(interceptingCiv, ourCombatant = MapUnitCombatant(interceptor), theirCombatant = attacker, combatAction = CombatAction.Intercept, attackedTile = attackedTile)
            if (interceptor.getMatchingUniques(UniqueType.CannotInterceptUnits, conditionalState)
                .any { attacker.matchesCategory(it.params[0]) }
            ) continue

            // Defender can't intercept either
            if (defender != null && defender is MapUnitCombatant && interceptor == defender.unit) continue
            interceptor.attacksThisTurn++  // even if you miss, you took the shot
            // Does Intercept happen? If not, exit
            if (Random().nextFloat() > interceptor.interceptChance() / 100f) return

            var damage = BattleDamage.calculateDamageToDefender(
                MapUnitCombatant(interceptor),
                attacker
            )

            var damageFactor = 1f + interceptor.interceptDamagePercentBonus().toFloat() / 100f
            damageFactor *= attacker.unit.receivedInterceptDamageFactor()

            damage = (damage.toFloat() * damageFactor).toInt()

            attacker.takeDamage(damage)
            if (damage > 0)
                addXp(MapUnitCombatant(interceptor), 2, attacker)

            val attackerName = attacker.getName()
            val interceptorName = interceptor.name
            val locations = LocationAction(interceptor.currentTile.position, attacker.unit.currentTile.position)
            if (attacker.isDefeated()) {
                if (interceptor.getTile() in attacker.getCivInfo().viewableTiles) {
                    val attackerText =
                            "Our [$attackerName] ([-$damage] HP) was destroyed by an intercepting [$interceptorName] ([-0] HP)"
                    attacker.getCivInfo().addNotification(
                        attackerText, interceptor.currentTile.position,
                        attackerName, NotificationIcon.War, interceptorName
                    )
                } else {
                    val attackerText =
                            "Our [$attackerName] ([-$damage] HP) was destroyed by an unknown interceptor"
                    attacker.getCivInfo().addNotification(
                        attackerText, attackedTile.position,
                        attackerName, NotificationIcon.War, interceptorName
                    )
                }
            } else {
                val attackerText =
                        "Our [$attackerName] ([-$damage] HP) was attacked by an intercepting [$interceptorName] ([-0] HP)"
                attacker.getCivInfo().addNotification(
                    attackerText, interceptor.currentTile.position,
                    attackerName, NotificationIcon.War, interceptorName
                )
            }

            val interceptorText = if (attacker.isDefeated())
                "Our [$interceptorName] ([-0] HP) intercepted and destroyed an enemy [$attackerName] ([-$damage] HP)"
            else "Our [$interceptorName] ([-0] HP) intercepted and attacked an enemy [$attackerName] ([-$damage] HP)"
            interceptingCiv.addNotification(interceptorText, locations,
                    interceptorName, NotificationIcon.War, attackerName)
            return
        }
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
        // Promotions have no effect as per what I could find in available documentation
        val attackBaseUnit = attacker.unit.baseUnit
        val defendBaseUnit = defender.unit.baseUnit
        val fromTile = defender.getTile()
        val attTile = attacker.getTile()
        fun canNotWithdrawTo(tile: TileInfo): Boolean { // if the tile is what the defender can't withdraw to, this fun will return true
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
        if (Random().nextInt(100) > percentChance) return false
        val firstCandidateTiles = fromTile.neighbors.filterNot { it == attTile || it in attTile.neighbors }
                .filterNot { canNotWithdrawTo(it) }
        val secondCandidateTiles = fromTile.neighbors.filter { it in attTile.neighbors }
                .filterNot { canNotWithdrawTo(it) }
        val toTile: TileInfo = when {
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
        defender.getCivInfo().addNotification(notificationString, locations, defendingUnit, NotificationIcon.War, attackingUnit)
        attacker.getCivInfo().addNotification(notificationString, locations, defendingUnit, NotificationIcon.War, attackingUnit)
        return true
    }

    private fun doDestroyImprovementsAbility(attacker: MapUnitCombatant, attackedTile: TileInfo, defender: ICombatant) {
        val conditionalState = StateForConditionals(attacker.getCivInfo(), ourCombatant = attacker, theirCombatant = defender, combatAction = CombatAction.Attack, attackedTile = attackedTile)
        if (attackedTile.improvement != Constants.barbarianEncampment
            && attackedTile.getTileImprovement()?.isAncientRuinsEquivalent() != true
            && attacker.hasUnique(UniqueType.DestroysImprovementUponAttack, conditionalState)
        ) {
            attackedTile.improvement = null
        }
    }
}

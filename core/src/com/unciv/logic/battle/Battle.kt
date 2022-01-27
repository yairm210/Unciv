package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
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
import com.unciv.ui.utils.toPercent
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
object Battle {

    fun moveAndAttack(attacker: ICombatant, attackableTile: AttackableTile) {
        if (attacker is MapUnitCombatant) {
            attacker.unit.movement.moveToTile(attackableTile.tileToAttackFrom)
            /**
             * When calculating movement distance, we assume that a hidden tile is 1 movement point,
             * which can lead to EXCEEDINGLY RARE edge cases where you think
             * that you can attack a tile by passing through a HIDDEN TILE,
             * but the hidden tile is actually IMPASSIBLE so you stop halfway!
             */
            if (attacker.getTile() != attackableTile.tileToAttackFrom) return
            /** Alternatively, maybe we DID reach that tile, but it turned out to be a hill or something,
             * so we expended all of our movement points!
             */
            if (attacker.unit.currentMovement == 0f)
                return
            if (attacker.hasUnique(UniqueType.MustSetUp) && !attacker.unit.isSetUpForSiege()) {
                attacker.unit.action = UnitActionType.SetUp.value
                attacker.unit.useMovementPoints(1f)
            }
        }

        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isNuclearWeapon())
            return NUKE(attacker, attackableTile.tileToAttack)
        attack(attacker, getMapCombatantOfTile(attackableTile.tileToAttack)!!)
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        if (UncivGame.Current.alertBattle) {
            println(attacker.getCivInfo().civName + " " + attacker.getName() + " attacked " +
                    defender.getCivInfo().civName + " " + defender.getName())
        }
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
            tryInterceptAirAttack(attacker, attackedTile, defender.getCivInfo())
            if (attacker.isDefeated()) return
        }

        // Withdraw from melee ability
        if (attacker is MapUnitCombatant && attacker.isMelee() && defender is MapUnitCombatant) {
            val withdraw = defender.unit.getMatchingUniques(UniqueType.MayWithdraw)
                .maxByOrNull{ it.params[0] }  // If a mod allows multiple withdraw properties, ensure the best is used
            if (withdraw != null && doWithdrawFromMeleeAbility(attacker, defender, withdraw)) return
        }

        val isAlreadyDefeatedCity = defender is CityCombatant && defender.isDefeated()

        takeDamage(attacker, defender)

        // check if unit is captured by the attacker (prize ships unique)
        // As ravignir clarified in issue #4374, this only works for aggressor
        val captureMilitaryUnitSuccess = defender is MapUnitCombatant && attacker is MapUnitCombatant
                && defender.isDefeated() && !defender.unit.isCivilian()
                && tryCaptureUnit(attacker, defender)

        if (!captureMilitaryUnitSuccess) // capture creates a new unit, but `defender` still is the original, so this function would still show a kill message
            postBattleNotifications(attacker, defender, attackedTile, attacker.getTile())

        postBattleNationUniques(defender, attackedTile, attacker)

        // This needs to come BEFORE the move-to-tile, because if we haven't conquered it we can't move there =)
        if (defender.isDefeated() && defender is CityCombatant && attacker is MapUnitCombatant
                && attacker.isMelee() && !attacker.unit.hasUnique("Unable to capture cities")) {
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

            try {
                val stat = Stat.valueOf(unique.params[3])
                civUnit.getCivInfo().addStat(stat, yieldAmount)
            } catch (ex: Exception) {
            } // parameter is not a stat
        }

        // CS friendship from killing barbarians
        if (defeatedUnit.matchesCategory("Barbarian") && defeatedUnit.matchesCategory("Military") && civUnit.getCivInfo().isMajorCiv()) {
            for (cityState in UncivGame.Current.gameInfo.getAliveCityStates()) {
                if (civUnit.getCivInfo().knows(cityState) && defeatedUnit.unit.threatensCiv(cityState)) {
                    cityState.cityStateFunctions.threateningBarbarianKilledBy(civUnit.getCivInfo())
                }
            }
        }

        // CS war with major pseudo-quest
        for (cityState in UncivGame.Current.gameInfo.getAliveCityStates()) {
            cityState.questManager.militaryUnitKilledBy(civUnit.getCivInfo(), defeatedUnit.getCivInfo())
        }
    }

    private fun tryCaptureUnit(attacker: MapUnitCombatant, defender: MapUnitCombatant): Boolean {
        // https://forums.civfanatics.com/threads/prize-ships-for-land-units.650196/
        // https://civilization.fandom.com/wiki/Module:Data/Civ5/GK/Defines

        if (attacker.unit.getMatchingUniques(UniqueType.KillUnitCapture)
            .none { defender.matchesCategory(it.params[0]) }
        ) return false

        val captureChance = min(0.8f, 0.1f + attacker.getAttackingStrength().toFloat() / defender.getDefendingStrength().toFloat() * 0.4f)
        if (Random().nextFloat() > captureChance) return false

        // This is called after takeDamage and so the defeated defender is already destroyed and
        // thus removed from the tile - but MapUnit.destroy() will not clear the unit's currentTile.
        // Therefore placeUnitNearTile _will_ place the new unit exactly where the defender was
        val defenderName = defender.getName()
        val newUnit = attacker.getCivInfo().placeUnitNearTile(defender.getTile().position, defenderName)
            ?: return false  // silently fail

        attacker.getCivInfo().addNotification(
            "Your [${attacker.getName()}] captured an enemy [$defenderName]",
            newUnit.getTile().position, attacker.getName(), NotificationIcon.War, defenderName )

        newUnit.currentMovement = 0f
        newUnit.health = 50
        return true
    }

    private fun takeDamage(attacker: ICombatant, defender: ICombatant) {
        var potentialDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, attacker.getTile(), defender)
        var potentialDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, attacker.getTile(), defender)

        val defenderHealthBefore = defender.getHealth()

        if (defender is MapUnitCombatant && defender.unit.isCivilian() && attacker.isMelee()) {
            captureCivilianUnit(attacker, defender)
        } else if (attacker.isRanged()) {
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

        plunderFromDamage(attacker, defender, defenderHealthBefore - defender.getHealth())
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
        attackerTile: TileInfo? = null
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
            val notificationString = attackerString + whatHappenedString + defenderString
            val attackerIcon = if (attacker is CityCombatant) NotificationIcon.City else attacker.getName()
            val defenderIcon = if (defender is CityCombatant) NotificationIcon.City else defender.getName()
            val locations = LocationAction (
                if (attackerTile != null && attackerTile.position != attackedTile.position)
                        listOf(attackedTile.position, attackerTile.position)
                else listOf(attackedTile.position)
            )
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

    private fun postBattleNationUniques(defender: ICombatant, attackedTile: TileInfo, attacker: ICombatant) {

        // Barbarians reduce spawn countdown after their camp was attacked "kicking the hornet's nest"
        if (defender.getCivInfo().isBarbarian() && attackedTile.improvement == Constants.barbarianEncampment) {
            defender.getCivInfo().gameInfo.barbarians.campAttacked(attackedTile.position)

            // German unique - needs to be checked before we try to move to the enemy tile, since the encampment disappears after we move in
            if (defender.isDefeated()
                    && attacker.getCivInfo().hasUnique("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment")
                    && Random().nextDouble() < 0.67) {
                attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
                attacker.getCivInfo().addGold(25)
                attacker.getCivInfo().addNotification("A barbarian [${defender.getName()}] has joined us!", attackedTile.position, defender.getName())
            }
        }

        // Similarly, Ottoman unique
        if (attacker.getCivInfo().hasUnique("50% chance of capturing defeated Barbarian naval units and earning 25 Gold")
                && defender.isDefeated()
                && defender is MapUnitCombatant
                && defender.unit.baseUnit.isWaterUnit()
                && defender.getCivInfo().isBarbarian()
                && attacker.isMelee()
                && attacker is MapUnitCombatant
                && attacker.unit.baseUnit.isWaterUnit()
                && Random().nextDouble() > 0.5) {
            attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
            attacker.getCivInfo().addGold(25)
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
        if (!attacker.isMelee()) { // ranged attack
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
        // Deprecated since 3.18.12
            for (unique in thisCombatant.getCivInfo().getMatchingUniques(UniqueType.BonusXPGainForUnits, stateForConditionals)) {
                if (thisCombatant.unit.matchesFilter(unique.params[0]))
                    xpModifier += unique.params[1].toFloat() / 100
            }
            for (unique in thisCombatant.getMatchingUniques(UniqueType.BonuxXPGain, stateForConditionals, true))
                xpModifier += unique.params[0].toFloat() / 100
        //
        
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

        if (attackerCiv.isPlayerCivilization()) {
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.id))
            UncivGame.Current.settings.addCompletedTutorialTask("Conquer a city")
        } else {
            city.puppetCity(attackerCiv)
            if (city.population.population < 4 && city.canBeDestroyed(justCaptured = true)) {
                city.annexCity()
                city.isBeingRazed = true
            }
        }
    }

    fun getMapCombatantOfTile(tile: TileInfo): ICombatant? {
        if (tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if (tile.militaryUnit != null) return MapUnitCombatant(tile.militaryUnit!!)
        if (tile.civilianUnit != null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    fun captureCivilianUnit(attacker: ICombatant, defender: MapUnitCombatant, checkDefeat: Boolean = true) {
        // need to save this because if the unit is captured its owner wil be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = defender.unit
        capturedUnit.civInfo.addNotification("An enemy [" + attacker.getName() + "] has captured our [" + defender.getName() + "]",
                defender.getTile().position, attacker.getName(), NotificationIcon.War, defender.getName())

        val capturedUnitTile = capturedUnit.getTile()
        val originalOwner = if (capturedUnit.originalOwner != null)
            capturedUnit.civInfo.gameInfo.getCivilization(capturedUnit.originalOwner!!)
            else null


        when {
            // Uncapturable units are destroyed
            defender.unit.hasUnique(UniqueType.Uncapturable) -> {
                capturedUnit.destroy()
            }
            // City states can never capture settlers at all
            capturedUnit.hasUnique(UniqueType.FoundCity) && attacker.getCivInfo().isCityState() -> {
                capturedUnit.destroy()
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

        val blastRadius =
            if (!attacker.hasUnique(UniqueType.BlastRadius)) 2
            // Don't check conditionals as there are not supported
            else attacker.unit.getMatchingUniques(UniqueType.BlastRadius).first().params[0].toInt()

        val strength = when {
            (attacker.unit.hasUnique("Nuclear weapon of Strength []")) ->
                attacker.unit.getMatchingUniques("Nuclear weapon of Strength []").first().params[0].toInt()
            else -> return
        }

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
                    tryInterceptAirAttack(attacker, targetTile, civWhoseUnitWasAttacked)
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
            doNukeExplosion(attacker, tile, strength)
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
    
    private fun doNukeExplosion(attacker: MapUnitCombatant, tile: TileInfo, nukeStrength: Int) {
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
        if (tile.improvement != null && !tile.getTileImprovement()!!.hasUnique(UniqueType.Indestructible)) {
            tile.turnsToImprovement = 2 
            tile.improvementInProgress = tile.improvement
            tile.improvement = null
        }
        tile.roadStatus = RoadStatus.None
        if (tile.isLand && !tile.isImpassible() && !tile.terrainFeatures.contains("Fallout")) {
            if (tile.terrainFeatures.any { attacker.getCivInfo().gameInfo.ruleSet.terrains[it]!!.hasUnique(UniqueType.ResistsNukes) }) {
                if (Random().nextFloat() < 0.25f) {
                    tile.terrainFeatures.removeAll { attacker.getCivInfo().gameInfo.ruleSet.terrains[it]!!.hasUnique(UniqueType.DestroyableByNukes) }
                    tile.terrainFeatures.add("Fallout")
                }
            } else if (Random().nextFloat() < 0.5f) {
                tile.terrainFeatures.removeAll { attacker.getCivInfo().gameInfo.ruleSet.terrains[it]!!.hasUnique(UniqueType.DestroyableByNukes) }
                tile.terrainFeatures.add("Fallout")
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
        // Deprecated since 3.16.11
            for (unique in targetedCity.getLocalMatchingUniques("Population loss from nuclear attacks -[]%")) {
                populationLoss *= 1 - unique.params[0].toFloat() / 100f
            }
        //
        for (unique in targetedCity.getMatchingUniques("Population loss from nuclear attacks []% []")) {
            if (!targetedCity.matchesFilter(unique.params[1])) continue
            populationLoss *= unique.params[0].toPercent()
        }
        targetedCity.population.addPopulation(-populationLoss.toInt())
        if (targetedCity.population.population < 1) targetedCity.population.setPopulation(1)
    }

    private fun tryInterceptAirAttack(attacker: MapUnitCombatant, attackedTile:TileInfo, interceptingCiv:CivilizationInfo) {
        if (attacker.unit.hasUnique("Cannot be intercepted")) return
        for (interceptor in interceptingCiv.getCivUnits()
            .filter { it.canIntercept(attackedTile) }) {
            if (Random().nextFloat() > interceptor.interceptChance() / 100f) continue

            var damage = BattleDamage.calculateDamageToDefender(
                MapUnitCombatant(interceptor),
                null,
                attacker
            )

            var damageFactor = 1f + interceptor.interceptDamagePercentBonus().toFloat() / 100f
            damageFactor *= attacker.unit.receivedInterceptDamageFactor()

            damage = (damage.toFloat() * damageFactor).toInt()

            attacker.takeDamage(damage)
            interceptor.attacksThisTurn++

            val attackerName = attacker.getName()
            val interceptorName = interceptor.name
            val locations = LocationAction(
                listOf(
                    interceptor.currentTile.position,
                    attacker.unit.currentTile.position
                )
            )

            if (attacker.isDefeated()) {
                attacker.getCivInfo()
                    .addNotification(
                        "Our [$attackerName] was destroyed by an intercepting [$interceptorName]",
                        interceptor.currentTile.position, attackerName, NotificationIcon.War,
                        interceptorName
                    )
                interceptingCiv
                    .addNotification(
                        "Our [$interceptorName] intercepted and destroyed an enemy [$attackerName]",
                        locations, interceptorName, NotificationIcon.War, attackerName
                    )
            } else {
                attacker.getCivInfo()
                    .addNotification(
                        "Our [$attackerName] was attacked by an intercepting [$interceptorName]",
                        interceptor.currentTile.position, attackerName,NotificationIcon.War,
                        interceptorName
                    )
                interceptingCiv
                    .addNotification(
                        "Our [$interceptorName] intercepted and attacked an enemy [$attackerName]",
                        locations, interceptorName, NotificationIcon.War, attackerName
                    )
            }
            return
        }
    }

    private fun doWithdrawFromMeleeAbility(attacker: ICombatant, defender: ICombatant, withdrawUnique: Unique): Boolean {
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
        // base chance for all units is set to 80%
        val baseChance = withdrawUnique.params[0].toFloat()
        /* Calculate success chance: Base chance from json, calculation method from https://www.bilibili.com/read/cv2216728
        In general, except attacker's tile, 5 tiles neighbors the defender :
        2 of which are also attacker's neighbors ( we call them 2-Tiles) and the other 3 aren't (we call them 3-Tiles).
        Withdraw chance depends on 2 factors : attacker's movement and how many tiles in 3-Tiles the defender can't withdraw to.
        If the defender can withdraw, at first we choose a tile as toTile from 3-Tiles the defender can withdraw to.
        If 3-Tiles the defender can withdraw to is null, we choose this from 2-Tiles the defender can withdraw to.
        If 2-Tiles the defender can withdraw to is also null, we return false.
        */
        val percentChance = baseChance - max(0, (attackBaseUnit.movement-2)) * 20 -
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
        val locations = LocationAction(listOf(toTile.position, attacker.getTile().position))
        defender.getCivInfo().addNotification(notificationString, locations, defendingUnit, NotificationIcon.War, attackingUnit)
        attacker.getCivInfo().addNotification(notificationString, locations, defendingUnit, NotificationIcon.War, attackingUnit)
        return true
    }

}

package com.unciv.logic.battle

import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import java.util.*
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object BattleDamage {

    private fun getModifierStringFromUnique(unique: Unique): String {
        val source = when (unique.sourceObjectType) {
            UniqueTarget.Unit -> "Unit ability"
            UniqueTarget.Nation -> "National ability"
            UniqueTarget.Global -> GlobalUniques.getUniqueSourceDescription(unique)
            else -> "[${unique.sourceObjectName}] ([${unique.sourceObjectType?.name}])"
        }.tr()
        if (unique.conditionals.isEmpty()) return source

        val conditionalsText = unique.conditionals.joinToString { it.text.tr() }
        return "$source - $conditionalsText"
    }

    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant, combatAction: CombatAction, tileToAttackFrom:Tile): Counter<String> {
        val modifiers = Counter<String>()

        val civInfo = combatant.getCivInfo()
        val attackedTile =
            if (combatAction == CombatAction.Attack) enemy.getTile()
            else combatant.getTile()

        val conditionalState = StateForConditionals(civInfo, city = (combatant as? CityCombatant)?.city, ourCombatant = combatant, theirCombatant = enemy,
            attackedTile = attackedTile, combatAction = combatAction)

        if (combatant is MapUnitCombatant) {

            for (unique in combatant.getMatchingUniques(UniqueType.Strength, conditionalState, true)) {
                modifiers.add(getModifierStringFromUnique(unique), unique.params[0].toInt())
            }
            for (unique in combatant.getMatchingUniques(
                UniqueType.StrengthNearCapital, conditionalState, true
            )) {
                if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) break
                val distance =
                    combatant.getTile().aerialDistanceTo(civInfo.getCapital()!!.getCenterTile())
                // https://steamcommunity.com/sharedfiles/filedetails/?id=326411722#464287
                val effect = unique.params[0].toInt() - 3 * distance
                if (effect <= 0) continue
                modifiers.add("${unique.sourceObjectName} (${unique.sourceObjectType})", effect)
            }

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            var adjacentUnits = combatant.getTile().neighbors.flatMap { it.getUnits() }
            if (enemy.getTile() !in combatant.getTile().neighbors && tileToAttackFrom in combatant.getTile().neighbors
                    && enemy is MapUnitCombatant)
                adjacentUnits += sequenceOf(enemy.unit)
            val strengthMalus = adjacentUnits.filter { it.civ.isAtWarWith(civInfo) }
                    .flatMap { it.getMatchingUniques(UniqueType.StrengthForAdjacentEnemies) }
                    .filter { combatant.matchesCategory(it.params[1]) && combatant.getTile().matchesFilter(it.params[2]) }
                    .maxByOrNull { it.params[0] }
            if (strengthMalus != null) {
                modifiers.add("Adjacent enemy units", strengthMalus.params[0].toInt())
            }

            val civResources = civInfo.getCivResourcesByName()
            for (resource in combatant.unit.baseUnit.getResourceRequirementsPerTurn().keys)
                if (civResources[resource]!! < 0 && !civInfo.isBarbarian())
                    modifiers["Missing resource"] = -25  //todo ModConstants

            val (greatGeneralName, greatGeneralBonus) = GreatGeneralImplementation.getGreatGeneralBonus(combatant, enemy, combatAction)
            if (greatGeneralBonus != 0)
                modifiers[greatGeneralName] = greatGeneralBonus

            for (unique in combatant.unit.getMatchingUniques(UniqueType.StrengthWhenStacked)) {
                var stackedUnitsBonus = 0
                if (combatant.unit.getTile().getUnits().any { it.matchesFilter(unique.params[1]) })
                    stackedUnitsBonus += unique.params[0].toInt()

                if (stackedUnitsBonus > 0)
                    modifiers["Stacked with [${unique.params[1]}]"] = stackedUnitsBonus
            }

            if (enemy.getCivInfo().isCityState()
                && civInfo.hasUnique(UniqueType.StrengthBonusVsCityStates)
            )
                modifiers["vs [City-States]"] = 30
        } else if (combatant is CityCombatant) {
            for (unique in combatant.city.getMatchingUniques(UniqueType.StrengthForCities, conditionalState)) {
                modifiers.add(getModifierStringFromUnique(unique), unique.params[0].toInt())
            }
        }

        if (enemy.getCivInfo().isBarbarian()) {
            modifiers["Difficulty"] =
                (civInfo.gameInfo.getDifficulty().barbarianBonus * 100).toInt()
        }

        return modifiers
    }

    fun getAttackModifiers(
        attacker: ICombatant,
        defender: ICombatant, tileToAttackFrom: Tile
    ): Counter<String> {
        val modifiers = getGeneralModifiers(attacker, defender, CombatAction.Attack, tileToAttackFrom)

        if (attacker is MapUnitCombatant) {
            if (attacker.unit.isEmbarked()
                    && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast))
                modifiers["Landing"] = -50

            // Land Melee Unit attacking to Water
            if (attacker.unit.type.isLandUnit() && !attacker.unit.isEmbarked() && attacker.isMelee() && defender.getTile().isWater
                    && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast))
                modifiers["Boarding"] = -50
            // Naval Unit Melee attacking to Land (not City) unit
            if (attacker.unit.type.isWaterUnit() && attacker.isMelee() && !defender.getTile().isWater
                    && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast) && !defender.isCity())
                modifiers["Landing"] = -50
            // Air unit attacking with Air Sweep
            if (attacker.unit.isPreparingAirSweep())
                modifiers.add(getAirSweepAttackModifiers(attacker))

            if (attacker.isMelee()) {
                val numberOfOtherAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null && it.militaryUnit != attacker.unit
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfOtherAttackersSurroundingDefender > 0) {
                    var flankingBonus = 10f //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
                    for (unique in attacker.unit.getMatchingUniques(UniqueType.FlankAttackBonus, checkCivInfoUniques = true))
                        flankingBonus *= unique.params[0].toPercent()
                    modifiers["Flanking"] =
                        (flankingBonus * numberOfOtherAttackersSurroundingDefender).toInt()
                }
                if (tileToAttackFrom.aerialDistanceTo(defender.getTile()) == 1 &&
                        tileToAttackFrom.isConnectedByRiver(defender.getTile()) &&
                        !attacker.unit.hasUnique(UniqueType.AttackAcrossRiver)
                ) {
                    if (!tileToAttackFrom
                            .hasConnection(attacker.getCivInfo()) // meaning, the tiles are not road-connected for this civ
                        || !defender.getTile().hasConnection(attacker.getCivInfo())
                        || !attacker.getCivInfo().tech.roadsConnectAcrossRivers
                    ) {
                        modifiers["Across river"] = -20
                    }
                }
            }

        }

        return modifiers
    }

    fun getAirSweepAttackModifiers(
        attacker: ICombatant
    ): Counter<String> {
        val modifiers = Counter<String>()

        if (attacker is MapUnitCombatant) {
            for (unique in attacker.unit.getMatchingUniques(UniqueType.StrengthWhenAirsweep)) {
                modifiers.add(getModifierStringFromUnique(unique), unique.params[0].toInt())
            }
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant, tileToAttackFrom: Tile): Counter<String> {
        val modifiers = getGeneralModifiers(defender, attacker, CombatAction.Defend, tileToAttackFrom)
        val tile = defender.getTile()

        if (defender is MapUnitCombatant) {

            if (defender.unit.isEmbarked()) {
                // embarked units get no defensive modifiers apart from this unique
                if (defender.unit.hasUnique(UniqueType.DefenceBonusWhenEmbarked, checkCivInfoUniques = true)
                )
                    modifiers["Embarked"] = 100

                return modifiers
            }

            val tileDefenceBonus = tile.getDefensiveBonus()
            if (!defender.unit.hasUnique(UniqueType.NoDefensiveTerrainBonus, checkCivInfoUniques = true) && tileDefenceBonus > 0
                || !defender.unit.hasUnique(UniqueType.NoDefensiveTerrainPenalty, checkCivInfoUniques = true) && tileDefenceBonus < 0
            )
                modifiers["Tile"] = (tileDefenceBonus * 100).toInt()


            if (defender.unit.isFortified())
                modifiers["Fortification"] = 20 * defender.unit.getFortificationTurns()
        }

        return modifiers
    }


    private fun modifiersToFinalBonus(modifiers: Counter<String>): Float {
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier += modifierValue / 100f
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        return if (combatant !is MapUnitCombatant
            || combatant.unit.hasUnique(UniqueType.NoDamagePenalty, checkCivInfoUniques = true)
        ) 1f
        // Each 3 points of health reduces damage dealt by 1%
        else 1 - (100 - combatant.getHealth()) / 300f
    }


    /**
     * Includes attack modifiers
     */
    fun getAttackingStrength(
        attacker: ICombatant,
        defender: ICombatant,
        tileToAttackFrom: Tile
    ): Float {
        val attackModifier = modifiersToFinalBonus(getAttackModifiers(attacker, defender, tileToAttackFrom))
        return max(1f, attacker.getAttackingStrength() * attackModifier)
    }


    /**
     * Includes defence modifiers
     */
    fun getDefendingStrength(attacker: ICombatant, defender: ICombatant, tileToAttackFrom: Tile): Float {
        val defenceModifier = modifiersToFinalBonus(getDefenceModifiers(attacker, defender, tileToAttackFrom))
        return max(1f, defender.getDefendingStrength(attacker.isRanged()) * defenceModifier)
    }

    fun calculateDamageToAttacker(
        attacker: ICombatant,
        defender: ICombatant,
        tileToAttackFrom: Tile = defender.getTile(),
        /** Between 0 and 1. */
        randomnessFactor: Float = Random(attacker.getCivInfo().gameInfo.turns * attacker.getTile().position.hashCode().toLong()).nextFloat()
    ): Int {
        if (attacker.isRanged() && !attacker.isAirUnit()) return 0
        if (defender.isCivilian()) return 0
        val ratio = getAttackingStrength(attacker, defender, tileToAttackFrom) / getDefendingStrength(
                attacker, defender, tileToAttackFrom)
        return (damageModifier(ratio, true, randomnessFactor) * getHealthDependantDamageRatio(defender)).roundToInt()
    }

    fun calculateDamageToDefender(
        attacker: ICombatant,
        defender: ICombatant,
        tileToAttackFrom: Tile = defender.getTile(),
        /** Between 0 and 1.  Defaults to turn and location-based random to avoid save scumming */
        randomnessFactor: Float = Random(attacker.getCivInfo().gameInfo.turns * attacker.getTile().position.hashCode().toLong()).nextFloat()
        ,
    ): Int {
        if (defender.isCivilian()) return 40
        val ratio = getAttackingStrength(attacker, defender, tileToAttackFrom) /
                getDefendingStrength(attacker, defender, tileToAttackFrom)
        return (damageModifier(ratio, false, randomnessFactor) * getHealthDependantDamageRatio(attacker)).roundToInt()
    }

    private fun damageModifier(
        attackerToDefenderRatio: Float,
        damageToAttacker: Boolean,
        /** Between 0 and 1. */
        randomnessFactor: Float,
    ): Float {
        // https://forums.civfanatics.com/threads/getting-the-combat-damage-math.646582/#post-15468029
        val strongerToWeakerRatio =
            attackerToDefenderRatio.pow(if (attackerToDefenderRatio < 1) -1 else 1)
        var ratioModifier = (((strongerToWeakerRatio + 3) / 4).pow(4) + 1) / 2
        if (damageToAttacker && attackerToDefenderRatio > 1 || !damageToAttacker && attackerToDefenderRatio < 1) // damage ratio from the weaker party is inverted
            ratioModifier = ratioModifier.pow(-1)
        val randomCenteredAround30 = 24 + 12 * randomnessFactor
        return randomCenteredAround30 * ratioModifier
    }
}
enum class CombatAction {
    Attack,
    Defend,
    Intercept,
}

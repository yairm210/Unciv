package com.unciv.logic.battle

import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.toPercent
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

    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant, combatAction: CombatAction): Counter<String> {
        val modifiers = Counter<String>()

        val civInfo = combatant.getCivInfo()
        val attackedTile =
            if (combatAction == CombatAction.Attack) enemy.getTile()
            else combatant.getTile()

        val conditionalState = StateForConditionals(civInfo, cityInfo = (combatant as? CityCombatant)?.city, ourCombatant = combatant, theirCombatant = enemy,
            attackedTile = attackedTile, combatAction = combatAction)
        
        
        if (combatant is MapUnitCombatant) {
            
            for (unique in combatant.getMatchingUniques(UniqueType.Strength, conditionalState, true)) {
                modifiers.add(getModifierStringFromUnique(unique), unique.params[0].toInt())
            }
            for (unique in combatant.getMatchingUniques(
                UniqueType.StrengthNearCapital, conditionalState, true
            )) {
                if (civInfo.cities.isEmpty()) break
                val distance =
                    combatant.getTile().aerialDistanceTo(civInfo.getCapital().getCenterTile())
                // https://steamcommunity.com/sharedfiles/filedetails/?id=326411722#464287
                val effect = unique.params[0].toInt() - 3 * distance
                if (effect <= 0) continue
                modifiers.add("${unique.sourceObjectName} (${unique.sourceObjectType})", effect)
            }

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            val adjacentUnits = combatant.getTile().neighbors.flatMap { it.getUnits() }


            for (unique in adjacentUnits.filter { it.civInfo.isAtWarWith(combatant.getCivInfo()) }
                    .flatMap { it.getMatchingUniques(UniqueType.StrengthForAdjacentEnemies) })
                if (combatant.matchesCategory(unique.params[1]) && combatant.getTile()
                        .matchesFilter(unique.params[2])
                )
                    modifiers.add("Adjacent enemy units", unique.params[0].toInt())

            val civResources = civInfo.getCivResourcesByName()
            for (resource in combatant.unit.baseUnit.getResourceRequirements().keys)
                if (civResources[resource]!! < 0 && !civInfo.isBarbarian())
                    modifiers["Missing resource"] = -25


            val nearbyCivUnits = combatant.unit.getTile().getTilesInDistance(2)
                .flatMap { it.getUnits() }.filter { it.civInfo == combatant.unit.civInfo }
            if (nearbyCivUnits.any { it.hasUnique("Bonus for units in 2 tile radius 15%") }) {
                val greatGeneralModifier =
                    if (combatant.unit.civInfo.hasUnique(UniqueType.GreatGeneralProvidesDoubleCombatBonus)) 30 else 15

                modifiers["Great General"] = greatGeneralModifier
            }

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
        defender: ICombatant
    ): Counter<String> {
        val modifiers = getGeneralModifiers(attacker, defender, CombatAction.Attack)

        if (attacker is MapUnitCombatant) {
            modifiers.add(getTileSpecificModifiers(attacker, defender.getTile()))

            // Depreciated Version
            if (attacker.unit.isEmbarked() && !attacker.unit.hasUnique(UniqueType.AttackFromSea))
                modifiers["Landing"] = -50
            if (attacker.unit.isEmbarked() && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast))
                modifiers["Landing"] = -50

            // Land Melee Unit attacking to Water
            if (attacker.unit.type.isLandUnit() && !attacker.unit.isEmbarked() && attacker.isMelee() && defender.getTile().isWater
                    && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast))
                modifiers["Boarding"] = -50
            // Naval Unit Melee attacking to Land (not City) unit
            if (attacker.unit.type.isWaterUnit() && attacker.isMelee() && !defender.getTile().isWater
                    && !attacker.unit.hasUnique(UniqueType.AttackAcrossCoast) && !defender.isCity())
                modifiers["Landing"] = -50

            if (attacker.isMelee()) {
                val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfAttackersSurroundingDefender > 1) {
                    var flankingBonus = 10f //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
                    for (unique in attacker.unit.getMatchingUniques(UniqueType.FlankAttackBonus, checkCivInfoUniques = true))
                        flankingBonus *= unique.params[0].toPercent()
                    modifiers["Flanking"] =
                        (flankingBonus * (numberOfAttackersSurroundingDefender - 1)).toInt()
                }
                if (attacker.getTile().aerialDistanceTo(defender.getTile()) == 1 &&
                        attacker.getTile().isConnectedByRiver(defender.getTile()) &&
                        !attacker.unit.hasUnique(UniqueType.AttackAcrossRiver)
                ) {
                    if (!attacker.getTile()
                            .hasConnection(attacker.getCivInfo()) // meaning, the tiles are not road-connected for this civ
                        || !defender.getTile().hasConnection(attacker.getCivInfo())
                        || !attacker.getCivInfo().tech.roadsConnectAcrossRivers
                    ) {
                        modifiers["Across river"] = -20
                    }
                }
            }

            for (unique in attacker.getCivInfo().getMatchingUniques(UniqueType.TimedAttackStrength)) {
                if (attacker.matchesCategory(unique.params[1])) {
                    modifiers.add("Temporary Bonus", unique.params[0].toInt())
                }
            }

        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant): Counter<String> {
        val modifiers = getGeneralModifiers(defender, attacker, CombatAction.Defend)
        val tile = defender.getTile()
    
        if (defender is MapUnitCombatant) {

            if (defender.unit.isEmbarked()) {
                // embarked units get no defensive modifiers apart from this unique
                if (defender.unit.hasUnique(UniqueType.DefenceBonusWhenEmbarked, checkCivInfoUniques = true) ||
                    defender.getCivInfo().hasUnique(UniqueType.DefenceBonusWhenEmbarkedCivwide)
                )
                    modifiers["Embarked"] = 100

                return modifiers
            }

            modifiers.putAll(getTileSpecificModifiers(defender, tile))

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
    
    @Deprecated("As of 4.0.3", level=DeprecationLevel.WARNING)
    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): Counter<String> {
        val modifiers = Counter<String>()

        for (unique in unit.getCivInfo().getMatchingUniques(UniqueType.StrengthWithinTilesOfTile)) {
            if (tile.getTilesInDistance(unique.params[1].toInt())
                    .any { it.matchesFilter(unique.params[2]) }
            )
                modifiers[unique.params[2]] = unique.params[0].toInt()
        }

        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: Counter<String>): Float {
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier *= modifierValue.toPercent()
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        return if (combatant !is MapUnitCombatant
            || combatant.unit.hasUnique(UniqueType.NoDamagePenalty, checkCivInfoUniques = true)
            || combatant.getCivInfo().hasUnique(UniqueType.UnitsFightFullStrengthWhenDamaged)
        ) {
            1f
        }
        // Each 3 points of health reduces damage dealt by 1%
        else 1 - (100 - combatant.getHealth()) / 300f 
    }


    /**
     * Includes attack modifiers
     */
    private fun getAttackingStrength(
        attacker: ICombatant,
        defender: ICombatant
    ): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker, defender))
        return max(1f, attacker.getAttackingStrength() * attackModifier)
    }


    /**
     * Includes defence modifiers
     */
    private fun getDefendingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val defenceModifier = modifiersToMultiplicationBonus(getDefenceModifiers(attacker, defender))
        return max(1f, defender.getDefendingStrength() * defenceModifier)
    }

    fun calculateDamageToAttacker(
        attacker: ICombatant,
        defender: ICombatant,
        ignoreRandomness: Boolean = false,
    ): Int {
        if (attacker.isRanged() && !attacker.isAirUnit()) return 0
        if (defender.isCivilian()) return 0
        val ratio = getAttackingStrength(attacker, defender) / getDefendingStrength(
                attacker, defender)
        return (damageModifier(ratio, true, attacker, ignoreRandomness) * getHealthDependantDamageRatio(defender)).roundToInt()
    }

    fun calculateDamageToDefender(
        attacker: ICombatant,
        defender: ICombatant,
        ignoreRandomness: Boolean = false,
    ): Int {
        if (defender.isCivilian()) return 40
        val ratio = getAttackingStrength(attacker, defender) / getDefendingStrength(
                attacker, defender)
        return (damageModifier(ratio, false, attacker, ignoreRandomness) * getHealthDependantDamageRatio(attacker)).roundToInt()
    }

    private fun damageModifier(
        attackerToDefenderRatio: Float,
        damageToAttacker: Boolean,
        attacker: ICombatant, // for the randomness
        ignoreRandomness: Boolean = false,
    ): Float {
        // https://forums.civfanatics.com/threads/getting-the-combat-damage-math.646582/#post-15468029
        val strongerToWeakerRatio =
            attackerToDefenderRatio.pow(if (attackerToDefenderRatio < 1) -1 else 1)
        var ratioModifier = (((strongerToWeakerRatio + 3) / 4).pow(4) + 1) / 2
        if (damageToAttacker && attackerToDefenderRatio > 1 || !damageToAttacker && attackerToDefenderRatio < 1) // damage ratio from the weaker party is inverted
            ratioModifier = ratioModifier.pow(-1)
        val randomSeed = attacker.getCivInfo().gameInfo.turns * attacker.getTile().position.hashCode() // so people don't save-scum to get optimal results
        val randomCenteredAround30 = 24 +
                if (ignoreRandomness) 6f
                else 12 * Random(randomSeed.toLong()).nextFloat()
        return randomCenteredAround30 * ratioModifier
    }
}

enum class CombatAction {
    Attack,
    Defend,
    Intercept,
}

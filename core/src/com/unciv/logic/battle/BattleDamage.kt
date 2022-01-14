package com.unciv.logic.battle

import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.toPercent
import java.util.*
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object BattleDamage {
    
    private fun getModifierStringFromUnique(unique: Unique): String {
        val source =  when (unique.sourceObjectType) {
            UniqueTarget.Unit -> "Unit ability"
            UniqueTarget.Nation -> "National ability"
            else -> "[${unique.sourceObjectName}] ([${unique.sourceObjectType?.name}])"
        }
        if (unique.conditionals.isEmpty()) return source

        val conditionalsText = unique.conditionals.joinToString { it.text }
        return "$source - $conditionalsText"
    }

    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant, combatAction: CombatAction): Counter<String> {
        val modifiers = Counter<String>()

        val civInfo = combatant.getCivInfo()
        val attackedTile =
            if (combatAction == CombatAction.Attack) enemy.getTile()
            else combatant.getTile()
        
        val conditionalState = StateForConditionals(civInfo, ourCombatant = combatant, theirCombatant = enemy,
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
            val civHappiness = if (civInfo.isCityState() && civInfo.getAllyCiv() != null)
            // If we are a city state with an ally we are vulnerable to their unhappiness.
                min(
                    civInfo.gameInfo.getCivilization(civInfo.getAllyCiv()!!).getHappiness(),
                    civInfo.getHappiness()
                )
            else civInfo.getHappiness()
            if (civHappiness < 0)
                modifiers["Unhappiness"] = max(
                    2 * civHappiness,
                    -90
                ) // otherwise it could exceed -100% and start healing enemy units...

            val adjacentUnits = combatant.getTile().neighbors.flatMap { it.getUnits() }

            // Deprecated since 3.18.17
                for (unique in civInfo.getMatchingUniques(UniqueType.StrengthFromAdjacentUnits)) {
                    if (combatant.matchesCategory(unique.params[1])
                        && adjacentUnits.any { it.civInfo == civInfo && it.matchesFilter(unique.params[2]) }
                    ) {
                        modifiers.add("Adjacent units", unique.params[0].toInt())
                    }
                }
            //

            for (unique in adjacentUnits.filter { it.civInfo.isAtWarWith(combatant.getCivInfo()) }
                .flatMap { it.getMatchingUniques("[]% Strength for enemy [] units in adjacent [] tiles") })
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
                    if (combatant.unit.civInfo.hasUnique("Great General provides double combat bonus")) 30 else 15

                modifiers["Great General"] = greatGeneralModifier
            }

            for (unique in combatant.unit.getMatchingUniques("[]% Strength when stacked with []")) {
                var stackedUnitsBonus = 0
                if (combatant.unit.getTile().getUnits().any { it.matchesFilter(unique.params[1]) })
                    stackedUnitsBonus += unique.params[0].toInt()

                if (stackedUnitsBonus > 0)
                    modifiers["Stacked with [${unique.params[1]}]"] = stackedUnitsBonus
            }

            if (enemy.getCivInfo().isCityState()
                && civInfo.hasUnique("+30% Strength when fighting City-State units and cities")
            )
                modifiers["vs [City-States]"] = 30
        } else if (combatant is CityCombatant) {
            for (unique in combatant.getCivInfo().getMatchingUniques(UniqueType.StrengthForCities, conditionalState)) {
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


            if (attacker.unit.isEmbarked() && !attacker.unit.hasUnique("Eliminates combat penalty for attacking from the sea"))
                modifiers["Landing"] = -50


            if (attacker.isMelee()) {
                val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfAttackersSurroundingDefender > 1) {
                    var flankingBonus = 10f //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
                    for (unique in attacker.unit.getMatchingUniques("[]% to Flank Attack bonuses"))
                        flankingBonus *= unique.params[0].toPercent()
                    modifiers["Flanking"] =
                        (flankingBonus * (numberOfAttackersSurroundingDefender - 1)).toInt()
                }
                if (attacker.getTile()
                        .aerialDistanceTo(defender.getTile()) == 1 && attacker.getTile()
                        .isConnectedByRiver(defender.getTile())
                    && !attacker.unit.hasUnique("Eliminates combat penalty for attacking over a river")
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

        } else if (attacker is CityCombatant) {
            if (attacker.city.getCenterTile().militaryUnit != null) {
                val garrisonBonus = attacker.city.getMatchingUniques("+[]% attacking strength for cities with garrisoned units")
                    .sumOf { it.params[0].toInt() }
                if (garrisonBonus != 0)
                    modifiers["Garrisoned unit"] = garrisonBonus
            }
            for (unique in attacker.city.getMatchingUniques(UniqueType.StrengthForCitiesAttacking)) {
                modifiers.add("Attacking Bonus", unique.params[0].toInt())
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
                if (defender.unit.hasUnique(UniqueType.DefenceBonusWhenEmbarked) ||
                    defender.getCivInfo().hasUnique(UniqueType.DefenceBonusWhenEmbarkedCivwide)
                )
                    modifiers["Embarked"] = 100

                return modifiers
            }

            modifiers.putAll(getTileSpecificModifiers(defender, tile))

            val tileDefenceBonus = tile.getDefensiveBonus()
            if (!defender.unit.hasUnique(UniqueType.NoDefensiveTerrainBonus) && tileDefenceBonus > 0
                || !defender.unit.hasUnique(UniqueType.NoDefensiveTerrainPenalty) && tileDefenceBonus < 0
            )
                modifiers["Tile"] = (tileDefenceBonus * 100).toInt()


            if (defender.unit.isFortified())
                modifiers["Fortification"] = 20 * defender.unit.getFortificationTurns()
        } else if (defender is CityCombatant) {

            modifiers["Defensive Bonus"] =
                defender.city.civInfo.getMatchingUniques(UniqueType.StrengthForCitiesDefending)
                    .map { it.params[0].toFloat() / 100f }.sum().toInt()
        }

        return modifiers
    }
    
    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): Counter<String> {
        val modifiers = Counter<String>()

        for (unique in unit.getCivInfo().getMatchingUniques("+[]% Strength if within [] tiles of a []")) {
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
        return if (combatant !is MapUnitCombatant // is city
            || (combatant.getCivInfo().hasUnique("Units fight as though they were at full strength even when damaged")
                && !combatant.unit.baseUnit.movesLikeAirUnits()
            )
        ) {
            1f
        }
        else 1 - (100 - combatant.getHealth()) / 300f// Each 3 points of health reduces damage dealt by 1% like original game
    }


    /**
     * Includes attack modifiers
     */
    private fun getAttackingStrength(
        attacker: ICombatant,
        defender: ICombatant
    ): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker, defender))
        return attacker.getAttackingStrength() * attackModifier
    }


    /**
     * Includes defence modifiers
     */
    private fun getDefendingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val defenceModifier = modifiersToMultiplicationBonus(getDefenceModifiers(attacker, defender))
        return defender.getDefendingStrength() * defenceModifier
    }

    fun calculateDamageToAttacker(
        attacker: ICombatant,
        tileToAttackFrom: TileInfo?,
        defender: ICombatant
    ): Int {
        if (attacker.isRanged()) return 0
        if (defender.isCivilian()) return 0
        val ratio =
            getAttackingStrength(attacker, defender) / getDefendingStrength(
                attacker,
                defender
            )
        return (damageModifier(ratio, true) * getHealthDependantDamageRatio(defender)).roundToInt()
    }

    fun calculateDamageToDefender(
        attacker: ICombatant,
        tileToAttackFrom: TileInfo?,
        defender: ICombatant
    ): Int {
        val ratio =
            getAttackingStrength(attacker, defender) / getDefendingStrength(
                attacker,
                defender
            )
        return (damageModifier(ratio, false) * getHealthDependantDamageRatio(attacker)).roundToInt()
    }

    private fun damageModifier(attackerToDefenderRatio: Float, damageToAttacker: Boolean): Float {
        // https://forums.civfanatics.com/threads/getting-the-combat-damage-math.646582/#post-15468029
        val strongerToWeakerRatio =
            attackerToDefenderRatio.pow(if (attackerToDefenderRatio < 1) -1 else 1)
        var ratioModifier = (((strongerToWeakerRatio + 3) / 4).pow(4) + 1) / 2
        if (damageToAttacker && attackerToDefenderRatio > 1 || !damageToAttacker && attackerToDefenderRatio < 1) // damage ratio from the weaker party is inverted
            ratioModifier = ratioModifier.pow(-1)
        val randomCenteredAround30 = 24 + 12 * Random().nextFloat()
        return randomCenteredAround30 * ratioModifier
    }
}

enum class CombatAction {
    Attack,
    Defend,
    Intercept,
}
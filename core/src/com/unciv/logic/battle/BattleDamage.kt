package com.unciv.logic.battle

import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.unit.UnitType
import java.util.*
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class BattleDamageModifier(val vs:String, val modificationAmount:Float){
    fun getText(): String = "vs $vs"
}

object BattleDamage {

    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant): Counter<String> {
        val modifiers = Counter<String>()

        val civInfo = combatant.getCivInfo()
        if (combatant is MapUnitCombatant) {
            for (unique in 
                    combatant.unit.getMatchingUniques("+[]% Strength vs []") +
                    civInfo.getMatchingUniques("+[]% Strength vs []")
            ) {
                if (enemy.matchesCategory(unique.params[1]))
                    modifiers.add("vs [${unique.params[1]}]", unique.params[0].toInt())
            }
            for (unique in combatant.unit.getMatchingUniques("-[]% Strength vs []")+
                    civInfo.getMatchingUniques("-[]% Strength vs []")
            ) {
                if (enemy.matchesCategory(unique.params[1]))
                    modifiers.add("vs [${unique.params[1]}]", -unique.params[0].toInt())
            }

            for (unique in combatant.unit.getMatchingUniques("+[]% Combat Strength"))
                modifiers.add("Combat Strength", unique.params[0].toInt())

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            val civHappiness = civInfo.getHappiness()
            if (civHappiness < 0)
                modifiers["Unhappiness"] = max(
                    2 * civHappiness,
                    -90
                ) // otherwise it could exceed -100% and start healing enemy units...

            for (unique in civInfo.getMatchingUniques("[] units deal +[]% damage")) {
                if (combatant.matchesCategory(unique.params[0])) {
                    modifiers.add(unique.params[0], unique.params[1].toInt())
                }
            }

            val adjacentUnits = combatant.getTile().neighbors.flatMap { it.getUnits() }
            
            for (unique in civInfo.getMatchingUniques("+[]% Strength for [] units which have another [] unit in an adjacent tile")) {
                if (combatant.matchesCategory(unique.params[1])
                    && adjacentUnits.any { it.civInfo == civInfo && it.matchesFilter(unique.params[2]) } 
                ) {
                    modifiers.add("Adjacent units", unique.params[0].toInt())
                }
            }
            
            for (unique in adjacentUnits.flatMap { it.getMatchingUniques("[]% Strength for enemy [] units in adjacent [] tiles") })
                if (combatant.matchesCategory(unique.params[1]) && combatant.getTile().matchesFilter(unique.params[2]))
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

            if (civInfo.goldenAges.isGoldenAge() && civInfo.hasUnique("+10% Strength for all units during Golden Age"))
                modifiers["Golden Age"] = 10

            if (enemy.getCivInfo()
                    .isCityState() && civInfo.hasUnique("+30% Strength when fighting City-State units and cities")
            )
                modifiers["vs [City-States]"] = 30
            
            // Deprecated since 3.14.17
                if (civInfo.hasUnique("+15% combat strength for melee units which have another military unit in an adjacent tile")
                    && combatant.isMelee()
                    && combatant.getTile().neighbors.flatMap { it.getUnits() }
                        .any { it.civInfo == civInfo && !it.type.isCivilian() && !it.type.isAirUnit() && !it.type.isMissile() }
                )
                    modifiers["Discipline"] = 15
            //
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
        val modifiers = getGeneralModifiers(attacker, defender)

        if (attacker is MapUnitCombatant) {
            modifiers.add(getTileSpecificModifiers(attacker, defender.getTile()))

            for (unique in attacker.unit.getMatchingUniques("+[]% Strength when attacking")) {
                modifiers.add("Attacker Bonus", unique.params[0].toInt())
            }

            if (attacker.unit.isEmbarked() && !attacker.unit.hasUnique("Eliminates combat penalty for attacking from the sea"))
                modifiers["Landing"] = -50


            if (attacker.isMelee()) {
                val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfAttackersSurroundingDefender > 1)
                    modifiers["Flanking"] =
                        10 * (numberOfAttackersSurroundingDefender - 1) //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
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

            for (unique in attacker.getCivInfo().getMatchingUniques("+[]% attack strength to all [] units for [] turns")) {
                if (attacker.matchesCategory(unique.params[1])) {
                    modifiers.add("Temporary Bonus", unique.params[0].toInt())
                }
            }

            if (defender is CityCombatant &&
                attacker.getCivInfo()
                    .hasUnique("+15% Combat Strength for all units when attacking Cities")
            )
                modifiers["Statue of Zeus"] = 15
        } else if (attacker is CityCombatant) {
            if (attacker.city.getCenterTile().militaryUnit != null) {
                val garrisonBonus = attacker.city.getMatchingUniques("+[]% attacking strength for cities with garrisoned units")
                    .sumBy { it.params[0].toInt() }
                if (garrisonBonus != 0)
                    modifiers["Garrisoned unit"] = garrisonBonus
            }
            for (unique in attacker.city.getMatchingUniques("[]% attacking Strength for cities")) {
                modifiers.add("Attacking Bonus", unique.params[0].toInt())
            }
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant): Counter<String> {
        val modifiers = getGeneralModifiers(defender, attacker)
        val tile = defender.getTile()
    
        if (defender is MapUnitCombatant) {

            if (defender.unit.isEmbarked()) {
                // embarked units get no defensive modifiers apart from this unique
                if (defender.unit.hasUnique("Defense bonus when embarked") ||
                    defender.getCivInfo().hasUnique("Embarked units can defend themselves")
                )
                    modifiers["Embarked"] = 100

                return modifiers
            }

            modifiers.putAll(getTileSpecificModifiers(defender, tile))

            val tileDefenceBonus = tile.getDefensiveBonus()
            if (!defender.unit.hasUnique("No defensive terrain bonus") && tileDefenceBonus > 0
                || !defender.unit.hasUnique("No defensive terrain penalty") && tileDefenceBonus < 0
            )
                modifiers["Tile"] = (tileDefenceBonus * 100).toInt()

            for (unique in defender.unit.getMatchingUniques("[]% Strength when defending vs []")) {
                if (attacker.matchesCategory(unique.params[1]))
                    modifiers.add("defence vs [${unique.params[1]}] ", unique.params[0].toInt())
            }
            
            // Deprecated since 3.15.7
                if (attacker.isRanged()) {
                    val defenceVsRanged = 25 * defender.unit.getUniques()
                        .count { it.text == "+25% Defence against ranged attacks" }
                    if (defenceVsRanged > 0) modifiers.add("defence vs ranged", defenceVsRanged)
                }
            //

            for (unique in defender.unit.getMatchingUniques("+[]% Strength when defending")) {
                modifiers.add("Defender Bonus", unique.params[0].toInt())
            }

            for (unique in defender.unit.getMatchingUniques("+[]% defence in [] tiles")) {
                if (tile.matchesFilter(unique.params[1]))
                    modifiers.add("[${unique.params[1]}] defence", unique.params[0].toInt())
            }

            if (defender.unit.isFortified())
                modifiers["Fortification"] = 20 * defender.unit.getFortificationTurns()
        } else if (defender is CityCombatant) {
            
            modifiers["Defensive Bonus"] = defender.city.civInfo.getMatchingUniques("+[]% Defensive strength for cities")
                .map { it.params[0].toFloat() / 100f }.sum().toInt()
            
        }

        return modifiers
    }
    
    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): Counter<String> {
        val modifiers = Counter<String>()

        for (unique in unit.unit.getMatchingUniques("+[]% Strength in []")
                + unit.getCivInfo()
            .getMatchingUniques("+[]% Strength for units fighting in []")) {
            val filter = unique.params[1]
            if (tile.matchesFilter(filter, unit.getCivInfo()))
                modifiers.add(filter, unique.params[0].toInt())
        }
        
        // Deprecated since 3.15
            for (unique in unit.getCivInfo().getMatchingUniques("+[]% combat bonus for units fighting in []")) {
                val filter = unique.params[1]
                if (tile.matchesFilter(filter, unit.getCivInfo()))
                    modifiers.add(filter, unique.params[0].toInt())
            }
        //

        for (unique in unit.getCivInfo()
            .getMatchingUniques("+[]% Strength if within [] tiles of a []")) {
            if (tile.getTilesInDistance(unique.params[1].toInt())
                    .any { it.matchesFilter(unique.params[2]) }
            )
                modifiers[unique.params[2]] = unique.params[0].toInt()
        }
    
        // Deprecated since 3.15.7
            if (tile.neighbors.flatMap { it.getUnits() }
                    .any {
                        it.hasUnique("-10% combat strength for adjacent enemy units") && it.civInfo.isAtWarWith(
                            unit.getCivInfo()
                        )
                    })
                modifiers["Haka War Dance"] = -10
        //
        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: Counter<String>): Float {
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier *= (1 + modifierValue / 100f) // so 25 will result in *= 1.25
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        return if (combatant.getUnitType() == UnitType.City
            || combatant.getCivInfo()
                .hasUnique("Units fight as though they were at full strength even when damaged")
            && !combatant.getUnitType().isAirUnit()
            && !combatant.getUnitType().isMissile()
        )
            1f
        else 1 - (100 - combatant.getHealth()) / 300f// Each 3 points of health reduces damage dealt by 1% like original game
    }


    /**
     * Includes attack modifiers
     */
    private fun getAttackingStrength(
        attacker: ICombatant,
        tileToAttackFrom: TileInfo?,
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
        if (defender.getUnitType().isCivilian()) return 0
        val ratio =
            getAttackingStrength(attacker, tileToAttackFrom, defender) / getDefendingStrength(
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
            getAttackingStrength(attacker, tileToAttackFrom, defender) / getDefendingStrength(
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
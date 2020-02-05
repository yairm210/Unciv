package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unit.UnitType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class BattleDamageModifier(val vs:String,val modificationAmount:Float){
    fun getText(): String = "vs $vs"
}

class BattleDamage{

    private fun getBattleDamageModifiersOfUnit(unit:MapUnit): MutableList<BattleDamageModifier> {
        val modifiers = mutableListOf<BattleDamageModifier>()
        for (ability in unit.getUniques()) {
            // This beut allows us to have generic unit uniques: "Bonus vs City 75%", "Penatly vs Mounted 25%" etc.
            val regexResult = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""").matchEntire(ability)
            if (regexResult == null) continue
            val vs = regexResult.groups[2]!!.value
            val modificationAmount = regexResult.groups[3]!!.value.toFloat() / 100  // if it says 15%, that's 0.15f in modification
            if (regexResult.groups[1]!!.value == "Bonus")
                modifiers.add(BattleDamageModifier(vs, modificationAmount))
            else
                modifiers.add(BattleDamageModifier(vs, -modificationAmount))
        }
        return modifiers
    }


    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String, Float>()
        fun addToModifiers(BDM:BattleDamageModifier){
            val text = BDM.getText()
            if(!modifiers.containsKey(text)) modifiers[text]=0f
            modifiers[text]=modifiers[text]!!+BDM.modificationAmount
        }

        if (combatant is MapUnitCombatant) {
            for (BDM in getBattleDamageModifiersOfUnit(combatant.unit)) {
                if (BDM.vs == enemy.getUnitType().toString())
                    addToModifiers(BDM)
                if (BDM.vs == "wounded units" && enemy is MapUnitCombatant && enemy.getHealth() < 100)
                    addToModifiers(BDM)
                if (BDM.vs == "land units" && enemy.getUnitType().isLandUnit())
                    addToModifiers(BDM)
                if (BDM.vs == "water units" && enemy.getUnitType().isWaterUnit())
                    addToModifiers(BDM)
                if (BDM.vs == "air units" && enemy.getUnitType().isAirUnit())
                    addToModifiers(BDM)
            }

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            val civHappiness = combatant.getCivInfo().getHappiness()
            if (civHappiness < 0)
                modifiers["Unhappiness"] = max(0.02f * civHappiness, -0.9f) // otherwise it could exceed -100% and start healing enemy units...

            if (combatant.getCivInfo().policies.isAdopted("Populism") && combatant.getHealth() < 100) {
                modifiers["Populism"] = 0.25f
            }

            if (combatant.getCivInfo().policies.isAdopted("Discipline") && combatant.isMelee()
                    && combatant.getTile().neighbors.flatMap { it.getUnits() }
                            .any { it.civInfo == combatant.getCivInfo() && !it.type.isCivilian() && !it.type.isAirUnit() })
                modifiers["Discipline"] = 0.15f

            val requiredResource = combatant.unit.baseUnit.requiredResource
            if (requiredResource != null && combatant.getCivInfo().getCivResourcesByName()[requiredResource]!! < 0
                    && !combatant.getCivInfo().isBarbarian()) {
                modifiers["Missing resource"] = -0.25f
            }

            val nearbyCivUnits = combatant.unit.getTile().getTilesInDistance(2)
                    .filter { it.civilianUnit?.civInfo == combatant.unit.civInfo }
                    .map { it.civilianUnit }
            if (nearbyCivUnits.any { it!!.hasUnique("Bonus for units in 2 tile radius 15%") }) {
                val greatGeneralModifier = if (combatant.unit.civInfo.nation.unique ==
                        "Great general provides double combat bonus, and spawns 50% faster") 0.3f
                else 0.15f
                modifiers["Great General"] = greatGeneralModifier
            }

            if(combatant.getCivInfo().nation.unique=="Golden Ages last 50% longer. During a Golden Age, units receive +1 Movement and +10% Strength"
                    && combatant.getCivInfo().goldenAges.isGoldenAge())
                modifiers["Golden Age"] = 0.1f

            if (combatant.getCivInfo().nation.unique == "Combat Strength +30% when fighting City-State units or attacking a City-State itself. All mounted units have +1 Movement."
                    && enemy.getCivInfo().isCityState())
                modifiers["Mongol Terror"] = 0.3f
        }

        if (enemy.getCivInfo().isBarbarian()) {
            modifiers["Difficulty"] = combatant.getCivInfo().gameInfo.getDifficulty().barbarianBonus
            if (combatant.getCivInfo().policies.isAdopted("Honor"))
                modifiers["vs Barbarians"] = 0.25f
        }
        
        return modifiers
    }

    fun getAttackModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = getGeneralModifiers(attacker, defender)

        if(attacker is MapUnitCombatant) {
            modifiers.putAll(getTileSpecificModifiers(attacker,defender.getTile()))

            for (ability in attacker.unit.getUniques()) {
                val regexResult = Regex("""Bonus as Attacker [(\d*)]%""").matchEntire(ability) //to do: extend to defender, and penalyy
                if (regexResult == null) continue
                val bonus = regexResult.groups[1]!!.value.toFloat() / 100
                if (modifiers.containsKey("Attacker Bonus"))
                    modifiers["Attacker Bonus"] =modifiers["Attacker Bonus"]!! + bonus
                else modifiers["Attacker Bonus"] = bonus
            }

            if(attacker.unit.isEmbarked())
                modifiers["Landing"] = -0.5f

            if (attacker.isMelee()) {
                val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfAttackersSurroundingDefender > 1)
                    modifiers["Flanking"] = 0.1f * (numberOfAttackersSurroundingDefender-1) //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            }
        }

        else if (attacker is CityCombatant) {
            if (attacker.getCivInfo().policies.isAdopted("Oligarchy") && attacker.city.getCenterTile().militaryUnit != null)
                modifiers["Oligarchy"] = 0.5f
        }



        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: MapUnitCombatant): HashMap<String, Float> {
        val modifiers = HashMap<String, Float>()

        if (defender.unit.isEmbarked()) {
            // embarked units get no defensive modifiers apart from this unique
            if (defender.unit.hasUnique("Defense bonus when embarked") ||
                    defender.getCivInfo().nation.unique == "Receive triple Gold from Barbarian encampments and pillaging Cities. Embarked units can defend themselves.")
                modifiers["Embarkation"] = 1f

            return modifiers
        }

        modifiers.putAll(getGeneralModifiers(defender, attacker))

        modifiers.putAll(getTileSpecificModifiers(defender, defender.getTile()))

        if (!defender.unit.hasUnique("No defensive terrain bonus")) {
            val tileDefenceBonus = defender.getTile().getDefensiveBonus()
            if (tileDefenceBonus > 0) modifiers["Terrain"] = tileDefenceBonus
        }

        if(attacker.isRanged()) {
            val defenceVsRanged = 0.25f * defender.unit.getUniques().count { it == "+25% Defence against ranged attacks" }
            if (defenceVsRanged > 0) modifiers["defence vs ranged"] = defenceVsRanged
        }

        val carrierDefenceBonus = 0.25f * defender.unit.getUniques().count { it == "+25% Combat Bonus when defending" }
        if (carrierDefenceBonus > 0) modifiers["Armor Plating"] = carrierDefenceBonus


        if (defender.unit.isFortified())
            modifiers["Fortification"] = 0.2f * defender.unit.getFortificationTurns()

        return modifiers
    }

    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): HashMap<String,Float> {
        val modifiers = HashMap<String,Float>()
        val isFriendlyTerritory = tile.getOwner()!=null && !unit.getCivInfo().isAtWarWith(tile.getOwner()!!)
        if(isFriendlyTerritory && unit.getCivInfo().containsBuildingUnique("+15% combat strength for units fighting in friendly territory"))
            modifiers["Himeji Castle"] = 0.15f
        if(!isFriendlyTerritory && unit.unit.hasUnique("+20% bonus outside friendly territory"))
            modifiers["Foreign Land"] = 0.2f


        if(unit.getCivInfo().nation.unique == "Can embark and move over Coasts and Oceans immediately. +1 Sight when embarked. +10% Combat Strength bonus if within 2 tiles of a Moai."
                && tile.getTilesInDistance(2).any { it.improvement=="Moai" })
            modifiers["Moai"] = 0.1f

        if(tile.neighbors.flatMap { it.getUnits() }
                        .any { it.hasUnique("-10% combat strength for adjacent enemy units") && it.civInfo.isAtWarWith(unit.getCivInfo()) })
            modifiers["Haka War Dance"] = -0.1f


        if(unit.unit.hasUnique("+33% combat bonus in Forest/Jungle")
                && (tile.terrainFeature== Constants.forest || tile.terrainFeature==Constants.jungle))
            modifiers[tile.terrainFeature!!]=0.33f

        val isRoughTerrain = tile.isRoughTerrain()
        for (BDM in getBattleDamageModifiersOfUnit(unit.unit)) {
            val text = BDM.getText()
            if (BDM.vs == "units in open terrain" && !isRoughTerrain) {
                if (modifiers.containsKey(text))
                    modifiers[text] = modifiers[text]!! + BDM.modificationAmount
                else modifiers[text] = BDM.modificationAmount
            }
            if (BDM.vs == "units in rough terrain" && isRoughTerrain) {
                if (modifiers.containsKey(text))
                    modifiers[text] = modifiers[text]!! + BDM.modificationAmount
                else modifiers[text] = BDM.modificationAmount
            }
        }

        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: HashMap<String, Float>): Float {
        // modifiers are like 0.1 for a 10% bonus, -0.1 for a 10% loss
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier *= (1 + modifierValue)
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        return if(combatant.getUnitType() == UnitType.City
                || combatant.getCivInfo().nation.unique == "Units fight as though they were at full strength even when damaged" && !combatant.getUnitType().isAirUnit())
            1f
        else 1 - (100 - combatant.getHealth()) / 300f// Each 3 points of health reduces damage dealt by 1% like original game
    }


    /**
     * Includes attack modifiers
     */
    private fun getAttackingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker,defender))
        return attacker.getAttackingStrength() * attackModifier
    }


    /**
     * Includes defence modifiers
     */
    private fun getDefendingStrength(attacker: ICombatant, defender: ICombatant): Float {
        var defenceModifier = 1f
        if(defender is MapUnitCombatant) defenceModifier = modifiersToMultiplicationBonus(getDefenceModifiers(attacker,defender))
        return defender.getDefendingStrength() * defenceModifier
    }

    fun calculateDamageToAttacker(attacker: ICombatant, defender: ICombatant): Int {
        if(attacker.isRanged()) return 0
        if(defender.getUnitType().isCivilian()) return 0
        val ratio = getAttackingStrength(attacker,defender) / getDefendingStrength(attacker,defender)
        return (damageModifier(ratio, true) * getHealthDependantDamageRatio(defender)).roundToInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        val ratio = getAttackingStrength(attacker,defender) / getDefendingStrength(attacker,defender)
        return (damageModifier(ratio,false) * getHealthDependantDamageRatio(attacker)).roundToInt()
    }

    private fun damageModifier(attackerToDefenderRatio: Float, damageToAttacker:Boolean): Float {
        // https://forums.civfanatics.com/threads/getting-the-combat-damage-math.646582/#post-15468029
        val strongerToWeakerRatio = attackerToDefenderRatio.pow(if (attackerToDefenderRatio < 1) -1 else 1)
        var ratioModifier = ((((strongerToWeakerRatio + 3)/4).pow(4) +1)/2)
        if((damageToAttacker && attackerToDefenderRatio>1) || (!damageToAttacker && attackerToDefenderRatio<1)) // damage ratio from the weaker party is inverted
            ratioModifier = ratioModifier.pow(-1)
        val randomCenteredAround30 = (24 + 12 * Random().nextFloat())
        return  randomCenteredAround30 * ratioModifier
    }
}

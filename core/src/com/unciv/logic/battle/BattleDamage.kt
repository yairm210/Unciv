package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.unit.UnitType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class BattleDamageModifier(val vs:String, val modificationAmount:Float){
    fun getText(): String = "vs $vs"
}

object BattleDamage {

    const val BONUS_VS_UNIT_TYPE = """(Bonus|Penalty) vs (.*) (\d*)%"""

    private fun getBattleDamageModifiersOfUnit(unit:MapUnit): MutableList<BattleDamageModifier> {
        val modifiers = mutableListOf<BattleDamageModifier>()
        for (ability in unit.getUniques()) {
            // This beut allows us to have generic unit uniques: "Bonus vs City 75%", "Penatly vs Mounted 25%" etc.
            val regexResult = Regex(BONUS_VS_UNIT_TYPE).matchEntire(ability.text)
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


    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant): Counter<String> {
        val modifiers = Counter<String>()
        fun addToModifiers(BDM:BattleDamageModifier) =
                modifiers.add(BDM.getText(), (BDM.modificationAmount*100).toInt())

        val civInfo = combatant.getCivInfo()
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

            for (unique in combatant.unit.getMatchingUniques("+[]% Strength vs []")) {
                if (unique.params[1] == enemy.getName())
                    modifiers.add("vs [${unique.params[1]}]", unique.params[0].toInt())
            }

            for (unique in combatant.unit.getMatchingUniques("+[]% Combat Strength"))
                modifiers.add("Combat Strength", unique.params[0].toInt())

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            val civHappiness = civInfo.getHappiness()
            if (civHappiness < 0)
                modifiers["Unhappiness"] = max(2 * civHappiness, -90) // otherwise it could exceed -100% and start healing enemy units...

            if (civInfo.hasUnique("Wounded military units deal +25% damage") && combatant.getHealth() < 100) {
                modifiers["Wounded unit"] = 25
            }

            if (civInfo.hasUnique("+15% combat strength for melee units which have another military unit in an adjacent tile")
                    && combatant.isMelee()
                    && combatant.getTile().neighbors.flatMap { it.getUnits() }
                            .any { it.civInfo == civInfo && !it.type.isCivilian() && !it.type.isAirUnit() })
                modifiers["Discipline"] = 15

            val requiredResource = combatant.unit.baseUnit.requiredResource
            if (requiredResource != null && civInfo.getCivResourcesByName()[requiredResource]!! < 0
                    && !civInfo.isBarbarian()) {
                modifiers["Missing resource"] = -25
            }

            val nearbyCivUnits = combatant.unit.getTile().getTilesInDistance(2)
                    .filter { it.civilianUnit?.civInfo == combatant.unit.civInfo }
                    .map { it.civilianUnit }
            if (nearbyCivUnits.any { it!!.hasUnique("Bonus for units in 2 tile radius 15%") }) {
                val greatGeneralModifier = if (combatant.unit.civInfo.hasUnique("Great General provides double combat bonus")) 30 else 15
                modifiers["Great General"] = greatGeneralModifier
            }

            if(civInfo.goldenAges.isGoldenAge() && civInfo.hasUnique("+10% Strength for all units during Golden Age"))
                modifiers["Golden Age"] = 10

            if (enemy.getCivInfo().isCityState() && civInfo.hasUnique("+30% Strength when fighting City-State units and cities"))
                modifiers["vs [City-States]"] = 30

        }

        if (enemy.getCivInfo().isBarbarian()) {
            modifiers["Difficulty"] = (civInfo.gameInfo.getDifficulty().barbarianBonus*100).toInt()
            if (civInfo.hasUnique("+25% bonus vs Barbarians"))
                modifiers["vs Barbarians"] = 25
        }
        
        return modifiers
    }

    fun getAttackModifiers(attacker: ICombatant, tileToAttackFrom:TileInfo?, defender: ICombatant): Counter<String> {
        val modifiers = getGeneralModifiers(attacker, defender)

        if (attacker is MapUnitCombatant) {
            modifiers.add(getTileSpecificModifiers(attacker, defender.getTile()))

            for (ability in attacker.unit.getUniques()) {
                if (ability.placeholderText == "Bonus as Attacker []%")
                    modifiers.add("Attacker Bonus", ability.params[0].toInt())
            }

            if (attacker.unit.isEmbarked() && !attacker.unit.hasUnique("Amphibious"))
                modifiers["Landing"] = -50

            if (attacker.isMelee()) {
                val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                    it.militaryUnit != null
                            && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                            && MapUnitCombatant(it.militaryUnit!!).isMelee()
                }
                if (numberOfAttackersSurroundingDefender > 1)
                    modifiers["Flanking"] = 10 * (numberOfAttackersSurroundingDefender - 1) //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
                if (attacker.getTile().aerialDistanceTo(defender.getTile()) == 1 && attacker.getTile().isConnectedByRiver(defender.getTile())
                        && !attacker.unit.hasUnique("Amphibious")) {
                    if (!attacker.getTile().hasConnection(attacker.getCivInfo()) // meaning, the tiles are not road-connected for this civ
                            || !defender.getTile().hasConnection(attacker.getCivInfo())
                            || !attacker.getCivInfo().tech.roadsConnectAcrossRivers) {
                        modifiers["Across river"] = -20
                    }
                }
            }

            if (attacker.getCivInfo().policies.autocracyCompletedTurns > 0)
                modifiers["Autocracy Complete"] = 20

            if (defender is CityCombatant &&
                    attacker.getCivInfo().hasUnique("+15% Combat Strength for all units when attacking Cities"))
                modifiers["Statue of Zeus"] = 15
        } else if (attacker is CityCombatant) {
            if (attacker.getCivInfo().hasUnique("+50% attacking strength for cities with garrisoned units")
                    && attacker.city.getCenterTile().militaryUnit != null)
                modifiers["Oligarchy"] = 50
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: MapUnitCombatant): Counter<String> {
        val modifiers = getGeneralModifiers(defender, attacker)
        val tile = defender.getTile()

        if (defender.unit.isEmbarked()) {
            // embarked units get no defensive modifiers apart from this unique
            if (defender.unit.hasUnique("Defense bonus when embarked") ||
                    defender.getCivInfo().hasUnique("Embarked units can defend themselves"))
                modifiers["Embarked"] = 100

            return modifiers
        }

        modifiers.putAll(getTileSpecificModifiers(defender, tile))

        val tileDefenceBonus = tile.getDefensiveBonus()
        if (!defender.unit.hasUnique("No defensive terrain bonus") || tileDefenceBonus < 0)
            modifiers["Tile"] = (tileDefenceBonus*100).toInt()

        if (attacker.isRanged()) {
            val defenceVsRanged = 25 * defender.unit.getUniques().count { it.text == "+25% Defence against ranged attacks" }
            if (defenceVsRanged > 0) modifiers["defence vs ranged"] = defenceVsRanged
        }

        val carrierDefenceBonus = 25 * defender.unit.getUniques().count { it.text == "+25% Combat Bonus when defending" }
        if (carrierDefenceBonus > 0) modifiers["Armor Plating"] = carrierDefenceBonus

        for(unique in defender.unit.getMatchingUniques("+[]% defence in [] tiles")) {
            if (tile.fitsUniqueFilter(unique.params[1]))
                modifiers["[${unique.params[1]}] defence"] = unique.params[0].toInt()
        }


        if (defender.unit.isFortified())
            modifiers["Fortification"] = 20 * defender.unit.getFortificationTurns()

        return modifiers
    }

    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): Counter<String> {
        val modifiers = Counter<String>()

        // As of 3.11.0 This is to be deprecated and converted to "+[15]% combat bonus for units fighting in [Friendly Land]" - keeping it here to that mods with this can still work for now
        // Civ 5 does not use "Himeji Castle"
        if(tile.isFriendlyTerritory(unit.getCivInfo()) && unit.getCivInfo().hasUnique("+15% combat strength for units fighting in friendly territory"))
            modifiers.add("Friendly Land", 15)

        // As of 3.11.0 This is to be deprecated and converted to "+[20]% combat bonus in [Foreign Land]" - keeping it here to that mods with this can still work for now
        if(!tile.isFriendlyTerritory(unit.getCivInfo()) && unit.unit.hasUnique("+20% bonus outside friendly territory"))
            modifiers.add("Foreign Land", 20)

        for (unique in unit.unit.getMatchingUniques("+[]% combat bonus in []")
                + unit.getCivInfo().getMatchingUniques("+[]% combat bonus for units fighting in []")) {
            val filter = unique.params[1]
            if (filter == tile.getLastTerrain().name
                    || filter == "Foreign Land" && !tile.isFriendlyTerritory(unit.getCivInfo())
                    || filter == "Friendly Land" && tile.isFriendlyTerritory(unit.getCivInfo()))
                modifiers.add(filter, unique.params[0].toInt())
        }

        // As of 3.10.6 This is to be deprecated and converted to "+[]% combat bonus in []" - keeping it here to that mods with this can still work for now
        if (unit.unit.hasUnique("+25% bonus in Snow, Tundra and Hills") &&
                (tile.baseTerrain == Constants.snow
                        || tile.baseTerrain == Constants.tundra
                        || tile.isHill()) &&
                // except when there is a vegetation
                (tile.terrainFeature != Constants.forest
                        || tile.terrainFeature != Constants.jungle))
            modifiers[tile.baseTerrain] = 25

        for(unique in unit.getCivInfo().getMatchingUniques("+[]% Strength if within [] tiles of a []")) {
            if (tile.getTilesInDistance(unique.params[1].toInt()).any { it.improvement == unique.params[2] })
                modifiers[unique.params[2]] = unique.params[0].toInt()
        }

        if(tile.neighbors.flatMap { it.getUnits() }
                        .any { it.hasUnique("-10% combat strength for adjacent enemy units") && it.civInfo.isAtWarWith(unit.getCivInfo()) })
            modifiers["Haka War Dance"] = -10

        // As of 3.10.6 This is to be deprecated and converted to "+[]% combat bonus in []" - keeping it here to that mods with this can still work for now
        if(unit.unit.hasUnique("+33% combat bonus in Forest/Jungle")
                && (tile.terrainFeature== Constants.forest || tile.terrainFeature==Constants.jungle))
            modifiers[tile.terrainFeature!!]=33

        val isRoughTerrain = tile.isRoughTerrain()
        for (BDM in getBattleDamageModifiersOfUnit(unit.unit)) {
            val text = BDM.getText()
            // this will change when we change over everything to ints
            if (BDM.vs == "units in open terrain" && !isRoughTerrain) modifiers.add(text, (BDM.modificationAmount*100).toInt())
            if (BDM.vs == "units in rough terrain" && isRoughTerrain) modifiers.add(text, (BDM.modificationAmount*100).toInt())
        }

        return modifiers
    }

    fun Counter<String>.toOldModifiers(): HashMap<String, Float> {
        val modifiers = HashMap<String,Float>()
        for((key,value) in this) modifiers[key] = value.toFloat()/100
        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: Counter<String>): Float {
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier *= (1 + modifierValue/100) // so 25 will result in *= 1.25
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        return if (combatant.getUnitType() == UnitType.City
                || combatant.getCivInfo().hasUnique("Units fight as though they were at full strength even when damaged")
                && !combatant.getUnitType().isAirUnit())
            1f
        else 1 - (100 - combatant.getHealth()) / 300f// Each 3 points of health reduces damage dealt by 1% like original game
    }


    /**
     * Includes attack modifiers
     */
    private fun getAttackingStrength(attacker: ICombatant, tileToAttackFrom: TileInfo?, defender: ICombatant): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker,tileToAttackFrom, defender))
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

    fun calculateDamageToAttacker(attacker: ICombatant, tileToAttackFrom: TileInfo?, defender: ICombatant): Int {
        if(attacker.isRanged()) return 0
        if(defender.getUnitType().isCivilian()) return 0
        val ratio = getAttackingStrength(attacker, tileToAttackFrom, defender) / getDefendingStrength(attacker,defender)
        return (damageModifier(ratio, true) * getHealthDependantDamageRatio(defender)).roundToInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, tileToAttackFrom: TileInfo?, defender: ICombatant): Int {
        val ratio = getAttackingStrength(attacker,tileToAttackFrom, defender) / getDefendingStrength(attacker,defender)
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

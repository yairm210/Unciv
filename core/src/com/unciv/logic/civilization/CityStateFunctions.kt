package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.civilization.diplomacy.*
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.ui.victoryscreen.RankingType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.math.min
import kotlin.math.pow

/** Class containing city-state-specific functions */
class CityStateFunctions(val civInfo: CivilizationInfo) {

    /** Attempts to initialize the city state, returning true if successful. */
    fun initCityState(ruleset: Ruleset, startingEra: String, unusedMajorCivs: Collection<String>): Boolean {
        val cityStateType = ruleset.nations[civInfo.civName]?.cityStateType
            ?: return false

        val startingTechs = ruleset.technologies.values.filter { it.hasUnique(UniqueType.StartingTech) }
        for (tech in startingTechs)
            civInfo.tech.techsResearched.add(tech.name) // can't be .addTechnology because the civInfo isn't assigned yet

        val allMercantileResources = ruleset.tileResources.values.filter { it.hasUnique(UniqueType.CityStateOnlyResource) }.map { it.name }
        val allPossibleBonuses = HashSet<Unique>()    // We look through these to determine what kind of city state we are
        var fallback = false
        for (era in ruleset.eras.values) {
            if (era.undefinedCityStateBonuses()) {
                fallback = true
                break
            }
            val allyBonuses = era.getCityStateBonuses(cityStateType, RelationshipLevel.Ally)
            val friendBonuses = era.getCityStateBonuses(cityStateType, RelationshipLevel.Friend)
            allPossibleBonuses.addAll(allyBonuses)
            allPossibleBonuses.addAll(friendBonuses)
        }

        // CS Personality
        civInfo.cityStatePersonality = CityStatePersonality.values().random()

        // Mercantile bonus resources
        if (allPossibleBonuses.any { it.isOfType(UniqueType.CityStateUniqueLuxury) }
            || fallback && cityStateType == CityStateType.Mercantile) { // Fallback for badly defined Eras.json
            civInfo.cityStateResource = allMercantileResources.randomOrNull()
        }

        // Unique unit for militaristic city-states
        if (allPossibleBonuses.any { it.isOfType(UniqueType.CityStateMilitaryUnits) }
            || fallback && cityStateType == CityStateType.Militaristic // Fallback for badly defined Eras.json
        ) {

            val possibleUnits = ruleset.units.values.filter { it.requiredTech != null
                && ruleset.eras[ruleset.technologies[it.requiredTech!!]!!.era()]!!.eraNumber > ruleset.eras[startingEra]!!.eraNumber // Not from the start era or before
                && it.uniqueTo != null && it.uniqueTo in unusedMajorCivs // Must be from a major civ not in the game
                && ruleset.unitTypes[it.unitType]!!.isLandUnit() && ( it.strength > 0 || it.rangedStrength > 0 ) } // Must be a land military unit
            if (possibleUnits.isNotEmpty())
                civInfo.cityStateUniqueUnit = possibleUnits.random().name
        }

        // TODO: Return false if attempting to put a religious city-state in a game without religion

        return true
    }

    /** Gain a random great person from the city state */
    fun giveGreatPersonToPatron(receivingCiv: CivilizationInfo) {

        // Great Prophets can't be gotten from CS
        val giftableUnits = civInfo.gameInfo.ruleSet.units.values.filter { it.isGreatPerson()
                && !it.hasUnique(UniqueType.MayFoundReligion) }
        if (giftableUnits.isEmpty()) // For badly defined mods that don't have great people but do have the policy that makes city states grant them
            return
        val giftedUnit = giftableUnits.random()
        val cities = NextTurnAutomation.getClosestCities(receivingCiv, civInfo) ?: return
        val placedUnit = receivingCiv.placeUnitNearTile(cities.city1.location, giftedUnit.name)
            ?: return
        val locations = LocationAction(placedUnit.getTile().position, cities.city2.location)
        receivingCiv.addNotification( "[${civInfo.civName}] gave us a [${giftedUnit.name}] as a gift!", locations, civInfo.civName, giftedUnit.name)
    }

    fun giveMilitaryUnitToPatron(receivingCiv: CivilizationInfo) {
        val cities = NextTurnAutomation.getClosestCities(receivingCiv, civInfo) ?: return

        val city = cities.city1

        fun giftableUniqueUnit(): BaseUnit? {
            val uniqueUnit = civInfo.gameInfo.ruleSet.units[civInfo.cityStateUniqueUnit]
                ?: return null
            if (uniqueUnit.requiredTech != null && !receivingCiv.tech.isResearched(uniqueUnit.requiredTech!!))
                return null
            if (uniqueUnit.obsoleteTech != null && receivingCiv.tech.isResearched(uniqueUnit.obsoleteTech!!))
                return null
            return uniqueUnit
        }
        fun randomGiftableUnit() =
                city.cityConstructions.getConstructableUnits()
                .filter { !it.isCivilian() && it.isLandUnit() && it.uniqueTo == null }
                .toList().randomOrNull()
        val militaryUnit = giftableUniqueUnit() // If the receiving civ has discovered the required tech and not the obsolete tech for our unique, always give them the unique
            ?: randomGiftableUnit() // Otherwise pick at random
            ?: return  // That filter _can_ result in no candidates, if so, quit silently

        // placing the unit may fail - in that case stay quiet
        val placedUnit = receivingCiv.placeUnitNearTile(city.location, militaryUnit.name) ?: return

        // The unit should have bonuses from Barracks, Alhambra etc as if it was built in the CS capital
        militaryUnit.addConstructionBonuses(placedUnit, civInfo.getCapital()!!.cityConstructions)

        // Siam gets +10 XP for all CS units
        for (unique in receivingCiv.getMatchingUniques(UniqueType.CityStateGiftedUnitsStartWithXp)) {
            placedUnit.promotions.XP += unique.params[0].toInt()
        }

        // Point to the places mentioned in the message _in that order_ (debatable)
        val placedLocation = placedUnit.getTile().position
        val locations = LocationAction(placedLocation, cities.city2.location, city.location)
        receivingCiv.addNotification(
            "[${civInfo.civName}] gave us a [${militaryUnit.name}] as gift near [${city.name}]!",
            locations,
            civInfo.civName,
            militaryUnit.name
        )
    }

    fun influenceGainedByGift(donorCiv: CivilizationInfo, giftAmount: Int): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/aa29e80751f541ae04858b6d2a2c7dcca454201e/CvGameCoreDLL_Expansion1/CvMinorCivAI.cpp
        // line 8681 and below
        var influenceGained = giftAmount.toFloat().pow(1.01f) / 9.8f
        val speed = civInfo.gameInfo.speed
        val gameProgressApproximate = min(civInfo.gameInfo.turns / (400f * speed.modifier), 1f)
        influenceGained *= 1 - (2/3f) * gameProgressApproximate
        influenceGained *= speed.goldGiftModifier
        for (unique in donorCiv.getMatchingUniques(UniqueType.CityStateGoldGiftsProvideMoreInfluence))
            influenceGained *= 1f + unique.params[0].toFloat() / 100f

        // Bonus due to "Invest" quests
        influenceGained *= civInfo.questManager.getInvestmentMultiplier(donorCiv.civName)

        influenceGained -= influenceGained % 5
        if (influenceGained < 5f) influenceGained = 5f
        return influenceGained.toInt()
    }

    fun receiveGoldGift(donorCiv: CivilizationInfo, giftAmount: Int) {
        if (!civInfo.isCityState()) throw Exception("You can only gain influence with City-States!")
        donorCiv.addGold(-giftAmount)
        civInfo.addGold(giftAmount)
        civInfo.getDiplomacyManager(donorCiv).addInfluence(influenceGainedByGift(donorCiv, giftAmount).toFloat())
        civInfo.questManager.receivedGoldGift(donorCiv)
    }

    fun getProtectorCivs() : List<CivilizationInfo> {
        if(civInfo.isMajorCiv()) return emptyList()
        return civInfo.diplomacy.values
            .filter{ !it.otherCiv().isDefeated() && it.diplomaticStatus == DiplomaticStatus.Protector }
            .map{ it.otherCiv() }
    }

    fun addProtectorCiv(otherCiv: CivilizationInfo) {
        if(!otherCivCanPledgeProtection(otherCiv))
            return

        val diplomacy = civInfo.getDiplomacyManager(otherCiv.civName)
        diplomacy.diplomaticStatus = DiplomaticStatus.Protector
        diplomacy.setFlag(DiplomacyFlags.RecentlyPledgedProtection, 10) // Can't break for 10 turns
    }

    fun removeProtectorCiv(otherCiv: CivilizationInfo, forced: Boolean = false) {
        if(!forced && !otherCivCanWithdrawProtection(otherCiv))
            return

        val diplomacy = civInfo.getDiplomacyManager(otherCiv)
        diplomacy.diplomaticStatus = DiplomaticStatus.Peace
        diplomacy.setFlag(DiplomacyFlags.RecentlyWithdrewProtection, 20) // Can't re-pledge for 20 turns
        diplomacy.addInfluence(-20f)
    }

    fun otherCivCanPledgeProtection(otherCiv: CivilizationInfo): Boolean {
        // Must be a known city state
        if(!civInfo.isCityState() || !otherCiv.isMajorCiv() || otherCiv.isDefeated() || !civInfo.knows(otherCiv))
            return false
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)
        // Can't pledge too soon after withdrawing
        if (diplomacy.hasFlag(DiplomacyFlags.RecentlyWithdrewProtection))
            return false
        // Must have at least 0 influence
        if (diplomacy.getInfluence() < 0)
            return false
        // can't be at war
        if (civInfo.isAtWarWith(otherCiv))
            return false
        // Must not be protected already
        if (diplomacy.diplomaticStatus == DiplomaticStatus.Protector)
            return false
        return true
    }

    fun otherCivCanWithdrawProtection(otherCiv: CivilizationInfo): Boolean {
        // Must be a known city state
        if(!civInfo.isCityState() || !otherCiv.isMajorCiv() || otherCiv.isDefeated() || !civInfo.knows(otherCiv))
            return false
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)
        // Can't withdraw too soon after pledging
        if (diplomacy.hasFlag(DiplomacyFlags.RecentlyPledgedProtection))
            return false
        // Must be protected
        if (diplomacy.diplomaticStatus != DiplomaticStatus.Protector)
            return false
        return true
    }

    fun updateAllyCivForCityState() {
        var newAllyName: String? = null
        if (!civInfo.isCityState()) return
        val maxInfluence = civInfo.diplomacy
            .filter { !it.value.otherCiv().isCityState() && !it.value.otherCiv().isDefeated() }
            .maxByOrNull { it.value.getInfluence() }
        if (maxInfluence != null && maxInfluence.value.getInfluence() >= 60) {
            newAllyName = maxInfluence.key
        }

        if (civInfo.getAllyCiv() != newAllyName) {
            val oldAllyName = civInfo.getAllyCiv()
            civInfo.setAllyCiv(newAllyName)

            // If the city-state is captured by a civ, it stops being the ally of the civ it was previously an ally of.
            //  This means that it will NOT HAVE a capital at that time, so if we run getCapital we'll get a crash!
            val capitalLocation = if (civInfo.cities.isNotEmpty() && civInfo.getCapital() != null) civInfo.getCapital()!!.location else null

            if (newAllyName != null) {
                val newAllyCiv = civInfo.gameInfo.getCivilization(newAllyName)
                val text = "We have allied with [${civInfo.civName}]."
                if (capitalLocation != null) newAllyCiv.addNotification(text, capitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else newAllyCiv.addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
                newAllyCiv.updateViewableTiles()
                newAllyCiv.updateDetailedCivResources()
                for (unique in newAllyCiv.getMatchingUniques(UniqueType.CityStateCanBeBoughtForGold))
                    newAllyCiv.getDiplomacyManager(civInfo.civName).setFlag(DiplomacyFlags.MarriageCooldown, unique.params[0].toInt())

                // Join the wars of our new ally - loop through all civs they are at war with
                for (newEnemy in civInfo.gameInfo.civilizations.filter { it.isAtWarWith(newAllyCiv) && it.isAlive() } ) {
                    if (civInfo.knows(newEnemy) && !civInfo.isAtWarWith(newEnemy))
                        civInfo.getDiplomacyManager(newEnemy).declareWar()
                    else if (!civInfo.knows(newEnemy)) {
                        // We have to meet first
                        civInfo.makeCivilizationsMeet(newEnemy, warOnContact = true)
                        civInfo.getDiplomacyManager(newEnemy).declareWar()
                    }
                }
            }
            if (oldAllyName != null) {
                val oldAllyCiv = civInfo.gameInfo.getCivilization(oldAllyName)
                val text = "We have lost alliance with [${civInfo.civName}]."
                if (capitalLocation != null) oldAllyCiv.addNotification(text, capitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else oldAllyCiv.addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
                oldAllyCiv.updateViewableTiles()
                oldAllyCiv.updateDetailedCivResources()
            }
        }
    }

    fun getDiplomaticMarriageCost(): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvMinorCivAI.cpp, line 7812
        var cost = (500 * civInfo.gameInfo.speed.goldCostModifier).toInt()
        // Plus disband value of all units
        for (unit in civInfo.getCivUnits()) {
            cost += unit.baseUnit.getDisbandGold(civInfo)
        }
        // Round to lower multiple of 5
        cost /= 5
        cost *= 5

        return cost
    }

    fun canBeMarriedBy(otherCiv: CivilizationInfo): Boolean {
        return (!civInfo.isDefeated()
                && civInfo.isCityState()
                && civInfo.cities.any()
                && civInfo.getDiplomacyManager(otherCiv).relationshipLevel() == RelationshipLevel.Ally
                && !otherCiv.getDiplomacyManager(civInfo).hasFlag(DiplomacyFlags.MarriageCooldown)
                && otherCiv.getMatchingUniques(UniqueType.CityStateCanBeBoughtForGold).any()
                && otherCiv.gold >= getDiplomaticMarriageCost())

    }

    fun diplomaticMarriage(otherCiv: CivilizationInfo) {
        if (!canBeMarriedBy(otherCiv))  // Just in case
            return

        otherCiv.addGold(-getDiplomaticMarriageCost())
        otherCiv.addNotification("We have married into the ruling family of [${civInfo.civName}], bringing them under our control.",
            civInfo.getCapital()!!.location, civInfo.civName, NotificationIcon.Diplomacy, otherCiv.civName)
        for (civ in civInfo.gameInfo.civilizations.filter { it != otherCiv })
            civ.addNotification("[${otherCiv.civName}] has married into the ruling family of [${civInfo.civName}], bringing them under their control.",
                civInfo.getCapital()!!.location, civInfo.civName, NotificationIcon.Diplomacy, otherCiv.civName)
        for (unit in civInfo.getCivUnits())
            unit.gift(otherCiv)

        // Make sure this CS can never be liberated
        civInfo.gameInfo.getCities().filter {
            it.foundingCiv == civInfo.civName
        }.forEach {
            it.foundingCiv = ""
            it.isOriginalCapital = false
        }

        for (city in civInfo.cities) {
            city.moveToCiv(otherCiv)
            city.isPuppet = true // Human players get a popup that allows them to annex instead
        }
        civInfo.destroy()
    }

    fun getTributeWillingness(demandingCiv: CivilizationInfo, demandingWorker: Boolean = false): Int {
        return getTributeModifiers(demandingCiv, demandingWorker).values.sum()
    }

    fun getTributeModifiers(demandingCiv: CivilizationInfo, demandingWorker: Boolean = false, requireWholeList: Boolean = false): HashMap<String, Int> {
        val modifiers = LinkedHashMap<String, Int>()    // Linked to preserve order when presenting the modifiers table
        // Can't bully major civs or unsettled CS's
        if (!civInfo.isCityState()) {
            modifiers["Major Civ"] = -999
            return modifiers
        }
        if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) {
            modifiers["No Cities"] = -999
            return  modifiers
        }

        modifiers["Base value"] = -110

        if (civInfo.cityStatePersonality == CityStatePersonality.Hostile)
            modifiers["Hostile"] = -10
        if (civInfo.cityStateType == CityStateType.Militaristic)
            modifiers["Militaristic"] = -10
        if (civInfo.getAllyCiv() != null && civInfo.getAllyCiv() != demandingCiv.civName)
            modifiers["Has Ally"] = -10
        if (getProtectorCivs().any { it != demandingCiv })
            modifiers["Has Protector"] = -20
        if (demandingWorker)
            modifiers["Demanding a Worker"] = -30
        if (demandingWorker && civInfo.getCapital()!!.population.population < 4)
            modifiers["Demanding a Worker from small City-State"] = -300
        val recentBullying = civInfo.getRecentBullyingCountdown()
        if (recentBullying != null && recentBullying > 10)
            modifiers["Very recently paid tribute"] = -300
        else if (recentBullying != null && recentBullying > 0)
            modifiers["Recently paid tribute"] = -40
        if (civInfo.getDiplomacyManager(demandingCiv).getInfluence() < -30)
            modifiers["Influence below -30"] = -300

        // Slight optimization, we don't do the expensive stuff if we have no chance of getting a >= 0 result
        if (!requireWholeList && modifiers.values.sum() < -200)
            return modifiers

        val forceRank = civInfo.gameInfo.getAliveMajorCivs().sortedByDescending { it.getStatForRanking(
            RankingType.Force) }.indexOf(demandingCiv)
        modifiers["Military Rank"] = 100 - ((100 / civInfo.gameInfo.gameParameters.players.size) * forceRank)

        if (!requireWholeList && modifiers.values.sum() < -100)
            return modifiers

        val bullyRange = (civInfo.gameInfo.tileMap.tileMatrix.size / 10).coerceIn(5, 10)   // Longer range for larger maps
        val inRangeTiles = civInfo.getCapital()!!.getCenterTile().getTilesInDistanceRange(1..bullyRange)
        val forceNearCity = inRangeTiles
            .sumOf { if (it.militaryUnit?.civInfo == demandingCiv)
                    it.militaryUnit!!.getForceEvaluation()
                else 0
            }
        val csForce = civInfo.getCapital()!!.getForceEvaluation() + inRangeTiles
            .sumOf { if (it.militaryUnit?.civInfo == civInfo)
                    it.militaryUnit!!.getForceEvaluation()
                else 0
            }
        val forceRatio = forceNearCity.toFloat() / csForce.toFloat()

        modifiers["Military near City-State"] = when {
            forceRatio > 3f -> 100
            forceRatio > 2f -> 80
            forceRatio > 1.5f -> 60
            forceRatio > 1f -> 40
            forceRatio > 0.5f -> 20
            else -> 0
        }

        return modifiers
    }

    fun goldGainedByTribute(): Int {
        // These values are close enough, linear increase throughout the game
        var gold = (10 * civInfo.gameInfo.speed.goldGiftModifier).toInt() * 5 // rounding down to nearest 5
        val turnsToIncrement = civInfo.gameInfo.speed.cityStateTributeScalingInterval
        gold += 5 * (civInfo.gameInfo.turns / turnsToIncrement).toInt()

        return gold
    }

    fun tributeGold(demandingCiv: CivilizationInfo) {
        if (!civInfo.isCityState()) throw Exception("You can only demand gold from City-States!")
        val goldAmount = goldGainedByTribute()
        demandingCiv.addGold(goldAmount)
        civInfo.getDiplomacyManager(demandingCiv).addInfluence(-15f)
        cityStateBullied(demandingCiv)
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
    }

    fun tributeWorker(demandingCiv: CivilizationInfo) {
        if (!civInfo.isCityState()) throw Exception("You can only demand workers from City-States!")

        val buildableWorkerLikeUnits = civInfo.gameInfo.ruleSet.units.filter {
            it.value.hasUnique(UniqueType.BuildImprovements) &&
                it.value.isCivilian() && it.value.isBuildable(civInfo)
        }
        if (buildableWorkerLikeUnits.isEmpty()) return  // Bad luck?
        demandingCiv.placeUnitNearTile(civInfo.getCapital()!!.location, buildableWorkerLikeUnits.keys.random())

        civInfo.getDiplomacyManager(demandingCiv).addInfluence(-50f)
        cityStateBullied(demandingCiv)
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
    }

    fun canGiveStat(statType: Stat): Boolean {
        if (!civInfo.isCityState())
            return false
        val eraInfo = civInfo.getEra()
        if (!eraInfo.undefinedCityStateBonuses()) {
            // Defined city states in json
            for (bonus in eraInfo.getCityStateBonuses(civInfo.cityStateType, RelationshipLevel.Ally)) {
                if (bonus.stats[statType] > 0 || (bonus.isOfType(UniqueType.CityStateHappiness) && statType == Stat.Happiness))
                    return true
            }
        } else {
            // compatibility mode
            return when {
                civInfo.cityStateType == CityStateType.Mercantile && statType == Stat.Happiness -> true
                civInfo.cityStateType == CityStateType.Cultured && statType == Stat.Culture -> true
                civInfo.cityStateType == CityStateType.Maritime && statType == Stat.Food -> true
                civInfo.cityStateType == CityStateType.Religious && statType == Stat.Faith ->true
                else -> false
            }
        }

        return false
    }

    fun updateDiplomaticRelationshipForCityState() {
        // Check if city-state invaded by other civs
        if (getNumThreateningBarbarians() > 0) return // Assume any players are there to fight barbarians

        for (otherCiv in civInfo.getKnownCivs().filter { it.isMajorCiv() }) {
            if (civInfo.isAtWarWith(otherCiv)) continue
            if (otherCiv.hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)) continue
            val diplomacy = civInfo.getDiplomacyManager(otherCiv)
            if (diplomacy.hasFlag(DiplomacyFlags.AngerFreeIntrusion)) continue // They recently helped us

            val unitsInBorder = otherCiv.getCivUnits().count { !it.isCivilian() && it.getTile().getOwner() == civInfo }
            if (unitsInBorder > 0 && diplomacy.relationshipLevel() < RelationshipLevel.Friend) {
                diplomacy.addInfluence(-10f)
                if (!diplomacy.hasFlag(DiplomacyFlags.BorderConflict)) {
                    otherCiv.popupAlerts.add(PopupAlert(AlertType.BorderConflict, civInfo.civName))
                    diplomacy.setFlag(DiplomacyFlags.BorderConflict, 10)
                }
            }
        }
    }

    fun getFreeTechForCityState() {
        // City-States automatically get all techs that at least half of the major civs know
        val researchableTechs = civInfo.gameInfo.ruleSet.technologies.keys
            .filter { civInfo.tech.canBeResearched(it) }
        for (tech in researchableTechs) {
            val aliveMajorCivs = civInfo.gameInfo.getAliveMajorCivs()
            if (aliveMajorCivs.count { it.tech.isResearched(tech) } >= aliveMajorCivs.size / 2)
                civInfo.tech.addTechnology(tech)
        }
        return
    }

    fun getNumThreateningBarbarians(): Int {
        if (civInfo.gameInfo.gameParameters.noBarbarians) return 0
        val barbarianCiv = civInfo.gameInfo.civilizations.firstOrNull { it.isBarbarian() }
            ?: return 0
        return barbarianCiv.getCivUnits().count { it.threatensCiv(civInfo) }
    }

    fun threateningBarbarianKilledBy(otherCiv: CivilizationInfo) {
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)
        if (diplomacy.diplomaticStatus == DiplomaticStatus.War) return // No reward for enemies

        diplomacy.addInfluence(12f)

        if (diplomacy.hasFlag(DiplomacyFlags.AngerFreeIntrusion))
            diplomacy.setFlag(DiplomacyFlags.AngerFreeIntrusion, diplomacy.getFlag(DiplomacyFlags.AngerFreeIntrusion) + 5)
        else
            diplomacy.setFlag(DiplomacyFlags.AngerFreeIntrusion, 5)

        otherCiv.addNotification("[${civInfo.civName}] is grateful that you killed a Barbarian that was threatening them!",
            DiplomacyAction(civInfo.civName), civInfo.civName)
    }

    /** A city state was bullied. What are its protectors going to do about it??? */
    private fun cityStateBullied(bully: CivilizationInfo) {
        if (!civInfo.isCityState()) return // What are we doing here?

        for (protector in civInfo.getProtectorCivs()) {
            if (!protector.knows(bully)) // Who?
                continue
            val protectorDiplomacy = protector.getDiplomacyManager(bully)
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.BulliedProtectedMinor)
                && protectorDiplomacy.getFlag(DiplomacyFlags.RememberBulliedProtectedMinor) > 50)
                protectorDiplomacy.addModifier(DiplomaticModifiers.BulliedProtectedMinor, -10f) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.BulliedProtectedMinor, -15f)
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberBulliedProtectedMinor, 75)    // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                bully.addNotification("[${protector.civName}] is upset that you demanded tribute from [${civInfo.civName}], whom they have pledged to protect!",
                    NotificationIcon.Diplomacy, protector.civName)
            else    // Let humans choose who to side with
                protector.popupAlerts.add(PopupAlert(AlertType.BulliedProtectedMinor,
                    bully.civName + "@" + civInfo.civName))   // we need to pass both civs as argument, hence the horrible chimera
        }

        // Set a diplomatic flag so we remember for future quests (and not to give them any)
        civInfo.getDiplomacyManager(bully).setFlag(DiplomacyFlags.Bullied, 20)

        // Notify all City-States that we were bullied (for quests)
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.cityStateBullied(civInfo, bully) }
    }

    /** A city state was attacked. What are its protectors going to do about it??? Also checks for Wary */
    fun cityStateAttacked(attacker: CivilizationInfo) {
        if (!civInfo.isCityState()) return // What are we doing here?
        if (attacker.isCityState()) return // City states can't be upset with each other

        // We might become wary!
        if (attacker.isMinorCivWarmonger()) { // They've attacked a lot of city-states
            civInfo.getDiplomacyManager(attacker).becomeWary()
        }
        else if (attacker.isMinorCivAggressor()) { // They've attacked a few
            if (Random().nextBoolean()) { // 50% chance
                civInfo.getDiplomacyManager(attacker).becomeWary()
            }
        }
        // Others might become wary!
        if (attacker.isMinorCivAggressor()) {
            for (cityState in civInfo.gameInfo.getAliveCityStates()) {
                if (cityState == civInfo) // Must be a different minor
                    continue
                if (cityState.getAllyCiv() == attacker.civName) // Must not be allied to the attacker
                    continue
                if (!cityState.knows(attacker)) // Must have met
                    continue
                if (cityState.questManager.wantsDead(civInfo.civName))  // Must not want us dead
                    continue

                var probability: Int
                if (attacker.isMinorCivWarmonger()) {
                    // High probability if very aggressive
                    probability = when (cityState.getProximity(attacker)) {
                        Proximity.Neighbors -> 100
                        Proximity.Close     -> 75
                        Proximity.Far       -> 50
                        Proximity.Distant   -> 25
                        else                -> 0
                    }
                } else {
                    // Lower probability if only somewhat aggressive
                    probability = when (cityState.getProximity(attacker)) {
                        Proximity.Neighbors -> 50
                        Proximity.Close     -> 20
                        else                -> 0
                    }
                }

                // Higher probability if already at war
                if (cityState.isAtWarWith(attacker))
                    probability += 50

                if (Random().nextInt(100) <= probability) {
                    cityState.getDiplomacyManager(attacker).becomeWary()
                }
            }
        }

        for (protector in civInfo.getProtectorCivs()) {
            if (!protector.knows(attacker)) // Who?
                continue
            val protectorDiplomacy = protector.getDiplomacyManager(attacker)
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.AttackedProtectedMinor)
                && protectorDiplomacy.getFlag(DiplomacyFlags.RememberAttackedProtectedMinor) > 50)
                protectorDiplomacy.addModifier(DiplomaticModifiers.AttackedProtectedMinor, -15f) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.AttackedProtectedMinor, -20f)
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberAttackedProtectedMinor, 75)   // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                attacker.addNotification("[${protector.civName}] is upset that you attacked [${civInfo.civName}], whom they have pledged to protect!",
                    NotificationIcon.Diplomacy, protector.civName)
            else    // Let humans choose who to side with
                protector.popupAlerts.add(PopupAlert(AlertType.AttackedProtectedMinor,
                    attacker.civName + "@" + civInfo.civName))   // we need to pass both civs as argument, hence the horrible chimera
        }

        // Set up war with major pseudo-quest
        civInfo.questManager.wasAttackedBy(attacker)
        civInfo.getDiplomacyManager(attacker).setFlag(DiplomacyFlags.RecentlyAttacked, 2) // Reminder to ask for unit gifts in 2 turns
    }

    /** A city state was destroyed. Its protectors are going to be upset! */
    fun cityStateDestroyed(attacker: CivilizationInfo) {
        if (!civInfo.isCityState()) return // What are we doing here?

        for (protector in civInfo.getProtectorCivs()) {
            if (!protector.knows(attacker)) // Who?
                continue
            val protectorDiplomacy = protector.getDiplomacyManager(attacker)
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.DestroyedProtectedMinor))
                protectorDiplomacy.addModifier(DiplomaticModifiers.DestroyedProtectedMinor, -10f) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.DestroyedProtectedMinor, -40f) // Oof
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberDestroyedProtectedMinor, 125)   // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                attacker.addNotification("[${protector.civName}] is outraged that you destroyed [${civInfo.civName}], whom they had pledged to protect!",
                    NotificationIcon.Diplomacy, protector.civName)
            protector.addNotification("[${attacker.civName}] has destroyed [${civInfo.civName}], whom you had pledged to protect!", attacker.civName,
                NotificationIcon.Death, civInfo.civName)
        }

        // Notify all City-States that we were killed (for quest completion)
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.cityStateConquered(civInfo, attacker) }
    }

    /** Asks all met majors that haven't yet declared wor on [attacker] to at least give some units */
    fun askForUnitGifts(attacker: CivilizationInfo) {
        if (attacker.isDefeated() || civInfo.isDefeated()) // nevermind, someone died
            return
        if (civInfo.cities.isEmpty()) // Can't receive units with no cities
            return

        for (thirdCiv in civInfo.getKnownCivs()
            .filter { it != attacker && it.isAlive() && it.knows(attacker) && !it.isAtWarWith(attacker) && it.isMajorCiv() }
        ) {
            thirdCiv.addNotification(
                "[${civInfo.civName}] is being attacked by [${attacker.civName}] and asks all major civilizations to help them out by gifting them military units.",
                civInfo.getCapital()!!.location,
                civInfo.civName,
                "OtherIcons/Present",
            )
        }
    }

    fun getCityStateResourcesForAlly() = ResourceSupplyList().apply {
        for (city in civInfo.cities) {
            // IGNORE the fact that they consume their own resources - #4769
            addPositiveByResource(city.getCityResources(), Constants.cityStates)
        }
    }
}

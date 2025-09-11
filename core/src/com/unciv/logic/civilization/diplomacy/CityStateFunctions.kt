package com.unciv.logic.civilization.diplomacy

import com.unciv.Constants
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.managers.SpyFleeReason
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.Proximity
import com.unciv.models.Spy
import com.unciv.models.SpyAction
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.utils.randomWeighted
import yairm210.purity.annotations.Readonly
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Class containing city-state-specific functions */
class CityStateFunctions(val civInfo: Civilization) {

    /** Attempts to initialize the city state, returning true if successful. */
    fun initCityState(ruleset: Ruleset, startingEra: String, usedMajorCivs: Sequence<String>): Boolean {
        val allMercantileResources = ruleset.tileResources.values.filter { it.hasUnique(UniqueType.CityStateOnlyResource) }.map { it.name }
        val uniqueTypes = HashSet<UniqueType>()    // We look through these to determine what kinds of city states we have

        val nation = ruleset.nations[civInfo.civName]!!
        val cityStateType = ruleset.cityStateTypes[nation.cityStateType]!!
        uniqueTypes.addAll(cityStateType.friendBonusUniqueMap.getAllUniques().mapNotNull { it.type })
        uniqueTypes.addAll(cityStateType.allyBonusUniqueMap.getAllUniques().mapNotNull { it.type })

        // CS Personality
        civInfo.cityStatePersonality = CityStatePersonality.entries.random()

        // Mercantile bonus resources

        if (uniqueTypes.contains(UniqueType.CityStateUniqueLuxury)) {
            civInfo.cityStateResource = allMercantileResources.randomOrNull()
        }

        // Unique unit for militaristic city-states
        if (uniqueTypes.contains(UniqueType.CityStateMilitaryUnits)) {
            val possibleUnits = ruleset.units.values.filter {
                return@filter !it.availableInEra(ruleset, startingEra) // Not from the start era or before
                    && it.uniqueTo != null && it.uniqueTo !in usedMajorCivs // Must be from a major civ not in the game
                        // Note that this means that units unique to a civ *filter* instead of a civ *name* will not be provided
                    && ruleset.nations[it.uniqueTo]?.isMajorCiv == true // don't take unique units from other city states / barbs
                    && ruleset.unitTypes[it.unitType]!!.isLandUnit()
                    && (it.strength > 0 || it.rangedStrength > 0) // Must be a land military unit
            }
            if (possibleUnits.isNotEmpty())
                civInfo.cityStateUniqueUnit = possibleUnits.random().name
        }

        // TODO: Return false if attempting to put a religious city-state in a game without religion

        return true
    }

    fun holdElections() {
        civInfo.addFlag(CivFlags.TurnsTillCityStateElection.name, civInfo.gameInfo.ruleset.modOptions.constants.cityStateElectionTurns)
        val capital = civInfo.getCapital() ?: return

        val spies = capital.espionage.getAllStationedSpies().filter { it.action == SpyAction.RiggingElections }
        if (spies.isEmpty()) return

        fun getVotesFromSpy(spy: Spy?): Float {
            if (spy == null) return 20f
            var votes = (civInfo.getDiplomacyManagerOrMeet(spy.civInfo).influence / 2)
            votes += (spy.getSkillModifierPercent() * spy.getEfficiencyModifier()).toFloat() // ranges from 30 to 90
            return votes
        }

        val parties: MutableList<Spy?> = spies.toMutableList()
        parties.add(null) // Null spy is a neuteral party in the election
        val randomSeed = capital.location.x * capital.location.y + 123f * civInfo.gameInfo.turns
        val winner: Civilization? = parties.randomWeighted(Random(randomSeed.toInt()))  { getVotesFromSpy(it) }?.civInfo

        // There may be no winner, in that case all spies will loose 5 influence
        if (winner != null) {
            val allyCiv = civInfo.getAllyCiv()

            // Winning civ gets influence and all others loose influence
            for (civ in civInfo.getKnownCivs().toList()) {
                val influence = if (civ == winner) 20f else -5f
                civInfo.getDiplomacyManager(civ)!!.addInfluence(influence)
                if (civ == winner)  {
                    civ.addNotification("Your spy successfully rigged the election in [${civInfo.civName}]!", capital.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                } else if (spies.any { it.civInfo == civ}) {
                    civ.addNotification("Your spy lost the election in [${civInfo.civName}] to [${winner.civName}]!", capital.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                } else if (civ == allyCiv) {
                    // If the previous ally has no spy in the city then we should notify them
                    allyCiv.addNotification("The election in [${civInfo.civName}] was rigged by [${winner.civName}]!", capital.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                }
            }

        } else {
            // No spy won the election, the civs that tried to rig the election loose influence
            for (spy in spies) {
                civInfo.getDiplomacyManager(spy.civInfo)!!.addInfluence(-5f)
                spy.civInfo.addNotification("Your spy lost the election in [$capital]!", capital.location, NotificationCategory.Espionage, NotificationIcon.Spy)
            }
        }
    }

    @Readonly
    fun turnsForGreatPersonFromCityState(): Int = ((37 + Random.Default.nextInt(7)) * civInfo.gameInfo.speed.modifier).toInt()

    /** Gain a random great person from the city state */
    fun giveGreatPersonToPatron(receivingCiv: Civilization) {

        // Great Prophets can't be gotten from CS
        val giftableUnits = civInfo.gameInfo.ruleset.units.values.filter { it.isGreatPerson
                && !it.hasUnique(UniqueType.MayFoundReligion) }
        if (giftableUnits.isEmpty()) // For badly defined mods that don't have great people but do have the policy that makes city states grant them
            return
        val giftedUnit = giftableUnits.random()
        val cities = NextTurnAutomation.getClosestCities(receivingCiv, civInfo) ?: return
        val placedUnit = receivingCiv.units.placeUnitNearTile(cities.city1.location, giftedUnit)
            ?: return
        val locations = LocationAction(placedUnit.getTile().position, cities.city2.location)
        receivingCiv.addNotification( "[${civInfo.civName}] gave us a [${giftedUnit.name}] as a gift!", locations,
            NotificationCategory.Units, civInfo.civName, giftedUnit.name)
    }

    fun giveMilitaryUnitToPatron(receivingCiv: Civilization) {
        val cities = NextTurnAutomation.getClosestCities(receivingCiv, civInfo) ?: return

        val city = cities.city1

        @Readonly
        fun giftableUniqueUnit(): BaseUnit? {
            val uniqueUnit = civInfo.gameInfo.ruleset.units[civInfo.cityStateUniqueUnit]
                ?: return null
            if (!receivingCiv.tech.isResearched(uniqueUnit))
                return null
            if (receivingCiv.tech.isObsolete(uniqueUnit))
                return null
            return uniqueUnit
        }
        @Readonly
        fun randomGiftableUnit() =
                city.cityConstructions.getConstructableUnits()
                .filter { !it.isCivilian() && it.isLandUnit && it.uniqueTo == null }
                // Does not make us go over any resource quota
                .filter { it.getResourceRequirementsPerTurn(receivingCiv.state).none {
                    it.value > 0 && receivingCiv.getResourceAmount(it.key) < it.value
                } }
                .toList().randomOrNull()
        
        val militaryUnit = giftableUniqueUnit() // If the receiving civ has discovered the required tech and not the obsolete tech for our unique, always give them the unique
            ?: randomGiftableUnit() // Otherwise pick at random
            ?: return  // That filter _can_ result in no candidates, if so, quit silently

        // placing the unit may fail - in that case stay quiet
        val placedUnit = receivingCiv.units.placeUnitNearTile(city.location, militaryUnit.name) ?: return

        // The unit should have bonuses from Barracks, Alhambra etc as if it was built in the CS capital
        militaryUnit.addConstructionBonuses(placedUnit, civInfo.getCapital()!!.cityConstructions)

        // Siam gets +10 XP for all CS units
        for (unique in receivingCiv.getMatchingUniques(UniqueType.CityStateGiftedUnitsStartWithXp)) {
            placedUnit.promotions.XP += unique.params[0].toInt()
        }

        // Point to the gifted unit, then to the other places mentioned in the message
        val unitAction = sequenceOf(MapUnitAction(placedUnit))
        val notificationActions = unitAction + LocationAction(cities.city2.location, city.location)
        receivingCiv.addNotification(
            "[${civInfo.civName}] gave us a [${militaryUnit.name}] as gift near [${city.name}]!",
            notificationActions,
            NotificationCategory.Units,
            civInfo.civName,
            militaryUnit.name
        )
    }

    @Readonly
    fun influenceGainedByGift(donorCiv: Civilization, giftAmount: Int): Int {
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

    fun receiveGoldGift(donorCiv: Civilization, giftAmount: Int) {
        if (!civInfo.isCityState) throw Exception("You can only gain influence with City-States!")
        donorCiv.addGold(-giftAmount)
        civInfo.addGold(giftAmount)
        civInfo.getDiplomacyManager(donorCiv)!!.addInfluence(influenceGainedByGift(donorCiv, giftAmount).toFloat())
        civInfo.questManager.receivedGoldGift(donorCiv)
    }

    @Readonly
    fun getProtectorCivs() : List<Civilization> {
        if(civInfo.isMajorCiv()) return emptyList()
        return civInfo.diplomacy.values
            .filter{ !it.otherCiv().isDefeated() && it.diplomaticStatus == DiplomaticStatus.Protector }
            .map{ it.otherCiv() }
    }

    fun addProtectorCiv(otherCiv: Civilization) {
        if(!otherCivCanPledgeProtection(otherCiv))
            return

        val diplomacy = civInfo.getDiplomacyManager(otherCiv.civName)!!
        diplomacy.diplomaticStatus = DiplomaticStatus.Protector
        diplomacy.setFlag(DiplomacyFlags.RecentlyPledgedProtection, 10) // Can't break for 10 turns
    }

    fun removeProtectorCiv(otherCiv: Civilization, forced: Boolean = false) {
        if(!forced && !otherCivCanWithdrawProtection(otherCiv))
            return

        val diplomacy = civInfo.getDiplomacyManager(otherCiv)!!
        diplomacy.diplomaticStatus = DiplomaticStatus.Peace
        diplomacy.setFlag(DiplomacyFlags.RecentlyWithdrewProtection, 20) // Can't re-pledge for 20 turns
        diplomacy.addInfluence(-20f)
    }

    @Readonly
    fun otherCivCanPledgeProtection(otherCiv: Civilization): Boolean {
        // Must be a known city state
        if(!civInfo.isCityState || !otherCiv.isMajorCiv() || otherCiv.isDefeated() || !civInfo.knows(otherCiv))
            return false
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)!!
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

    @Readonly
    fun otherCivCanWithdrawProtection(otherCiv: Civilization): Boolean {
        // Must be a known city state
        if(!civInfo.isCityState || !otherCiv.isMajorCiv() || otherCiv.isDefeated() || !civInfo.knows(otherCiv))
            return false
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)!!
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
        if (!civInfo.isCityState) return
        
        val maxInfluence = civInfo.diplomacy
            .filter { it.value.otherCiv().isMajorCiv() && !it.value.otherCiv().isDefeated() }
            .maxByOrNull { it.value.getInfluence() }
        if (maxInfluence != null && maxInfluence.value.getInfluence() >= 60) {
            newAllyName = maxInfluence.key
        }

        if (civInfo.getAllyCivName() == newAllyName) return
        
        val oldAllyName = civInfo.getAllyCivName()
        civInfo.setAllyCiv(newAllyName)

        if (newAllyName != null) {
            val newAllyCiv = civInfo.gameInfo.getCivilization(newAllyName)
            val text = "We have allied with [${civInfo.civName}]."
            newAllyCiv.addNotification(text,
                getNotificationActions(),
                NotificationCategory.Diplomacy, civInfo.civName,
                NotificationIcon.Diplomacy
            )
            newAllyCiv.cache.updateViewableTiles()
            newAllyCiv.cache.updateCivResources()
            for (unique in newAllyCiv.getMatchingUniques(UniqueType.CityStateCanBeBoughtForGold))
                newAllyCiv.getDiplomacyManager(civInfo)!!.setFlag(DiplomacyFlags.MarriageCooldown, unique.params[0].toInt())

            // Join the wars of our new ally - loop through all civs they are at war with
            for (newEnemy in civInfo.gameInfo.civilizations.filter { it.isAtWarWith(newAllyCiv) && it.isAlive() } ) {
                if (!civInfo.isAtWarWith(newEnemy)) {
                    if (!civInfo.knows(newEnemy))
                        // We have to meet first (meet interesting people - and kill them!)
                        civInfo.diplomacyFunctions.makeCivilizationsMeet(newEnemy, warOnContact = true)
                    civInfo.getDiplomacyManager(newEnemy)!!.declareWar(DeclareWarReason(WarType.CityStateAllianceWar, newAllyCiv))
                }
            }
        }
        
        if (oldAllyName != null && civInfo.isAlive()) {
            val oldAllyCiv = civInfo.gameInfo.getCivilization(oldAllyName)
            val text = "We have lost alliance with [${civInfo.civName}]."
            oldAllyCiv.addNotification(text,
                getNotificationActions(),
                NotificationCategory.Diplomacy, civInfo.civName,
                NotificationIcon.Diplomacy
            )
            if (newAllyName != null && oldAllyCiv.knows(newAllyName)){
                val diplomacyManager = oldAllyCiv.getDiplomacyManager(newAllyName)!!
                diplomacyManager.addModifier(DiplomaticModifiers.StoleOurAlly, -10f)
            }
            oldAllyCiv.cache.updateViewableTiles()
            oldAllyCiv.cache.updateCivResources()
        }
    }

    /** @return a Sequence of NotificationActions for use in addNotification, showing Capital on map if any, then opening diplomacy */
    @Readonly
    fun getNotificationActions() = sequence {
        // Notification click will first point to CS location, if any, then open diplomacy.
        // That's fine for the influence notifications and for afraid too.
        //
        // If the city-state is captured by a civ, it stops being the ally of the civ it was previously an ally of.
        //  This means that it will NOT HAVE a capital at that time, so if we run getCapital()!! we'll get a crash!
        // Or, City States can get stuck with only their Settler and no cities until late into a game if city placements are rare
        // We also had `cities.asSequence() // in practice 0 or 1 entries, that's OK` before (a CS *can* have >1 cities but it will always raze conquests).
        val capital = civInfo.getCapital()
        if (capital != null)
            yield(LocationAction(capital.location))
        yield(DiplomacyAction(civInfo.civName))
    }

    @Readonly
    fun getDiplomaticMarriageCost(): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvMinorCivAI.cpp, line 7812
        var cost = (500 * civInfo.gameInfo.speed.goldCostModifier).toInt()
        // Plus disband value of all units
        for (unit in civInfo.units.getCivUnits()) {
            cost += unit.baseUnit.getDisbandGold(civInfo)
        }
        // Round to lower multiple of 5
        cost /= 5
        cost *= 5

        return cost
    }

    @Readonly
    fun canBeMarriedBy(otherCiv: Civilization): Boolean {
        return (!civInfo.isDefeated()
                && civInfo.isCityState
                && civInfo.cities.any()
                && civInfo.getDiplomacyManager(otherCiv)!!.isRelationshipLevelEQ(RelationshipLevel.Ally)
                && !otherCiv.getDiplomacyManager(civInfo)!!.hasFlag(DiplomacyFlags.MarriageCooldown)
                && otherCiv.getMatchingUniques(UniqueType.CityStateCanBeBoughtForGold).any()
                && otherCiv.gold >= getDiplomaticMarriageCost())

    }

    fun diplomaticMarriage(otherCiv: Civilization) {
        if (!canBeMarriedBy(otherCiv))  // Just in case
            return

        otherCiv.addGold(-getDiplomaticMarriageCost())
        
        val notificationLocation = civInfo.getCapital()!!.location
        otherCiv.addNotification("We have married into the ruling family of [${civInfo.civName}], bringing them under our control.",
            notificationLocation,
            NotificationCategory.Diplomacy, civInfo.civName,
            NotificationIcon.Diplomacy, otherCiv.civName)
        
        for (civ in civInfo.gameInfo.civilizations.filter { it != otherCiv })
            civ.addNotification("[${otherCiv.civName}] has married into the ruling family of [${civInfo.civName}], bringing them under their control.",
                notificationLocation,
                NotificationCategory.Diplomacy, civInfo.civName,
                NotificationIcon.Diplomacy, otherCiv.civName)
        
        for (unit in civInfo.units.getCivUnits())
            unit.gift(otherCiv)
        

        // Make sure this CS can never be liberated
        civInfo.gameInfo.getCities().filter {
            it.foundingCiv == civInfo.civName
        }.forEach {
            it.foundingCiv = ""
            it.isOriginalCapital = false
        }

        for (city in civInfo.cities) {
            city.espionage.removeAllPresentSpies(SpyFleeReason.CityTakenOverByMarriage)
            city.moveToCiv(otherCiv)
            city.isPuppet = true // Human players get a popup that allows them to annex instead
        }
        civInfo.destroy(notificationLocation)
    }

    @Readonly
    fun getTributeWillingness(demandingCiv: Civilization, demandingWorker: Boolean = false): Int {
        return getTributeModifiers(demandingCiv, demandingWorker).values.sum()
    }

    @Readonly
    fun getTributeModifiers(demandingCiv: Civilization, demandingWorker: Boolean = false, requireWholeList: Boolean = false): HashMap<String, Int> {
        val modifiers = LinkedHashMap<String, Int>()    // Linked to preserve order when presenting the modifiers table
        // Can't bully major civs or unsettled CS's
        if (!civInfo.isCityState) {
            modifiers["Major Civ"] = -999
            return modifiers
        }
        if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) {
            modifiers["No Cities"] = -999
            return modifiers
        }

        modifiers["Base value"] = -110

        if (civInfo.cityStatePersonality == CityStatePersonality.Hostile)
            modifiers["Hostile"] = -10
        if (civInfo.getAllyCivName() != null && civInfo.getAllyCivName() != demandingCiv.civName)
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
        if (civInfo.getDiplomacyManager(demandingCiv)!!.getInfluence() < -30)
            modifiers["Influence below -30"] = -300

        // Slight optimization, we don't do the expensive stuff if we have no chance of getting a >= 0 result
        if (!requireWholeList && modifiers.values.sum() < -200)
            return modifiers

        val forceRank = civInfo.gameInfo.getAliveMajorCivs().sortedByDescending { it.getStatForRanking(
            RankingType.Force) }.indexOf(demandingCiv)
        val globalModifier = civInfo.gameInfo.ruleset.modOptions.constants.tributeGlobalModifier
        modifiers["Military Rank"] = globalModifier - ((globalModifier / civInfo.gameInfo.gameParameters.players.size) * forceRank)

        if (!requireWholeList && modifiers.values.sum() < -100)
            return modifiers

        val bullyRange = (civInfo.gameInfo.tileMap.tileMatrix.size / 10).coerceIn(5, 10)   // Longer range for larger maps
        val inRangeTiles = civInfo.getCapital()!!.getCenterTile().getTilesInDistanceRange(1..bullyRange)
        val forceNearCity = inRangeTiles
            .sumOf { if (it.militaryUnit?.civ == demandingCiv)
                    it.militaryUnit!!.getForceEvaluation()
                else 0
            }
        val csForce = CityCombatant(civInfo.getCapital()!!).getDefendingStrength().toFloat().pow(1.5f).toInt() + inRangeTiles
            .sumOf { if (it.militaryUnit?.civ == civInfo)
                    it.militaryUnit!!.getForceEvaluation()
                else 0
            }
        val forceRatio = forceNearCity.toFloat() / csForce.toFloat()
        val localModifier = civInfo.gameInfo.ruleset.modOptions.constants.tributeLocalModifier

        modifiers["Military near City-State"] = when {
            forceRatio > 3f -> localModifier
            forceRatio > 2f -> localModifier * 4/5
            forceRatio > 1.5f -> localModifier * 3/5
            forceRatio > 1f -> localModifier * 2/5
            forceRatio > 0.5f -> localModifier / 5
            else -> 0
        }

        return modifiers
    }

    @Readonly
    fun goldGainedByTribute(): Int {
        // These values are close enough, linear increase throughout the game
        var gold = (10 * civInfo.gameInfo.speed.goldGiftModifier).toInt() * 5 // rounding down to nearest 5
        val turnsToIncrement = civInfo.gameInfo.speed.cityStateTributeScalingInterval
        gold += 5 * (civInfo.gameInfo.turns / turnsToIncrement).toInt()

        return gold
    }

    fun tributeGold(demandingCiv: Civilization) {
        if (!civInfo.isCityState) throw Exception("You can only demand gold from City-States!")
        val goldAmount = goldGainedByTribute()
        demandingCiv.addGold(goldAmount)
        civInfo.getDiplomacyManager(demandingCiv)!!.addInfluence(-15f)
        cityStateBullied(demandingCiv)
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
    }

    fun tributeWorker(demandingCiv: Civilization) {
        if (!civInfo.isCityState) throw Exception("You can only demand workers from City-States!")

        val buildableWorkerLikeUnits = civInfo.gameInfo.ruleset.units.filter {
            it.value.hasUnique(UniqueType.BuildImprovements) &&
                it.value.isCivilian() && it.value.isBuildable(civInfo)
        }
        if (buildableWorkerLikeUnits.isEmpty()) return  // Bad luck?
        demandingCiv.units.placeUnitNearTile(civInfo.getCapital()!!.location, buildableWorkerLikeUnits.values.random())

        civInfo.getDiplomacyManager(demandingCiv)!!.addInfluence(-50f)
        cityStateBullied(demandingCiv)
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
    }

    @Readonly
    fun canProvideStat(statType: Stat): Boolean {
        if (!civInfo.isCityState)
            return false
        for (bonus in getCityStateBonuses(civInfo.cityStateType, RelationshipLevel.Ally)) {
            if (bonus.stats[statType] > 0)
                return true
        }
        return false
    }

    fun updateDiplomaticRelationshipForCityState() {
        // Check if city-state invaded by other civs
        if (getNumThreateningBarbarians() > 0) return // Assume any players are there to fight barbarians

        for (otherCiv in civInfo.getKnownCivs().filter { it.isMajorCiv() }.toList()) {
            if (civInfo.isAtWarWith(otherCiv)) continue
            if (otherCiv.hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)) continue
            val diplomacy = civInfo.getDiplomacyManager(otherCiv)!!
            if (diplomacy.hasFlag(DiplomacyFlags.AngerFreeIntrusion)) continue // They recently helped us

            val unitsInBorder = otherCiv.units.getCivUnits().count { !it.isCivilian() && it.getTile().getOwner() == civInfo }
            if (unitsInBorder > 0 && diplomacy.isRelationshipLevelLT(RelationshipLevel.Friend)) {
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
        val researchableTechs = civInfo.gameInfo.ruleset.technologies.values
            .filter { !it.hasUnique(UniqueType.ResearchableMultipleTimes) && civInfo.tech.canBeResearched(it.name) }
        for (tech in researchableTechs) {
            val aliveMajorCivs = civInfo.gameInfo.getAliveMajorCivs()
            if (aliveMajorCivs.count { it.tech.isResearched(tech.name) } > aliveMajorCivs.size / 2)
                civInfo.tech.addTechnology(tech.name)
        }
        return
    }

    @Readonly
    fun getNumThreateningBarbarians(): Int {
        if (civInfo.gameInfo.gameParameters.noBarbarians) return 0
        val barbarianCiv = civInfo.gameInfo.civilizations.firstOrNull { it.isBarbarian }
            ?: return 0
        return barbarianCiv.units.getCivUnits().count { it.threatensCiv(civInfo) }
    }

    fun threateningBarbarianKilledBy(otherCiv: Civilization) {
        val diplomacy = civInfo.getDiplomacyManager(otherCiv)!!
        if (diplomacy.diplomaticStatus == DiplomaticStatus.War) return // No reward for enemies

        diplomacy.addInfluence(12f)

        if (diplomacy.hasFlag(DiplomacyFlags.AngerFreeIntrusion))
            diplomacy.setFlag(DiplomacyFlags.AngerFreeIntrusion, diplomacy.getFlag(DiplomacyFlags.AngerFreeIntrusion) + 5)
        else
            diplomacy.setFlag(DiplomacyFlags.AngerFreeIntrusion, 5)

        otherCiv.addNotification("[${civInfo.civName}] is grateful that you killed a Barbarian that was threatening them!",
            DiplomacyAction(civInfo.civName), NotificationCategory.Diplomacy, civInfo.civName)
    }

    /** A city state was bullied. What are its protectors going to do about it??? */
    private fun cityStateBullied(bully: Civilization) {
        if (!civInfo.isCityState) return // What are we doing here?

        for (protector in civInfo.cityStateFunctions.getProtectorCivs()) {
            if (!protector.knows(bully)) // Who?
                continue
            val protectorDiplomacy = protector.getDiplomacyManager(bully)!!
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.BulliedProtectedMinor)
                && protectorDiplomacy.getFlag(DiplomacyFlags.RememberBulliedProtectedMinor) > 50)
                protectorDiplomacy.addModifier(DiplomaticModifiers.BulliedProtectedMinor, -10f) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.BulliedProtectedMinor, -15f)
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberBulliedProtectedMinor, 75)    // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                bully.addNotification("[${protector.civName}] is upset that you demanded tribute from [${civInfo.civName}], whom they have pledged to protect!",
                    NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, protector.civName)
            else    // Let humans choose who to side with
                protector.popupAlerts.add(
                    PopupAlert(
                        AlertType.BulliedProtectedMinor,
                    bully.civName + "@" + civInfo.civName)
                )   // we need to pass both civs as argument, hence the horrible chimera
        }

        // Set a diplomatic flag so we remember for future quests (and not to give them any)
        civInfo.getDiplomacyManager(bully)!!.setFlag(DiplomacyFlags.Bullied, 20)

        // Notify all City-States that we were bullied (for quests)
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.cityStateBullied(civInfo, bully) }
    }

    /** A city state was attacked. What are its protectors going to do about it??? Also checks for Wary */
    fun cityStateAttacked(attacker: Civilization) {
        if (!civInfo.isCityState) return // What are we doing here?
        if (attacker.isCityState) return // City states can't be upset with each other

        // We might become wary!
        if (attacker.isMinorCivWarmonger()) { // They've attacked a lot of city-states
            civInfo.getDiplomacyManager(attacker)!!.becomeWary()
        }
        else if (attacker.isMinorCivAggressor()) { // They've attacked a few
            if (Random.Default.nextBoolean()) { // 50% chance
                civInfo.getDiplomacyManager(attacker)!!.becomeWary()
            }
        }
        // Others might become wary!
        if (attacker.isMinorCivAggressor()) makeOtherCityStatesWaryOfAttacker(attacker)

        triggerProtectorCivs(attacker)

        // Even if we aren't *technically* protectors, we *can* still be pissed you attacked our allies
        triggerAllyCivs(attacker)

        // Set up war with major pseudo-quest
        civInfo.questManager.wasAttackedBy(attacker)
        civInfo.getDiplomacyManager(attacker)!!.setFlag(DiplomacyFlags.RecentlyAttacked, 2) // Reminder to ask for unit gifts in 2 turns
    }

    private fun makeOtherCityStatesWaryOfAttacker(attacker: Civilization) {
        for (cityState in civInfo.gameInfo.getAliveCityStates()) {
            if (cityState == civInfo) // Must be a different minor
                continue
            if (cityState.getAllyCivName() == attacker.civName) // Must not be allied to the attacker
                continue
            if (!cityState.knows(attacker)) // Must have met
                continue
            if (cityState.questManager.wantsDead(civInfo.civName))  // Must not want us dead
                continue

            var probability: Int = if (attacker.isMinorCivWarmonger()) {
                // High probability if very aggressive
                when (cityState.getProximity(attacker)) {
                    Proximity.Neighbors -> 100
                    Proximity.Close -> 75
                    Proximity.Far -> 50
                    Proximity.Distant -> 25
                    else -> 0
                }
            } else {
                // Lower probability if only somewhat aggressive
                when (cityState.getProximity(attacker)) {
                    Proximity.Neighbors -> 50
                    Proximity.Close -> 20
                    else -> 0
                }
            }

            // Higher probability if already at war
            if (cityState.isAtWarWith(attacker))
                probability += 50

            if (Random.nextInt(100) <= probability) {
                cityState.getDiplomacyManager(attacker)!!.becomeWary()
            }
        }
    }

    private fun triggerProtectorCivs(attacker: Civilization) {
        for (protector in civInfo.cityStateFunctions.getProtectorCivs()) {
            val protectorDiplomacy = protector.getDiplomacyManager(attacker) ?: continue // Who?
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.AttackedProtectedMinor)
                && protectorDiplomacy.getFlag(DiplomacyFlags.RememberAttackedProtectedMinor) > 50
            )
                protectorDiplomacy.addModifier(
                    DiplomaticModifiers.AttackedProtectedMinor,
                    -15f
                ) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.AttackedProtectedMinor, -20f)
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberAttackedProtectedMinor, 75)   // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                attacker.addNotification(
                    "[${protector.civName}] is upset that you attacked [${civInfo.civName}], whom they have pledged to protect!",
                    NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, protector.civName
                )
            else    // Let humans choose who to side with
                protector.popupAlerts.add(
                    PopupAlert(
                        AlertType.AttackedProtectedMinor,
                        attacker.civName + "@" + civInfo.civName
                    )
                )   // we need to pass both civs as argument, hence the horrible chimera
        }
    }

    private fun triggerAllyCivs(attacker: Civilization) {
        val allyCiv = civInfo.getAllyCiv()
        if (allyCiv != null && allyCiv !in civInfo.cityStateFunctions.getProtectorCivs() && allyCiv.knows(attacker)) {
            val allyDiplomacy = allyCiv.getDiplomacyManager(attacker)!!
            // Less than if we were protectors
            allyDiplomacy.addModifier(DiplomaticModifiers.AttackedAlliedMinor, -10f)

            if (allyCiv.playerType != PlayerType.Human)   // Humans can have their own emotions
                attacker.addNotification(
                    "[${allyCiv.civName}] is upset that you attacked [${civInfo.civName}], whom they are allied with!",
                    NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, allyCiv.civName
                )
            else    // Let humans choose who to side with
                allyCiv.popupAlerts.add(
                    PopupAlert(
                        AlertType.AttackedAllyMinor,
                        attacker.civName + "@" + civInfo.civName
                    )
                )
        }
    }


    /** A city state was destroyed. Its protectors are going to be upset! */
    fun cityStateDestroyed(attacker: Civilization) {
        if (!civInfo.isCityState) return // What are we doing here?

        for (protector in civInfo.cityStateFunctions.getProtectorCivs()) {
            if (!protector.knows(attacker)) // Who?
                continue
            val protectorDiplomacy = protector.getDiplomacyManager(attacker)!!
            if (protectorDiplomacy.hasModifier(DiplomaticModifiers.DestroyedProtectedMinor))
                protectorDiplomacy.addModifier(DiplomaticModifiers.DestroyedProtectedMinor, -10f) // Penalty less severe for second offence
            else
                protectorDiplomacy.addModifier(DiplomaticModifiers.DestroyedProtectedMinor, -40f) // Oof
            protectorDiplomacy.setFlag(DiplomacyFlags.RememberDestroyedProtectedMinor, 125)   // Reset their memory

            if (protector.playerType != PlayerType.Human)   // Humans can have their own emotions
                attacker.addNotification("[${protector.civName}] is outraged that you destroyed [${civInfo.civName}], whom they had pledged to protect!",
                    NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, protector.civName)
            protector.addNotification("[${attacker.civName}] has destroyed [${civInfo.civName}], whom you had pledged to protect!",
                NotificationCategory.Diplomacy,  attacker.civName, NotificationIcon.Death, civInfo.civName)
        }

        // Notify all City-States that we were killed (for quest completion)
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.cityStateConquered(civInfo, attacker) }
    }

    /** Asks all met majors that haven't yet declared wor on [attacker] to at least give some units */
    fun askForUnitGifts(attacker: Civilization) {
        if (attacker.isDefeated() || civInfo.isDefeated()) // never mind, someone died
            return
        if (civInfo.cities.isEmpty()) // Can't receive units with no cities
            return

        for (thirdCiv in civInfo.getKnownCivs()
            .filter { it != attacker && it.isAlive() && it.knows(attacker) && !it.isAtWarWith(attacker) && it.isMajorCiv() }
        ) {
            thirdCiv.addNotification(
                "[${civInfo.civName}] is being attacked by [${attacker.civName}] and asks all major civilizations to help them out by gifting them military units.",
                civInfo.getCapital()!!.location,
                NotificationCategory.Diplomacy,
                civInfo.civName,
                "OtherIcons/Present",
            )
        }
    }

    @Readonly
    fun getCityStateResourcesForAlly(): ResourceSupplyList {
        val resourceSupplyList = ResourceSupplyList()
        // TODO: City-states don't give allies resources from civ-wide uniques!
        val civResourceModifiers = civInfo.getResourceModifiers()
        for (city in civInfo.cities) {
            // IGNORE the fact that they consume their own resources - #4769
            resourceSupplyList.addPositiveByResource(city.getResourcesGeneratedByCity(civResourceModifiers), Constants.cityStates)
        }
        return resourceSupplyList
    }

    // TODO: Optimize, update whenever status changes, otherwise retain the same list
    @Readonly
    fun getUniquesProvidedByCityStates(
        uniqueType: UniqueType,
        gameContext: GameContext
    ):Sequence<Unique> {
        if (civInfo.isCityState) return emptySequence()

        return civInfo.getKnownCivs().filter { it.isCityState }
            .flatMap {
                // We don't use DiplomacyManager.getRelationshipLevel for performance reasons - it tries to calculate getTributeWillingness which is heavy
                val relationshipLevel =
                        if (it.getAllyCivName() == civInfo.civName) RelationshipLevel.Ally
                        else if (it.getDiplomacyManager(civInfo)!!.getInfluence() >= 30) RelationshipLevel.Friend
                        else RelationshipLevel.Neutral
                getCityStateBonuses(it.cityStateType, relationshipLevel, uniqueType)
            }
            .filter { it.conditionalsApply(gameContext) }
            .flatMap { it.getMultiplied(gameContext) }
    }

    companion object {
        @Readonly
        fun getCityStateBonuses(
            cityStateType: CityStateType,
            relationshipLevel: RelationshipLevel,
            uniqueType: UniqueType? = null
        ): Sequence<Unique> {
            val cityStateUniqueMap = when (relationshipLevel) {
                RelationshipLevel.Ally -> cityStateType.allyBonusUniqueMap
                RelationshipLevel.Friend -> cityStateType.friendBonusUniqueMap
                else -> return emptySequence()
            }
            return if (uniqueType == null) cityStateUniqueMap.getAllUniques()
            else cityStateUniqueMap.getUniques(uniqueType)
        }
    }
}

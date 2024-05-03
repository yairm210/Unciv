package com.unciv.logic.automation.civilization

import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.unit.EspionageAutomation
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.victoryscreen.RankingType
import kotlin.random.Random

object NextTurnAutomation {

    /** Top-level AI turn task list */
    fun automateCivMoves(civInfo: Civilization) {
        if (civInfo.isBarbarian()) return BarbarianAutomation(civInfo).automate()
        if (civInfo.isSpectator()) return // When there's a spectator in multiplayer games, it's processed automatically, but shouldn't be able to actually do anything

        respondToPopupAlerts(civInfo)
        TradeAutomation.respondToTradeRequests(civInfo)

        if (civInfo.isMajorCiv()) {
            if (!civInfo.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)) {
                DiplomacyAutomation.declareWar(civInfo)
                DiplomacyAutomation.offerPeaceTreaty(civInfo)
                DiplomacyAutomation.offerDeclarationOfFriendship(civInfo)
            }
            if (civInfo.gameInfo.isReligionEnabled()) {
                ReligionAutomation.spendFaithOnReligion(civInfo)
            }
            DiplomacyAutomation.offerOpenBorders(civInfo)
            DiplomacyAutomation.offerResearchAgreement(civInfo)
            DiplomacyAutomation.offerDefensivePact(civInfo)
            TradeAutomation.exchangeLuxuries(civInfo)
            issueRequests(civInfo)
            adoptPolicy(civInfo)  // todo can take a second - why?
            freeUpSpaceResources(civInfo)
        } else {
            civInfo.cityStateFunctions.getFreeTechForCityState()
            civInfo.cityStateFunctions.updateDiplomaticRelationshipForCityState()
        }

        chooseTechToResearch(civInfo)
        automateCityBombardment(civInfo)
        UseGoldAutomation.useGold(civInfo)
        if (!civInfo.isCityState()) {
            protectCityStates(civInfo)
            bullyCityStates(civInfo)
        }
        automateUnits(civInfo)  // this is the most expensive part

        if (civInfo.isMajorCiv()) {
            if (civInfo.gameInfo.isReligionEnabled()) {
                // Can only be done now, as the prophet first has to decide to found/enhance a religion
                ReligionAutomation.chooseReligiousBeliefs(civInfo)
            }
            if (civInfo.gameInfo.isEspionageEnabled()) {
                // Do after cities are conquered
                EspionageAutomation(civInfo).automateSpies()
            }
        }

        automateCities(civInfo)  // second most expensive
        trainSettler(civInfo)
        tryVoteForDiplomaticVictory(civInfo)
    }

    fun automateGoldToSciencePercentage(civInfo: Civilization) {
        // Don't let the AI run blindly with the default convert-gold-to-science ratio if that option is enabled
        val estimatedIncome = civInfo.stats.statsForNextTurn.gold.toInt()
        val projectedGold = civInfo.gold + estimatedIncome
        // TODO: some cleverness, this is just wild guessing.
        val pissPoor = civInfo.tech.era.baseUnitBuyCost
        val stinkingRich = civInfo.tech.era.startingGold * 10 + civInfo.cities.size * 2 * pissPoor
        val maxPercent = 0.8f
        civInfo.tech.goldPercentConvertedToScience = when {
            civInfo.gold <= 0 -> 0f
            projectedGold <= pissPoor -> 0f
            else -> ((projectedGold - pissPoor) * maxPercent / stinkingRich).coerceAtMost(maxPercent)
        }
    }
    private fun respondToPopupAlerts(civInfo: Civilization) {
        for (popupAlert in civInfo.popupAlerts.toList()) { // toList because this can trigger other things that give alerts, like Golden Age
            if (popupAlert.type == AlertType.DemandToStopSettlingCitiesNear) {  // we're called upon to make a decision
                val demandingCiv = civInfo.gameInfo.getCivilization(popupAlert.value)
                val diploManager = civInfo.getDiplomacyManager(demandingCiv)
                if (Automation.threatAssessment(civInfo, demandingCiv) >= ThreatLevel.High)
                    diploManager.agreeNotToSettleNear()
                else diploManager.refuseDemandNotToSettleNear()
            }
            if (popupAlert.type == AlertType.DeclarationOfFriendship) {
                val requestingCiv = civInfo.gameInfo.getCivilization(popupAlert.value)
                val diploManager = civInfo.getDiplomacyManager(requestingCiv)
                if (civInfo.diplomacyFunctions.canSignDeclarationOfFriendshipWith(requestingCiv)
                    && DiplomacyAutomation.wantsToSignDeclarationOfFrienship(civInfo,requestingCiv)) {
                    diploManager.signDeclarationOfFriendship()
                    requestingCiv.addNotification("We have signed a Declaration of Friendship with [${civInfo.civName}]!", NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, civInfo.civName)
                } else  {
                    diploManager.otherCivDiplomacy().setFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship, 10)
                    requestingCiv.addNotification("[${civInfo.civName}] has denied our Declaration of Friendship!", NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, civInfo.civName)
                }

            }
        }

        civInfo.popupAlerts.clear() // AIs don't care about popups.
    }

    internal fun valueCityStateAlliance(civInfo: Civilization, cityState: Civilization, includeQuests: Boolean = false): Int {
        var value = 0
        val civPersonality = civInfo.getPersonality()

        if (cityState.cityStateFunctions.canProvideStat(Stat.Culture)) {
            if (civInfo.wantsToFocusOn(Victory.Focus.Culture))
                value += 10
            value += civPersonality[PersonalityValue.Culture].toInt() - 5
        }
        if (cityState.cityStateFunctions.canProvideStat(Stat.Faith)) {
            if (civInfo.wantsToFocusOn(Victory.Focus.Faith))
                value += 10
            value += civPersonality[PersonalityValue.Faith].toInt() - 5
        }
        if (cityState.cityStateFunctions.canProvideStat(Stat.Production)) {
            if (civInfo.wantsToFocusOn(Victory.Focus.Production))
                value += 10
            value += civPersonality[PersonalityValue.Production].toInt() - 5
        }
        if (cityState.cityStateFunctions.canProvideStat(Stat.Science)) {
            // In case someone mods this in
            if (civInfo.wantsToFocusOn(Victory.Focus.Science))
                value += 10
            value += civPersonality[PersonalityValue.Science].toInt() - 5
        }
        if (civInfo.wantsToFocusOn(Victory.Focus.Military)) {
            if (!cityState.isAlive())
                value -= 5
            else {
                // Don't ally close city-states, conquer them instead
                val distance = getMinDistanceBetweenCities(civInfo, cityState)
                if (distance < 20)
                    value -= (20 - distance) / 4
            }
        }
        if (civInfo.wantsToFocusOn(Victory.Focus.CityStates)) {
            value += 5  // Generally be friendly
        }
        if (civInfo.getHappiness() < 5 && cityState.cityStateFunctions.canProvideStat(Stat.Happiness)) {
            value += 10 - civInfo.getHappiness()
            value += civPersonality[PersonalityValue.Happiness].toInt() - 5
        }
        if (civInfo.getHappiness() > 5 && cityState.cityStateFunctions.canProvideStat(Stat.Food)) {
            value += 5
            value += civPersonality[PersonalityValue.Food].toInt() - 5
        }

        if (!cityState.isAlive() || cityState.cities.isEmpty() || civInfo.cities.isEmpty())
            return value

        // The more we have invested into the city-state the more the alliance is worth
        val ourInfluence = if (civInfo.knows(cityState))
            cityState.getDiplomacyManager(civInfo).getInfluence().toInt()
        else 0
        value += ourInfluence / 10

        if (civInfo.gold < 100 && ourInfluence < 30) {
            // Consider bullying for cash
            value -= 5
        }

        if (cityState.getAllyCiv() != null && cityState.getAllyCiv() != civInfo.civName) {
            // easier not to compete if a third civ has this locked down
            val thirdCivInfluence = cityState.getDiplomacyManager(cityState.getAllyCiv()!!).getInfluence().toInt()
            value -= (thirdCivInfluence - 30) / 10
        }

        // Bonus for luxury resources we can get from them
        value += cityState.detailedCivResources.count {
            it.resource.resourceType == ResourceType.Luxury
            && it.resource !in civInfo.detailedCivResources.map { supply -> supply.resource }
        }

        if (includeQuests) {
            // Investing is better if there is an investment bonus quest active.
            value += (cityState.questManager.getInvestmentMultiplier(civInfo.civName) * 10).toInt() - 10
        }

        return value
    }

    private fun protectCityStates(civInfo: Civilization) {
        for (state in civInfo.getKnownCivs().filter { !it.isDefeated() && it.isCityState() }) {
            val diplomacyManager = state.getDiplomacyManager(civInfo.civName)
            val isAtLeastFriend = diplomacyManager.isRelationshipLevelGE(RelationshipLevel.Friend)
            if (isAtLeastFriend && state.cityStateFunctions.otherCivCanPledgeProtection(civInfo)) {
                state.cityStateFunctions.addProtectorCiv(civInfo)
            } else if (!isAtLeastFriend && state.cityStateFunctions.otherCivCanWithdrawProtection(civInfo)) {
                state.cityStateFunctions.removeProtectorCiv(civInfo)
            }
        }
    }

    private fun bullyCityStates(civInfo: Civilization) {
        for (state in civInfo.getKnownCivs().filter { !it.isDefeated() && it.isCityState() }.toList()) {
            val diplomacyManager = state.getDiplomacyManager(civInfo.civName)
            if (diplomacyManager.isRelationshipLevelLT(RelationshipLevel.Friend)
                    && diplomacyManager.diplomaticStatus == DiplomaticStatus.Peace
                    && valueCityStateAlliance(civInfo, state) <= 0
                    && state.cityStateFunctions.getTributeWillingness(civInfo) >= 0) {
                if (state.cityStateFunctions.getTributeWillingness(civInfo, demandingWorker = true) > 0)
                    state.cityStateFunctions.tributeWorker(civInfo)
                else
                    state.cityStateFunctions.tributeGold(civInfo)
            }
        }
    }

    private fun chooseTechToResearch(civInfo: Civilization) {
        fun getGroupedResearchableTechs(): List<List<Technology>> {
            val researchableTechs = civInfo.gameInfo.ruleset.technologies.values
                .asSequence()
                .filter { civInfo.tech.canBeResearched(it.name) }
                .groupBy { it.cost }
            return researchableTechs.toSortedMap().values.toList()
        }
        while(civInfo.tech.freeTechs > 0) {
            val costs = getGroupedResearchableTechs()
            if (costs.isEmpty()) return

            val mostExpensiveTechs = costs[costs.size - 1]
            civInfo.tech.getFreeTechnology(mostExpensiveTechs.random().name)
        }
        if (civInfo.tech.techsToResearch.isEmpty()) {
            val costs = getGroupedResearchableTechs()
            if (costs.isEmpty()) return

            val cheapestTechs = costs[0]
            //Do not consider advanced techs if only one tech left in cheapest group
            val techToResearch: Technology =
                if (cheapestTechs.size == 1 || costs.size == 1) {
                    cheapestTechs.random()
                } else {
                    //Choose randomly between cheapest and second cheapest group
                    val techsAdvanced = costs[1]
                    (cheapestTechs + techsAdvanced).random()
                }

            civInfo.tech.techsToResearch.add(techToResearch.name)
        }
    }

    private fun adoptPolicy(civInfo: Civilization) {
        /*
        # Branch-based policy-to-adopt decision
        Basically the AI prioritizes finishing incomplete branches before moving on, \
        unless a new branch with higher priority is adoptable.

        - If incomplete branches have higher priorities than any newly adoptable branch,
            - Candidates are the unfinished branches.
        - Else if newly adoptable branches have higher priorities than any incomplete branch,
            - Candidates are the new branches.
        - Choose a random candidate closest to completion.
        - Pick a random child policy of a chosen branch and adopt it.
        */
        while (civInfo.policies.canAdoptPolicy()) {
            val incompleteBranches: Set<PolicyBranch> = civInfo.policies.incompleteBranches
            val adoptableBranches: Set<PolicyBranch> = civInfo.policies.adoptableBranches

            // Skip the whole thing if all branches are completed
            if (incompleteBranches.isEmpty() && adoptableBranches.isEmpty()) return

            val priorityMap: Map<PolicyBranch, Int> = civInfo.policies.priorityMap
            var maxIncompletePriority: Int? =
                civInfo.policies.getMaxPriority(incompleteBranches)
            var maxAdoptablePriority: Int? = civInfo.policies.getMaxPriority(adoptableBranches)

            // This here is a (probably dirty) code to bypass NoSuchElementException error
            //  when one of the priority variables is null
            if (maxIncompletePriority == null) maxIncompletePriority =
                maxAdoptablePriority!! - 1
            if (maxAdoptablePriority == null) maxAdoptablePriority =
                maxIncompletePriority - 1

            // Candidate branches to adopt
            val candidates: Set<PolicyBranch> =
                // If incomplete branches have higher priorities than any newly adoptable branch,
                if (maxAdoptablePriority <= maxIncompletePriority) {
                    // Prioritize finishing one of the unfinished branches
                    incompleteBranches.filter {
                        priorityMap[it] == maxIncompletePriority
                    }.toSet()
                }
                // If newly adoptable branches have higher priorities than any incomplete branch,
                else {
                    // Prioritize adopting one of the new branches
                    adoptableBranches.filter {
                        priorityMap[it] == maxAdoptablePriority
                    }.toSet()
                }

            // branchCompletionMap but keys are only candidates
            val candidateCompletionMap: Map<PolicyBranch, Int> =
                civInfo.policies.branchCompletionMap.filterKeys { key ->
                    key in candidates
                }

            // Choose the branch with the LEAST REMAINING policies, not the MOST ADOPTED ones
            val targetBranch = candidateCompletionMap.minBy { it.key.policies.size - it.value }.key

            val policyToAdopt: Policy =
                if (civInfo.policies.isAdoptable(targetBranch)) targetBranch
                else targetBranch.policies.filter { civInfo.policies.isAdoptable(it) }.random()

            civInfo.policies.adopt(policyToAdopt)
        }
    }

    fun chooseGreatPerson(civInfo: Civilization) {
        if (civInfo.greatPeople.freeGreatPeople == 0) return
        val mayanGreatPerson = civInfo.greatPeople.mayaLimitedFreeGP > 0
        val greatPeople =
            if (mayanGreatPerson)
                civInfo.greatPeople.getGreatPeople().filter { it.name in civInfo.greatPeople.longCountGPPool }
            else civInfo.greatPeople.getGreatPeople()

        if (greatPeople.isEmpty()) return
        var greatPerson = greatPeople.random()

        if (civInfo.wantsToFocusOn(Victory.Focus.Culture)) {
            val culturalGP =
                greatPeople.firstOrNull { it.uniques.contains("Great Person - [Culture]") }
            if (culturalGP != null) greatPerson = culturalGP
        }
        if (civInfo.wantsToFocusOn(Victory.Focus.Science)) {
            val scientificGP =
                greatPeople.firstOrNull { it.uniques.contains("Great Person - [Science]") }
            if (scientificGP != null) greatPerson = scientificGP
        }

        civInfo.units.addUnit(greatPerson, civInfo.cities.firstOrNull { it.isCapital() })

        civInfo.greatPeople.freeGreatPeople--
        if (mayanGreatPerson){
            civInfo.greatPeople.longCountGPPool.remove(greatPerson.name)
            civInfo.greatPeople.mayaLimitedFreeGP--
        }
    }

    /** If we are able to build a spaceship but have already spent our resources, try disbanding
     *  a unit and selling a building to make room. Can happen due to trades etc */
    private fun freeUpSpaceResources(civInfo: Civilization) {
        // No need to build spaceship parts just yet
        if (civInfo.gameInfo.ruleset.victories.none { civInfo.victoryManager.getNextMilestone(it.value)?.type == MilestoneType.AddedSSPartsInCapital } )
            return

        for (resource in civInfo.gameInfo.spaceResources) {
            // Have enough resources already
            if (civInfo.getResourceAmount(resource) >= Automation.getReservedSpaceResourceAmount(civInfo))
                continue

            val unitToDisband = civInfo.units.getCivUnits()
                .filter { it.requiresResource(resource) }
                .minByOrNull { it.getForceEvaluation() }
            unitToDisband?.disband()

            for (city in civInfo.cities) {
                if (city.hasSoldBuildingThisTurn)
                    continue
                val buildingToSell = civInfo.gameInfo.ruleset.buildings.values.filter {
                        city.cityConstructions.isBuilt(it.name)
                        && it.requiredResources(StateForConditionals(civInfo, city)).contains(resource)
                        && it.isSellable()
                        && !civInfo.civConstructions.hasFreeBuilding(city, it) }
                    .randomOrNull()
                if (buildingToSell != null) {
                    city.sellBuilding(buildingToSell)
                    break
                }
            }
        }
    }


    private fun automateUnits(civInfo: Civilization) {
        val isAtWar = civInfo.isAtWar()
        val sortedUnits = civInfo.units.getCivUnits().sortedBy { unit -> getUnitPriority(unit, isAtWar) }
        for (unit in sortedUnits) UnitAutomation.automateUnitMoves(unit)
    }

    /** Returns the priority of the unit, a lower value is higher priority **/
    fun getUnitPriority(unit: MapUnit, isAtWar: Boolean): Int {
        if (unit.isCivilian() && !unit.isGreatPersonOfType("War")) return 1 // Civilian
        if (unit.baseUnit.isAirUnit()) return when {
            unit.canIntercept() -> 2 // Fighers first
            unit.baseUnit.isNuclearWeapon() -> 3 // Then Nukes (area damage)
            !unit.hasUnique(UniqueType.SelfDestructs) -> 4 // Then Bombers (reusable)
            else -> 5 // Missiles
        }
        val distance = if (!isAtWar) 0 else unit.civ.threatManager.getDistanceToClosestEnemyUnit(unit.getTile(),6)
        // Lower health units should move earlier to swap with higher health units
        return distance + (unit.health / 10) + when {
            unit.baseUnit.isRanged() -> 10
            unit.baseUnit.isMelee() -> 30
            unit.isGreatPersonOfType("War") -> 100 // Generals move after military units
            else -> 1
        }
    }

    fun automateCityBombardment(civInfo: Civilization) {
        for (city in civInfo.cities) UnitAutomation.tryBombardEnemy(city)
    }

    fun automateCities(civInfo: Civilization) {
        val ownMilitaryStrength = civInfo.getStatForRanking(RankingType.Force)
        val sumOfEnemiesMilitaryStrength =
                civInfo.gameInfo.civilizations
                    .filter { it != civInfo && !it.isBarbarian() && civInfo.isAtWarWith(it) }
                    .sumOf { it.getStatForRanking(RankingType.Force) }
        val civHasSignificantlyWeakerMilitaryThanEnemies =
                ownMilitaryStrength < sumOfEnemiesMilitaryStrength * 0.66f
        for (city in civInfo.cities) {
            if (city.isPuppet && city.population.population > 9
                    && !city.isInResistance() && !civInfo.hasUnique(UniqueType.MayNotAnnexCities)
            ) {
                city.annexCity()
            }

            city.reassignAllPopulation()

            if (city.health < city.getMaxHealth() || civHasSignificantlyWeakerMilitaryThanEnemies) {
                Automation.tryTrainMilitaryUnit(city) // need defenses if city is under attack
                if (city.cityConstructions.constructionQueue.isNotEmpty())
                    continue // found a unit to build so move on
            }

            city.cityConstructions.chooseNextConstruction()
        }
    }

    private fun trainSettler(civInfo: Civilization) {
        val personality = civInfo.getPersonality()
        if (civInfo.isCityState()) return
        if (civInfo.isAtWar()) return // don't train settlers when you could be training troops.
        if (civInfo.wantsToFocusOn(Victory.Focus.Culture) && civInfo.cities.size > 3 &&
            civInfo.getPersonality().isNeutralPersonality)
            return
        if (civInfo.cities.none()) return
        if (civInfo.getHappiness() <= civInfo.cities.size) return

        val settlerUnits = civInfo.gameInfo.ruleset.units.values
                .filter { it.isCityFounder() && it.isBuildable(civInfo) &&
                    personality.getMatchingUniques(UniqueType.WillNotBuild, StateForConditionals(civInfo))
                        .none { unique -> it.matchesFilter(unique.params[0]) } }
        if (settlerUnits.isEmpty()) return
        if (!civInfo.units.getCivUnits().none { it.hasUnique(UniqueType.FoundCity) }) return

        if (civInfo.cities.any {
                val currentConstruction = it.cityConstructions.getCurrentConstruction()
                currentConstruction is BaseUnit && currentConstruction.isCityFounder()
            }) return

        if (civInfo.units.getCivUnits().none { it.isMilitary() }) return // We need someone to defend him first

        val workersBuildableForThisCiv = civInfo.gameInfo.ruleset.units.values.any {
            it.hasUnique(UniqueType.BuildImprovements)
                && it.isBuildable(civInfo)
        }

        val bestCity = civInfo.cities.filterNot { it.isPuppet }
            // If we can build workers, then we want AT LEAST 2 improvements, OR a worker nearby.
            // Otherwise, AI tries to produce settlers when it can hardly sustain itself
            .filter { city ->
                !workersBuildableForThisCiv
                    || city.getCenterTile().getTilesInDistance(2).count { it.improvement!=null } > 1
                    || city.getCenterTile().getTilesInDistance(3).any { it.civilianUnit?.hasUnique(UniqueType.BuildImprovements)==true }
            }
            .maxByOrNull { it.cityStats.currentCityStats.production }
            ?: return
        if (bestCity.cityConstructions.getBuiltBuildings().count() > 1) // 2 buildings or more, otherwise focus on self first
            bestCity.cityConstructions.currentConstructionFromQueue = settlerUnits.minByOrNull { it.cost }!!.name
    }

    // Technically, this function should also check for civs that have liberated one or more cities
    // However, that can be added in another update, this PR is large enough as it is.
    private fun tryVoteForDiplomaticVictory(civ: Civilization) {
        if (!civ.mayVoteForDiplomaticVictory()) return

        val chosenCiv: String? = if (civ.isMajorCiv()) {

            val knownMajorCivs = civ.getKnownCivs().filter { it.isMajorCiv() }
            val highestOpinion = knownMajorCivs
                .maxOfOrNull {
                    civ.getDiplomacyManager(it).opinionOfOtherCiv()
                }

            if (highestOpinion == null) null  // Abstain if we know nobody
            else if (highestOpinion < -80 || highestOpinion < -40 && highestOpinion + Random.Default.nextInt(40) < -40)
                null // Abstain if we hate everybody (proportional chance in the RelationshipLevel.Enemy range - lesser evil)
            else knownMajorCivs
                .filter { civ.getDiplomacyManager(it).opinionOfOtherCiv() == highestOpinion }
                .toList().random().civName

        } else {
            civ.getAllyCiv()
        }

        civ.diplomaticVoteForCiv(chosenCiv)
    }

    private fun issueRequests(civInfo: Civilization) {
        for (otherCiv in civInfo.getKnownCivs().filter { it.isMajorCiv() && !civInfo.isAtWarWith(it) }) {
            val diploManager = civInfo.getDiplomacyManager(otherCiv)
            if (diploManager.hasFlag(DiplomacyFlags.SettledCitiesNearUs))
                onCitySettledNearBorders(civInfo, otherCiv)
        }
    }

    private fun onCitySettledNearBorders(civInfo: Civilization, otherCiv: Civilization) {
        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        when {
            diplomacyManager.hasFlag(DiplomacyFlags.IgnoreThemSettlingNearUs) -> {
            }
            diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs) -> {
                otherCiv.popupAlerts.add(PopupAlert(AlertType.CitySettledNearOtherCivDespiteOurPromise, civInfo.civName))
                diplomacyManager.setFlag(DiplomacyFlags.IgnoreThemSettlingNearUs, 100)
                diplomacyManager.setModifier(DiplomaticModifiers.BetrayedPromiseToNotSettleCitiesNearUs, -20f)
                diplomacyManager.removeFlag(DiplomacyFlags.AgreedToNotSettleNearUs)
            }
            else -> {
                val threatLevel = Automation.threatAssessment(civInfo, otherCiv)
                if (threatLevel < ThreatLevel.High) // don't piss them off for no reason please.
                    otherCiv.popupAlerts.add(PopupAlert(AlertType.DemandToStopSettlingCitiesNear, civInfo.civName))
            }
        }
        diplomacyManager.removeFlag(DiplomacyFlags.SettledCitiesNearUs)
    }

    fun getMinDistanceBetweenCities(civ1: Civilization, civ2: Civilization): Int {
        return getClosestCities(civ1, civ2)?.aerialDistance ?: Int.MAX_VALUE
    }

    data class CityDistance(val city1: City, val city2: City, val aerialDistance: Int)

    fun getClosestCities(civ1: Civilization, civ2: Civilization): CityDistance? {
        if (civ1.cities.isEmpty() || civ2.cities.isEmpty())
            return null

        val cityDistances = arrayListOf<CityDistance>()
        for (civ1city in civ1.cities)
            for (civ2city in civ2.cities)
                cityDistances += CityDistance(civ1city, civ2city,
                        civ1city.getCenterTile().aerialDistanceTo(civ2city.getCenterTile()))

        return cityDistances.minByOrNull { it.aerialDistance }!!
    }
}

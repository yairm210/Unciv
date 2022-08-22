package com.unciv.models.ruleset.unique

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.*
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.UniqueType.*
import com.unciv.models.stats.Stat
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.hasPlaceholderParameters
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.worldscreen.unit.UnitActions
import kotlin.random.Random

// Buildings, techs, policies, ancient ruins and promotions can have 'triggered' effects
object UniqueTriggerActivation {
    /** @return boolean whether an action was successfully performed */
    fun triggerCivwideUnique(
        unique: Unique,
        civInfo: CivilizationInfo,
        cityInfo: CityInfo? = null,
        tile: TileInfo? = null,
        notification: String? = null
    ): Boolean {
        val timingConditional = unique.conditionals.firstOrNull{it.type == ConditionalTimedUnique}
        if (timingConditional != null) {
            civInfo.temporaryUniques.add(TemporaryUnique(unique, timingConditional.params[0].toInt()))
            return true
        }

        if (!unique.conditionalsApply(civInfo, cityInfo)) return false

        val chosenCity = cityInfo ?: civInfo.cities.firstOrNull { it.isCapital() }
        val tileBasedRandom =
            if (tile != null) Random(tile.position.toString().hashCode())
            else Random(-550) // Very random indeed
        val ruleSet = civInfo.gameInfo.ruleSet

        when (unique.type) {
            OneTimeFreeUnit -> {
                val unitName = unique.params[0]
                val unit = ruleSet.units[unitName]
                if (chosenCity == null || unit == null || (unit.hasUnique(FoundCity) && civInfo.isOneCityChallenger()))
                    return false

                val placedUnit = civInfo.addUnit(unitName, chosenCity)
                if (notification != null && placedUnit != null) {
                    civInfo.addNotification(
                        notification,
                        placedUnit.getTile().position,
                        placedUnit.name
                    )
                }
                return true
            }
            OneTimeAmountFreeUnits -> {
                val unitName = unique.params[1]
                val unit = ruleSet.units[unitName]
                if (chosenCity == null || unit == null || (unit.hasUnique(FoundCity) && civInfo.isOneCityChallenger()))
                    return false

                val tilesUnitsWerePlacedOn: MutableList<Vector2> = mutableListOf()
                for (i in 1..unique.params[0].toInt()) {
                    val placedUnit = civInfo.addUnit(unitName, chosenCity)
                    if (placedUnit != null)
                        tilesUnitsWerePlacedOn.add(placedUnit.getTile().position)
                }
                if (notification != null && tilesUnitsWerePlacedOn.isNotEmpty()) {
                    civInfo.addNotification(
                        notification,
                        LocationAction(tilesUnitsWerePlacedOn),
                        civInfo.getEquivalentUnit(unit).name
                    )
                }
                return true
            }
            OneTimeFreeUnitRuins -> {
                var unit = civInfo.getEquivalentUnit(unique.params[0])
                if ( unit.hasUnique(UniqueType.FoundCity) && civInfo.isOneCityChallenger()) {
                     val replacementUnit = ruleSet.units.values.firstOrNull{it.getMatchingUniques(UniqueType.BuildImprovements)
                            .any { it.params[0] == "Land" }} ?: return false
                    unit = civInfo.getEquivalentUnit(replacementUnit.name)
                }

                val placingTile =
                    tile ?: civInfo.cities.random().getCenterTile()

                val placedUnit = civInfo.placeUnitNearTile(placingTile.position, unit.name)
                if (notification != null && placedUnit != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(unique.params[0])
                        else notification
                    civInfo.addNotification(
                        notificationText,
                        LocationAction(placedUnit.getTile().position, tile?.position),
                        placedUnit.name
                    )
                }

                return placedUnit != null
            }

            OneTimeFreePolicy -> {
                // spectators get all techs at start of game, and if (in a mod) a tech gives a free policy, the game gets stuck on the policy picker screen
                if (civInfo.isSpectator()) return false
                civInfo.policies.freePolicies++
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.Culture)
                }
                return true
            }
            OneTimeAmountFreePolicies -> {
                if (civInfo.isSpectator()) return false
                civInfo.policies.freePolicies += unique.params[0].toInt()
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.Culture)
                }
                return true
            }
            OneTimeEnterGoldenAge -> {
                civInfo.goldenAges.enterGoldenAge()
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.Happiness)
                }
                return true
            }

            OneTimeFreeGreatPerson, MayanGainGreatPerson -> {
                if (civInfo.isSpectator()) return false
                val greatPeople = civInfo.getGreatPeople()
                if (unique.type == MayanGainGreatPerson && civInfo.greatPeople.longCountGPPool.isEmpty())
                    civInfo.greatPeople.longCountGPPool = greatPeople.map { it.name }.toHashSet()
                if (civInfo.isPlayerCivilization()) {
                    civInfo.greatPeople.freeGreatPeople++
                    // Anyone an idea for a good icon?
                    if (unique.type == MayanGainGreatPerson) {
                        civInfo.greatPeople.mayaLimitedFreeGP++
                        civInfo.addNotification(notification!!, MayaLongCountAction(), MayaCalendar.notificationIcon)
                    } else if (notification != null)
                        civInfo.addNotification(notification)
                    return true
                } else {
                    if (unique.type == MayanGainGreatPerson)
                        greatPeople.removeAll { it.name !in civInfo.greatPeople.longCountGPPool }
                    if (greatPeople.isEmpty()) return false
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

                    if (unique.type == MayanGainGreatPerson)
                        civInfo.greatPeople.longCountGPPool.remove(greatPerson.name)
                    return civInfo.addUnit(greatPerson.name, chosenCity) != null
                }
            }

            OneTimeGainPopulation -> {
                val citiesWithPopulationChanged: MutableList<Vector2> = mutableListOf()
                val applicableCities = when (unique.params[1]) {
                    "in this city" -> sequenceOf(cityInfo!!)
                    "in other cities" -> civInfo.cities.asSequence().filter { it != cityInfo }
                    else -> civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                }
                for (city in applicableCities) {
                    city.population.addPopulation(unique.params[0].toInt())
                }
                if (notification != null && applicableCities.any())
                    civInfo.addNotification(
                        notification,
                        LocationAction(applicableCities.map { it.location }),
                        NotificationIcon.Population
                    )
                return citiesWithPopulationChanged.isNotEmpty()
            }
            OneTimeGainPopulationRandomCity -> {
                if (civInfo.cities.isEmpty()) return false
                val randomCity = civInfo.cities.random(tileBasedRandom)
                randomCity.population.addPopulation(unique.params[0].toInt())
                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(randomCity.name)
                        else notification
                    civInfo.addNotification(
                        notificationText,
                        LocationAction(randomCity.location, tile?.position),
                        NotificationIcon.Population
                    )
                }
                return true
            }

            OneTimeFreeTech -> {
                if (civInfo.isSpectator()) return false
                civInfo.tech.freeTechs += 1
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.Science)
                }
                return true
            }
            OneTimeAmountFreeTechs -> {
                if (civInfo.isSpectator()) return false
                civInfo.tech.freeTechs += unique.params[0].toInt()
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.Science)
                }
                return true
            }
            OneTimeFreeTechRuins -> {
                val researchableTechsFromThatEra = ruleSet.technologies.values
                    .filter {
                        (it.column!!.era == unique.params[1] || unique.params[1] == "any era")
                                && civInfo.tech.canBeResearched(it.name)
                    }
                if (researchableTechsFromThatEra.isEmpty()) return false

                val techsToResearch = researchableTechsFromThatEra.shuffled(tileBasedRandom)
                    .take(unique.params[0].toInt())
                for (tech in techsToResearch)
                    civInfo.tech.addTechnology(tech.name)

                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(*(techsToResearch.map { it.name }
                                .toTypedArray()))
                        else notification
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationIcon.Science)
                }

                return true
            }

            StrategicResourcesIncrease -> {
                civInfo.updateDetailedCivResources()
                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        NotificationIcon.War
                    ) // I'm open for better icons
                }
                return true
            }

            OneTimeRevealEntireMap -> {
                if (notification != null) {
                    civInfo.addNotification(notification, LocationAction(tile?.position), NotificationIcon.Scout)
                }
                return civInfo.exploredTiles.addAll(
                    civInfo.gameInfo.tileMap.values.asSequence().map { it.position })
            }

            UnitsGainPromotion -> {
                val filter = unique.params[0]
                val promotion = unique.params[1]

                val promotedUnitLocations: MutableList<Vector2> = mutableListOf()
                for (unit in civInfo.getCivUnits()) {
                    if (unit.matchesFilter(filter)
                        && ruleSet.unitPromotions.values.any {
                            it.name == promotion && unit.type.name in it.unitTypes
                        }
                    ) {
                        unit.promotions.addPromotion(promotion, isFree = true)
                        promotedUnitLocations.add(unit.getTile().position)
                    }
                }

                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        LocationAction(promotedUnitLocations),
                        "unitPromotionIcons/${unique.params[1]}"
                    )
                }
                return promotedUnitLocations.isNotEmpty()
            }

            CityStateCanGiftGreatPeople -> {
                civInfo.addFlag(
                    CivFlags.CityStateGreatPersonGift.name,
                    civInfo.turnsForGreatPersonFromCityState() / 2
                )
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.CityState)
                }
                return true
            }
            // The mechanics for granting great people are wonky, but basically the following happens:
            // Based on the game speed, a timer with some amount of turns is set, 40 on regular speed
            // Every turn, 1 is subtracted from this timer, as long as you have at least 1 city state ally
            // So no, the number of city-state allies does not matter for this. You have a global timer for all of them combined.
            // If the timer reaches the amount of city-state allies you have (or 10, whichever is lower), it is reset.
            // You will then receive a random great person from a random city-state you are allied to
            // The very first time after acquiring this policy, the timer is set to half of its normal value
            // This is the basics, and apart from this, there is some randomness in the exact turn count, but I don't know how much

            // There is surprisingly little information findable online about this policy, and the civ 5 source files are
            // also quite though to search through, so this might all be incorrect.
            // For now this mechanic seems decent enough that this is fine.

            // Note that the way this is implemented now, this unique does NOT stack
            // I could parametrize the [Allied], but eh.

            OneTimeGainStat -> {
                val stat = Stat.safeValueOf(unique.params[1]) ?: return false

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                ) return false

                civInfo.addStat(stat, unique.params[0].toInt())
                if (notification != null)
                    civInfo.addNotification(notification, LocationAction(tile?.position), stat.notificationIcon)
                return true
            }
            OneTimeGainStatRange -> {
                val stat = Stat.safeValueOf(unique.params[2]) ?: return false

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                    || unique.params[1].toIntOrNull() == null
                ) return false

                val foundStatAmount =
                    (tileBasedRandom.nextInt(unique.params[0].toInt(), unique.params[1].toInt()) *
                            civInfo.gameInfo.speed.statCostModifiers[stat]!!
                            ).toInt()

                civInfo.addStat(
                    Stat.valueOf(unique.params[2]),
                    foundStatAmount
                )

                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters()) {
                            notification.fillPlaceholders(foundStatAmount.toString())
                        } else notification
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), stat.notificationIcon)
                }

                return true
            }
            OneTimeGainPantheon -> {
                if (civInfo.religionManager.religionState != ReligionState.None) return false
                val gainedFaith = civInfo.religionManager.faithForPantheon(2)
                if (gainedFaith == 0) return false

                civInfo.addStat(Stat.Faith, gainedFaith)

                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(gainedFaith.toString())
                        else notification
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationIcon.Faith)
                }

                return true
            }
            OneTimeGainProphet -> {
                if (civInfo.religionManager.getGreatProphetEquivalent() == null) return false
                val gainedFaith =
                    (civInfo.religionManager.faithForNextGreatProphet() * (unique.params[0].toFloat() / 100f)).toInt()
                if (gainedFaith == 0) return false

                civInfo.addStat(Stat.Faith, gainedFaith)

                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(gainedFaith.toString())
                        else notification
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationIcon.Faith)
                }

                return true
            }
            OneTimeFreeBelief -> {
                if (!civInfo.isMajorCiv()) return false
                val beliefType = BeliefType.valueOf(unique.params[0])
                val religionManager = civInfo.religionManager
                if ((beliefType != BeliefType.Pantheon && beliefType != BeliefType.Any)
                        && religionManager.religionState <= ReligionState.Pantheon)
                    return false // situation where we're trying to add a formal religion belief to a civ that hasn't founded a religion
                if (religionManager.numberOfBeliefsAvailable(beliefType) == 0)
                    return false // no more available beliefs of this type

                if (beliefType == BeliefType.Any && religionManager.religionState <= ReligionState.Pantheon)
                    religionManager.freeBeliefs.add(BeliefType.Pantheon.name, 1) // add pantheon instead of any type
                else
                    religionManager.freeBeliefs.add(beliefType.name, 1)
                return true
            }

            OneTimeRevealSpecificMapTiles -> {
                if (tile == null) return false
                val nearbyRevealableTiles = tile
                    .getTilesInDistance(unique.params[2].toInt())
                    .filter {
                        !civInfo.exploredTiles.contains(it.position) &&
                        it.matchesFilter(unique.params[1])
                    }
                    .map { it.position }
                if (nearbyRevealableTiles.none()) return false
                val revealedTiles = nearbyRevealableTiles
                        .shuffled(tileBasedRandom)
                        .apply {
                            // Implements [UniqueParameterType.CombatantFilter] - At the moment the only use
                            if (unique.params[0] != "All") this.take(unique.params[0].toInt())
                        }
                for (position in revealedTiles) {
                    civInfo.exploredTiles.add(position)
                    val revealedTileInfo = civInfo.gameInfo.tileMap[position]
                    if (revealedTileInfo.improvement == null)
                        civInfo.lastSeenImprovement.remove(position)
                    else
                        civInfo.lastSeenImprovement[position] = revealedTileInfo.improvement!!
                }

                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        LocationAction(nearbyRevealableTiles),
                        if (unique.params[1] == Constants.barbarianEncampment)
                            NotificationIcon.Barbarians else NotificationIcon.Scout
                    )
                }

                return true
            }
            OneTimeRevealCrudeMap -> {
                if (tile == null) return false
                val revealCenter = tile.getTilesAtDistance(unique.params[0].toInt())
                    .filter { it.position !in civInfo.exploredTiles }
                    .toList()
                    .randomOrNull(tileBasedRandom)
                    ?: return false
                val tilesToReveal = revealCenter
                    .getTilesInDistance(unique.params[1].toInt())
                    .map { it.position }
                    .filter { tileBasedRandom.nextFloat() < unique.params[2].toFloat() / 100f }
                civInfo.exploredTiles.addAll(tilesToReveal)
                civInfo.updateViewableTiles()
                if (notification != null)
                    civInfo.addNotification(
                        notification,
                        tile.position,
                        NotificationIcon.Ruins
                    )
                return true
            }

            OneTimeTriggerVoting -> {
                for (civ in civInfo.gameInfo.civilizations)
                    if (!civ.isBarbarian() && !civ.isSpectator())
                        civ.addFlag(
                            CivFlags.TurnsTillNextDiplomaticVote.name,
                            civInfo.getTurnsBetweenDiplomaticVotes()
                        )
                if (notification != null)
                    civInfo.addNotification(notification, NotificationIcon.Diplomacy)
                return true
            }

            OneTimeGlobalSpiesWhenEnteringEra -> {
                if (!civInfo.isMajorCiv()) return false
                if (!civInfo.gameInfo.isEspionageEnabled()) return false
                val currentEra = civInfo.getEra().name
                for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
                    if (currentEra !in otherCiv.espionageManager.erasSpyEarnedFor) {
                        val spyName = otherCiv.espionageManager.addSpy()
                        otherCiv.espionageManager.erasSpyEarnedFor.add(currentEra)
                        if (otherCiv == civInfo || otherCiv.knows(civInfo))
                            otherCiv.addNotification("We have recruited [${spyName}] as a spy!", NotificationIcon.Spy)
                        else
                            otherCiv.addNotification("After an unknown civilization entered the [${currentEra}], we have recruited [${spyName}] as a spy!", NotificationIcon.Spy)
                    }
                }
                return true
            }

            FreeStatBuildings, FreeSpecificBuildings -> {
                civInfo.civConstructions.tryAddFreeBuildings()
                return true // not fully correct
            }

            else -> {}
        }
        return false
    }

    /** @return boolean whether an action was successfully performed */
    fun triggerUnitwideUnique(
        unique: Unique,
        unit: MapUnit,
        notification: String? = null
    ): Boolean {
        when (unique.type) {
            OneTimeUnitHeal -> {
                unit.healBy(unique.params[0].toInt())
                if (notification != null)
                    unit.civInfo.addNotification(notification, unit.getTile().position) // Do we have a heal icon?
                return true
            }
            OneTimeUnitGainXP -> {
                if (!unit.baseUnit.isMilitary()) return false
                unit.promotions.XP += unique.params[0].toInt()
                if (notification != null)
                    unit.civInfo.addNotification(notification, unit.getTile().position)
                return true
            }
            OneTimeUnitUpgrade -> {
                val upgradeAction = UnitActions.getFreeUpgradeAction(unit)
                    ?: return false
                upgradeAction.action!!()
                if (notification != null)
                    unit.civInfo.addNotification(notification, unit.getTile().position)
                return true
            }
            OneTimeUnitSpecialUpgrade -> {
                val upgradeAction = UnitActions.getAncientRuinsUpgradeAction(unit)
                    ?: return false
                upgradeAction.action!!()
                if (notification != null)
                    unit.civInfo.addNotification(notification, unit.getTile().position)
                return true
            }
            OneTimeUnitGainPromotion -> {
                val promotion = unit.civInfo.gameInfo.ruleSet.unitPromotions.keys
                    .firstOrNull { it == unique.params[0] }
                    ?: return false
                unit.promotions.addPromotion(promotion, true)
                if (notification != null)
                    unit.civInfo.addNotification(notification, unit.getTile().position, unit.name)
                return true
            }
            else -> return false
        }
    }
}

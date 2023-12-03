package com.unciv.models.ruleset.unique

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.MayaLongCountAction
import com.unciv.logic.civilization.NotificationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PolicyAction
import com.unciv.logic.civilization.TechAction
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Victory
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.hasPlaceholderParameters
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.extensions.addToMapOfSets
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import kotlin.math.roundToInt
import kotlin.random.Random

// Buildings, techs, policies, ancient ruins and promotions can have 'triggered' effects
object UniqueTriggerActivation {
    /** @return whether an action was successfully performed */
    fun triggerCivwideUnique(
        unique: Unique,
        civInfo: Civilization,
        city: City? = null,
        tile: Tile? = city?.getCenterTile(),
        notification: String? = null,
        triggerNotificationText: String? = null
    ): Boolean {
        val timingConditional = unique.conditionals.firstOrNull { it.type == UniqueType.ConditionalTimedUnique }
        if (timingConditional != null) {
            civInfo.temporaryUniques.add(TemporaryUnique(unique, timingConditional.params[0].toInt()))
            return true
        }

        if (!unique.conditionalsApply(civInfo, city)) return false

        val chosenCity = city ?: civInfo.cities.firstOrNull { it.isCapital() }
        val tileBasedRandom =
            if (tile != null) Random(tile.position.toString().hashCode())
            else Random(-550) // Very random indeed
        val ruleSet = civInfo.gameInfo.ruleset

        when (unique.type) {
            UniqueType.OneTimeFreeUnit -> {
                val unitName = unique.params[0]
                val unit = ruleSet.units[unitName]
                if ((chosenCity == null && tile == null)
                        || unit == null
                        || unit.isCityFounder() && civInfo.isOneCityChallenger())
                    return false

                val limit = unit.getMatchingUniques(UniqueType.MaxNumberBuildable)
                    .map { it.params[0].toInt() }.minOrNull()
                if (limit != null && limit <= civInfo.units.getCivUnits().count { it.name == unitName })
                    return false

                val placedUnit = if (city != null || tile == null)
                    civInfo.units.addUnit(unitName, chosenCity) ?: return false
                    else civInfo.units.placeUnitNearTile(tile.position, unitName) ?: return false

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "Gained [1] [$unitName] unit(s)")
                    ?: return true

                civInfo.addNotification(
                    notificationText,
                    MapUnitAction(placedUnit),
                    NotificationCategory.Units,
                    placedUnit.name
                )
                return true
            }
            UniqueType.OneTimeAmountFreeUnits -> {
                val unitName = unique.params[1]
                val unit = ruleSet.units[unitName]
                if ((chosenCity == null && tile == null) || unit == null || (unit.isCityFounder() && civInfo.isOneCityChallenger()))
                    return false

                val limit = unit.getMatchingUniques(UniqueType.MaxNumberBuildable)
                    .map { it.params[0].toInt() }.minOrNull()
                val unitCount = civInfo.units.getCivUnits().count { it.name == unitName }
                val amountFromTriggerable = unique.params[0].toInt()
                val actualAmount = when {
                    limit == null -> amountFromTriggerable
                    amountFromTriggerable + unitCount > limit -> limit - unitCount
                    else -> amountFromTriggerable
                }

                if (actualAmount <= 0) return false

                val tilesUnitsWerePlacedOn: MutableList<Vector2> = mutableListOf()
                repeat(actualAmount) {
                    val placedUnit = if (city != null || tile == null) civInfo.units.addUnit(unitName, chosenCity)
                        else civInfo.units.placeUnitNearTile(tile.position, unitName)
                    if (placedUnit != null)
                        tilesUnitsWerePlacedOn.add(placedUnit.getTile().position)
                }
                if (tilesUnitsWerePlacedOn.isEmpty()) return true

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "Gained [${tilesUnitsWerePlacedOn.size}] [$unitName] unit(s)")
                    ?: return true

                civInfo.addNotification(
                    notificationText,
                    MapUnitAction(tilesUnitsWerePlacedOn),
                    NotificationCategory.Units,
                    civInfo.getEquivalentUnit(unit).name
                )
                return true
            }
            UniqueType.OneTimeFreeUnitRuins -> {
                var unit = civInfo.getEquivalentUnit(unique.params[0])
                if ( unit.isCityFounder() && civInfo.isOneCityChallenger()) {
                     val replacementUnit = ruleSet.units.values
                         .firstOrNull {
                             it.getMatchingUniques(UniqueType.BuildImprovements)
                                .any { unique -> unique.params[0] == "Land" }
                         } ?: return false
                    unit = civInfo.getEquivalentUnit(replacementUnit.name)
                }

                val placingTile =
                    tile ?: civInfo.cities.random().getCenterTile()

                val placedUnit = civInfo.units.placeUnitNearTile(placingTile.position, unit.name)
                if (notification != null && placedUnit != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(unique.params[0])
                        else notification
                    civInfo.addNotification(
                        notificationText,
                        sequence {
                            yield(MapUnitAction(placedUnit))
                            yieldAll(LocationAction(tile?.position))
                        },
                        NotificationCategory.Units,
                        placedUnit.name
                    )
                }

                return placedUnit != null
            }

            UniqueType.OneTimeFreePolicy -> {
                // spectators get all techs at start of game, and if (in a mod) a tech gives a free policy, the game gets stuck on the policy picker screen
                if (civInfo.isSpectator()) return false
                civInfo.policies.freePolicies++

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You may choose a free Policy")
                    ?: return true

                civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Culture)
                return true
            }
            UniqueType.OneTimeAmountFreePolicies -> {
                if (civInfo.isSpectator()) return false
                val newFreePolicies = unique.params[0].toInt()
                civInfo.policies.freePolicies += newFreePolicies

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You may choose [$newFreePolicies] free Policies")
                    ?: return true

                civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Culture)
                return true
            }
            UniqueType.OneTimeAdoptPolicy -> {
                val policyName = unique.params[0]
                if (civInfo.policies.isAdopted(policyName)) return false
                val policy = civInfo.gameInfo.ruleset.policies[policyName] ?: return false
                civInfo.policies.freePolicies++
                civInfo.policies.adopt(policy)

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You gain the [$policyName] Policy")
                    ?: return true

                civInfo.addNotification(notificationText, PolicyAction(policyName), NotificationCategory.General, NotificationIcon.Culture)
                return true
            }
            UniqueType.OneTimeEnterGoldenAge, UniqueType.OneTimeEnterGoldenAgeTurns -> {
                if (unique.type == UniqueType.OneTimeEnterGoldenAgeTurns) civInfo.goldenAges.enterGoldenAge(unique.params[0].toInt())
                else civInfo.goldenAges.enterGoldenAge()

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You enter a Golden Age")
                    ?: return true

                civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Happiness)
                return true
            }

            UniqueType.OneTimeFreeGreatPerson, UniqueType.MayanGainGreatPerson -> {
                if (civInfo.isSpectator()) return false
                val greatPeople = civInfo.greatPeople.getGreatPeople()
                if (unique.type == UniqueType.MayanGainGreatPerson && civInfo.greatPeople.longCountGPPool.isEmpty())
                    civInfo.greatPeople.longCountGPPool = greatPeople.map { it.name }.toHashSet()
                if (civInfo.isHuman() && !UncivGame.Current.settings.autoPlay.isAutoPlayingAndFullAI()) {
                    civInfo.greatPeople.freeGreatPeople++
                    // Anyone an idea for a good icon?
                    if (unique.type == UniqueType.MayanGainGreatPerson) {
                        civInfo.greatPeople.mayaLimitedFreeGP++
                        civInfo.addNotification(notification!!, MayaLongCountAction(), NotificationCategory.General, MayaCalendar.notificationIcon)
                    } else if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.General)
                    return true
                } else {
                    if (unique.type == UniqueType.MayanGainGreatPerson)
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

                    if (unique.type == UniqueType.MayanGainGreatPerson)
                        civInfo.greatPeople.longCountGPPool.remove(greatPerson.name)
                    return civInfo.units.addUnit(greatPerson.name, chosenCity) != null
                }
            }

            UniqueType.OneTimeGainPopulation -> {
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(city!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                for (applicableCity in applicableCities) {
                    applicableCity.population.addPopulation(unique.params[0].toInt())
                }
                if (notification != null && applicableCities.any())
                    civInfo.addNotification(
                        notification,
                        LocationAction(applicableCities.map { it.location }),
                        NotificationCategory.Cities,
                        NotificationIcon.Population
                    )
                return applicableCities.any()
            }
            UniqueType.OneTimeGainPopulationRandomCity -> {
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
                        NotificationCategory.Cities,
                        NotificationIcon.Population
                    )
                }
                return true
            }

            UniqueType.OneTimeFreeTech -> {
                if (civInfo.isSpectator()) return false
                civInfo.tech.freeTechs += 1
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Science)
                }
                return true
            }
            UniqueType.OneTimeAmountFreeTechs -> {
                if (civInfo.isSpectator()) return false
                civInfo.tech.freeTechs += unique.params[0].toInt()
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Science)
                }
                return true
            }
            UniqueType.OneTimeFreeTechRuins -> {
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
                    // Notification click for first tech only, supporting multiple adds little value.
                    // Relies on RulesetValidator catching <= 0!
                    val notificationActions: Sequence<NotificationAction> =
                        LocationAction(tile?.position) + TechAction(techsToResearch.first().name)
                    civInfo.addNotification(notificationText, notificationActions,
                        NotificationCategory.General, NotificationIcon.Science)
                }

                return true
            }
            UniqueType.OneTimeDiscoverTech -> {
                val techName = unique.params[0]
                if (civInfo.tech.isResearched(techName)) return false
                civInfo.tech.addTechnology(techName)

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You have discovered the secrets of [$techName]")
                    ?: return true

                civInfo.addNotification(notificationText, TechAction(techName), NotificationCategory.General, NotificationIcon.Science)
                return true
            }

            UniqueType.StrategicResourcesIncrease -> {
                civInfo.cache.updateCivResources()
                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        NotificationCategory.General,
                        NotificationIcon.Construction
                    )
                }
                return true
            }

            UniqueType.OneTimeProvideResources -> {
                val resourceName = unique.params[1]
                val resource = ruleSet.tileResources[resourceName] ?: return false
                if (!resource.isStockpiled()) return false

                val amount = unique.params[0].toInt()
                civInfo.resourceStockpiles.add(resourceName, amount)

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You have gained [$amount] [$resourceName]")
                    ?: return true

                civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Science, "ResourceIcons/$resourceName")
                return true
            }

            UniqueType.OneTimeConsumeResources -> {
                val resourceName = unique.params[1]
                val resource = ruleSet.tileResources[resourceName] ?: return false
                if (!resource.isStockpiled()) return false

                val amount = unique.params[0].toInt()
                civInfo.resourceStockpiles.add(resourceName, -amount)

                val notificationText = getNotificationText(notification, triggerNotificationText,
                    "You have lost [$amount] [$resourceName]")
                    ?: return true

                civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Science, "ResourceIcons/$resourceName")
                return true
            }

            UniqueType.OneTimeRevealEntireMap -> {
                if (notification != null) {
                    civInfo.addNotification(notification, LocationAction(tile?.position), NotificationCategory.General, NotificationIcon.Scout)
                }
                civInfo.gameInfo.tileMap.values.asSequence()
                    .forEach { it.setExplored(civInfo, true) }
                return true
            }

            UniqueType.UnitsGainPromotion -> {
                val filter = unique.params[0]
                val promotion = unique.params[1]

                val promotedUnitLocations: MutableList<Vector2> = mutableListOf()
                for (unit in civInfo.units.getCivUnits()) {
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
                        MapUnitAction(promotedUnitLocations),
                        NotificationCategory.Units,
                        "unitPromotionIcons/${unique.params[1]}"
                    )
                }
                return promotedUnitLocations.isNotEmpty()
            }

            /**
             * The mechanics for granting great people are wonky, but basically the following happens:
             * Based on the game speed, a timer with some amount of turns is set, 40 on regular speed
             * Every turn, 1 is subtracted from this timer, as long as you have at least 1 city state ally
             * So no, the number of city-state allies does not matter for this. You have a global timer for all of them combined.
             * If the timer reaches the amount of city-state allies you have (or 10, whichever is lower), it is reset.
             * You will then receive a random great person from a random city-state you are allied to
             * The very first time after acquiring this policy, the timer is set to half of its normal value
             * This is the basics, and apart from this, there is some randomness in the exact turn count, but I don't know how much
             * There is surprisingly little information findable online about this policy, and the civ 5 source files are
             *  also quite tough to search through, so this might all be incorrect.
             * For now this mechanic seems decent enough that this is fine.
             * Note that the way this is implemented now, this unique does NOT stack
             * I could parametrize the 'Allied' of the Unique text, but eh.
             */
            UniqueType.CityStateCanGiftGreatPeople -> {
                civInfo.addFlag(
                    CivFlags.CityStateGreatPersonGift.name,
                    civInfo.cityStateFunctions.turnsForGreatPersonFromCityState() / 2
                )
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationCategory.Diplomacy, NotificationIcon.CityState)
                }
                return true
            }

            UniqueType.OneTimeGainStat -> {
                val stat = Stat.safeValueOf(unique.params[1]) ?: return false

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                ) return false

                val statAmount = unique.params[0].toInt()
                val stats = Stats().add(stat, statAmount.toFloat())
                civInfo.addStats(stats)

                val filledNotification = if(notification!=null && notification.hasPlaceholderParameters())
                    notification.fillPlaceholders(statAmount.toString())
                else notification

                val notificationText = getNotificationText(filledNotification, triggerNotificationText,
                    "Gained [${stats.toStringForNotifications()}]")
                    ?: return true

                civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)
                return true
            }

            UniqueType.OneTimeGainStatSpeed -> {
                val stat = Stat.safeValueOf(unique.params[1]) ?: return false

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                ) return false

                val statAmount = (unique.params[0].toInt() * (civInfo.gameInfo.speed.statCostModifiers[stat]!!)).roundToInt()
                val stats = Stats().add(stat, statAmount.toFloat())
                civInfo.addStats(stats)

                val filledNotification = if(notification!=null && notification.hasPlaceholderParameters())
                    notification.fillPlaceholders(statAmount.toString())
                else notification

                val notificationText = getNotificationText(filledNotification, triggerNotificationText,
                    "Gained [${stats.toStringForNotifications()}]")
                    ?: return true

                civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)
                return true
            }

            UniqueType.OneTimeGainStatRange -> {
                val stat = Stat.safeValueOf(unique.params[2]) ?: return false

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                    || unique.params[1].toIntOrNull() == null
                ) return false

                val finalStatAmount = (tileBasedRandom.nextInt(unique.params[0].toInt(), unique.params[1].toInt()) *
                                civInfo.gameInfo.speed.statCostModifiers[stat]!!).roundToInt()

                val stats = Stats().add(stat, finalStatAmount.toFloat())
                civInfo.addStats(stats)

                val filledNotification = if (notification!=null && notification.hasPlaceholderParameters())
                    notification.fillPlaceholders(finalStatAmount.toString())
                else notification

                val notificationText = getNotificationText(filledNotification, triggerNotificationText,
                    "Gained [${stats.toStringForNotifications()}]")
                    ?: return true

                civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)

                return true
            }
            UniqueType.OneTimeGainPantheon -> {
                if (civInfo.religionManager.religionState != ReligionState.None) return false
                val gainedFaith = civInfo.religionManager.faithForPantheon(2)
                if (gainedFaith == 0) return false

                civInfo.addStat(Stat.Faith, gainedFaith)

                if (notification != null) {
                    val notificationText =
                        if (notification.hasPlaceholderParameters())
                            notification.fillPlaceholders(gainedFaith.toString())
                        else notification
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.Religion, NotificationIcon.Faith)
                }

                return true
            }
            UniqueType.OneTimeGainProphet -> {
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
                    civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.Religion, NotificationIcon.Faith)
                }

                return true
            }
            UniqueType.OneTimeFreeBelief -> {
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

            UniqueType.OneTimeRevealSpecificMapTiles -> {

                if (tile == null)
                    return false

                // "Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius"
                val amount = unique.params[0]
                val filter = unique.params[1]
                val radius = unique.params[2].toInt()

                val isAll = amount == "All"
                val positions = ArrayList<Vector2>()

                var explorableTiles = tile.getTilesInDistance(radius)
                    .filter { !it.isExplored(civInfo) && it.matchesFilter(filter) }

                if (explorableTiles.none())
                    return false

                if (!isAll) {
                    explorableTiles.shuffled(tileBasedRandom)
                    explorableTiles = explorableTiles.take(amount.toInt())
                }

                for (explorableTile in explorableTiles) {
                    explorableTile.setExplored(civInfo, true)
                    positions += explorableTile.position
                    if (explorableTile.improvement == null)
                        civInfo.lastSeenImprovement.remove(explorableTile.position)
                    else
                        civInfo.lastSeenImprovement[explorableTile.position] = explorableTile.improvement!!
                }

                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        LocationAction(positions),
                        NotificationCategory.War,
                        if (unique.params[1] == Constants.barbarianEncampment)
                            NotificationIcon.Barbarians else NotificationIcon.Scout
                    )
                }

                return true
            }
            UniqueType.OneTimeRevealCrudeMap -> {
                if (tile == null)
                    return false

                // "From a randomly chosen tile [amount] tiles away from the ruins,
                // reveal tiles up to [amount] tiles away with [amount]% chance"

                val distance = unique.params[0].toInt()
                val radius = unique.params[1].toInt()
                val chance = unique.params[2].toFloat() / 100f

                val revealCenter = tile.getTilesAtDistance(distance)
                    .filter { !it.isExplored(civInfo) }
                    .toList()
                    .randomOrNull(tileBasedRandom)
                    ?: return false
                revealCenter.getTilesInDistance(radius)
                    .filter { tileBasedRandom.nextFloat() < chance }
                    .forEach { it.setExplored(civInfo, true) }
                civInfo.cache.updateViewableTiles()
                if (notification != null)
                    civInfo.addNotification(
                        notification,
                        tile.position,
                        NotificationCategory.General,
                        NotificationIcon.Ruins
                    )
                return true
            }

            UniqueType.OneTimeTriggerVoting -> {
                for (civ in civInfo.gameInfo.civilizations)
                    if (!civ.isBarbarian() && !civ.isSpectator())
                        civ.addFlag(
                            CivFlags.TurnsTillNextDiplomaticVote.name,
                            civInfo.getTurnsBetweenDiplomaticVotes()
                        )
                if (notification != null)
                    civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Diplomacy)
                return true
            }

            UniqueType.OneTimeGlobalSpiesWhenEnteringEra -> {
                if (!civInfo.isMajorCiv()) return false
                if (!civInfo.gameInfo.isEspionageEnabled()) return false
                val currentEra = civInfo.getEra().name
                for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
                    if (currentEra !in otherCiv.espionageManager.erasSpyEarnedFor) {
                        val spyName = otherCiv.espionageManager.addSpy()
                        otherCiv.espionageManager.erasSpyEarnedFor.add(currentEra)
                        if (otherCiv == civInfo || otherCiv.knows(civInfo))
                            // We don't tell which civilization entered the new era, as that is done in the notification directly above this one
                            otherCiv.addNotification("We have recruited [${spyName}] as a spy!", NotificationCategory.Espionage, NotificationIcon.Spy)
                        else
                            otherCiv.addNotification("After an unknown civilization entered the [${currentEra}], we have recruited [${spyName}] as a spy!", NotificationCategory.Espionage, NotificationIcon.Spy)
                    }
                }
                return true
            }

            UniqueType.GainFreeBuildings -> {
                val freeBuilding = civInfo.getEquivalentBuilding(unique.params[0])
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(city!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                for (applicableCity in applicableCities) {
                    applicableCity.cityConstructions.freeBuildingsProvidedFromThisCity.addToMapOfSets(applicableCity.id, freeBuilding.name)

                    if (applicableCity.cityConstructions.containsBuildingOrEquivalent(freeBuilding.name)) continue
                    applicableCity.cityConstructions.constructionComplete(freeBuilding)
                }
                return true
            }
            UniqueType.FreeStatBuildings -> {
                val stat = Stat.safeValueOf(unique.params[0]) ?: return false
                civInfo.civConstructions.addFreeStatBuildings(stat, unique.params[1].toInt())
                return true
            }
            UniqueType.FreeSpecificBuildings ->{
                val building = ruleSet.buildings[unique.params[0]] ?: return false
                civInfo.civConstructions.addFreeBuildings(building, unique.params[1].toInt())
                return true
            }

            UniqueType.RemoveBuilding -> {

                val applicableCities = civInfo.cities.asSequence().filter {
                        it.matchesFilter(unique.params[1])
                    }

                for (applicableCity in applicableCities) {
                    val buildingsToRemove = applicableCity.cityConstructions.getBuiltBuildings().filter {
                        it.matchesFilter(unique.params[0])
                    }.toSet()

                    applicableCity.cityConstructions.removeBuildings(buildingsToRemove)
                }

                return true
            }

            UniqueType.SellBuilding -> {

                val applicableCities = civInfo.cities.asSequence().filter {
                    it.matchesFilter(unique.params[1])
                }

                for (applicableCity in applicableCities) {
                    val buildingsToSell = applicableCity.cityConstructions.getBuiltBuildings().filter {
                        it.matchesFilter(unique.params[0]) && it.isSellable()
                    }

                    for (building in buildingsToSell) {
                        applicableCity.sellBuilding(building)
                    }

                }

                return true
            }

            else -> {}
        }
        return false
    }

    private fun getNotificationText(notification: String?, triggerNotificationText: String?, effectNotificationText: String): String? {
        return if (!notification.isNullOrEmpty()) notification
        else if (triggerNotificationText != null)
        {
            if (UncivGame.Current.translations.triggerNotificationEffectBeforeCause(UncivGame.Current.settings.language))
                "{$effectNotificationText}{ }{$triggerNotificationText}"
            else "{$triggerNotificationText}{ }{$effectNotificationText}"
        }
        else null
    }

    /** @return boolean whether an action was successfully performed */
    fun triggerUnitwideUnique(
        unique: Unique,
        unit: MapUnit,
        notification: String? = null,
        triggerNotificationText: String? = null
    ): Boolean {
        when (unique.type) {
            UniqueType.OneTimeUnitHeal -> {
                unit.healBy(unique.params[0].toInt())
                if (notification != null)
                    unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units) // Do we have a heal icon?
                return true
            }
            UniqueType.OneTimeUnitGainXP -> {
                if (!unit.baseUnit.isMilitary()) return false
                unit.promotions.XP += unique.params[0].toInt()
                if (notification != null)
                    unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                return true
            }
            UniqueType.OneTimeUnitUpgrade -> {
                val upgradeAction = UnitActionsUpgrade.getFreeUpgradeAction(unit)
                    ?: return false
                upgradeAction.action!!()
                if (notification != null)
                    unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                return true
            }
            UniqueType.OneTimeUnitSpecialUpgrade -> {
                val upgradeAction = UnitActionsUpgrade.getAncientRuinsUpgradeAction(unit)
                    ?: return false
                upgradeAction.action!!()
                if (notification != null)
                    unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                return true
            }
            UniqueType.OneTimeUnitGainPromotion -> {
                val promotion = unit.civ.gameInfo.ruleset.unitPromotions.keys
                    .firstOrNull { it == unique.params[0] }
                    ?: return false
                unit.promotions.addPromotion(promotion, true)
                if (notification != null)
                    unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units, unit.name)
                return true
            }
            else -> return triggerCivwideUnique(unique, civInfo = unit.civ, tile=unit.currentTile, triggerNotificationText = triggerNotificationText)
        }
    }
}

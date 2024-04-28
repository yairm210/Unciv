package com.unciv.models.ruleset.unique

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PolicyAction
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.TechAction
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapgenerator.NaturalWonderGenerator
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.hasPlaceholderParameters
import com.unciv.ui.components.extensions.addToMapOfSets
import com.unciv.ui.screens.mapeditorscreen.TileInfoNormalizer
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import kotlin.math.roundToInt
import kotlin.random.Random

// Buildings, techs, policies, ancient ruins and promotions can have 'triggered' effects
object UniqueTriggerActivation {

    fun triggerUnique(
        unique: Unique,
        city: City,
        notification: String? = null,
        triggerNotificationText: String? = null
    ): Boolean {
        return triggerUnique(unique, city.civ, city, tile = city.getCenterTile(),
            notification = notification, triggerNotificationText = triggerNotificationText)
    }
    fun triggerUnique(
        unique: Unique,
        unit: MapUnit,
        notification: String? = null,
        triggerNotificationText: String? = null
    ): Boolean {
        return triggerUnique(unique, unit.civ, unit =  unit, tile = unit.currentTile,
            notification = notification, triggerNotificationText = triggerNotificationText)
    }

    /** @return whether an action was successfully performed */
    fun triggerUnique(
        unique: Unique,
        civInfo: Civilization,
        city: City? = null,
        unit: MapUnit? = null,
        tile: Tile? = city?.getCenterTile() ?: unit?.currentTile,
        notification: String? = null,
        triggerNotificationText: String? = null
    ): Boolean {
        val function = getTriggerFunction(unique, civInfo, city, unit, tile, notification, triggerNotificationText) ?: return false
        return function.invoke()
    }

        /** @return The action to be performed if possible, else null */
    fun getTriggerFunction(
        unique: Unique,
        civInfo: Civilization,
        city: City? = null,
        unit: MapUnit? = null,
        tile: Tile? = city?.getCenterTile() ?: unit?.currentTile,
        notification: String? = null,
        triggerNotificationText: String? = null
    ): (()->Boolean)? {

        val relevantCity by lazy {
            city?: tile?.getCity()
        }

        val timingConditional = unique.conditionals.firstOrNull { it.type == UniqueType.ConditionalTimedUnique }
        if (timingConditional != null) {
            return { civInfo.temporaryUniques.add(TemporaryUnique(unique, timingConditional.params[0].toInt())) }
        }

        val stateForConditionals = StateForConditionals(civInfo, city, unit, tile)

        if (!unique.conditionalsApply(stateForConditionals)) return null

        val chosenCity = relevantCity ?:
            civInfo.cities.firstOrNull { it.isCapital() }

        val tileBasedRandom =
            if (tile != null) Random(tile.position.toString().hashCode())
            else Random(-550) // Very random indeed
        val ruleset = civInfo.gameInfo.ruleset

        when (unique.type) {
            UniqueType.TriggerEvent -> {
                val event = ruleset.events[unique.params[0]] ?: return null
                val choices = event.getMatchingChoices(stateForConditionals)
                    ?: return null
                return {
                    if (civInfo.isAI()) choices.random().triggerChoice(civInfo)
                    else civInfo.popupAlerts.add(PopupAlert(AlertType.Event, event.name))
                    true
                }
            }

            UniqueType.OneTimeFreeUnit -> {
                val unitName = unique.params[0]
                val baseUnit = ruleset.units[unitName] ?: return null
                val civUnit = civInfo.getEquivalentUnit(baseUnit)
                if (civUnit.isCityFounder() && civInfo.isOneCityChallenger())
                    return null

                val limit = civUnit.getMatchingUniques(UniqueType.MaxNumberBuildable)
                    .map { it.params[0].toInt() }.minOrNull()
                if (limit != null && limit <= civInfo.units.getCivUnits().count { it.name == civUnit.name })
                    return null

                fun placeUnit(): Boolean {
                    val placedUnit = when {
                        // Set unit at city if there's an explict city or if there's no tile to set at
                        relevantCity != null || (tile == null && civInfo.cities.isNotEmpty()) ->
                            civInfo.units.addUnit(civUnit, chosenCity) ?: return false
                        // Else set the unit at the given tile
                        tile != null -> civInfo.units.placeUnitNearTile(tile.position, civUnit) ?: return false
                        // Else set unit unit near other units if we have no cities
                        civInfo.units.getCivUnits().any() ->
                            civInfo.units.placeUnitNearTile(civInfo.units.getCivUnits().first().currentTile.position, civUnit) ?: return false

                        else -> return false
                    }
                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "Gained [1] [${civUnit.name}] unit(s)"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(
                            notificationText,
                            MapUnitAction(placedUnit),
                            NotificationCategory.Units,
                            placedUnit.name
                        )
                    return true
                }
                return { placeUnit() }
            }

            UniqueType.OneTimeAmountFreeUnits -> {
                val unitName = unique.params[1]
                val baseUnit = ruleset.units[unitName] ?: return null
                val civUnit = civInfo.getEquivalentUnit(baseUnit)
                if (civUnit.isCityFounder() && civInfo.isOneCityChallenger())
                    return null

                val limit = civUnit.getMatchingUniques(UniqueType.MaxNumberBuildable)
                    .map { it.params[0].toInt() }.minOrNull()
                val unitCount = civInfo.units.getCivUnits().count { it.name == civUnit.name }
                val amountFromTriggerable = unique.params[0].toInt()
                val actualAmount = when {
                    limit == null -> amountFromTriggerable
                    amountFromTriggerable + unitCount > limit -> limit - unitCount
                    else -> amountFromTriggerable
                }

                if (actualAmount <= 0) return null

                fun placeUnits(): Boolean {
                    val tilesUnitsWerePlacedOn: MutableList<Vector2> = mutableListOf()
                    repeat(actualAmount) {
                        val placedUnit = when {
                            // Set unit at city if there's an explict city or if there's no tile to set at
                            relevantCity != null || (tile == null && civInfo.cities.isNotEmpty()) ->
                                civInfo.units.addUnit(civUnit, chosenCity)
                            // Else set the unit at the given tile
                            tile != null -> civInfo.units.placeUnitNearTile(tile.position, civUnit)
                            // Else set unit unit near other units if we have no cities
                            civInfo.units.getCivUnits().any() ->
                                civInfo.units.placeUnitNearTile(civInfo.units.getCivUnits().first().currentTile.position, civUnit)

                            else -> null
                        }
                        if (placedUnit != null)
                            tilesUnitsWerePlacedOn.add(placedUnit.getTile().position)
                    }
                    if (tilesUnitsWerePlacedOn.isEmpty()) return false

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "Gained [${tilesUnitsWerePlacedOn.size}] [${civUnit.name}] unit(s)"
                    )

                    if (notificationText != null)
                        civInfo.addNotification(
                            notificationText,
                            MapUnitAction(tilesUnitsWerePlacedOn),
                            NotificationCategory.Units,
                            civUnit.name
                        )
                    return true
                }
                return { placeUnits() }
            }
            UniqueType.OneTimeFreeUnitRuins -> {
                var civUnit = civInfo.getEquivalentUnit(unique.params[0])
                if ( civUnit.isCityFounder() && civInfo.isOneCityChallenger()) {
                     val replacementUnit = ruleset.units.values
                         .firstOrNull {
                             it.getMatchingUniques(UniqueType.BuildImprovements)
                                .any { unique -> unique.params[0] == "Land" }
                         } ?: return null
                    civUnit = civInfo.getEquivalentUnit(replacementUnit.name)
                }

                val placingTile =
                    tile ?: civInfo.cities.random().getCenterTile()

                fun placeUnit(): Boolean {
                    val placedUnit = civInfo.units.placeUnitNearTile(placingTile.position, civUnit.name)
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

                return {placeUnit()}
            }

            UniqueType.OneTimeFreePolicy -> {
                // spectators get all techs at start of game, and if (in a mod) a tech gives a free policy, the game gets stuck on the policy picker screen
                if (civInfo.isSpectator()) return null

                return {
                    civInfo.policies.freePolicies++

                    val notificationText = getNotificationText(notification, triggerNotificationText,
                        "You may choose a free Policy")
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Culture)
                    true
                }
            }
            UniqueType.OneTimeAmountFreePolicies -> {
                if (civInfo.isSpectator()) return null
                val newFreePolicies = unique.params[0].toInt()

                return {
                    civInfo.policies.freePolicies += newFreePolicies

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You may choose [$newFreePolicies] free Policies"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Culture)
                    true
                }
            }
            UniqueType.OneTimeAdoptPolicy -> {
                val policyName = unique.params[0]
                if (civInfo.policies.isAdopted(policyName)) return null
                val policy = civInfo.gameInfo.ruleset.policies[policyName] ?: return null

                return {
                    civInfo.policies.freePolicies++
                    civInfo.policies.adopt(policy)

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You gain the [$policyName] Policy"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, PolicyAction(policyName), NotificationCategory.General, NotificationIcon.Culture)
                    true
                }
            }
            UniqueType.OneTimeRemovePolicy -> {
                val policyFilter = unique.params[0]
                val policiesToRemove = civInfo.policies.adoptedPolicies
                    .mapNotNull { civInfo.gameInfo.ruleset.policies[it] }
                    .filter { it.matchesFilter(policyFilter) }
                if (policiesToRemove.isEmpty()) return null

                return {
                    for (policy in policiesToRemove){
                        civInfo.policies.removePolicy(policy)

                        val notificationText = getNotificationText(
                            notification, triggerNotificationText,
                            "You lose the [${policy.name}] Policy"
                        )
                        if (notificationText != null)
                            civInfo.addNotification(notificationText, PolicyAction(policy.name), NotificationCategory.General, NotificationIcon.Culture)
                    }
                    true
                }
            }
            UniqueType.OneTimeEnterGoldenAge, UniqueType.OneTimeEnterGoldenAgeTurns -> {
                return {
                    if (unique.type == UniqueType.OneTimeEnterGoldenAgeTurns) civInfo.goldenAges.enterGoldenAge(unique.params[0].toInt())
                    else civInfo.goldenAges.enterGoldenAge()

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You enter a Golden Age"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Happiness)
                    true
                }
            }

            UniqueType.OneTimeFreeGreatPerson -> {
                if (civInfo.isSpectator()) return null
                return {
                    civInfo.greatPeople.freeGreatPeople++
                    // Anyone an idea for a good icon?
                    if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.General)

                    if (civInfo.isAI() || UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == true) {
                        NextTurnAutomation.chooseGreatPerson(civInfo)
                    }
                    true
                }
            }

            UniqueType.OneTimeGainPopulation -> {
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(relevantCity!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                if (applicableCities.none()) return null
                return {
                    for (applicableCity in applicableCities) {
                        applicableCity.population.addPopulation(unique.params[0].toInt())
                    }
                    if (notification != null)
                        civInfo.addNotification(
                            notification,
                            LocationAction(applicableCities.map { it.location }),
                            NotificationCategory.Cities,
                            NotificationIcon.Population
                        )
                    true
                }
            }
            UniqueType.OneTimeGainPopulationRandomCity -> {
                if (civInfo.cities.isEmpty()) return null
                return {
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
                    true
                }
            }

            UniqueType.OneTimeFreeTech -> {
                if (civInfo.isSpectator()) return null
                return {
                    civInfo.tech.freeTechs += 1
                    if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Science)
                    true
                }
            }
            UniqueType.OneTimeAmountFreeTechs -> {
                if (civInfo.isSpectator()) return null
                return {
                    civInfo.tech.freeTechs += unique.params[0].toInt()
                    if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Science)
                    true
                }
            }
            UniqueType.OneTimeFreeTechRuins -> {
                val researchableTechsFromThatEra = ruleset.technologies.values
                    .filter {
                        (it.column!!.era == unique.params[1] || unique.params[1] == "any era")
                                && civInfo.tech.canBeResearched(it.name)
                    }
                if (researchableTechsFromThatEra.isEmpty()) return null

                return {
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
                        civInfo.addNotification(
                            notificationText, notificationActions,
                            NotificationCategory.General, NotificationIcon.Science
                        )
                    }
                    true
                }
            }
            UniqueType.OneTimeDiscoverTech -> {
                val techName = unique.params[0]
                if (civInfo.tech.isResearched(techName)) return null

                return {
                    civInfo.tech.addTechnology(techName)
                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You have discovered the secrets of [$techName]"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, TechAction(techName), NotificationCategory.General, NotificationIcon.Science)
                    true
                }
            }

            UniqueType.StrategicResourcesIncrease -> {
                return {
                    civInfo.cache.updateCivResources()
                    if (notification != null)
                        civInfo.addNotification(
                            notification,
                            NotificationCategory.General,
                            NotificationIcon.Construction
                        )
                    true
                }
            }

            UniqueType.OneTimeProvideResources -> {
                val resourceName = unique.params[1]
                val resource = ruleset.tileResources[resourceName] ?: return null
                if (!resource.isStockpiled()) return null

                return {
                    val amount = unique.params[0].toInt()
                    civInfo.resourceStockpiles.add(resourceName, amount)

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You have gained [$amount] [$resourceName]"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Science, "ResourceIcons/$resourceName")
                    true
                }
            }

            UniqueType.OneTimeConsumeResources -> {
                val resourceName = unique.params[1]
                val resource = ruleset.tileResources[resourceName] ?: return null
                if (!resource.isStockpiled()) return null

                return {
                    val amount = unique.params[0].toInt()
                    civInfo.resourceStockpiles.add(resourceName, -amount)

                    val notificationText = getNotificationText(
                        notification, triggerNotificationText,
                        "You have lost [$amount] [$resourceName]"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, NotificationCategory.General, NotificationIcon.Science, "ResourceIcons/$resourceName")
                    true
                }
            }

            UniqueType.OneTimeRevealEntireMap -> {
                return {
                    if (notification != null) {
                        civInfo.addNotification(notification, LocationAction(tile?.position), NotificationCategory.General, NotificationIcon.Scout)
                    }
                    civInfo.gameInfo.tileMap.values.asSequence()
                        .forEach { it.setExplored(civInfo, true) }
                    true
                }
            }

            UniqueType.UnitsGainPromotion -> {
                val filter = unique.params[0]
                val promotion = unique.params[1]

                val unitsToPromote = civInfo.units.getCivUnits().filter { it.matchesFilter(filter) }
                    .filter { unitToPromote ->
                        ruleset.unitPromotions.values.any {
                            it.name == promotion && unitToPromote.type.name in it.unitTypes
                        }
                    }.toList()
                if (unitsToPromote.isEmpty()) return null

                return {
                    val promotedUnitLocations: MutableList<Vector2> = mutableListOf()
                    for (civUnit in unitsToPromote) {
                        civUnit.promotions.addPromotion(promotion, isFree = true)
                        promotedUnitLocations.add(civUnit.getTile().position)
                    }

                    if (notification != null) {
                        civInfo.addNotification(
                            notification,
                            MapUnitAction(promotedUnitLocations),
                            NotificationCategory.Units,
                            "unitPromotionIcons/${unique.params[1]}"
                        )
                    }
                    true
                }
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
                return {
                    civInfo.addFlag(
                        CivFlags.CityStateGreatPersonGift.name,
                        civInfo.cityStateFunctions.turnsForGreatPersonFromCityState() / 2
                    )
                    if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.Diplomacy, NotificationIcon.CityState)
                    true
                }
            }

            UniqueType.OneTimeGainStat -> {
                val stat = Stat.safeValueOf(unique.params[1]) ?: return null

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                ) return null

                return {
                    val statAmount = unique.params[0].toInt()
                    val stats = Stats().add(stat, statAmount.toFloat())
                    civInfo.addStats(stats)

                    val filledNotification = if (notification != null && notification.hasPlaceholderParameters())
                        notification.fillPlaceholders(statAmount.toString())
                    else notification

                    val notificationText = getNotificationText(
                        filledNotification, triggerNotificationText,
                        "Gained [${stats.toStringForNotifications()}]"
                    )
                    if (notificationText != null)
                        civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)
                    true
                }
            }

            UniqueType.OneTimeGainStatSpeed -> {
                val stat = Stat.safeValueOf(unique.params[1]) ?: return null

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                ) return null

                return {
                    val statAmount = (unique.params[0].toInt() * (civInfo.gameInfo.speed.statCostModifiers[stat]!!)).roundToInt()
                    val stats = Stats().add(stat, statAmount.toFloat())
                    civInfo.addStats(stats)

                    val filledNotification = if (notification != null && notification.hasPlaceholderParameters())
                        notification.fillPlaceholders(statAmount.toString())
                    else notification

                    val notificationText = getNotificationText(
                        filledNotification, triggerNotificationText,
                        "Gained [${stats.toStringForNotifications()}]"
                    )

                    if (notificationText != null)
                        civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)
                    true
                }
            }

            UniqueType.OneTimeGainStatRange -> {
                val stat = Stat.safeValueOf(unique.params[2]) ?: return null

                if (stat !in Stat.statsWithCivWideField
                    || unique.params[0].toIntOrNull() == null
                    || unique.params[1].toIntOrNull() == null
                ) return null


                val finalStatAmount = (tileBasedRandom.nextInt(unique.params[0].toInt(), unique.params[1].toInt()) *
                                civInfo.gameInfo.speed.statCostModifiers[stat]!!).roundToInt()

                return {
                    val stats = Stats().add(stat, finalStatAmount.toFloat())
                    civInfo.addStats(stats)

                    val filledNotification = if (notification != null && notification.hasPlaceholderParameters())
                        notification.fillPlaceholders(finalStatAmount.toString())
                    else notification

                    val notificationText = getNotificationText(
                        filledNotification, triggerNotificationText,
                        "Gained [${stats.toStringForNotifications()}]"
                    )

                    if (notificationText != null)
                        civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.General, stat.notificationIcon)
                    true
                }
            }
            UniqueType.OneTimeGainPantheon -> {
                if (civInfo.religionManager.religionState != ReligionState.None) return null
                val gainedFaith = civInfo.religionManager.faithForPantheon(2)
                if (gainedFaith == 0) return null

                return {
                    civInfo.addStat(Stat.Faith, gainedFaith)

                    if (notification != null) {
                        val notificationText =
                            if (notification.hasPlaceholderParameters())
                                notification.fillPlaceholders(gainedFaith.toString())
                            else notification
                        civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.Religion, NotificationIcon.Faith)
                    }
                    true
                }
            }
            UniqueType.OneTimeGainProphet -> {
                if (civInfo.religionManager.getGreatProphetEquivalent() == null) return null
                val gainedFaith =
                    (civInfo.religionManager.faithForNextGreatProphet() * (unique.params[0].toFloat() / 100f)).toInt()
                if (gainedFaith == 0) return null

                return {
                    civInfo.addStat(Stat.Faith, gainedFaith)

                    if (notification != null) {
                        val notificationText =
                            if (notification.hasPlaceholderParameters())
                                notification.fillPlaceholders(gainedFaith.toString())
                            else notification
                        civInfo.addNotification(notificationText, LocationAction(tile?.position), NotificationCategory.Religion, NotificationIcon.Faith)
                    }
                    true
                }
            }
            UniqueType.OneTimeFreeBelief -> {
                if (!civInfo.isMajorCiv()) return null
                val beliefType = BeliefType.valueOf(unique.params[0])
                val religionManager = civInfo.religionManager
                if ((beliefType != BeliefType.Pantheon && beliefType != BeliefType.Any)
                        && religionManager.religionState <= ReligionState.Pantheon)
                    return null // situation where we're trying to add a formal religion belief to a civ that hasn't founded a religion
                if (religionManager.numberOfBeliefsAvailable(beliefType) == 0)
                    return null // no more available beliefs of this type

                return {
                    if (beliefType == BeliefType.Any && religionManager.religionState <= ReligionState.Pantheon)
                        religionManager.freeBeliefs.add(BeliefType.Pantheon.name, 1) // add pantheon instead of any type
                    else
                        religionManager.freeBeliefs.add(beliefType.name, 1)
                    true
                }
            }

            UniqueType.OneTimeRevealSpecificMapTiles -> {
                if (tile == null) return null

                // "Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius"
                val amount = unique.params[0]
                val filter = unique.params[1]
                val radius = unique.params[2].toInt()

                val isAll = amount in Constants.all
                val positions = ArrayList<Vector2>()

                var explorableTiles = tile.getTilesInDistance(radius)
                    .filter { !it.isExplored(civInfo) && it.matchesFilter(filter) }

                if (explorableTiles.none())
                    return null

                if (!isAll) {
                    explorableTiles.shuffled(tileBasedRandom)
                    explorableTiles = explorableTiles.take(amount.toInt())
                }

                return {
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
                    true
                }
            }
            UniqueType.OneTimeRevealCrudeMap -> {
                if (tile == null) return null

                // "From a randomly chosen tile [amount] tiles away from the ruins,
                // reveal tiles up to [amount] tiles away with [amount]% chance"
                val distance = unique.params[0].toInt()
                val radius = unique.params[1].toInt()
                val chance = unique.params[2].toFloat() / 100f

                val revealCenter = tile.getTilesAtDistance(distance)
                    .filter { !it.isExplored(civInfo) }
                    .toList()
                    .randomOrNull(tileBasedRandom)
                    ?: return null

                return {
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
                    true
                }
            }

            UniqueType.OneTimeTriggerVoting -> {
                return {
                    for (civ in civInfo.gameInfo.civilizations)
                        if (!civ.isBarbarian() && !civ.isSpectator())
                            civ.addFlag(
                                CivFlags.TurnsTillNextDiplomaticVote.name,
                                civInfo.getTurnsBetweenDiplomaticVotes()
                            )
                    if (notification != null)
                        civInfo.addNotification(notification, NotificationCategory.General, NotificationIcon.Diplomacy)
                    true
                }
            }

            UniqueType.OneTimeGlobalSpiesWhenEnteringEra -> {
                if (!civInfo.isMajorCiv()) return null
                if (!civInfo.gameInfo.isEspionageEnabled()) return null

                return {
                    val currentEra = civInfo.getEra().name
                    for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
                        if (currentEra !in otherCiv.espionageManager.erasSpyEarnedFor) {
                            val spyName = otherCiv.espionageManager.addSpy().name
                            otherCiv.espionageManager.erasSpyEarnedFor.add(currentEra)
                            if (otherCiv == civInfo || otherCiv.knows(civInfo))
                            // We don't tell which civilization entered the new era, as that is done in the notification directly above this one
                                otherCiv.addNotification("We have recruited [${spyName}] as a spy!", NotificationCategory.Espionage, NotificationIcon.Spy)
                            else
                                otherCiv.addNotification(
                                    "After an unknown civilization entered the [${currentEra}], we have recruited [${spyName}] as a spy!",
                                    NotificationCategory.Espionage,
                                    NotificationIcon.Spy
                                )
                        }
                    }
                    true
                }
            }

            UniqueType.OneTimeSpiesLevelUp -> {
                if (!civInfo.isMajorCiv()) return null
                if (!civInfo.gameInfo.isEspionageEnabled()) return null

                return {
                    civInfo.espionageManager.spyList.forEach { it.levelUpSpy() }
                    true
                }
            }

            UniqueType.OneTimeGainSpy -> {
                if (!civInfo.isMajorCiv()) return null
                if (!civInfo.gameInfo.isEspionageEnabled()) return null

                return {
                    civInfo.espionageManager.addSpy()
                    true
                }
            }

            UniqueType.GainFreeBuildings -> {
                val freeBuilding = civInfo.getEquivalentBuilding(unique.params[0])
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(relevantCity!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                if (applicableCities.none()) return null

                return {
                    for (applicableCity in applicableCities) {
                        applicableCity.cityConstructions.freeBuildingsProvidedFromThisCity.addToMapOfSets(applicableCity.id, freeBuilding.name)

                        if (applicableCity.cityConstructions.containsBuildingOrEquivalent(freeBuilding.name)) continue
                        applicableCity.cityConstructions.constructionComplete(freeBuilding)
                    }
                    true
                }
            }
            UniqueType.FreeStatBuildings -> {
                val stat = Stat.safeValueOf(unique.params[0]) ?: return null
                return {
                    civInfo.civConstructions.addFreeStatBuildings(stat, unique.params[1].toInt())
                    true
                }
            }
            UniqueType.FreeSpecificBuildings ->{
                val building = ruleset.buildings[unique.params[0]] ?: return null
                return {
                    civInfo.civConstructions.addFreeBuildings(building, unique.params[1].toInt())
                    true
                }
            }

            UniqueType.RemoveBuilding -> {
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(relevantCity!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                if (applicableCities.none()) return null

                return {
                    for (applicableCity in applicableCities) {
                        val buildingsToRemove = applicableCity.cityConstructions.getBuiltBuildings().filter {
                            it.matchesFilter(unique.params[0])
                        }.toSet()
                        applicableCity.cityConstructions.removeBuildings(buildingsToRemove)
                    }
                    true
                }
            }

            UniqueType.SellBuilding -> {
                val applicableCities =
                    if (unique.params[1] == "in this city") sequenceOf(relevantCity!!)
                    else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }
                if (applicableCities.none()) return null

                return {
                    for (applicableCity in applicableCities) {
                        val buildingsToSell = applicableCity.cityConstructions.getBuiltBuildings().filter {
                            it.matchesFilter(unique.params[0]) && it.isSellable()
                        }

                        for (building in buildingsToSell) applicableCity.sellBuilding(building)
                    }
                    true
                }
            }

            UniqueType.OneTimeUnitHeal -> {
                if (unit == null) return null
                if (unit.health == 100) return null
                return {
                    unit.healBy(unique.params[0].toInt())
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units) // Do we have a heal icon?
                    true
                }
            }
            UniqueType.OneTimeUnitDamage -> {
                if (unit == null) return null
                return {
                    unit.takeDamage(unique.params[0].toInt())
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units) // Do we have a heal icon?
                    true
                }
            }
            UniqueType.OneTimeUnitGainXP -> {
                if (unit == null) return null
                if (!unit.baseUnit.isMilitary()) return null
                return {
                    unit.promotions.XP += unique.params[0].toInt()
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                    true
                }
            }
            UniqueType.OneTimeUnitUpgrade -> {
                if (unit == null) return null
                val upgradeAction = UnitActionsUpgrade.getFreeUpgradeAction(unit)
                if (upgradeAction.none()) return null
                return {
                    (upgradeAction.minBy { (it as UpgradeUnitAction).unitToUpgradeTo.cost }).action!!()
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                    true
                }
            }
            UniqueType.OneTimeUnitSpecialUpgrade -> {
                if (unit == null) return null
                val upgradeAction = UnitActionsUpgrade.getAncientRuinsUpgradeAction(unit)
                if (upgradeAction.none()) return null
                return {
                    (upgradeAction.minBy { (it as UpgradeUnitAction).unitToUpgradeTo.cost }).action!!()
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units)
                    true
                }
            }
            UniqueType.OneTimeUnitGainPromotion -> {
                if (unit == null) return null
                val promotion = unit.civ.gameInfo.ruleset.unitPromotions.keys
                    .firstOrNull { it == unique.params[0] }
                    ?: return null
                return {
                    unit.promotions.addPromotion(promotion, true)
                    if (notification != null)
                        unit.civ.addNotification(notification, unit.getTile().position, NotificationCategory.Units, unit.name)
                    true
                }
            }
            UniqueType.OneTimeUnitRemovePromotion -> {
                if (unit == null) return null
                val promotion = unit.civ.gameInfo.ruleset.unitPromotions.keys
                    .firstOrNull { it == unique.params[0]}
                    ?: return null
                return {
                    unit.promotions.removePromotion(promotion)
                    true
                }
            }

            UniqueType.OneTimeChangeTerrain -> {
                if (tile == null) return null
                val terrain = ruleset.terrains[unique.params[0]] ?: return null
                if (terrain.name == Constants.river)
                    return getOneTimeChangeRiverTriggerFunction(tile)
                if (terrain.type == TerrainType.TerrainFeature && !terrain.occursOn.contains(tile.lastTerrain.name))
                    return null
                if (tile.terrainFeatures.contains(terrain.name)) return null
                if (tile.isCityCenter() && terrain.type != TerrainType.Land) return null
                if (terrain.type.isBaseTerrain && tile.baseTerrain == terrain.name) return null

                return {
                    when (terrain.type) {
                        TerrainType.Land, TerrainType.Water -> tile.setBaseTerrain(terrain)
                        TerrainType.TerrainFeature -> tile.addTerrainFeature(terrain.name)
                        TerrainType.NaturalWonder -> NaturalWonderGenerator.placeNaturalWonder(terrain, tile)
                    }
                    TileInfoNormalizer.normalizeToRuleset(tile, ruleset)
                    tile.getUnits().filter { !it.movement.canPassThrough(tile) }.toList()
                        .forEach { it.movement.teleportToClosestMoveableTile() }
                    true
                }
            }

            else -> return null
        }
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

    private fun getOneTimeChangeRiverTriggerFunction(tile: Tile): (()->Boolean)? {
        if (tile.neighbors.none { it.isLand && !tile.isConnectedByRiver(it) })
            return null  // no place for another river
        return { RiverGenerator.continueRiverOn(tile) }
    }
}

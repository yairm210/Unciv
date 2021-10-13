package com.unciv.models.ruleset.unique

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.*
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unique.UniqueType.*
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.hasPlaceholderParameters
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.worldscreen.unit.UnitActions
import kotlin.random.Random

// Buildings, techs, policies, ancient ruins and promotions can have 'triggered' effects
object UniqueTriggerActivation {
    /** @return boolean whether an action was successfully preformed */
    fun triggerCivwideUnique(
        unique: Unique,
        civInfo: CivilizationInfo,
        cityInfo: CityInfo? = null,
        tile: TileInfo? = null,
        notification: String? = null
    ): Boolean {
        val chosenCity = cityInfo ?: civInfo.cities.firstOrNull { it.isCapital() }
        val tileBasedRandom =
            if (tile != null) Random(tile.position.toString().hashCode())
            else Random(-550) // Very random indeed

        @Suppress("NON_EXHAUSTIVE_WHEN")  // Yes we're not treating all types here
        when (unique.type) {
            OneTimeFreeUnit -> {
                val unitName = unique.params[0]
                val unit = civInfo.gameInfo.ruleSet.units[unitName]
                if (chosenCity == null || unit == null || (unit.hasUnique(UniqueType.FoundCity) && civInfo.isOneCityChallenger()))
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
                val unit = civInfo.gameInfo.ruleSet.units[unitName]
                if (chosenCity == null || unit == null || (unit.hasUnique(UniqueType.FoundCity) && civInfo.isOneCityChallenger()))
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
                val unit = civInfo.getEquivalentUnit(unique.params[0])
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
                        placedUnit.getTile().position,
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
                if (unique.type == MayanGainGreatPerson) {
                    if (civInfo.greatPeople.longCountGPPool.isEmpty())
                        // replenish maya GP pool when dry
                        civInfo.greatPeople.longCountGPPool = greatPeople.map { it.name }.toHashSet()
                }
                if (civInfo.isPlayerCivilization()) {
                    civInfo.greatPeople.freeGreatPeople++
                    if (unique.type == MayanGainGreatPerson) {
                        civInfo.greatPeople.mayaLimitedFreeGP++
                        civInfo.addNotification(notification!!, MayaLongCountAction(), MayaCalendar.notificationIcon)
                    } else {
                        if (notification != null)
                            civInfo.addNotification(notification) // Anyone an idea for a good icon?
                    }
                    return true
                } else {
                    if (unique.type == MayanGainGreatPerson)
                        greatPeople.removeAll { it.name !in civInfo.greatPeople.longCountGPPool }
                    if (greatPeople.isEmpty()) return false
                    var greatPerson = greatPeople.random()

                    val preferredVictoryType = civInfo.victoryType()
                    if (preferredVictoryType == VictoryType.Cultural) {
                        val culturalGP =
                            greatPeople.firstOrNull { it.uniques.contains("Great Person - [Culture]") }
                        if (culturalGP != null) greatPerson = culturalGP
                    }
                    if (preferredVictoryType == VictoryType.Scientific) {
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
                    "in this city" -> listOf(cityInfo!!)
                    "in other cities" -> civInfo.cities.filter { it != cityInfo }
                    else -> civInfo.cities.filter { it.matchesFilter(unique.params[1]) }
                }
                for (city in applicableCities) {
                    city.population.addPopulation(unique.params[0].toInt())
                }
                if (notification != null && applicableCities.isNotEmpty())
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
                        randomCity.location,
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
                val researchableTechsFromThatEra = civInfo.gameInfo.ruleSet.technologies.values
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
                    civInfo.addNotification(notificationText, NotificationIcon.Science)
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

            TimedAttackStrength -> {
                civInfo.temporaryUniques.add(Pair(unique, unique.params[2].toInt()))
                if (notification != null) {
                    civInfo.addNotification(notification, NotificationIcon.War)
                }
                return true
            }

            OneTimeRevealEntireMap -> {
                if (notification != null) {
                    civInfo.addNotification(notification, "UnitIcons/Scout")
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
                        && civInfo.gameInfo.ruleSet.unitPromotions.values.any {
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
                if (Stat.values().none { it.name == unique.params[1] }) return false
                val stat = Stat.valueOf(unique.params[1])

                if (stat !in listOf(Stat.Gold, Stat.Faith, Stat.Science, Stat.Culture)
                    || unique.params[0].toIntOrNull() == null
                ) return false

                civInfo.addStat(stat, unique.params[0].toInt())
                if (notification != null)
                    civInfo.addNotification(notification, stat.notificationIcon)
                return true
            }
            OneTimeGainStatRange -> {
                if (Stat.values().none { it.name == unique.params[2] }) return false
                val stat = Stat.valueOf(unique.params[2])

                if (stat !in listOf(Stat.Gold, Stat.Faith, Stat.Science, Stat.Culture)
                    || unique.params[0].toIntOrNull() == null
                    || unique.params[1].toIntOrNull() == null
                ) return false

                val foundStatAmount =
                    (tileBasedRandom.nextInt(unique.params[0].toInt(), unique.params[1].toInt()) *
                            civInfo.gameInfo.gameParameters.gameSpeed.modifier
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
                    civInfo.addNotification(notificationText, stat.notificationIcon)
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
                    civInfo.addNotification(notificationText, NotificationIcon.Faith)
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
                    civInfo.addNotification(notificationText, NotificationIcon.Faith)
                }

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
                civInfo.exploredTiles.addAll(nearbyRevealableTiles
                    .shuffled(tileBasedRandom)
                    .apply {
                        if (unique.params[0] != "All") this.take(unique.params[0].toInt())
                    }
                )

                if (notification != null) {
                    civInfo.addNotification(
                        notification,
                        LocationAction(nearbyRevealableTiles.toList())
                    ) // We really need a barbarian icon
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
                        "ImprovementIcons/Ancient ruins"
                    )
            }

            OneTimeTriggerVoting -> {
                for (civ in civInfo.gameInfo.civilizations)
                    if (!civ.isBarbarian() && !civ.isSpectator())
                        civ.addFlag(
                            CivFlags.TurnsTillNextDiplomaticVote.name,
                            civInfo.getTurnsBetweenDiplomaticVotings()
                        )
                if (notification != null)
                    civInfo.addNotification(notification, NotificationIcon.Diplomacy)
                return true
            }

            FreeStatBuildings, FreeSpecificBuildings ->
                civInfo.civConstructions.tryAddFreeBuildings()
        }
        return false
    }

    /** @return boolean whether an action was successfully preformed */
    fun triggerUnitwideUnique(
        unique: Unique,
        unit: MapUnit,
        notification: String? = null
    ): Boolean {
        @Suppress("NON_EXHAUSTIVE_WHEN")  // Yes we're not treating all types here
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
                val upgradeAction = UnitActions.getUpgradeAction(unit, true)
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
                    unit.civInfo.addNotification(notification, unit.name)
                return true
            }
        }
        return false
    }
}

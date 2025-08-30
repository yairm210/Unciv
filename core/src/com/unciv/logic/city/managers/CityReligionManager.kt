package com.unciv.logic.city.managers

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly

class CityReligionManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var city: City

    // This needs to be kept track of for the
    // "[Stats] when a city adopts this religion for the first time" unique
    private val religionsAtSomePointAdopted: HashSet<String> = hashSetOf()

    private val pressures: Counter<String> = Counter()
    // Cached because using `updateNumberOfFollowers` to get this value resulted in many calls
    @Transient
    private val followers: Counter<String> = Counter()

    @delegate:Transient
    private val pressureFromAdjacentCities: Int by lazy { city.civ.gameInfo.speed.religiousPressureAdjacentCity }

    var religionThisIsTheHolyCityOf: String? = null
    var isBlockedHolyCity = false

    init {
        clearAllPressures()
    }

    fun clone(): CityReligionManager {
        val toReturn = CityReligionManager()
        toReturn.city = city
        toReturn.religionsAtSomePointAdopted.addAll(religionsAtSomePointAdopted)
        toReturn.pressures.putAll(pressures)
        toReturn.followers.putAll(followers)
        toReturn.religionThisIsTheHolyCityOf = religionThisIsTheHolyCityOf
        toReturn.isBlockedHolyCity = isBlockedHolyCity
        return toReturn
    }

    fun setTransients(city: City) {
        this.city = city
        // We don't need to check for changes in the majority religion, and as this
        // loads in the religion, _of course_ the religion changes, but it shouldn't
        // have any effect
        updateNumberOfFollowers(false)
    }

    fun endTurn() {
        getAffectedBySurroundingCities()
    }

    @Readonly
    fun getUniques(uniqueType: UniqueType): Sequence<Unique> {
        val majorityReligion = getMajorityReligion() ?: return emptySequence()
        return majorityReligion.followerBeliefUniqueMap.getUniques(uniqueType)
    }


    @Readonly fun getPressures(): Counter<String> = pressures.clone()

    private fun clearAllPressures() {
        pressures.clear()
        // We add pressure for following no religion
        // Basically used as a failsafe so that there is always some religion,
        // and we don't suddenly divide by 0 somewhere
        // Should be removed when updating the followers so it never becomes the majority religion,
        // `null` is used for that instead.
        pressures.add(Constants.noReligionName, 100)
    }

    fun addPressure(religionName: String, amount: Int, shouldUpdateFollowers: Boolean = true) {
        if (!city.civ.gameInfo.isReligionEnabled()) return // No religion, no pressures
        pressures.add(religionName, amount)

        if (shouldUpdateFollowers) updateNumberOfFollowers()
    }

    fun removeAllPressuresExceptFor(religion: String) {
        val pressureFromThisReligion = pressures[religion]
        // Atheism is never removed
        val pressureFromAtheism = pressures[Constants.noReligionName]
        clearAllPressures()
        pressures.add(religion, pressureFromThisReligion)
        if (pressureFromAtheism != 0) pressures[Constants.noReligionName] = pressureFromAtheism
        updateNumberOfFollowers()
    }

    fun updatePressureOnPopulationChange(populationChangeAmount: Int) {
        val majorityReligion =
            if (getMajorityReligionName() != null) getMajorityReligionName()!!
            else Constants.noReligionName

        if (populationChangeAmount > 0) {
            addPressure(majorityReligion, 100 * populationChangeAmount)
        } else {
            updateNumberOfFollowers()
        }
    }

    private fun triggerReligionAdoption(newMajorityReligion: String) {
        val newMajorityReligionObject = city.civ.gameInfo.religions[newMajorityReligion]!!
        city.civ.addNotification("Your city [${city.name}] was converted to [${newMajorityReligionObject.getReligionDisplayName()}]!",
            city.location, NotificationCategory.Religion, NotificationIcon.Faith)

        if (newMajorityReligion in religionsAtSomePointAdopted) return

        val religionOwningCiv = newMajorityReligionObject.getFounder()
        if (religionOwningCiv.hasUnique(UniqueType.StatsWhenAdoptingReligion)) {
            val statsGranted =
                religionOwningCiv.getMatchingUniques(UniqueType.StatsWhenAdoptingReligion).map { it.stats.times(if (!it.isModifiedByGameSpeed()) 1f else city.civ.gameInfo.speed.modifier) }
                .reduce { acc, stats -> acc + stats }

            for ((key, value) in statsGranted)
                religionOwningCiv.addStat(key, value.toInt())
            if (religionOwningCiv.hasExplored(city.getCenterTile()))
                religionOwningCiv.addNotification(
                    "You gained [$statsGranted] as your religion was spread to [${city.name}]",
                    city.location,
                    NotificationCategory.Religion,
                    NotificationIcon.Faith
                )
            else
                religionOwningCiv.addNotification(
                    "You gained [$statsGranted] as your religion was spread to an unknown city",
                    NotificationCategory.Religion,
                    NotificationIcon.Faith
                )
        }
        religionsAtSomePointAdopted.add(newMajorityReligion)
    }

    private fun updateNumberOfFollowers(checkForReligionAdoption: Boolean = true) {
        val oldMajorityReligion =
            if (checkForReligionAdoption) getMajorityReligionName()
            else null

        val previousFollowers = followers.clone()
        followers.clear()

        if (city.population.population <= 0) return

        val remainders = HashMap<String, Float>()
        val pressurePerFollower = pressures.values.sum() / city.population.population

        // First give each religion an approximate share based on pressure
        for ((religion, pressure) in pressures) {
            val followersOfThisReligion = (pressure.toFloat() / pressurePerFollower).toInt()
            followers.add(religion, followersOfThisReligion)
            remainders[religion] = pressure.toFloat() - followersOfThisReligion * pressurePerFollower
        }

        var unallocatedPopulation = city.population.population - followers.values.sum()

        // Divide up the remaining population
        while (unallocatedPopulation > 0) {
            val largestRemainder = remainders.maxByOrNull { it.value }
            if (largestRemainder == null) {
                followers.add(Constants.noReligionName, unallocatedPopulation)
                break
            }
            followers.add(largestRemainder.key, 1)
            remainders[largestRemainder.key] = 0f
            unallocatedPopulation -= 1
        }

        followers.remove(Constants.noReligionName)


        if (checkForReligionAdoption) {
            val newMajorityReligion = getMajorityReligionName()
            if (oldMajorityReligion != newMajorityReligion && newMajorityReligion != null) {
                triggerReligionAdoption(newMajorityReligion)
            }
            if (oldMajorityReligion != newMajorityReligion)
                city.civ.cache.updateCivResources() // follower uniques can provide resources
            if (followers != previousFollowers)
                city.cityStats.update()
        }
    }

    @Readonly fun getNumberOfFollowers(): Counter<String> = followers.clone()
    @Readonly fun getFollowersOf(religion: String): Int = followers[religion]

    @Readonly
    fun getFollowersOfMajorityReligion(): Int {
        val majorityReligion = getMajorityReligionName() ?: return 0
        return followers[majorityReligion]
    }

    @Readonly
    fun getFollowersOfOtherReligionsThan(religion: String): Int {
        return followers.filterNot { it.key == religion }.values.sum()
    }

    /** Removes all pantheons except for the one founded by the current owner of the city
     *  Should be called whenever a city changes hands, e.g. conquering and trading
     */
    fun removeUnknownPantheons() {
        for (pressure in pressures.keys.toList()) {  // Copy the keys because we might modify
            if (pressure == Constants.noReligionName) continue
            val correspondingReligion = city.civ.gameInfo.religions[pressure]!!
            if (correspondingReligion.isPantheon()
                && correspondingReligion.foundingCivName != city.civ.civName
            ) {
                pressures.remove(pressure)
            }
        }
        updateNumberOfFollowers()
    }

    @Readonly
    fun getMajorityReligionName(): String? {
        if (followers.isEmpty()) return null
        val religionWithMaxPressure = followers.maxByOrNull { it.value }!!.key
        return when {
            religionWithMaxPressure == Constants.noReligionName -> null
            followers[religionWithMaxPressure] >= city.population.population / 2 -> religionWithMaxPressure
            else -> null
        }
    }

    @Readonly
    fun getMajorityReligion(): Religion? {
        val majorityReligionName = getMajorityReligionName() ?: return null
        return city.civ.gameInfo.religions[majorityReligionName]
    }

    private fun getAffectedBySurroundingCities() {
        if (!city.civ.gameInfo.isReligionEnabled()) return // No religion, no spreading
        // We don't update the amount of followers yet, as only the end result should matter
        // If multiple religions would become the majority religion due to pressure,
        // this will make it so we only receive a notification for the last one.
        // Also, doing it like this increases performance :D
        if (city.isHolyCity()) {
            addPressure(religionThisIsTheHolyCityOf!!,5 * pressureFromAdjacentCities, false)
        }

        for (otherCity in city.civ.gameInfo.getCities()) {
            if (otherCity == city) continue
            val majorityReligionOfCity = otherCity.religion.getMajorityReligionName() ?: continue
            if (!this.city.civ.gameInfo.religions[majorityReligionOfCity]!!.isMajorReligion()) continue
            if (otherCity.getCenterTile().aerialDistanceTo(city.getCenterTile())
                    > otherCity.religion.getSpreadRange()) continue
            addPressure(majorityReligionOfCity, otherCity.religion.pressureAmountToAdjacentCities(city), false)
        }

        updateNumberOfFollowers()
    }

    @Readonly
    private fun getSpreadRange(): Int {
        var spreadRange = 10

        for (unique in city.getMatchingUniques(UniqueType.ReligionSpreadDistance)) {
            spreadRange += unique.params[0].toInt()
        }

        val majorityReligion = getMajorityReligion()
        if (majorityReligion != null) {
            for (unique in majorityReligion.getFounder().getMatchingUniques(UniqueType.ReligionSpreadDistance))
                spreadRange += unique.params[0].toInt()
        }

        return spreadRange
    }

    /** Doesn't update the pressures, only returns what they are if the update were to happen right now */
    @Readonly
    fun getPressuresFromSurroundingCities(): Counter<String> {
        val addedPressure = Counter<String>()
        if (city.isHolyCity()) {
            addedPressure[religionThisIsTheHolyCityOf!!] = 5 * pressureFromAdjacentCities
        }
        val allCitiesWithin10Tiles =
            city.civ.gameInfo.getCities()
                .filter {
                    it != city
                    && it.getCenterTile().aerialDistanceTo(city.getCenterTile()) <= it.religion.getSpreadRange()
                }
        for (city in allCitiesWithin10Tiles) {
            val majorityReligionOfCity = city.religion.getMajorityReligion() ?: continue
            if (!majorityReligionOfCity.isMajorReligion()) continue
            addedPressure.add(majorityReligionOfCity.name, city.religion.pressureAmountToAdjacentCities(
                this.city
            ))
        }
        return addedPressure
    }

    @Readonly
    fun isProtectedByInquisitor(fromReligion: String? = null): Boolean {
        for (tile in city.getCenterTile().getTilesInDistance(1)) {
            for (unit in listOf(tile.civilianUnit, tile.militaryUnit)) {
                if (unit?.religion != null
                    && (fromReligion == null || unit.religion != fromReligion)
                    && unit.hasUnique(UniqueType.PreventSpreadingReligion)
                ) return true
            }
        }
        return false
    }

    @Readonly
    private fun pressureAmountToAdjacentCities(pressuredCity: City): Int {
        var pressure = pressureFromAdjacentCities.toFloat()

        // Follower beliefs of this religion
        for (unique in city.getMatchingUniques(UniqueType.NaturalReligionSpreadStrength)) {
            if (pressuredCity.matchesFilter(unique.params[1]))
                pressure *= unique.params[0].toPercent()
        }

        // Founder beliefs of this religion
        val majorityReligion = getMajorityReligion()
        if (majorityReligion != null) {
            for (unique in majorityReligion.getFounder().getMatchingUniques(UniqueType.NaturalReligionSpreadStrength))
                if (pressuredCity.matchesFilter(unique.params[1]))
                    pressure *= unique.params[0].toPercent()
        }

        return pressure.toInt()
    }

    /** Calculates how much pressure this religion is lacking compared to the majority religion
     * That is, if we gain more than this, we'll be the majority */
    @Readonly
    fun getPressureDeficit(otherReligion: String?): Int {
        val pressures = getPressures()
        return (pressures[getMajorityReligionName()] ?: 0) - (pressures[otherReligion] ?: 0)
    }
}

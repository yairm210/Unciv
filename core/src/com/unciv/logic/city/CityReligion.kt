package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent

class CityInfoReligionManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var cityInfo: CityInfo

    // This needs to be kept track of for the
    // "[Stats] when a city adopts this religion for the first time" unique
    val religionsAtSomePointAdopted: HashSet<String> = hashSetOf()

    private val pressures: Counter<String> = Counter()
    // Cached because using `updateNumberOfFollowers` to get this value resulted in many calls
    @Transient
    private val followers: Counter<String> = Counter()

    @delegate:Transient
    private val pressureFromAdjacentCities: Int by lazy { cityInfo.civInfo.gameInfo.speed.religiousPressureAdjacentCity }

    var religionThisIsTheHolyCityOf: String? = null
    var isBlockedHolyCity = false

    init {
        clearAllPressures()
    }

    fun clone(): CityInfoReligionManager {
        val toReturn = CityInfoReligionManager()
        toReturn.cityInfo = cityInfo
        toReturn.religionsAtSomePointAdopted.addAll(religionsAtSomePointAdopted)
        toReturn.pressures.putAll(pressures)
        toReturn.followers.putAll(followers)
        toReturn.religionThisIsTheHolyCityOf = religionThisIsTheHolyCityOf
        toReturn.isBlockedHolyCity = isBlockedHolyCity
        return toReturn
    }

    fun setTransients(cityInfo: CityInfo) {
        this.cityInfo = cityInfo
        // We don't need to check for changes in the majority religion, and as this
        // loads in the religion, _of course_ the religion changes, but it shouldn't
        // have any effect
        updateNumberOfFollowers(false)
    }

    fun endTurn() {
        getAffectedBySurroundingCities()
    }

    fun getUniques(): Sequence<Unique> {
        val majorityReligion = getMajorityReligion() ?: return sequenceOf()
        return majorityReligion.getFollowerUniques()
    }


    fun getPressures(): Counter<String> = pressures.clone()

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
        if (!cityInfo.civInfo.gameInfo.isReligionEnabled()) return // No religion, no pressures
        pressures.add(religionName, amount)

        if (shouldUpdateFollowers) {
            updateNumberOfFollowers(shouldUpdateFollowers)
        }
    }

    fun removeAllPressuresExceptFor(religion: String) {
        val pressureFromThisReligion = pressures[religion]!!
        // Atheism is never removed
        val pressureFromAtheism = pressures[Constants.noReligionName]
        clearAllPressures()
        pressures.add(religion, pressureFromThisReligion)
        if (pressureFromAtheism != null) pressures[Constants.noReligionName] = pressureFromAtheism
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
        val newMajorityReligionObject = cityInfo.civInfo.gameInfo.religions[newMajorityReligion]!!
        cityInfo.civInfo.addNotification("Your city [${cityInfo.name}] was converted to [${newMajorityReligionObject.getReligionDisplayName()}]!", cityInfo.location, NotificationIcon.Faith)

        if (newMajorityReligion in religionsAtSomePointAdopted) return

        val religionOwningCiv = newMajorityReligionObject.getFounder()
        if (religionOwningCiv.hasUnique(UniqueType.StatsWhenAdoptingReligionSpeed) || religionOwningCiv.hasUnique(UniqueType.StatsWhenAdoptingReligion)) {
            val statsGranted =
                (
                    religionOwningCiv.getMatchingUniques(UniqueType.StatsWhenAdoptingReligionSpeed)
                    + religionOwningCiv.getMatchingUniques(UniqueType.StatsWhenAdoptingReligion)
                ).map { it.stats }
                .reduce { acc, stats -> acc + stats }

            for ((key, value) in statsGranted)
                religionOwningCiv.addStat(key, value.toInt())
            if (cityInfo.location in religionOwningCiv.exploredTiles)
                religionOwningCiv.addNotification(
                    "You gained [$statsGranted] as your religion was spread to [${cityInfo.name}]",
                    cityInfo.location,
                    NotificationIcon.Faith
                )
            else
                religionOwningCiv.addNotification(
                    "You gained [$statsGranted] as your religion was spread to an unknown city",
                    NotificationIcon.Faith
                )
        }
        religionsAtSomePointAdopted.add(newMajorityReligion)
    }

    private fun updateNumberOfFollowers(checkForReligionAdoption: Boolean = true) {
        val oldMajorityReligion =
            if (checkForReligionAdoption) getMajorityReligionName()
            else null

        followers.clear()
        if (cityInfo.population.population <= 0) return

        val remainders = HashMap<String, Float>()
        val pressurePerFollower = pressures.values.sum() / cityInfo.population.population

        // First give each religion an approximate share based on pressure
        for ((religion, pressure) in pressures) {
            val followersOfThisReligion = (pressure.toFloat() / pressurePerFollower).toInt()
            followers.add(religion, followersOfThisReligion)
            remainders[religion] = pressure.toFloat() - followersOfThisReligion * pressurePerFollower
        }

        var unallocatedPopulation = cityInfo.population.population - followers.values.sum()

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
        }
    }

    fun getNumberOfFollowers(): Counter<String> {
        return followers.clone()
    }

    fun getFollowersOf(religion: String): Int? {
        return followers[religion]
    }

    fun getFollowersOfMajorityReligion(): Int {
        val majorityReligion = getMajorityReligionName() ?: return 0
        return followers[majorityReligion]!!
    }

    fun getFollowersOfOtherReligionsThan(religion: String): Int {
        return followers.filterNot { it.key == religion }.values.sum()
    }

    /** Removes all pantheons except for the one founded by the current owner of the city
     *  Should be called whenever a city changes hands, e.g. conquering and trading
     */
    fun removeUnknownPantheons() {
        for (pressure in pressures.keys.toList()) {  // Copy the keys because we might modify
            if (pressure == Constants.noReligionName) continue
            val correspondingReligion = cityInfo.civInfo.gameInfo.religions[pressure]!!
            if (correspondingReligion.isPantheon()
                && correspondingReligion.foundingCivName != cityInfo.civInfo.civName
            ) {
                pressures.remove(pressure)
            }
        }
        updateNumberOfFollowers()
    }

    fun getMajorityReligionName(): String? {
        if (followers.isEmpty()) return null
        val religionWithMaxPressure = pressures.maxByOrNull { it.value }!!.key
        return when {
            religionWithMaxPressure == Constants.noReligionName -> null
            followers[religionWithMaxPressure]!! >= cityInfo.population.population / 2 -> religionWithMaxPressure
            else -> null
        }
    }

    fun getMajorityReligion(): Religion? {
        return cityInfo.civInfo.gameInfo.religions[getMajorityReligionName()]
    }

    private fun getAffectedBySurroundingCities() {
        if (!cityInfo.civInfo.gameInfo.isReligionEnabled()) return // No religion, no spreading
        // We don't update the amount of followers yet, as only the end result should matter
        // If multiple religions would become the majority religion due to pressure,
        // this will make it so we only receive a notification for the last one.
        // Also, doing it like this increases performance :D
        if (cityInfo.isHolyCity()) {
            addPressure(religionThisIsTheHolyCityOf!!,5 * pressureFromAdjacentCities, false)
        }

        for (city in cityInfo.civInfo.gameInfo.getCities()) {
            if (city == cityInfo) continue
            val majorityReligionOfCity = city.religion.getMajorityReligionName() ?: continue
            if (!cityInfo.civInfo.gameInfo.religions[majorityReligionOfCity]!!.isMajorReligion()) continue
            if (city.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile())
                    > city.religion.getSpreadRange()) continue
            addPressure(majorityReligionOfCity, city.religion.pressureAmountToAdjacentCities(cityInfo), false)
        }

        updateNumberOfFollowers()
    }

    private fun getSpreadRange(): Int {
        var spreadRange = 10

        for (unique in cityInfo.getLocalMatchingUniques(UniqueType.ReligionSpreadDistance)) {
            spreadRange += unique.params[0].toInt()
        }

        if (getMajorityReligion() != null) {
            for (unique in getMajorityReligion()!!.getFounder().getMatchingUniques(UniqueType.ReligionSpreadDistance))
                spreadRange += unique.params[0].toInt()
        }

        return spreadRange
    }

    /** Doesn't update the pressures, only returns what they are if the update were to happen right now */
    fun getPressuresFromSurroundingCities(): Counter<String> {
        val addedPressure = Counter<String>()
        if (cityInfo.isHolyCity()) {
            addedPressure[religionThisIsTheHolyCityOf!!] = 5 * pressureFromAdjacentCities
        }
        val allCitiesWithin10Tiles =
            cityInfo.civInfo.gameInfo.getCities()
                .filter {
                    it != cityInfo
                    && it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) <= it.religion.getSpreadRange()
                }
        for (city in allCitiesWithin10Tiles) {
            val majorityReligionOfCity = city.religion.getMajorityReligion() ?: continue
            if (!majorityReligionOfCity.isMajorReligion()) continue
            addedPressure.add(majorityReligionOfCity.name, city.religion.pressureAmountToAdjacentCities(cityInfo))
        }
        return addedPressure
    }

    fun isProtectedByInquisitor(fromReligion: String? = null): Boolean {
        for (tile in cityInfo.getCenterTile().getTilesInDistance(1)) {
            for (unit in listOf(tile.civilianUnit, tile.militaryUnit)) {
                if (unit?.religion != null
                    && (fromReligion == null || unit.religion != fromReligion)
                    && unit.hasUnique(UniqueType.PreventSpreadingReligion)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun pressureAmountToAdjacentCities(pressuredCity: CityInfo): Int {
        var pressure = pressureFromAdjacentCities.toFloat()

        // Follower beliefs of this religion
        for (unique in cityInfo.getLocalMatchingUniques(UniqueType.NaturalReligionSpreadStrength)) {
            if (pressuredCity.matchesFilter(unique.params[1]))
                pressure *= unique.params[0].toPercent()
        }

        // Founder beliefs of this religion
        if (getMajorityReligion() != null) {
            for (unique in getMajorityReligion()!!.getFounder().getMatchingUniques(UniqueType.NaturalReligionSpreadStrength))
                if (pressuredCity.matchesFilter(unique.params[1]))
                    pressure *= unique.params[0].toPercent()
        }

        return pressure.toInt()
    }

    fun getPressureDeficit(otherReligion: String?): Int {
        return (getPressures()[getMajorityReligionName()] ?: 0) - (getPressures()[otherReligion] ?: 0)
    }
}

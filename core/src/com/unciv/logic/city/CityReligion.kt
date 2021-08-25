package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.Unique

class CityInfoReligionManager {
    @Transient
    lateinit var cityInfo: CityInfo
    
    // This needs to be kept track of for the 
    // "[Stats] when a city adopts this religion for the first time" unique
    val religionsAtSomePointAdopted: HashSet<String> = hashSetOf()

    private val pressures: Counter<String> = Counter()
    // `getNumberOfFollowers()` was called a surprisingly large amount of time, so caching it feels useful
    @Transient
    private val followers: Counter<String> = Counter()
    
    @delegate:Transient
    private val pressureFromAdjacentCities: Int by lazy {
        when (cityInfo.civInfo.gameInfo.gameParameters.gameSpeed) {
            GameSpeed.Quick -> 9
            GameSpeed.Standard -> 6
            GameSpeed.Epic -> 4
            GameSpeed.Marathon -> 2
        }
    }
    
    var religionThisIsTheHolyCityOf: String? = null 
    
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
        val majorityReligion = getMajorityReligion()
        if (majorityReligion == null) return sequenceOf()
        return majorityReligion.getFollowerUniques()
    }
    
    fun getMatchingUniques(unique: String): Sequence<Unique> {
        return getUniques().filter { it.placeholderText == unique }
    }
    
    fun clearAllPressures() {
        pressures.clear()
        // We add pressure for following no religion
        // Basically used as a failsafe so that there is always some religion, 
        // and we don't suddenly divide by 0 somewhere
        // Should be removed when updating the followers so it never becomes the majority religion, 
        // `null` is used for that instead.
        pressures.add(Constants.noReligionName, 100)
    }
    
    fun addPressure(religionName: String, amount: Int, shouldUpdateFollowers: Boolean = true) {
        pressures.add(religionName, amount)
        
        if (shouldUpdateFollowers) {
            updateNumberOfFollowers(shouldUpdateFollowers)
        }
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
        cityInfo.civInfo.addNotification("Your city [${cityInfo.name}] was converted to [$newMajorityReligion]!", cityInfo.location, NotificationIcon.Faith)
        if (newMajorityReligion in religionsAtSomePointAdopted) return
        
        val religionOwningCiv = cityInfo.civInfo.gameInfo.getCivilization(cityInfo.civInfo.gameInfo.religions[newMajorityReligion]!!.foundingCivName)
        for (unique in cityInfo.civInfo.gameInfo.religions[newMajorityReligion]!!.getFounderUniques()) {
            val statsGranted = when (unique.placeholderText) {
                "[] when a city adopts this religion for the first time (modified by game speed)" ->
                    unique.stats.times(cityInfo.civInfo.gameInfo.gameParameters.gameSpeed.modifier)
                "[] when a city adopts this religion for the first time" -> unique.stats
                else -> continue
            }
            for (stat in statsGranted.toHashMap())
                religionOwningCiv.addStat(stat.key, stat.value.toInt())
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
        val religionWithMaxFollowers = followers.maxByOrNull { it.value }!!
        return if (religionWithMaxFollowers.value >= cityInfo.population.population / 2) religionWithMaxFollowers.key
        else null
    }
    
    fun getMajorityReligion(): Religion? {
        return cityInfo.civInfo.gameInfo.religions[getMajorityReligionName()]
    }

    private fun getAffectedBySurroundingCities() {
        // We don't update the amount of followers yet, as only the end result should matter
        // If multiple religions would become the majority religion due to pressure, 
        // this will make it so we only receive a notification for the last one.
        // Also, doing it like this increases performance :D
        if (cityInfo.isHolyCity()) {
            addPressure(religionThisIsTheHolyCityOf!!,5 * pressureFromAdjacentCities, false)
        }
        
        val allCitiesWithinSpreadRange =
            cityInfo.civInfo.gameInfo.getCities()
                .filter {
                    it != cityInfo 
                    && it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) <= it.religion.getSpreadRange() 
                }
        for (city in allCitiesWithinSpreadRange) {
            val majorityReligionOfCity = city.religion.getMajorityReligionName() ?: continue
            if (!cityInfo.civInfo.gameInfo.religions[majorityReligionOfCity]!!.isMajorReligion()) continue
            addPressure(majorityReligionOfCity, city.religion.pressureAmountToAdjacentCities(cityInfo), false) 
        }
        
        updateNumberOfFollowers()
    }

    private fun getSpreadRange(): Int {
        var spreadRange = 10
        for (unique in cityInfo.getMatchingUniques("Religion naturally spreads to cities [] tiles away"))
            spreadRange += unique.params[0].toInt()
        
        if (getMajorityReligion() != null)
            for (unique in getMajorityReligion()!!.getFounderUniques()
                .filter { it.placeholderText == "Religion naturally spreads to cities [] tiles away"}
            ) spreadRange += unique.params[0].toInt()
        
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
    
    private fun pressureAmountToAdjacentCities(pressuredCity: CityInfo): Int {
        var pressure = pressureFromAdjacentCities.toFloat()
        
        for (unique in cityInfo.getMatchingUniques("[]% Natural religion spread []")) {
            if (pressuredCity.matchesFilter(unique.params[1]))
                pressure *= 1f + unique.params[0].toFloat() / 100f
        }
        
        for (unique in cityInfo.getMatchingUniques("[]% Natural religion spread [] with []"))
            if (pressuredCity.matchesFilter(unique.params[1]) 
                && (
                    cityInfo.civInfo.tech.isResearched(unique.params[2]) 
                    || cityInfo.civInfo.policies.isAdopted(unique.params[2])
                )
            ) pressure *= 1f + unique.params[0].toFloat() / 100f
        
        return pressure.toInt()
    }
}
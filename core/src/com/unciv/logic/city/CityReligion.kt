package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Counter
import com.unciv.models.ruleset.Unique

class CityInfoReligionManager {
    @Transient
    lateinit var cityInfo: CityInfo
    
    // This needs to be kept track of for the 
    // "[Stats] when a city adopts this religion for the first time" unique
    val religionsAtSomePointAdopted: HashSet<String> = hashSetOf()

    private val pressures: Counter<String> = Counter()
    // `getNumberOfFollowers()` was called a surprisingly large amount of time, so caching it feels useful
    private val followers: Counter<String> = Counter()
    
    var religionThisIsTheHolyCityOf: String? = null 
    
    init {
        clearAllPressures()
    }
    
    fun clone(): CityInfoReligionManager {
        val toReturn = CityInfoReligionManager()
        toReturn.religionsAtSomePointAdopted.addAll(religionsAtSomePointAdopted)
        toReturn.pressures.putAll(pressures)
        toReturn.followers.putAll(followers)
        toReturn.religionThisIsTheHolyCityOf = religionThisIsTheHolyCityOf
        return toReturn
    }
    
    fun setTransients(cityInfo: CityInfo) {
        this.cityInfo = cityInfo
    }
    
    fun endTurn() {
        getAffectedBySurroundingCities()
    }
    
    fun getUniques(): Sequence<Unique> {
        val majorityReligion = getMajorityReligion()
        if (majorityReligion == null) return sequenceOf()
        return cityInfo.civInfo.gameInfo.religions[majorityReligion]!!.getFollowerUniques()
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
            updateNumberOfFollowers()
        }
    }
    
    fun updatePressureOnPopulationChange(populationChangeAmount: Int) {
        val majorityReligion =
            if (getMajorityReligion() != null) getMajorityReligion()!!
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
    
    private fun updateNumberOfFollowers() {
        val oldMajorityReligion = getMajorityReligion()
        
        followers.clear()

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

        val newMajorityReligion = getMajorityReligion()
        if (oldMajorityReligion != newMajorityReligion && newMajorityReligion != null) {
            triggerReligionAdoption(newMajorityReligion)
        }
    }
    
    fun getNumberOfFollowers(): Counter<String> {
        
        // println(followers) // ToDo: remove this when a UI for viewing followers is added
        
        return followers
    }

    fun getMajorityReligion(): String? {
        val followersPerReligion = getNumberOfFollowers()
        if (followersPerReligion.isEmpty()) return null
        val religionWithMaxFollowers = followersPerReligion.maxByOrNull { it.value }!!
        return if (religionWithMaxFollowers.value >= cityInfo.population.population / 2) religionWithMaxFollowers.key
        else null
    }

    private fun getAffectedBySurroundingCities() {
        // We don't update the amount of followers yet, as only the end result should matter
        // If multiple religions would become the majority religion due to pressure, 
        // this will make it so we only receive a notification for the last one.
        // Also, doing it like this increases performance :D
        if (cityInfo.isHolyCity()) {
            addPressure(religionThisIsTheHolyCityOf!!,30,false)
        }
        
        val allCitiesWithin10Tiles =
            cityInfo.civInfo.gameInfo.getCities()
                .filter {
                    it != cityInfo 
                    && it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) <= 10
                }
        for (city in allCitiesWithin10Tiles) {
            val majorityReligionOfCity = city.religion.getMajorityReligion() ?: continue
            if (!cityInfo.civInfo.gameInfo.religions[majorityReligionOfCity]!!.isMajorReligion()) continue
            addPressure(majorityReligionOfCity,6,false) 
        }
        
        updateNumberOfFollowers()
    }
}
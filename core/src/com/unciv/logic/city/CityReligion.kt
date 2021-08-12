package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Counter
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.Stats
import kotlin.math.roundToInt

class CityInfoReligionManager {
    @Transient
    lateinit var cityInfo: CityInfo
    
    // This needs to be kept track of for the 
    // "[Stats] when a city adopts this religion for the first time" unique
    val religionsAtSomePointAdopted: HashSet<String> = hashSetOf()

    private val pressures: Counter<String> = Counter()
    
    fun clone(): CityInfoReligionManager {
        val toReturn = CityInfoReligionManager()
        toReturn.religionsAtSomePointAdopted.addAll(religionsAtSomePointAdopted)
        toReturn.pressures.putAll(pressures)
        return toReturn
    }
    
    fun setTransients(cityInfo: CityInfo) {
        this.cityInfo = cityInfo
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
    }
    
    fun addPressure(religionName: String, amount: Int) {
        val oldMajorityReligion = getMajorityReligion()
        pressures.add(religionName, amount)
        val newMajorityReligion = getMajorityReligion()
        if (oldMajorityReligion != newMajorityReligion && newMajorityReligion != null) {
            triggerReligionAdoption(newMajorityReligion)
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
    
    fun getNumberOfFollowers(): Counter<String> {
        val totalInfluence = pressures.values.sum()
        val population = cityInfo.population.population
        if (totalInfluence > 100 * population) {
            val toReturn = Counter<String>()
            for ((key, value) in pressures)
                if (value > 100)
                    toReturn.add(key, value / 100)
            return toReturn
        }

        val toReturn = Counter<String>()

        for ((key, value) in pressures) {
            val percentage = value.toFloat() / totalInfluence
            val relativePopulation = (percentage * population).roundToInt()
            toReturn.add(key, relativePopulation)
        }
        return toReturn
    }

    fun getMajorityReligion(): String? {
        val followersPerReligion = getNumberOfFollowers()
        if (followersPerReligion.isEmpty()) return null
        val religionWithMaxFollowers = followersPerReligion.maxByOrNull { it.value }!!
        return if (religionWithMaxFollowers.value >= cityInfo.population.population) religionWithMaxFollowers.key
        else null
    }

    fun getAffectedBySurroundingCities() {
        val allCitiesWithin10Tiles =
            cityInfo.civInfo.gameInfo.civilizations.asSequence().flatMap { it.cities }
                .filter {
                    it != cityInfo && it.getCenterTile()
                        .aerialDistanceTo(cityInfo.getCenterTile()) <= 10
                }
        for (city in allCitiesWithin10Tiles) {
            val majorityReligionOfCity = city.religion.getMajorityReligion()
            if (majorityReligionOfCity == null) continue
            else addPressure(
                majorityReligionOfCity, 
                if (city.isHolyCity()) 30
                else 6
            ) 
        }
    }
}
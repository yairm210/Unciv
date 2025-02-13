package com.unciv.logic.city.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Spy

enum class SpyFleeReason {
    CityDestroyed,
    CityCaptured,
    CityBought,
    CityTakenOverByMarriage,
    Other
}

class CityEspionageManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var city: City

    fun clone(): CityEspionageManager {
        return CityEspionageManager()
    }

    fun setTransients(city: City) {
        this.city = city
    }

    fun hasSpyOf(civInfo: Civilization): Boolean {
        return civInfo.espionageManager.spyList.any { it.getCityOrNull() == city }
    }

    fun getAllStationedSpies(): List<Spy> {
        return city.civ.gameInfo.civilizations.flatMap { it.espionageManager.getSpiesInCity(city) }
    }

    fun removeAllPresentSpies(reason: SpyFleeReason) {
        for (spy in getAllStationedSpies()) {
            val notificationString = when (reason) {
                SpyFleeReason.CityDestroyed -> "After the city of [${city.name}] was destroyed, your spy [${spy.name}] has fled back to our hideout."
                SpyFleeReason.CityCaptured -> "After the city of [${city.name}] was conquered, your spy [${spy.name}] has fled back to our hideout."
                SpyFleeReason.CityBought, SpyFleeReason.CityTakenOverByMarriage ->  "After the city of [${city.name}] was taken over, your spy [${spy.name}] has fled back to our hideout."
                else -> "Due to the chaos ensuing in [${city.name}], your spy [${spy.name}] has fled back to our hideout."
            }
            spy.addNotification(notificationString)
            spy.moveTo(null)
        }
    }
}

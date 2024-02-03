package com.unciv.logic.city.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.Spy

enum class SpyFleeReason {
    CityDestroyed,
    CityCaptured,
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
        return civInfo.espionageManager.spyList.any { it.location == city.id }
    }

    private fun getAllStationedSpies(): List<Spy> {
        return city.civ.gameInfo.civilizations.flatMap { it.espionageManager.getSpiesInCity(city) }
    }

    fun removeAllPresentSpies(reason: SpyFleeReason) {
        for (spy in getAllStationedSpies()) {
            val owningCiv = spy.civInfo
            val notificationString = when (reason) {
                SpyFleeReason.CityDestroyed -> "After the city of [${city.name}] was destroyed, your spy [${spy.name}] has fled back to our hideout."
                SpyFleeReason.CityCaptured -> "After the city of [${city.name}] was conquered, your spy [${spy.name}] has fled back to our hideout."
                else -> "Due to the chaos ensuing in [${city.name}], your spy [${spy.name}] has fled back to our hideout."
            }
            owningCiv.addNotification(notificationString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)
            spy.location = null
        }
    }
}

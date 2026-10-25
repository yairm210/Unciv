package com.unciv.logic.city.managers

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Spy
import yairm210.purity.annotations.Readonly

enum class SpyFleeReason {
    CityDestroyed,
    CityCaptured,
    CityBought,
    CityTakenOverByMarriage,
    Other
}

class CityEspionageManager {
    // Note: Instance lives in City, but since we don't carry data, it's transient, and no `: IsPartOfGameInfoSerialization` here.

    @Transient
    lateinit var city: City

    fun setTransients(city: City) {
        this.city = city
    }

    @Readonly fun hasSpyOf(civInfo: Civilization): Boolean = civInfo.espionageManager.spyList.any { it.getCityOrNull() == city }

    @Readonly fun getAllStationedSpies(): List<Spy> =
        city.civ.gameInfo.civilizations.flatMap { it.espionageManager.getSpiesInCity(city) }

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

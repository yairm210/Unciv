package com.unciv.logic.city.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization

class CityEspionageManager : IsPartOfGameInfoSerialization{
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

}

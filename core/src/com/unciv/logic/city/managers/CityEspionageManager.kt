package com.unciv.logic.city.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo

class CityEspionageManager : IsPartOfGameInfoSerialization{
    @Transient
    lateinit var cityInfo: CityInfo

    fun clone(): CityEspionageManager {
        return CityEspionageManager()
    }

    fun setTransients(cityInfo: CityInfo) {
        this.cityInfo = cityInfo
    }

    fun hasSpyOf(civInfo: CivilizationInfo): Boolean {
        return civInfo.espionageManager.spyList.any { it.location == cityInfo.id }
    }

}

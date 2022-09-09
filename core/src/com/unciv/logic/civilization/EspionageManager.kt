package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.CityInfo

class Spy() : IsPartOfGameInfoSerialization {
    // `location == null` means that the spy is in its hideout
    var location: String? = null
    lateinit var name: String

    constructor(name: String) : this() {
        this.name = name
    }

    fun clone(): Spy {
        val toReturn = Spy(name)
        toReturn.location = location
        return toReturn
    }

    fun getLocation(gameInfo: GameInfo): CityInfo? {
        return gameInfo.getCities().firstOrNull { it.id == location }
    }

    fun getLocationName(gameInfo: GameInfo): String {
        return getLocation(gameInfo)?.name ?: Constants.spyHideout
    }
}

class EspionageManager : IsPartOfGameInfoSerialization {

    var spyCount = 0
    var spyList = mutableListOf<Spy>()
    var erasSpyEarnedFor = mutableListOf<String>()

    @Transient
    lateinit var civInfo: CivilizationInfo

    fun clone(): EspionageManager {
        val toReturn = EspionageManager()
        toReturn.spyCount = spyCount
        toReturn.spyList.addAll(spyList.map { it.clone() })
        toReturn.erasSpyEarnedFor.addAll(erasSpyEarnedFor)
        return toReturn
    }

    fun setTransients(civInfo: CivilizationInfo) {
        this.civInfo = civInfo
    }

    private fun getSpyName(): String {
        val usedSpyNames = spyList.map { it.name }.toHashSet()
        val validSpyNames = civInfo.nation.spyNames.filter { it !in usedSpyNames }
        if (validSpyNames.isEmpty()) { return "Spy ${spyList.size+1}" } // +1 as non-programmers count from 1
        return validSpyNames.random()
    }

    fun addSpy(): String {
        val spyName = getSpyName()
        spyList.add(Spy(spyName))
        ++spyCount
        return spyName
    }
}

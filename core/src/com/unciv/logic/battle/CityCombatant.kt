package com.unciv.logic.battle

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.models.gamebasics.GameBasics

class CityCombatant(val city: CityInfo) : ICombatant {
    override fun getHealth(): Int = city.health
    override fun getCivilization(): CivilizationInfo = city.civInfo
    override fun getTile(): TileInfo = city.getCenterTile()
    override fun getName(): String = city.name
    override fun isDefeated(): Boolean = city.health==1

    override fun takeDamage(damage: Int) {
        city.health -= damage
        if(city.health<1) city.health=1  // min health is 1
    }

    override fun getUnitType():UnitType=UnitType.City
    override fun getAttackingStrength(defender: ICombatant): Int = getCityStrength()
    override fun getDefendingStrength(attacker: ICombatant): Int = getCityStrength()

    private fun
            getCityStrength(): Int {
        val baseStrength = 10
        // as tech progresses so does city strength
        val techsPercentKnown: Float = city.civInfo.tech.techsResearched.count().toFloat() /
                GameBasics.Technologies.count()
        val strengthFromTechs = Math.pow(techsPercentKnown*5.0,2.0) *5

        // The way all of this adds up...
        // 25% of the way through the game provides an extra 3.12
        // 50% of the way through the game provides an extra 12.50
        // 75% of the way through the game provides an extra 28.12
        // 100% of the way through the game provides an extra 50.00

        // 10% bonus foreach pop
        val strengthWithPop = (baseStrength + strengthFromTechs) * (1 + 0.1*city.population.population)

        return strengthWithPop.toInt() * 100 // *100 because a city is always at 100% strength
    }

    override fun toString(): String {return city.name} // for debug

}
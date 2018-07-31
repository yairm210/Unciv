package com.unciv.logic.battle

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.UnitType

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

    override fun getUnitType(): UnitType = UnitType.City
    override fun getAttackingStrength(defender: ICombatant): Int = getCityStrength()
    override fun getDefendingStrength(attacker: ICombatant): Int{
        if(isDefeated()) return 1
        return getCityStrength()
    }

    fun getCityStrength(): Int { // Civ fanatics forum, from a modder who went through the original code
        var strength = 8f
        if(city.isCapital()) strength+=2.5f
        strength += (city.population.population/5) * 2 // Each 5 pop gives 2 defence
        val cityTile = city.getCenterTile()
        if(cityTile.baseTerrain=="Hill") strength+=5
        // as tech progresses so does city strength
        val techsPercentKnown: Float = city.civInfo.tech.techsResearched.count().toFloat() /
                GameBasics.Technologies.count()
        strength += Math.pow(techsPercentKnown*5.5, 2.8).toFloat()

        // The way all of this adds up...
        // All ancient techs - 0.5 extra, Classical - 2.7, Medieval - 8, Renaissance - 17.5,
        // Industrial - 32.4, Modern - 51, Atomic - 72.5, All - 118.3
        // 100% of the way through the game provides an extra 50.00

        // Garrisoned unit gives up to 20% of strength to city, health-dependant
        if(cityTile.militaryUnit!=null)
            strength += cityTile.militaryUnit!!.getBaseUnit().strength * cityTile.militaryUnit!!.health/100f

        strength += city.cityConstructions.getBuiltBuildings().sumBy{ it.cityStrength }

        return strength.toInt()
    }

    override fun toString(): String {return city.name} // for debug

}
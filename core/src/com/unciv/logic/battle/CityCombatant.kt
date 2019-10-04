package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.UnitType
import kotlin.math.roundToInt

class CityCombatant(val city: CityInfo) : ICombatant {
    override fun getMaxHealth(): Int {
        return city.getMaxHealth()
    }

    override fun getHealth(): Int = city.health
    override fun getCivInfo(): CivilizationInfo = city.civInfo
    override fun getTile(): TileInfo = city.getCenterTile()
    override fun getName(): String = city.name
    override fun isDefeated(): Boolean = city.health==1
    override fun isInvisible(): Boolean = false
    override fun canAttack(): Boolean = (!city.attackedThisTurn)

    override fun takeDamage(damage: Int) {
        city.health -= damage
        if(city.health<1) city.health=1  // min health is 1
    }

    override fun getUnitType(): UnitType = UnitType.City
    override fun getAttackingStrength(): Int = getCityStrength() //If we don't need to multiply by a modifier number (0.75) in Damage Calculations, getCityStrength() should multiply by 1/(x^i+0.5) (x = CityStrength / DefendUnitStrength, i = if(x>1) 1 else -1).
    override fun getDefendingStrength(): Int{
        if(isDefeated()) return 1
        return getCityStrength()
    }

    fun getCityStrength(): Int { // Civ fanatics forum, from a modder who went through the original code
        var strength = 8f
        if(city.isCapital()) strength+=2f
        strength += (city.population.population/5) * 2 // Each 5 pop gives 2 defence
        val cityTile = city.getCenterTile()
        if(cityTile.baseTerrain== Constants.hill) strength+=5
        // as tech progresses so does city strength
        val techsPercentKnown: Float = city.civInfo.tech.techsResearched.count().toFloat() /
                GameBasics.Technologies.count()
        strength += Math.pow(techsPercentKnown*5.5, 2.8).toFloat()

        // The way all of this adds up...
        // All ancient techs - 0.5 extra, Classical - 2.7, Medieval - 8, Renaissance - 17.5,
        // Industrial - 32.4, Modern - 51, Atomic - 72.5, All - 118.3
        // 100% of the way through the game provides an extra 50.00

        // Garrisoned unit gives up to 20% of strength to city, in original game strengh of unit has nothing to do with health, health only is related to damage.
        if(cityTile.militaryUnit!=null)
            strength += cityTile.militaryUnit!!.baseUnit().strength / 5f

        var buildingsStrength = city.cityConstructions.getBuiltBuildings().sumBy{ it.cityStrength }.toFloat()
        if(getCivInfo().containsBuildingUnique("Defensive buildings in all cities are 25% more effective"))
            buildingsStrength*=1.25f
        strength += buildingsStrength

        return strength.roundToInt()
    }

    override fun toString(): String {return city.name} // for debug
}

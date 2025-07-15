package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.pow
import kotlin.math.roundToInt

class CityCombatant(val city: City) : ICombatant {
    override fun getMaxHealth(): Int {
        return city.getMaxHealth()
    }

    override fun getHealth(): Int = city.health
    override fun getCivInfo(): Civilization = city.civ
    override fun getTile(): Tile = city.getCenterTile()
    override fun getName(): String = city.name
    override fun isDefeated(): Boolean = city.health == 1
    override fun isInvisible(to: Civilization): Boolean = false
    override fun canAttack(): Boolean = city.canBombard()
    override fun matchesFilter(filter: String, multiFilter: Boolean) = 
        if (multiFilter) MultiFilter.multiFilter(filter, { it == "City" || it in Constants.all || city.matchesFilter(it, multiFilter = false) })
    else filter == "City" || filter in Constants.all || city.matchesFilter(filter, multiFilter = false)
    override fun getAttackSound() = UncivSound.Bombard

    override fun takeDamage(damage: Int) {
        city.health -= damage
        if (city.health < 1) city.health = 1  // min health is 1
    }

    override fun getUnitType(): UnitType = UnitType.City
    override fun getAttackingStrength(): Int = (getCityStrength(CombatAction.Attack) * 0.75).roundToInt()
    override fun getDefendingStrength(attackedByRanged: Boolean): Int {
        if (isDefeated()) return 1
        return getCityStrength()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getCityStrength(combatAction: CombatAction = CombatAction.Defend): Int { // Civ fanatics forum, from a modder who went through the original code
        val modConstants = getCivInfo().gameInfo.ruleset.modOptions.constants
        var strength = modConstants.cityStrengthBase
        strength += (city.population.population * modConstants.cityStrengthPerPop) // Each 5 pop gives 2 defence
        val cityTile = city.getCenterTile()
        for (unique in cityTile.allTerrains.flatMap { it.getMatchingUniques(UniqueType.GrantsCityStrength) })
            strength += unique.params[0].toInt()
        // as tech progresses so does city strength
        val techCount = getCivInfo().gameInfo.ruleset.technologies.size
        val techsPercentKnown: Float = if (techCount > 0) city.civ.tech.techsResearched.size.toFloat() / techCount else 0.5f // for mods with no tech
        strength += (techsPercentKnown * modConstants.cityStrengthFromTechsMultiplier).pow(modConstants.cityStrengthFromTechsExponent) * modConstants.cityStrengthFromTechsFullMultiplier

        // The way all of this adds up...
        // All ancient techs - 0.5 extra, Classical - 2.7, Medieval - 8, Renaissance - 17.5,
        // Industrial - 32.4, Modern - 51, Atomic - 72.5, All - 118.3

        // Garrisoned unit gives up to 20% of strength to city, health-dependant
        if (cityTile.militaryUnit != null)
            strength += cityTile.militaryUnit!!.baseUnit.strength * (cityTile.militaryUnit!!.health / 100f) * modConstants.cityStrengthFromGarrison

        var buildingsStrength = city.getStrength()
        val gameContext = GameContext(getCivInfo(), city, ourCombatant = this, combatAction = combatAction)

        for (unique in getCivInfo().getMatchingUniques(UniqueType.BetterDefensiveBuildings, gameContext))
            buildingsStrength *= unique.params[0].toPercent()
        strength += buildingsStrength

        return strength.roundToInt()
    }

    override fun toString() = city.name // for debug
}

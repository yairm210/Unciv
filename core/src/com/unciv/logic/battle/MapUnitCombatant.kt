package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unit.UnitType

class MapUnitCombatant(val unit: MapUnit) : ICombatant {
    override fun getHealth(): Int = unit.health
    override fun getMaxHealth() = 100
    override fun getCivInfo(): CivilizationInfo = unit.civInfo
    override fun getTile(): TileInfo = unit.getTile()
    override fun getName(): String = unit.name
    override fun isDefeated(): Boolean = unit.health <= 0
    override fun isInvisible(to: CivilizationInfo): Boolean = unit.isInvisible(to)
    override fun canAttack(): Boolean = unit.canAttack()
    override fun matchesCategory(category:String) = unit.matchesFilter(category)
    override fun getAttackSound() = unit.baseUnit.attackSound.let { 
        if (it==null) UncivSound.Click else UncivSound.custom(it)
    }

    override fun takeDamage(damage: Int) {
        unit.health -= damage
        if(isDefeated()) unit.destroy()
    }

    override fun getAttackingStrength(): Int {
        return if (isRanged()) unit.baseUnit().rangedStrength
        else unit.baseUnit().strength
    }

    override fun getDefendingStrength(): Int {
        return if (unit.isEmbarked() && !isCivilian()) 5 * getCivInfo().getEraNumber()
        else unit.baseUnit().strength
    }

    override fun getUnitType(): UnitType {
        return unit.type
    }

    override fun toString(): String {
        return unit.name+" of "+unit.civInfo.civName
    }

    fun getMatchingUniques(uniqueTemplate: String): Sequence<Unique> = unit.getMatchingUniques(uniqueTemplate)

}
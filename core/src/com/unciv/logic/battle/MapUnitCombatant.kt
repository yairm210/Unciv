package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unit.UnitType

class MapUnitCombatant(val unit: MapUnit) : ICombatant {
    override fun getHealth(): Int = unit.health
    override fun getMaxHealth() = 100
    override fun getCivInfo(): CivilizationInfo = unit.civInfo
    override fun getTile(): TileInfo = unit.getTile()
    override fun getName(): String = unit.name
    override fun isDefeated(): Boolean = unit.health <= 0
    override fun isInvisible(): Boolean = unit.isInvisible()
    override fun canAttack(): Boolean = unit.canAttack()

    override fun takeDamage(damage: Int) {
        unit.health -= damage
        if(isDefeated()) unit.destroy()
    }

    override fun getAttackingStrength(): Int {
        if (isRanged()) return unit.baseUnit().rangedStrength
        else return unit.baseUnit().strength
    }

    override fun getDefendingStrength(): Int {
        if(unit.isEmbarked() && !unit.type.isCivilian())
            return 5 * getCivInfo().getEraNumber()
        return unit.baseUnit().strength
    }

    override fun getUnitType(): UnitType {
        return unit.type
    }

    override fun toString(): String {
        return unit.name+" of "+unit.civInfo.civName
    }
}
package com.unciv.view

import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign cities. Superclass of [CityView]. */
open class ForeignCityView(internal open val city: City, internal open val viewer: Civilization) {
    val name: String get() = city.name
    val location: HexCoord get() = city.location

    @Readonly fun getHealth(): Int = city.health
    @Readonly fun getMaxHealth(): Int = city.getMaxHealth()
    @Readonly fun getDefendingStrength(): Int = CityCombatant(city).getDefendingStrength()
    @Readonly fun getAttackingStrength(): Int = CityCombatant(city).getAttackingStrength()
    @Readonly fun getCenterTile(): Tile = city.getCenterTile()
    @Readonly fun canBombard(): Boolean = city.canBombard()
    @Readonly fun civ(): CivView = CivView(city.civ, viewer)
    @Readonly fun isSameCivAs(other: ForeignCityView): Boolean = city.civ === other.city.civ
    @Readonly fun getCity(): City = city

    override fun equals(other: Any?) = other is ForeignCityView && city === other.city
    override fun hashCode() = city.hashCode()
}

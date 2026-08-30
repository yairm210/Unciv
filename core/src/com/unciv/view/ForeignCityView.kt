package com.unciv.view

import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.city.managers.CityReligionManager
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.map.HexCoord
import com.unciv.models.ImmutableColor
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.DebugUtils
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign cities. Superclass of [CityView]. */
open class ForeignCityView(internal open val city: City,
                           viewer: Civilization,
                           spectatorMode: Boolean = false,
                           open val gameView: GameView) : GameBasedView<City>(city, viewer, spectatorMode) {
    val name: String get() = city.name
    val location: HexCoord get() = city.location

    /** The owning civ's [CivView], as visible from [viewer]'s perspective. For the viewing player's full CivView, use [CityView.viewingCiv]. */
    val owningCivView: CivView get() = gameView.getCivView(city.civ)

    // Navigation
    @Readonly fun getCity(): City = city
    @Readonly fun getViewingCiv(): Civilization = viewer
    /** The owning civ of this city, as visible from [viewer]'s perspective. For the viewing player's full CivView, use [CityView.viewingCiv]. */
    @Readonly open fun owningCiv(): ForeignCivView = ForeignCivView(city.civ, viewer, spectatorMode)
    /** Get from a foreign view to an inner view */
    @Readonly fun tryGetCityView(): CityView? {
        val canSeeCityData = viewer.isSpectator() // not posing, actual spectator
                || city.civ == viewer
                || spyIsSetUpAtCity(viewer)
                || DebugUtils.VISIBLE_MAP
        if (!canSeeCityData) return null
        return gameView.getCityView(city)
    }

    // Data retrieval
    @Readonly fun getHealth(): Int = city.health
    @Readonly fun getMaxHealth(): Int = city.getMaxHealth()
    @Readonly fun getDefendingStrength(): Int = CityCombatant(city).getDefendingStrength()
    @Readonly fun getAttackingStrength(): Int = CityCombatant(city).getAttackingStrength()
    @Readonly fun getCenterTile(): TileView {
        val tile = city.getCenterTile()
        return gameView.tileMapView.getTile(tile)
    }
    @Readonly fun canBombard(): Boolean = city.canBombard()
    @Readonly fun isSameCivAs(other: ForeignCityView): Boolean = city.civ === other.city.civ
    @Readonly fun getProductionMarkup(): FormattedLine = city.cityConstructions.getProductionMarkup(city.getRuleset())

    @Readonly fun belongsTo(civ: Civilization): Boolean = city.civ === civ
    @Readonly fun isCityState(): Boolean = city.civ.isCityState
    @Readonly fun civKnows(civ: Civilization): Boolean = city.civ.knows(civ)
    @Readonly fun isKnownTo(civ: Civilization): Boolean = civ.knows(city.civ)
    @Readonly fun getDiplomacyManagerWith(civ: Civilization): DiplomacyManager? = city.civ.getDiplomacyManager(civ)
    @Readonly fun isEspionageEnabled(): Boolean = city.civ.gameInfo.isEspionageEnabled()
    @Readonly fun isReligionEnabled(): Boolean = city.civ.gameInfo.isReligionEnabled()
    @Readonly fun spyIsSetUpAtCity(viewer: Civilization): Boolean =
        viewer.espionageManager.getSpyAssignedToCity(city)?.isSetUp() == true
    @Readonly fun getCivInnerColor(): ImmutableColor = city.civ.nation.getInnerColor()
    @Readonly fun getReligionManager(): CityReligionManager = city.religion
}

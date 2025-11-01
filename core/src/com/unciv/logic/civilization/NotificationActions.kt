package com.unciv.logic.civilization

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.screens.overviewscreen.EspionageOverviewScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen


/** defines what to do if the user clicks on a notification */
/*
 * Not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 *
 * IsPartOfGameInfoSerialization is just a marker class and not actually tested for, so inheriting it
 * _indirectly_ is OK (the NotificationAction subclasses need not re-implement, a `is` test would still succeed).
 *
 * Also note all implementations need the default no-args constructor for deserialization,
 * therefore the otherwise unused default initializers.
 */
interface NotificationAction : IsPartOfGameInfoSerialization {
    fun execute(worldScreen: WorldScreen)
}

/** A notification action that shows map places. */
class LocationAction(private val location: Vector2 = Vector2.Zero) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.mapHolder.setCenterPosition(location, selectUnit = false)
    }

    /**
     *  This Companion implements constructor-like factories through [invoke] to simulate the old [LocationAction]
     *  which stored several locations (back then in turn there was only one action per Notification).
     *  - Meant to be used in [Civilization.addNotification] calls.
     *  - Note there are overloads accepting `Iterable`, `Sequence` or `vararg` of [Vector2]
     *  - The `vararg` version accepts and ignores `null`s, often making pre-filtering of conditionally available locations unnecessary.
     *
     *  Example: `addNotification("Bob hit alice", LocationAction(bob.position, alice.position), NotificationCategory.War)`
     */
    companion object {
        operator fun invoke(locations: Sequence<Vector2>): Sequence<LocationAction> =
            locations.map { LocationAction(it) }
        operator fun invoke(locations: Iterable<Vector2>): Sequence<LocationAction> =
            locations.asSequence().map { LocationAction(it) }
        operator fun invoke(vararg locations: Vector2?): Sequence<LocationAction> =
            locations.asSequence().filterNotNull().map { LocationAction(it) }
    }
}

/** show tech screen */
class TechAction(private val techName: String = "") : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val tech = worldScreen.gameInfo.ruleset.technologies[techName]
        worldScreen.game.pushScreen(TechPickerScreen(worldScreen.selectedCiv, tech))
    }
}

/** enter city */
class CityAction(private val city: Vector2 = Vector2.Zero) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val cityObject = worldScreen.mapHolder.tileMap[city].getCity()
            ?: return
        if (cityObject.civ == worldScreen.viewingCiv)
            worldScreen.game.pushScreen(CityScreen(cityObject))
    }
    companion object {
        fun withLocation(city: City) = listOf(LocationAction(city.location), CityAction(city.location))
    }
}

/** enter diplomacy screen */
class DiplomacyAction(
    private val otherCivName: String = "",
    private var showTrade: Boolean = false
) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val currentCiv = worldScreen.selectedCiv
        val otherCiv = worldScreen.gameInfo.getCivilization(otherCivName)

        if (showTrade && otherCiv == currentCiv)
            // Because TradeTable will set up otherCiv against that one,
            // not the one we pass below, and two equal civs will crash - can't look up a DiplomacyManager.
            return
        // We should not be able to trade with city-states
        if (showTrade && (otherCiv.isCityState || currentCiv.isCityState))
            showTrade = false

        if (showTrade && currentCiv.isAtWarWith(otherCiv))
            showTrade = false  // Can't trade right now

        worldScreen.game.pushScreen(DiplomacyScreen(currentCiv, otherCiv, showTrade = showTrade))
    }
}

/** enter Maya Long Count popup */
class MayaLongCountAction : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, worldScreen.gameInfo.getYear())
    }
}

/** A notification action that shows **and selects** things on the map.
 *
 *  Saves and serializes only the location and optionally a MapUnit id.
 *  When an id is present, activation will select that unit if it is still in the given position - otherwise, it will behave like LocationAction.
 *  Without id, activation will select the tile which will select any unit on it or cycle through selections if this NotificationAction is the only one on the Notification.
 *  When the unit has been moved away, activation still shows the tile and not the unit.
 *  Activation without unit id also works for cities, selecting them - so a bombard is one click less.
 */
class MapUnitAction(
    private val location: Vector2 = Vector2.Zero,
    private val id: Int = Constants.NO_ID
) : NotificationAction {
    constructor(unit: MapUnit) : this(unit.currentTile.position, unit.id)
    override fun execute(worldScreen: WorldScreen) {
        val selectUnit = id != Constants.NO_ID  // This is the unspecific "select any unit on that tile", specific works without this being on
        val unit = if (selectUnit) null else
            worldScreen.gameInfo.tileMap[location].getUnits().firstOrNull { it.id == id }
        worldScreen.mapHolder.setCenterPosition(location, selectUnit = selectUnit, forceSelectUnit = unit)
    }
    companion object {
        // Convenience shortcut as it makes replacing LocationAction calls easier (see above)
        operator fun invoke(units: Iterable<MapUnit>): Sequence<MapUnitAction> =
            units.asSequence().map { MapUnitAction(it) }
    }
}

/** A notification action that shows a Civilopedia entry, e.g. for a Wonder. */
class CivilopediaAction(private val link: String = "") : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.openCivilopedia(link)
    }
}

/** Show Promotion picker for a MapUnit - by name and location, as they lack a serialized unique ID */
class PromoteUnitAction(private val name: String = "", private val location: Vector2 = Vector2.Zero) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val tile = worldScreen.gameInfo.tileMap[location]
        val unit = tile.militaryUnit?.takeIf { it.name == name && it.civ == worldScreen.selectedCiv }
            ?: return
        worldScreen.game.pushScreen(PromotionPickerScreen(unit))
    }
}

/** Open the Empire Overview to a specific page, potentially "selecting" some entry */
class OverviewAction(
    private val page: EmpireOverviewCategories = EmpireOverviewCategories.Resources,
    private val select: String = ""
) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv, page, select))
    }
}

/** Open policy picker, optionally preselecting [select] (how or if at all that works for branches is [PolicyPickerScreen]'s business) */
class PolicyAction(
    private val select: String? = null
) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.game.pushScreen(PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState, select))
    }
}

/** Open [EspionageOverviewScreen] */
class EspionageAction : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.game.pushScreen(EspionageOverviewScreen(worldScreen.selectedCiv, worldScreen))
    }
    companion object {
        fun withLocation(location: Vector2?): Sequence<NotificationAction> =
            LocationAction(location) + EspionageAction()
    }
}

/** Open [url] externally in the browser */
class LinkAction(private val url: String = "") : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        if (url.isNotEmpty()) Gdx.net.openURI(url)
    }
}

/** Open [EmpireOverviewScreen] on the [Religion][EmpireOverviewCategories.Religion] tab */
class ReligionAction(private val religionName: String? = null) : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.game.pushScreen(EmpireOverviewScreen(worldScreen.selectedCiv, EmpireOverviewCategories.Religion, religionName.orEmpty()))
    }
    companion object {
        fun withLocation(location: Vector2?, religionName: String?): Sequence<NotificationAction> =
            LocationAction(location) + ReligionAction(religionName)
    }
}


@Suppress("PrivatePropertyName")  // These names *must* match their class name, see below
/** This exists as trick to leverage readFields for Json deserialization.
 *
 *  The serializer writes each [NotificationAction] as json object (within the [actions][Notification.actions] array),
 *  containing the class `simpleName` as subfield name, which carries any (or none) subclass-specific data as its object value.
 *  So, reading this from json data will fill just one of the fields, and the `listOfNotNull` will output that field only.
 *  Even though we know there's only one result, no need to `first()` since it's no advantage to the caller.
 *
 *  In a way, this is like asking the class loader to resolve the class by compilation instead of via the reflection API, like Gdx would try unaided.
 */
internal class NotificationActionsDeserializer {
    private val LocationAction: LocationAction? = null
    private val TechAction: TechAction? = null
    private val CityAction: CityAction? = null
    private val DiplomacyAction: DiplomacyAction? = null
    private val MayaLongCountAction: MayaLongCountAction? = null
    private val MapUnitAction: MapUnitAction? = null
    private val CivilopediaAction: CivilopediaAction? = null
    private val PromoteUnitAction: PromoteUnitAction? = null
    private val OverviewAction: OverviewAction? = null
    private val PolicyAction: PolicyAction? = null
    private val EspionageAction: EspionageAction? = null
    private val LinkAction: LinkAction? = null
    private val ReligionAction: ReligionAction? = null

    fun read(json: Json, jsonData: JsonValue): List<NotificationAction> {
        json.readFields(this, jsonData)
        return listOfNotNull(
            LocationAction, TechAction, CityAction, DiplomacyAction, MayaLongCountAction,
            MapUnitAction, CivilopediaAction, PromoteUnitAction, OverviewAction, PolicyAction,
            EspionageAction, LinkAction, ReligionAction
        )
    }
}

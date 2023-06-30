package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen


/** defines what to do if the user clicks on a notification */
/*
 * Not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 */
interface NotificationAction : IsPartOfGameInfoSerialization {
    fun execute(worldScreen: WorldScreen)
}

/** A notification action that shows map places. */
class LocationAction(var location: Vector2) : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.mapHolder.setCenterPosition(location, selectUnit = false)
    }
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
class TechAction(val techName: String = "") : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        val tech = worldScreen.gameInfo.ruleset.technologies[techName]
        worldScreen.game.pushScreen(TechPickerScreen(worldScreen.viewingCiv, tech))
    }
}

/** enter city */
class CityAction(val city: Vector2 = Vector2.Zero): NotificationAction,
    IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        val cityObject = worldScreen.mapHolder.tileMap[city].getCity()
            ?: return
        if (cityObject.civ == worldScreen.viewingCiv)
            worldScreen.game.pushScreen(CityScreen(cityObject))
    }
}

/** enter diplomacy screen */
class DiplomacyAction(val otherCivName: String = ""): NotificationAction,
    IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        val otherCiv = worldScreen.gameInfo.getCivilization(otherCivName)
        worldScreen.game.pushScreen(DiplomacyScreen(worldScreen.viewingCiv, otherCiv))
    }
}

/** enter Maya Long Count popup */
class MayaLongCountAction : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, worldScreen.gameInfo.getYear())
    }
}

/** A notification action that shows and selects units on the map. */
class MapUnitAction(var location: Vector2) : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.mapHolder.setCenterPosition(location, selectUnit = true)
    }
}

/** A notification action that shows the Civilopedia entry for a Wonder. */
class WonderAction(val wonderName: String) : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.game.pushScreen(CivilopediaScreen(worldScreen.gameInfo.ruleset, CivilopediaCategories.Wonder, wonderName))
    }
}

/** Show Promotion picker for a MapUnit - by name and location, as they lack a serialized unique ID */
class PromoteUnitAction(val name: String, val location: Vector2) : NotificationAction, IsPartOfGameInfoSerialization {
    override fun execute(worldScreen: WorldScreen) {
        val tile = worldScreen.gameInfo.tileMap[location]
        val unit = tile.militaryUnit?.takeIf { it.name == name && it.civ == worldScreen.selectedCiv }
            ?: return
        worldScreen.game.pushScreen(PromotionPickerScreen(unit))
    }
}

@Suppress("PropertyName")
internal class NotificationActionsDeserializer {
    // This exists as trick to leverage readFields for Json deserialization
    private var locationAction: LocationAction? = null
    private var techAction: TechAction? = null
    private var cityAction: CityAction? = null
    private var diplomacyAction: DiplomacyAction? = null
    private var mayaLongCountAction: MayaLongCountAction? = null
    private var mapUnitAction: MapUnitAction? = null
    private var wonderAction: WonderAction? = null
    private var promoteUnitAction: PromoteUnitAction? = null
    fun read(json: Json, jsonData: JsonValue): List<NotificationAction> {
        json.readFields(this, jsonData)
        return listOfNotNull(
            locationAction, techAction, cityAction, diplomacyAction,
            mayaLongCountAction, mapUnitAction, wonderAction, promoteUnitAction
        )
    }
}

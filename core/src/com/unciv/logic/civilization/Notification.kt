package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.worldscreen.WorldScreen

object NotificationIcon {
    // Remember: The typical white-on-transparency icon will not be visible on Notifications
    const val Culture = "StatIcons/Culture"
    const val Construction = "StatIcons/Production"
    const val Growth = "StatIcons/Population"
    const val War = "OtherIcons/Pillage"
    const val Trade = "StatIcons/Acquire"
    const val Science = "StatIcons/Science"
    const val Gold = "StatIcons/Gold"
    const val Death = "OtherIcons/DisbandUnit"
    const val Diplomacy = "OtherIcons/Diplomacy"
    const val City = "ImprovementIcons/City center"
    const val Citadel = "ImprovementIcons/Citadel"
    const val Happiness = "StatIcons/Happiness"
    const val Population = "StatIcons/Population"
    const val CityState = "OtherIcons/CityState"
    const val Production = "StatIcons/Production"
    const val Food = "StatIcons/Food"
    const val Faith = "StatIcons/Faith"
    const val Crosshair = "OtherIcons/CrosshairB"
}

/**
 * [action] is not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 */
open class Notification() {

    var text: String = ""

    var icons: ArrayList<String> = ArrayList() // Must be ArrayList and not List so it can be deserialized
    var action: NotificationAction? = null

    constructor(text: String, notificationIcons: ArrayList<String>, action: NotificationAction? = null) : this() {
        this.text = text
        this.icons = notificationIcons
        this.action = action
    }
}

/** defines what to do if the user clicks on a notification */
interface NotificationAction {
    fun execute(worldScreen: WorldScreen)
}

/** A notification action that cycles through tiles.
 * 
 * Constructors accept any kind of [Vector2] collection, including [Iterable], [Sequence], `vararg`.
 * `varargs` allows nulls which are ignored, a resulting empty list is allowed and equivalent to no [NotificationAction].
 */
data class LocationAction(var locations: ArrayList<Vector2> = ArrayList()) : NotificationAction {
    constructor(locations: Iterable<Vector2>) : this(locations.toCollection(ArrayList()))
    constructor(locations: Sequence<Vector2>) : this(locations.toCollection(ArrayList()))
    constructor(vararg locations: Vector2?) : this(locations.asSequence().filterNotNull())

    override fun execute(worldScreen: WorldScreen) {
        if (locations.isNotEmpty()) {
            var index = locations.indexOf(worldScreen.mapHolder.selectedTile?.position)
            index = ++index % locations.size // cycle through tiles
            worldScreen.mapHolder.setCenterPosition(locations[index], selectUnit = false)
        }
    }
}

/** show tech screen */
class TechAction(val techName: String = "") : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val tech = worldScreen.gameInfo.ruleSet.technologies[techName]
        worldScreen.game.setScreen(TechPickerScreen(worldScreen.viewingCiv, tech))
    }
}

/** enter city */
data class CityAction(val city: Vector2 = Vector2.Zero): NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.mapHolder.tileMap[city].getCity()?.let {
            if (it.civInfo == worldScreen.viewingCiv)
                worldScreen.game.setScreen(CityScreen(it))
        }
    }
}

/** enter diplomacy screen */
data class DiplomacyAction(val otherCivName: String = ""): NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val screen = DiplomacyScreen(worldScreen.viewingCiv)
        screen.updateRightSide(worldScreen.gameInfo.getCivilization(otherCivName))
        worldScreen.game.setScreen(screen)
    }
}

/** enter Maya Long Count popup */
class MayaLongCountAction() : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, worldScreen.gameInfo.getYear())
    }
}

package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.worldscreen.WorldScreen

object NotificationIcon {
    val Culture = "StatIcons/Culture"
    val Construction = "StatIcons/Production"
    val Growth = "StatIcons/Population"
    val War = "OtherIcons/Pillage"
    val Trade = "StatIcons/Acquire"
    val Science = "StatIcons/Science"
    val Gold = "StatIcons/Gold"
    val Death = "OtherIcons/DisbandUnit"
    val Diplomacy = "OtherIcons/Diplomacy"
}

/**
 * [action] is not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 */
open class Notification() {

    var text: String=""
    @Deprecated("As of 3.13.10 - replaced with icons")
    var color: Color?=null
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

/** cycle through tiles */
data class LocationAction(var locations: ArrayList<Vector2> = ArrayList()) : NotificationAction {

    constructor(locations: List<Vector2>): this(ArrayList(locations))

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
            worldScreen.game.setScreen(CityScreen(it))
        }
    }

}

data class DiplomacyAction(val otherCivName: String = ""): NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val screen = DiplomacyScreen(worldScreen.viewingCiv)
        screen.updateRightSide(worldScreen.gameInfo.getCivilization(otherCivName))
        worldScreen.game.setScreen(screen)
    }
}
package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.worldscreen.WorldScreen

enum class NotificationType {
    Culture,
    Construction,
    Growth,
    War,
    Trade,
    Science
}

/**
 * [action] is not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 */
open class Notification() {

    var text: String=""
    var color: Color?=null
    var notificationType:NotificationType?=null
    var action: NotificationAction? = null

    // default parameters necessary for json deserialization
    constructor(text: String, color: Color, action: NotificationAction?) : this() {
        this.text = text
        this.color = color
        this.action = action
    }

    constructor(text: String, notificationType: NotificationType, action: NotificationAction? = null) : this() {
        this.text = text
        this.notificationType = notificationType
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
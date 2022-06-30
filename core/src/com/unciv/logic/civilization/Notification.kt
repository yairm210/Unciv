package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.images.ImageGetter
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
    const val Scout = "UnitIcons/Scout"
    const val Ruins = "ImprovementIcons/Ancient ruins"
    const val Barbarians = "ImprovementIcons/Barbarian encampment"
}

enum class NotificationColor(val color: Color) {
    Default(Color(1f,1f,1f,1f)),
    Construction(Color(0.992f, 0.847f, 0.694f, 1f)),
    VeryBad(Color(0.996f, 0.752f, 0.698f, 1f)),
    Bad(Color(1f, 0.835f, 0.8f, 1f)),
    VeryGood(Color(0.745f, 1f, 0.698f, 1f)),
    Good(Color(0.843f, 1f, 0.811f, 1f)),
    WLTK(Color(0.956f, 0.988f, 0.678f, 1f)),
    Gold(Color(0.976f, 1f, 0.784f, 1f)),
    Quest(Color(0.733f, 0.945f, 1f, 1f))
}

/**
 * [action] is not realized as lambda, as it would be too easy to introduce references to objects
 * there that should not be serialized to the saved game.
 */
open class Notification() {

    var text: String = ""

    var icons: ArrayList<String> = ArrayList() // Must be ArrayList and not List so it can be deserialized
    var action: NotificationAction? = null
    var color: NotificationColor = NotificationColor.Default

    constructor(
        text: String,
        notificationIcons: ArrayList<String>,
        action: NotificationAction? = null,
        color: NotificationColor = NotificationColor.Default
    ) : this() {
        this.text = text
        this.icons = notificationIcons
        this.action = action
        this.color = color
    }

    fun addNotificationIcons(ruleset: Ruleset, iconSize: Float, table: Table) {
        if (icons.isEmpty()) return
        for (icon in icons.reversed()) {
            val image: Actor = when {
                ruleset.technologies.containsKey(icon) -> ImageGetter.getTechIcon(icon)
                ruleset.nations.containsKey(icon) -> ImageGetter.getNationIndicator(
                    ruleset.nations[icon]!!,
                    iconSize
                )
                ruleset.units.containsKey(icon) -> ImageGetter.getUnitIcon(icon)
                else -> ImageGetter.getImage(icon)
            }
            table.add(image).size(iconSize).padRight(5f)
        }
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
        worldScreen.game.pushScreen(TechPickerScreen(worldScreen.viewingCiv, tech))
    }
}

/** enter city */
data class CityAction(val city: Vector2 = Vector2.Zero): NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        worldScreen.mapHolder.tileMap[city].getCity()?.let {
            if (it.civInfo == worldScreen.viewingCiv)
                worldScreen.game.pushScreen(CityScreen(it))
        }
    }
}

/** enter diplomacy screen */
data class DiplomacyAction(val otherCivName: String = ""): NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        val otherCiv = worldScreen.gameInfo.getCivilization(otherCivName)
        worldScreen.game.pushScreen(DiplomacyScreen(worldScreen.viewingCiv, otherCiv))
    }
}

/** enter Maya Long Count popup */
class MayaLongCountAction : NotificationAction {
    override fun execute(worldScreen: WorldScreen) {
        MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, worldScreen.gameInfo.getYear())
    }
}

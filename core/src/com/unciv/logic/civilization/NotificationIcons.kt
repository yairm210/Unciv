package com.unciv.logic.civilization

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.JsonValue
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.images.ImageGetter

interface INotificationIcon {
    val type: NotificationIconType
    fun getImage(iconSize: Float): Actor
    fun getValueForJson(): String
}

enum class NotificationIconType {
    Set {
        override fun parse(value: String) =
            NotificationIcon.values().firstOrNull { it.name == value }
    },
    Other {
        override fun parse(value: String) = OtherNotificationIcon(value)
    },
    Tech {
        override fun parse(value: String) = TechNotificationIcon(value)
    },
    Nation {
        override fun parse(value: String) = NationNotificationIcon(value)
    },
    Unit {
        override fun parse(value: String) = UnitNotificationIcon(value)
    },
    ;
    abstract fun parse(value: String): INotificationIcon?
}

enum class NotificationIcon(
    private val imagePath: String
) : INotificationIcon {
    // Remember: The typical white-on-transparency icon will not be visible on Notifications

    Barbarians("ImprovementIcons/Barbarian encampment"),
    Citadel("ImprovementIcons/Citadel"),
    City("ImprovementIcons/City center"),
    CityState("OtherIcons/CityState"),
    Crosshair("OtherIcons/CrosshairB"),
    Culture("StatIcons/Culture"),
    Construction("StatIcons/Production"),
    Death("OtherIcons/DisbandUnit"),
    Diplomacy("OtherIcons/Diplomacy"),
    Faith("StatIcons/Faith"),
    Food("StatIcons/Food"),
    Gold("StatIcons/Gold"),
    Growth("StatIcons/Population"),
    Happiness("StatIcons/Happiness"),
    Population("StatIcons/Population"),
    Production("StatIcons/Production"),
    Question("OtherIcons/Question"),
    Ruins("ImprovementIcons/Ancient ruins"),
    Science("StatIcons/Science"),
    Scout("UnitIcons/Scout"),
    Spy("OtherIcons/Spy"),
    Trade("StatIcons/Acquire"),
    War("OtherIcons/Pillage"),
    ;

    override val type = NotificationIconType.Set
    override fun getImage(iconSize: Float) = ImageGetter.getImage(imagePath)
    override fun toString() = imagePath
    override fun getValueForJson() = name

    companion object {
        fun parseOldFormat(ruleset: Ruleset?, icon: String): INotificationIcon? {
            return when {
                ruleset != null && ruleset.technologies.containsKey(icon) -> TechNotificationIcon(icon)
                ruleset != null && ruleset.nations.containsKey(icon) -> NationNotificationIcon(icon)
                ruleset != null && ruleset.units.containsKey(icon) -> UnitNotificationIcon(icon)
                else -> NotificationIcon.values().firstOrNull { it.imagePath == icon }
                    ?: (if ('/' in icon) OtherNotificationIcon(icon) else null)
            }
        }

        fun parseJson(jsonData: JsonValue): INotificationIcon? {
            if (!jsonData.isObject) return null
            val field = jsonData.child()
            val type = NotificationIconType.values().firstOrNull { it.name == field.name }
                ?: return null
            return type.parse(field.asString())
        }

        fun convertToTyped(notificationIcons: Array<out Any>, ruleset: Ruleset): ArrayList<INotificationIcon> {
            val result = ArrayList<INotificationIcon>(notificationIcons.size)
            for (entry in notificationIcons) {
                when (entry) {
                    is INotificationIcon -> result += entry
                    is String -> result += NotificationIcon.parseOldFormat(ruleset, entry)
                        ?: throw IllegalArgumentException("Not found: $entry")
                    else -> throw IllegalArgumentException("Wrong type: " + entry::class.java.simpleName)
                }
            }
            return result
        }
    }
}

class OtherNotificationIcon(val path: String) : INotificationIcon {
    override val type = NotificationIconType.Other
    override fun getImage(iconSize: Float) = ImageGetter.getImage(path)
    override fun getValueForJson() = path
}

class TechNotificationIcon(val name: String) : INotificationIcon {
    constructor(tech: Technology) : this(tech.name)
    override val type = NotificationIconType.Tech
    override fun getImage(iconSize: Float) = ImageGetter.getTechIconPortrait(name, iconSize)
    override fun getValueForJson() = name
}

class NationNotificationIcon(val nation: String) : INotificationIcon {
    constructor(civ: Civilization) : this(civ.civName)
    override val type = NotificationIconType.Nation
    override fun getImage(iconSize: Float): Actor {
        val nationObj = ImageGetter.ruleset.nations[nation]
            ?: return ImageGetter.getWhiteDot()
        return ImageGetter.getNationPortrait(nationObj, iconSize)
    }
    override fun getValueForJson() = nation
}

class UnitNotificationIcon(val name: String) : INotificationIcon {
    constructor(unit: BaseUnit) : this(unit.name)
    override val type = NotificationIconType.Unit
    override fun getImage(iconSize: Float) = ImageGetter.getUnitIcon(name)
    override fun getValueForJson() = name
}

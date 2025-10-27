package com.unciv.logic.civilization

import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.images.ImageGetter

@Suppress("ConstPropertyName") // We want enum-like names
/**
 *  For use in the `notificationIcons` parameter of [addNotification][Civilization.addNotification],
 *  which accepts texture paths or some ruleset object names (nation, tech, unit) - this will be translated through [getImage].
 */
object NotificationIcon {
    // Remember: The typical white-on-transparency icon will not be visible on Notifications

    const val Barbarians = "ImprovementIcons/Barbarian encampment"
    const val City = "ImprovementIcons/City center"
    const val CityState = "OtherIcons/CityState"
    const val Crosshair = "OtherIcons/CrosshairB"
    const val Culture = "StatIcons/Culture"
    const val Construction = "StatIcons/Production"
    const val Death = "OtherIcons/DisbandUnit"
    const val Diplomacy = "OtherIcons/Diplomacy"
    const val Faith = "StatIcons/Faith"
    const val Food = "StatIcons/Food"
    const val Gold = "StatIcons/Gold"
    const val Growth = "StatIcons/Population"
    const val Happiness = "StatIcons/Happiness"
    const val Population = "StatIcons/Population"
    const val Production = "StatIcons/Production"
    const val Question = "OtherIcons/Question"
    const val Ruins = "ImprovementIcons/Ancient ruins"
    const val Science = "StatIcons/Science"
    const val Scout = "UnitIcons/Scout"
    const val Spy = "OtherIcons/Spy"
    const val Trade = "StatIcons/Acquire"
    const val War = "OtherIcons/Pillage"

    /** Get the image for a [Notification] icon, translating:
     *  - A [Baseunit][com.unciv.models.ruleset.unit.BaseUnit] name to [ImageGetter.getUnitIcon]
     *  - A [Nation][com.unciv.models.ruleset.nation.Nation] name to [ImageGetter.getNationPortrait]
     *  - A [Technology][com.unciv.models.ruleset.tech.Technology] name to [ImageGetter.getTechIconPortrait]
     *  - Otherwise, the string must be a direct texture path resolvable by [ImageGetter.getImage]
     */
    fun getImage(icon: String, ruleset: Ruleset, iconSize: Float) = when {
        ruleset.technologies.containsKey(icon) ->
            ImageGetter.getTechIconPortrait(icon, iconSize)
        ruleset.nations.containsKey(icon) ->
            ImageGetter.getNationPortrait(ruleset.nations[icon]!!, iconSize)
        ruleset.units.containsKey(icon) ->
            ImageGetter.getUnitIcon(ruleset.units[icon]!!)
        ruleset.unitPromotions.containsKey(icon) ->
            ImageGetter.getPromotionPortrait(icon, iconSize)
        ruleset.tileResources.containsKey(icon) ->
            ImageGetter.getResourcePortrait(icon, iconSize)
        else ->
            ImageGetter.getImage(icon)
    }
}

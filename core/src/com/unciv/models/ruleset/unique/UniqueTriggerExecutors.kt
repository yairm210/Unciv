package com.unciv.models.ruleset.unique

import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.fillPlaceholders

/**
 *  Container for the innermost "executors" for triggers, the code actually affecting game state.
 *
 *  [UniqueTriggerActivation.getTriggerFunction] wraps them in lambdas, therefore this also serves
 *  as documentation what exactly is needed as closure - here the function parameters.
 *  They should return the Boolean "success" status the final lambda should return.
 *
 *  TODO This is a fresh file, there are many triggers that could move part of their code here.
 */
internal object UniqueTriggerExecutors {

    fun triggerGlobalAlerts(
        civInfo: Civilization,
        alertText: String,
        triggerNotificationText: String
    ): Boolean {
        // civInfo is the cause of the alert, the one not getting notified.
        // triggerNotificationText is e.g. "due to adopting [${policy.name}]" or "due to researching [$techName]"
        // alertText is the parameter of the unique.

        // Translate what the original triggerUnique got from the container into something aimed
        // at the other civilizations, and guess an icon:
        val (messageSuffix, iconName) = when {
            triggerNotificationText.startsWith("due to adopting ") ->
                // templates:
                // [civName|An unknown civilization] has adopted the [policyName] policy
                "has adopted the " + triggerNotificationText.removePrefix("due to adopting ") +
                " policy" to NotificationIcon.Culture
            triggerNotificationText.startsWith("due to researching ") ->
                // templates:
                // [civName|An unknown civilization] has researched [techName]
                "has researched " + triggerNotificationText.removePrefix("due to researching ") to
                NotificationIcon.Science
            // Kludge! If we get here, the translation template will be missing.
            triggerNotificationText.startsWith("due to ") ->
                triggerNotificationText.removePrefix("due to ") to ""
            // Megakludge! If we get here, the notification will be unreadable. Probably.
            else -> triggerNotificationText to ""
        }

        for (civ in civInfo.gameInfo.civilizations) {
            if (civ == civInfo || !civ.isMajorCiv()) continue
            val defaultNotificationText = if (civ.getKnownCivs().contains(civInfo)) {
                "[${civInfo.civName}] $messageSuffix"
            } else {
                "[An unknown civilization] $messageSuffix"
            }
            civ.addNotification(
                "{${defaultNotificationText}}.\n{${alertText}}",
                NotificationCategory.General, iconName
            )
        }

        return true
    }

    fun canReduceCityFlag(civInfo: Civilization, unique: Unique, unit: MapUnit?): Triple<City, CityFlags, Int>? {
        val city = unit?.currentTile?.getCity() ?: return null
        if (!city.matchesFilter(unique.params[2], civInfo)) return null
        val amount = unique.params[0].toInt()
        val flag = CityFlags.safeValueOf(unique.params[1]) ?: return null
        if (!shouldLimitFlagAmount(city, flag, amount)) return null
        return Triple(city, flag, amount)
    }

    private fun shouldLimitFlagAmount(city: City, flag: CityFlags, amount: Int): Boolean {
        if (!city.hasFlag(flag)) return false
        if (flag == CityFlags.ResourceDemand && city.demandedResource.isEmpty()) return false
        if (amount == 0 || amount > 0 && city.getFlag(flag) <= 1) return false // can't reduce past 1
        return true
    }

    fun getReduceCityFlagActionText(unique: Unique, unit: MapUnit): String {
        val city = unit.currentTile.getCity()
        val flag = CityFlags.safeValueOf(unique.params[1])!!
        var amount = unique.params[0].toInt()
        if (city != null && shouldLimitFlagAmount(city, flag, amount))
            amount = amount.coerceAtMost(city.getFlag(flag) - 1)
        val inCity = city?.let { "in [${it.name}]" }.orEmpty()
        return unique.placeholderText.fillPlaceholders(amount.toString(), unique.params[1], inCity)
    }

    fun reduceCityFlag(city: City, flag: CityFlags, amount: Int): Boolean {
        val old = city.getFlag(flag)
        val new = (old - amount).coerceAtLeast(1) // We don't want to deal with the countdown expiring NOW
        city.setFlag(flag, new)
        return true
    }
}

package com.unciv.models.ruleset.unique

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon

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

        for (civ in civInfo.gameInfo.civilizations.filter { it.isMajorCiv() }) {
            if (civ == civInfo) continue
            val defaultNotificationText = if (civ.getKnownCivs().contains(civInfo)) {
                "[${civInfo.civName}] $messageSuffix"
            } else {
                "[An unknown civilization] $messageSuffix"
            }
            civ.addNotification(
                "{${defaultNotificationText}} {${alertText}}",
                NotificationCategory.General, iconName
            )
        }

        return true
    }

}

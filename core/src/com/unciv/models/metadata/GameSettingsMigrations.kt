package com.unciv.models.metadata

import com.badlogic.gdx.utils.JsonValue
import java.time.Duration

private const val CURRENT_VERSION = 2

fun GameSettings.doMigrations(json: JsonValue) {
    if (version == null) {
        migrateMultiplayerSettings(json)
    }
    if (continuousRendering != null) {
        enabledAnimations.setLevel(if (continuousRendering!!) GameSettings.AnimationLevels.All else GameSettings.AnimationLevels.None)
        continuousRendering = null
    }
    version = CURRENT_VERSION
}

fun GameSettings.isMigrationNecessary(): Boolean {
    return version != CURRENT_VERSION
}

private fun GameSettings.migrateMultiplayerSettings(json: JsonValue) {
    val userId = json.get("userId")
    if (userId != null && userId.isString) {
        multiplayer.userId = userId.asString()
    }
    val server = json.get("multiplayerServer")
    if (server != null && server.isString) {
        multiplayer.server = server.asString()
    }
    val enabled = json.get("multiplayerTurnCheckerEnabled")
    if (enabled != null && enabled.isBoolean) {
        multiplayer.turnCheckerEnabled = enabled.asBoolean()
    }
    val notification = json.get("multiplayerTurnCheckerPersistentNotificationEnabled")
    if (notification != null && notification.isBoolean) {
        multiplayer.turnCheckerPersistentNotificationEnabled = notification.asBoolean()
    }
    val delayInMinutes = json.get("multiplayerTurnCheckerDelayInMinutes")
    if (delayInMinutes != null && delayInMinutes.isNumber) {
        multiplayer.turnCheckerDelay = Duration.ofMinutes(delayInMinutes.asLong())
    }
}

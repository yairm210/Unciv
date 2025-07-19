package com.unciv.models.metadata

import com.badlogic.gdx.utils.JsonValue
import java.time.Duration

private const val CURRENT_VERSION = 2

fun GameSettings.doMigrations(json: JsonValue) {
    if (version == null) {
        migrateMultiplayerSettings(json)
        version = 1
    }
}

fun GameSettings.isMigrationNecessary(): Boolean {
    return version != CURRENT_VERSION
}

private fun GameSettings.migrateMultiplayerSettings(json: JsonValue) {
    val userId = json.get("userId")
    if (userId != null && userId.isString) {
        multiplayer.setUserId(userId.asString())
    }
    val server = json.get("multiplayerServer")
    if (server != null && server.isString) {
        multiplayer.setServer(server.asString())
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

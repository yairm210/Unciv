package com.unciv.models.metadata

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.migrateMultiplayerParameters
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import java.time.Duration

private val migrations = listOf<GameSettings.(JsonValue) -> Unit>(
    { data ->
        if (data["multiplayer"] == null) {
            data.addChild("multiplayer", JsonValue(JsonValue.ValueType.`object`))
        }
        val mpData = data["multiplayer"]
        val userId = data["userId"]
        if (userId != null && userId.isString) {
            multiplayer.userId = userId.asString()
            mpData.addChild(multiplayer::userId.name, JsonValue(userId.asString()))
        }
        val server = data["multiplayerServer"]
        if (server != null && server.isString) {
            migrateMultiplayerServerString(mpData, server.asString())
        }
        val enabled = data["multiplayerTurnCheckerEnabled"]
        if (enabled != null && enabled.isBoolean) {
            multiplayer.turnCheckerEnabled = enabled.asBoolean()
            mpData.addChild(multiplayer::turnCheckerEnabled.name, JsonValue(enabled.asBoolean()))
        }
        val notification = data["multiplayerTurnCheckerPersistentNotificationEnabled"]
        if (notification != null && notification.isBoolean) {
            multiplayer.turnCheckerPersistentNotificationEnabled = notification.asBoolean()
            mpData.addChild(multiplayer::turnCheckerPersistentNotificationEnabled.name, JsonValue(notification.asBoolean()))
        }
        val delayInMinutes = data["multiplayerTurnCheckerDelayInMinutes"]
        if (delayInMinutes != null && delayInMinutes.isNumber) {
            val duration = Duration.ofMinutes(delayInMinutes.asLong())
            multiplayer.turnCheckerDelay = duration
            mpData.addChild(multiplayer::turnCheckerDelay.name, JsonValue(duration.toString()))
        }
    },
    {data ->
        val mpData = data["multiplayer"]
        val serverUrl = if (mpData != null && mpData.isObject && mpData["server"] != null && mpData["server"].isString) {
            val serverUrl = mpData["server"].asString()
            migrateMultiplayerServerString(mpData, serverUrl)
            serverUrl
        } else {
            null
        }

        val defaultServerData = ServerData(serverUrl)
        lastGameSetup?.gameParameters?.migrateMultiplayerParameters(defaultServerData)
        val mpParams = lastGameSetup?.gameParameters?.multiplayer
        if (mpParams != null) {
            val lastGameSetupJson = data["lastGameSetup"]
            val gameParametersJson = lastGameSetupJson["gameParameters"]
            gameParametersJson.addChild("multiplayer", JsonReader().parse(json().toJson(mpParams)))
        }
    }
)

private fun GameSettings.migrateMultiplayerServerString(mpData: JsonValue, serverStr: String) {
    multiplayer.defaultServerData = ServerData(if (serverStr == "Dropbox") null else serverStr)
    mpData.addChild(multiplayer::defaultServerData.name, JsonValue(multiplayer.defaultServerData.url))
}

fun GameSettings.doMigrations(json: JsonValue) {
    for ((idx, migration) in migrations.withIndex()) {
        if (version == idx || (version == null && idx == 0)) {
            migration(json)
            version = idx + 1
        }
    }
}

fun GameSettings.isMigrationNecessary(): Boolean {
    return version != migrations.count()
}


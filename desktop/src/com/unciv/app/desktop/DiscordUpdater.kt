package com.unciv.app.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.sun.jna.Native
import com.unciv.utils.debug
import java.util.Timer
import kotlin.concurrent.timer

class DiscordGameInfo(
    var gameNation: String = "",
    var gameLeader: String = "",
    var gameTurn: Int = 0
)

class DiscordUpdater {

    private var onUpdate: (() -> DiscordGameInfo?)? = null
    private var updateTimer: Timer? = null

    fun setOnUpdate(callback: () -> DiscordGameInfo?) {
        onUpdate = callback
    }

    fun startUpdates() {
        try {
            /*
             We try to load the Discord library manually before the instance initializes.
             This is because if there's a crash when the instance initializes on a similar line,
              it's not within the bounds of the try/catch and thus the app will crash.
             */
            Native.load("discord-rpc", DiscordRPC::class.java)
            val handlers = DiscordEventHandlers()
            DiscordRPC.INSTANCE.Discord_Initialize("647066573147996161", handlers, true, null)

            Runtime.getRuntime().addShutdownHook(Thread { DiscordRPC.INSTANCE.Discord_Shutdown() })

            updateTimer = timer(name = "Discord", daemon = true, period = 1000) {
                try {
                    updateRpc()
                } catch (ex: Exception) {
                    debug("Exception while updating Discord Rich Presence", ex)
                }
            }
        } catch (_: Throwable) {
            // This needs to be a Throwable because if we can't find the discord_rpc library, we'll get a UnsatisfiedLinkError, which is NOT an exception.
            debug("Could not initialize Discord")
        }
    }

    fun stopUpdates() {
        updateTimer?.cancel()
    }

    private fun updateRpc() {

        if (onUpdate == null)
            return

        val info = onUpdate!!.invoke() ?: return

        val presence = DiscordRichPresence()
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage

        if (info.gameLeader.isNotEmpty() && info.gameNation.isNotEmpty()) {
            presence.details = "${info.gameLeader} of ${info.gameNation}"
            presence.details = "Turn ${info.gameTurn}"
        }

        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence)
    }



}

package com.unciv.app.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.badlogic.gdx.Files
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import com.unciv.UncivGame
import com.unciv.models.gamebasics.tr
import java.io.File
import kotlin.concurrent.thread

internal object DesktopLauncher {
    @JvmStatic
    fun main(arg: Array<String>) {
        if (File("../Images").exists()) // So we don't run this from within a fat JAR
            packImages()

        val config = LwjglApplicationConfiguration()
        config.useGL30 = true
        config.addIcon("ExtraImages/Icon.png", Files.FileType.Internal)
        config.title = "Unciv"
        config.useHDPI = true

        val game = UncivGame("Desktop")

        tryActivateDiscord(game)

        LwjglApplication(game, config)
    }

    private fun packImages() {
        val settings = TexturePacker.Settings()
        // Apparently some chipsets, like NVIDIA Tegra 3 graphics chipset (used in Asus TF700T tablet),
        // don't support non-power-of-two texture sizes - kudos @yuroller!
        // https://github.com/yairm210/UnCiv/issues/1340
        settings.maxWidth = 2048
        settings.maxHeight = 2048
        settings.combineSubdirectories = true
        settings.pot = true
        settings.fast = true

        // This is so they don't look all pixelated
        settings.filterMag = Texture.TextureFilter.MipMapLinearLinear
        settings.filterMin = Texture.TextureFilter.MipMapLinearLinear
        TexturePacker.process(settings, "../Images", ".", "game")
    }

    private fun tryActivateDiscord(game: UncivGame) {
        try {
            val handlers = DiscordEventHandlers()
            DiscordRPC.INSTANCE.Discord_Initialize("647066573147996161", handlers, true, null)

            Runtime.getRuntime().addShutdownHook(Thread { DiscordRPC.INSTANCE.Discord_Shutdown() })

            thread {
                while (true) {
                    updateRpc(game)
                    Thread.sleep(1000)
                }
            }
        } catch (ex: Exception) {
            print("Could not initialize Discord")
        }
    }

    fun updateRpc(game: UncivGame) {
        if(!game.isInitialized) return
        val presence = DiscordRichPresence()
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()
        presence.details=currentPlayerCiv.getTranslatedNation().getLeaderDisplayName().tr()
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage
        presence.largeImageText ="Turn".tr()+" " + currentPlayerCiv.gameInfo.turns
        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
    }
}

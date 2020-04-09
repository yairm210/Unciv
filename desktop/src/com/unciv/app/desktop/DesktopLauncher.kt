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
import com.unciv.models.translations.tr
import java.io.File
import java.util.*
import kotlin.concurrent.timer
import kotlin.system.exitProcess


internal object DesktopLauncher {
    private var discordTimer: Timer? = null

    @JvmStatic
    fun main(arg: Array<String>) {

        packImages()

        val config = LwjglApplicationConfiguration()
        // Don't activate GL 3.0 because it causes problems for MacOS computers
        config.addIcon("ExtraImages/Icon.png", Files.FileType.Internal)
        config.title = "Unciv"
        config.useHDPI = true

        val versionFromJar = DesktopLauncher.javaClass.`package`.specificationVersion ?: "Desktop"

        val game = UncivGame ( versionFromJar, null, { exitProcess(0) }, { discordTimer?.cancel() } )

        if(!RaspberryPiDetector.isRaspberryPi()) // No discord RPC for Raspberry Pi, see https://github.com/yairm210/Unciv/issues/1624
            tryActivateDiscord(game)

        LwjglApplication(game, config)
    }

    private fun packImages() {
        val startTime = System.currentTimeMillis()

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

        if (File("../Images").exists()) // So we don't run this from within a fat JAR
            packImagesIfOutdated(settings, "../Images", ".", "game")

        // pack for mods as well
        val modDirectory = File("mods")
        if(modDirectory.exists()) {
            for (mod in modDirectory.listFiles()!!){
                if (!mod.isHidden && File(mod.path + "/Images").exists())
                    packImagesIfOutdated(settings, mod.path + "/Images", mod.path, "game")
            }
        }

        val texturePackingTime = System.currentTimeMillis() - startTime
        println("Packing textures - "+texturePackingTime+"ms")
    }

    private fun packImagesIfOutdated (settings: TexturePacker.Settings, input: String, output: String, packFileName: String) {
        fun File.listTree(): Sequence<File> = when {
                this.isFile -> sequenceOf(this)
                this.isDirectory -> this.listFiles().asSequence().flatMap { it.listTree() }
                else -> sequenceOf()
            }

        val atlasFile = File(output + File.separator + packFileName + ".atlas")
        if (atlasFile.exists()) {
            val atlasModTime = atlasFile.lastModified()
            if (!File(input).listTree().any { it.extension in listOf("png","jpg","jpeg") && it.lastModified() > atlasModTime }) return
        }
        TexturePacker.process(settings, input, output, packFileName )
    }

    private fun tryActivateDiscord(game: UncivGame) {
        try {
            val handlers = DiscordEventHandlers()
            DiscordRPC.INSTANCE.Discord_Initialize("647066573147996161", handlers, true, null)

            Runtime.getRuntime().addShutdownHook(Thread { DiscordRPC.INSTANCE.Discord_Shutdown() })

            discordTimer = timer(name = "Discord", daemon = true, period = 1000) {
                try {
                    updateRpc(game)
                } catch (ex:Exception){}
            }
        } catch (ex: Exception) {
            println("Could not initialize Discord")
        }
    }

    private fun updateRpc(game: UncivGame) {
        if(!game.isInitialized) return
        val presence = DiscordRichPresence()
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()
        presence.details=currentPlayerCiv.nation.getLeaderDisplayName().tr()
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage
        presence.largeImageText ="Turn".tr()+" " + currentPlayerCiv.gameInfo.turns
        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence)
    }
}

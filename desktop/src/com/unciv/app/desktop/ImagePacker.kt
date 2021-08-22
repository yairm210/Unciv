package com.unciv.app.desktop

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import java.io.File

/**
 * Re-packs our texture assets into atlas + png File pairs, which will be loaded by the game.
 * With the exception of the ExtraImages folder and the Font system these are the only
 * graphics used (The source Image folders are unused at run time except here).
 * 
 * [TexturePacker] documentation is [here](https://github.com/libgdx/libgdx/wiki/Texture-packer)
 */
internal object ImagePacker {

    fun packImages() {
        val startTime = System.currentTimeMillis()

        val settings = TexturePacker.Settings()
        // Apparently some chipsets, like NVIDIA Tegra 3 graphics chipset (used in Asus TF700T tablet),
        // don't support non-power-of-two texture sizes - kudos @yuroller!
        // https://github.com/yairm210/UnCiv/issues/1340

        /**
         * These should be as big as possible in order to accommodate ALL the images together in one big file.
         * Why? Because the rendering function of the main screen renders all the images consecutively, and every time it needs to switch between textures,
         * this causes a delay, leading to horrible lag if there are enough switches.
         * The cost of this specific solution is that the entire game.png needs be be kept in-memory constantly.
         * Now here we come to what Fred Colon would call an Imp Arse.
         * On the one hand, certain tilesets (ahem 5hex ahem) are really big.
         * You wouldn't believe how hugely mindbogglingly big they are. So theoretically we should want all of their images to be together.
         * HOWEVER certain chipsets (see https://github.com/yairm210/Unciv/issues/3330) only seem to support to up to 2048 width*height so this is maximum we can have.
         * Practically this means that big custom tilesets will have to reload the texture a lot when covering the map and so the
         *    panning on the map will tend to lag a lot :(
         *
         *    TL;DR this should be 2048.
         */
        settings.maxWidth = 2048
        settings.maxHeight = 2048

        // Trying to disable the subdirectory combine lead to even worse results. Don't.
        settings.combineSubdirectories = true
        settings.pot = true  // powers of two only for width/height
        settings.fast = true  // with pot on this just resorts by width
        // settings.rotation - do not set. Allows rotation, potentially packing tighter.
        //      Proper rendering is mostly automatic - except borders which overwrite rotation.

        // Set some additional padding and enable duplicatePadding to prevent image edges from bleeding into each other due to mipmapping
        settings.paddingX = 8
        settings.paddingY = 8
        settings.duplicatePadding = true
        settings.filterMin = Texture.TextureFilter.MipMapLinearLinear
        settings.filterMag = Texture.TextureFilter.MipMapLinearLinear // I'm pretty sure this doesn't make sense for magnification, but setting it to Linear gives strange results

        if (File("../Images").exists()) { // So we don't run this from within a fat JAR
            for ((file, packFileName) in imageFolders()) {
                packImagesIfOutdated(settings, file, ".", packFileName)
            }
        }

        if (File("../Skin").exists()) {
            settings.filterMag = Texture.TextureFilter.Linear
            settings.filterMin = Texture.TextureFilter.Linear
            packImagesIfOutdated(settings, "../Skin", ".", "Skin")
        }

        // pack for mods as well
        val modDirectory = File("mods")
        if (modDirectory.exists()) {
            for (mod in modDirectory.listFiles()!!) {
                if (!mod.isHidden && File(mod.path + "/Images").exists())
                    packImagesIfOutdated(settings, mod.path + "/Images", mod.path, "game")
            }
        }

        val texturePackingTime = System.currentTimeMillis() - startTime
        println("Packing textures - " + texturePackingTime + "ms")
    }

    private fun packImagesIfOutdated(settings: TexturePacker.Settings, input: String, output: String, packFileName: String) {
        fun File.listTree(): Sequence<File> = when {
            this.isFile -> sequenceOf(this)
            this.isDirectory -> this.listFiles()!!.asSequence().flatMap { it.listTree() }
            else -> sequenceOf()
        }

        val atlasFile = File("$output${File.separator}$packFileName.atlas")
        if (atlasFile.exists() && File("$output${File.separator}$packFileName.png").exists()) {
            val atlasModTime = atlasFile.lastModified()
            if (File(input).listTree().none { it.extension in listOf("png", "jpg", "jpeg") && it.lastModified() > atlasModTime }) return
        }

        TexturePacker.process(settings, input, output, packFileName)
    }

    private data class ImageFolderResult(val folder: String, val atlasName: String)
    private fun imageFolders() = sequence {
        val parent = File("..")
        for (folder in parent.listFiles()!!) {
            if (!folder.isDirectory) continue
            if (folder.nameWithoutExtension != "Images") continue
            val atlasName = if (folder.name == "Images") "game" else folder.extension
            yield(ImageFolderResult("..${File.separator}${folder.name}", atlasName))
        }
    }
}

package com.unciv.ui.components.fonts

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/** Finds font files shipped by mods, in `<mod>/fonts/` */
object ModFonts {
    // What both java.awt.Font.createFont and android.graphics.Typeface.createFromFile support:
    private val supportedExtensions = setOf("ttf", "otf")

    // Internal and Classpath handles may live inside a jar or apk and can't be listed via java.nio
    private val fileSystemTypes = setOf(Files.FileType.Local, Files.FileType.Absolute, Files.FileType.External)

    /**
     *  Lists the fonts of all mods in [modsDir] (expected to be [UncivFiles.getModsFolder][com.unciv.logic.files.UncivFiles.getModsFolder]).
     *  Requires Android API 26 (java.nio.file).
     */
    @Suppress("NewApi")
    fun scan(modsDir: FileHandle): Flow<FontFamilyData> = flow {
        if (modsDir.type() !in fileSystemTypes) return@flow
        val modsPath = modsDir.file().toPath()
        if (!modsPath.isDirectory()) return@flow
        java.nio.file.Files.list(modsPath).use { stream ->
            for (mod in stream)
                emitAll(scanMod(modsDir.name(), mod))
        }
    }

    @Suppress("NewApi")
    private fun scanMod(modsDirName: String, mod: Path) = flow {
        if (!mod.isDirectory()) return@flow
        val fontsPath = mod.resolve("fonts")
        if (!fontsPath.exists() || !fontsPath.isDirectory()) return@flow
        java.nio.file.Files.list(fontsPath).use { stream ->
            for (file in stream) {
                if (file.extension.lowercase() !in supportedExtensions) continue
                emit(FontFamilyData(
                    "${file.nameWithoutExtension} (${mod.name})",
                    file.nameWithoutExtension,
                    // Relative to the data folder, as the font loaders resolve it with UncivFiles.getLocalFile
                    "$modsDirName/${mod.name}/fonts/${file.name}"
                ))
            }
        }
    }
}

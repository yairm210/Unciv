package com.unciv.ui.images

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.unciv.json.json
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.utils.Log
import java.io.File

/**
 *  This extracts all texture names from all atlases of a Ruleset.
 *  - Weak point: For combined rulesets, this always loads the builtin assets.
 *  - Used by RulesetValidator to check texture names without relying on ImageGetter
 *  - Doubles as integrity checker and detects:
 *      - Atlases.json names an atlas that does not exist
 *      - Existing atlas is empty
 *      - If Atlases.json names an atlas that does not exist, but the corresponding Images folder exists...
 *         * Non-png files in the Images folders
 *         * Any png files in the Images folders that Gdx can't load
 */
class AtlasPreview(ruleset: Ruleset, errorList: RulesetErrorList) : Iterable<String> {
    // This partially duplicates code in ImageGetter.getAvailableTilesets, but we don't want to reload that singleton cache.

    private val regionNames = mutableSetOf<String>()

    init {
        // For builtin rulesets, the Atlases.json is right in internal root
        val folder = ruleset.folder()
        val controlFile = folder.child("Atlases.json")
        val controlFileExists = controlFile.exists()


        val fileNames = getFileNames(controlFileExists, controlFile, errorList).toMutableSet()

        val backwardsCompatibility = ruleset.name.isNotEmpty() && "game" !in fileNames
        if (backwardsCompatibility)
            fileNames += "game"  // Backwards compatibility - when packed by 4.9.15+ this is already in the control file
        for (fileName in fileNames) {
            val file = folder.child("$fileName.atlas")
            if (!file.exists()) {
                if (controlFileExists && (fileName != "game" || !backwardsCompatibility))
                    logMissingAtlas(fileName, ruleset, errorList)
                continue
            }

            // Next, we need to cope with this running without GL context (unit test) - no TextureAtlas(file)
            val data = TextureAtlasData(file, file.parent(), false)
            if (data.regions.isEmpty)
                errorList.add("${file.name()} contains no textures")
            data.regions.mapTo(regionNames) { it.name }
        }
        Log.debug("Atlas preview for $ruleset: ${regionNames.size} entries.")
    }

    private fun getFileNames(
        controlFileExists: Boolean,
        controlFile: FileHandle?,
        errorList: RulesetErrorList
    ): Array<String> {
        if (!controlFileExists) return emptyArray()
        
        // Type checker doesn't know that fromJson can return null if the file is empty, treat as an empty array
        val fileNames = json().fromJson(Array<String>::class.java, controlFile) ?: emptyArray()
        if (fileNames.isEmpty()) errorList.add("Atlases.json is empty", RulesetErrorSeverity.Warning)
        return fileNames
    }

    fun imageExists(name: String) = name in regionNames

    override fun iterator(): Iterator<String> = regionNames.iterator()

    private fun logMissingAtlas(name: String, ruleset: Ruleset, errorList: RulesetErrorList) {
        errorList.add("Atlases.json contains \"$name\" but there is no corresponding atlas file.")
        val imagesFolder = ruleset.folder().child(if (name == "game") "Images" else "Images.$name")
        if (!imagesFolder.exists() || !imagesFolder.isDirectory) return

        // switch over from Gdx file handling to kotlin.io.FileTreeWalk because it's easy
        for (file in imagesFolder.file().walk()) {
            if (file.isDirectory || file.isHidden) continue
            if (file.extension != "png") {
                errorList.add("${imagesFolder.name()} contains ${file.relativePath(ruleset)} which does not have the png extension", RulesetErrorSeverity.WarningOptionsOnly)
                continue
            }
            try {
                // Since we have walked for java.io.File instances, getting the bytes manually is easier than converting that back to a Gdx FileHandle
                val bytes = file.readBytes()
                // This tests the bits whether they can be understood as png
                // (or jpeg or bmp - but since we already checked the extension, it's quite unlikely this succeeds but TexturePacker.process still chokes on it)
                val pixmap = Pixmap(bytes, 0, bytes.size)
                pixmap.dispose()
            } catch (ex: Throwable) {
                var innerException = ex
                while (innerException.cause != null && innerException.cause !== innerException) innerException = innerException.cause!!
                errorList.add("Cannot load ${file.relativePath(ruleset)}: ${innerException.message}")
            }
        }
    }

    private fun Ruleset.folder() = folderLocation ?: Gdx.files.internal("")

    private fun File.relativePath(ruleset: Ruleset) =
        path.removePrefix(ruleset.folder().file().path).removePrefix("/")
}

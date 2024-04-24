package com.unciv.ui.images

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.unciv.json.json
import com.unciv.models.ruleset.Ruleset
import com.unciv.utils.Log

class AtlasPreview(ruleset: Ruleset) : Iterable<String> {
    // This partially duplicates code in ImageGetter.getAvailableTilesets, but we don't want to reload that singleton cache.

    private val regionNames = mutableSetOf<String>()

    init {
        // For builtin rulesets, the Atlases.json is right in internal root
        val folder = ruleset.folderLocation ?: Gdx.files.internal(".")
        val controlFile = folder.child("Atlases.json")
        val fileNames = (if (controlFile.exists()) json().fromJson(Array<String>::class.java, controlFile)
            else emptyArray()).toMutableSet()
        if (ruleset.name.isNotEmpty())
            fileNames += "game"  // Backwards compatibility - when packed by 4.9.15+ this is already in the control file
        for (fileName in fileNames) {
            val file = folder.child("$fileName.atlas")
            if (!file.exists()) continue

            // Next, we need to cope with this running without GL context (unit test) - no TextureAtlas(file)
            val data = TextureAtlasData(file, file.parent(), false)
            data.regions.mapTo(regionNames) { it.name }
        }
        Log.debug("Atlas preview for $ruleset: ${regionNames.size} entries.")
    }

    fun imageExists(name: String) = name in regionNames

    override fun iterator(): Iterator<String> = regionNames.iterator()
}

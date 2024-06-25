package com.unciv.ui.screens.mapeditorscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.FileChooser
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job

// Wesnoth maps come with a non-playable border of one Hex.
// Wesnoth puts the odd corner with only two neighbors on top, while Unciv puts it on the bottom.
// This means that Wesnoth's coord mapping is a little different, so we need to shift every other column vertically one place.
// To do so, we use half the unplayable hexes Wesnoth's map has on top and alternatingly those on the bottom.
// This means a map loaded in Unciv has its height increased by 1 compared to what Wesnoth showed (they don't include the unplayable border in dimensions).

//todo Allow different rulesets?

class MapEditorWesnothImporter(private val editorScreen: MapEditorScreen) : DisposableHandle {
    companion object {
        var lastFileFolder: FileHandle? = null
    }

    private val ruleset by lazy { RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!! }

    private var importJob: Job? = null

    private val ignoreLines = setOf("usage=map", "border_size=1")

    private val parseTerrain by lazy {
        Regex(
            """^
                (((?<start>\d+)|[A-Za-z\d_]+)\ )*         # Space-separated prefixes allowed, if numeric it's a starting location, others are scenario-specific (we ignore)
                (?<base>[A-Z_][a-z\\|/]{1,3})             # mandatory layer
                (\^(?<layer>[A-Z_][a-z\\|/]{1,3}))?       # optional layer separated by `^`
            $""".trimIndent(), RegexOption.COMMENTS)
    }

    private val translationCodes: LinkedHashMap<String,ArrayList<String>> by lazy {
        json().fromJson(
            linkedMapOf<String,ArrayList<String>>()::class.java,
            arrayListOf<String>()::class.java,  // else we get Gdx.Array despite the class above stating ArrayList
            Gdx.files.local("jsons/WesnothImportMappings.json")
        )
    }

    // The json must define a fallback that includes a BaseTerrain to ensure we can always find a BaseTerrain.
    // (using key "" and letting translateTerrainWML loop downto 0 is close, but inserts the fallback twice, and we want it only at the end)
    private val fallback: List<String> by lazy { translationCodes["fallback"] ?: listOf(Constants.grassland) }

    override fun dispose() {
        importJob?.cancel()
    }

    fun onImportButtonClicked() {
        editorScreen.askIfDirtyForLoad(::openFileDialog)
    }
    private fun openFileDialog() {
        FileChooser.createLoadDialog(editorScreen.stage, "Choose a Wesnoth map file", lastFileFolder) { success: Boolean, file: FileHandle ->
            if (!success) return@createLoadDialog
            startImport(file)
            lastFileFolder = file.parent()
        }.apply {
            filter = FileChooser.createExtensionFilter("map")
        }.open()
    }

    private fun startImport(file: FileHandle) {
        dispose()
        importJob = Concurrency.run("Map import") {
            try {
                val mapData = file.readString(Charsets.UTF_8.name()) // Actually, it's pure ascii, but force of habit...
                val map = mapData.parse()
                Concurrency.runOnGLThread {
                    editorScreen.loadMap(map)
                }
            } catch (ex: UncivShowableException) {
                Log.error("Could not load map", ex)
                Concurrency.runOnGLThread {
                    ToastPopup(ex.message, editorScreen, 4000L)
                }
            } catch (ex: Throwable) {
                Log.error("Could not load map", ex)
                Concurrency.runOnGLThread {
                    ToastPopup("Could not load map!", editorScreen)
                }
            }
        }
    }

    /** Parses receiver string for a complete Wesnoth map */
    private fun String.parse(): TileMap {
        // first we need to know the size.
        // There can be a header, optional, and not used in any campaign map or saved by the editor, skip anyway.
        // isNotBlank also ensures the line break after the last row won't count as line.
        // Wesnoth maps have a non-playable border - exclude.
        val lines = lineSequence().filter { it.isNotBlank() && it !in ignoreLines }.toList()
        val height = lines.size - 1
        val width = if (height <= 0) 0 else lines[0].split(',').size - 2
        if (width <= 0) throw UncivShowableException("That map is invalid!")

        val map = TileMap(width, height, ruleset, false)
        map.mapParameters.apply {
            type = MapType.empty
            shape = MapShape.rectangular
            mapSize = MapSize(width, height)
        }

        val colOffset = 1 + width / 2
        val rowOffset = height / 2
        for ((row, line) in lines.withIndex()) {
            for ((column, cellCode) in line.split(',').withIndex()) {
                val effectiveRow = rowOffset - row + column % 2 // see comment block at the top of the file
                val pos = HexMath.getTileCoordsFromColumnRow(column - colOffset, effectiveRow)
                if (!map.contains(pos)) continue
                map[pos].paintFromWesnothCode(cellCode.trim(), map)
            }
        }

        return map
    }

    /** Modify receiver Tile so it matches a Wesnoth cell code (e.g. "Wo", "Gs^Fp", "1 Ke" or "sceptre Uu^Ii") */
    private fun Tile.paintFromWesnothCode(cellCode: String, map: TileMap) {
        // See https://wiki.wesnoth.org/TerrainCodesWML
        // First do a pattern match that validates and splits off player start indices, base and layer codes, and ignores other scenario prefixes
        val matches = parseTerrain.matchEntire(cellCode)
            ?: throw UncivShowableException("{That map is invalid!}\n{(\"[$cellCode]\" does not conform to TerrainCodesWML)}")
        val start = matches.groups["start"]?.value
        val base = matches.groups["base"]!!.value  // This capture is not optional in the pattern
        val layer = matches.groups["layer"]?.value
        // Now build a loose collection of candidate elements
        val allStrings: Sequence<String> =
            translateTerrainWML(base) + translateTerrainWML(layer) + fallback
        // Map to RulesetObjects and ensure Hills are always under other features
        val allObjects = allStrings
            .sortedBy { it != Constants.hill }
            .mapNotNull { ruleset.tileImprovements[it] ?: ruleset.terrains[it] }
            .toList()
        // BaseTerrain is simply the first candidate, the fallback above makes first() not crash (unless the json breaks it)
        baseTerrain = allObjects.first { it is Terrain && it.type.isBaseTerrain }.name
        // Features are all valid candiates in order, enduring no duplicates
        val features = allObjects.filter { it is Terrain && it.type == TerrainType.TerrainFeature }.map { it.name }.distinct()
        if (features.isNotEmpty()) {
            setTerrainTransients() // or else can't setTerrainFeatures as baseTerrainObject may be uninitialized
            setTerrainFeatures(features)
        }
        // Optional improvement - mainly Wesnoth Villages to Ancient ruins
        allObjects.firstOrNull { it is TileImprovement }?.apply { improvement = name }
        // Start locations are separate - and for now very simple:
        // If a side number prefix is there, define an "Any" starting location. To actually match names
        // a separate Wesnoth file would have to be read - and the existing ones match no Civ nation anyway.
        // ('../scenarios/<mapname>.cfg', WMLpath('scenario/side[i-1]/user_team_name') or team_name,
        // with a Mod defining Elves, Orcs, Dwarves and so on, and decoupling Civilization from Nation that may even work)
        if (start == null) return
        map.addStartingLocation(Constants.spectator, this)
    }

    /** Get all relevant matches for one layer code from the definition map,
     *  in order from most to least specific, flattened to a Sequence.
     *  (That is, match original then shorten the string stepwise and append all hits together) */
    private fun translateTerrainWML(code: String?) = sequence {
        if (code == null) return@sequence
        for (length in code.length downTo 1) {
            translationCodes[code.slice(0 until length)]?.also { yieldAll(it) }
        }
    }
}

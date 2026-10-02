package com.unciv.logic.map

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.JsonReader
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.Ruleset
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * Reads an Open Doctrines `.odmap` into a [TileMap].
 *
 * An `.odmap` is a zip. Two members matter here:
 *
 *  - `provinces.png`, where each pixel carries a province id in its colour,
 *    `(r shl 16) or (g shl 8) or b`. Id 0 is unpainted, which that game reads
 *    as open water.
 *  - `provinces.json`, a table keyed by that id. A province may carry a
 *    `terrain` token; the shipped world map carries none, and then a climate
 *    is derived from latitude so the result is playable rather than blank.
 *
 * It is a province map, not a tile map: a few thousand irregular regions over a
 * raster thousands of pixels wide. So it is RESAMPLED -- each hex takes the
 * province under its centre -- and the hex grid is sized independently of the
 * raster.
 *
 * Deliberately pure Kotlin, and deliberately [Pixmap] rather than `ImageIO`,
 * which does not exist on Android.
 *
 * Separated from the map editor's importer so it can be tested without a
 * screen.
 */
object OdMapImport {
    /** Large enough that continents survive the resample, small enough to play. */
    const val defaultColumns = 80
    const val defaultRows = 50

    private class Province(val terrain: String?, val isSea: Boolean)

    fun read(
        file: FileHandle,
        ruleset: Ruleset,
        columns: Int = defaultColumns,
        rows: Int = defaultRows
    ): TileMap {
        val provinces = HashMap<Int, Province>()
        var ids: IntArray? = null
        var width = 0
        var height = 0

        ZipFile(file.file()).use { zip ->
            val provinceEntry = zip.getEntry("provinces.png")
                ?: throw UncivShowableException("That is not an Open Doctrines map - it has no provinces.png")
            val pngBytes = zip.getInputStream(provinceEntry).use { it.readBytes() }
            val pixmap = Pixmap(pngBytes, 0, pngBytes.size)
            try {
                width = pixmap.width
                height = pixmap.height
                val raster = IntArray(width * height)
                for (y in 0 until height) for (x in 0 until width) {
                    // getPixel answers RGBA8888 whatever the file's own format is.
                    raster[y * width + x] = (pixmap.getPixel(x, y) ushr 8) and 0xFFFFFF
                }
                ids = raster
            } finally {
                pixmap.dispose()
            }

            val tableEntry = zip.getEntry("provinces.json")
            if (tableEntry != null) {
                val text = zip.getInputStream(tableEntry).use { it.readBytes() }.toString(Charsets.UTF_8)
                var child = JsonReader().parse(text).child
                while (child != null) {
                    val id = child.getInt("id", child.name?.toIntOrNull() ?: 0)
                    if (id != 0) provinces[id] = Province(
                        child.getString("terrain", null),
                        child.getBoolean("is_sea", false)
                    )
                    child = child.next
                }
            }
        }

        val raster = ids ?: throw UncivShowableException("That map's provinces.png could not be read")
        if (width <= 0 || height <= 0)
            throw UncivShowableException("That map's provinces.png is empty")

        return build(raster, width, height, provinces, ruleset, columns, rows)
    }

    private fun build(
        raster: IntArray,
        rasterWidth: Int,
        rasterHeight: Int,
        provinces: Map<Int, Province>,
        ruleset: Ruleset,
        columns: Int,
        rows: Int
    ): TileMap {
        // worldWrap = true: an Open Doctrines map is a globe. Using this
        // constructor rather than laying coordinates out by hand is what keeps
        // the grid centred on the origin -- TileMap.setTransients accepts no
        // other layout once a tileMatrix exists, and it only checks from its
        // SECOND call, so a hand-rolled grid loads here and dies in GameStarter.
        val map = TileMap(columns, rows, ruleset, true)
        map.mapParameters.apply {
            type = MapType.empty
            shape = MapShape.rectangular
            mapSize = MapSize(columns, rows)
        }

        val colMin = -columns / 2
        val rowMin = -rows / 2
        for (ri in 0 until rows) for (ci in 0 until columns) {
            val px = ((ci + 0.5) * rasterWidth / columns).toInt().coerceIn(0, rasterWidth - 1)
            val py = ((ri + 0.5) * rasterHeight / rows).toInt().coerceIn(0, rasterHeight - 1)
            val id = raster[py * rasterWidth + px]
            val province = provinces[id]

            // Null when the province names no terrain, or names one that is
            // not among the thirteen the two sides share.
            val stated = if (province?.terrain == null) null
                         else OdTerrain.forToken(province.terrain)

            val base: String
            var feature: String? = null
            when {
                id == 0 || province?.isSea == true -> base = "Ocean"
                stated != null -> {
                    base = stated.baseTerrain
                    feature = stated.feature
                }
                else -> {
                    val latitude = 90.0 - 180.0 * (py + 0.5) / rasterHeight
                    base = climateFor(latitude)
                    if (abs(latitude) < 12.0) feature = "Jungle"
                }
            }

            val position = HexMath.getTileCoordsFromColumnRow(ci + colMin, ri + rowMin)
            if (!map.contains(position)) continue
            map[position].baseTerrain = base
            map[position].setTerrainFeatures(if (feature == null) emptyList() else listOf(feature))
        }

        map.setTransients(ruleset, true)
        return map
    }


    private fun climateFor(latitude: Double): String {
        val a = abs(latitude)
        return when {
            a >= 75.0 -> "Snow"
            a >= 60.0 -> "Tundra"
            a >= 35.0 -> "Plains"
            a >= 30.0 -> "Desert"    // the horse latitudes, roughly
            a >= 23.0 -> "Plains"
            else -> "Grassland"      // the tropics; Jungle is added as a feature
        }
    }
}

package com.unciv.logic.map

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.tile.Tile
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [TileMap] out as an Open Doctrines `.odmap`.
 *
 * The inverse of [OdMapImport], and inverse in the literal sense: the importer
 * samples the pixel at the centre of each grid cell, so the exporter paints
 * each cell as a solid rectangle, and a map that goes out and comes back is the
 * map that set out.
 *
 * What the archive holds, and why:
 *
 *  - `provinces.png` -- one province per LAND hex, its id in the pixel colour
 *    as `(r shl 16) or (g shl 8) or b`. Water hexes stay id 0, which Open
 *    Doctrines reads as open sea.
 *  - `land_sea.png` -- the same thing as a greyscale mask, land 200, sea 40.
 *    That game keeps both layers and reads them for different questions.
 *  - `provinces.json`, `countries.json`, `metadata.json` -- the province table,
 *    one country per continent, and the map's name.
 *
 * ### The `terrain` field
 *
 * Open Doctrines stores no terrain: its raster says land or sea and nothing
 * else, and its own `provinces.json` has no such key. This writer adds one
 * anyway. It is additive and that game ignores keys it does not know, so the
 * archive stays a valid `.odmap` -- and it is what lets a map exported from
 * here and imported back carry its terrain instead of having a climate guessed
 * from latitude. A map exported here and opened in Open Doctrines is unaffected
 * by the extra key.
 */
object OdMapExport {
    /** Raster pixels per hex. Enough that coastlines are not staircases. */
    const val defaultCellSize = 16

    fun write(
        map: TileMap,
        file: FileHandle,
        name: String = "Unciv map",
        cellSize: Int = defaultCellSize
    ) {
        val tiles = map.values.toList()
        require(tiles.isNotEmpty()) { "a TileMap with no tiles" }

        // The grid is walked FORWARDS, the way the importer and TileMap's own
        // constructor walk it, and never by inverting a hex coordinate back to
        // a column and row. HexMath.getColumn/getRow are not exact inverses of
        // getTileCoordsFromColumnRow: on odd columns the row comes back one
        // out, because the `twoRows += 1` shift is not undone. Inverting a
        // 20x10 map gives 50 wrong cells and only 190 distinct ones, so two
        // hexes land on one province and seven provinces are never written.
        val columns = map.mapParameters.mapSize.width
        val rows = map.mapParameters.mapSize.height
        val colMin = -columns / 2
        val rowMin = -rows / 2

        val byCell = HashMap<Long, Tile>(tiles.size * 2)
        for (ri in 0 until rows) for (ci in 0 until columns) {
            val position = HexMath.getTileCoordsFromColumnRow(ci + colMin, ri + rowMin)
            if (!map.contains(position)) continue
            byCell[cellKey(ci + colMin, ri + rowMin)] = map[position]
        }
        if (byCell.size != tiles.size)
            throw UncivShowableException(
                "That map's declared size [${columns}x${rows}] does not match its "
                    + "[${tiles.size}] tiles - [${byCell.size}] were found on the grid"
            )

        map.assignContinents(TileMap.AssignContinentsMode.Ensure)

        // One province per land hex, numbered from 1 -- id 0 means unpainted.
        val provinceOf = HashMap<Long, Int>()
        val continents = LinkedHashSet<Int>()
        var nextId = 1
        for (ri in 0 until rows) for (ci in 0 until columns) {
            val tile = byCell[cellKey(ci + colMin, ri + rowMin)] ?: continue
            if (tile.isWater) continue
            provinceOf[cellKey(ci + colMin, ri + rowMin)] = nextId++
            continents.add(tile.getContinent())
        }
        if (provinceOf.isEmpty())
            throw UncivShowableException("That map is all water - there is nothing to export")

        val countryOf = continents.withIndex().associate { (i, c) -> c to i + 1 }

        val provincePng = paint(columns, rows, cellSize) { ci, ri ->
            val id = provinceOf[cellKey(ci + colMin, ri + rowMin)] ?: 0
            (id shl 8) or 0xFF                      // RGBA8888; id in the top three bytes
        }
        val landSeaPng = paint(columns, rows, cellSize) { ci, ri ->
            val land = provinceOf.containsKey(cellKey(ci + colMin, ri + rowMin))
            val v = if (land) 200 else 40
            (v shl 24) or (v shl 16) or (v shl 8) or 0xFF
        }

        // In grid order, so the table reads the way the raster is laid out.
        val provincesJson = sequence {
            for (ri in 0 until rows) for (ci in 0 until columns) {
                val key = cellKey(ci + colMin, ri + rowMin)
                val id = provinceOf[key] ?: continue
                val tile = byCell[key]!!
                val country = countryOf[tile.getContinent()] ?: 1
                yield(
                    """"$id":{"id":$id,"color":"#%06X","country_id":$country,""".format(id)
                        + """"iso_a3":"${isoFor(country)}","name":"Province $id","""
                        + """"terrain":"${OdTerrain.forTile(tile).token}"}"""
                )
            }
        }.joinToString(",", "{", "}")

        val countriesJson = countryOf.values.sorted().joinToString(",", "{", "}") { country ->
            """"$country":{"id":$country,"color":"${countryColour(country)}",""" +
                """"iso_a3":"${isoFor(country)}","name":"Continent $country","treasury":100.0}"""
        }

        val metadata = """{"name":${quote(name)},""" +
            """"description":"Exported from Unciv","author":"Unciv","""" +
            """map_date":"January 2000 AD","license":"","has_scripts":false}"""

        ZipOutputStream(file.write(false)).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.putEntry("provinces.png", provincePng)
            zip.putEntry("land_sea.png", landSeaPng)
            zip.putEntry("provinces.json", provincesJson.toByteArray())
            zip.putEntry("countries.json", countriesJson.toByteArray())
            zip.putEntry("metadata.json", metadata.toByteArray())
        }
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    /** Grid cell as one key, so no nested maps and no allocation per lookup. */
    private fun cellKey(column: Int, row: Int) = (column.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL)

    /** Each cell painted as a solid rectangle, which the importer re-samples at its centre. */
    private inline fun paint(columns: Int, rows: Int, cellSize: Int, rgbaOf: (Int, Int) -> Int): ByteArray {
        val pixmap = Pixmap(columns * cellSize, rows * cellSize, Pixmap.Format.RGBA8888)
        try {
            for (ri in 0 until rows) for (ci in 0 until columns) {
                pixmap.setColor(rgbaOf(ci, ri))
                pixmap.fillRectangle(ci * cellSize, ri * cellSize, cellSize, cellSize)
            }
            val out = ByteArrayOutputStream()
            // PixmapIO.PNG is Disposable, not Closeable, so no `use` here.
            val png = PixmapIO.PNG(pixmap.width * pixmap.height / 8 + 64)
            try {
                png.setFlipY(false)
                png.write(out, pixmap)
            } finally {
                png.dispose()
            }
            return out.toByteArray()
        } finally {
            pixmap.dispose()
        }
    }


    /** AAA, AAB, ... - a placeholder code, since a continent has no country. */
    private fun isoFor(country: Int): String {
        val n = country - 1
        return charArrayOf(
            'A' + (n / 676) % 26, 'A' + (n / 26) % 26, 'A' + n % 26
        ).concatToString()
    }

    /**
     * Spread around the hue circle by the golden angle, so neighbours differ.
     * Written out rather than taken from java.awt.Color, which Android has not
     * got -- the same reason the importer decodes PNG through Pixmap.
     */
    private fun countryColour(country: Int): String {
        val h = ((country * 137.508) % 360.0) / 60.0
        val s = 0.55; val v = 0.75
        val i = h.toInt()
        val f = h - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return "#%02X%02X%02X".format((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    private fun quote(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

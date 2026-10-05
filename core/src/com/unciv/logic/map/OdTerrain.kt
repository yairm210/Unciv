package com.unciv.logic.map

import com.unciv.logic.map.tile.Tile

/**
 * The thirteen terrain tokens an Open Doctrines map can carry, and what each
 * one is here.
 *
 * One list, read both ways: [OdMapImport] asks [forToken] and [OdMapExport]
 * asks [forTile]. It was two `when` blocks facing each other, and they had
 * already drifted once -- the reading side knew seven of the thirteen, so a
 * Snow hex written as `frozen` came back Grassland and a quarter of the map
 * changed terrain in silence. A table that is one table cannot drift.
 *
 * The mapping is deliberately not a bijection, in two places that are easier
 * to see written down than described:
 *
 *  - `inland_sea` and `lakes` both arrive as Lakes. Lakes leaves as `lakes`,
 *    because it is declared first.
 *  - **Grassland has no token.** The model has no plain grassland, so a
 *    Grassland hex leaves as `plains` and comes home as Plains. That is the
 *    one terrain a round trip does not preserve, and
 *    `OdMapExportTests.a map survives the round trip hex for hex` asserts it
 *    as a known exception rather than letting it pass unnoticed.
 */
enum class OdTerrain(
    /** What the Open Doctrines side calls it. */
    val token: String,
    /** The Unciv base terrain. */
    val baseTerrain: String,
    /** The Unciv terrain feature the token implies, where it implies one. */
    val feature: String? = null
) {
    Ocean("ocean", "Ocean"),
    CoastalSea("coastal_sea", "Coast"),
    Lakes("lakes", "Lakes"),
    InlandSea("inland_sea", "Lakes"),
    Mountain("mountain", "Mountain"),
    Hills("hills", "Plains", "Hill"),
    Desert("desert", "Desert"),
    Plains("plains", "Plains"),
    Forest("forest", "Grassland", "Forest"),
    Jungle("jungle", "Plains", "Jungle"),
    Swamp("swamp", "Grassland", "Marsh"),
    Tundra("tundra", "Tundra"),
    Frozen("frozen", "Snow");

    companion object {
        /** `null` when the token is not one of the thirteen. */
        fun forToken(token: String): OdTerrain? {
            val wanted = token.lowercase()
            for (terrain in entries)
                if (terrain.token == wanted) return terrain
            return null
        }

        /**
         * What to write for a tile.
         *
         * A feature makes the token more specific -- Plains carrying a Hill is
         * `hills`, not `plains` -- so a match on base AND feature wins over a
         * match on base alone, and a bare Plains must not come back as hills.
         */
        fun forTile(tile: Tile): OdTerrain {
            for (terrain in entries)
                if (terrain.baseTerrain == tile.baseTerrain && terrain.feature != null
                    && tile.terrainFeatures.contains(terrain.feature)) return terrain
            for (terrain in entries)
                if (terrain.baseTerrain == tile.baseTerrain && terrain.feature == null) return terrain
            return Plains   // Grassland, which has no token of its own
        }
    }
}

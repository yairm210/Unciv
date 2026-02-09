package com.unciv.json

import com.badlogic.gdx.utils.JsonReader
import com.unciv.logic.GameInfo
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.RulesetCache
import com.unciv.platform.PlatformCapabilities
import java.util.LinkedHashSet

/**
 * TeaVM occasionally misses reflective writes for deeply nested save/map payloads.
 * For web only, recover TileMap core payload directly from raw JSON when tileList is empty.
 */
object WebJsonFallback {
    private fun inferBaseRulesetNameFromCivilizations(gameInfo: GameInfo): String? {
        val civNames = gameInfo.civilizations
            .flatMap { listOf(it.civName, it.civID) }
            .filter { it.isNotBlank() && it != Constants.barbarians && it != Constants.spectator }
            .toSet()
        if (civNames.isEmpty()) return null

        val baseRulesets = RulesetCache.values.filter { it.modOptions.isBaseRuleset }
        val candidateRulesets = if (baseRulesets.isNotEmpty()) baseRulesets else RulesetCache.values

        val scored = candidateRulesets
            .map { ruleset -> ruleset to civNames.count { it in ruleset.nations } }
            .maxByOrNull { it.second }
            ?: return null
        if (scored.second == 0) {
            val fallback = RulesetCache.values
                .map { ruleset -> ruleset to civNames.count { it in ruleset.nations } }
                .maxByOrNull { it.second }
                ?: return null
            if (fallback.second == 0) return null
            return fallback.first.name
        }
        return scored.first.name
    }

    fun ensureBaseRulesetForCivilizations(gameInfo: GameInfo) {
        if (PlatformCapabilities.current.backgroundThreadPools) return
        val currentBase = gameInfo.gameParameters.baseRuleset
        val currentRuleset = RulesetCache[currentBase]
        val civKeys = gameInfo.civilizations.asSequence()
            .flatMap { sequenceOf(it.civName, it.civID) }
            .filter { it.isNotBlank() && it != Constants.barbarians && it != Constants.spectator }
            .toSet()
        if (civKeys.isEmpty()) return

        val missingAnyCiv = civKeys.any { civKey -> currentRuleset == null || civKey !in currentRuleset.nations }

        if (currentBase.isBlank() || currentRuleset == null || missingAnyCiv) {
            val baseRulesets = RulesetCache.values.filter { it.modOptions.isBaseRuleset }
            val candidates = if (baseRulesets.isNotEmpty()) baseRulesets else RulesetCache.values
            val fullMatch = candidates.firstOrNull { ruleset -> civKeys.all { it in ruleset.nations } }
            if (fullMatch != null) {
                gameInfo.gameParameters.baseRuleset = fullMatch.name
                return
            }
            val inferredBase = inferBaseRulesetNameFromCivilizations(gameInfo)
            if (!inferredBase.isNullOrBlank()) {
                gameInfo.gameParameters.baseRuleset = inferredBase
            }
        }
    }

    fun hydrateGameParameters(gameInfo: GameInfo, rawJson: String) {
        if (PlatformCapabilities.current.backgroundThreadPools) return

        val root = runCatching { JsonReader().parse(rawJson) }.getOrNull() ?: return
        val rawParameters = root.get("gameParameters") ?: return
        val gameParameters = gameInfo.gameParameters

        val baseRuleset = rawParameters.getString("baseRuleset", "")
        if (baseRuleset.isNotBlank()) gameParameters.baseRuleset = baseRuleset

        val hydratedMods = LinkedHashSet<String>()
        var rawMod = rawParameters.get("mods")?.child
        while (rawMod != null) {
            val mod = rawMod.asString()
            if (mod.isNotBlank()) hydratedMods += mod
            rawMod = rawMod.next
        }
        if (hydratedMods.isNotEmpty()) {
            gameParameters.mods.clear()
            gameParameters.mods.addAll(hydratedMods)
        }

        ensureBaseRulesetForCivilizations(gameInfo)
    }

    fun hydrateGameInfoIfMissingCivilizations(gameInfo: GameInfo, rawJson: String) {
        if (PlatformCapabilities.current.backgroundThreadPools) return
        if (gameInfo.civilizations.isNotEmpty()) {
            ensureBaseRulesetForCivilizations(gameInfo)
            return
        }

        val root = runCatching { JsonReader().parse(rawJson) }.getOrNull() ?: return
        val civilizationsNode = root.get("civilizations") ?: return

        val hydratedCivs = ArrayList<Civilization>()
        var civNode = civilizationsNode.child
        while (civNode != null) {
            val civName = civNode.getString("civName", "").ifBlank { civNode.getString("civID", "") }
            if (civName.isNotBlank()) {
                val civID = civNode.getString("civID", civName)
                val civ = Civilization(civName, civID)
                val playerTypeName = civNode.getString("playerType", PlayerType.AI.name)
                civ.playerType = runCatching { PlayerType.valueOf(playerTypeName) }.getOrDefault(PlayerType.AI)
                hydratedCivs += civ
            }
            civNode = civNode.next
        }

        if (hydratedCivs.isNotEmpty()) {
            gameInfo.civilizations.clear()
            gameInfo.civilizations.addAll(hydratedCivs)
        }

        if (gameInfo.currentPlayer.isBlank()) {
            gameInfo.currentPlayer = root.getString("currentPlayer", gameInfo.currentPlayer)
        }

        ensureBaseRulesetForCivilizations(gameInfo)
    }

    fun hydrateTileMapIfMissingTiles(tileMap: TileMap, rawJson: String) {
        if (PlatformCapabilities.current.backgroundThreadPools) return
        if (tileMap.tileList.isNotEmpty()) return

        val root = runCatching { JsonReader().parse(rawJson) }.getOrNull() ?: return
        val rawTileMap = root.get("tileMap") ?: root

        val parser = json()
        rawTileMap.get("mapParameters")?.let { mapParametersNode ->
            runCatching {
                tileMap.mapParameters = parser.readValue(MapParameters::class.java, mapParametersNode)
            }
        }
        tileMap.description = rawTileMap.getString("description", tileMap.description)

        val hydratedTiles = ArrayList<Tile>()
        val tileListNode = rawTileMap.get("tileList") ?: rawTileMap.get("tiles")
        var rawTile = tileListNode?.child
        while (rawTile != null) {
            val tile = Tile()
            runCatching { parser.readFields(tile, rawTile) }

            val positionNode = rawTile.get("position")
            val x = positionNode?.getInt("x", 0) ?: 0
            val y = positionNode?.getInt("y", 0) ?: 0
            tile.position = HexCoord(x, y)
            tile.baseTerrain = rawTile.getString("baseTerrain", tile.baseTerrain.ifBlank { Constants.grassland })
            if (tile.terrainFeatures.isEmpty() && rawTile.get("terrainFeatures") != null) {
                val features = ArrayList<String>()
                var featureNode = rawTile.get("terrainFeatures")?.child
                while (featureNode != null) {
                    val feature = featureNode.asString()
                    if (feature.isNotBlank()) features += feature
                    featureNode = featureNode.next
                }
                tile.setTerrainFeaturesSerialized(features)
            }
            tile.hasBottomRiver = rawTile.getBoolean("hasBottomRiver", tile.hasBottomRiver)
            tile.hasBottomLeftRiver = rawTile.getBoolean("hasBottomLeftRiver", tile.hasBottomLeftRiver)
            tile.hasBottomRightRiver = rawTile.getBoolean("hasBottomRightRiver", tile.hasBottomRightRiver)
            tile.improvement = rawTile.getString("improvement", tile.improvement)
            tile.improvementIsPillaged = rawTile.getBoolean("improvementIsPillaged", tile.improvementIsPillaged)
            val resourceName = rawTile.getString("resource", "")
            tile.setTileResource(resourceName.ifBlank { null }, updateCache = false)
            tile.resourceAmount = rawTile.getInt("resourceAmount", tile.resourceAmount)
            tile.naturalWonder = rawTile.getString("naturalWonder", tile.naturalWonder)

            hydratedTiles += tile
            rawTile = rawTile.next
        }
        if (hydratedTiles.isNotEmpty()) tileMap.tileList = hydratedTiles

        tileMap.startingLocations.clear()
        var startingLocationNode = rawTileMap.get("startingLocations")?.child
        while (startingLocationNode != null) {
            runCatching {
                tileMap.startingLocations += parser.readValue(TileMap.StartingLocation::class.java, startingLocationNode)
            }
            startingLocationNode = startingLocationNode.next
        }
    }
}

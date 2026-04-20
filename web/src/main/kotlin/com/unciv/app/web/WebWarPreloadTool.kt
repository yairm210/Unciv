package com.unciv.app.web

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.json.json
import java.io.File

object WebWarPreloadTool {
    private const val defaultRuleset = "Civ V - Gods & Kings"
    private const val defaultRequiredUnit = "Warrior"
    private const val schemaVersion = 1

    private data class RoleDefinition(
        val preloadId: String,
        val payloadFile: String,
        val metadataFile: String,
        val mapSeed: Long,
    )

    private class WarPreloadMeta {
        var schemaVersion: Int = 0
        var preloadId: String = ""
        var ruleset: String = ""
        var mapSeed: Long = 0L
        var expectedPlayerCiv: String = ""
        var expectedMajorEnemies: ArrayList<String> = arrayListOf()
        var expectedPeaceTarget: String = ""
        var expectedRequiredUnit: String = defaultRequiredUnit
    }

    private class LocalToolFiles(
        private val assetsRoot: File,
        private val localRoot: File,
    ) : Files {
        override fun getFileHandle(path: String, type: Files.FileType): FileHandle {
            return when (type) {
                Files.FileType.Classpath -> classpath(path)
                Files.FileType.Internal -> internal(path)
                Files.FileType.External -> external(path)
                Files.FileType.Absolute -> absolute(path)
                Files.FileType.Local -> local(path)
            }
        }

        override fun classpath(path: String): FileHandle = FileHandle(File(assetsRoot, path))

        override fun internal(path: String): FileHandle = FileHandle(File(assetsRoot, path))

        override fun external(path: String): FileHandle = FileHandle(File(localRoot, path))

        override fun absolute(path: String): FileHandle = FileHandle(File(path))

        override fun local(path: String): FileHandle = FileHandle(File(localRoot, path))

        override fun getExternalStoragePath(): String = localRoot.absolutePath

        override fun isExternalStorageAvailable(): Boolean = true

        override fun getLocalStoragePath(): String = localRoot.absolutePath

        override fun isLocalStorageAvailable(): Boolean = true
    }

    private val roles = listOf(
        RoleDefinition(
            preloadId = "war-from-start",
            payloadFile = "war-from-start.save.txt",
            metadataFile = "war-from-start.meta.json",
            mapSeed = 2026021501L,
        ),
        RoleDefinition(
            preloadId = "war-preworld",
            payloadFile = "war-preworld.save.txt",
            metadataFile = "war-preworld.meta.json",
            mapSeed = 2026021502L,
        ),
        RoleDefinition(
            preloadId = "war-deep",
            payloadFile = "war-deep.save.txt",
            metadataFile = "war-deep.meta.json",
            mapSeed = 2026021503L,
        ),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()?.trim()?.lowercase() ?: "verify"
        val repoRoot = File(System.getProperty("user.dir")).absoluteFile
        val outputDir = File(repoRoot, "web/src/main/resources/webtest/preloads")

        bootstrapRuntime(repoRoot)
        val ruleset = loadRuleset()
        val civOrder = selectMajorCivs(ruleset)

        when (mode) {
            "generate" -> generateAll(outputDir, civOrder)
            "verify" -> verifyAll(outputDir)
            else -> error("Unsupported mode '$mode'. Use generate or verify.")
        }
    }

    private fun bootstrapRuntime(repoRoot: File) {
        val assetsRoot = File(repoRoot, "android/assets")
        if (!assetsRoot.isDirectory) {
            error("Missing assets root: ${assetsRoot.absolutePath}")
        }

        if (Gdx.files == null) {
            Gdx.files = LocalToolFiles(assetsRoot = assetsRoot, localRoot = repoRoot)
        }

        val game = UncivGame(isConsoleMode = true)
        game.settings = GameSettings()
        game.files = UncivFiles(Gdx.files, repoRoot.absolutePath)
        UncivGame.Current = game
    }

    private fun loadRuleset(): Ruleset {
        RulesetCache.clear()
        RulesetCache.loadRulesets(consoleMode = false, noMods = true)
        val setup = GameSetupInfo().apply {
            gameParameters.baseRuleset = defaultRuleset
        }
        return RulesetCache.getComplexRuleset(setup.gameParameters)
    }

    private fun selectMajorCivs(ruleset: Ruleset): List<String> {
        val preferred = listOf("America", "England", "France", "Germany")
            .filter { name -> ruleset.nations[name]?.isMajorCiv == true }
        if (preferred.size >= 4) return preferred.take(4)

        val fallback = ruleset.nations.values
            .asSequence()
            .filter { it.isMajorCiv }
            .map { it.name }
            .sorted()
            .take(4)
            .toList()
        if (fallback.size < 4) error("Could not select four major civs for WAR preload generation.")
        return fallback
    }

    private fun generateAll(outputDir: File, civOrder: List<String>) {
        outputDir.mkdirs()
        roles.forEach { role ->
            val generated = generateDeterministicGame(role, civOrder)
            val gameInfo = generated.first
            val playerCiv = generated.second
            val majorEnemies = generated.third
            val metadata = WarPreloadMeta().apply {
                schemaVersion = this@WebWarPreloadTool.schemaVersion
                preloadId = role.preloadId
                ruleset = defaultRuleset
                mapSeed = gameInfo.tileMap.mapParameters.seed
                expectedPlayerCiv = playerCiv
                expectedMajorEnemies = ArrayList(majorEnemies.take(2))
                expectedPeaceTarget = majorEnemies.first()
                expectedRequiredUnit = defaultRequiredUnit
            }

            ensureInvariants(gameInfo, metadata, role.preloadId)

            val payload = UncivFiles.gameInfoToString(gameInfo, forceZip = true, updateChecksum = true)
            val payloadFile = File(outputDir, role.payloadFile)
            val metadataFile = File(outputDir, role.metadataFile)
            payloadFile.writeText(payload)
            val metadataJson = json().apply { setUsePrototypes(false) }
            metadataFile.writeText(metadataJson.prettyPrint(metadata))

            println("generated ${payloadFile.absolutePath} and ${metadataFile.absolutePath}")
        }
    }

    private fun generateDeterministicGame(
        role: RoleDefinition,
        civOrder: List<String>,
    ): Triple<com.unciv.logic.GameInfo, String, List<String>> {
        for (attempt in 0..16) {
            val setup = GameSetupInfo().apply {
                gameParameters.baseRuleset = defaultRuleset
                gameParameters.players = arrayListOf(
                    Player(chosenCiv = civOrder[0], playerType = PlayerType.Human),
                    Player(chosenCiv = civOrder[1], playerType = PlayerType.AI),
                    Player(chosenCiv = civOrder[2], playerType = PlayerType.AI),
                    Player(chosenCiv = civOrder[3], playerType = PlayerType.AI),
                )
                gameParameters.randomNumberOfCityStates = false
                gameParameters.numberOfCityStates = 0
                gameParameters.minNumberOfCityStates = 0
                gameParameters.maxNumberOfCityStates = 0
                gameParameters.noBarbarians = true
                gameParameters.shufflePlayerOrder = false

                mapParameters.shape = MapShape.rectangular
                mapParameters.worldWrap = true
                mapParameters.mapSize = MapSize.Small
                mapParameters.type = MapType.pangaea
                mapParameters.seed = role.mapSeed + attempt
                mapParameters.baseRuleset = defaultRuleset
            }

            val gameInfo = GameStarter.startNewGame(setup)
            val player = gameInfo.civilizations.firstOrNull { it.isHuman() }
                ?: error("Generated game missing human player.")
            ensureCivilizationHasCity(player)
            gameInfo.civilizations
                .asSequence()
                .filter { civ -> civ != player && civ.isMajorCiv() }
                .forEach { ensureCivilizationHasCity(it) }
            if (role.preloadId == "war-preworld") {
                val peaceTargetName = civOrder[1]
                val peaceTarget = gameInfo.civilizations.firstOrNull { civ -> civ.civName == peaceTargetName }
                    ?: error("Generated game missing preworld peace target [$peaceTargetName].")
                ensureCivilizationHasExtraCityNearPlayer(player, peaceTarget)
            }

            val enemiesWithCities = gameInfo.civilizations
                .asSequence()
                .filter { civ -> civ != player && civ.isMajorCiv() && civ.cities.isNotEmpty() }
                .map { it.civName }
                .sorted()
                .toList()
            if (enemiesWithCities.size >= 2) {
                gameInfo.civilizations
                    .asSequence()
                    .filter { civ -> civ != player && civ.isMajorCiv() }
                    .forEach { enemy ->
                        if (!player.knows(enemy)) player.diplomacyFunctions.makeCivilizationsMeet(enemy)
                    }
                gameInfo.civilizations.forEach { civ ->
                    civ.popupAlerts.removeAll { alert ->
                        alert.type == AlertType.FirstContact || alert.type == AlertType.StartIntro
                    }
                }
                return Triple(gameInfo, player.civName, enemiesWithCities)
            }
        }
        error("Could not generate deterministic WAR preload for ${role.preloadId} with >=2 major enemies with cities.")
    }

    private fun ensureCivilizationHasCity(civ: com.unciv.logic.civilization.Civilization) {
        if (civ.cities.isNotEmpty()) return
        val settler = civ.units.getCivUnits().firstOrNull { it.hasUnique(UniqueType.FoundCity) }
            ?: civ.units.getCivUnits().firstOrNull()
            ?: return
        val cityLocation = settler.currentTile.position
        civ.addCity(cityLocation, settler)
    }

    private fun verifyAll(outputDir: File) {
        if (!outputDir.isDirectory) {
            error("Missing preload directory: ${outputDir.absolutePath}")
        }

        roles.forEach { role ->
            val payloadFile = File(outputDir, role.payloadFile)
            val metadataFile = File(outputDir, role.metadataFile)
            if (!payloadFile.isFile) error("Missing WAR preload payload: ${payloadFile.absolutePath}")
            if (!metadataFile.isFile) error("Missing WAR preload metadata: ${metadataFile.absolutePath}")

            val payload = payloadFile.readText().trim()
            val metadataRaw = metadataFile.readText().trim()
            if (payload.isBlank()) error("WAR preload payload is empty: ${payloadFile.absolutePath}")
            if (metadataRaw.isBlank()) error("WAR preload metadata is empty: ${metadataFile.absolutePath}")

            val metadata = json().fromJson(WarPreloadMeta::class.java, metadataRaw)
                ?: error("WAR preload metadata is invalid JSON: ${metadataFile.absolutePath}")
            if (metadata.preloadId != role.preloadId) {
                error("WAR preload id mismatch for ${role.preloadId}: metadata=${metadata.preloadId}")
            }
            if (metadata.schemaVersion != schemaVersion) {
                error("WAR preload schema mismatch for ${role.preloadId}: expected=$schemaVersion actual=${metadata.schemaVersion}")
            }

            val gameInfo = UncivFiles.gameInfoFromString(payload)
            ensureInvariants(gameInfo, metadata, role.preloadId)
            if (gameInfo.tileMap.mapParameters.seed != metadata.mapSeed) {
                error("WAR preload seed mismatch for ${role.preloadId}: expected=${metadata.mapSeed} actual=${gameInfo.tileMap.mapParameters.seed}")
            }

            println("verified ${role.preloadId}")
        }
    }

    private fun ensureInvariants(
        gameInfo: com.unciv.logic.GameInfo,
        metadata: WarPreloadMeta,
        roleId: String,
    ) {
        if (metadata.ruleset != gameInfo.gameParameters.baseRuleset) {
            error("Ruleset mismatch: expected=${metadata.ruleset} actual=${gameInfo.gameParameters.baseRuleset}")
        }

        val player = gameInfo.civilizations.firstOrNull { civ ->
            civ.civName == metadata.expectedPlayerCiv && civ.isHuman()
        } ?: error("Expected player civ not found: ${metadata.expectedPlayerCiv}")

        metadata.expectedMajorEnemies.forEach { enemyName ->
            val enemy = gameInfo.civilizations.firstOrNull { civ -> civ.civName == enemyName }
                ?: error("Expected major enemy missing: $enemyName")
            if (!enemy.isMajorCiv()) error("Expected major enemy is not major civ: $enemyName")
            if (enemy.cities.isEmpty()) error("Expected major enemy has no city: $enemyName")
            if (!player.knows(enemy)) error("Expected major enemy is not known by player: $enemyName")
        }

        val peaceTarget = gameInfo.civilizations.firstOrNull { civ -> civ.civName == metadata.expectedPeaceTarget }
            ?: error("Expected peace target missing: ${metadata.expectedPeaceTarget}")
        if (peaceTarget.cities.isEmpty()) {
            error("Expected peace target has no city: ${metadata.expectedPeaceTarget}")
        }
        if (roleId == "war-preworld" && peaceTarget.cities.size < 2) {
            error("war-preworld requires peace target to have at least two cities before capture.")
        }
        if (!player.knows(peaceTarget)) {
            error("Expected peace target is not known by player: ${metadata.expectedPeaceTarget}")
        }

        val blockingAlerts = player.popupAlerts.filter { alert ->
            alert.type == AlertType.FirstContact || alert.type == AlertType.StartIntro
        }
        if (blockingAlerts.isNotEmpty()) {
            val types = blockingAlerts.joinToString(",") { it.type.name }
            error("Expected preload has no blocking player popup alerts, found: $types")
        }

        val requiredUnit = gameInfo.ruleset.units[metadata.expectedRequiredUnit]
            ?: error("Required unit not found in ruleset: ${metadata.expectedRequiredUnit}")
        if (!requiredUnit.isMilitary || !requiredUnit.isLandUnit) {
            error("Required unit is not a land military unit: ${metadata.expectedRequiredUnit}")
        }

        val hasRequiredUnit = player.units.getCivUnits().any { unit ->
            unit.baseUnit.name == metadata.expectedRequiredUnit
        }
        if (!hasRequiredUnit) {
            val capital = player.getCapital() ?: error("Expected player has no capital: ${player.civName}")
            val placed = player.units.placeUnitNearTile(capital.location, requiredUnit)
            if (placed == null) {
                error("Could not ensure required unit [${metadata.expectedRequiredUnit}] for player [${player.civName}].")
            }
        }
    }

    private fun ensureCivilizationHasExtraCityNearPlayer(
        player: com.unciv.logic.civilization.Civilization,
        target: com.unciv.logic.civilization.Civilization,
    ) {
        if (target.cities.size >= 2) return
        val playerCapital = player.getCapital()?.getCenterTile() ?: return
        val candidateTile = playerCapital.getTilesInDistance(4)
            .asSequence()
            .filter { tile ->
                tile.isLand &&
                    !tile.isImpassible() &&
                    tile.getCity() == null &&
                    tile.militaryUnit == null &&
                    tile.civilianUnit == null
            }
            .sortedBy { tile -> tile.aerialDistanceTo(playerCapital) }
            .firstOrNull()
            ?: return
        runCatching { target.addCity(candidateTile.position) }
    }
}

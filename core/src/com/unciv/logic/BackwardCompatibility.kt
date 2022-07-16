package com.unciv.logic

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.json.HashMapVector2
import com.unciv.json.MultiplayerGameStatusSerializer
import com.unciv.json.json
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.MultiplayerGame
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameParametersMultiplayer
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.saves.Gzip
import com.unciv.utils.Log

/**
 * Container for all temporarily used code managing transitions from deprecated elements to their replacements.
 *
 * Please place ***all*** such code here and call  it _only_ from [GameInfo.setTransients].
 * Functions are allowed to remain once no longer used if you think they might serve as template for
 * similar usecases in the future. Please comment sufficiently :)
 */
@Suppress("unused")  // as mentioned above
object BackwardCompatibility {

    /**
     * Mods can change, leading to things on the map that are no longer defined in the mod.
     * This function removes them so the game doesn't crash when it tries to access them.
     */
    fun GameInfo.removeMissingModReferences() {
        tileMap.removeMissingTerrainModReferences(ruleSet)

        for (tile in tileMap.values) {
            for (unit in tile.getUnits()) {
                if (!ruleSet.units.containsKey(unit.name)) tile.removeUnit(unit)

                for (promotion in unit.promotions.promotions.toList())
                    if (!ruleSet.unitPromotions.containsKey(promotion))
                        unit.promotions.promotions.remove(promotion)
            }
        }

        for (city in civilizations.asSequence().flatMap { it.cities.asSequence() }) {

            changeBuildingNameIfNotInRuleset(ruleSet, city.cityConstructions, "Hanse", "Bank")

            for (building in city.cityConstructions.builtBuildings.toHashSet()) {

                if (!ruleSet.buildings.containsKey(building))
                    city.cityConstructions.builtBuildings.remove(building)
            }

            fun isInvalidConstruction(construction: String) =
                    !ruleSet.buildings.containsKey(construction)
                            && !ruleSet.units.containsKey(construction)
                            && !PerpetualConstruction.perpetualConstructionsMap.containsKey(construction)

            // Remove invalid buildings or units from the queue - don't just check buildings and units because it might be a special construction as well
            for (construction in city.cityConstructions.constructionQueue.toList()) {
                if (isInvalidConstruction(construction))
                    city.cityConstructions.constructionQueue.remove(construction)
            }
            // And from being in progress
            for (construction in city.cityConstructions.inProgressConstructions.keys.toList())
                if (isInvalidConstruction(construction))
                    city.cityConstructions.inProgressConstructions.remove(construction)
        }

        for (civInfo in civilizations) {
            for (tech in civInfo.tech.techsResearched.toList())
                if (!ruleSet.technologies.containsKey(tech))
                    civInfo.tech.techsResearched.remove(tech)
            for (policy in civInfo.policies.adoptedPolicies.toList())
                if (!ruleSet.policies.containsKey(policy))
                    civInfo.policies.adoptedPolicies.remove(policy)
        }
    }

    /**
     * Replaces all occurrences of [oldBuildingName] in [cityConstructions] with [newBuildingName]
     * if the former is not contained in the ruleset.
     */
    private fun changeBuildingNameIfNotInRuleset(
        ruleSet: Ruleset,
        cityConstructions: CityConstructions,
        oldBuildingName: String,
        newBuildingName: String
    ) {
        if (ruleSet.buildings.containsKey(oldBuildingName))
            return
        // Replace in built buildings
        if (cityConstructions.builtBuildings.contains(oldBuildingName)) {
            cityConstructions.builtBuildings.remove(oldBuildingName)
            cityConstructions.builtBuildings.add(newBuildingName)
        }
        // Replace in construction queue
        if (!cityConstructions.builtBuildings.contains(newBuildingName) && !cityConstructions.constructionQueue.contains(newBuildingName))
            cityConstructions.constructionQueue = cityConstructions.constructionQueue
                .map { if (it == oldBuildingName) newBuildingName else it }
                .toMutableList()
        else
            cityConstructions.constructionQueue.remove(oldBuildingName)
        // Replace in in-progress constructions
        if (cityConstructions.inProgressConstructions.containsKey(oldBuildingName)) {
            if (!cityConstructions.builtBuildings.contains(newBuildingName) && !cityConstructions.inProgressConstructions.containsKey(newBuildingName))
                cityConstructions.inProgressConstructions[newBuildingName] = cityConstructions.inProgressConstructions[oldBuildingName]!!
            cityConstructions.inProgressConstructions.remove(oldBuildingName)
        }
    }

    /** Replace a changed tech name */
    private fun TechManager.replaceUpdatedTechName(oldTechName: String, newTechName: String) {
        if (oldTechName in techsResearched) {
            techsResearched.remove(oldTechName)
            techsResearched.add(newTechName)
        }
        val index = techsToResearch.indexOf(oldTechName)
        if (index >= 0) {
            techsToResearch[index] = newTechName
        }
        if (oldTechName in techsInProgress) {
            techsInProgress[newTechName] = researchOfTech(oldTechName)
            techsInProgress.remove(oldTechName)
        }
    }

    /** Replace a deprecated DiplomacyFlags instance */
    fun GameInfo.replaceDiplomacyFlag(old: DiplomacyFlags, new: DiplomacyFlags) {
        fun DiplomacyManager.replaceFlag() {
            if (hasFlag(old)) {
                val value = getFlag(old)
                removeFlag(old)
                setFlag(new, value)
            }
        }
        civilizations.flatMap { civ -> civ.diplomacy.values }.forEach { it.replaceFlag() }
    }

    /** Make sure all MapUnits have the starting promotions that they're supposed to. */
    fun GameInfo.guaranteeUnitPromotions() {
        for (tileInfo in tileMap.values) for (unit in tileInfo.getUnits())
            for (startingPromo in unit.baseUnit.promotions)
                if (startingPromo !in unit.promotions.promotions)
                    unit.promotions.addPromotion(startingPromo, true)
    }

    /** Upgrade the uniques from deprecated format to the new more general one **/
    fun GameInfo.updateGreatGeneralUniques() {
        ruleSet.units.values.filter { it.uniques.contains("Bonus for units in 2 tile radius 15%") }.forEach {
            it.uniques.remove("Bonus for units in 2 tile radius 15%")
            it.uniques.add("[+15]% Strength bonus for [Military] units within [2] tiles")
        }
    }

    /** Move max XP from barbarians to new home */
    @Suppress("DEPRECATION")
    fun ModOptions.updateDeprecations() {
        if (maxXPfromBarbarians != 30) {
            constants.maxXPfromBarbarians = maxXPfromBarbarians
            maxXPfromBarbarians = 30
        }
    }

    /** Removes the workaround previously used for storing a map that does not have a [String] key
     * @see com.unciv.json.NonStringKeyMapSerializer
     */
    @Suppress("DEPRECATION")
    fun CivilizationInfo.migrateSeenImprovements() {
        if (lastSeenImprovementSaved.isEmpty()) return
        lastSeenImprovement.putAll(lastSeenImprovementSaved.mapKeys { Vector2().fromString(it.key) })
        lastSeenImprovementSaved.clear()
    }

    /**
     * Fixes barbarian manager camps not being correctly serialized. Previously we had a [HashMap<Vector2, Encampment] but it was being
     * serialized as [HashMap<String, Encampment>]. We need to fix that each time an old save is loaded.
     *
     * When removing this, also remove [com.unciv.json.NonStringKeyMapSerializer.readOldFormat]
     */
    @Suppress("DEPRECATION")
    fun BarbarianManager.migrateBarbarianCamps() {
        if (isOldFormat(this)) {
            val newFormat = HashMapVector2<Encampment>()
            @Suppress("UNCHECKED_CAST") // The old format is deserialized to a <String, JsonValue> map
            for ((key, value) in camps as MutableMap<String, JsonValue>) {
                val newKey = Vector2().fromString(key)
                val newValue = json().readValue(Encampment::class.java, value)
                newFormat[newKey] = newValue
            }

            camps.clear()
            camps.putAll(newFormat)
        }
    }

    /** Convert from Fortify X to Fortify and save off X */
    fun GameInfo.convertFortify() {
        val reg = Regex("""^Fortify\s+(\d+)([\w\s]*)""")
        for (civInfo in civilizations) {
            for (unit in civInfo.getCivUnits()) {
                if (unit.action != null && reg.matches(unit.action!!)) {
                    val (turns, heal) = reg.find(unit.action!!)!!.destructured
                    unit.turnsFortified = turns.toInt()
                    unit.action = "Fortify$heal"
                }
            }
        }
    }

    private fun isOldFormat(manager: BarbarianManager): Boolean {
        val keys = manager.camps.keys as Set<Any>
        val iterator = keys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key is String) {
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    fun GameInfo.convertOldGameSpeed() {
        if (gameParameters.gameSpeed != "" && gameParameters.gameSpeed in ruleSet.speeds.keys) {
            gameParameters.speed = gameParameters.gameSpeed
            gameParameters.gameSpeed = ""
        }
    }

    /** Remove this completely once users migrated their saves. When removing this, also remove [GameInfo.currentPlayer] and [GameInfoPreview.currentPlayer] */
    @Suppress("DEPRECATION")
    fun GameInfo.migrateCurrentCivName() {
        if (currentPlayer != "") {
            currentCivName = currentPlayer
            currentPlayer = ""
        }
    }

    /** Remove this completely once users migrated their saves. When removing this, also remove [GameInfo.currentPlayer] and [GameInfoPreview.currentPlayer] */
    @Suppress("DEPRECATION")
    fun GameInfoPreview.migrateCurrentCivName() {
        if (currentPlayer != "") {
            currentCivName = currentPlayer
            currentPlayer = ""
        }
    }

    /** Remove this completely once users migrated their saves. */
    fun MultiplayerGameStatusSerializer.readOldFormat(json: Json, jsonData: JsonValue): Multiplayer.GameStatus {
        val preview = json.readValue(GameInfoPreview::class.java, jsonData)
        preview.migrateCurrentCivName()

        val turns = preview.turns
        val currentTurnStartTime = preview.currentTurnStartTime
        val currentCivName = preview.currentCivName
        val currentPlayerId = preview.currentPlayerId

        return Multiplayer.GameStatus(turns, currentTurnStartTime, currentCivName, currentPlayerId)
    }

    const val OLD_MULTIPLAYER_FILES_FOLDER = "MultiplayerGames"
    /** Remove this completely once users migrated their multiplayer games. */
    suspend fun UncivFiles.migrateOldMultiplayerGames(files: Collection<FileHandle>): Map<String, MultiplayerGame> {
        val multiplayerGames = files
            .map {
                try {
                    val fileAsString = it.readString()

                    val jsonValue = JsonReader().parse(fileAsString)
                    val gameIdValue = jsonValue["gameId"]
                    val gameId = gameIdValue.asString()

                    val gameStatus = json().fromJson(Multiplayer.GameStatus::class.java, fileAsString)

                    // We purposefully don't migrate the lastUpdate property, which we theoretically could by looking at when the file was last modified.
                    // But it gets overwritten almost immediately afterwards anyway most likely, so it's really just wasted effort.
                    val multiplayerGame = MultiplayerGame(gameId, Multiplayer.ServerData.default, it.name(), gameStatus)

                    deleteFile(it) // don't need the old file anymore

                    multiplayerGame
                } catch (ex: Exception) {
                    Log.error("Error while migrating multiplayer game %s", it.name())
                    Log.error("Stacktrace:", ex)
                    return@map null
                }
            }
            .filterNotNull()
            .associateBy { it.gameId }

        saveMultiplayerGames(multiplayerGames)

        val oldFolder = getFile(OLD_MULTIPLAYER_FILES_FOLDER)
        if (oldFolder.list().isEmpty()) {
            deleteFile(oldFolder)
        }

        return multiplayerGames
    }

    /** Remove this completely once users migrated their saves & settings. */
    fun GameParameters.migrateMultiplayerParameters(defaultServerData: Multiplayer.ServerData) {
        if (isOnlineMultiplayer && multiplayer == null) {
            val mpParams = GameParametersMultiplayer(defaultServerData, null)
            mpParams.serverData = defaultServerData
            multiplayer = mpParams
        }
    }

    /** Remove this completely once users migrated their multiplayer games. */
    fun MultiplayerFiles.migrateGameStatus(gameStatusString: String): String {
        return try {
            Gzip.unzip(gameStatusString)
        } catch (ex: Exception) {
            gameStatusString
        }
    }
}

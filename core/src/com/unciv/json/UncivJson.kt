package com.unciv.json

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.SerializationException
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.Tutorial
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitNameGroup
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import java.time.Duration
import java.util.HashMap


/**
 * [Json] is not thread-safe. Use a new one for each parse.
 */
fun json() = Json(JsonWriter.OutputType.json).apply {
    // Gdx default output type is JsonWriter.OutputType.minimal, which generates invalid Json - e.g. most quotes removed.
    // The constructor parameter above changes that to valid Json
    // Note an instance set to json can read minimal and vice versa

    setIgnoreDeprecated(true)
    ignoreUnknownFields = true

    setSerializer(Duration::class.java, DurationSerializer())
    setSerializer(KeyCharAndCode::class.java, KeyCharAndCode.Serializer())
    setSerializer(Vector2::class.java, Vector2Serializer())
    setSerializer(HexCoord::class.java, HexCoord.Serializer())
    setSerializer(TileMap::class.java, TileMapSerializer())
    setSerializer(TechColumn::class.java, TechColumnSerializer())

    configureRulesetElementTypes()
}

private fun Json.configureRulesetElementTypes() {
    trySetElementType(RulesetObject::class.java, "uniques", String::class.java)
    trySetElementType(RulesetObject::class.java, "civilopediaText", FormattedLine::class.java)
    trySetElementType(RulesetStatsObject::class.java, "uniques", String::class.java)
    trySetElementType(RulesetStatsObject::class.java, "civilopediaText", FormattedLine::class.java)
    trySetElementType(TechColumn::class.java, "techs", Technology::class.java)
    trySetElementType(Technology::class.java, "prerequisites", String::class.java)
    trySetElementType(Policy::class.java, "requires", String::class.java)
    trySetElementType(PolicyBranch::class.java, "policies", Policy::class.java)
    trySetElementType(Event::class.java, "choices", EventChoice::class.java)
    trySetElementType(Speed::class.java, "turns", HashMap::class.java)
    trySetElementType(Building::class.java, "requiredNearbyImprovedResources", String::class.java)
    trySetElementType(GlobalUniques::class.java, "unitUniques", String::class.java)
    trySetElementType(ModOptions::class.java, "techsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "buildingsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "unitsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "nationsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "policyBranchesToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "policiesToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "beliefsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "religionsToRemove", String::class.java)
    trySetElementType(ModOptions::class.java, "topics", String::class.java)
    trySetElementType(ModOptions::class.java, "uniques", String::class.java)
    trySetElementType(Difficulty::class.java, "playerBonusStartingUnits", String::class.java)
    trySetElementType(Difficulty::class.java, "aiFreeTechs", String::class.java)
    trySetElementType(Difficulty::class.java, "aiMajorCivBonusStartingUnits", String::class.java)
    trySetElementType(Difficulty::class.java, "aiCityStateBonusStartingUnits", String::class.java)
    trySetElementType(CityStateType::class.java, "friendBonusUniques", String::class.java)
    trySetElementType(CityStateType::class.java, "allyBonusUniques", String::class.java)
    trySetElementType(Tutorial::class.java, "steps", String::class.java)
    trySetElementType(Nation::class.java, "startBias", String::class.java)
    trySetElementType(Nation::class.java, "spyNames", String::class.java)
    trySetElementType(Nation::class.java, "cities", String::class.java)
    trySetElementType(BaseUnit::class.java, "promotions", String::class.java)
    trySetElementType(Promotion::class.java, "prerequisites", String::class.java)
    trySetElementType(Promotion::class.java, "unitTypes", String::class.java)
    trySetElementType(UnitNameGroup::class.java, "unitNames", String::class.java)
    trySetElementType(Era::class.java, "settlerBuildings", String::class.java)
    trySetElementType(Era::class.java, "startingObsoleteWonders", String::class.java)
    trySetElementType(Terrain::class.java, "occursOn", String::class.java)
    trySetElementType(TileResource::class.java, "terrainsCanBeFoundOn", String::class.java)
    trySetElementType(TileResource::class.java, "improvedBy", String::class.java)
    trySetElementType(Victory::class.java, "milestones", String::class.java)
    trySetElementType(Victory::class.java, "requiredSpaceshipParts", String::class.java)
}

private fun Json.trySetElementType(type: Class<*>, fieldName: String, elementType: Class<*>) {
    try {
        setElementType(type, fieldName, elementType)
    } catch (_: SerializationException) {
        // Kotlin-backed properties are not always visible to libGDX's field lookup on every runtime.
        // Keep explicit serializers as the source of truth for the tricky TeaVM paths.
    }
}

/**
 *  Load a json file by [filePath] from Gdx.files.internal
 *  (meaning from jar/apk for packaged release code, and not appropriate for mod files)
 *  @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, filePath: String): T = fromJsonFile(tClass, Gdx.files.internal(filePath))

/**
 *  Load a json [file] - by handle, so internal/external/local is caller's decision.
 *
 *  Reminder:
 *  * `internal` for Unciv-packaged assets, loaded from jar/apk, e.g. Built-in ruleset files.
 *  * `local` for mods and settings - Android will place that under /data/data/com.unciv.app/files.
 *  * `external` for saves - Android will place that under /sdcard/Android/data/com.unciv.app/files.
 *  @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, file: FileHandle): T {
    try {
        return fromJson(tClass, file)
    } catch (exception: Exception) {
        val jsonText = file.readString(Charsets.UTF_8.name())
        throw Exception("Could not parse json of file ${file.name()}", exception)
    }
}

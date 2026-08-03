package com.unciv.app.desktop.mcp

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Whether map/unit reads are filtered to what [agentCiv] can actually see. */
enum class VisibilityMode { FULL, RESTRICTED }

/**
 * Turns engine state into compact JSON for an MCP tool response.
 * [VisibilityMode.RESTRICTED] filters tiles/units through [Tile.isVisible]/[Tile.isExplored]
 * exactly as the human client's fog of war would.
 */
class GameStateView(private val visibility: VisibilityMode) {

    fun civSummary(agentCiv: Civilization): JsonObject = buildJsonObject {
        put("civName", agentCiv.civName)
        put("gold", agentCiv.gold)
        put("happiness", agentCiv.getHappiness())
        val statMap = agentCiv.stats.getStatMapForNextTurn()
        val totals = statMap.values.fold(com.unciv.models.stats.Stats()) { acc, s -> acc.add(s); acc }
        putJsonObject("incomePerTurn") {
            put("gold", totals.gold)
            put("science", totals.science)
            put("culture", totals.culture)
            put("faith", totals.faith)
        }
        putJsonObject("research") {
            put("current", agentCiv.tech.currentTechnologyName())
            putJsonArray("queue") { agentCiv.tech.techsToResearch.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("researched") { agentCiv.tech.techsResearched.forEach { add(JsonPrimitive(it)) } }
            // Valid names for set_research - the agent otherwise has to guess.
            putJsonArray("researchable") {
                agentCiv.gameInfo.ruleset.technologies.values
                    .filter { agentCiv.tech.canBeResearched(it.name) }
                    .forEach { add(JsonPrimitive(it.name)) }
            }
        }
        putJsonArray("adoptedPolicies") { agentCiv.policies.getAdoptedPolicies().forEach { add(JsonPrimitive(it)) } }
        put("cityCount", agentCiv.cities.size)
        put("unitCount", agentCiv.units.getCivUnitsSize())
    }

    fun cities(agentCiv: Civilization): JsonArray = buildJsonArray {
        for (city in agentCiv.cities) {
            add(buildJsonObject {
                put("name", city.name)
                put("population", city.population.population)
                put("health", city.health)
                put("position", positionOf(city.getCenterTile()))
                put("currentProduction", city.cityConstructions.currentConstructionName())
                putJsonArray("productionQueue") { city.cityConstructions.constructionQueue.forEach { add(JsonPrimitive(it)) } }
                // Valid names for set_city_production - the agent otherwise has to guess.
                putJsonArray("buildable") {
                    city.cityConstructions.getBuildableBuildings().forEach { add(JsonPrimitive(it.name)) }
                    city.cityConstructions.getConstructableUnits().forEach { add(JsonPrimitive(it.name)) }
                }
            })
        }
    }

    fun units(agentCiv: Civilization): JsonArray = buildJsonArray {
        for (unit in agentCiv.units.getCivUnits()) {
            add(buildJsonObject {
                put("id", unit.id)
                put("name", unit.name)
                put("position", positionOf(unit.getTile()))
                put("health", unit.health)
                put("movementLeft", unit.currentMovement)
                putJsonArray("availableActions") {
                    availableActions(unit).forEach { add(JsonPrimitive(it)) }
                }
            })
        }
    }

    fun map(agentCiv: Civilization, gameInfo: com.unciv.logic.GameInfo): JsonArray = buildJsonArray {
        for (tile in gameInfo.tileMap.tileList) {
            val entry = tileToJson(tile, agentCiv) ?: continue
            add(entry)
        }
    }

    /** Same as [map] but limited to tiles within [radius] hexes of ([centerX], [centerY]) - keeps large maps out of the LLM's context. */
    fun map(agentCiv: Civilization, gameInfo: com.unciv.logic.GameInfo, centerX: Int, centerY: Int, radius: Int): JsonArray = buildJsonArray {
        for (tile in gameInfo.tileMap.tileList) {
            if (com.unciv.logic.map.HexMath.getDistance(centerX, centerY, tile.position.x, tile.position.y) > radius) continue
            val entry = tileToJson(tile, agentCiv) ?: continue
            add(entry)
        }
    }

    fun events(agentCiv: Civilization, sinceTurns: Int): JsonObject = buildJsonObject {
        putJsonArray("current") { agentCiv.notifications.forEach { add(notificationToJson(it)) } }
        putJsonArray("log") {
            for (turnLog in agentCiv.notificationsLog.takeLast(sinceTurns)) {
                add(buildJsonObject {
                    put("turn", turnLog.turn)
                    putJsonArray("notifications") { turnLog.notifications.forEach { add(notificationToJson(it)) } }
                })
            }
        }
    }

    private fun tileToJson(tile: Tile, agentCiv: Civilization): JsonObject? {
        if (visibility == VisibilityMode.RESTRICTED && !tile.isExplored(agentCiv)) return null
        val visible = visibility == VisibilityMode.FULL || tile.isVisible(agentCiv)
        return buildJsonObject {
            put("position", positionOf(tile))
            put("baseTerrain", tile.baseTerrain)
            put("improvement", tile.getShownImprovement(agentCiv))
            put("visible", visible)
            if (visible) {
                put("resource", tile.resource)
                put("owningCity", tile.getCity()?.name)
                tile.civilianUnit?.let { put("civilianUnit", it.name) }
                tile.militaryUnit?.let { put("militaryUnit", it.name) }
            }
        }
    }

    /**
     * [UnitActions.getUnitActions] (no type arg) enumerates every action provider, including
     * addEscortAction/addSwapAction, which eagerly call [com.unciv.GUI.getWorldScreen] even
     * though we're headless - that's an instant NPE, every time, for every land/sea unit.
     * So instead of that enumerator, list only what's headless-safe to check:
     * - mapped action types (FoundCity, ConstructImprovement, ...) via the type-filtered
     *   overload, which invokes just that one provider - except ConnectRoad, which *also*
     *   eagerly touches the world screen (for units that can build roads).
     * - the common unmapped verbs (Fortify/Sleep/Explore/Automate), whose real availability
     *   we derive from the same unit-state checks their GUI actions use internally, since
     *   asking the crashing enumerator isn't an option.
     */
    private fun availableActions(unit: MapUnit): List<String> = buildList {
        for (type in UnitActions.mappedActionTypes) {
            if (type == UnitActionType.ConnectRoad) continue
            if (UnitActions.getUnitActions(unit, type).any { it.action != null }) add(type.name)
        }
        if (unit.canFortify() && unit.hasMovement()) add(UnitActionType.Fortify.name)
        if (!unit.isFortified() && !unit.canFortify() && !unit.isGuarding() && unit.hasMovement()) add(UnitActionType.Sleep.name)
        if (!unit.baseUnit.movesLikeAirUnits && !unit.isExploring()) add(UnitActionType.Explore.name)
        if (!unit.isAutomated() && unit.hasMovement()) add(UnitActionType.Automate.name)
    }

    private fun notificationToJson(notification: Notification): JsonElement = JsonPrimitive(notification.text)

    private fun positionOf(tile: Tile): JsonObject = buildJsonObject {
        put("x", tile.position.x)
        put("y", tile.position.y)
    }
}

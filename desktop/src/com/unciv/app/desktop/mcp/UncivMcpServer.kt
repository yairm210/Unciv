package com.unciv.app.desktop.mcp

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.UnitActionType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * MCP tool surface for the LLM counterparty. One [Server] per process, one connected game.
 * Each tool re-downloads the [GameInfo] fresh from the multiplayer server before mutating it,
 * mirroring [com.unciv.logic.multiplayer.Multiplayer.skipCurrentPlayerTurn] - see the plan doc
 * for why we never cache a GameInfo across calls or clone before nextTurn().
 */
class UncivMcpServer {
    private var connection: GameConnection? = null

    private class GameConnection(
        val gameId: String,
        val civId: String,
        val visibility: VisibilityMode,
    )

    val server: Server = Server(
        serverInfo = Implementation(name = "unciv", version = "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
    ) {
        registerConnectGame()
        registerTurnStatus()
        registerEndTurn()
        registerReadTools()
        registerActionTools()
        registerChat()
    }

    private fun requireConnection(): GameConnection =
        connection ?: error("Not connected. Call connect_game first.")

    private fun textResult(text: String) = CallToolResult(content = listOf(TextContent(text)))

    private fun jsonResult(json: JsonObject) = textResult(json.toString())

    private fun errorResult(message: String) = CallToolResult(content = listOf(TextContent(message)), isError = true)

    private fun Server.registerConnectGame() {
        addTool(
            name = "connect_game",
            description = "Connect to a running Unciv multiplayer game as the agent's civilization. Must be called before any other tool.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("serverUrl") { put("type", "string"); put("description", "Base URL of the UncivServer, e.g. http://localhost:8080") }
                    putJsonObject("gameId") { put("type", "string") }
                    putJsonObject("playerId") { put("type", "string"); put("description", "This agent's multiplayer user id") }
                    putJsonObject("civName") { put("type", "string"); put("description", "civID of the civilization this agent controls") }
                    putJsonObject("password") { put("type", "string") }
                    putJsonObject("visibilityMode") { put("type", "string"); put("description", "FULL or RESTRICTED") }
                },
                required = listOf("serverUrl", "gameId", "playerId", "civName", "password"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val serverUrl = args["serverUrl"]?.jsonPrimitive?.content ?: return@addTool errorResult("serverUrl required")
            val gameId = args["gameId"]?.jsonPrimitive?.content ?: return@addTool errorResult("gameId required")
            val playerId = args["playerId"]?.jsonPrimitive?.content ?: return@addTool errorResult("playerId required")
            val civName = args["civName"]?.jsonPrimitive?.content ?: return@addTool errorResult("civName required")
            val password = args["password"]?.jsonPrimitive?.content ?: return@addTool errorResult("password required")
            val visibility = when (args["visibilityMode"]?.jsonPrimitive?.content?.uppercase()) {
                "RESTRICTED" -> VisibilityMode.RESTRICTED
                else -> VisibilityMode.FULL
            }

            val settings = UncivGame.Current.settings.multiplayer
            settings.setServer(serverUrl)
            settings.setUserId(playerId)
            settings.setCurrentServerPassword(password)
            // MultiplayerServer() re-derives serverUrl from these settings on every call
            // (see MultiplayerServer.fileStorage()), but checkAuthStatus/authenticate below call
            // UncivServerFileStorage directly, which does NOT go through that - it needs its own
            // serverUrl set, or SimpleHttp builds "http:///auth" (empty host) and the JVM throws
            // "Failed to select a proxy" trying to resolve a proxy for a null host.
            UncivServerFileStorage.serverUrl = serverUrl

            val authStatus = UncivServerFileStorage.checkAuthStatus(playerId, password)
            when (authStatus) {
                AuthStatus.UNREGISTERED -> UncivServerFileStorage.authenticate(playerId, password)
                AuthStatus.UNAUTHORIZED -> return@addTool errorResult("Authentication failed: invalid credentials")
                AuthStatus.UNKNOWN -> return@addTool errorResult("Could not reach server at $serverUrl")
                AuthStatus.VERIFIED -> {}
            }

            // Verify civName/playerId actually refer to the same, human-controlled slot: nextTurn()
            // auto-plays AI civs (we'd never get a turn) and AI civs never populate notifications
            // (get_events would silently stay empty), so this must hold for the agent to work at all.
            val gameInfo = MultiplayerServer().tryDownloadGame(gameId)
            val civ = gameInfo.getCivilizationOrNull(civName)
                ?: return@addTool errorResult("No civilization named $civName in game $gameId")
            if (civ.playerType != PlayerType.Human) {
                return@addTool errorResult("$civName is an AI-controlled civ; the agent must occupy a Human slot")
            }
            if (civ.playerId != playerId) {
                return@addTool errorResult("$civName's slot belongs to playerId '${civ.playerId}', not '$playerId'")
            }

            connection = GameConnection(gameId, civName, visibility)
            ChatWebSocket.requestMessageSend(Message.Join(listOf(gameId)))

            textResult("Connected to game $gameId as $civName")
        }
    }

    private fun Server.registerTurnStatus() {
        addTool(
            name = "get_turn_status",
            description = "Check whether it is currently the agent's turn. Call this before acting; if isMyTurn is false, wait and poll again.",
        ) {
            val conn = requireConnection()
            val preview = MultiplayerServer().tryDownloadGamePreview(conn.gameId)
            jsonResult(buildJsonObject {
                put("turn", preview.turns)
                put("currentPlayer", preview.currentPlayer)
                put("isMyTurn", preview.isUsersTurn())
            })
        }
    }

    private fun Server.registerEndTurn() {
        addTool(
            name = "end_turn",
            description = "End the agent's turn, advancing the game (auto-plays any AI civs) until the next human's turn.",
        ) {
            val conn = requireConnection()
            val multiplayerServer = MultiplayerServer()
            val gameInfo = multiplayerServer.tryDownloadGame(conn.gameId)
            if (gameInfo.currentPlayer != conn.civId) {
                return@addTool errorResult("It is not ${conn.civId}'s turn (current: ${gameInfo.currentPlayer})")
            }
            gameInfo.nextTurn()
            multiplayerServer.uploadGame(gameInfo, withPreview = true)
            textResult("Turn ended. Now turn ${gameInfo.turns}, current player: ${gameInfo.currentPlayer}")
        }
    }

    private fun Server.registerReadTools() {
        addTool(name = "get_my_civ", description = "Read the agent's civilization: gold, income, research, policies, counts.") {
            withGame { gameInfo, civ, view -> jsonResult(view.civSummary(civ)) }
        }
        addTool(name = "get_cities", description = "List the agent's cities with population, health, and production queues.") {
            withGame { gameInfo, civ, view -> jsonResult(buildJsonObject { put("cities", view.cities(civ)) }) }
        }
        addTool(name = "get_units", description = "List the agent's units with position, health, movement, and available actions.") {
            withGame { gameInfo, civ, view -> jsonResult(buildJsonObject { put("units", view.units(civ)) }) }
        }
        addTool(
            name = "get_map",
            description = "Read the map, filtered by the connection's visibility mode. Defaults to the whole map; " +
                "pass centerX/centerY/radius to limit output for large maps.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("centerX") { put("type", "integer") }
                    putJsonObject("centerY") { put("type", "integer") }
                    putJsonObject("radius") { put("type", "integer"); put("description", "Hex distance from center, used only if center is given") }
                },
            ),
        ) { request ->
            val args = request.arguments
            val centerX = args?.get("centerX")?.jsonPrimitive?.int
            val centerY = args?.get("centerY")?.jsonPrimitive?.int
            val radius = args?.get("radius")?.jsonPrimitive?.int ?: 5
            withGame { gameInfo, civ, view ->
                val tiles = if (centerX != null && centerY != null) view.map(civ, gameInfo, centerX, centerY, radius) else view.map(civ, gameInfo)
                jsonResult(buildJsonObject { put("tiles", tiles) })
            }
        }
        addTool(
            name = "get_events",
            description = "Read notifications: the current turn's events plus a recent per-turn archive - what happened while it wasn't the agent's turn.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("turnsOfHistory") { put("type", "integer"); put("description", "How many past turns of the notification log to include, default 3") }
                },
            ),
        ) { request ->
            val turns = request.arguments?.get("turnsOfHistory")?.jsonPrimitive?.int ?: 3
            withGame { gameInfo, civ, view -> jsonResult(view.events(civ, turns)) }
        }
    }

    private fun Server.registerActionTools() {
        addTool(
            name = "set_research",
            description = "Set the technology research queue (replaces the current queue).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("techNames") { put("type", "array") }
                },
                required = listOf("techNames"),
            ),
        ) { request ->
            val techNames = request.arguments?.get("techNames")?.let { arg ->
                (arg as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }
            } ?: return@addTool errorResult("techNames required")
            mutateGame { gameInfo, civ ->
                val unknown = techNames.filterNot { gameInfo.ruleset.technologies.containsKey(it) }
                if (unknown.isNotEmpty()) return@mutateGame errorResult("Unknown tech(s): $unknown")
                civ.tech.techsToResearch = ArrayList(techNames)
                null
            }
        }

        addTool(
            name = "set_city_production",
            description = "Queue a building/unit/wonder for construction in a city.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("cityName") { put("type", "string") }
                    putJsonObject("construction") { put("type", "string") }
                },
                required = listOf("cityName", "construction"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val cityName = args["cityName"]?.jsonPrimitive?.content ?: return@addTool errorResult("cityName required")
            val construction = args["construction"]?.jsonPrimitive?.content ?: return@addTool errorResult("construction required")
            mutateGame { gameInfo, civ ->
                val city = civ.cities.firstOrNull { it.name == cityName }
                    ?: return@mutateGame errorResult("No city named $cityName")
                val ruleset = gameInfo.ruleset
                val isKnown = ruleset.buildings.containsKey(construction) ||
                    ruleset.units.containsKey(construction) ||
                    com.unciv.models.ruleset.PerpetualConstruction.isNamePerpetual(construction)
                if (!isKnown) return@mutateGame errorResult("$construction is not a known building, unit, or wonder")
                city.cityConstructions.addToQueue(construction)
                null
            }
        }

        addTool(
            name = "move_unit",
            description = "Move a unit towards a tile (multi-turn pathing if it can't be reached this turn).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("unitId") { put("type", "integer") }
                    putJsonObject("x") { put("type", "integer") }
                    putJsonObject("y") { put("type", "integer") }
                },
                required = listOf("unitId", "x", "y"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val unitId = args["unitId"]?.jsonPrimitive?.int ?: return@addTool errorResult("unitId required")
            val x = args["x"]?.jsonPrimitive?.int ?: return@addTool errorResult("x required")
            val y = args["y"]?.jsonPrimitive?.int ?: return@addTool errorResult("y required")
            mutateGame { gameInfo, civ ->
                val unit = civ.units.getCivUnits().firstOrNull { it.id == unitId }
                    ?: return@mutateGame errorResult("No unit with id $unitId")
                val destination = gameInfo.tileMap.getOrNull(x, y)
                    ?: return@mutateGame errorResult("No tile at ($x, $y)")
                try {
                    unit.movement.headTowards(destination)
                } catch (e: UnitMovement.UnreachableDestinationException) {
                    return@mutateGame errorResult("Unit $unitId can't reach ($x, $y) this turn")
                }
                null
            }
        }

        addTool(
            name = "unit_action",
            description = "Invoke a named unit action (e.g. FoundCity, Fortify, Sleep, Explore, Upgrade, ConstructImprovement) on a unit.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("unitId") { put("type", "integer") }
                    putJsonObject("action") { put("type", "string") }
                },
                required = listOf("unitId", "action"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val unitId = args["unitId"]?.jsonPrimitive?.int ?: return@addTool errorResult("unitId required")
            val actionName = args["action"]?.jsonPrimitive?.content ?: return@addTool errorResult("action required")
            val actionType = UnitActionType.entries.firstOrNull { it.name == actionName }
                ?: return@addTool errorResult("Unknown action type $actionName")
            mutateGame { gameInfo, civ ->
                val unit: MapUnit = civ.units.getCivUnits().firstOrNull { it.id == unitId }
                    ?: return@mutateGame errorResult("No unit with id $unitId")
                // Fortify/Sleep/Explore/Automate aren't in UnitActions' mapped-provider table, so
                // invokeUnitAction would fall back to enumerating every provider - including
                // addEscortAction/addSwapAction, which crash headless (see GameStateView.units()).
                // Mutate the same unit state their GUI actions do, directly, instead.
                when (actionType) {
                    UnitActionType.Fortify -> {
                        if (!unit.canFortify() || !unit.hasMovement()) return@mutateGame errorResult("Unit $unitId can't fortify right now")
                        unit.fortify()
                    }
                    UnitActionType.Sleep -> {
                        if (unit.isFortified() || unit.canFortify() || unit.isGuarding() || !unit.hasMovement())
                            return@mutateGame errorResult("Unit $unitId can't sleep right now")
                        unit.action = UnitActionType.Sleep.value
                    }
                    UnitActionType.Explore -> {
                        if (unit.baseUnit.movesLikeAirUnits) return@mutateGame errorResult("Air units can't explore")
                        unit.action = UnitActionType.Explore.value
                        if (unit.hasMovement()) UnitAutomation.automatedExplore(unit)
                    }
                    UnitActionType.Automate -> {
                        if (!unit.hasMovement()) return@mutateGame errorResult("Unit $unitId can't automate right now")
                        unit.automated = true
                        UnitAutomation.automateUnitMoves(unit)
                    }
                    else -> {
                        // Mapped types (FoundCity, ConstructImprovement, ...) are headless-safe.
                        // Any other unmapped type would still hit the enumerator crash - catch it
                        // rather than let the whole call NPE.
                        val invoked = try {
                            UnitActions.invokeUnitAction(unit, actionType)
                        } catch (e: Exception) {
                            return@mutateGame errorResult("Action $actionName isn't supported for unit $unitId in a headless session")
                        }
                        if (!invoked) return@mutateGame errorResult("Action $actionName is not available for unit $unitId right now")
                    }
                }
                null
            }
        }
    }

    private fun Server.registerChat() {
        addTool(
            name = "send_chat",
            description = "Send a chat message to the human player.",
            inputSchema = ToolSchema(
                properties = buildJsonObject { putJsonObject("message") { put("type", "string") } },
                required = listOf("message"),
            ),
        ) { request ->
            val conn = requireConnection()
            val message = request.arguments?.get("message")?.jsonPrimitive?.content
                ?: return@addTool errorResult("message required")
            ChatWebSocket.requestMessageSend(Message.Chat(conn.civId, message, conn.gameId))
            textResult("Sent")
        }
    }

    /** Read-only helper: download, look up the agent's civ, hand it to [block]. */
    private suspend fun withGame(block: (GameInfo, com.unciv.logic.civilization.Civilization, GameStateView) -> CallToolResult): CallToolResult {
        val conn = requireConnection()
        val gameInfo = MultiplayerServer().tryDownloadGame(conn.gameId)
        val civ = gameInfo.getCivilizationOrNull(conn.civId)
            ?: return errorResult("Civilization ${conn.civId} not found in game")
        return block(gameInfo, civ, GameStateView(conn.visibility))
    }

    /**
     * Mutate-and-upload helper for act tools: download fresh, apply [block], upload without
     * calling nextTurn() (only end_turn advances the game). [block] returns an error result to
     * short-circuit, or null on success.
     */
    private suspend fun mutateGame(block: (GameInfo, com.unciv.logic.civilization.Civilization) -> CallToolResult?): CallToolResult {
        val conn = requireConnection()
        val multiplayerServer = MultiplayerServer()
        val gameInfo = multiplayerServer.tryDownloadGame(conn.gameId)
        val civ = gameInfo.getCivilizationOrNull(conn.civId)
            ?: return errorResult("Civilization ${conn.civId} not found in game")
        if (gameInfo.currentPlayer != conn.civId) {
            return errorResult("It is not ${conn.civId}'s turn (current: ${gameInfo.currentPlayer})")
        }
        block(gameInfo, civ)?.let { return it }
        multiplayerServer.uploadGame(gameInfo, withPreview = true)
        return textResult("Applied")
    }
}

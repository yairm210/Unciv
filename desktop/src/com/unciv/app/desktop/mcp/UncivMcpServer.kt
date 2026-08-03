package com.unciv.app.desktop.mcp

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.city.CityFocus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
    ) {
        /** Turn number last served to get_events(sinceMyLastTurn=true) - the engine has no
         *  per-agent turn memory, so the server tracks this itself (see registerReadTools). */
        var lastGistTurn: Int = 0
        /** Index into ChatStore.Chat.messagesSince up to which get_events has already returned
         *  chat messages - so repeated calls don't re-show the same lines. */
        var lastChatIndex: Int = 0
    }

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

    // Error messages that list what IS valid, so the agent can recover without another round-trip.
    private fun unknownCityError(civ: com.unciv.logic.civilization.Civilization, cityName: String) =
        "No city named $cityName; valid: ${civ.cities.joinToString { it.name }}"
    private fun unknownUnitError(civ: com.unciv.logic.civilization.Civilization, unitId: Int) =
        "No unit with id $unitId; valid ids: ${civ.units.getCivUnits().joinToString { "${it.id} (${it.name})" }}"
    private fun unknownCivError(gameInfo: GameInfo, civName: String) =
        "No civilization named $civName; valid: ${gameInfo.civilizations.joinToString { it.civName }}"

    private fun Server.registerConnectGame() {
        addTool(
            name = "connect_game",
            description = "Connect to a running Unciv multiplayer game as the agent's civilization. Must be called before any other tool. " +
                "civName is optional - if omitted, the civilization is looked up by playerId (the human slot the agent occupies).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("serverUrl") { put("type", "string"); put("description", "Base URL of the UncivServer, e.g. http://localhost:8080") }
                    putJsonObject("gameId") { put("type", "string") }
                    putJsonObject("playerId") { put("type", "string"); put("description", "This agent's multiplayer user id") }
                    putJsonObject("civName") { put("type", "string"); put("description", "civID of the civilization this agent controls; omit to auto-detect from playerId") }
                    putJsonObject("password") { put("type", "string") }
                    putJsonObject("visibilityMode") { put("type", "string"); put("description", "FULL or RESTRICTED") }
                },
                required = listOf("serverUrl", "gameId", "playerId", "password"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val serverUrl = args["serverUrl"]?.jsonPrimitive?.content ?: return@addTool errorResult("serverUrl required")
            val gameId = args["gameId"]?.jsonPrimitive?.content ?: return@addTool errorResult("gameId required")
            val playerId = args["playerId"]?.jsonPrimitive?.content ?: return@addTool errorResult("playerId required")
            val requestedCivName = args["civName"]?.jsonPrimitive?.content
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
            val civ = if (requestedCivName != null) {
                val named = gameInfo.getCivilizationOrNull(requestedCivName)
                    ?: return@addTool errorResult(unknownCivError(gameInfo, requestedCivName))
                if (named.playerType != PlayerType.Human) {
                    return@addTool errorResult("$requestedCivName is an AI-controlled civ; the agent must occupy a Human slot")
                }
                if (named.playerId != playerId) {
                    return@addTool errorResult("$requestedCivName's slot belongs to playerId '${named.playerId}', not '$playerId'")
                }
                named
            } else {
                gameInfo.civilizations.firstOrNull { it.playerType == PlayerType.Human && it.playerId == playerId }
                    ?: return@addTool errorResult("No human-controlled civilization in game $gameId belongs to playerId '$playerId'; " +
                        "human civs: ${gameInfo.civilizations.filter { it.playerType == PlayerType.Human }.joinToString { it.civName }}")
            }
            val civName = civ.civName

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
            description = "Read notifications and chat: the current turn's events plus a recent per-turn archive - what happened while it wasn't the agent's turn - " +
                "and any chat messages received since the last get_events call. " +
                "Pass sinceMyLastTurn=true for a turn-start gist: only notifications logged since this tool was last called that way, plus score/gold/tech/etc deltas. " +
                "Call that mode once at the start of your turn, not while polling for it to start - each call advances the cutoff.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("turnsOfHistory") { put("type", "integer"); put("description", "How many past turns of the notification log to include, default 3 (ignored if sinceMyLastTurn is true)") }
                    putJsonObject("sinceMyLastTurn") { put("type", "boolean"); put("description", "Return only what's new since the last sinceMyLastTurn=true call, plus stat deltas") }
                },
            ),
        ) { request ->
            val conn = requireConnection()
            val turns = request.arguments?.get("turnsOfHistory")?.jsonPrimitive?.int ?: 3
            val sinceMyLastTurn = request.arguments?.get("sinceMyLastTurn")?.jsonPrimitive?.boolean ?: false
            val chat = ChatStore.getChatByGameId(conn.gameId)
            val newChatMessages = chat.messagesSince(conn.lastChatIndex)
            withGame { gameInfo, civ, view ->
                val cutoff = if (sinceMyLastTurn) conn.lastGistTurn else null
                val result = jsonResult(view.events(civ, gameInfo, turns, cutoff, newChatMessages))
                if (sinceMyLastTurn) conn.lastGistTurn = gameInfo.turns
                conn.lastChatIndex = chat.length
                result
            }
        }

        addTool(
            name = "get_chat",
            description = "Read the full chat log for this game (get_events only shows messages new since the last call).",
        ) {
            val conn = requireConnection()
            val chat = ChatStore.getChatByGameId(conn.gameId)
            jsonResult(buildJsonObject {
                putJsonArray("messages") {
                    chat.messagesSince(0).forEach { (civName, message) ->
                        add(buildJsonObject {
                            put("from", civName)
                            put("message", message)
                        })
                    }
                }
            })
        }

        addTool(
            name = "get_civ_intel",
            description = "Comparative standing a human sees: per-metric rankings (score, gold, military " +
                "force, tech count, population, …) of the major civs, plus each met civ's era, score, and " +
                "who they're at war with. Stats are turn-start snapshots; in RESTRICTED mode they obey the " +
                "host's hide-other-civ-stats setting and unmet civs show as 'unknown'.",
        ) {
            withGame { gameInfo, civ, view -> jsonResult(view.civIntel(civ, gameInfo)) }
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
                    ?: return@mutateGame errorResult(unknownCityError(civ, cityName))
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
                    ?: return@mutateGame errorResult(unknownUnitError(civ, unitId))
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
                    ?: return@mutateGame errorResult(unknownUnitError(civ, unitId))
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

        addTool(
            name = "purchase_construction",
            description = "Buy a unit/building/wonder in a city immediately with Gold (default) or Faith, instead of waiting for production.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("cityName") { put("type", "string") }
                    putJsonObject("construction") { put("type", "string") }
                    putJsonObject("stat") { put("type", "string"); put("description", "Gold or Faith, default Gold") }
                },
                required = listOf("cityName", "construction"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val cityName = args["cityName"]?.jsonPrimitive?.content ?: return@addTool errorResult("cityName required")
            val construction = args["construction"]?.jsonPrimitive?.content ?: return@addTool errorResult("construction required")
            val statName = args["stat"]?.jsonPrimitive?.content ?: "Gold"
            mutateGame { gameInfo, civ ->
                val city = civ.cities.firstOrNull { it.name == cityName }
                    ?: return@mutateGame errorResult(unknownCityError(civ, cityName))
                val stat = runCatching { Stat.valueOf(statName) }.getOrNull()
                    ?: return@mutateGame errorResult("Unknown stat $statName; use Gold or Faith")
                if (stat != Stat.Gold && stat != Stat.Faith)
                    return@mutateGame errorResult("Can only purchase with Gold or Faith")
                val constr = (gameInfo.ruleset.buildings[construction] ?: gameInfo.ruleset.units[construction]) as? INonPerpetualConstruction
                    ?: return@mutateGame errorResult("$construction is not a purchasable building or unit; buildable in $cityName: " +
                        (city.cityConstructions.getBuildableBuildings().map { it.name } + city.cityConstructions.getConstructableUnits().map { it.name }).joinToString())
                val cost = constr.getStatBuyCost(city, stat)
                    ?: return@mutateGame errorResult("$construction cannot be bought with $statName")
                if (!city.cityConstructions.isConstructionPurchaseAllowed(constr, stat, cost))
                    return@mutateGame errorResult("Cannot purchase $construction in $cityName right now (cost $cost $statName, reserve ${city.getStatReserve(stat)})")
                if (!city.cityConstructions.purchaseConstruction(construction, queuePosition = -1, automatic = false, stat = stat))
                    return@mutateGame errorResult("Purchase of $construction failed (could not place unit?)")
                null
            }
        }

        addTool(
            name = "modify_production_queue",
            description = "Reorder or remove an entry in a city's production queue (use set_city_production to add).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("cityName") { put("type", "string") }
                    putJsonObject("index") { put("type", "integer") }
                    putJsonObject("op") { put("type", "string"); put("description", "remove|top|end|raise|lower") }
                },
                required = listOf("cityName", "index", "op"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val cityName = args["cityName"]?.jsonPrimitive?.content ?: return@addTool errorResult("cityName required")
            val index = args["index"]?.jsonPrimitive?.int ?: return@addTool errorResult("index required")
            val op = args["op"]?.jsonPrimitive?.content ?: return@addTool errorResult("op required")
            mutateGame { gameInfo, civ ->
                val city = civ.cities.firstOrNull { it.name == cityName }
                    ?: return@mutateGame errorResult(unknownCityError(civ, cityName))
                val cc = city.cityConstructions
                if (index !in cc.constructionQueue.indices)
                    return@mutateGame errorResult("index $index out of range (queue size ${cc.constructionQueue.size})")
                when (op) {
                    "remove" -> cc.removeFromQueue(index, automatic = false)
                    "top" -> cc.moveEntryToTop(index)
                    "end" -> cc.moveEntryToEnd(index)
                    "raise" -> cc.raisePriority(index)
                    "lower" -> cc.lowerPriority(index)
                    else -> return@mutateGame errorResult("Unknown op $op; use remove|top|end|raise|lower")
                }
                null
            }
        }

        addTool(
            name = "set_city_focus",
            description = "Set a city's citizen-management focus (e.g. ProductionFocus, GoldFocus) and immediately reassign worked tiles/specialists.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("cityName") { put("type", "string") }
                    putJsonObject("focus") { put("type", "string") }
                },
                required = listOf("cityName", "focus"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool errorResult("Missing arguments")
            val cityName = args["cityName"]?.jsonPrimitive?.content ?: return@addTool errorResult("cityName required")
            val focus = args["focus"]?.jsonPrimitive?.content ?: return@addTool errorResult("focus required")
            mutateGame { gameInfo, civ ->
                val city = civ.cities.firstOrNull { it.name == cityName }
                    ?: return@mutateGame errorResult(unknownCityError(civ, cityName))
                val focusEnum = runCatching { CityFocus.valueOf(focus) }.getOrNull()
                    ?: return@mutateGame errorResult("Unknown focus $focus; valid: ${CityFocus.entries.joinToString { it.name }}")
                city.setCityFocus(focusEnum)
                city.reassignPopulation()
                null
            }
        }

        addTool(
            name = "adopt_policy",
            description = "Adopt a social policy, spending stored culture (or a free policy slot if available).",
            inputSchema = ToolSchema(
                properties = buildJsonObject { putJsonObject("policyName") { put("type", "string") } },
                required = listOf("policyName"),
            ),
        ) { request ->
            val policyName = request.arguments?.get("policyName")?.jsonPrimitive?.content
                ?: return@addTool errorResult("policyName required")
            mutateGame { gameInfo, civ ->
                val policy = gameInfo.ruleset.policies[policyName]
                    ?: return@mutateGame errorResult("Unknown policy $policyName; valid: ${gameInfo.ruleset.policies.keys.joinToString()}")
                if (!civ.policies.canAdoptPolicy())
                    return@mutateGame errorResult("Cannot adopt any policy right now (need ${civ.policies.getCultureNeededForNextPolicy()} culture, have ${civ.policies.storedCulture})")
                if (!civ.policies.isAdoptable(policy))
                    return@mutateGame errorResult("$policyName is not adoptable (prerequisites/era not met, or already adopted)")
                civ.policies.adopt(policy)
                null
            }
        }

        addTool(
            name = "declare_war",
            description = "Declare war on a civilization the agent has met.",
            inputSchema = ToolSchema(
                properties = buildJsonObject { putJsonObject("targetCivName") { put("type", "string") } },
                required = listOf("targetCivName"),
            ),
        ) { request ->
            val targetCivName = request.arguments?.get("targetCivName")?.jsonPrimitive?.content
                ?: return@addTool errorResult("targetCivName required")
            mutateGame { gameInfo, civ ->
                val target = gameInfo.getCivilizationOrNull(targetCivName)
                    ?: return@mutateGame errorResult(unknownCivError(gameInfo, targetCivName))
                if (target == civ) return@mutateGame errorResult("Cannot target your own civilization")
                val diplo = civ.getDiplomacyManager(target)
                    ?: return@mutateGame errorResult("Have not met $targetCivName; known civs: ${civ.getKnownCivs().joinToString { it.civName }}")
                if (!diplo.canDeclareWar())
                    return@mutateGame errorResult("Cannot declare war on $targetCivName right now (already at war, or a peace treaty is in effect)")
                diplo.declareWar()
                null
            }
        }

        addTool(
            name = "make_peace",
            description = "Immediately make mutual peace with a civilization the agent is at war with (not a negotiated peace treaty).",
            inputSchema = ToolSchema(
                properties = buildJsonObject { putJsonObject("targetCivName") { put("type", "string") } },
                required = listOf("targetCivName"),
            ),
        ) { request ->
            val targetCivName = request.arguments?.get("targetCivName")?.jsonPrimitive?.content
                ?: return@addTool errorResult("targetCivName required")
            mutateGame { gameInfo, civ ->
                val target = gameInfo.getCivilizationOrNull(targetCivName)
                    ?: return@mutateGame errorResult(unknownCivError(gameInfo, targetCivName))
                if (target == civ) return@mutateGame errorResult("Cannot target your own civilization")
                val diplo = civ.getDiplomacyManager(target)
                    ?: return@mutateGame errorResult("Have not met $targetCivName; known civs: ${civ.getKnownCivs().joinToString { it.civName }}")
                if (diplo.diplomaticStatus != DiplomaticStatus.War)
                    return@mutateGame errorResult("Not at war with $targetCivName")
                diplo.makePeace()
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

package com.unciv.app.desktop.mcp

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.trade.TradeOffer
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.ui.screens.victoryscreen.VictoryScreen
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
        // Targets for declare_war/make_peace, and context for whether it's safe to act - the agent
        // otherwise has no way to see who it knows or is at war with.
        putJsonArray("knownCivs") {
            for (other in agentCiv.getKnownCivs()) {
                val diplo = agentCiv.getDiplomacyManager(other) ?: continue
                add(buildJsonObject {
                    put("name", other.civName)
                    put("isCityState", other.isCityState)
                    put("status", diplo.diplomaticStatus.name)
                    put("relationship", diplo.relationshipLevel().name)
                    put("atWar", diplo.diplomaticStatus == DiplomaticStatus.War)
                })
            }
        }
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
                // Valid names for promote_unit - the agent otherwise has to guess.
                putJsonArray("availablePromotions") {
                    unit.promotions.getAvailablePromotions().forEach { add(JsonPrimitive(it.name)) }
                }
                // Valid (x, y) targets for attack - the agent otherwise has to probe blindly.
                if (unit.canAttack()) putJsonArray("attackableTiles") {
                    for (target in TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())) {
                        add(buildJsonObject {
                            put("x", target.tileToAttack.position.x)
                            put("y", target.tileToAttack.position.y)
                            put("defender", Battle.getMapCombatantOfTile(target.tileToAttack)?.getName())
                        })
                    }
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

    /**
     * [sinceLastTurn], if given, replaces the last-[sinceTurns] window with "everything logged
     * after that turn" (the MCP server tracks the cutoff per connection - see UncivMcpServer's
     * GameConnection.lastGistTurn) and adds a [RankingType] scalar-delta summary from
     * [Civilization.statsHistory], so the agent gets a turn-start gist instead of re-exploring.
     */
    fun events(agentCiv: Civilization, gameInfo: GameInfo, sinceTurns: Int, sinceLastTurn: Int? = null, chatMessages: List<Pair<String, String>> = emptyList()): JsonObject = buildJsonObject {
        putJsonArray("chat") {
            for ((civName, message) in chatMessages) add(buildJsonObject {
                put("from", civName)
                put("message", message)
            })
        }
        putJsonArray("current") { agentCiv.notifications.forEach { add(notificationToJson(it)) } }
        putJsonArray("log") {
            val buckets = if (sinceLastTurn != null) agentCiv.notificationsLog.filter { it.turn > sinceLastTurn }
                else agentCiv.notificationsLog.takeLast(sinceTurns)
            for (turnLog in buckets) {
                add(buildJsonObject {
                    put("turn", turnLog.turn)
                    putJsonArray("notifications") { turnLog.notifications.forEach { add(notificationToJson(it)) } }
                })
            }
        }
        if (sinceLastTurn != null) {
            // Best-effort: statsHistory is only snapshotted at startTurn, so either turn key can
            // be absent (e.g. the very first gist call, or an unusual gap) - just omit "deltas"
            // rather than error; don't "fix" this by throwing on a missing entry.
            val now = agentCiv.statsHistory[gameInfo.turns]
            val then = agentCiv.statsHistory[sinceLastTurn]
            if (now != null && then != null) putJsonObject("deltas") {
                for (type in listOf(RankingType.Gold, RankingType.Technologies, RankingType.Territory,
                        RankingType.Force, RankingType.Population, RankingType.Score)) {
                    put(type.name, (now[type] ?: 0) - (then[type] ?: 0))
                }
            }
        }
    }

    /**
     * Comparative standing a human sees via Rankings/Demographics + Global Politics.
     * Ranking values are [Civilization.statsHistory] turn-start snapshots (matching
     * VictoryScreenDemographics) and, in RESTRICTED mode, only shown when a human could
     * (VictoryScreen.canViewCivStats); unmet civs are named "unknown".
     * ponytail: spy-gated intel (enemy production/stealable tech) is never revealed - no spy model exposed.
     */
    fun civIntel(agentCiv: Civilization, gameInfo: GameInfo): JsonObject = buildJsonObject {
        val majors = gameInfo.civilizations.filter { it.isMajorCiv() && it.isAlive() }
        putJsonObject("rankings") {
            for (type in RankingType.entries) {
                putJsonArray(type.name) {
                    for (civ in majors) {
                        val canSee = visibility == VisibilityMode.FULL ||
                            VictoryScreen.canViewCivStats(gameInfo, agentCiv, civ)
                        if (!canSee && civ != agentCiv) continue
                        val known = civ == agentCiv || visibility == VisibilityMode.FULL || agentCiv.knows(civ)
                        add(buildJsonObject {
                            put("civ", if (known) civ.civName else "unknown")
                            put("value", statSnapshot(civ, type, gameInfo))
                        })
                    }
                }
            }
        }
        putJsonArray("knownCivs") {
            val others = if (visibility == VisibilityMode.FULL) majors.filter { it != agentCiv }
                else agentCiv.getKnownCivs().filter { it.isMajorCiv() }.toList()
            for (other in others) {
                add(buildJsonObject {
                    put("name", other.civName)
                    put("era", other.tech.era.name)
                    put("score", other.calculateTotalScore())
                    putJsonArray("atWarWith") {
                        for (third in other.getKnownCivs())
                            if ((visibility == VisibilityMode.FULL || agentCiv.knows(third)) && other.isAtWarWith(third))
                                add(JsonPrimitive(third.civName))
                    }
                })
            }
        }
    }

    /** Trade requests and popup alerts a human would see and answer via the respond_to_trade/resolve_alert tools. */
    fun pendingDecisions(agentCiv: Civilization): JsonObject = buildJsonObject {
        putJsonArray("tradeRequests") {
            for (request in agentCiv.tradeRequests) {
                add(buildJsonObject {
                    put("requestingCiv", agentCiv.gameInfo.getCivilizationOrNull(request.requestingCiv)?.civName ?: request.requestingCiv)
                    // Naming from TradeRequest's own doc: "their" offers are what's offered to us, "our" offers are what's wanted from us.
                    putJsonArray("offeredToUs") { request.trade.theirOffers.forEach { add(tradeOfferJson(it)) } }
                    putJsonArray("wantedFromUs") { request.trade.ourOffers.forEach { add(tradeOfferJson(it)) } }
                })
            }
        }
        putJsonArray("alerts") {
            agentCiv.popupAlerts.forEachIndexed { index, alert ->
                add(buildJsonObject {
                    put("index", index)
                    put("type", alert.type.name)
                    put("value", alert.value)
                })
            }
        }
    }

    /** Turn-start snapshot for [type], falling back to latest snapshot then live (as demographics does). */
    private fun statSnapshot(civ: Civilization, type: RankingType, gameInfo: GameInfo): Int {
        val snap = civ.statsHistory[gameInfo.turns] ?: civ.statsHistory.maxByOrNull { it.key }?.value
        return snap?.get(type) ?: civ.getStatForRanking(type)
    }

    private fun tileToJson(tile: Tile, agentCiv: Civilization): JsonObject? {
        if (visibility == VisibilityMode.RESTRICTED && !tile.isExplored(agentCiv)) return null
        val visible = visibility == VisibilityMode.FULL || tile.isVisible(agentCiv)
        return buildJsonObject {
            put("x", tile.position.x)
            put("y", tile.position.y)
            put("baseTerrain", tile.baseTerrain)
            tile.getShownImprovement(agentCiv)?.let { put("improvement", it) }
            put("visible", visible)
            // A human remembers enemy cities they've seen even under fog: name, pop, religion, capital,
            // owner, defensive strength (CityButton.update / CityTable). Health only when the tile is
            // actively visible; production/buildings are spy-gated, so never shown here.
            val city = tile.getCity()
            if (city != null && tile.isCityCenter()) putJsonObject("city") {
                put("name", city.name)
                put("owner", city.civ.civName)
                put("population", city.population.population)
                put("isCapital", city.isCapital())
                put("majorityReligion", city.religion.getMajorityReligionName())
                put("defensiveStrength", com.unciv.logic.battle.CityCombatant(city).getDefendingStrength(null))
                if (visible) put("health", city.health)
            }
            if (visible) {
                tile.resource?.let { put("resource", it) }
                city?.let { put("owningCity", it.name) }
                tile.civilianUnit?.let { put("civilianUnit", it.name) }
                tile.militaryUnit?.let { put("militaryUnit", it.name) }
            }
        }
    }

    /**
     * [UnitActions.getUnitActions] enumerates every action provider - addEscortAction/
     * addSwapAction (the two that used to call [com.unciv.GUI.getWorldScreen] eagerly during
     * enumeration) now guard on [com.unciv.GUI.isWorldLoaded], so this is headless-safe.
     */
    private fun availableActions(unit: MapUnit): List<String> =
        UnitActions.getUnitActions(unit).filter { it.action != null }.map { it.type.name }.distinct().toList()

    private fun notificationToJson(notification: Notification): JsonElement = buildJsonObject {
        put("text", notification.text)
        put("category", notification.category.name)
    }

    private fun positionOf(tile: Tile): JsonObject = buildJsonObject {
        put("x", tile.position.x)
        put("y", tile.position.y)
    }
}

/** Shared by [GameStateView.pendingDecisions] and UncivMcpServer's get_trade_options tool. */
fun tradeOfferJson(offer: TradeOffer): JsonObject = buildJsonObject {
    put("type", offer.type.name)
    put("name", offer.name)
    put("amount", offer.amount)
}

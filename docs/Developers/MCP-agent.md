# The LLM counterparty MCP agent

`desktop/src/com/unciv/app/desktop/mcp/` hosts a headless Unciv that speaks the
[Model Context Protocol](https://modelcontextprotocol.io) over stdio. It occupies a **human**
slot in a multiplayer game, so an LLM client can play as a counterparty to an actual human
player - trading, fighting, chatting - rather than as a scripted AI civ.

`McpAgentLauncher.kt` boots the engine the same way `ConsoleLauncher` does (load rulesets,
tilesets, skins), then hands a `StdioServerTransport` to `UncivMcpServer`. **Nothing may reach
stdout except MCP JSON-RPC traffic** - all Unciv logging is routed to stderr via `Log`. If you
add a `println` anywhere on this path, you will corrupt the protocol stream; use `Log.debug`
instead.

## Setup

1. Run an `UncivServer` and start (or open) a multiplayer game with a second **human** slot for
   the agent to occupy. Note that slot's `playerId` and the server password.
2. Wire up a client - see below. All three configs in this repo call
   `desktop/mcp-agent.sh`, which works around one local quirk: Gradle can't configure the
   `:android` module on JDK 24+, so the script prefers a JDK 17 via `/usr/libexec/java_home` if
   `JAVA_HOME` isn't already set. On Linux, or if you already export `JAVA_HOME`, it's a no-op.
3. Call `connect_game` with the server URL, game ID, the agent's `playerId`/password, and
   optionally `civName` (auto-detected from `playerId` if omitted). Everything else needs a live
   connection first.

Verified by `./gradlew :tests:test` (`tests/src/com/unciv/logic/unit/UnitActionsHeadlessTest.kt`
covers headless action enumeration and combat targeting) and by running the agent directly:
`./desktop/mcp-agent.sh < /dev/null` should produce no stdout output.

### Claude Code

Already configured via the repo's `.mcp.json`:
```json
{
  "mcpServers": {
    "unciv": { "command": "./desktop/mcp-agent.sh" }
  }
}
```

### opencode

Already configured via the repo's `opencode.json`.

### Codex

Codex only reads MCP servers from the global `~/.codex/config.toml`, so there's nothing to
commit - add this yourself, with an absolute path (Codex's working directory isn't the repo):
```toml
[mcp_servers.unciv]
command = "/absolute/path/to/Unciv/desktop/mcp-agent.sh"
args = []
env = {}
```

## Tool reference

**Connection & turn**
- `connect_game` - connect as the agent's civ. Call first.
- `get_turn_status` - poll whether it's currently the agent's turn.
- `end_turn` - end the turn, auto-playing any AI civs until the next human's turn.

**Reading state**
- `get_my_civ` - gold, income, research, policies, counts, known civs.
- `get_cities` - population, health, production queue per city.
- `get_units` - position, health, movement, available actions, promotions, attackable tiles.
- `get_map` - the map, filtered by visibility mode; pass `centerX`/`centerY`/`radius` on large maps.
- `get_events` - notifications and chat since last call, or a turn-start gist via `sinceMyLastTurn`.
- `get_chat` - the full chat log.
- `get_civ_intel` - comparative rankings and known-civ diplomatic state.

**Cities & economy**
- `set_research` - replace the tech queue.
- `set_city_production` / `modify_production_queue` - queue and reorder construction.
- `purchase_construction` - buy with Gold or Faith.
- `set_city_focus` - citizen-management focus, reassigns tiles immediately.
- `adopt_policy` - adopt a social policy.

**Units & combat**
- `move_unit` - multi-turn pathing towards a tile.
- `attack` - attack an enemy unit/city, moving into range first.
- `bombard` - city bombardment of the strongest reachable target.
- `unit_action` - invoke any other named `UnitActionType` (Fortify, Explore, Upgrade, ...).
- `promote_unit` - apply a promotion (not reachable via `unit_action`, see below).

**Diplomacy & trade**
- `declare_war` / `make_peace`
- `get_pending_decisions` - trade requests and popup alerts awaiting a response.
- `get_trade_options` - what can be offered to / requested from a civ.
- `propose_trade` / `respond_to_trade`
- `resolve_alert` - dismiss a popup alert, or answer a demand / declaration of friendship.

**Chat**
- `send_chat` - message the human player.

## How it behaves

- **One download, one upload per turn.** `UncivMcpServer.holdGame` downloads `GameInfo` once and
  holds it in memory for the rest of the agent's turn; every read and mutation after that uses
  the held copy. `end_turn` is the only tool that uploads. If the process dies mid-turn, the
  server still has the pre-turn state - the failure mode is "the agent didn't move," never a
  corrupted save.
- **Two visibility modes** (`GameStateView.VisibilityMode`, set via `connect_game`'s
  `visibilityMode`): `FULL` returns the whole game state; `RESTRICTED` filters tiles and units
  through fog of war exactly as the human client would.
- **Errors are recoverable without a round trip.** Unknown-name errors (city, unit, civ, ...)
  list the valid names/ids in the same response, so the agent doesn't need a follow-up read.

## Not implemented

- **Conquered-city disposition.** `resolve_alert` dismisses `CityConquered`/`CityTraded`/
  `DiplomaticMarriage` alerts without choosing annex/puppet/raze/liberate
  (compare `AlertPopup.addCityConquered`). This is the biggest gap: an agent that takes a city
  gets whatever state the dismiss leaves it in.
- Espionage and spies.
- City-state gifts, quests, and bullying.
- Manual work-tile assignment and tile purchase - only `set_city_focus`'s automatic reassignment.
- Religion: founding, belief selection, and the free-tech/free-policy choice popups.
- Nukes and air interception - `attack` covers neither.
- Victory-condition progress reporting.
- `EscortFormation`/`StopEscortFormation`/`SwapUnits` - deliberately inert headless, since they
  depend on which units are selected in the (nonexistent) world screen
  (`UnitActions.addEscortAction`/`addSwapAction`).
- `Promote` via `unit_action` is rejected on purpose - use `promote_unit`, which needs a
  promotion name `unit_action` has no way to pass.
- `get_map` with no `centerX`/`centerY` returns the entire map - several thousand tiles on a
  standard-size game. Prefer the radius-limited form unless you actually need it all.

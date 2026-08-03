package com.unciv.app.desktop.mcp

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.unciv.UncivGame
import com.unciv.app.desktop.DesktopLogBackend
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.utils.Log
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.PrintStream

/**
 * Headless boot for the LLM counterparty MCP agent - mirrors [com.unciv.app.desktop.ConsoleLauncher]'s
 * cache-loading sequence, but hosts an MCP server over stdio instead of running a simulation.
 *
 * Must not print anything to stdout: this process speaks MCP JSON-RPC there. All Unciv logging
 * goes through [Log], routed to stderr by [DesktopLogBackend].
 */
object McpAgentLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        Log.backend = DesktopLogBackend()

        UncivGame.Current = UncivGame(true)
        UncivGame.Current.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 10000
        }

        // Loading logs go through println (e.g. RulesetCache's "Loaded N rulesets" banner);
        // stdout is reserved for MCP JSON-RPC once the transport connects, so redirect it here.
        val realStdout = System.out
        System.setOut(PrintStream(object : java.io.OutputStream() { override fun write(b: Int) {} }))
        try {
            RulesetCache.loadRulesets(consoleMode = true)
            TileSetCache.loadTileSetConfigs(consoleMode = true)
            SkinCache.loadSkinConfigs(consoleMode = true)
        } finally {
            System.setOut(realStdout)
        }
        UncivServerFileStorage.timeout = 30000

        // Chat (ChatStore.relayGlobalMessage etc.) calls Gdx.app.postRunnable, and
        // Multiplayer()'s constructor needs Gdx.files (via UncivGame.Current.files) to list
        // local save files - neither is set up by isConsoleMode=true. HeadlessApplication sets
        // Gdx.app/files/net/audio/graphics/input to real (mock, where relevant) implementations;
        // this is the same setup GdxTestRunner.kt uses for headless JUnit tests.
        HeadlessApplication(object : ApplicationAdapter() {})
        UncivGame.Current.files = com.unciv.logic.files.UncivFiles(Gdx.files)
        // Multiplayer's background game-list refresher reads UncivGame.Current.gameInfo, which we
        // never set (we always operate on freshly downloaded GameInfo instances), so it's a no-op.
        UncivGame.Current.onlineMultiplayer = Multiplayer()

        val mcpServer = UncivMcpServer()
        val transport = StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered(),
        )

        runBlocking {
            val session = mcpServer.server.createSession(transport)
            val done = Job()
            session.onClose { done.complete() }
            done.join()
        }
    }
}

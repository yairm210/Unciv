package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Latency regression tool for GameInfo.nextTurn().
 *
 * Provide a save file via system property:
 *   -Dunciv.nextTurnSaveFile=/absolute/path/to/save
 *
 * Run locally before and after changes to compare wall-clock time.
 * Not a CI gate — results vary by machine.
 *
 * Typical invocation:
 *   JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew tests:test \
 *     --tests "com.unciv.logic.NextTurnLatencyTest.nextTurnLatency" \
 *     -Dunciv.nextTurnSaveFile=/path/to/MySave
 *     
 *  Written by golems, for golems
 */
@RunWith(GdxTestRunner::class)
class NextTurnLatencyTest {

    companion object {
        private const val SAVE_FILE_PROPERTY = "unciv.nextTurnSaveFile"

        @BeforeClass @JvmStatic
        fun setup() {
            UncivGame.Current = UncivGame()
            UncivGame.Current.settings = GameSettings()
            UncivGame.Current.files = UncivFiles(Gdx.files)
            RulesetCache.loadRulesets(noMods = false)
        }
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun nextTurnLatency() {
        val savePath = System.getProperty(SAVE_FILE_PROPERTY)
        Assume.assumeTrue(
            "Set -D$SAVE_FILE_PROPERTY=/path/to/save to run this test",
            savePath != null
        )

        val saveFile = File(savePath!!)
        require(saveFile.exists()) { "Save file not found: $savePath" }

        fun loadGame(): GameInfo {
            val data = saveFile.readText(Charsets.UTF_8)
            return UncivFiles.gameInfoFromString(data)
        }

        // Warmup: let JIT compile hot paths
        println("=== NextTurn latency — warming up (3 runs) ===")
        for (w in 0 until 3) {
            val game = loadGame()
            game.nextTurn()
        }

        val runs = 5
        val times = LongArray(runs)
        println("=== NextTurn latency — measuring ($runs runs) ===")
        for (i in 0 until runs) {
            val game = loadGame()
            val t0 = System.nanoTime()
            game.nextTurn()
            times[i] = System.nanoTime() - t0
        }

        val ms = times.map { it / 1_000_000L }
        ms.forEachIndexed { i, t -> println("  Run ${i + 1}: $t ms") }
        println("  Min:    ${ms.min()} ms")
        println("  Median: ${ms.sorted()[runs / 2]} ms")
        println("  Max:    ${ms.max()} ms")
    }
}

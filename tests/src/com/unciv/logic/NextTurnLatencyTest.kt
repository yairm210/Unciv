package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.automation.Timers
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
 * Latency regression tool for [com.unciv.logic.GameInfo.nextTurn].
 *
 * ## Purpose
 * Measures wall-clock time for one full AI turn cycle: all civs from current human player back
 * to the next human player. Use this to baseline before a change, apply the change, run again,
 * compare. Not a CI gate — results vary by machine and JIT warmup.
 *
 * ## How to run
 *
 * 1. Pick a save file. Vanilla saves work out of the box. For mod saves, mods must be present
 *    in android/assets/mods/ — saves referencing missing mods will throw [MissingModsException].
 *    Good candidates: android/assets/SaveFiles/Autosave (large late-game, no mods).
 *
 * 2. Run via Gradle, passing the save path as a system property:
 *    ```
 *    JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :tests:test \
 *      --tests "com.unciv.logic.NextTurnLatencyTest.nextTurnLatency" \
 *      -Dunciv.nextTurnSaveFile=/absolute/path/to/save
 *    ```
 *    Without the property the test is silently skipped (safe in CI).
 *
 * ## Reading the output
 * The test prints per-run milliseconds plus min/median/max.
 * Compare median between runs; ignore outliers (GC, OS scheduling).
 * Example:
 * ```
 *   Run 1: 2043 ms
 *   Run 2: 1982 ms
 *   ...
 *   Min:    1945 ms
 *   Median: 1963 ms    <-- use this for comparison
 *   Max:    2043 ms
 * ```
 *
 * ## Workflow for profiling a change
 * 1. Run baseline, note median.
 * 2. Apply code change.
 * 3. Run again, note median.
 * 4. Difference = improvement (or regression).
 *
 * Written by golems, for golems.
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
        repeat(3) {
            loadGame().nextTurn()
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

        // Span breakdown: one extra run with Timers enabled to see where time is spent
        println("=== NextTurn latency — span breakdown (1 instrumented run) ===")
        Timers.singleton.startTiming()
        loadGame().nextTurn()
        Timers.singleton.endTiming()
    }
}

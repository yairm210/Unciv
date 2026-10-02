package com.unciv.dev

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessFiles
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.MapSaver
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.screens.LanguagePickerScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.Display
import com.unciv.utils.PlatformDisplay
import kotlin.system.exitProcess
import java.io.File
import java.util.Locale

/**
 * Turn-speed measurement for [Objective Judge Horizon](https://github.com/Pr1nted/objective-judge-horizon),
 * a benchmark that measures turn-based strategy games the same way on the same
 * machine.
 *
 * This is a developer tool, the same kind of thing as [FasterUIDevelopment] and
 * [com.unciv.logic.NextTurnLatencyTest] -- it lives in `tests/`, nothing in
 * `core/` or `desktop/` refers to it, and no build ships it. Deleting this one
 * file removes it completely; OJH keeps an outside driver and falls back to it.
 *
 * ## Why it is here rather than outside
 *
 * OJH can already drive Unciv from outside, and does. But Java cannot see
 * Kotlin's default arguments, so from outside the only way to start a turn is
 * the generated `GameInfo.nextTurn$default` -- a compiler artifact, not API.
 * From here it is `gameInfo.nextTurn()`. A signature change breaks the outside
 * driver silently, at runtime, after the benchmark has reported a number;
 * it breaks this file at compile time, in Unciv's own `./gradlew check`.
 *
 * It also takes `--map`, so Unciv can be measured on a map converted from
 * another game rather than on a generated world of about the right size. That
 * is the difference between "a 63-player large map" and "the same world every
 * other game in the comparison played".
 *
 * ## Running it
 *
 * ```
 * ./gradlew :tests:ojhTurns -Pojh.args="--turns 250 --players 63 --map /path/map.json"
 * ```
 *
 * or directly, with the tests classpath:
 *
 * ```
 * java -cp <tests runtime classpath> com.unciv.dev.OjhBenchmark --turns 250 --players 8
 * ```
 *
 * ## Output
 *
 * OJH's line protocol on stdout, one line per fact, everything else on stderr:
 *
 * ```
 * OJH players <n>
 * OJH regions <n> tiles
 * OJH ready                       the game is loaded; turn 1 starts next
 * OJH turn <n> <seconds>
 * ```
 */
object OjhBenchmark {

    private fun say(line: String) = println(line)
    private fun note(line: String) = System.err.println(line)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()
            ?: throw IllegalArgumentException("the first argument is the mode: turns or fps")
        require(mode == "turns" || mode == "fps") { "unknown mode $mode" }
        var turns = 100
        var seconds = 5.0
        var lateTurns = 20
        var players = 8
        var size = "small"
        var mapPath: String? = null
        var seed: Long = 0

        var i = 1
        while (i < args.size) {
            val value = { args.getOrNull(i + 1) ?: throw IllegalArgumentException("${args[i]} needs a value") }
            when (args[i]) {
                "--turns" -> { turns = value().toInt(); i++ }
                "--players" -> { players = value().toInt(); i++ }
                "--size" -> { size = value(); i++ }
                "--map" -> { mapPath = value(); i++ }
                "--seed" -> { seed = value().toLong(); i++ }
                "--seconds" -> { seconds = value().toDouble(); i++ }
                "--late-turns" -> { lateTurns = value().toInt(); i++ }
                else -> throw IllegalArgumentException("unknown argument ${args[i]}")
            }
            i++
        }
        require(turns > 0) { "--turns must be at least 1" }
        require(players >= 2) { "--players must be at least 2" }

        if (mode == "fps") {
            fps(players, size, mapPath, seed, seconds, lateTurns)
            return
        }

        // Gdx.files only, not a HeadlessApplication: that starts a render loop
        // on a non-daemon thread, so the JVM never exits once the turns are
        // done. A turn needs files and nothing else.
        Gdx.files = HeadlessFiles()

        val game = UncivGame(true)
        UncivGame.Current = game
        game.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 1_000_000    // autosaving is not part of a turn
        }
        game.files = UncivFiles(Gdx.files)
        RulesetCache.loadRulesets(noMods = true)

        val bootStart = System.nanoTime()
        val info = start(players, size, mapPath, seed)
        val bootSeconds = (System.nanoTime() - bootStart) / 1e9

        say("OJH players ${info.civilizations.count { it.playerType == PlayerType.AI && !it.isBarbarian }}")
        say("OJH regions ${info.tileMap.values.size} tiles")
        note(String.format(Locale.ROOT, "loaded in %.3f s", bootSeconds))
        say("OJH ready")

        for (turn in 1..turns) {
            val t0 = System.nanoTime()
            info.nextTurn()
            val seconds = (System.nanoTime() - t0) / 1e9
            say(String.format(Locale.ROOT, "OJH turn %d %.6f", turn, seconds))
        }

        // Unciv's own Concurrency pool is not daemon, so without this the JVM
        // sits for a minute after the last turn with nothing left to do.
        System.out.flush()
        kotlin.system.exitProcess(0)
    }

    /**
     * A game played entirely by the game's own AI, on `mapPath` when one is given.
     *
     * The settings are the ones OJH's outside driver already uses, so a number
     * from here is comparable with every number it has published: King, Quick,
     * no barbarians, player order unshuffled, and the ruleset's major
     * civilizations as AI. Unciv requires a human -- `setTransients` does
     * `civilizations.first { it.isHuman() }` -- so a Spectator is that human.
     * It takes no turn, so one nextTurn() is still one full round of AI.
     * Players asked for above the number of major civilizations become city
     * states, which is what the outside driver does too.
     */
    private fun start(players: Int, size: String, mapPath: String?, seed: Long): GameInfo {
        val setup = GameSetupInfo()
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!

        val chosen = ArrayList<Player>()
        for (nation in ruleset.nations.values) {
            if (chosen.size >= players) break
            if (!nation.isMajorCiv) continue
            chosen.add(Player(nation.name, PlayerType.AI))
        }
        val cityStates = (players - chosen.size).coerceAtLeast(0)
        chosen.add(Player(Constants.spectator, PlayerType.Human))

        setup.gameParameters.apply {
            baseRuleset = ruleset.name
            difficulty = "King"
            speed = "Quick"
            noBarbarians = true
            numberOfCityStates = cityStates
            shufflePlayerOrder = false
            this.players = chosen
        }
        note("${chosen.size - 1} AI civilizations, $cityStates city states, 1 spectator")

        if (mapPath != null) {
            val file = File(mapPath)
            require(file.isFile) { "no map at $mapPath" }
            // Any map Unciv's own editor can open, including one converted from
            // another game, so every game in a comparison plays one world.
            setup.mapFile = Gdx.files.absolute(file.absolutePath)
            setup.mapParameters.type = MapGeneratedMainType.custom
            setup.mapParameters.name = file.name
            val preview = MapSaver.loadMapParameters(setup.mapFile!!)
            note("map ${file.name}: ${preview.mapSize.width}x${preview.mapSize.height}, ${preview.shape}")
        } else {
            setup.mapParameters.type = MapType.pangaea
            setup.mapParameters.mapSize = when (size) {
                "tiny" -> MapSize.Tiny
                "small" -> MapSize.Small
                "medium" -> MapSize.Medium
                "large" -> MapSize.Large
                "huge" -> MapSize.Huge
                else -> throw IllegalArgumentException("unknown --size $size")
            }
            if (seed != 0L) setup.mapParameters.seed = seed
        }

        return GameStarter.startNewGame(setup)
    }

    // ------------------------------------------------------------------ fps

    /**
     * Frame rate, in the real window.
     *
     * The outside driver does this too, and does it well -- but it reaches the
     * research screen by trying constructors by reflection until one of them
     * takes, because from outside there is no other way to open a screen. From
     * here it is `TechPickerScreen(civ)`, so the heaviest panel is the panel it
     * actually measures rather than whichever one happened to construct.
     */
    private fun fps(players: Int, size: String, mapPath: String?, seed: Long,
                    seconds: Double, lateTurns: Int) {
        // UncivGame.create asks Display for the screen mode, and the desktop
        // launcher is what normally sets it. Every method on the interface has
        // a default, so an empty one is a complete one: this is measuring frame
        // rate in a window, not exercising screen modes.
        Display.platform = object : PlatformDisplay {}

        val config = Lwjgl3ApplicationConfiguration()
        config.setTitle("Unciv - OJH")
        config.setWindowedMode(1600, 900)
        config.useVsync(false)            // a cap is a measurement of the cap
        config.setForegroundFPS(0)
        // Without this the window stops rendering the moment anything else is
        // clicked, and a frame rate measured on a paused window is zero.
        config.setPauseWhenLostFocus(false)
        // macOS gives a covered window about one frame a second, so it has to
        // be in front of whatever else is on screen.
        Lwjgl3Application(FpsRun(players, size, mapPath, seed, seconds, lateTurns), config)
    }

    private class FpsRun(
        private val players: Int,
        private val size: String,
        private val mapPath: String?,
        private val seed: Long,
        private val seconds: Double,
        private val lateTurns: Int
    ) : UncivGame(false) {

        private var stage = 0
        private var last = 0L
        private var sceneName: String? = null
        private var sceneEnd = 0L
        private var warmup = 0
        private val frames = ArrayList<Double>()
        private val reported = HashSet<String>()
        private var started: GameInfo? = null
        private var lateGame: GameInfo? = null
        private var turnsDone = false
        private var failure: String? = null

        private fun begin(name: String, warmupFrames: Int) {
            sceneName = name
            frames.clear()
            warmup = warmupFrames
            sceneEnd = 0
        }

        private fun elapsed(now: Long): Boolean {
            if (sceneEnd == 0L) sceneEnd = now + (seconds * 1e9).toLong()
            return now >= sceneEnd
        }

        private fun report() {
            val name = sceneName ?: return
            sceneName = null
            if (frames.size < 10) {
                OjhBenchmark.say("OJH noscene $name too few frames were drawn to time")
                reported.add(name)
                return
            }
            val sorted = frames.sorted()
            val total = frames.sum()
            fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()] * 1000.0
            // The 1% low is the mean of the slowest hundredth, which is what a
            // player feels as a stutter -- not the single worst frame.
            val worst = sorted.subList((sorted.size * 99) / 100, sorted.size)
            val low = if (worst.isEmpty()) 0.0 else 1.0 / (worst.sum() / worst.size)
            OjhBenchmark.say(String.format(Locale.ROOT, "OJH scene %s %d %.5f %.3f %.3f %.3f %.2f",
                name, frames.size, total, pct(0.50), pct(0.95), pct(0.99), low))
            reported.add(name)
        }

        private fun holder() = (screen as WorldScreen).mapHolder

        private fun load(info: GameInfo) {
            Concurrency.run("OJH load") { loadGame(info) }
        }

        override fun create() {
            // What DesktopLauncher does before the game starts, and what
            // FasterUIDevelopment does for the same reason: the real font
            // rasteriser, because a stub one would be measuring a different
            // thing being drawn.
            Fonts.fontImplementation = FontDesktop()
            super.create()
        }

        override fun render() {
            val now = System.nanoTime()
            super.render()
            if (sceneName != null && last != 0L) {
                if (warmup > 0) warmup-- else frames.add((now - last) / 1e9)
            }
            last = now
            if (failure != null) return finish(failure)
            advance(now)
        }

        private fun advance(now: Long) {
            when (stage) {
                0 -> {
                    if (screen is LanguagePickerScreen) {
                        settings.language = "English"
                        settings.updateLocaleFromLanguage()
                        settings.isFreshlyCreated = false
                        goToMainMenu()
                        return
                    }
                    if (screen is MainMenuScreen) {
                        // macOS gives a covered or unfocused window about one
                        // frame a second, so the measurement has to happen with
                        // the window in front. This TAKES OVER THE SCREEN for
                        // the length of the run.
                        (Gdx.graphics as Lwjgl3Graphics).window.focusWindow()
                        val gl = Gdx.graphics.glVersion
                        OjhBenchmark.say("OJH renderer ${gl.rendererString}, OpenGL "
                            + "${gl.majorVersion}.${gl.minorVersion} via LWJGL3")
                        OjhBenchmark.say("OJH resolution ${Gdx.graphics.backBufferWidth}x${Gdx.graphics.backBufferHeight}")
                        OjhBenchmark.say("OJH vsync off")
                        begin("menu", 60)
                        stage = 1
                    }
                }
                1 -> if (elapsed(now)) {
                    report()
                    started = OjhBenchmark.start(players, size, mapPath, seed)
                    load(started!!)
                    stage = 2
                }
                2 -> if (screen is WorldScreen) { begin("map-start", 90); stage = 3 }
                3 -> if (elapsed(now)) {
                    report(); holder().zoom(holder().minZoom); begin("map-out", 60); stage = 4
                }
                4 -> if (elapsed(now)) {
                    report(); holder().zoom(holder().maxZoom); begin("map-in", 60); stage = 5
                }
                5 -> if (elapsed(now)) {
                    report(); holder().zoom(1f); begin("map-pan", 60); stage = 6
                }
                6 -> {
                    val h = holder()
                    h.scrollX += 6f
                    h.updateVisualScroll()
                    if (elapsed(now)) {
                        report()
                        val world = screen as WorldScreen
                        val civ = started!!.civilizations.firstOrNull { it.playerType == PlayerType.Human }
                        if (civ == null) {
                            OjhBenchmark.say("OJH noscene panel the game has no human civilization to open it for")
                            reported.add("panel")
                            stage = 8
                        } else {
                            // The research tree, by name: it is the heaviest
                            // information screen this game has.
                            replaceCurrentScreen { TechPickerScreen(civ) }
                            begin("panel", 60)
                            lateGame = null
                            panelReturn = world
                            stage = 7
                        }
                    }
                }
                7 -> if (elapsed(now)) {
                    report(); replaceCurrentScreen { panelReturn!! }; stage = 8
                }
                8 -> {
                    val clone = started!!.clone()
                    clone.setTransients()
                    Concurrency.run("OJH turns") {
                        try {
                            repeat(lateTurns) { clone.nextTurn() }
                            lateGame = clone
                            turnsDone = true
                        } catch (e: Throwable) {
                            failure = "playing turns failed: $e"
                        }
                    }
                    begin("end-turn", 10)
                    stage = 9
                }
                9 -> if (turnsDone) { report(); load(lateGame!!); stage = 10 }
                10 -> if (screen is WorldScreen) { begin("map-late", 90); stage = 11 }
                11 -> if (elapsed(now)) { report(); finish(null) }
            }
        }

        /** The world screen to come back to after the panel scene. */
        private var panelReturn: WorldScreen? = null

        private fun finish(why: String?) {
            for (scene in scenesOf()) {
                if (!reported.contains(scene))
                    OjhBenchmark.say("OJH noscene $scene ${why ?: "not reached"}")
            }
            System.out.flush()
            exitProcess(0)
        }

        /** Every scene OJH times. One a game has not got is `noscene`, never zero. */
        private fun scenesOf() = listOf(
            "menu", "map-start", "map-out", "map-in", "map-pan", "panel", "end-turn", "map-late")
    }

}

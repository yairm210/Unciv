package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.utils.Log
import com.unciv.utils.debug
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class GameSerializationTests {

    private var game = GameInfo()
    private var settingsBackup = GameSettings()

    /** A runtime Class object for [kotlin.SynchronizedLazyImpl] to enable helping Gdx.Json to
     * not StackOverflow on them, as a direct compile time retrieval is forbidden */
    private val classSynchronizedLazyImpl: Class<*> by lazy {
        // I hope you get the irony...
        @Suppress("unused") // No, test is not _directly_ used, only reflected on
        class TestWithLazy { val test: Int by lazy { 0 } }
        val badInstance = TestWithLazy()
        val badField = badInstance::class.java.declaredFields[0]
        badField.isAccessible = true
        badField.get(badInstance)::class.java
    }

    @Before
    fun prepareGame() {
        RulesetCache.loadRulesets(noMods = true)

        // Create a tiny game with just 1 human player and the barbarians
        // Must be 1 human otherwise GameInfo.setTransients crashes on the `if (currentPlayer == "")` line
        val param = GameParameters().apply {
            numberOfCityStates = 0
            players.clear()
            players.add(Player("Rome", PlayerType.Human))
            players.add(Player("Greece"))
        }
        val mapParameters = MapParameters().apply {
            mapSize = MapSizeNew(MapSize.Tiny)
            seed = 42L
        }
        val setup = GameSetupInfo(param, mapParameters)
        UncivGame.Current = UncivGame()
        UncivGame.Current.files = UncivFiles(Gdx.files)

        // Both startNewGame and makeCivilizationsMeet will cause a save to storage of our empty settings
        settingsBackup = UncivGame.Current.files.getGeneralSettings()

        UncivGame.Current.settings = GameSettings()
        game = GameStarter.startNewGame(setup)
        UncivGame.Current.startSimulation(game)

        // Found a city otherwise too many classes have no instance and are not tested
        val civ = game.getCurrentPlayerCivilization()
        val unit = civ.units.getCivUnits().first { it.hasUnique(UniqueType.FoundCity) }
        val tile = unit.getTile()
        unit.civ.addCity(tile.position)
        if (tile.ruleset.tileImprovements.containsKey(Constants.cityCenter))
            tile.changeImprovement(Constants.cityCenter)
        unit.destroy()

        // Ensure some diplomacy objects are instantiated
        val otherCiv = game.getCivilization("Greece")
        civ.diplomacyFunctions.makeCivilizationsMeet(otherCiv)

        // Ensure a barbarian encampment is included
        game.barbarians.placeBarbarianEncampment(forTesting = true)
    }

    @Test
    fun canSerializeGame() {
        val json = try {
            json().toJson(game)
        } catch (_: Exception) {
            ""
        }
        Assert.assertTrue("This test will only pass when a game can be serialized", json.isNotEmpty())
    }

    @Test
    fun serializedLaziesTest() {
        val jsonSerializer = com.badlogic.gdx.utils.Json().apply {
            setIgnoreDeprecated(true)
            setDeprecated(classSynchronizedLazyImpl, "initializer", true)
            setDeprecated(classSynchronizedLazyImpl, "lock", true)  // this is the culprit as kotlin initializes it to `this@SynchronizedLazyImpl`
        }

        val json = try {
            jsonSerializer.toJson(game)
        } catch (ex: Throwable) {
            Log.error("Failed to serialize game", ex)
            return
        }

        val pattern = """\{(\w+)\\${'$'}delegate:\{class:kotlin.SynchronizedLazyImpl,"""
        val matches = Regex(pattern).findAll(json)
        matches.forEach {
            debug("Lazy missing `@delegate:Transient` annotation: %s", it.groups[1]!!.value)
        }
        val result = matches.any()
        Assert.assertFalse("This test will only pass when no serializable lazy fields are found", result)
    }

    @After
    fun cleanup() {
        settingsBackup.save()
    }
}

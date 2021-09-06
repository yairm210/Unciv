package com.unciv.testing

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.metadata.GameSetupInfo
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class SerializationTests {

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
            players.add(Player("Rome").apply { playerType = PlayerType.Human })
            players.add(Player("Greece"))
            religionEnabled = true
            startingEra = "Ancient era"
        }
        val mapParameters = MapParameters().apply {
            mapSize = MapSizeNew(MapSize.Tiny)
            seed = 42L
        }
        val setup = GameSetupInfo(param, mapParameters)
        UncivGame.Current = UncivGame("")

        // Both startNewGame and makeCivilizationsMeet will cause a save to storage of our empty settings
        settingsBackup = GameSaver.getGeneralSettings()

        UncivGame.Current.settings = GameSettings()
        game = GameStarter.startNewGame(setup)
        UncivGame.Current.gameInfo = game

        // Found a city otherwise too many classes have no instance and are not tested
        val civ = game.getCurrentPlayerCivilization()
        val unit = civ.getCivUnits().first { it.hasUnique(Constants.settlerUnique) }
        val tile = unit.getTile()
        unit.civInfo.addCity(tile.position)
        if (tile.ruleset.tileImprovements.containsKey("City center"))
            tile.improvement = "City center"
        unit.destroy()

        // Ensure some diplomacy objects are instantiated
        val otherCiv = game.getCivilization("Greece")
        civ.makeCivilizationsMeet(otherCiv)
    }

    @Test
    fun canSerializeGame() {
        val json = try {
            GameSaver.json().toJson(game)
        } catch (ex: Exception) {
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
            ex.printStackTrace()
            return
        }

        val pattern = """\{(\w+)\${'$'}delegate:\{class:kotlin.SynchronizedLazyImpl,"""
        val matches = Regex(pattern).findAll(json)
        matches.forEach {
            println("Lazy missing `@delegate:Transient` annotation: " + it.groups[1]!!.value)
        }
        val result = matches.any()
        Assert.assertFalse("This test will only pass when no serializable lazy fields are found", result)
    }

    @After
    fun cleanup() {
        settingsBackup.save()
    }
}

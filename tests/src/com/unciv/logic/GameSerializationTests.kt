package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.platform.PlatformCapabilities
import com.unciv.ui.screens.savescreens.Gzip
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
        runCatching {
            // TeaVM can block reflective private-field access; obtain the runtime impl class directly.
            class TestWithLazy { val delegate = lazy { 0 } }
            val instance = TestWithLazy()
            instance.delegate::class.java
        }.getOrElse {
            runCatching { Class.forName("kotlin.SynchronizedLazyImpl") }
                .getOrElse { Any::class.java }
        }
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
            mapSize = MapSize.Tiny
            seed = 42L
        }
        val setup = GameSetupInfo(param, mapParameters)
        UncivGame.Current = UncivGame()
        UncivGame.Current.files = UncivFiles(Gdx.files)

        // Both startNewGame and makeCivilizationsMeet will cause a save to storage of our empty settings
        settingsBackup = UncivGame.Current.files.getGeneralSettings()

        UncivGame.Current.settings = GameSettings()
        game = GameStarter.startNewGame(setup)
        UncivGame.Current.gameInfo = game

        // Found a city otherwise too many classes have no instance and are not tested
        val civ = game.getCurrentPlayerCivilization()
        val unit = civ.units.getCivUnits().first { it.hasUnique(UniqueType.FoundCity) }
        val tile = unit.getTile()
        unit.civ.addCity(tile.position)
        if (tile.ruleset.tileImprovements.containsKey(Constants.cityCenter))
            tile.setImprovement(Constants.cityCenter)
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
        val jsonSerializer = Json().apply {
            setIgnoreDeprecated(true)
            val fields = runCatching { classSynchronizedLazyImpl.declaredFields.map { it.name }.toSet() }
                .getOrDefault(emptySet())
            if ("initializer" in fields) setDeprecated(classSynchronizedLazyImpl, "initializer", true)
            if ("lock" in fields) {
                // This is the culprit as kotlin initializes it to `this@SynchronizedLazyImpl`.
                setDeprecated(classSynchronizedLazyImpl, "lock", true)
            }
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

    @Test
    fun checksumMatchesUpstreamAlgorithmWhenBackgroundPoolsExist() {
        if (Gdx.app.type == com.badlogic.gdx.Application.ApplicationType.WebGL) return
        val previousCapabilities = PlatformCapabilities.current
        try {
            PlatformCapabilities.setCurrent(PlatformCapabilities.Features())
            val expected = calculateJvmSha1Checksum(serializedBytesWithoutChecksum())
            Assert.assertEquals(expected, game.calculateChecksum())
        } finally {
            PlatformCapabilities.setCurrent(previousCapabilities)
        }
    }

    @Test
    fun checksumUsesTeavmSafeFallbackWithoutBackgroundPools() {
        val previousCapabilities = PlatformCapabilities.current
        try {
            PlatformCapabilities.setCurrent(PlatformCapabilities.webPhase4Full())
            val expected = serializedBytesWithoutChecksum().fold(0xcbf29ce484222325uL) { hash, byte ->
                (hash xor (byte.toInt() and 0xff).toULong()) * 0x100000001b3uL
            }.toString(16).padStart(16, '0')
            Assert.assertEquals(expected, game.calculateChecksum())
        } finally {
            PlatformCapabilities.setCurrent(previousCapabilities)
        }
    }

    private fun serializedBytesWithoutChecksum(): ByteArray {
        val oldChecksum = game.checksum
        game.checksum = ""
        val bytes = json().toJson(game).toByteArray(Charsets.UTF_8)
        game.checksum = oldChecksum
        return bytes
    }

    private fun calculateJvmSha1Checksum(bytes: ByteArray): String {
        val digestClass = Class.forName("java.security.MessageDigest")
        val getInstance = digestClass.getMethod("getInstance", String::class.java)
        val messageDigest = getInstance.invoke(null, "SHA-1")
        val digest = digestClass.getMethod("digest", ByteArray::class.java)
            .invoke(messageDigest, bytes) as ByteArray
        return Gzip.encode(digest)
    }

    @After
    fun cleanup() {
        settingsBackup.save()
    }
}

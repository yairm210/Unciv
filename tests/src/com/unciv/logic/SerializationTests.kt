package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivRankingHistory
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.json.LastSeenImprovement
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.tile.TileHistory
import com.unciv.models.Counter
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.KeyboardBindings
import com.unciv.ui.screens.victoryscreen.RankingType
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.temporal.ChronoUnit

@RunWith(GdxTestRunner::class)
class SerializationTests {
    private val json = com.unciv.json.json()

    // use @RedirectOutput(RedirectPolicy.Show) to see the actual json

    @Test
    fun `test DurationSerializer`() {
        val data = arrayListOf(
            // Java Duration! (even though kotlin.Duration is perfectly fine - all the multiplayer code is outdated in that respect)
            Duration.ZERO,
            Duration.of(666, ChronoUnit.HOURS),
            Duration.parse("P1DT2H3M4.058S"),
        )
        testRoundtrip(data, Duration::class.java)
    }

    @Test
    //@RedirectOutput(RedirectPolicy.Show)
    fun `test LastSeenImprovement serialization roundtrip`() {
        val data = LastSeenImprovement()
        data[Vector2.Zero] = "Borehole"
        data[Vector2.X] = "Smokestack"
        data[Vector2.Y] = "Waffle stand"
        testRoundtrip(data)
    }

    @Test
    fun `test KeyboardBindings serialization roundtrip`() {
        val data = KeyboardBindings()
        data[KeyboardBinding.DeveloperConsole] = KeyCharAndCode.TAB
        data[KeyboardBinding.NextTurn] = KeyCharAndCode('X')
        data[KeyboardBinding.NextTurnAlternate] = KeyCharAndCode.ctrl('X')
        data[KeyboardBinding.Menu] = KeyCharAndCode.BACK
        testRoundtrip(data)
    }

    @Test
    fun `test TileHistory serialization roundtrip`() {
        val data = TileHistory()
        data.addTestEntry(0, TileHistory.TileHistoryState("Greece", TileHistory.TileHistoryState.CityCenterType.Capital))
        data.addTestEntry(1, TileHistory.TileHistoryState(null, TileHistory.TileHistoryState.CityCenterType.None))
        testRoundtrip(data) { old, new ->
            // Neither TileHistory nor TileHistoryState support equality contract
            Assert.assertTrue(old.all {
                val oldState = it.value
                val newState = new.getState(it.key)
                oldState.owningCivName == newState.owningCivName && oldState.cityCenterType == newState.cityCenterType
            })
        }
    }

    @Test
    fun `test CivRankingHistory serialization roundtrip`() {
        val data = CivRankingHistory()
        data[0] = mapOf(RankingType.Force to 0, RankingType.Territory to 7)
        data[20] = mapOf(RankingType.Culture to 42, RankingType.Territory to 666)
        testRoundtrip(data)
    }

    @Test
    fun `test Notification serialization roundtrip`() {
        val data = arrayListOf(
            Notification("hello", emptyArray(), emptyList(), Notification.NotificationCategory.Espionage),
            Notification("Oh my goddesses", arrayOf("ReligionIcons/Pray"), listOf(CivilopediaAction("Tutorial/Religion")), Notification.NotificationCategory.Religion),
            Notification("There's Horses", arrayOf("ResourceIcons/Horses"), LocationAction(Vector2.Zero, Vector2.X).asIterable(), Notification.NotificationCategory.General),
            Notification("An evil overlord has arisen", arrayOf("PersonalityIcons/Devil"), listOf(DiplomacyAction("Russia")), Notification.NotificationCategory.War),
            Notification("Here's a Wizzard", arrayOf("EmojiIcons/Great Scientist"), listOf(MapUnitAction(Vector2.Y, 42)), Notification.NotificationCategory.Units),
        )

        // Neither Notification nor NotificationAction support equality contract
        fun Notification.isEqual(other: Notification): Boolean {
            if (text != other.text) return false
            if (category != other.category) return false
            if (icons != other.icons) return false
            val otherIterator = other.actions.iterator()
            for (action in actions) {
                if (!otherIterator.hasNext()) return false
                val otherAction = otherIterator.next()
                if (action.javaClass != otherAction.javaClass) return false
                // The lazy way to compare fields that vary from one NotificationAction subclass to the next
                if (json.toJson(action) != json.toJson(otherAction)) return false
            }
            return !otherIterator.hasNext()
        }

        testRoundtrip(data, Notification::class.java) { old, new ->
            Assert.assertTrue(old.withIndex().all { (index, notification) ->
                notification.isEqual(new[index])
            })
        }
    }

    /** Note that no other Counter<X> will pass this test */
    @Test
    fun `test Counter(String) serialization roundtrip`() {
        val data = Counter(mapOf("Foo" to 1, "Bar" to 3, "Towel" to 42))
        testRoundtrip(data)
    }

    ///////////////////////////////// Helper
    private inline fun <reified T> testRoundtrip(
        data: T,
        elementType: Class<*>? = null,
        testEquality: ((old: T, new: T)->Unit) = { old, new ->
            Assert.assertEquals(old, new)
        }
    ) {
        val serialized = json.toJson(data)
        println("Serialized form: $serialized")
        Assert.assertTrue(serialized.isNotBlank())
        val deserialized = json.fromJson(T::class.java, elementType, serialized)
        testEquality(data, deserialized)
    }
}

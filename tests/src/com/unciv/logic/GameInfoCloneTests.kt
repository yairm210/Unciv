package com.unciv.logic

import com.unciv.json.json
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.Modifier

@RunWith(BaseTestRunner::class)
class GameInfoCloneTests {

    /** Written by the save routine, not by game play - [GameInfo.clone] need not carry them. */
    private val notCloned = setOf("version", "checksum")

    /** Structured state with a `clone()` of its own (or shared by design): compared by serialized form only, not mutated here. */
    private val structured = setOf("civilizations", "barbarians", "religions", "tileMap", "gameParameters")

    private fun serializedFields(): List<Field> = GameInfo::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }
        .onEach { it.isAccessible = true }

    /** A value that differs from the field's default, so a clone that forgets to copy it is detectable. */
    private fun distinctValue(field: Field, current: Any?): Any? = when {
        field.type == Int::class.javaPrimitiveType -> 4242
        field.type == Long::class.javaPrimitiveType -> 123_456_789_012L
        field.type == Boolean::class.javaPrimitiveType -> !(current as Boolean)
        field.type == String::class.java -> "changed ${field.name}"
        field.type == VictoryData::class.java -> VictoryData("someCiv", "someVictory", 7)
        field.type == HashMap::class.java && field.name == "diplomaticVictoryVotesCast" -> hashMapOf<String, String?>("a" to "b", "c" to null)
        field.type == ArrayList::class.java && field.name == "unitNamesTaken" -> arrayListOf("Some Name")
        else -> throw AssertionError(
            "GameInfo has a new serialized field '${field.name}' of type ${field.type.simpleName}: " +
                "make GameInfo.clone() copy it, then teach this test a distinct value for it (or list it as structured/notCloned)")
    }

    @Test
    fun cloneKeepsEverySerializedField() {
        val original = TestGame().gameInfo
        val fields = serializedFields()
        for (field in fields) {
            if (field.name in structured || field.name in notCloned) continue
            field.set(original, distinctValue(field, field.get(original)))
        }

        val clone = original.clone()

        val lost = fields.filter { it.name !in notCloned }
            .filter { json().toJson(it.get(original)) != json().toJson(it.get(clone)) }
            .map { it.name }
        Assert.assertEquals("GameInfo.clone() did not carry over", emptyList<String>(), lost)
    }

    @Test
    fun clearingTheVotesAlsoResetsTheProcessedFlag() {
        val game = TestGame()
        val civ = game.addCiv()
        val gameInfo = game.gameInfo
        gameInfo.diplomaticVictoryVotesCast[civ.civID] = null
        gameInfo.diplomaticVictoryVotesProcessed = true
        civ.addFlag(CivFlags.ShouldResetDiplomaticVotes.name, 1)

        TurnManager(civ).startTurn()

        Assert.assertTrue("votes should be cleared", gameInfo.diplomaticVictoryVotesCast.isEmpty())
        Assert.assertFalse("the next round's results must be processed again", gameInfo.diplomaticVictoryVotesProcessed)
    }
}

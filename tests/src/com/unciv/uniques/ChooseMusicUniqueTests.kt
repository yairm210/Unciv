package com.unciv.uniques

import com.unciv.UncivGame
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.audio.MusicController
import com.unciv.ui.audio.MusicTrackChooserFlags
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import java.util.EnumSet

@RunWith(BaseTestRunner::class)
class ChooseMusicUniqueTests {

    /** Replaces the game's MusicController with a recorder of its `chooseTrack(prefix, suffixes, flags)` calls. */
    private fun recordChooseTrackCalls(): List<List<Any?>> {
        val calls = mutableListOf<List<Any?>>()
        UncivGame.Current.musicController = mock(MusicController::class.java) { invocation ->
            if (invocation.method.name == "chooseTrack" && invocation.arguments.getOrNull(1) is List<*>)
                calls += invocation.arguments.toList()
            if (invocation.method.returnType == Boolean::class.javaPrimitiveType) true else null
        }
        return calls
    }

    @Test
    fun suffixesComeFromTheSecondAndFlagsFromTheThirdParameter() {
        val game = TestGame()
        val civ = game.addCiv(isPlayer = true)
        val calls = recordChooseTrackCalls()

        val triggered = UniqueTriggerActivation.triggerUnique(
            Unique("Choose a music track for [Babylon], [Peace, War], [PrefixMustMatch, slowfade]"), civ)

        Assert.assertTrue(triggered)
        val (prefix, suffixes, flags) = calls.single()
        Assert.assertEquals("Babylon", prefix)
        Assert.assertEquals(listOf("Peace", "War"), suffixes)
        Assert.assertEquals(EnumSet.of(MusicTrackChooserFlags.PrefixMustMatch, MusicTrackChooserFlags.SlowFade), flags)
    }

    @Test
    fun emptyFlagParameterGivesNoFlags() {
        val game = TestGame()
        val civ = game.addCiv(isPlayer = true)
        val calls = recordChooseTrackCalls()

        UniqueTriggerActivation.triggerUnique(Unique("Choose a music track for [this civ], [Peace], []"), civ)

        val (prefix, _, flags) = calls.single()
        Assert.assertEquals(civ.civID, prefix)
        Assert.assertEquals(MusicTrackChooserFlags.none, flags)
    }
}

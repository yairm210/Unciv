package com.unciv.testing

import com.unciv.JsonParser
import com.unciv.models.Tutorial
import com.unciv.ui.tutorials.TutorialMiner
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TutorialMinerTests {

    private val languages = listOf(
            "English", "Czech", "French", "Italian",
            "Korean", "Polish", "Ukrainian", "Russian",
            "Simplified_Chinese", "Traditional_Chinese"
    )
    private val jsonParser = JsonParser()
    private val tutorialMiner = TutorialMiner(jsonParser)

    @Test
    fun getCivilopediaTutorials() {
        // GIVEN
        val expectedTutorKeys = Tutorial.values().filter { it.isCivilopedia }.map { it.value }

        // WHEN
        val result = languages.map { it to tutorialMiner.getCivilopediaTutorials(it).map { it.key.value } }

        // THEN
        result.forEach { (language, keys) ->
            assertTrue("$language civilopedia does not match", keys.containsAll(expectedTutorKeys))
        }
    }
}
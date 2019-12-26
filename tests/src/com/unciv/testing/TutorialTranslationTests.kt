package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.unciv.models.Tutorial
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TutorialTranslationTests {

    private val filesPath = "jsons/Tutorials/Tutorials_%s.json"
    private var tutorialCount = Tutorial.values().size
    private var tutorialKeyNames = Tutorial.values().map { it.value }
    private val json = Json().apply { ignoreUnknownFields = true }

    private val languages = listOf(
            "English", "Czech", "French", "Italian",
            "Korean", "Polish", "Ukrainian", "Russian",
            "Simplified_Chinese", "Traditional_Chinese"
    )

    @Test
    fun testValidNumberOfTranslationTutorials() {
        languages.forEach { language ->
            val keys = getKeysForLanguage(language)
            assertTrue("$language tutorial does not match", keys.size == tutorialCount)
        }
    }

    @Test
    fun testTutorialKeyValidity() {
        for (language in languages) {
            val keys = getKeysForLanguage(language)
            for (key in tutorialKeyNames) {
                assertTrue("$language tutorial does not have $key", keys.contains(key))
            }
        }
    }

    private fun getKeysForLanguage(language: String): List<String> {
        val jsonText = Gdx.files.internal(filesPath.format(language)).readString(Charsets.UTF_8.name())
        return json.fromJson(HashMap<String, Array<Array<String>>>()::class.java, jsonText).map { it.key }
    }
}
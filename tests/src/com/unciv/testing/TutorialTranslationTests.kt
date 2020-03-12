package com.unciv.testing

import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.models.Tutorial
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedHashMap

@RunWith(GdxTestRunner::class)
class TutorialTranslationTests {

    private var tutorialCount = Tutorial.values().size
    private var tutorialKeyNames = Tutorial.values().map { it.value }

    @Test
    fun tutorialsFileIsSerializable() {
        val map = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

        assertTrue("The number of items from Tutorials.json must match to the enum Tutorial",
                map.size == tutorialCount)

        assertTrue("The items from Tutorials.json must match to the enum Tutorial values",
                tutorialKeyNames.containsAll(map.keys))
    }
}
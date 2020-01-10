package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.JsonParser
import com.unciv.models.ruleset.Nation
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class NationParsingTests {

    private val filesPath = "jsons/Nations/Nations.json"
    private val jsonParser = JsonParser()

    @Test
    fun deserializeJson() {
        val nationsFile = Gdx.files.internal(filesPath)
        assertTrue("Nation file does not exist", nationsFile.exists())

        val nations = jsonParser.getFromJson(Array<Nation>::class.java, nationsFile)
        assertTrue("Can not parse all nations", nations.size == 46)
    }
}
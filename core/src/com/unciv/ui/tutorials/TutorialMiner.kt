package com.unciv.ui.tutorials

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.models.Tutorial

class TutorialMiner {

    companion object {
        private const val TUTORIALS_PATH = "jsons/Tutorials/Tutorials_%s.json"
    }

    fun getAllTutorials(language: String): Map<Tutorial, List<String>> {
        val path = TUTORIALS_PATH.format(language)
        if (!Gdx.files.internal(path).exists()) return emptyMap()

        // ...Yes. Disgusting. I wish I didn't have to do this.
        val x = LinkedHashMap<String, Array<Array<String>>>()
        val tutorials: LinkedHashMap<String, Array<Array<String>>> = UncivGame.Current.ruleset.getFromJson(x.javaClass, path)

        val tutorialMap = mutableMapOf<Tutorial, List<String>>()
        for (tutorial in tutorials) {
            tutorialMap[Tutorial.findByName(tutorial.key)!!] = tutorial.value.map { it.joinToString("\n") }
        }
        return tutorialMap
    }

    fun getCivilopediaTutorials(language: String): Map<Tutorial, List<String>> =
            getAllTutorials(language).filter { !it.key.name.startsWith("_") }

    fun getTutorial(tutorial: Tutorial, language: String): List<String> =
            getAllTutorials(language)[tutorial] ?: emptyList()
}
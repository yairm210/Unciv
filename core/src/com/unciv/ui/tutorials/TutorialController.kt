package com.unciv.ui.tutorials

import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.models.Tutorial
import com.unciv.ui.utils.CameraStageBaseScreen

class TutorialController(screen: CameraStageBaseScreen) {

    private val tutorialQueue = mutableSetOf<Tutorial>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null
    private val tutorialRender = TutorialRender(screen)
    private val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

    fun showTutorial(tutorial: Tutorial) {
        tutorialQueue.add(tutorial)
        showTutorialIfNeeded()
    }

    fun removeTutorial(tutorialName: String) {
        Tutorial.valueOf(tutorialName).let { removeTutorial(it) }
    }
    fun removeTutorial(tutorial: Tutorial) {
        isTutorialShowing = false
        tutorialQueue.remove(tutorial)
        with(UncivGame.Current.settings) {
            if (!tutorialsShown.contains(tutorial.name)) {
                tutorialsShown.add(tutorial.name)
                save()
            }
        }
        showTutorialIfNeeded()
    }

    private fun showTutorialIfNeeded() {
        if (!UncivGame.Current.settings.showTutorials) return
        val tutorial = tutorialQueue.firstOrNull()
        if (tutorial == null) {
            allTutorialsShowedCallback?.invoke()
        } else if (!isTutorialShowing) {
            isTutorialShowing = true
            val texts = getTutorial(tutorial)
            tutorialRender.showTutorial(TutorialForRender(tutorial, texts)) {
                removeTutorial(tutorial)
            }
        }
    }

    fun getCivilopediaTutorials(): Map<String, Array<String>> {
        return tutorials.filter { Tutorial.findByName(it.key)!!.isCivilopedia }
    }

    private fun getTutorial(tutorial: Tutorial): Array<String> {
        return tutorials[tutorial.value] ?: Array()
    }
}

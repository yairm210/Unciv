package com.unciv.ui.tutorials

import com.unciv.UncivGame
import com.unciv.models.Tutorial

class TutorialController(
        private val tutorialMiner: TutorialMiner,
        private val tutorialRender: TutorialRender
) {

    private val tutorialQueue = mutableSetOf<Tutorial>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null

    fun showTutorial(tutorial: Tutorial) {
        if (!UncivGame.Current.settings.showTutorials) return
        if (UncivGame.Current.settings.tutorialsShown.contains(tutorial.name)) return

        tutorialQueue.add(tutorial)
        showTutorialIfNeeded()
    }

    private fun showTutorialIfNeeded() {
        val tutorial = tutorialQueue.firstOrNull()
        if (tutorial == null) {
            allTutorialsShowedCallback?.invoke()
        } else if (!isTutorialShowing) {
            isTutorialShowing = true
            val texts = tutorialMiner.getTutorial(tutorial, UncivGame.Current.settings.language)
            tutorialRender.showTutorial(TutorialForRender(tutorial, texts)) {
                tutorialQueue.remove(tutorial)
                isTutorialShowing = false
                with(UncivGame.Current.settings) {
                    if (!tutorialsShown.contains(tutorial.name)) {
                        tutorialsShown.add(tutorial.name)
                        save()
                    }
                }
                showTutorialIfNeeded()
            }
        }
    }
}

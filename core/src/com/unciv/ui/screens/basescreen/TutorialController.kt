package com.unciv.ui.screens.basescreen

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.TutorialTrigger
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import yairm210.purity.annotations.Readonly


class TutorialController(screen: BaseScreen) {

    private val tutorialQueue = mutableSetOf<TutorialTrigger>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null
    private val tutorialRender = TutorialRender(screen)
    //TODO needs more testing whether this holds in all cases
    private val tutorials by screen.getCivilopediaRuleset()::tutorials

    fun showTutorial(tutorial: TutorialTrigger) {
        tutorialQueue.add(tutorial)
        showTutorialIfNeeded()
    }

    private fun removeTutorial(tutorial: TutorialTrigger) {
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

    @Readonly
    private fun getTutorial(tutorial: TutorialTrigger): List<String> {
        val name = tutorial.value.replace('_', ' ').trimStart()
        return tutorials[name]?.steps ?: emptyList()
    }
}


data class TutorialForRender(val tutorial: TutorialTrigger, val texts: List<String>)

class TutorialRender(private val screen: BaseScreen) {

    fun showTutorial(tutorial: TutorialForRender, closeAction: () -> Unit) {
        showDialog(tutorial.tutorial.name, tutorial.texts, closeAction)
    }

    private fun showDialog(tutorialName: String, texts: List<String>, closeAction: () -> Unit) {
        if (texts.isEmpty()) return closeAction()

        val tutorialPopup = Popup(screen)
        tutorialPopup.name = Constants.tutorialPopupNamePrefix + tutorialName

        val externalImage = ImageGetter.findExternalImage(tutorialName)
        if (externalImage != null) {
            tutorialPopup.add(ImageGetter.getExternalImage(externalImage)).row()
        }

        tutorialPopup.addGoodSizedLabel(texts[0]).row()

        tutorialPopup.addCloseButton(additionalKey = KeyCharAndCode.SPACE) {
            tutorialPopup.remove()
            showDialog(tutorialName, texts.subList(1, texts.size), closeAction)
        }
        tutorialPopup.open()
    }
}

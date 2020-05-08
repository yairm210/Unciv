package com.unciv.ui.tutorials

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.models.Tutorial
import com.unciv.ui.utils.*

data class TutorialForRender(val tutorial: Tutorial, val texts: Array<String>)

class TutorialRender(private val screen: CameraStageBaseScreen) {

    fun showTutorial(tutorial: TutorialForRender, closeAction: () -> Unit) {
        showDialog(tutorial.tutorial.name, tutorial.texts, closeAction)
    }

    private fun showDialog(tutorialName: String, texts: Array<String>, closeAction: () -> Unit) {
        val text = texts.firstOrNull()
        if (text == null) {
            closeAction()
        } else {
            val tutorialPopup = Popup(screen)
            tutorialPopup.name = Constants.tutorialPopupNamePrefix + tutorialName

            if (Gdx.files.internal("ExtraImages/$tutorialName").exists()) {
                tutorialPopup.add(ImageGetter.getExternalImage(tutorialName)).row()
            }

            tutorialPopup.addGoodSizedLabel(texts[0]).row()

            val button = Constants.close.toTextButton()
            button.onClick {
                tutorialPopup.remove()
                texts.removeIndex(0)
                showDialog(tutorialName, texts, closeAction)
            }
            tutorialPopup.add(button).pad(10f)
            tutorialPopup.open()
        }
    }
}

package com.unciv.ui.tutorials

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.models.TutorialTrigger
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode

@Suppress("ArrayInDataClass")
data class TutorialForRender(val tutorial: TutorialTrigger, val texts: Array<String>)

class TutorialRender(private val screen: BaseScreen) {

    fun showTutorial(tutorial: TutorialForRender, closeAction: () -> Unit) {
        showDialog(tutorial.tutorial.name, tutorial.texts, closeAction)
    }

    private fun showDialog(tutorialName: String, texts: Array<String>, closeAction: () -> Unit) {
        if (texts.isEmpty()) return closeAction()

        val tutorialPopup = Popup(screen)
        tutorialPopup.name = Constants.tutorialPopupNamePrefix + tutorialName

        if (Gdx.files.internal("ExtraImages/$tutorialName").exists()) {
            tutorialPopup.add(ImageGetter.getExternalImage(tutorialName)).row()
        }

        tutorialPopup.addGoodSizedLabel(texts[0]).row()

        tutorialPopup.addCloseButton(additionalKey = KeyCharAndCode.SPACE) {
            tutorialPopup.remove()
            showDialog(tutorialName, texts.sliceArray(1 until texts.size), closeAction)
        }
        tutorialPopup.open()
    }
}

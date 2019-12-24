package com.unciv.ui.tutorials

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.models.Tutorial
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.optionstable.PopupTable

data class TutorialForRender(val tutorial: Tutorial, val texts: List<String>)

class TutorialRender(private val screen: CameraStageBaseScreen) {

    fun showTutorial(tutorial: TutorialForRender, closeAction: () -> Unit) {
        showDialog(tutorial.tutorial.name, tutorial.texts, closeAction)
    }

    private fun showDialog(tutorialName: String, texts: List<String>, closeAction: () -> Unit) {
        val text = texts.firstOrNull()
        if (text == null) {
            closeAction()
        } else {
            val tutorialTable = PopupTable(screen)

            if (Gdx.files.internal("ExtraImages/$tutorialName").exists()) {
                tutorialTable.add(ImageGetter.getExternalImage(tutorialName)).row()
            }

            tutorialTable.addGoodSizedLabel(texts[0]).row()

            val button = TextButton("Close".tr(), CameraStageBaseScreen.skin)
            button.onClick {
                tutorialTable.remove()
                showDialog(tutorialName, texts - text, closeAction)
            }
            tutorialTable.add(button).pad(10f)
            tutorialTable.open()
        }
    }
}
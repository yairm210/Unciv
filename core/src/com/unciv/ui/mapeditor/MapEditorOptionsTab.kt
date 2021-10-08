package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class MapEditorOptionsTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private val copySeedButton = "".toTextButton()
    private var seedToCopy = ""

    init {
        top()
        pad(5f)
        defaults().pad(5f)
        add(copySeedButton).row()
        copySeedButton.onClick {
            Gdx.app.clipboard.contents = seedToCopy
        }
    }

    override fun activated(index: Int) {
        seedToCopy = editorScreen.tileMap.mapParameters.seed.toString()
        copySeedButton.setText("{RNG Seed} $seedToCopy: {Copy to clipboard}".tr())
    }
}

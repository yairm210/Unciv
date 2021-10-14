package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class MapEditorOptionsTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private var seedToCopy = ""
    private val seedLabel = "".toLabel()
    private val copySeedButton = "Copy to clipboard".toTextButton()
    private val tileMatchGroup = ButtonGroup<CheckBox>()
    private var tileMatchFuzziness = TileMatchFuzziness.CompleteMatch
    //Current map RND seed = [amount]

    enum class TileMatchFuzziness(val label: String) {
        CompleteMatch("Complete match"),
        NoImprovement("Except improvements"),
        BaseAndFeatures("Base and terrain features"),
        BaseTerrain("Base terrain only"),
        LandOrWater("Land or water only"),
    }
    init {
        top()
        defaults().pad(10f)
        add(seedLabel).row()
        add(copySeedButton).row()
        copySeedButton.onClick {
            Gdx.app.clipboard.contents = seedToCopy
        }
        addSeparator(Color.GRAY)
        add("Tile Matching Criteria".toLabel()).row()
        for (option in TileMatchFuzziness.values()) {
            val check = option.label.toCheckBox(option == tileMatchFuzziness)
                { tileMatchFuzziness = option }
            add(check).row()
            tileMatchGroup.add(check)
        }
    }

    override fun activated(index: Int) {
        seedToCopy = editorScreen.tileMap.mapParameters.seed.toString()
        seedLabel.setText("Current map RNG seed = [$seedToCopy]".tr())
    }

    override fun deactivated(newIndex: Int) {
        editorScreen.tileMatchFuzziness = tileMatchFuzziness
    }
}

package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class PromotionsEditorPopup(selectedUnit: MapUnit, previousScreen: TileEditorOptionsTable): Popup(previousScreen.mapEditorScreen) {

    init {
        val promotionsCheckBoxTable = Table(skin)

        for (promotion in previousScreen.mapEditorScreen.ruleset.unitPromotions.values) {
            promotionsCheckBoxTable.addCheckbox(promotion.name, selectedUnit.promotions.promotions.contains("${promotion.name}"))
            {
                if (it)
                    selectedUnit.promotions.promotions.add(promotion.name)
                else
                    selectedUnit.promotions.promotions.remove(promotion.name)
            }
            val promotionEffect = promotion.effect.toLabel()
            promotionEffect.setWrap(true)
            promotionsCheckBoxTable.add(promotionEffect).colspan(2)
                    .width(300f).padBottom(10f).row()
        }

        add(ScrollPane(promotionsCheckBoxTable)).height(previousScreen.mapEditorScreen.mapHolder.height * 0.5f)

        addCloseButton() { previousScreen.updateUnitEditTable(selectedUnit) }
    }

    private fun Table.addCheckbox(text: String, initialState: Boolean, onChange: (newValue: Boolean) -> Unit) {
        val checkbox = CheckBox(text.tr(), CameraStageBaseScreen.skin)
        checkbox.isChecked = initialState
        checkbox.onChange { onChange(checkbox.isChecked) }
        add(checkbox).colspan(2).left().row()
    }
}
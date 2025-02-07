package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.PerpetualStatConversion
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen

/** This is the bottom-right table in the city screen that shows the currently selected construction */
class ConstructionInfoTable(val cityScreen: CityScreen) : Table() {
    private val selectedConstructionTable = Table()
    private val buyButtonFactory = BuyButtonFactory(cityScreen)

    init {
        selectedConstructionTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/ConstructionInfoTable/SelectedConstructionTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        add(selectedConstructionTable).pad(2f).fill()
        background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/ConstructionInfoTable/Background",
            tintColor = Color.WHITE
        )
    }

    fun update(selectedConstruction: IConstruction?) {
        selectedConstructionTable.clear()  // clears content and listeners

        if (selectedConstruction == null) {
            isVisible = false
            return
        }
        isVisible = true

        updateSelectedConstructionTable(selectedConstruction)

        pack()
    }

    private fun updateSelectedConstructionTable(construction: IConstruction) {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        //val selectedConstructionTable = Table()
        selectedConstructionTable.run {
            pad(10f)

            add(ImageGetter.getConstructionPortrait(construction.name, 50f).apply {
                val link = (construction as? IRulesetObject)?.makeLink() ?: return@apply
                if (link.isEmpty()) return@apply
                touchable = Touchable.enabled
                this.onClick {
                    cityScreen.openCivilopedia(link)
                }
            }).pad(5f)

            var buildingText = construction.name.tr(hideIcons = true)
            val specialConstruction = PerpetualConstruction.perpetualConstructionsMap[construction.name]

            buildingText += specialConstruction?.getProductionTooltip(city)
                    ?: cityConstructions.getTurnsToConstructionString(construction)

            add(Label(buildingText, BaseScreen.skin)).expandX().row()  // already translated

            val description = when (construction) {
                is BaseUnit -> construction.getDescription(city)
                is Building -> construction.getDescription(city, true)
                is PerpetualStatConversion -> construction.description.replace("[rate]", "[${construction.getConversionRate(city)}]").tr()
                is PerpetualConstruction -> construction.description.tr()
                else -> ""  // Should never happen
            }

            val descriptionLabel = Label(description, BaseScreen.skin)  // already translated
            descriptionLabel.wrap = true
            add(descriptionLabel).colspan(2).width(stage.width / if(cityScreen.isCrampedPortrait()) 3 else 4)
            
            if (cityConstructions.isBuilt(construction.name)) {
                showSellButton(construction)
            } else if (buyButtonFactory.hasBuyButtons(construction)) {
                row()
                for (button in buyButtonFactory.getBuyButtons(construction)) {
                    selectedConstructionTable.add(button).padTop(5f).colspan(2).center().row()
                }
            }
            if (construction is BaseUnit) {
                val unitType = construction.unitType
                
                if (city.canBuildUnitTypeWithSavedPromotion[unitType] != null) {
                    row()
                    add(city.canBuildUnitTypeWithSavedPromotion[unitType]?.let {
                        "Build units with saved UnitType promotion".toCheckBox(
                            it
                        ) {city.canBuildUnitTypeWithSavedPromotion[unitType] = it}
                    }).colspan(2).center()
                }
            }
        }
    }

    // Show sell button if construction is a currently sellable building
    private fun showSellButton(
        construction: IConstruction
    ) {
        if (construction is Building && construction.isSellable()) {
            selectedConstructionTable.run {
                val sellAmount = cityScreen.city.getGoldForSellingBuilding(construction.name)
                val sellText = "{Sell} $sellAmount " + Fonts.gold
                val sellBuildingButton = sellText.toTextButton()
                row()
                add(sellBuildingButton).padTop(5f).colspan(2).center()

                val isFree = cityScreen.hasFreeBuilding(construction)
                val enableSell = !isFree &&
                    !cityScreen.city.isPuppet &&
                    cityScreen.canChangeState &&
                    (!cityScreen.city.hasSoldBuildingThisTurn || cityScreen.city.civ.gameInfo.gameParameters.godMode)
                sellBuildingButton.isEnabled = enableSell
                if (enableSell)
                    sellBuildingButton.onClick(UncivSound.Coin) {
                        sellBuildingButton.disable()
                        sellBuildingClicked(construction, sellText)
                    }

                if (cityScreen.city.hasSoldBuildingThisTurn && !cityScreen.city.civ.gameInfo.gameParameters.godMode
                        || cityScreen.city.isPuppet
                        || !cityScreen.canChangeState)
                    sellBuildingButton.disable()
            }
        }
    }
    
    private fun sellBuildingClicked(construction: Building, sellText: String) {
        cityScreen.closeAllPopups()

        ConfirmPopup(
            cityScreen,
            "Are you sure you want to sell this [${construction.name}]?",
            sellText,
            restoreDefault = {
                cityScreen.update()
            }
        ) {
            sellBuildingConfirmed(construction)
        }.open()
    }

    private fun sellBuildingConfirmed(construction: Building) {
        cityScreen.city.sellBuilding(construction)
        cityScreen.clearSelection()
        cityScreen.update()
    }
    
}

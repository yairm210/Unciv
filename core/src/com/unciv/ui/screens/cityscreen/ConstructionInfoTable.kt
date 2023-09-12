package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HolidayDates
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.PerpetualStatConversion
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.ColorMarkupLabel
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen

class ConstructionInfoTable(val cityScreen: CityScreen): Table() {
    private val selectedConstructionTable = Table()

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
                    UncivGame.Current.pushScreen(CivilopediaScreen(city.getRuleset(), link = link))
                }
            }).pad(5f)

            var buildingText = construction.name.tr(hideIcons = true)
            val specialConstruction = PerpetualConstruction.perpetualConstructionsMap[construction.name]

            buildingText += specialConstruction?.getProductionTooltip(city)
                    ?: cityConstructions.getTurnsToConstructionString(construction)

            add(Label(buildingText, BaseScreen.skin)).row()  // already translated

            val description = when (construction) {
                is BaseUnit -> construction.getDescription(city)
                is Building -> construction.getDescription(city, true)
                is PerpetualStatConversion -> construction.description.replace("[rate]", "[${construction.getConversionRate(city)}]").tr()
                is PerpetualConstruction -> construction.description.tr()
                else -> ""  // Should never happen
            }

            val descriptionLabel = Label(description, BaseScreen.skin)  // already translated
            descriptionLabel.wrap = true
            add(descriptionLabel).colspan(2).width(stage.width / 4)

            // Show sell button if construction is a currently sellable building
            if (construction is Building && cityConstructions.isBuilt(construction.name)
                    && construction.isSellable()) {
                val sellAmount = cityScreen.city.getGoldForSellingBuilding(construction.name)
                val sellText = "{Sell} $sellAmount " + Fonts.gold
                val sellBuildingButton = sellText.toTextButton()
                row()
                add(sellBuildingButton).padTop(5f).colspan(2).center()

                val isFree = cityScreen.hasFreeBuilding(construction)
                val isAprilFools = HolidayDates.getHolidayByDate() == HolidayDates.Holidays.AprilFools
                val enableSell = (!isFree || isAprilFools) &&
                    !cityScreen.city.isPuppet &&
                    cityScreen.canChangeState &&
                    (!cityScreen.city.hasSoldBuildingThisTurn || cityScreen.city.civ.gameInfo.gameParameters.godMode)
                sellBuildingButton.isEnabled = enableSell
                if (sellBuildingButton.isEnabled) sellBuildingButton.onClick(UncivSound.Coin) {
                    sellBuildingButton.disable()
                    sellBuildingClicked(construction, isFree, sellText)
                }

                if (cityScreen.city.hasSoldBuildingThisTurn && !cityScreen.city.civ.gameInfo.gameParameters.godMode
                        || cityScreen.city.isPuppet
                        || !cityScreen.canChangeState)
                    sellBuildingButton.disable()
            }
        }
    }

    private fun sellBuildingClicked(construction: Building, isFree: Boolean, sellText: String) {
        cityScreen.closeAllPopups()

        ConfirmPopup(
            cityScreen,
            "Are you sure you want to sell this [${construction.name}]?",
            sellText,
            restoreDefault = {
                cityScreen.update()
            }
        ) {
            sellBuildingConfirmed(construction, isFree)
        }.open()
    }

    private fun sellBuildingConfirmed(construction: Building, isFree: Boolean) {
        if (isFree) {
            AprilFoolsEasterEgg()
        } else {
            cityScreen.city.sellBuilding(construction)
            cityScreen.clearSelection()
            cityScreen.update()
        }
    }

    // Warning: Easter Egg!
    private inner class AprilFoolsEasterEgg : Popup(cityScreen, Scrollability.None) {
        init {
            val fakeGold = 100 * (12..666).random()
            val maxWidth = cityScreen.stage.width / 2
            fun line(msg: String, size: Int) {
                val label = ColorMarkupLabel(msg, size)
                label.setAlignment(Align.center)
                label.wrap = true
                add(label).maxWidth(maxWidth).row()
            }
            line("«GOLD»{You gain [$fakeGold] gold as reward for finding an exploit!}«»", Constants.headingFontSize)
            add().minHeight(100f).row()
            line("«RED»{April Fools day! Nope, no gold for you!}«»", 12)
            add().minHeight(20f).row()
            addCloseButton("Aww...")
            open()
            // Leaves sellBuildingButton disabled
        }
    }
}

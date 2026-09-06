package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.models.Religion
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.TileView

/**
 * This class handles everything related to buying constructions. This includes
 * showing and handling [ConfirmBuyPopup] and the actual purchase in [purchaseConstruction].
 */
class BuyButtonFactory(val cityScreen: CityScreen) {

    private var preferredBuyStat = Stat.Gold  // Used for keyboard buy

    fun hasBuyButtons(construction: IConstruction?): Boolean = getBuyButtons(construction).isNotEmpty()

    fun getBuyButtons(construction: IConstruction?): List<TextButton> {
        val selection = cityScreen.selectedConstruction!=null || cityScreen.selectedQueueEntry >= 0
        if (selection && construction != null && construction !is PerpetualConstruction)
            return Stat.statsUsableToBuy.mapNotNull {
                getBuyButton(construction as INonPerpetualConstruction, it)
            }
        return emptyList()
    }

    private fun getBuyButton(construction: INonPerpetualConstruction?, stat: Stat = Stat.Gold): TextButton? {
        if (stat !in Stat.statsUsableToBuy || construction == null)
            return null

        val cityView = cityScreen.cityView
        val button = "".toTextButton()

        if (!isConstructionPurchaseShown(construction, stat)) {
            // This can't ever be bought with the given currency.
            // We want one disabled "buy" button without a price for "priceless" buildings such as wonders
            // We don't want such a button when the construction can be bought using a different currency
            if (stat != Stat.Gold || cityView.canBePurchasedWithAnyStat(construction))
                return null
            button.setText("Buy".tr())
            button.disable()
        } else {
            val constructionBuyCost = cityView.constructions.getStatBuyCost(construction, stat)!!
            button.setText("Buy".tr() + " " + constructionBuyCost.tr() + stat.character)

            button.onActivation(binding = KeyboardBinding.BuyConstruction) {
                button.disable()
                buyButtonOnClick(construction, stat)
            }
            // allow puppets, since isConstructionPurchaseAllowed handles that and exceptions to that rule
            button.isEnabled = cityScreen.canChangeState &&
                cityView.constructions.isConstructionPurchaseAllowed(construction, stat, constructionBuyCost)
            preferredBuyStat = stat  // Not very intelligent, but the least common currency "wins"
            if (cityView.constructions.isConstructionPurchaseBlockedByUnit(construction)) {
                button.addTooltip("Move unit out of city first", 26f, false)
            }
        }

        button.labelCell.pad(5f)

        return button
    }

    private fun buyButtonOnClick(construction: INonPerpetualConstruction, stat: Stat = preferredBuyStat) {
        if (construction !is Building || !construction.hasCreateOneImprovementUnique())
            return askToBuyConstruction(construction, stat)
        if (cityScreen.selectedQueueEntry < 0)
            return cityScreen.startPickTileForCreatesOneImprovement(construction, stat, true)
        // Buying a UniqueType.CreatesOneImprovement building from queue must pass down
        // the already selected tile, otherwise a new one is chosen from Automation code.
        val cityView = cityScreen.cityView
        val improvement = cityView.getImprovementToCreate(construction)!!
        val tileForImprovement = cityView.constructions.getTileForImprovement(improvement.name)
        askToBuyConstruction(construction, stat, tileForImprovement)
    }

    /** Ask whether user wants to buy [construction] for [stat].
     *
     * Used from onClick and keyboard dispatch, thus only minimal parameters are passed,
     * and it needs to do all checks and the sound as appropriate.
     */
    fun askToBuyConstruction(
        construction: INonPerpetualConstruction,
        stat: Stat = preferredBuyStat,
        tile: TileView? = null
    ) {
        if (!isConstructionPurchaseShown(construction, stat)) return
        val cityView = cityScreen.cityView
        val constructionStatBuyCost = cityView.constructions.getStatBuyCost(construction, stat)!!
        if (!cityView.constructions.isConstructionPurchaseAllowed(construction, stat, constructionStatBuyCost)) return

        cityScreen.closeAllPopups()
        ConfirmBuyPopup(construction, stat, constructionStatBuyCost, tile)
    }

    private inner class ConfirmBuyPopup(
        construction: INonPerpetualConstruction,
        stat: Stat,
        constructionStatBuyCost: Int,
        tile: TileView?
    ) : Popup(cityScreen.stage) {
        init {
            val cityView = cityScreen.cityView
            val balance = cityView.getStatReserve(stat)
            val majorityReligion = cityView.getMajorityReligion()
            val yourReligion = cityView.viewingCiv().getYourReligion()
            val isBuyingWithFaithForForeignReligion = construction.hasUnique(UniqueType.ReligiousUnit)
                && !construction.hasUnique(UniqueType.TakeReligionOverBirthCity)
                && majorityReligion != yourReligion

            addGoodSizedLabel("Currently you have [$balance] [${stat.name}].").padBottom(10f).row()
            if (isBuyingWithFaithForForeignReligion) {
                // Earlier tests should forbid this Popup unless both religions are non-null, but to be safe:
                fun Religion?.getName() = this?.getReligionDisplayName() ?: Constants.unknownCityName
                addGoodSizedLabel("You are buying a religious unit in a city that doesn't follow the religion you founded ([${yourReligion.getName()}]). " +
                    "This means that the unit is tied to that foreign religion ([${majorityReligion.getName()}]) and will be less useful.").row()
                addGoodSizedLabel("Are you really sure you want to purchase this unit?", Constants.headingFontSize).run {
                    actor.color = Color.FIREBRICK
                    padBottom(10f)
                    row()
                }
            }
            addGoodSizedLabel("Would you like to purchase [${construction.name}] for [$constructionStatBuyCost] [${stat.character}]?").row()

            addCloseButton(Constants.cancel, KeyboardBinding.Cancel) { cityScreen.updateAsync() }
            val confirmStyle = BaseScreen.skin.get("positive", TextButton.TextButtonStyle::class.java)
            addOKButton("Purchase", KeyboardBinding.Confirm, confirmStyle) {
                purchaseConstruction(construction, stat, tile)
            }
            equalizeLastTwoButtonWidths()
            open(true)
        }
    }

    /** This tests whether the buy button should be _shown_ */
    private fun isConstructionPurchaseShown(construction: INonPerpetualConstruction, stat: Stat): Boolean {
        return cityScreen.cityView.canBePurchasedWithStat(construction, stat)
    }

    /** Called only by askToBuyConstruction's Yes answer - not to be confused with [com.unciv.logic.city.CityConstructions.purchaseConstruction]
     * @param tile supports [UniqueType.CreatesOneImprovement]
     */
    private fun purchaseConstruction(
        construction: INonPerpetualConstruction,
        stat: Stat = Stat.Gold,
        tile: TileView? = null
    ) {
        SoundPlayer.play(stat.purchaseSound)
        val cityView = cityScreen.cityView
        if (!cityView.constructions.purchaseConstruction(construction, cityScreen.selectedQueueEntry, stat, tile)) {
            Popup(cityScreen).apply {
                add("No space available to place [${construction.name}] near [${cityView.name}]".tr()).row()
                addCloseButton()
                open()
            }
            return
        }
        if (cityScreen.selectedQueueEntry>=0 || cityScreen.selectedConstruction?.let { cityView.constructions.isBuildable(it) } != true) {
            cityScreen.selectedQueueEntry = -1
            cityScreen.clearSelection()

            if (cityView.constructions.currentConstructionName().isNotEmpty()) {
                val newConstruction = cityView.constructions.getCurrentConstruction()
                if (newConstruction is INonPerpetualConstruction)
                    cityScreen.selectConstruction(newConstruction)
            }
        }
        cityScreen.updateAsync()
    }

}

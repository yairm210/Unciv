package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Religion
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Use [addBuyButtons] to add buy buttons to a table.
 * This class handles everything related to buying constructions. This includes
 * showing and handling [ConfirmBuyPopup] and the actual purchase in [purchaseConstruction].
 */
class BuyButtonFactory(val cityScreen: CityScreen) {

    private var preferredBuyStat = Stat.Gold  // Used for keyboard buy
    
    fun addBuyButtons(table: Table, construction: IConstruction?, onButtonAdded: (Cell<TextButton>) -> Unit) {
        for (button in getBuyButtons(construction)) {
            onButtonAdded(table.add(button))
        }
    }

    fun hasBuyButtons(construction: IConstruction?): Boolean {
        return getBuyButtons(construction).isNotEmpty()
    }
    
    private fun getBuyButtons(construction: IConstruction?): List<TextButton> {
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

        val city = cityScreen.city
        val button = "".toTextButton()

        if (!isConstructionPurchaseShown(construction, stat)) {
            // This can't ever be bought with the given currency.
            // We want one disabled "buy" button without a price for "priceless" buildings such as wonders
            // We don't want such a button when the construction can be bought using a different currency
            if (stat != Stat.Gold || construction.canBePurchasedWithAnyStat(city))
                return null
            button.setText("Buy".tr())
            button.disable()
        } else {
            val constructionBuyCost = construction.getStatBuyCost(city, stat)!!
            button.setText("Buy".tr() + " " + constructionBuyCost.tr() + stat.character)

            button.onActivation(binding = KeyboardBinding.BuyConstruction) {
                button.disable()
                buyButtonOnClick(construction, stat)
            }
            // allow puppets, since isConstructionPurchaseAllowed handles that and exceptions to that rule
            button.isEnabled = cityScreen.canChangeState &&
                city.cityConstructions.isConstructionPurchaseAllowed(construction, stat, constructionBuyCost)
            preferredBuyStat = stat  // Not very intelligent, but the least common currency "wins"
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
        val improvement = construction.getImprovementToCreate(
            cityScreen.city.getRuleset(), cityScreen.city.civ)!!
        val tileForImprovement = cityScreen.city.cityConstructions.getTileForImprovement(improvement.name)
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
        tile: Tile? = null
    ) {
        if (!isConstructionPurchaseShown(construction, stat)) return
        val city = cityScreen.city
        val constructionStatBuyCost = construction.getStatBuyCost(city, stat)!!
        if (!city.cityConstructions.isConstructionPurchaseAllowed(construction, stat, constructionStatBuyCost)) return

        cityScreen.closeAllPopups()
        ConfirmBuyPopup(construction, stat,constructionStatBuyCost, tile)
    }

    private inner class ConfirmBuyPopup(
        construction: INonPerpetualConstruction,
        stat: Stat,
        constructionStatBuyCost: Int,
        tile: Tile?
    ) : Popup(cityScreen.stage) {
        init {
            val city = cityScreen.city
            val balance = city.getStatReserve(stat)
            val majorityReligion = city.religion.getMajorityReligion()
            val yourReligion = city.civ.religionManager.religion
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

            addCloseButton(Constants.cancel, KeyboardBinding.Cancel) { cityScreen.update() }
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
        val city = cityScreen.city
        return construction.canBePurchasedWithStat(city, stat)
    }

    /** Called only by askToBuyConstruction's Yes answer - not to be confused with [CityConstructions.purchaseConstruction]
     * @param tile supports [UniqueType.CreatesOneImprovement]
     */
    private fun purchaseConstruction(
        construction: INonPerpetualConstruction,
        stat: Stat = Stat.Gold,
        tile: Tile? = null
    ) {
        SoundPlayer.play(stat.purchaseSound)
        val city = cityScreen.city
        if (!city.cityConstructions.purchaseConstruction(construction, cityScreen.selectedQueueEntry, false, stat, tile)) {
            Popup(cityScreen).apply {
                add("No space available to place [${construction.name}] near [${city.name}]".tr()).row()
                addCloseButton()
                open()
            }
            return
        }
        if (cityScreen.selectedQueueEntry>=0 || cityScreen.selectedConstruction?.isBuildable(city.cityConstructions) != true) {
            cityScreen.selectedQueueEntry = -1
            cityScreen.clearSelection()

            // Allow buying next queued or auto-assigned construction right away
            city.cityConstructions.chooseNextConstruction()
            if (city.cityConstructions.currentConstructionFromQueue.isNotEmpty()) {
                val newConstruction = city.cityConstructions.getCurrentConstruction()
                if (newConstruction is INonPerpetualConstruction)
                    cityScreen.selectConstruction(newConstruction)
            }
        }
        cityScreen.city.reassignPopulation()
        cityScreen.update()
    }

}

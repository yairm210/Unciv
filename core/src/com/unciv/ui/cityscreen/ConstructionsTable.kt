package com.unciv.ui.cityscreen

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.models.UncivSound
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.ConstructionInfoTable.Companion.turnOrTurns
import com.unciv.ui.utils.*

class ConstructionsTable(val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    /* -1 = Nothing, >= 0 queue entry (0 = current construction) */
    private var selectedQueueEntry = -1 // None

    private val showCityInfoTableButton: TextButton
    private val constructionsQueueScrollPane: ScrollPane
    private val availableConstructionsScrollPane: ScrollPane

    private val constructionsQueueTable = Table()
    private val availableConstructionsTable = Table()
    private val buttons = Table()

    private val pad = 10f

    init {
        showCityInfoTableButton = "Show stats drilldown".toTextButton()
        showCityInfoTableButton.onClick {
            cityScreen.showConstructionsTable = false
            cityScreen.update()
        }

        constructionsQueueScrollPane = ScrollPane(constructionsQueueTable.addBorder(2f, Color.WHITE))
        constructionsQueueScrollPane.setOverscroll(false,false)
        availableConstructionsScrollPane = ScrollPane(availableConstructionsTable.addBorder(2f, Color.WHITE))
        availableConstructionsScrollPane.setOverscroll(false,false)

        constructionsQueueTable.background = ImageGetter.getBackground(Color.BLACK)
        availableConstructionsTable.background = ImageGetter.getBackground(Color.BLACK)

        add(showCityInfoTableButton).left().padLeft(pad).padBottom(pad).row()
        add(constructionsQueueScrollPane).left().padBottom(pad).row()
        add(buttons).center().bottom().padBottom(pad).row()
        add(availableConstructionsScrollPane).left().bottom().row()
    }

    fun update(selectedConstruction: IConstruction?) {
        val queueScrollY = constructionsQueueScrollPane.scrollY
        val constrScrollY = availableConstructionsScrollPane.scrollY

        clearContent()

        updateButtons(selectedConstruction)

        updateConstructionQueue()
        constructionsQueueScrollPane.layout()
        constructionsQueueScrollPane.scrollY = queueScrollY
        constructionsQueueScrollPane.updateVisualScroll()
        getCell(constructionsQueueScrollPane).maxHeight(stage.height / 3 - 10f)

        // Need to pack before computing space left for bottom panel
        pack()
        val usedHeight = showCityInfoTableButton.height + constructionsQueueScrollPane.height + buttons.height + 3f * pad + 10f

        updateAvailableConstructions()
        availableConstructionsScrollPane.layout()
        availableConstructionsScrollPane.scrollY = constrScrollY
        availableConstructionsScrollPane.updateVisualScroll()
        getCell(availableConstructionsScrollPane).maxHeight(stage.height - usedHeight)

        pack()
    }

    private fun clearContent() {
        constructionsQueueTable.clear()
        buttons.clear()
        availableConstructionsTable.clear()
    }

    private fun updateButtons(construction: IConstruction?) {
        buttons.add(getQueueButton(construction)).padRight(5f)
        buttons.add(getBuyButton(construction))
    }

    private fun updateConstructionQueue() {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions
        val currentConstruction = cityConstructions.currentConstructionFromQueue
        val queue = cityConstructions.constructionQueue

        constructionsQueueTable.defaults().pad(0f)
        constructionsQueueTable.add(getHeader("Current construction".tr())).fillX()
        constructionsQueueTable.addSeparator()

        if (currentConstruction != "")
            constructionsQueueTable.add(getQueueEntry(0, currentConstruction))
                    .expandX().fillX().row()
        else
            constructionsQueueTable.add("Pick a construction".toLabel()).pad(2f).row()

        constructionsQueueTable.addSeparator()
        constructionsQueueTable.add(getHeader("Construction queue".tr())).fillX()
        constructionsQueueTable.addSeparator()

        if (queue.isNotEmpty()) {
            queue.forEachIndexed { i, constructionName ->
                if (i != 0)  // This is already displayed as "Current construction"
                    constructionsQueueTable.add(getQueueEntry(i, constructionName))
                        .expandX().fillX().row()
                if (i != queue.size - 1)
                    constructionsQueueTable.addSeparator()
            }
        } else
            constructionsQueueTable.add("Queue empty".toLabel()).pad(2f).row()
    }

    private fun updateAvailableConstructions() {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val units = ArrayList<Table>()
        val buildableWonders = ArrayList<Table>()
        val buildableNationalWonders = ArrayList<Table>()
        val buildableBuildings = ArrayList<Table>()
        val specialConstructions = ArrayList<Table>()

        for (unit in city.getRuleset().units.values.filter { it.shouldBeDisplayed(cityConstructions) }) {
            val useStoredProduction = !cityConstructions.isBeingConstructedOrEnqueued(unit.name)
            val turnsToUnit = cityConstructions.turnsToConstruction(unit.name, useStoredProduction)
            var buttonText = unit.name.tr() + turnOrTurns(turnsToUnit)
            if(unit.requiredResource != null)
                buttonText += "\n"+"Consumes 1 [${unit.requiredResource}]".tr()

            val productionButton = getProductionButton(unit,
                    buttonText,
                    unit.getRejectionReason(cityConstructions))
            units.add(productionButton)
        }

        for (building in city.getRuleset().buildings.values.filter { it.shouldBeDisplayed(cityConstructions)}) {
            val turnsToBuilding = cityConstructions.turnsToConstruction(building.name)
            var buttonText = building.name.tr() + turnOrTurns(turnsToBuilding)
            if(building.requiredResource != null)
                buttonText += "\n"+"Consumes 1 [${building.requiredResource}]".tr()
            val productionTextButton = getProductionButton(building,
                    buttonText,
                    building.getRejectionReason(cityConstructions)
            )
            if (building.isWonder) buildableWonders += productionTextButton
            else if (building.isNationalWonder) buildableNationalWonders += productionTextButton
            else buildableBuildings += productionTextButton
        }

        for (specialConstruction in PerpetualConstruction.perpetualConstructionsMap.values
                .filter { it.shouldBeDisplayed(cityConstructions) }) {
            specialConstructions += getProductionButton(specialConstruction,
                    "Produce [${specialConstruction.name}]".tr()
                            + specialConstruction.getProductionTooltip(city))
        }

        availableConstructionsTable.addCategory("Units", units)
        availableConstructionsTable.addCategory("Wonders", buildableWonders)
        availableConstructionsTable.addCategory("National Wonders", buildableNationalWonders)
        availableConstructionsTable.addCategory("Buildings", buildableBuildings)
        availableConstructionsTable.addCategory("Other", specialConstructions)
    }

    private fun getQueueEntry(constructionQueueIndex: Int, name: String): Table {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val table = Table()
        table.align(Align.left).pad(5f)
        table.background = ImageGetter.getBackground(Color.BLACK)

        if (constructionQueueIndex == selectedQueueEntry)
            table.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))

        val isFirstConstructionOfItsKind = cityConstructions.isFirstConstructionOfItsKind(constructionQueueIndex, name)
        val turnsToComplete = cityConstructions.turnsToConstruction(name, isFirstConstructionOfItsKind)
        var text = name.tr() +
                if (name in PerpetualConstruction.perpetualConstructionsMap) "\n∞"
                else turnOrTurns(turnsToComplete)

        val constructionResource = cityConstructions.getConstruction(name).getResource()

        if(constructionResource != null)
            text += "\n"+"Consumes 1 [$constructionResource]".tr()

        table.defaults().pad(2f).minWidth(40f)
        if(isFirstConstructionOfItsKind) table.add(getProgressBar(name)).minWidth(5f)
        else table.add().minWidth(5f)
        table.add(ImageGetter.getConstructionImage(name).surroundWithCircle(40f)).padRight(10f)
        table.add(text.toLabel()).expandX().fillX().left()

        if (constructionQueueIndex > 0) table.add(getRaisePriorityButton(constructionQueueIndex, name, city)).right()
        else table.add().right()
        if (constructionQueueIndex != cityConstructions.constructionQueue.lastIndex)
            table.add(getLowerPriorityButton(constructionQueueIndex, name, city)).right()
        else table.add().right()

        table.touchable = Touchable.enabled
        table.onClick {
            cityScreen.selectedConstruction = cityConstructions.getConstruction(name)
            cityScreen.selectedTile = null
            selectedQueueEntry = constructionQueueIndex
            cityScreen.update()
        }
        return table
    }

    fun getProgressBar(constructionName:String): Table {
        val cityConstructions = cityScreen.city.cityConstructions
        val construction = cityConstructions.getConstruction(constructionName)
        if (construction is PerpetualConstruction) return Table()
        if (cityConstructions.getWorkDone(constructionName) == 0) return Table()

        val constructionPercentage = cityConstructions.getWorkDone(constructionName) /
                construction.getProductionCost(cityConstructions.cityInfo.civInfo).toFloat()
        return ImageGetter.getProgressBarVertical(2f, 30f, constructionPercentage,
                Color.BROWN.cpy().lerp(Color.WHITE, 0.5f), Color.WHITE)
    }

    private fun getProductionButton(construction: IConstruction, buttonText: String, rejectionReason: String = ""): Table {
        val pickProductionButton = Table()

        pickProductionButton.align(Align.left).pad(5f)
        pickProductionButton.background = ImageGetter.getBackground(Color.BLACK)
        pickProductionButton.touchable = Touchable.enabled

        if (!isSelectedQueueEntry() && cityScreen.selectedConstruction != null && cityScreen.selectedConstruction == construction) {
            pickProductionButton.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))
        }

        pickProductionButton.add(getProgressBar(construction.name)).padRight(5f)
        pickProductionButton.add(ImageGetter.getConstructionImage(construction.name).surroundWithCircle(40f)).padRight(10f)
        pickProductionButton.add(buttonText.toLabel()).expandX().fillX().left().row()

        // no rejection reason means we can build it!
        if(rejectionReason != "") {
            pickProductionButton.color = Color.GRAY
            pickProductionButton.add(rejectionReason.toLabel(Color.RED).apply{ setWrap(true)} )
                    .colspan(pickProductionButton.columns).fillX().left().padTop(2f)
        }
        pickProductionButton.onClick {
            cityScreen.selectedConstruction = construction
            cityScreen.selectedTile = null
            selectedQueueEntry = -1
            cityScreen.update()
        }

        return pickProductionButton
    }

    private fun isSelectedQueueEntry(): Boolean = selectedQueueEntry >= 0

    private fun getQueueButton(construction: IConstruction?): TextButton {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions
        val button: TextButton

        if (isSelectedQueueEntry()) {
            button = "Remove from queue".toTextButton()
            if (!UncivGame.Current.worldScreen.isPlayersTurn || city.isPuppet) button.disable()
            else {
                button.onClick {
                    cityConstructions.removeFromQueue(selectedQueueEntry,false)
                    cityScreen.selectedConstruction = null
                    selectedQueueEntry = -1
                    cityScreen.update()
                }
            }
        } else {
            button = "Add to queue".toTextButton()
            if (construction == null
                    || cityConstructions.isQueueFull()
                    || !cityConstructions.getConstruction(construction.name).isBuildable(cityConstructions)
                    || !UncivGame.Current.worldScreen.isPlayersTurn
                    || construction is PerpetualConstruction && cityConstructions.isBeingConstructedOrEnqueued(construction.name)
                    || city.isPuppet) {
                button.disable()
            } else {
                button.onClick {
                    cityConstructions.addToQueue(construction.name)
                    if (!construction.shouldBeDisplayed(cityConstructions)) cityScreen.selectedConstruction = null
                    cityScreen.update()
                    cityScreen.game.settings.addCompletedTutorialTask("Pick construction")
                }
            }
        }

        button.labelCell.pad(5f)
        return button
    }


    fun purchaseConstruction(construction: IConstruction) {
        val city = cityScreen.city
        if (!city.cityConstructions.purchaseConstruction(construction.name, selectedQueueEntry, false)) {
            Popup(cityScreen).apply {
                add("No space available to place [${construction.name}] near [${city.name}]".tr()).row()
                addCloseButton()
                open()
            }
            return
        }
        if (isSelectedQueueEntry()) {
            selectedQueueEntry = -1
            cityScreen.selectedConstruction = null
        }
        cityScreen.update()
    }

    private fun getBuyButton(construction: IConstruction?): TextButton {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val button = "".toTextButton()

        if (construction == null || !construction.canBePurchased()
        ) {
            // fully disable a "buy" button only for "priceless" buildings such as wonders
            // for all other cases, the price should be displayed
            button.setText("Buy".tr())
            button.disable()
        } else {
            val constructionGoldCost = construction.getGoldCost(city.civInfo)
            button.setText("Buy".tr() + " " + constructionGoldCost)
            button.add(ImageGetter.getStatIcon(Stat.Gold.name)).size(20f).padBottom(2f)

            button.onClick(UncivSound.Coin) {
                button.disable()
                cityScreen.closeAllPopups()

                val purchasePrompt = "Currently you have [${city.civInfo.gold}] gold.".tr() + "\n\n" +
                        "Would you like to purchase [${construction.name}] for [$constructionGoldCost] gold?".tr()
                YesNoPopup(purchasePrompt, { purchaseConstruction(construction) }, cityScreen, { cityScreen.update() }).open()
            }

            if ( !construction.isBuildable(cityConstructions)
                    || !UncivGame.Current.worldScreen.isPlayersTurn
                    || city.isPuppet || city.isInResistance()
                    || !city.canPurchase(construction)
                    || constructionGoldCost > city.civInfo.gold )
                button.disable()
        }

        button.labelCell.pad(5f)

        return button
    }

    private fun getRaisePriorityButton(constructionQueueIndex: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Up").surroundWithCircle(40f))
        if (UncivGame.Current.worldScreen.isPlayersTurn && !city.isPuppet) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.raisePriority(constructionQueueIndex)
                cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(name)
                cityScreen.selectedTile = null
                selectedQueueEntry = constructionQueueIndex - 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getLowerPriorityButton(constructionQueueIndex: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Down").surroundWithCircle(40f))
        if (UncivGame.Current.worldScreen.isPlayersTurn && !city.isPuppet) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.lowerPriority(constructionQueueIndex)
                cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(name)
                cityScreen.selectedTile = null
                selectedQueueEntry = constructionQueueIndex + 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getHeader(title: String): Table {
        val headerTable = Table()
        headerTable.background = ImageGetter.getBackground(ImageGetter.getBlue())
        headerTable.add(title.toLabel(fontSize = 24)).fillX()
        return headerTable
    }

    private fun Table.addCategory(title: String, list: ArrayList<Table>) {
        if (list.isEmpty()) return

        addSeparator()
        add(getHeader(title)).fill().row()
        addSeparator()

        for (table in list) {
            add(table).fill().left().row()
            if (table != list.last()) addSeparator()
        }
    }
}

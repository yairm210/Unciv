package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable

class ConstructionsTable(val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    /* -2 = Nothing, -1 = current construction, >= 0 queue entry */
    private var selectedQueueEntry = -2 // None

    private val constructionsQueueScrollPane: ScrollPane
    private val availableConstructionsScrollPane: ScrollPane

    private val constructionsQueueTable = Table()
    private val availableConstructionsTable = Table()
    private val buttons = Table()

    private val pad = 10f

    init {
        constructionsQueueScrollPane = ScrollPane(constructionsQueueTable.addBorder(2f, Color.WHITE))
        availableConstructionsScrollPane = ScrollPane(availableConstructionsTable.addBorder(2f, Color.WHITE))

        constructionsQueueTable.background = ImageGetter.getBackground(Color.BLACK)
        availableConstructionsTable.background = ImageGetter.getBackground(Color.BLACK)

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
        val usedHeight = constructionsQueueScrollPane.height + buttons.height + 2f * pad + 10f

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
        val currentConstruction = cityConstructions.currentConstruction
        val queue = cityConstructions.constructionQueue

        constructionsQueueTable.defaults().pad(0f)
        constructionsQueueTable.add(getHeader("Current construction".tr())).fillX()
        constructionsQueueTable.addSeparator()

        if (currentConstruction != "")
            constructionsQueueTable.add(getQueueEntry(-1, currentConstruction, queue.isEmpty(), selectedQueueEntry == -1))
                    .expandX().fillX().row()
         else
            constructionsQueueTable.add("Pick a construction".toLabel()).pad(2f).row()

        constructionsQueueTable.addSeparator()
        constructionsQueueTable.add(getHeader("Construction queue".tr())).fillX()
        constructionsQueueTable.addSeparator()

        if (queue.isNotEmpty()) {
            queue.forEachIndexed { i, constructionName ->
                constructionsQueueTable.add(getQueueEntry(i, constructionName, i == queue.size - 1, i == selectedQueueEntry))
                        .expandX().fillX().row()
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
            val turnsToUnit = cityConstructions.turnsToConstruction(unit.name)
            val productionButton = getProductionButton(unit.name,
                    unit.name.tr() + "\r\n" + turnsToUnit + turnOrTurns(turnsToUnit),
                    unit.getRejectionReason(cityConstructions))
            units.add(productionButton)
        }

        for (building in city.getRuleset().buildings.values.filter { it.shouldBeDisplayed(cityConstructions)}) {
            val turnsToBuilding = cityConstructions.turnsToConstruction(building.name)
            val productionTextButton = getProductionButton(building.name,
                    building.name.tr() + "\r\n" + turnsToBuilding + turnOrTurns(turnsToBuilding),
                    building.getRejectionReason(cityConstructions)
            )
            if (building.isWonder) buildableWonders += productionTextButton
            else if (building.isNationalWonder) buildableNationalWonders += productionTextButton
            else buildableBuildings += productionTextButton
        }

        for (specialConstruction in SpecialConstruction.getSpecialConstructions().filter { it.shouldBeDisplayed(cityConstructions) }) {
            specialConstructions += getProductionButton(specialConstruction.name,
                    "Produce [${specialConstruction.name}]".tr())
        }

        availableConstructionsTable.addCategory("Units", units)
        availableConstructionsTable.addCategory("Wonders", buildableWonders)
        availableConstructionsTable.addCategory("National Wonders", buildableNationalWonders)
        availableConstructionsTable.addCategory("Buildings", buildableBuildings)
        availableConstructionsTable.addCategory("Other", specialConstructions)
    }

    private fun getQueueEntry(idx: Int, name: String, isLast: Boolean, isSelected: Boolean): Table {
        val city = cityScreen.city
        val table = Table()
        table.align(Align.left).pad(5f)
        table.background = ImageGetter.getBackground(Color.BLACK)

        if (isSelected)
            table.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))

        val turnsToComplete = cityScreen.city.cityConstructions.turnsToConstruction(name)
        val text = name.tr() + "\r\n" + turnsToComplete + turnOrTurns(turnsToComplete)

        table.defaults().pad(2f).minWidth(40f)
        table.add(ImageGetter.getConstructionImage(name).surroundWithCircle(40f)).padRight(10f)
        table.add(text.toLabel()).expandX().fillX().left()

        if (idx >= 0) table.add(getHigherPrioButton(idx, name, city)).right()
        else table.add().right()
        if (!isLast) table.add(getLowerPrioButton(idx, name, city)).right()
        else table.add().right()

        table.touchable = Touchable.enabled
        table.onClick {
            cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(name)
            cityScreen.selectedTile = null
            selectedQueueEntry = idx
            cityScreen.update()
        }
        return table
    }

    private fun getProductionButton(construction: String, buttonText: String, rejectionReason: String = "", isSelectable: Boolean = true): Table {
        val pickProductionButton = Table()

        pickProductionButton.align(Align.left).pad(5f)
        pickProductionButton.background = ImageGetter.getBackground(Color.BLACK)
        pickProductionButton.touchable = Touchable.enabled

        if (!isSelectedQueueEntry() && cityScreen.selectedConstruction != null && cityScreen.selectedConstruction!!.name == construction) {
            pickProductionButton.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))
        }

        pickProductionButton.add(ImageGetter.getConstructionImage(construction).surroundWithCircle(40f)).padRight(10f)
        pickProductionButton.add(buttonText.toLabel()).expandX().fillX().left()

        // no rejection reason means we can build it!
        if(rejectionReason == "") {
            pickProductionButton.onClick {
                cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(construction)
                cityScreen.selectedTile = null
                selectedQueueEntry = -2
                cityScreen.update()
            }
        } else {
            pickProductionButton.color = Color.GRAY
            pickProductionButton.row()
            pickProductionButton.add(rejectionReason.toLabel(Color.RED)).colspan(pickProductionButton.columns).fillX().left().padTop(2f)
        }

        return pickProductionButton
    }
    private fun isSelectedQueueEntry(): Boolean = selectedQueueEntry > -2

    private fun getQueueButton(construction: IConstruction?): TextButton {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions
        val button: TextButton

        if (isSelectedQueueEntry()) {
            button = TextButton("Remove from queue".tr(), CameraStageBaseScreen.skin)
            if (UncivGame.Current.worldScreen.isPlayersTurn && !city.isPuppet) {
                button.onClick {
                    cityConstructions.removeFromQueue(selectedQueueEntry)
                    cityScreen.selectedConstruction = null
                    selectedQueueEntry = -2
                    cityScreen.update()
                }
            } else {
                button.disable()
            }
        } else {
            button = TextButton("Add to queue".tr(), CameraStageBaseScreen.skin)
            if (construction != null
                    && !cityConstructions.isQueueFull()
                    && cityConstructions.getConstruction(construction.name).isBuildable(cityConstructions)
                    && UncivGame.Current.worldScreen.isPlayersTurn
                    && !city.isPuppet) {
                button.onClick {
                    cityConstructions.addToQueue(construction.name)
                    cityScreen.update()
                }
            } else {
                button.disable()
            }
        }

        button.labelCell.pad(5f)
        return button
    }

    private fun getBuyButton(construction: IConstruction?): TextButton {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val button = TextButton("", CameraStageBaseScreen.skin)

        if (construction != null
                && construction.canBePurchased()
                && UncivGame.Current.worldScreen.isPlayersTurn
                && !city.isPuppet) {
            val constructionGoldCost = construction.getGoldCost(city.civInfo)
            purchaseConstructionButton = TextButton("Buy for [$constructionGoldCost] gold".tr(), CameraStageBaseScreen.skin)
            purchaseConstructionButton.onClick(UncivSound.Coin) {
                YesNoPopupTable("Would you like to purchase [${construction.name}] for [$constructionGoldCost] gold?".tr(), {
                    cityConstructions.purchaseConstruction(construction.name)
                    if (isSelectedQueueEntry()) {
                        cityConstructions.removeFromQueue(selectedQueueEntry)
                        selectedQueueEntry = -2
                        cityScreen.selectedConstruction = null
                    }
                    cityScreen.update()
                }, cityScreen)
            }

            if (constructionGoldCost > city.civInfo.gold)
                button.disable()

        } else {
            button.setText("Buy".tr())
            button.disable()
        }

        button.labelCell.pad(5f)

        return button
    }

    private fun getHigherPrioButton(idx: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Up").surroundWithCircle(40f))
        if (UncivGame.Current.worldScreen.isPlayersTurn && !city.isPuppet) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.higherPrio(idx)
                cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(name)
                cityScreen.selectedTile = null
                selectedQueueEntry = idx - 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getLowerPrioButton(idx: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Down").surroundWithCircle(40f))
        if (UncivGame.Current.worldScreen.isPlayersTurn && !city.isPuppet) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.lowerPrio(idx)
                cityScreen.selectedConstruction = cityScreen.city.cityConstructions.getConstruction(name)
                cityScreen.selectedTile = null
                selectedQueueEntry = idx + 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun turnOrTurns(number: Int): String = if (number > 1) " {turns}".tr() else " {turn}".tr()

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
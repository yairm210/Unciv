package com.unciv.ui.cityscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.*
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class CityConstructionsTable(val cityScreen: CityScreen) {
    /* -1 = Nothing, >= 0 queue entry (0 = current construction) */
    private var selectedQueueEntry = -1 // None
    var improvementBuildingToConstruct: Building? = null

    private val upperTable = Table(CameraStageBaseScreen.skin)
    private val showCityInfoTableButton = "Show stats drilldown".toTextButton()
    private val constructionsQueueScrollPane: ScrollPane
    private val constructionsQueueTable = Table()
    private val buyButtonsTable = Table()

    private val lowerTable = Table()
    private val availableConstructionsScrollPane: ScrollPane
    private val availableConstructionsTable = Table()
    private val lowerTableScrollCell: Cell<ScrollPane>

    private val pad = 10f
    private val posFromEdge = 5f
    private val stageHeight = cityScreen.stage.height

    var isVisible: Boolean
        get() = upperTable.isVisible
        set(value) {
            upperTable.isVisible = value
            lowerTable.isVisible = value
        }

    init {
        showCityInfoTableButton.onClick {
            cityScreen.showConstructionsTable = false
            cityScreen.update()
        }

        constructionsQueueScrollPane = ScrollPane(constructionsQueueTable.addBorder(2f, Color.WHITE))
        constructionsQueueScrollPane.setOverscroll(false, false)
        constructionsQueueTable.background = ImageGetter.getBackground(Color.BLACK)

        upperTable.defaults().left().top()
        upperTable.add(showCityInfoTableButton).padLeft(pad).padBottom(pad).row()
        upperTable.add(constructionsQueueScrollPane)
            .maxHeight(stageHeight / 3 - 10f)
            .padBottom(pad).row()
        upperTable.add(buyButtonsTable).padBottom(pad).row()

        availableConstructionsScrollPane = ScrollPane(availableConstructionsTable.addBorder(2f, Color.WHITE))
        availableConstructionsScrollPane.setOverscroll(false, false)
        availableConstructionsTable.background = ImageGetter.getBackground(Color.BLACK)
        lowerTableScrollCell = lowerTable.add(availableConstructionsScrollPane).bottom()
        lowerTable.row()
    }

    fun addActorsToStage() {
        cityScreen.stage.addActor(upperTable)
        cityScreen.stage.addActor(lowerTable)
        lowerTable.setPosition(posFromEdge, posFromEdge, Align.bottomLeft)
    }

    fun update(selectedConstruction: IConstruction?) {
        updateButtons(selectedConstruction)
        updateConstructionQueue()
        upperTable.pack()
        // This should work when set once only in addActorsToStage, but it doesn't (table invisible - why?)
        upperTable.setPosition(posFromEdge, stageHeight - posFromEdge, Align.topLeft)
        
        updateAvailableConstructions()
        lowerTable.pack()
        lowerTableScrollCell.maxHeight(stageHeight - upperTable.height - 2 * posFromEdge)
    }

    private fun updateButtons(construction: IConstruction?) {
        buyButtonsTable.clear()
        buyButtonsTable.add(getQueueButton(construction)).padRight(5f)
        if (construction != null && construction !is PerpetualConstruction)
            for (button in getBuyButtons(construction as INonPerpetualConstruction))
                buyButtonsTable.add(button).padRight(5f)
    }

    private fun updateConstructionQueue() {
        val queueScrollY = constructionsQueueScrollPane.scrollY
        constructionsQueueTable.clear()

        val city = cityScreen.city
        val cityConstructions = city.cityConstructions
        val currentConstruction = cityConstructions.currentConstructionFromQueue
        val queue = cityConstructions.constructionQueue

        constructionsQueueTable.defaults().pad(0f)
        constructionsQueueTable.add(getHeader("Current construction")).fillX()

        constructionsQueueTable.addSeparator()

        if (currentConstruction != "")
            constructionsQueueTable.add(getQueueEntry(0, currentConstruction))
                    .expandX().fillX().row()
        else
            constructionsQueueTable.add("Pick a construction".toLabel()).pad(2f).row()

        constructionsQueueTable.addSeparator()

        if (queue.size > 1) {
            constructionsQueueTable.add(getHeader("Construction queue")).fillX()
            constructionsQueueTable.addSeparator()
            queue.forEachIndexed { i, constructionName ->
                // The first entry is already displayed as "Current construction"
                if (i != 0) {
                    constructionsQueueTable.add(getQueueEntry(i, constructionName))
                        .expandX().fillX().row()
                    if (i != queue.size - 1)
                        constructionsQueueTable.addSeparator()
                }
            }
        }

        constructionsQueueScrollPane.layout()
        constructionsQueueScrollPane.scrollY = queueScrollY
        constructionsQueueScrollPane.updateVisualScroll()
    }

    private fun getConstructionButtonDTOs(): ArrayList<ConstructionButtonDTO> {
        val constructionButtonDTOList = ArrayList<ConstructionButtonDTO>()

        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val constructionsSequence = city.getRuleset().units.values.asSequence() +
                city.getRuleset().buildings.values.asSequence()
        for (entry in constructionsSequence.filter { it.shouldBeDisplayed(cityConstructions) }) {
            val useStoredProduction = entry is Building || !cityConstructions.isBeingConstructedOrEnqueued(entry.name)
            var buttonText = entry.name.tr() + cityConstructions.getTurnsToConstructionString(entry.name, useStoredProduction)
            for ((resource, amount) in entry.getResourceRequirements()) {
                buttonText += "\n" + (if (amount == 1) "Consumes 1 [$resource]"
                    else "Consumes [$amount] [$resource]").tr()
            }

            constructionButtonDTOList.add(ConstructionButtonDTO(entry, buttonText,
                entry.getRejectionReason(cityConstructions)))
        }

        for (specialConstruction in PerpetualConstruction.perpetualConstructionsMap.values
                .filter { it.shouldBeDisplayed(cityConstructions) }) {
            constructionButtonDTOList.add(ConstructionButtonDTO(specialConstruction,
                    "Produce [${specialConstruction.name}]".tr()
                            + specialConstruction.getProductionTooltip(city)))
        }

        return constructionButtonDTOList
    }

    private fun updateAvailableConstructions() {
        val constructionsScrollY = availableConstructionsScrollPane.scrollY

        if (!availableConstructionsTable.hasChildren()) { //
            availableConstructionsTable.add("Loading...".toLabel()).pad(10f)
        }

        thread {
            // Since this can be a heavy operation and leads to many ANRs on older phones we put the metadata-gathering in another thread.
            val constructionButtonDTOList = getConstructionButtonDTOs()
            Gdx.app.postRunnable {
                val units = ArrayList<Table>()
                val buildableWonders = ArrayList<Table>()
                val buildableNationalWonders = ArrayList<Table>()
                val buildableBuildings = ArrayList<Table>()
                val specialConstructions = ArrayList<Table>()

                var maxButtonWidth = constructionsQueueTable.width
                for (dto in constructionButtonDTOList) {
                    val constructionButton = getConstructionButton(dto)
                    when (dto.construction) {
                        is BaseUnit -> units.add(constructionButton)
                        is Building -> {
                            when {
                                dto.construction.isWonder -> buildableWonders += constructionButton
                                dto.construction.isNationalWonder -> buildableNationalWonders += constructionButton
                                else -> buildableBuildings += constructionButton
                            }
                        }
                        is PerpetualConstruction -> specialConstructions.add(constructionButton)
                    }
                    if (constructionButton.needsLayout()) constructionButton.pack()
                    maxButtonWidth = max(maxButtonWidth, constructionButton.width)
                }

                availableConstructionsTable.apply { 
                    clear()
                    defaults().left().bottom()
                    addCategory("Units", units, maxButtonWidth)
                    addCategory("Wonders", buildableWonders, maxButtonWidth)
                    addCategory("National Wonders", buildableNationalWonders, maxButtonWidth)
                    addCategory("Buildings", buildableBuildings, maxButtonWidth)
                    addCategory("Other", specialConstructions, maxButtonWidth)
                    pack()
                }

                availableConstructionsScrollPane.apply { 
                    setSize(maxButtonWidth, min(availableConstructionsTable.prefHeight, lowerTableScrollCell.maxHeight))
                    layout()
                    scrollY = constructionsScrollY
                    updateVisualScroll()
                }
                lowerTable.pack()
            }
        }
    }

    private fun getQueueEntry(constructionQueueIndex: Int, constructionName: String): Table {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions

        val table = Table()
        table.align(Align.left).pad(5f)
        table.background = ImageGetter.getBackground(Color.BLACK)

        if (constructionQueueIndex == selectedQueueEntry)
            table.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))

        val isFirstConstructionOfItsKind = cityConstructions.isFirstConstructionOfItsKind(constructionQueueIndex, constructionName)

        var text = constructionName.tr() +
                if (constructionName in PerpetualConstruction.perpetualConstructionsMap) "\n∞"
                else cityConstructions.getTurnsToConstructionString(constructionName, isFirstConstructionOfItsKind)

        val constructionResource = cityConstructions.getConstruction(constructionName).getResourceRequirements()
        for ((resource, amount) in constructionResource)
            text += if (amount == 1) "\n" + "Consumes 1 [$resource]".tr()
                else "\n" + "Consumes [$amount] [$resource]".tr()

        table.defaults().pad(2f).minWidth(40f)
        if (isFirstConstructionOfItsKind) table.add(getProgressBar(constructionName)).minWidth(5f)
        else table.add().minWidth(5f)
        table.add(ImageGetter.getConstructionImage(constructionName).surroundWithCircle(40f)).padRight(10f)
        table.add(text.toLabel()).expandX().fillX().left()

        if (constructionQueueIndex > 0) table.add(getRaisePriorityButton(constructionQueueIndex, constructionName, city)).right()
        else table.add().right()
        if (constructionQueueIndex != cityConstructions.constructionQueue.lastIndex)
            table.add(getLowerPriorityButton(constructionQueueIndex, constructionName, city)).right()
        else table.add().right()

        table.add(getRemoveFromQueueButton(constructionQueueIndex, city)).right()

        table.touchable = Touchable.enabled
        table.onClick {
            cityScreen.selectedConstruction = cityConstructions.getConstruction(constructionName)
            cityScreen.selectedTile = null
            selectedQueueEntry = constructionQueueIndex
            cityScreen.update()
        }
        return table
    }

    private fun getProgressBar(constructionName: String): Group {
        val cityConstructions = cityScreen.city.cityConstructions
        val construction = cityConstructions.getConstruction(constructionName)
        if (construction is PerpetualConstruction) return Table()
        if (cityConstructions.getWorkDone(constructionName) == 0) return Table()

        val constructionPercentage = cityConstructions.getWorkDone(constructionName) /
                (construction as INonPerpetualConstruction).getProductionCost(cityConstructions.cityInfo.civInfo).toFloat()
        return ImageGetter.getProgressBarVertical(2f, 30f, constructionPercentage,
                Color.BROWN.cpy().lerp(Color.WHITE, 0.5f), Color.WHITE)
    }

    class ConstructionButtonDTO(val construction: IConstruction, val buttonText: String, val rejectionReason: String = "")

    private fun getConstructionButton(constructionButtonDTO: ConstructionButtonDTO): Table {
        val construction = constructionButtonDTO.construction
        val pickConstructionButton = Table()

        pickConstructionButton.align(Align.left).pad(5f)
        pickConstructionButton.background = ImageGetter.getBackground(Color.BLACK)
        pickConstructionButton.touchable = Touchable.enabled

        if (!isSelectedQueueEntry() && cityScreen.selectedConstruction != null && cityScreen.selectedConstruction == construction) {
            pickConstructionButton.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK, 0.5f))
        }

        pickConstructionButton.add(getProgressBar(construction.name)).padRight(5f)
        pickConstructionButton.add(ImageGetter.getConstructionImage(construction.name).surroundWithCircle(40f)).padRight(10f)
        pickConstructionButton.add(constructionButtonDTO.buttonText.toLabel()).expandX().fillX()

        if (!cannotAddConstructionToQueue(construction, cityScreen.city, cityScreen.city.cityConstructions)) {
            val addToQueueButton = ImageGetter.getImage("OtherIcons/New").apply { color = Color.BLACK }.surroundWithCircle(40f)
            addToQueueButton.onClick(getConstructionSound(construction)) {
                addConstructionToQueue(construction, cityScreen.city.cityConstructions)
            }
            pickConstructionButton.add(addToQueueButton)
        }
        pickConstructionButton.row()

        // no rejection reason means we can build it!
        if (constructionButtonDTO.rejectionReason != "") {
            pickConstructionButton.color = Color.GRAY
            pickConstructionButton.add(constructionButtonDTO.rejectionReason.toLabel(Color.RED).apply { wrap = true })
                    .colspan(pickConstructionButton.columns).fillX().left().padTop(2f)
        }
        pickConstructionButton.onClick {
            cityScreen.selectedConstruction = construction
            cityScreen.selectedTile = null
            selectedQueueEntry = -1
            cityScreen.update()
        }

        return pickConstructionButton
    }

    private fun isSelectedQueueEntry(): Boolean = selectedQueueEntry >= 0

    private fun cannotAddConstructionToQueue(construction: IConstruction, city: CityInfo, cityConstructions: CityConstructions): Boolean {
        return cityConstructions.isQueueFull()
                || !cityConstructions.getConstruction(construction.name).isBuildable(cityConstructions)
                || !cityScreen.canChangeState
                || construction is PerpetualConstruction && cityConstructions.isBeingConstructedOrEnqueued(construction.name)
                || city.isPuppet
    }

    private fun getQueueButton(construction: IConstruction?): TextButton {
        val city = cityScreen.city
        val cityConstructions = city.cityConstructions
        val button: TextButton

        if (isSelectedQueueEntry()) {
            button = "Remove from queue".toTextButton()
            if (!cityScreen.canChangeState || city.isPuppet)
                button.disable()
            else {
                button.onClick {
                    cityConstructions.removeFromQueue(selectedQueueEntry, false)
                    cityScreen.selectedConstruction = null
                    selectedQueueEntry = -1
                    cityScreen.update()
                }
            }
        } else {
            button = "Add to queue".toTextButton()
            if (construction == null
                    || cannotAddConstructionToQueue(construction, city, cityConstructions)) {
                button.disable()
            } else {
                button.onClick(getConstructionSound(construction)) {
                    addConstructionToQueue(construction, cityConstructions)
                }
            }
        }

        button.labelCell.pad(5f)
        return button
    }

    private fun addConstructionToQueue(construction: IConstruction, cityConstructions: CityConstructions) {
        if (construction is Building && construction.uniqueObjects.any { it.placeholderText == "Creates a [] improvement on a specific tile" }) {
            cityScreen.selectedTile
            improvementBuildingToConstruct = construction
            return
        }

        cityConstructions.addToQueue(construction.name)
        if (!construction.shouldBeDisplayed(cityConstructions)) // For buildings - unlike units which can be queued multiple times
            cityScreen.selectedConstruction = null
        cityScreen.update()
        cityScreen.game.settings.addCompletedTutorialTask("Pick construction")
    }

    private fun getConstructionSound(construction: IConstruction): UncivSound {
        return when(construction) {
            is Building -> UncivSound.Construction
            is BaseUnit -> UncivSound.Promote
            PerpetualConstruction.gold -> UncivSound.Coin
            PerpetualConstruction.science -> UncivSound.Paper
            else -> UncivSound.Click
        }
    }

    private fun purchaseConstruction(construction: INonPerpetualConstruction, stat: Stat = Stat.Gold) {
        val city = cityScreen.city
        if (!city.cityConstructions.purchaseConstruction(construction.name, selectedQueueEntry, false, stat)) {
            Popup(cityScreen).apply {
                add("No space available to place [${construction.name}] near [${city.name}]".tr()).row()
                addCloseButton()
                open()
            }
            return
        }
        if (isSelectedQueueEntry() || cityScreen.selectedConstruction?.isBuildable(city.cityConstructions) != true) {
            selectedQueueEntry = -1
            cityScreen.selectedConstruction = null
        }
        cityScreen.update()
    }
    
    private fun getBuyButtons(construction: INonPerpetualConstruction?): List<TextButton> {
        return Stat.statsUsableToBuy.mapNotNull { getBuyButton(construction, it) }
    }

    private fun getBuyButton(construction: INonPerpetualConstruction?, stat: Stat = Stat.Gold): TextButton? {
        if (stat !in Stat.statsUsableToBuy || construction == null)
            return null

        val city = cityScreen.city
        val button = "".toTextButton()

        if (!construction.canBePurchasedWithStat(city, stat) && !city.civInfo.gameInfo.gameParameters.godMode) {
            // This can't ever be bought with the given currency.
            // We want one disabled "buy" button without a price for "priceless" buildings such as wonders
            // We don't want such a button when the construction can be bought using a different currency
            if (stat != Stat.Gold || construction.canBePurchasedWithAnyStat(city))
                return null
            button.setText("Buy".tr())
            button.disable()
        } else {
            val constructionBuyCost = construction.getStatBuyCost(city, stat)!!
            button.setText("Buy".tr() + " " + constructionBuyCost + stat.character)

            button.onClick(stat.purchaseSound) {
                button.disable()
                cityScreen.closeAllPopups()
                
                val purchasePrompt = "Currently you have [${city.getStatReserve(stat)}] [${stat.name}].".tr() + "\n\n" +
                        "Would you like to purchase [${construction.name}] for [$constructionBuyCost] [${stat.character}]?".tr()
                YesNoPopup(
                    purchasePrompt,
                    action = { purchaseConstruction(construction, stat) },
                    screen = cityScreen,
                    restoreDefault = { cityScreen.update() }
                ).apply {
                    promptLabel.setAlignment(Align.center)
                    open()
                }
            }
            
            if (!cityScreen.canChangeState
                || !construction.isPurchasable(city.cityConstructions)
                || city.isPuppet 
                || city.isInResistance()
                || !city.canPurchase(construction)
                || (constructionBuyCost > city.getStatReserve(stat) && !city.civInfo.gameInfo.gameParameters.godMode)
            ) button.disable()
        }

        button.labelCell.pad(5f)

        return button
    }
    
    private fun getRaisePriorityButton(constructionQueueIndex: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Up").surroundWithCircle(40f))
        if (cityScreen.canChangeState && !city.isPuppet) {
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
        if (cityScreen.canChangeState && !city.isPuppet) {
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

    private fun getRemoveFromQueueButton(constructionQueueIndex: Int, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Stop").surroundWithCircle(40f))
        if (cityScreen.canChangeState && !city.isPuppet) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.removeFromQueue(constructionQueueIndex, false)
                cityScreen.selectedConstruction = null
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getHeader(title: String): Table {
        return Table()
                .background(ImageGetter.getBackground(ImageGetter.getBlue()))
                .addCell(title.toLabel(fontSize = 24))
                .pad(4f)
    }

    private fun resizeAvailableConstructionsScrollPane() {
        availableConstructionsScrollPane.height = min(availableConstructionsTable.prefHeight, lowerTableScrollCell.maxHeight)
        lowerTable.pack()
    }

    private fun Table.addCategory(title: String, list: ArrayList<Table>, prefWidth: Float) {
        if (list.isEmpty()) return

        if (rows > 0) addSeparator()
        val expander = ExpanderTab(
            title,
            defaultPad = 0f,
            expanderWidth = prefWidth,
            persistenceID = "CityConstruction.$title",
            onChange = { resizeAvailableConstructionsScrollPane() }
        ) {
            for (table in list) {
                it.addSeparator(colSpan = 1)
                it.add(table).left().row()
            }
        }
        add(expander).prefWidth(prefWidth).growX().row()
    }
}

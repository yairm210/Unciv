package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.closeAllPopups
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.addCell
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.getConsumesAmountString
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.packIfNeeded
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlin.math.max
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/**
 * Manager to hold and coordinate two widgets for the city screen left side:
 * - Construction queue with switch to [ConstructionInfoTable] button and the enqueue / buy buttons.
 *   The queue is scrollable, limited to one third of the stage height.
 * - Available constructions display, scrolling, grouped with expanders and therefore of dynamic height.
 */
class CityConstructionsTable(private val cityScreen: CityScreen) {
    /* -1 = Nothing, >= 0 queue entry (0 = current construction) */
    private var selectedQueueEntry = -1 // None
    private var preferredBuyStat = Stat.Gold  // Used for keyboard buy

    private val upperTable = Table(BaseScreen.skin)
    private val showCityInfoTableButton = "Show stats drilldown".toTextButton()
    private val constructionsQueueScrollPane: ScrollPane
    private val constructionsQueueTable = Table()
    private val buyButtonsTable = Table()

    private val lowerTable = Table()
    private val availableConstructionsScrollPane: ScrollPane
    private val availableConstructionsTable = Table()
    private val lowerTableScrollCell: Cell<ScrollPane>

    private val pad = 10f
    private val posFromEdge = CityScreen.posFromEdge
    private val stageHeight = cityScreen.stage.height

    /** Gets or sets visibility of [both widgets][CityConstructionsTable] */
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

    /** Forces layout calculation and returns the upper Table's (construction queue) width */
    fun getUpperWidth() = upperTable.packIfNeeded().width
    /** Forces layout calculation and returns the lower Table's (available constructions) width
     *  - or - the upper Table's width, whichever is greater (in case the former only contains "Loading...")
     */
    fun getLowerWidth() = max(lowerTable.packIfNeeded().width, getUpperWidth())

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

        city.cityStats.updateTileStats() // only once
        for (entry in constructionsSequence.filter { it.shouldBeDisplayed(cityConstructions) }) {
            val useStoredProduction = entry is Building || !cityConstructions.isBeingConstructedOrEnqueued(entry.name)
            var buttonText = entry.name.tr() + cityConstructions.getTurnsToConstructionString(entry.name, useStoredProduction)
            for ((resource, amount) in entry.getResourceRequirements()) {
                buttonText += "\n" + resource.getConsumesAmountString(amount).tr()
            }

            constructionButtonDTOList.add(
                ConstructionButtonDTO(
                    entry,
                    buttonText,
                    entry.getRejectionReasons(cityConstructions).getMostImportantRejectionReason()
                )
            )
        }

        for (specialConstruction in PerpetualConstruction.perpetualConstructionsMap.values
                .filter { it.shouldBeDisplayed(cityConstructions) }
        ) {
            constructionButtonDTOList.add(
                ConstructionButtonDTO(
                    specialConstruction,
                    "Produce [${specialConstruction.name}]".tr() + specialConstruction.getProductionTooltip(city)
                )
            )
        }

        return constructionButtonDTOList
    }

    private fun updateAvailableConstructions() {
        val constructionsScrollY = availableConstructionsScrollPane.scrollY

        if (!availableConstructionsTable.hasChildren()) { //
            availableConstructionsTable.add(Constants.loading.toLabel()).pad(10f)
        }

        Concurrency.run("Construction info gathering - ${cityScreen.city.name}") {
            // Since this can be a heavy operation and leads to many ANRs on older phones we put the metadata-gathering in another thread.
            val constructionButtonDTOList = getConstructionButtonDTOs()
            launchOnGLThread {
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
                    maxButtonWidth = max(maxButtonWidth, constructionButton.packIfNeeded().width)
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
            table.background = ImageGetter.getBackground(Color.GREEN.darken(0.5f))

        val isFirstConstructionOfItsKind = cityConstructions.isFirstConstructionOfItsKind(constructionQueueIndex, constructionName)

        var text = constructionName.tr() +
                if (constructionName in PerpetualConstruction.perpetualConstructionsMap) "\n∞"
                else cityConstructions.getTurnsToConstructionString(constructionName, isFirstConstructionOfItsKind)

        val constructionResource = cityConstructions.getConstruction(constructionName).getResourceRequirements()
        for ((resource, amount) in constructionResource)
            text += "\n" + resource.getConsumesAmountString(amount).tr()

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
            cityScreen.selectConstruction(constructionName)
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
                Color.BROWN.brighten(0.5f), Color.WHITE)
    }

    private class ConstructionButtonDTO(val construction: IConstruction, val buttonText: String, val rejectionReason: String? = null)

    private fun getConstructionButton(constructionButtonDTO: ConstructionButtonDTO): Table {
        val construction = constructionButtonDTO.construction
        val pickConstructionButton = Table()

        pickConstructionButton.align(Align.left).pad(5f)
        pickConstructionButton.background = ImageGetter.getBackground(Color.BLACK)
        pickConstructionButton.touchable = Touchable.enabled

        if (!isSelectedQueueEntry() && cityScreen.selectedConstruction == construction) {
            pickConstructionButton.background = ImageGetter.getBackground(Color.GREEN.darken(0.5f))
        }

        pickConstructionButton.add(getProgressBar(construction.name)).padRight(5f)
        pickConstructionButton.add(ImageGetter.getConstructionImage(construction.name).surroundWithCircle(40f)).padRight(10f)
        pickConstructionButton.add(constructionButtonDTO.buttonText.toLabel()).expandX().fillX()

        if (!cannotAddConstructionToQueue(construction, cityScreen.city, cityScreen.city.cityConstructions)) {
            val addToQueueButton = ImageGetter.getImage("OtherIcons/New").apply { color = Color.BLACK }.surroundWithCircle(40f)
            addToQueueButton.onClick(UncivSound.Silent) {
                addConstructionToQueue(construction, cityScreen.city.cityConstructions)
            }
            pickConstructionButton.add(addToQueueButton)
        }
        pickConstructionButton.row()

        // no rejection reason means we can build it!
        if (constructionButtonDTO.rejectionReason != null) {
            pickConstructionButton.color = Color.GRAY
            pickConstructionButton.add(constructionButtonDTO.rejectionReason.toLabel(Color.RED).apply { wrap = true })
                    .colspan(pickConstructionButton.columns).fillX().left().padTop(2f)
        }
        pickConstructionButton.onClick {
            cityScreen.selectConstruction(construction)
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
            if (!cityScreen.canCityBeChanged())
                button.disable()
            else {
                button.onClick {
                    cityConstructions.removeFromQueue(selectedQueueEntry, false)
                    cityScreen.clearSelection()
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
                button.onClick(UncivSound.Silent) {
                    addConstructionToQueue(construction, cityConstructions)
                }
            }
        }

        button.labelCell.pad(5f)
        return button
    }

    private fun addConstructionToQueue(construction: IConstruction, cityConstructions: CityConstructions) {
        // Some evil person decided to double tap real fast - #4977
        if (cannotAddConstructionToQueue(construction, cityScreen.city, cityConstructions))
            return

        // UniqueType.CreatesOneImprovement support - don't add yet, postpone until target tile for the improvement is selected
        if (construction is Building && construction.hasCreateOneImprovementUnique()) {
            cityScreen.startPickTileForCreatesOneImprovement(construction, Stat.Gold, false)
            return
        }
        cityScreen.stopPickTileForCreatesOneImprovement()

        SoundPlayer.play(getConstructionSound(construction))

        cityConstructions.addToQueue(construction.name)
        if (!construction.shouldBeDisplayed(cityConstructions)) // For buildings - unlike units which can be queued multiple times
            cityScreen.clearSelection()
        cityScreen.update()
        cityScreen.game.settings.addCompletedTutorialTask("Pick construction")
    }

    private fun getConstructionSound(construction: IConstruction): UncivSound {
        return when(construction) {
            is Building -> UncivSound.Construction
            is BaseUnit -> UncivSound.Promote
            PerpetualConstruction.gold -> UncivSound.Coin
            PerpetualConstruction.science -> UncivSound.Paper
            PerpetualConstruction.culture -> UncivSound.Policy
            PerpetualConstruction.faith -> UncivSound.Choir
            else -> UncivSound.Click
        }
    }

    private fun getBuyButtons(construction: INonPerpetualConstruction?): List<TextButton> {
        return Stat.statsUsableToBuy.mapNotNull { getBuyButton(construction, it) }
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
            button.setText("Buy".tr() + " " + constructionBuyCost + stat.character)

            button.onActivation {
                button.disable()
                buyButtonOnClick(construction, stat)
            }
            button.isEnabled = isConstructionPurchaseAllowed(construction, stat, constructionBuyCost)
            button.keyShortcuts.add('B')
            button.addTooltip('B')  // The key binding is done in CityScreen constructor
            preferredBuyStat = stat  // Not very intelligent, but the least common currency "wins"
        }

        button.labelCell.pad(5f)

        return button
    }

    private fun buyButtonOnClick(construction: INonPerpetualConstruction, stat: Stat = preferredBuyStat) {
        if (construction !is Building || !construction.hasCreateOneImprovementUnique())
            return askToBuyConstruction(construction, stat)
        if (selectedQueueEntry < 0)
            return cityScreen.startPickTileForCreatesOneImprovement(construction, stat, true)
        // Buying a UniqueType.CreatesOneImprovement building from queue must pass down
        // the already selected tile, otherwise a new one is chosen from Automation code.
        val improvement = construction.getImprovementToCreate(cityScreen.city.getRuleset())!!
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
        tile: TileInfo? = null
    ) {
        if (!isConstructionPurchaseShown(construction, stat)) return
        val city = cityScreen.city
        val constructionBuyCost = construction.getStatBuyCost(city, stat)!!
        if (!isConstructionPurchaseAllowed(construction, stat, constructionBuyCost)) return

        cityScreen.closeAllPopups()

        val purchasePrompt = "Currently you have [${city.getStatReserve(stat)}] [${stat.name}].".tr() + "\n\n" +
                "Would you like to purchase [${construction.name}] for [$constructionBuyCost] [${stat.character}]?".tr()
        ConfirmPopup(
            cityScreen,
            purchasePrompt,
            "Purchase",
            true,
            restoreDefault = { cityScreen.update() }
        ) { purchaseConstruction(construction, stat, tile) }.open()
    }

    /** This tests whether the buy button should be _shown_ */
    private fun isConstructionPurchaseShown(construction: INonPerpetualConstruction, stat: Stat): Boolean {
        val city = cityScreen.city
        return construction.canBePurchasedWithStat(city, stat)
    }

    /** This tests whether the buy button should be _enabled_ */
    private fun isConstructionPurchaseAllowed(construction: INonPerpetualConstruction, stat: Stat, constructionBuyCost: Int): Boolean {
        val city = cityScreen.city
        return when {
            city.isPuppet -> false
            !cityScreen.canChangeState -> false
            city.isInResistance() -> false
            !construction.isPurchasable(city.cityConstructions) -> false    // checks via 'rejection reason'
            construction is BaseUnit && !city.canPlaceNewUnit(construction) -> false
            city.civInfo.gameInfo.gameParameters.godMode -> true
            constructionBuyCost == 0 -> true
            else -> city.getStatReserve(stat) >= constructionBuyCost
        }
}

    /** Called only by askToBuyConstruction's Yes answer - not to be confused with [CityConstructions.purchaseConstruction]
     * @param tile supports [UniqueType.CreatesOneImprovement]
     */
    private fun purchaseConstruction(
        construction: INonPerpetualConstruction,
        stat: Stat = Stat.Gold,
        tile: TileInfo? = null
    ) {
        SoundPlayer.play(stat.purchaseSound)
        val city = cityScreen.city
        if (!city.cityConstructions.purchaseConstruction(construction.name, selectedQueueEntry, false, stat, tile)) {
            Popup(cityScreen).apply {
                add("No space available to place [${construction.name}] near [${city.name}]".tr()).row()
                addCloseButton()
                open()
            }
            return
        }
        if (isSelectedQueueEntry() || cityScreen.selectedConstruction?.isBuildable(city.cityConstructions) != true) {
            selectedQueueEntry = -1
            cityScreen.clearSelection()

            // Allow buying next queued or auto-assigned construction right away
            city.cityConstructions.chooseNextConstruction()
            if (city.cityConstructions.currentConstructionFromQueue.isNotEmpty()) {
                val newConstruction = city.cityConstructions.getCurrentConstruction()
                if (newConstruction is INonPerpetualConstruction)
                    cityScreen.selectConstruction(newConstruction)
            }
        }
        cityScreen.update()
    }

    private fun getRaisePriorityButton(constructionQueueIndex: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getArrowImage(Align.top).apply { color = Color.BLACK }.surroundWithCircle(40f))
        if (cityScreen.canCityBeChanged()) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.raisePriority(constructionQueueIndex)
                cityScreen.selectConstruction(name)
                selectedQueueEntry = constructionQueueIndex - 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getLowerPriorityButton(constructionQueueIndex: Int, name: String, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getArrowImage(Align.bottom).apply { color = Color.BLACK }.surroundWithCircle(40f))
        if (cityScreen.canCityBeChanged()) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.lowerPriority(constructionQueueIndex)
                cityScreen.selectConstruction(name)
                selectedQueueEntry = constructionQueueIndex + 1
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getRemoveFromQueueButton(constructionQueueIndex: Int, city: CityInfo): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Stop").surroundWithCircle(40f))
        if (cityScreen.canCityBeChanged()) {
            tab.touchable = Touchable.enabled
            tab.onClick {
                tab.touchable = Touchable.disabled
                city.cityConstructions.removeFromQueue(constructionQueueIndex, false)
                cityScreen.clearSelection()
                cityScreen.update()
            }
        }
        return tab
    }

    private fun getHeader(title: String): Table {
        return Table()
                .background(ImageGetter.getBackground(ImageGetter.getBlue()))
                .addCell(title.toLabel(fontSize = Constants.headingFontSize))
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

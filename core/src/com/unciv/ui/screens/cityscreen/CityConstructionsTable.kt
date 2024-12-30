package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.RejectionReason
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addBorder
import com.unciv.ui.components.extensions.addCell
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.CityScreenConstructionMenu
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlin.math.max
import kotlin.math.min
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

private class ConstructionButtonDTO(
    val construction: IConstruction,
    val buttonText: String,
    val resourcesRequired: HashMap<String, Int>? = null,
    val rejectionReason: RejectionReason? = null)

/**
 * Manager to hold and coordinate two widgets for the city screen left side:
 * - Construction queue with the buy button.
 *   The queue is scrollable, limited to one third of the stage height.
 * - Available constructions display, scrolling, grouped with expanders and therefore of dynamic height.
 */
class CityConstructionsTable(private val cityScreen: CityScreen) {
    /* -1 = Nothing, >= 0 queue entry (0 = current construction) */
    var selectedQueueEntry = -1 // None

    private val upperTable = Table(BaseScreen.skin)
    private val constructionsQueueScrollPane: ScrollPane
    private val constructionsQueueTable = Table()
    private val queueExpander: ExpanderTab
    private val buttonsTable = Table()
    private val buyButtonFactory = BuyButtonFactory(cityScreen)

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
        constructionsQueueScrollPane = ScrollPane(constructionsQueueTable.addBorder(2f, Color.WHITE))
        constructionsQueueScrollPane.setOverscroll(false, false)
        constructionsQueueTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityConstructionTable/ConstructionsQueueTable",
            tintColor = ImageGetter.CHARCOAL
        )
        queueExpander = ExpanderTab(
            "Queue", 
            onChange = { cityScreen.update() },
            startsOutOpened = false,
            defaultPad = 0f
        )

        upperTable.defaults().left().top()
        upperTable.add(constructionsQueueScrollPane).padBottom(pad).maxHeight(stageHeight*3/4).row()
        upperTable.add(buttonsTable).padBottom(pad).row()
        
        availableConstructionsScrollPane = ScrollPane(availableConstructionsTable.addBorder(2f, Color.WHITE))
        availableConstructionsScrollPane.setOverscroll(false, false)
        availableConstructionsTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityConstructionTable/AvailableConstructionsTable",
            tintColor = ImageGetter.CHARCOAL
        )
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
        updateQueueAndButtons(selectedConstruction)
        updateAvailableConstructions()
    }

    private fun updateQueueAndButtons(construction: IConstruction?) {
        updateButtons(construction)
        updateConstructionQueue()
        upperTable.pack()
        // Need to reposition when height changes as setPosition's alignment does not persist, it's just a readability shortcut to calculate bottomLeft
        upperTable.setPosition(posFromEdge, stageHeight - posFromEdge, Align.topLeft)
        lowerTableScrollCell.maxHeight(max(20f, stageHeight - upperTable.height - 2 * posFromEdge))
    }

    private fun updateButtons(construction: IConstruction?) {
        if (!cityScreen.canChangeState) return
        /** [UniqueType.MayBuyConstructionsInPuppets] support - we need a buy button for civs that could buy items in puppets */
        if (cityScreen.city.isPuppet && !cityScreen.city.getMatchingUniques(UniqueType.MayBuyConstructionsInPuppets).any()) return
        buttonsTable.clear()
        buyButtonFactory.addBuyButtons(buttonsTable, construction) {
            it.padRight(5f)
        }
        // priority buttons
        val queue = cityScreen.city.cityConstructions.constructionQueue
        if (selectedQueueEntry >= 0 && selectedQueueEntry < queue.size) {
            val constructionName = queue[selectedQueueEntry]
            val button = getRaisePriorityButton(selectedQueueEntry, constructionName, cityScreen.city)
            button.isVisible = cityScreen.canCityBeChanged() && selectedQueueEntry > 0
            buttonsTable.add(button).padRight(5f)
            if (selectedQueueEntry != queue.lastIndex && cityScreen.canCityBeChanged())
                buttonsTable.add(getLowerPriorityButton(selectedQueueEntry, constructionName, cityScreen.city)).padRight(5f)
            else buttonsTable.add() 
        }
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
            queueExpander.innerTable.clear()
            queueExpander.headerContent.clear()
            queueExpander.setText(if (queue.size <= 4) "Queue".tr() else "")
            
            queue.forEachIndexed { i, constructionName ->
                // The first entry is already displayed as "Current construction"
                if (i != 0) {
                    queueExpander.innerTable.add(getQueueEntry(i, constructionName))
                        .expandX().fillX().row()
                    if (i != queue.size - 1)
                        queueExpander.innerTable.addSeparator()
                    if (i < 5) {
                        val image = ImageGetter.getConstructionPortrait(constructionName, 40f)
                        image.touchable = Touchable.disabled
                        queueExpander.headerContent.add(image).padRight(5f)
                    }
                    if (i == 5) {
                        queueExpander.headerContent.add("(+{${queue.size-i}})".toLabel())
                    }
                }
            }
            constructionsQueueTable.add(queueExpander).fillX().pad(2f)
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
            val buttonText = cityConstructions.getTurnsToConstructionString(entry, useStoredProduction).trim()
            val resourcesRequired = if (entry is BaseUnit)
                entry.getResourceRequirementsPerTurn(city.civ.state)
                else entry.getResourceRequirementsPerTurn(city.state)
            val mostImportantRejection =
                    entry.getRejectionReasons(cityConstructions)
                        .filter { it.isImportantRejection() }
                        .minByOrNull { it.getRejectionPrecedence() }

            constructionButtonDTOList.add(
                ConstructionButtonDTO(
                    entry,
                    buttonText,
                    if (resourcesRequired.isEmpty()) null else resourcesRequired,
                    mostImportantRejection
                )
            )
        }

        for (specialConstruction in PerpetualConstruction.perpetualConstructionsMap.values
                .filter { it.shouldBeDisplayed(cityConstructions) }
        ) {
            constructionButtonDTOList.add(
                ConstructionButtonDTO(
                    specialConstruction,
                    "Produce [${specialConstruction.name}]".tr() + " " + specialConstruction.getProductionTooltip(city).trim()
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
                val blacklisted = ArrayList<Table>()
                val disabledAutoAssignConstructions: Set<String> = GUI.getSettings().disabledAutoAssignConstructions

                var maxButtonWidth = constructionsQueueTable.width
                for (dto in constructionButtonDTOList) {

                    /** filter out showing buildings that have RequiresBuildingInThisCity
                     * rejection (eg requiredBuilding entry) which are buildable.
                     * The rejection for RequiresBuildingInThisCity isn't yielded if
                     * the prerequisite is in the queue
                     */
                    if (dto.construction is Building
                            && dto.rejectionReason?.type == RejectionReasonType.RequiresBuildingInThisCity
                            && constructionButtonDTOList.any {
                                (it.construction is Building) && (it.construction.name == dto.construction.requiredBuilding
                                        || it.construction.replaces == dto.construction.requiredBuilding
                                        || it.construction.hasUnique(dto.construction.requiredBuilding!!, cityScreen.city.state))
                            })
                        continue

                    val constructionButton = getConstructionButton(dto)
                    if (dto.construction.name in disabledAutoAssignConstructions)
                        blacklisted.add(constructionButton)
                    else when (dto.construction) {
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
                    addCategory("Units", units, maxButtonWidth, KeyboardBinding.BuildUnits)
                    addCategory("Buildings", buildableBuildings, maxButtonWidth, KeyboardBinding.BuildBuildings)
                    addCategory("Wonders", buildableWonders, maxButtonWidth, KeyboardBinding.BuildWonders)
                    addCategory("National Wonders", buildableNationalWonders, maxButtonWidth, KeyboardBinding.BuildNationalWonders)
                    addCategory("Other", specialConstructions, maxButtonWidth, KeyboardBinding.BuildOther)
                    addCategory("Disabled", blacklisted, maxButtonWidth, KeyboardBinding.BuildDisabled, startsOutOpened = false)
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
        highlightQueueEntry(table, constructionQueueIndex == selectedQueueEntry)

        val construction = cityConstructions.getConstruction(constructionName)
        val isFirstConstructionOfItsKind = cityConstructions.isFirstConstructionOfItsKind(constructionQueueIndex, constructionName)

        var text = constructionName.tr(true) +
                if (constructionName in PerpetualConstruction.perpetualConstructionsMap) "\n" + Fonts.infinity
                else cityConstructions.getTurnsToConstructionString(construction, isFirstConstructionOfItsKind)

        val constructionResource = if (construction is BaseUnit)
                construction.getResourceRequirementsPerTurn(city.civ.state)
            else construction.getResourceRequirementsPerTurn(city.state)
        for ((resourceName, amount) in constructionResource) {
            val resource = cityConstructions.city.getRuleset().tileResources[resourceName] ?: continue
            text += "\n" + resourceName.getConsumesAmountString(amount, resource.isStockpiled).tr()
        }

        table.defaults().pad(2f).minWidth(40f)
        if (isFirstConstructionOfItsKind) table.add(getProgressBar(constructionName)).minWidth(5f)
        else table.add().minWidth(5f)
        table.add(ImageGetter.getConstructionPortrait(constructionName, 40f)).padRight(10f)
        table.add(text.toLabel()).expandX().fillX().left()

        if (cityScreen.canCityBeChanged()) table.add(getRemoveFromQueueButton(constructionQueueIndex, city)).right()
        else table.add().right()

        table.touchable = Touchable.enabled

        fun selectQueueEntry(onBeforeUpdate: () -> Unit) {
            cityScreen.selectConstruction(constructionName)
            selectedQueueEntry = constructionQueueIndex
            onBeforeUpdate()
            cityScreen.update()  // Not before CityScreenConstructionMenu or table will have no parent to get stage coords
            ensureQueueEntryVisible()
        }

        table.onClick { selectQueueEntry {} }
        if (cityScreen.canCityBeChanged())
            table.onRightClick { selectQueueEntry {
                CityScreenConstructionMenu(cityScreen.stage, table, cityScreen.city, construction) {
                    cityScreen.city.reassignPopulation()
                    cityScreen.update()
                }
            } }

        return table
    }

    private fun highlightQueueEntry(queueEntry: Table, highlight: Boolean) {
        queueEntry.background =
            if (highlight)
                BaseScreen.skinStrings.getUiBackground(
                    "CityScreen/CityConstructionTable/QueueEntrySelected",
                    tintColor = Color.GREEN.darken(0.5f)
                )
            else
                BaseScreen.skinStrings.getUiBackground(
                    "CityScreen/CityConstructionTable/QueueEntry",
                    tintColor = ImageGetter.CHARCOAL
                )
    }

    private fun getProgressBar(constructionName: String): Group {
        val cityConstructions = cityScreen.city.cityConstructions
        val construction = cityConstructions.getConstruction(constructionName)
        if (construction is PerpetualConstruction) return Table()
        if (cityConstructions.getWorkDone(constructionName) == 0) return Table()

        val constructionPercentage = cityConstructions.getWorkDone(constructionName) /
                (construction as INonPerpetualConstruction).getProductionCost(cityConstructions.city.civ, cityConstructions.city).toFloat()
        return ImageGetter.getProgressBarVertical(2f, 30f, constructionPercentage,
                Color.BROWN.brighten(0.5f), Color.WHITE)
    }

    private fun getConstructionButton(constructionButtonDTO: ConstructionButtonDTO): Table {
        val construction = constructionButtonDTO.construction
        val pickConstructionButton = Table().apply {
            isTransform = false
            align(Align.left).pad(5f)
            touchable = Touchable.enabled
        }

        highlightConstructionButton(pickConstructionButton, !isSelectedQueueEntry() && cityScreen.selectedConstruction == construction)

        val icon = ImageGetter.getConstructionPortrait(construction.name, 40f)
        pickConstructionButton.add(getProgressBar(construction.name)).padRight(5f)
        pickConstructionButton.add(icon).padRight(10f)

        val constructionTable = Table().apply { isTransform = false }
        val resourceTable = Table().apply { isTransform = false }

        val textColor = if (constructionButtonDTO.rejectionReason == null) Color.WHITE else Color.RED
        constructionTable.add(construction.name.toLabel(fontColor = textColor, hideIcons = true).apply { wrap=true })
            .width(cityScreen.stage.width/5).expandX().left().row()

        resourceTable.add(constructionButtonDTO.buttonText.toLabel()).expandX().left()
        if (constructionButtonDTO.resourcesRequired != null) {
            for ((resource, amount) in constructionButtonDTO.resourcesRequired) {
                val color = if (constructionButtonDTO.rejectionReason?.type == RejectionReasonType.ConsumesResources)
                    Color.RED else Color.WHITE
                resourceTable.add(amount.tr().toLabel(fontColor = color)).expandX().left().padLeft(5f)
                resourceTable.add(ImageGetter.getResourcePortrait(resource, 15f)).padBottom(1f)
            }
        }
        for (unique in constructionButtonDTO.construction
            .getMatchingUniquesNotConflicting(UniqueType.CostsResources, cityScreen.city.state)) {
            val color = if (constructionButtonDTO.rejectionReason?.type == RejectionReasonType.ConsumesResources)
                Color.RED else Color.WHITE
            resourceTable.add(ColorMarkupLabel(unique.params[0], color)).expandX().left().padLeft(5f)
            resourceTable.add(ImageGetter.getResourcePortrait(unique.params[1], 15f)).padBottom(1f)
        }
        constructionTable.add(resourceTable).expandX().left()

        pickConstructionButton.add(constructionTable).expandX().left()

        if (!cannotAddConstructionToQueue(construction, cityScreen.city, cityScreen.city.cityConstructions)) {
            val addToQueueButton = ImageGetter.getImage("OtherIcons/New")
                .apply { color = ImageGetter.CHARCOAL }.surroundWithCircle(40f)
            addToQueueButton.onClick(UncivSound.Silent) {
                // Since the pickConstructionButton.onClick adds the construction if it's selected,
                // this effectively adds the construction even if it's unselected
                cityScreen.selectConstruction(construction)
            }
            pickConstructionButton.add(addToQueueButton)
        }
        pickConstructionButton.row()

        // no rejection reason means we can build it!
        if (constructionButtonDTO.rejectionReason != null) {
            pickConstructionButton.color.a = 0.9f
            icon.color.a = 0.5f
            if (constructionButtonDTO.rejectionReason.type != RejectionReasonType.ConsumesResources) {
                pickConstructionButton.add(
                    ColorMarkupLabel(constructionButtonDTO.rejectionReason.errorMessage, Color.RED)
                        .apply { wrap = true })
                    .colspan(pickConstructionButton.columns)
                    .width(cityScreen.stage.width/4).fillX().left().padTop(2f)
            }
        }

        pickConstructionButton.onClick {
            if (cityScreen.selectedConstruction == construction) {
                addConstructionToQueue(construction, cityScreen.city.cityConstructions)
            } else {
                cityScreen.selectConstruction(construction)
                highlightConstructionButton(pickConstructionButton, true, true)  // without, will highlight but with visible delay
            }
            selectedQueueEntry = -1
            cityScreen.update()
        }

        if (!cityScreen.canCityBeChanged()) return pickConstructionButton

        pickConstructionButton.onRightClick {
            if (cityScreen.selectedConstruction != construction) {
                // Ensure context is visible
                cityScreen.selectConstruction(construction)
                highlightConstructionButton(pickConstructionButton, true, true)
                cityScreen.updateWithoutConstructionAndMap()
            }
            CityScreenConstructionMenu(cityScreen.stage, pickConstructionButton, cityScreen.city, construction) {
                cityScreen.city.reassignPopulation()
                cityScreen.update()
            }
        }
        return pickConstructionButton
    }

    private fun highlightConstructionButton(
        pickConstructionButton: Table,
        highlight: Boolean,
        clearOthers: Boolean = false
    ) {
        val unselected by lazy {
            // Lazy because possibly not needed (highlight true, clearOthers false) and slightly costly
            BaseScreen.skinStrings.getUiBackground(
                "CityScreen/CityConstructionTable/PickConstructionButton",
                tintColor = ImageGetter.CHARCOAL
            )
        }

        pickConstructionButton.background =
            if (highlight)
                BaseScreen.skinStrings.getUiBackground(
                    "CityScreen/CityConstructionTable/PickConstructionButtonSelected",
                    tintColor = Color.GREEN.darken(0.5f)
                )
            else unselected

        if (!clearOthers) return
        // Using knowledge about Widget hierarchy - Making the Buttons their own class might be a better design.
        for (categoryExpander in availableConstructionsTable.children.filterIsInstance<ExpanderTab>()) {
            if (!categoryExpander.isOpen) continue
            for (button in categoryExpander.innerTable.children.filterIsInstance<Table>()) {
                if (button == pickConstructionButton) continue
                button.background = unselected
            }
        }

        if (!isSelectedQueueEntry()) return
        // Same as above but worse - both buttons and headers are typed `Table`
        for (button in constructionsQueueTable.children.filterIsInstance<Table>()) {
            if (button.children.size == 1) continue // Skip headers, they only have 1 Label
            highlightQueueEntry(button, false)
        }
        selectedQueueEntry = -1
    }

    private fun isSelectedQueueEntry(): Boolean = selectedQueueEntry >= 0

    private fun cannotAddConstructionToQueue(construction: IConstruction, city: City, cityConstructions: CityConstructions): Boolean {
        return cityConstructions.isQueueFull()
                || !construction.isBuildable(cityConstructions)
                || !cityScreen.canChangeState
                || construction is PerpetualConstruction && cityConstructions.isBeingConstructedOrEnqueued(construction.name)
                || city.isPuppet
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
        cityScreen.city.reassignPopulation()
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
    
    private fun getMovePriorityButton(
        arrowDirection: Int,
        binding: KeyboardBinding,
        constructionQueueIndex: Int,
        name: String,
        movePriority: (Int) -> Int
    ): Table {
        val button = Table()
        button.add(ImageGetter.getArrowImage(arrowDirection).apply { color = ImageGetter.CHARCOAL }.surroundWithCircle(40f))
        button.touchable = Touchable.enabled
        // Don't bind the queue reordering keys here - those should affect only the selected entry, not all of them
        button.onActivation {
            button.touchable = Touchable.disabled
            selectedQueueEntry = movePriority(constructionQueueIndex)
            // Selection display may need to update as I can click the button of a non-selected entry.
            cityScreen.selectConstruction(name)
            cityScreen.city.reassignPopulation()
            cityScreen.update()
            //cityScreen.updateWithoutConstructionAndMap()
            updateQueueAndButtons(cityScreen.selectedConstruction)
            ensureQueueEntryVisible()  // Not passing current button info - already outdated, our parent is already removed from the stage hierarchy and replaced
        }
        if (selectedQueueEntry == constructionQueueIndex) {
            button.keyShortcuts.add(binding)  // This binds without automatic tooltip
            button.addTooltip(binding)
        }
        return button
    }

    private fun getRaisePriorityButton(constructionQueueIndex: Int, name: String, city: City) =
        getMovePriorityButton(Align.top, KeyboardBinding.RaisePriority, constructionQueueIndex, name, city.cityConstructions::raisePriority)

    private fun getLowerPriorityButton(constructionQueueIndex: Int, name: String, city: City) =
        getMovePriorityButton(Align.bottom, KeyboardBinding.LowerPriority, constructionQueueIndex, name, city.cityConstructions::lowerPriority)

    private fun getRemoveFromQueueButton(constructionQueueIndex: Int, city: City): Table {
        val tab = Table()
        tab.add(ImageGetter.getImage("OtherIcons/Stop").surroundWithCircle(40f))
        tab.touchable = Touchable.enabled
        tab.onClick {
            tab.touchable = Touchable.disabled
            city.cityConstructions.removeFromQueue(constructionQueueIndex, false)
            cityScreen.clearSelection()
            cityScreen.city.reassignPopulation()
            cityScreen.update()
        }
        return tab
    }

    private fun getHeader(title: String): Table {
        return Table()
                .background(
                    BaseScreen.skinStrings.getUiBackground(
                        "CityScreen/CityConstructionTable/Header",
                        tintColor = BaseScreen.skinStrings.skinConfig.baseColor
                    )
                )
                .addCell(title.toLabel(fontSize = Constants.headingFontSize))
                .pad(4f)
    }

    private fun ensureQueueEntryVisible() {
        getSelectedQueueButton()?.let {
            constructionsQueueScrollPane.scrollTo(2f, it.y, it.width, it.height, true, true)
        }
    }

    private fun getSelectedQueueButton(): Actor? {
        if (selectedQueueEntry == 0) {
            return constructionsQueueTable.cells[0].actor
        }
        if (selectedQueueEntry > 0 && selectedQueueEntry < cityScreen.city.cityConstructions.constructionQueue.size) {
            // *2 because it's always the entry and a separator
            return queueExpander.innerTable.cells[selectedQueueEntry * 2 - 2].actor
        }
        return null
    }

    private fun resizeAvailableConstructionsScrollPane() {
        availableConstructionsScrollPane.height = 
            min(availableConstructionsTable.prefHeight, lowerTableScrollCell.maxHeight)
        lowerTable.pack()
    }

    private fun Table.addCategory(
        title: String,
        list: ArrayList<Table>,
        prefWidth: Float,
        toggleKey: KeyboardBinding,
        startsOutOpened: Boolean = !cityScreen.isCrampedPortrait()
    ) {
        if (list.isEmpty()) return

        if (rows > 0) addSeparator()
        val expander = ExpanderTab(
            title,
            startsOutOpened = startsOutOpened,
            defaultPad = 0f,
            expanderWidth = prefWidth,
            persistenceID = "CityConstruction.$title",
            toggleKey = toggleKey,
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

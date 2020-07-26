package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import kotlin.math.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class EmpireOverviewScreen(private var viewingPlayer:CivilizationInfo, defaultPage: String = "Cities") : CameraStageBaseScreen(){
    private val topTable = Table().apply { defaults().pad(10f) }
    private val centerTable = Table().apply {  defaults().pad(20f) }

    init {
        onBackButtonClicked { game.setWorldScreen() }
        val clicks = HashMap<String,() -> Unit>()

        val closeButton = Constants.close.toTextButton()
        closeButton.onClick { game.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        topTable.add(closeButton)

        val setCityInfoButton = "Cities".toTextButton()
        val setCities = {
            centerTable.clear()
            centerTable.add(CityOverviewTable(viewingPlayer, this))
            centerTable.pack()
        }
        clicks["Cities"] = setCities
        setCityInfoButton.onClick(setCities)
        topTable.add(setCityInfoButton)

        val setStatsInfoButton = "Stats".toTextButton()
        val setStats = {
            game.settings.addCompletedTutorialTask("See your stats breakdown")
            centerTable.clear()
            centerTable.add(ScrollPane(Table().apply {
                defaults().pad(40f)
                add(getHappinessTable()).top()
                add(getGoldTable()).top()
                add(getScienceTable()).top()
                add(getGreatPeopleTable()).top()
            }))
            centerTable.pack()
        }
        clicks["Stats"] = setStats
        setStatsInfoButton.onClick(setStats)
        topTable.add(setStatsInfoButton)

        val setCurrentTradesButton = "Trades".toTextButton()
        setCurrentTradesButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getTradesTable())).height(stage.height * 0.8f) // so it doesn't cover the navigation buttons
            centerTable.pack()
        }
        topTable.add(setCurrentTradesButton)
        if (viewingPlayer.diplomacy.values.all { it.trades.isEmpty() })
            setCurrentTradesButton.disable()

        val setUnitsButton = "Units".toTextButton()
        setUnitsButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getUnitTable()).apply { setOverscroll(false,false) }).height(stage.height * 0.8f)
            centerTable.pack()
        }
        topTable.add(setUnitsButton)


        val setDiplomacyButton = "Diplomacy".toTextButton()
        setDiplomacyButton.onClick {
            centerTable.clear()
            centerTable.add(getDiplomacyGroup()).height(stage.height * 0.8f)
            centerTable.pack()
        }
        topTable.add(setDiplomacyButton)

        val setResourcesButton = "Resources".toTextButton()
        val setResources = {
            centerTable.clear()
            centerTable.add(ScrollPane(getResourcesTable())).size(stage.width * 0.8f, stage.height * 0.8f)
            centerTable.pack()
        }
        clicks["Resources"] = setResources
        setResourcesButton.onClick(setResources)
        topTable.add(setResourcesButton)
        if (viewingPlayer.detailedCivResources.isEmpty())
            setResourcesButton.disable()

        topTable.pack()

        clicks[defaultPage]?.invoke()

        val table = Table()
        table.add(topTable).row()
        table.add(centerTable).expand().row()
        table.setFillParent(true)
        stage.addActor(table)
    }

    private fun getTradesTable(): Table {
        val tradesTable = Table().apply { defaults().pad(10f) }
        val diplomacies = viewingPlayer.diplomacy.values.filter { it.trades.isNotEmpty() }
                .sortedWith(Comparator { d0, d1 ->
                    val d0offers = d0.trades.first().ourOffers
                    val d1offers = d1.trades.first().ourOffers
                    val d0max = if (d0offers.isEmpty()) 0 else d0offers.maxBy { it.duration }!!.duration
                    val d1max = if (d1offers.isEmpty()) 0 else d1offers.maxBy { it.duration }!!.duration
                    when {
                        d0max > d1max -> 1
                        d0max == d1max -> 0
                        else -> -1
                    }
                })
        for(diplomacy in diplomacies) {
            for (trade in diplomacy.trades)
                tradesTable.add(createTradeTable(trade, diplomacy.otherCiv())).row()
        }

        return tradesTable
    }

    private fun createTradeTable(trade: Trade, otherCiv:CivilizationInfo): Table {
        val generalTable = Table(skin)
        generalTable.add(createOffersTable(viewingPlayer,trade.ourOffers, trade.theirOffers.size)).fillY()
        generalTable.add(createOffersTable(otherCiv, trade.theirOffers, trade.ourOffers.size)).fillY()
        return generalTable
    }

    private fun createOffersTable(civ: CivilizationInfo, offersList: TradeOffersList, numberOfOtherSidesOffers: Int): Table {
        val table = Table()
        table.defaults().pad(10f)
        table.background = ImageGetter.getBackground(civ.nation.getOuterColor())
        table.add(civ.civName.toLabel(civ.nation.getInnerColor())).row()
        table.addSeparator()
        for(offer in offersList){
            var offerText = offer.getOfferText()
            if(!offerText.contains("\n")) offerText+="\n"
            table.add(offerText.toLabel(civ.nation.getInnerColor())).row()
        }
        for(i in 1..numberOfOtherSidesOffers - offersList.size)
            table.add("\n".toLabel()).row() // we want both sides of the general table to have the same number of rows
        return table
    }


    private fun getHappinessTable(): Table {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        val happinessHeader = Table(skin)
        happinessHeader.add(ImageGetter.getStatIcon("Happiness")).pad(5f,0f,5f,12f).size(20f)
        happinessHeader.add("Happiness".toLabel(fontSize = 24)).padTop(5f)
        happinessTable.add(happinessHeader).colspan(2).row()
        happinessTable.addSeparator()

        val happinessBreakdown = viewingPlayer.stats().getHappinessBreakdown()

        for (entry in happinessBreakdown.filterNot { it.value.roundToInt()==0 }) {
            happinessTable.add(entry.key.tr())
            happinessTable.add(entry.value.roundToInt().toString()).right().row()
        }
        happinessTable.add("Total".tr())
        happinessTable.add(happinessBreakdown.values.sum().roundToInt().toString()).right()
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        val goldHeader = Table(skin)
        goldHeader.add(ImageGetter.getStatIcon("Gold")).pad(5f,0f,5f,12f).size(20f)
        goldHeader.add("Gold".toLabel(fontSize = 24)).padTop(5f)
        goldTable.add(goldHeader).colspan(2).row()
        goldTable.addSeparator()
        var total=0f
        for (entry in viewingPlayer.stats().getStatMapForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key.tr())
            goldTable.add(entry.value.gold.roundToInt().toString()).right().row()
            total += entry.value.gold
        }
        goldTable.add("Total".tr())
        goldTable.add(total.roundToInt().toString()).right()
        goldTable.pack()
        return goldTable
    }


    private fun getScienceTable(): Table {
        val scienceTable = Table(skin)
        scienceTable.defaults().pad(5f)
        val scienceHeader = Table(skin)
        scienceHeader.add(ImageGetter.getStatIcon("Science")).pad(5f,0f,5f,12f).size(20f)
        scienceHeader.add("Science".toLabel(fontSize = 24)).padTop(5f)
        scienceTable.add(scienceHeader).colspan(2).row()
        scienceTable.addSeparator()
        val scienceStats = viewingPlayer.stats().getStatMapForNextTurn()
                .filter { it.value.science!=0f }
        for (entry in scienceStats) {
            scienceTable.add(entry.key.tr())
            scienceTable.add(entry.value.science.roundToInt().toString()).right().row()
        }
        scienceTable.add("Total".tr())
        scienceTable.add(scienceStats.values.map { it.science }.sum().roundToInt().toString()).right()
        scienceTable.pack()
        return scienceTable
    }


    private fun getGreatPeopleTable(): Table {
        val greatPeopleTable = Table(skin)

        val greatPersonPoints = viewingPlayer.greatPeople.greatPersonPoints.toHashMap()
        val greatPersonPointsPerTurn = viewingPlayer.getGreatPersonPointsForNextTurn().toHashMap()
        val pointsToGreatPerson = viewingPlayer.greatPeople.pointsForNextGreatPerson

        greatPeopleTable.defaults().pad(5f)
        val greatPeopleHeader = Table(skin)
        val greatPeopleIcon = ImageGetter.getStatIcon("Specialist")
        greatPeopleIcon.color = Color.ROYAL
        greatPeopleHeader.add(greatPeopleIcon).padRight(12f).size(30f)
        greatPeopleHeader.add("Great person points".toLabel(fontSize = 24)).padTop(5f)
        greatPeopleTable.add(greatPeopleHeader).colspan(3).row()
        greatPeopleTable.addSeparator()
        greatPeopleTable.add()
        greatPeopleTable.add("Current points".tr())
        greatPeopleTable.add("Points per turn".tr()).row()

        val mapping = viewingPlayer.greatPeople.statToGreatPersonMapping
        for(entry in mapping){
            greatPeopleTable.add(entry.value.tr())
            greatPeopleTable.add(greatPersonPoints[entry.key]!!.toInt().toString()+"/"+pointsToGreatPerson)
            greatPeopleTable.add(greatPersonPointsPerTurn[entry.key]!!.toInt().toString()).row()
        }
        val pointsForGreatGeneral = viewingPlayer.greatPeople.greatGeneralPoints.toString()
        val pointsForNextGreatGeneral = viewingPlayer.greatPeople.pointsForNextGreatGeneral.toString()
        greatPeopleTable.add("Great General".tr())
        greatPeopleTable.add("$pointsForGreatGeneral/$pointsForNextGreatGeneral").row()
        greatPeopleTable.pack()
        return greatPeopleTable
    }


    private fun getUnitTable(): Table {
        val table=Table(skin).apply { defaults().pad(5f) }
        table.add("Name".tr())
        table.add("Action".tr())
        table.add("Strength".tr())
        table.add("Ranged strength".tr())
        table.add("Movement".tr())
        table.add("Closest city".tr())
        table.add("Promotions".tr())
        table.add("Health".tr())
        table.row()
        table.addSeparator()

        for(unit in viewingPlayer.getCivUnits().sortedWith(compareBy({it.name},{!it.due},
                {it.currentMovement<0.1f},{abs(it.currentTile.position.x)+abs(it.currentTile.position.y)}))) {
            val baseUnit = unit.baseUnit()
            val button = unit.name.toTextButton()
            button.onClick {
                game.setWorldScreen()
                game.worldScreen.mapHolder.setCenterPosition(unit.currentTile.position)
            }
            table.add(button).left()
            val mapUnitAction = unit.mapUnitAction
            if (mapUnitAction != null) table.add(if(mapUnitAction.name().startsWith("Fortify")) "Fortify".tr() else mapUnitAction.name().tr()) else table.add()
            if(baseUnit.strength>0) table.add(baseUnit.strength.toString()) else table.add()
            if(baseUnit.rangedStrength>0) table.add(baseUnit.rangedStrength.toString()) else table.add()
            table.add(DecimalFormat("0.#").format(unit.currentMovement)+"/"+unit.getMaxMovement())
            val closestCity = unit.getTile().getTilesInDistance(3).firstOrNull{it.isCityCenter()}
            if (closestCity!=null) table.add(closestCity.getCity()!!.name.tr()) else table.add()
            val promotionsTable = Table()
            val promotionsForUnit = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter { unit.promotions.promotions.contains(it.name) }     // force same sorting as on picker (.sorted() would be simpler code, but...)
            for(promotion in promotionsForUnit)
                promotionsTable.add(ImageGetter.getPromotionIcon(promotion.name))
            if (unit.promotions.canBePromoted()) promotionsTable.add(ImageGetter.getImage("OtherIcons/Star").apply { color= Color.GOLDENROD }).size(24f).padLeft(8f)
            if (unit.canUpgrade()) promotionsTable.add(ImageGetter.getUnitIcon(unit.getUnitToUpgradeTo().name, Color.GREEN)).size(28f).padLeft(8f)
            promotionsTable.onClick {
                if (unit.promotions.canBePromoted() || unit.promotions.promotions.isNotEmpty()) {
                    game.setScreen(PromotionPickerScreen(unit))
                }
            }
            table.add(promotionsTable)
            if (unit.health < 100) table.add(unit.health.toString()) else table.add()
            table.row()
        }
        table.pack()
        return table
    }


    private fun playerKnows(civ:CivilizationInfo) = civ==viewingPlayer ||
            viewingPlayer.diplomacy.containsKey(civ.civName)

    private fun getDiplomacyGroup(): Group {
        val relevantCivs = viewingPlayer.gameInfo.civilizations.filter { it.isMajorCiv() }
        val freeWidth = stage.width
        val freeHeight = stage.height - topTable.height
        val group = Group()
        group.setSize(freeWidth, freeHeight)
        val civGroups = HashMap<String, Actor>()
        val civLines = HashMap<String, MutableSet<Actor>>()
        for (i in 0..relevantCivs.lastIndex) {
            val civ = relevantCivs[i]

            val civGroup = getCivGroup(civ, "", viewingPlayer)

            val vector = HexMath.getVectorForAngle(2 * Math.PI.toFloat() * i / relevantCivs.size)
            civGroup.center(group)
            civGroup.moveBy(vector.x * freeWidth / 2.5f, vector.y * freeHeight / 2.5f)
            civGroup.touchable = Touchable.enabled
            civGroup.onClick {
                onCivClicked(civLines, civ.civName)
            }

            civGroups[civ.civName] = civGroup
            group.addActor(civGroup)
        }

        for (civ in relevantCivs.filter { playerKnows(it) && !it.isDefeated() })
            for (diplomacy in civ.diplomacy.values.filter {
                it.otherCiv().isMajorCiv()
                        && playerKnows(it.otherCiv()) && !it.otherCiv().isDefeated()
            }) {
                val civGroup = civGroups[civ.civName]!!
                val otherCivGroup = civGroups[diplomacy.otherCivName]!!

                if (!civLines.containsKey(civ.civName))
                    civLines[civ.civName] = mutableSetOf()

                val statusLine = ImageGetter.getLine(civGroup.x + civGroup.width / 2, civGroup.y + civGroup.height / 2,
                        otherCivGroup.x + otherCivGroup.width / 2, otherCivGroup.y + otherCivGroup.height / 2, 3f)

                val diplomacyLevel = diplomacy.diplomaticModifiers.values.sum()
                statusLine.color = getColorForDiplomacyLevel(diplomacyLevel)

                civLines[civ.civName]!!.add(statusLine)

                group.addActor(statusLine)
                statusLine.toBack()
            }

        return group
    }

    private fun onCivClicked(civLines: HashMap<String, MutableSet<Actor>>, name: String) {
        // ignore the clicks on "dead" civilizations, and remember the selected one
        val selectedLines = civLines[name] ?: return

        // let's check whether lines of all civs are visible (except selected one)
        var atLeastOneLineVisible = false
        var allAreLinesInvisible = true
        for (lines in civLines.values) {
            // skip the civilization selected by user, and civilizations with no lines
            if (lines == selectedLines || lines.isEmpty()) continue

            val visibility = lines.first().isVisible
            atLeastOneLineVisible = atLeastOneLineVisible || visibility
            allAreLinesInvisible = allAreLinesInvisible && visibility

            // check whether both visible and invisible lines are present
            if (atLeastOneLineVisible && !allAreLinesInvisible) {
                // invert visibility of the selected civ's lines
                selectedLines.forEach { it.isVisible = !it.isVisible }
                return
            }
        }

        if (selectedLines.first().isVisible)
        // invert visibility of all lines except selected one
            civLines.filter { it.key != name }.forEach { it.value.forEach { line -> line.isVisible = !line.isVisible } }
        else
        // it happens only when all are visible except selected one
        // invert visibility of the selected civ's lines
            selectedLines.forEach { it.isVisible = !it.isVisible }
    }

    private fun getColorForDiplomacyLevel(value: Float): Color {

        var amplitude = min(1.0f,abs(value)/80) // 80 = RelationshipLevel.Ally
        val shade = max(0.5f - amplitude, 0.0f)
        amplitude = max(amplitude, 0.5f)

        return Color( if (sign(value) < 0) amplitude else shade,
                      if (sign(value) > 0) amplitude else shade,
                      shade,1.0f)
    }


    private fun getResourcesTable(): Table {
        val resourcesTable = Table().apply { defaults().pad(10f) }
        val resourceDrilldown = viewingPlayer.detailedCivResources

        // First row of table has all the icons
        resourcesTable.add()
        // Order of source ResourceSupplyList: by tiles, enumerating the map in that spiral pattern
        // UI should not surprise player, thus we need a deterministic and guessable order
        val resources = resourceDrilldown.map { it.resource }
                .filter { it.resourceType != ResourceType.Bonus }.distinct()
                .sortedWith(compareBy({ it.resourceType }, { it.name.tr() }))

        var visibleLabel: Label? = null
        for (resource in resources) {
            // Create a group of label and icon for each resource.
            val resourceImage = ImageGetter.getResourceImage(resource.name, 50f)
            val resourceLabel = resource.name.toLabel()
            val labelPadding = 10f
            // Using a table here leads to spacing issues
            // due to different label lengths.
            val holder = Group()
            resourceImage.onClick {
                if (visibleLabel != null)
                    visibleLabel!!.setVisible(false)
                resourceLabel.setVisible(true)
                visibleLabel = resourceLabel
            }
            holder.addActor(resourceImage)
            holder.addActor(resourceLabel)
            holder.setSize(resourceImage.getWidth(),
                    resourceImage.getHeight() + resourceLabel.getHeight() + labelPadding)
            // Center-align all labels, but right-align the last couple resources' labels
            // because they may get clipped otherwise. The leftmost label should be fine
            // center-aligned (if there are more than 2 resources), because the left side
            // has more padding.
            val alignFactor = when {
                (resources.indexOf(resource) + 2 >= resources.count()) -> 1
                else -> 2
            }
            resourceLabel.moveBy((resourceImage.getWidth() - resourceLabel.getWidth()) / alignFactor,
                    resourceImage.getHeight() + labelPadding)
            resourceLabel.setVisible(false)
            resourcesTable.add(holder)
        }
        resourcesTable.addSeparator()

        val origins = resourceDrilldown.map { it.origin }.distinct()
        for (origin in origins) {
            resourcesTable.add(origin.toLabel())
            for (resource in resources) {
                val resourceSupply = resourceDrilldown.firstOrNull { it.resource == resource && it.origin == origin }
                if (resourceSupply == null) resourcesTable.add()
                else resourcesTable.add(resourceSupply.amount.toString().toLabel())
            }
            resourcesTable.row()
        }

        resourcesTable.add("Total".toLabel())
        for (resource in resources) {
            val sum = resourceDrilldown.filter { it.resource == resource }.sumBy { it.amount }
            resourcesTable.add(sum.toLabel())
        }

        return resourcesTable
    }

    companion object {
        fun getCivGroup(civ: CivilizationInfo, afterCivNameText:String, currentPlayer:CivilizationInfo): Table {
            val civGroup = Table()

            var labelText = civ.civName.tr()+afterCivNameText
            var labelColor=Color.WHITE
            val backgroundColor:Color

            if (civ.isDefeated()) {
                civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
                backgroundColor = Color.LIGHT_GRAY
                labelColor = Color.BLACK
            } else if (currentPlayer==civ // game.viewEntireMapForDebug
                    || currentPlayer.knows(civ) || currentPlayer.isDefeated() || currentPlayer.victoryManager.hasWon()) {
                civGroup.add(ImageGetter.getNationIndicator(civ.nation, 30f))
                backgroundColor = civ.nation.getOuterColor()
                labelColor = civ.nation.getInnerColor()
            } else {
                backgroundColor = Color.DARK_GRAY
                labelText = "???"
            }

            civGroup.background = ImageGetter.getRoundedEdgeTableBackground(backgroundColor)
            val label = labelText.toLabel(labelColor)
            label.setAlignment(Align.center)

            civGroup.add(label).pad(10f)
            civGroup.pack()
            return civGroup
        }
    }
}


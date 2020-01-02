package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable

class ConstructionsTable(val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin){

    var constructionScrollPane:ScrollPane?=null
    var lastConstruction = ""


    fun update() {
        val city = cityScreen.city
        pad(10f)
        columnDefaults(0).padRight(10f)
        clear()

        addConstructionPickerScrollpane(city)
        addCurrentConstructionTable(city)

        pack()
    }

    private fun getProductionButton(construction: String, buttonText: String, rejectionReason: String=""): Table {
        val pickProductionButton = Table()
        pickProductionButton.touchable = Touchable.enabled
        pickProductionButton.align(Align.left)
        pickProductionButton.pad(5f)

        if(cityScreen.city.cityConstructions.currentConstruction==construction)
            pickProductionButton.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK,0.5f))
        else
            pickProductionButton.background = ImageGetter.getBackground(Color.BLACK)

        pickProductionButton.add(ImageGetter.getConstructionImage(construction).surroundWithCircle(40f)).padRight(10f)
        pickProductionButton.add(buttonText.toLabel())

        if(rejectionReason=="" && UncivGame.Current.worldScreen.isPlayersTurn) { // no rejection reason means we can build it!
            pickProductionButton.onClick {
                if (!cityScreen.city.isPuppet) {
                    lastConstruction = cityScreen.city.cityConstructions.currentConstruction
                    cityScreen.city.cityConstructions.currentConstruction = construction
                    cityScreen.city.cityConstructions.currentConstructionIsUserSet = true
                    cityScreen.city.cityStats.update()
                    cityScreen.update()
                    cityScreen.game.settings.addCompletedTutorialTask("Pick construction")
                }
            }

        }
        else {
            pickProductionButton.color = Color.GRAY
            pickProductionButton.row()
            pickProductionButton.add(rejectionReason.toLabel(Color.RED)).colspan(pickProductionButton.columns)
        }

        if(construction==cityScreen.city.cityConstructions.currentConstruction)
            pickProductionButton.color= Color.GREEN
        return pickProductionButton
    }

    private fun Table.addCategory(title:String,list:ArrayList<Table>){
        if(list.isEmpty()) return
        val titleTable = Table()
        titleTable.background = ImageGetter.getBackground(ImageGetter.getBlue())
        titleTable.add(title.toLabel(fontSize = 24))

        addSeparator()
        add(titleTable).fill().row()
        addSeparator()

        for(table in list) {
            add(table).fill().row()
            if(table != list.last()) addSeparator()
        }
    }

    private fun addConstructionPickerScrollpane(city: CityInfo) {
        val cityConstructions = city.cityConstructions

        val constructionPickerTable = Table()
        constructionPickerTable.background = ImageGetter.getBackground(Color.BLACK)

        val units = ArrayList<Table>()
        for (unit in city.getRuleset().units.values.filter { it.shouldBeDisplayed(cityConstructions) }) {
            val turnsToUnit = cityConstructions.turnsToConstruction(unit.name)
            units += getProductionButton(unit.name,
                    unit.name.tr() + "\r\n" + turnsToUnit + (if(turnsToUnit>1) " {turns}".tr() else " {turn}".tr()),
                    unit.getRejectionReason(cityConstructions))
        }

        constructionPickerTable.addCategory("Units",units)

        val buildableWonders = ArrayList<Table>()
        val buildableNationalWonders = ArrayList<Table>()
        val buildableBuildings = ArrayList<Table>()

        for (building in city.getRuleset().buildings.values) {
            if (!building.shouldBeDisplayed(cityConstructions) && building.name != cityConstructions.currentConstruction) continue
            val turnsToBuilding = cityConstructions.turnsToConstruction(building.name)
            val productionTextButton = getProductionButton(building.name,
                    building.name.tr() + "\r\n" + turnsToBuilding + (if(turnsToBuilding>1) " {turns}".tr() else " {turn}".tr()),
                    building.getRejectionReason(cityConstructions)
                    )
            if (building.isWonder)
                buildableWonders += productionTextButton
            else if(building.isNationalWonder)
                buildableNationalWonders += productionTextButton
            else
                buildableBuildings += productionTextButton
        }

        constructionPickerTable.addCategory("Wonders",buildableWonders)
        constructionPickerTable.addCategory("National Wonders",buildableNationalWonders)
        constructionPickerTable.addCategory("Buildings",buildableBuildings)

        val specialConstructions = ArrayList<Table>()
        for (specialConstruction in SpecialConstruction.getSpecialConstructions().filter { it.shouldBeDisplayed(cityConstructions) }) {
            specialConstructions += getProductionButton(specialConstruction.name,
                    "Produce [${specialConstruction.name}]".tr())
        }
        constructionPickerTable.addCategory("Other",specialConstructions)

        val scrollPane = ScrollPane(constructionPickerTable.addBorder(2f, Color.WHITE))

        // This is to keep the same amount of scrolling on the construction picker scroll between refresh()es
        if(constructionScrollPane!=null){
            scrollPane.layout()
            scrollPane.scrollY = constructionScrollPane!!.scrollY
            scrollPane.updateVisualScroll()
        }
        constructionScrollPane = scrollPane

        add(scrollPane).maxHeight(stage.height / 3 * (stage.height/600)).row()
    }

    private fun addCurrentConstructionTable(city: CityInfo) {
        val cityConstructions = city.cityConstructions
        val construction = cityConstructions.getCurrentConstruction()

        row()
        val purchaseConstructionButton: TextButton
        if (!city.isPuppet && !city.isInResistance() && construction.canBePurchased()) {
            val constructionGoldCost = construction.getGoldCost(city.civInfo)
            purchaseConstructionButton = TextButton("Buy for [$constructionGoldCost] gold".tr(), CameraStageBaseScreen.skin)
            purchaseConstructionButton.onClick(UncivSound.Coin) {
                YesNoPopupTable("Would you like to purchase [${construction.name}] for [$constructionGoldCost] gold?".tr(), {
                    cityConstructions.purchaseConstruction(construction.name)
                    if(lastConstruction!="" && cityConstructions.getConstruction(lastConstruction).isBuildable(cityConstructions))
                        city.cityConstructions.currentConstruction = lastConstruction
                    cityScreen.update() // since the list of available buildings needs to be updated too, so we can "see" that the building we bought now exists in the city
                }, cityScreen)
            }
            if (constructionGoldCost > city.civInfo.gold) {
                purchaseConstructionButton.disable()
            }
        } else {
            purchaseConstructionButton = TextButton("Buy".tr(), CameraStageBaseScreen.skin)
            purchaseConstructionButton.disable()
        }
        purchaseConstructionButton.labelCell.pad(10f)
        add(purchaseConstructionButton).pad(10f).row()


        val currentConstructionTable = Table()
        currentConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))
        currentConstructionTable.pad(10f)

        val userNeedsToSetProduction = city.cityConstructions.currentConstruction==""
        if(!userNeedsToSetProduction) {
            currentConstructionTable.add(
                    ImageGetter.getConstructionImage(city.cityConstructions.currentConstruction).surroundWithCircle(50f))
                    .pad(5f)

            val buildingText = city.cityConstructions.getCityProductionTextForCityButton()
            currentConstructionTable.add(buildingText.toLabel()).row()
        }
        else{
            currentConstructionTable.add() // no icon
            currentConstructionTable.add("Pick construction".toLabel()).row()
        }

        val description: String
        if(userNeedsToSetProduction)
            description=""
        else if (construction is BaseUnit)
            description = construction.getDescription(true)
        else if (construction is Building)
            description = construction.getDescription(true, city.civInfo,
                    city.civInfo.gameInfo.ruleSet)
        else if(construction is SpecialConstruction)
            description = construction.description.tr()
        else description="" // Should never happen

        val descriptionLabel = description.toLabel()
        descriptionLabel.setWrap(true)
        descriptionLabel.width = stage.width / 4
        val descriptionScroll = ScrollPane(descriptionLabel)
        currentConstructionTable.add(descriptionScroll).colspan(2)
                .width(stage.width / 4).height(stage.height / 8)

        add(currentConstructionTable.addBorder(2f, Color.WHITE))
    }

}
package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable

class ConstructionsTable(val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin){

    var constructionScrollPane:ScrollPane?=null

    private fun getProductionButton(construction: String, buttonText: String): Table {
        val pickProductionButton = Table()
        pickProductionButton.touchable = Touchable.enabled
        pickProductionButton.align(Align.left)
        pickProductionButton.pad(5f)

        if(cityScreen.city.cityConstructions.currentConstruction==construction)
            pickProductionButton.background = ImageGetter.getBackground(Color.GREEN.cpy().lerp(Color.BLACK,0.5f))
        else
            pickProductionButton.background = ImageGetter.getBackground(Color.BLACK)

        pickProductionButton.add(ImageGetter.getConstructionImage(construction).surroundWithCircle(40f)).padRight(10f)
        pickProductionButton.add(Label(buttonText, CameraStageBaseScreen.skin).setFontColor(Color.WHITE))
        pickProductionButton.onClick {
            cityScreen.city.cityConstructions.currentConstruction = construction
            update()
        }
        if(construction==cityScreen.city.cityConstructions.currentConstruction)
            pickProductionButton.color= Color.GREEN
        return pickProductionButton
    }

    fun update() {
        val city = cityScreen.city
        pad(10f)
        columnDefaults(0).padRight(10f)
        clear()

        addConstructionPickerScrollpane(city)
        addCurrentConstructionTable(city)

        pack()
    }

    private fun Table.addCategory(title:String,list:ArrayList<Table>){
        if(list.isEmpty()) return
        val titleTable = Table()
        titleTable.background = ImageGetter.getBackground(ImageGetter.getBlue())
        titleTable.add(Label(title.tr(),CameraStageBaseScreen.skin))

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
        for (unit in GameBasics.Units.values.filter { it.isBuildable(cityConstructions) })
            units += getProductionButton(unit.name,
                    unit.name.tr() + "\r\n" + cityConstructions.turnsToConstruction(unit.name) + " {turns}".tr())

        constructionPickerTable.addCategory("Units",units)

        val buildableWonders = ArrayList<Table>()
        val buildableBuildings = ArrayList<Table>()
        for (building in GameBasics.Buildings.values) {
            if (!building.isBuildable(cityConstructions) && building.name != cityConstructions.currentConstruction) continue
            val productionTextButton = getProductionButton(building.name,
                    building.name + "\r\n" + cityConstructions.turnsToConstruction(building.name) + " {turns}".tr())
            if (building.isWonder)
                buildableWonders += productionTextButton
            else
                buildableBuildings += productionTextButton
        }

        constructionPickerTable.addCategory("Wonders",buildableWonders)
        constructionPickerTable.addCategory("Buildings",buildableBuildings)

        val specialConstructions = ArrayList<Table>()
        for (specialConstruction in SpecialConstruction.getSpecialConstructions().filter { it.isBuildable(cityConstructions) }) {
            specialConstructions += getProductionButton(specialConstruction.name,
                    "Produce [${specialConstruction.name}]".tr())
        }
        constructionPickerTable.addCategory("Other",specialConstructions)

        val scrollPane = ScrollPane(constructionPickerTable, skin)

        // This is to keep the same amount of scrolling on the construction picker scroll between refresh()es
        if(constructionScrollPane!=null){
            scrollPane.layout()
            scrollPane.scrollY = constructionScrollPane!!.scrollY
            scrollPane.updateVisualScroll()
        }
        constructionScrollPane = scrollPane

        add(scrollPane).height(stage.height / 3).minWidth(stage.width/4).row()
    }

    private fun addCurrentConstructionTable(city: CityInfo) {
        val construction = city.cityConstructions.getCurrentConstruction()

        row()
        val purchaseConstructionButton: TextButton
        if (construction !is SpecialConstruction &&
                !(construction is Building && construction.isWonder)) {

            val buildingGoldCost = construction.getGoldCost(city.civInfo.policies.getAdoptedPolicies())
            purchaseConstructionButton = TextButton("Buy for [$buildingGoldCost] gold".tr(), CameraStageBaseScreen.skin)
            purchaseConstructionButton.onClick("coin") {
                YesNoPopupTable("Would you like to purchase [${construction.name}] for [$buildingGoldCost] gold?".tr(), {
                    city.cityConstructions.purchaseBuilding(city.cityConstructions.currentConstruction)
                    update()
                }, cityScreen)
            }
            if (buildingGoldCost > city.civInfo.gold) {
                purchaseConstructionButton.disable()
            }
        } else {
            purchaseConstructionButton = TextButton("Buy".tr(), CameraStageBaseScreen.skin)
            purchaseConstructionButton.disable()
        }
        add(purchaseConstructionButton).pad(10f).row()


        val currentConstructionTable = Table()
        currentConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))
        currentConstructionTable.pad(10f)

        currentConstructionTable.add(ImageGetter.getConstructionImage(city.cityConstructions.currentConstruction))
                .size(30f).pad(5f)

        val buildingText = city.cityConstructions.getCityProductionTextForCityButton()
        currentConstructionTable.add(Label(buildingText, CameraStageBaseScreen.skin).setFontColor(Color.WHITE)).row()

        val currentConstruction = city.cityConstructions.getCurrentConstruction()
        val description: String
        if (currentConstruction is BaseUnit)
            description = currentConstruction.getDescription(true)
        else if (currentConstruction is Building)
            description = currentConstruction.getDescription(true, city.civInfo.policies.adoptedPolicies)
        else description = currentConstruction.description.tr()

        val descriptionLabel = Label(description, CameraStageBaseScreen.skin)
        descriptionLabel.setWrap(true)
        descriptionLabel.width = stage.width / 4
        val descriptionScroll = ScrollPane(descriptionLabel)
        currentConstructionTable.add(descriptionScroll).colspan(2).width(stage.width / 4).height(stage.height / 8)

        add(currentConstructionTable.addBorder(2f, Color.WHITE))
    }

}
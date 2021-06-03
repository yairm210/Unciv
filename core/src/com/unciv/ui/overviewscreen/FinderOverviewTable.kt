package com.unciv.ui.overviewscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.*

/**
 * This manages one 'page' on the Empire Overview Screen: The 'Location Finder' 
 */
class FinderOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
): Table(CameraStageBaseScreen.skin) {

    private class RowData (
        val dynamicLine: Table,
        viewingPlayer: CivilizationInfo,
        overviewScreen: EmpireOverviewScreen
    ) {
        var linkedLine = FormattedLine()
        var linkedCell: Cell<Actor>? = null
        val showButton: Button
        var fixButton: Button? = null
        var getLocations: () -> Sequence<Vector2> = { sequenceOf() }
        var selected1 = ""      // selection in first SelectBox
        var selected2 = ""      // selection in second SelectBox if any
        var getNotificationText: () -> String = { "" }

        init {
            showButton = "Show".toTextButton()
            showButton.onClick {
                val locations = LocationAction( getLocations().toList() )
                if (locations.locations.isEmpty()) return@onClick
                viewingPlayer.addNotification( getNotificationText(),
                    locations, NotificationIcon.Eyes)
                val lastLocation = locations.locations.last()
                overviewScreen.game.setWorldScreen()
                overviewScreen.game.worldScreen.mapHolder.setCenterPosition(lastLocation)
                overviewScreen.dispose()
            }
        }
        fun newLink(line: FormattedLine) {
            //linkedLine.removeListeners()
            linkedLine = line
        }
    }

    private class StringSelect(skin: Skin, items: Collection<String>): SelectBox<String>(skin) {
        init {
            val array = GdxArray<String>(items.size)
            items.forEach { array.add(it) }
            this.items = array
        }
    }

    private val row1UnitAction: RowData? = getUnitAction()
    private val row2UnitUpgrade: RowData? = getUnitUpgrade()
    private val row3UnitPromote: RowData? = getUnitPromote()
    private val row4UnimprovedResources: RowData? = getUnimprovedResources()
    private val row5CitiesMissing: RowData? = getCitiesMissing()
    private val row6CitiesBuilding: RowData? = getCitiesConstructing()

    init {
        defaults().pad(5f).align(Align.left)
        addRow(row1UnitAction)
        addRow(row2UnitUpgrade)
        addRow(row3UnitPromote)
        addRow(row4UnimprovedResources)
        addRow(row5CitiesMissing)
        addRow(row6CitiesBuilding)
    }

    private fun addRow(row: RowData?) {
        if (row == null) return
        row.linkedCell = add(row.linkedLine.render(0f))
        add(row.dynamicLine)
        add(row.showButton)
        add(row.fixButton)
        row()
    }

    // X units that are doing Y
    private fun getUnitAction(): RowData? {
        val units = viewingPlayer.getCivUnits()
        if (units.none()) return null

        val wrapper = Table(skin)
        val selectUnit = StringSelect(skin, getUnitFilter(units))
        wrapper.add(selectUnit)
        wrapper.add(" {units that are} ".toLabel())
        val selectAction = StringSelect(skin, getActionFilter(units))
        wrapper.add(selectAction)

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )
        
        selectUnit.onChange { 
            rowData.selected1 = selectUnit.selected
        }
        rowData.selected1 = selectUnit.selected
        selectAction.onChange { 
            rowData.selected2 = selectAction.selected
        }
        rowData.selected2 = selectAction.selected
        rowData.getNotificationText = {
            "Showing [${rowData.selected1}] units that are [${rowData.selected2}]"
        }
        rowData.getLocations = {
            viewingPlayer.getCivUnits()
                .filter { it.matchesFilter(rowData.selected1) }
                .filter { it.matchesAction(rowData.selected2) }
                .map { it.currentTile.position }
        }

        return rowData
    }

    // X units that can be upgraded
    private fun getUnitUpgrade(): RowData? {
        val units = viewingPlayer.getCivUnits().filter { it.canUpgrade() }
        if (units.none()) return null

        val wrapper = Table(skin)
        val selectUnit = StringSelect(skin, getUnitFilter(units))
        wrapper.add(selectUnit)
        wrapper.add(" {units that can upgrade}".toLabel())

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )

        selectUnit.onChange {
            rowData.selected1 = selectUnit.selected
        }
        rowData.selected1 = selectUnit.selected
        rowData.getNotificationText = {
            "Showing [${rowData.selected1}] units that can upgrade"
        }
        rowData.getLocations = {
            viewingPlayer.getCivUnits()
                .filter { it.canUpgrade() }
                .filter { it.matchesFilter(rowData.selected1) }
                .map { it.currentTile.position }
        }

        return rowData
    }

    // X units that can promote
    private fun getUnitPromote(): RowData? {
        val units = viewingPlayer.getCivUnits().filter { it.promotions.canBePromoted() }
        if (units.none()) return null

        val wrapper = Table(skin)
        val selectUnit = StringSelect(skin, getUnitFilter(units))
        wrapper.add(selectUnit)
        wrapper.add(" {units that can be promoted}".toLabel())

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )

        selectUnit.onChange {
            rowData.selected1 = selectUnit.selected
        }
        rowData.selected1 = selectUnit.selected
        rowData.getNotificationText = {
            "Showing [${rowData.selected1}] units that can be promoted"
        }
        rowData.getLocations = {
            viewingPlayer.getCivUnits()
                .filter { it.promotions.canBePromoted() }
                .filter { it.matchesFilter(rowData.selected1) }
                .map { it.currentTile.position }
        }

        return rowData
    }

    // unimproved owned X resources
    private fun getUnimprovedResources(): RowData? {
        val tileMap = viewingPlayer.gameInfo.tileMap
        val ownedUnimprovedResourceTiles = viewingPlayer.cities.asSequence()
            // tiles are owned by cities so go from city list to all _owned_ tiles ...
            .flatMap { it.tiles }.map { tileMap[it] } 
            .filter {
                // Now only those tiles having a resource visible to the player
                it.hasViewableResource(viewingPlayer)
                // city centers do provide resources already
                && !it.isCityCenter()
                // Ignore resources w/o improvement
                && it.getTileResource().improvement != null
                // Ignore correct improvement already built
                && it.improvement != it.getTileResource().improvement
                // Ignore resources made available by great improvement
                && !(it.getTileResource().resourceType == ResourceType.Strategic
                    && it.improvement!=null
                    && it.getTileImprovement()!!.isGreatImprovement())
                // Ignore tiles where the correct improvement is underway
                && !(it.improvementInProgress == it.getTileResource().improvement
                    && it.civilianUnit != null
                    && it.civilianUnit!!.hasUnique(Constants.workerUnique))
            }
        val unimprovedResources = ownedUnimprovedResourceTiles
            .map { it.resource!! }.toHashSet()
        if (unimprovedResources.isEmpty()) return null
        val rulesetResources = viewingPlayer.gameInfo.ruleSet.tileResources
        val resourceObjects = unimprovedResources.mapNotNull { rulesetResources[it] }

        val wrapper = Table(skin)
        val selectResource = StringSelect(skin, getResourceFilter(resourceObjects))
        wrapper.add(selectResource)
        wrapper.add(" {resources that should be improved}".toLabel())

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )

        selectResource.onChange {
            rowData.selected1 = selectResource.selected
        }
        rowData.selected1 = selectResource.selected
        rowData.getNotificationText = {
            "Showing [${rowData.selected1}] resources that should be improved"
        }
        rowData.getLocations = {
            ownedUnimprovedResourceTiles
                .filter { it.getTileResource().matchesFilter(rowData.selected1) }
                .map { it.position }
        }

        return rowData
    }

    // cities not having or building X but that could have one
    private fun getCitiesMissing(): RowData? {
        class CityAndBuilding(val city: CityInfo, val building: Building)
        val citiesMissingBuildings =
            viewingPlayer.cities.asSequence()
                .flatMap {
                    city ->
                    city.cityConstructions.getBuildableBuildings().map {
                        building ->
                        CityAndBuilding(city, building)
                    }
                }
                .filter { 
                    !it.city.cityConstructions.isBeingConstructedOrEnqueued(it.building.name)
                }
        val buildings = citiesMissingBuildings.map { it.building.name }.distinct().sorted().toList()
        if (buildings.isEmpty()) return null

        val wrapper = Table(skin)
        wrapper.add("{Cities that could build} ".toLabel())
        val selectBuilding = StringSelect(skin, buildings)
        wrapper.add(selectBuilding)

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )

        selectBuilding.onChange {
            rowData.selected1 = selectBuilding.selected
        }
        rowData.selected1 = selectBuilding.selected
        rowData.getNotificationText = {
            "Showing cities that could build [${rowData.selected1}]"
        }
        rowData.getLocations = {
            val capitalTile = viewingPlayer.getCapital().getCenterTile()
            citiesMissingBuildings.filter { 
                it.building.name == rowData.selected1
            }.map { it.city }.distinct().sortedByDescending { 
                it.getCenterTile().aerialDistanceTo(capitalTile)
            }.map { it.location }
        }

        return rowData
    }

    // cities currently building X
    private fun getCitiesConstructing(): RowData? {
        class CityAndConstruction(val city: CityInfo, val construction: String)
        val citiesBuilding =
            viewingPlayer.cities.asSequence()
                .flatMap {
                    city ->
                    city.cityConstructions.constructionQueue.distinct().map {
                        construction ->
                        CityAndConstruction(city, construction)
                    }
                }
        val constructions = citiesBuilding.map { it.construction }.distinct().sorted().toList()
        if (constructions.isEmpty()) return null

        val wrapper = Table(skin)
        wrapper.add("{Cities that are constructing} ".toLabel())
        val selectConstruction = StringSelect(skin, constructions)
        wrapper.add(selectConstruction)

        val rowData = RowData( wrapper, viewingPlayer, overviewScreen )

        selectConstruction.onChange {
            rowData.selected1 = selectConstruction.selected
        }
        rowData.selected1 = selectConstruction.selected
        rowData.getNotificationText = {
            "Showing cities that are constructing [${rowData.selected1}]"
        }
        rowData.getLocations = {
            val capitalTile = viewingPlayer.getCapital().getCenterTile()
            citiesBuilding.filter {
                it.construction == rowData.selected1
            }.map { it.city }.distinct().sortedByDescending {
                it.getCenterTile().aerialDistanceTo(capitalTile)
            }.map { it.location }
        }

        return rowData
    }

    private fun getUnitFilter(units: Sequence<MapUnit>): List<String> {
        val resultList = mutableListOf("All")
        val distinctUnits = units.map { it.baseUnit() }.distinct()
        for (filter in listOf("Civilian", "Military", "Land", "Water", "Air", "Great Person")) {
            if (distinctUnits.any { it.matchesFilter(filter) }) resultList += filter
        }
        for (filter in listOf("Wounded", "Embarked")) {
            if (units.any { it.matchesFilter(filter) }) resultList += filter
        }
        for (unit in distinctUnits.sortedBy { it.name }) {
            resultList += unit.name
        }
        return resultList
    }

    private fun getResourceFilter(resources: List<TileResource>): Collection<String> {
        val resultList = mutableListOf("All")
        for (filter in ResourceType.values().map{it.name}) {
            if (resources.any { it.matchesFilter(filter) }) resultList += filter
        }
        for (resource in resources.map{it.name}.sorted()) {
            resultList += resource
        }
        return resultList
    }

    private fun getActionFilter(units: Sequence<MapUnit>): List<String> {
        val sequence = 
            sequenceOf("Doing anything") +
            units
                .map { it.actionLabel() }
                .distinct()
                .sortedBy { it }
        return sequence.toList()
    }

    // Should perhaps be a function within MapUnit and be supported by MapUnit.matchesFilter
    private fun MapUnit.actionLabel() = when {
        action == Constants.unitActionExplore -> "Exploring"
        action == Constants.unitActionAutomation -> "Automated"
        action == Constants.unitActionSetUp -> "Set up"
        isFortified() -> "Fortified"
        isSleeping() -> "Sleeping"
        isMoving() -> "Moving"
        isIdle() -> "Idle"
        hasUnique(Constants.workerUnique) && getTile().improvementInProgress != null -> "Constructing ${getTile().improvementInProgress}"
        hasUnique("Can construct roads") && currentTile.improvementInProgress == "Road" -> "Constructing Road"
        action != null -> action!!
        else -> "Done for this turn"
    }

    private fun MapUnit.matchesAction(filter: String) =
        filter == "Doing anything" || filter == actionLabel()

    private fun TileResource.matchesFilter(filter: String): Boolean {
        return when {
            filter == "All" -> true
            filter == this.resourceType.name -> true
            filter == this.name -> true
            else -> false
        }
    }
}

package com.unciv.ui.worldscreen.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.setFontSize
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

/** Basic table with customizable debug information
 * May take a while to update so disabled by default
 * Toggles on by [UncivGame.Current.showDebugInfo]
 *
 * Steps to add new object to monitor:
 * 1) add a label in the main class
 * 2) add a list wih property names or functions without arguments to display
 * 3) add an update method where [getPropsAsString] is used
 */
class DebugInfoTable(val worldScreen: WorldScreen): Table() {
    private val gameInfo = worldScreen.gameInfo
    private val worldScreenLabel = getDefaultLabel()
    private val gameInfoLabel = getDefaultLabel()
    private val unitLabel = getDefaultLabel()
    private val cityLabel = getDefaultLabel()

    private val tileInfoLabel = getDefaultLabel()
    private val tileGroupLabel = getDefaultLabel()


    private val worldScreenPropsList = listOf("viewingCiv.civName", "selectedCiv.civName", "isPlayersTurn",
            "canChangeState", "shouldUpdate")
    private val gameInfoPropsList = listOf("currentPlayer", "currentPlayerCiv.civName", "isUpToDate", "ruleSet.mods",
            "difficulty", "turns", "oneMoreTurnMode", "gameId", "getPlayerToViewAs", "gameParameters.isOnlineMultiplayer")

    private val unitPropsList = listOf("name", "owner", "health", "action", "promotions",
            "currentMovement", "isTransported", "attacksThisTurn", "due", "type")
    private val cityPropsList = listOf("civInfo.civName", "centerTileInfo.position", "hasJustBeenConquered",
            "location", "id", "name", "foundingCiv", "turnAcquired", "health", "resistanceCounter",

            "population.population", "population.foodStored", "population.getNumberOfSpecialists",
            "population.getNumberOfSpecialists","population.getFreePopulation", "population.getFoodToNextPopulation",

            "cityConstructions.builtBuildings", "cityConstructions.currentConstruction",
            "cityConstructions.currentConstructionFromQueue", "cityConstructions.currentConstructionIsUserSet",
            "cityConstructions.constructionQueue",

            "expansion.cultureStored",

//            "cityStats.baseStatList", "cityStats.finalStatList", "cityStats.statPercentBonusList", "cityStats.happinessList",
            "cityStats.foodEaten",

            "isBeingRazed", "attackedThisTurn", "hasSoldBuildingThisTurn", "isPuppet", "isOriginalCapital"
    )

    private val tileInfoPropsList = listOf("position", "latitude", "longitude", "baseTerrain", "terrainFeature",
            "naturalWonder", "resource","roadStatus", "improvement", "turnsToImprovement", "owningCity.name")
    private val tileGroupPropsList = listOf("baseLayerGroup.children.size", "terrainFeatureLayerGroup.children.size",
            "miscLayerGroup.children.size", "unitLayerGroup.children.size", "cityButtonLayerGroup.children.size",
            "circleCrosshairFogLayerGroup.children.size", "circleImage.isVisible", "crosshairImage.isVisible",
            "tileBaseImages.size", "roadImages.size", "borderImages.size", "fogImage.isVisible")


    init {
        left().top()

        var firstColumn = Table().apply {
            add(worldScreenLabel).left().row()
            add(gameInfoLabel).left().row()
            add(unitLabel).left().row()
            add(tileInfoLabel).left().row()
            debug()

        }
        var secondColumn = Table().apply {
            add(cityLabel).left().row()
            add(tileGroupLabel).left().row()
            debug()
        }

        add(firstColumn).top()
        add(secondColumn).top()

        pack()
        debug()

        zIndex = 10000
    }

    fun update() {
        updateWorldScreeTable()
        updateGameInfoTable()
        updateUnitTable()
        updateCityTable()
        updateTileInfoTable()
        updateTileGroupTable()
    }

    private fun getDefaultLabel(): Label {
        return "".toLabel().setFontSize(12).apply { color = Color.RED }
    }

    private fun updateWorldScreeTable() {
        var infoString = getPropsAsString(worldScreen, worldScreenPropsList)
        worldScreenLabel.setText(infoString)
    }

    private fun updateGameInfoTable() {
        var infoString = getPropsAsString(gameInfo, gameInfoPropsList, PropFormat.Full)
        gameInfoLabel.setText(infoString)
    }

    private fun updateUnitTable() {
        val unit = worldScreen.bottomUnitTable.selectedUnit
        var infoString = getPropsAsString(unit, unitPropsList)
        infoString += "\nhasChanged: "+worldScreen.bottomUnitTable.selectedUnitHasChanged
        unitLabel.setText("$infoString")
    }

    private fun updateCityTable() {
        val city = worldScreen.bottomUnitTable.selectedCity
        cityLabel.setText(getPropsAsString(city, cityPropsList, PropFormat.Last))
    }

    private fun updateTileInfoTable() {
        val tile = worldScreen.mapHolder.selectedTile
        val infoString = getPropsAsString(tile, tileInfoPropsList)
        tileInfoLabel.setText(infoString)
    }

    private fun updateTileGroupTable() {
        var tile = worldScreen.mapHolder.tileGroups[worldScreen.mapHolder.selectedTile]!!
        var infoString = getPropsAsString(tile, tileGroupPropsList)

        infoString += "\nisViewable: "+tile.isViewable(worldScreen.selectedCiv)
//        tileInfoString += "isExplored: "+tile?.isExplored(worldScreen.selectedCiv)+"\n"

        tileGroupLabel.setText(infoString)
    }
}


/** Generates a string representation of [obj] by a list of selected properties
 * @param [obj]             any object
 * @param [propList]        List of property names to be used in representation.
 *                          Can be simple propertues, e.g. "name"
 *                          - nested paths, e.g. "fogImage.isVisible"
 *                          - functions with no args, without "()" e.g. "getFreePopulation",
 * @param [propNameFormat]  How to display nested properties
 *                          [PropFormat.First] only first name shown, e.g. "fogImage"
 *                          [PropFormat.Last] only last name shown, e.g. "isVisible"
 *                          [PropFormat.Full] full name show, e.g. "fogImage.isVisible"
 * @return                  Text string with tab delimited property name/value pairs for [obj]
 * */
fun getPropsAsString(obj: Any?, propList: List<String>, propNameFormat: PropFormat = PropFormat.First): String {
    // short or full path name for nested properties
    fun formatName(name: String, propNameFormat: PropFormat): String {
        if (!name.contains(".")) return name
        return when (propNameFormat) {
            PropFormat.First -> "$name".split(".").first()
            PropFormat.Last -> "$name".split(".").last()
            PropFormat.Full -> name
        }
    }

    var infoString = ""

    if (obj == null) {
        // empty list for null object
        infoString += "null"
        for (name in propList) infoString += "\n"+formatName(name, propNameFormat) +": "
    } else {
        infoString += obj::class.simpleName
        for (name in propList)
            infoString += if (name.contains(".")) "\n"+formatName(name, propNameFormat) +": "+ getPropByPath(obj, name)
            else "\n$name: " + getPropByName(obj, name)
    }
    return infoString
}

fun getPropByName(obj: Any?, name: String): Any? {
    if (obj == null) return ""
    var member = getMember(obj, name)
    if (member == null) {
        println("Member $name in class ${obj::class.simpleName} not found")
        return "!error"
    }

    member.isAccessible = true

    return try { getValue(obj, member) } catch (ex: Exception) { printErrorMsg(ex); "!error" }
}

fun getPropByPath(obj: Any?, propPath: String): Any? {
    if (obj == null) return ""
    var tempObj = obj!!

    val propNames = propPath.split(".")

    for (name in propNames) {
        var member = getMember(tempObj, name)
        member?.isAccessible = true

        if (member != null)
            try {
                val value = getValue(tempObj, member)
                if (value!=null) tempObj = value
                else return ""
            }
            catch (ex:Exception) { printErrorMsg(ex); return "!error" }
        else return ""
    }

    return tempObj
}

fun getMember(obj: Any, name: String): KCallable<*>? {
    var member = obj::class.members.find { it.name == name }
    // search in superclasses
    if (member == null)
        for (kClass in obj::class.superclasses)
            member = kClass.members.find { it.name == name }
    return member
}

fun getValue(obj: Any, member: KCallable<*>): Any? {
    if (member is KProperty1<*, *>) {
        val prop = member as KProperty1<Any,*>
        return prop.get(obj)
    } else if (member is KFunction) {
        return member.call(obj)
    }

    return "!error"
}

fun printErrorMsg(ex: Exception) {
    println(ex.toString())
    for (line in ex.stackTrace)
        println(line)
}

enum class PropFormat {
    Full,
    First,
    Last
}

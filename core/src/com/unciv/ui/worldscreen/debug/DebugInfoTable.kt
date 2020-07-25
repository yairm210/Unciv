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


class DebugInfoTable(val worldScreen: WorldScreen): Table() {
    private val gameInfo = worldScreen.gameInfo
    private val viewingCivLabel = getDefaultLabel()
    private val selectedCivLabel = getDefaultLabel()
    private val unitLabel = getDefaultLabel()
    private val cityLabel = getDefaultLabel()

    private val tileInfoLabel = getDefaultLabel()
    private val tileGroupLabel = getDefaultLabel()

    private val tileInfoPropsList = listOf("position", "latitude", "longitude", "baseTerrain", "terrainFeature",
            "naturalWonder", "resource","roadStatus", "improvement", "turnsToImprovement", "owningCity.name")
    private val unitPropsList = listOf("name", "owner", "health", "action", "promotions",
            "currentMovement", "isTransported", "attacksThisTurn", "due", "type")
    private val cityInfoPropsList = listOf("")

    init {
        left()
        top()

        add(viewingCivLabel).left().row()
        add(selectedCivLabel).left().row()
        add(unitLabel).left().row()
        add(tileInfoLabel).left().row()
        add(tileGroupLabel).left().row()

        pack()
//        debug()

        zIndex = 10000
    }

    fun update() {
        viewingCivLabel.setText("viewingCiv: "+worldScreen.viewingCiv.civName)
        selectedCivLabel.setText("selectedCiv: "+worldScreen.selectedCiv.civName)
        updateUnitTable()
        updateTileInfoTable()
        updateTileGroupTable()
    }

    private fun getDefaultLabel(): Label {
        return "".toLabel().setFontSize(12).apply { color = Color.RED }
    }

    private fun updateUnitTable() {
        val unit = worldScreen.bottomUnitTable.selectedUnit

        var unitInfoString = getPropsAsString(unit, unitPropsList)

        unitInfoString += "\nhasChanged: "+worldScreen.bottomUnitTable.selectedUnitHasChanged
        unitLabel.setText("$unitInfoString")
    }

    private fun updateTileInfoTable() {
        val tile = worldScreen.mapHolder.selectedTile
        val tileInfoString = getPropsAsString(tile, tileInfoPropsList)
        tileInfoLabel.setText(tileInfoString)
    }

    private fun updateTileGroupTable() {
        var tile = worldScreen.mapHolder.tileGroups[worldScreen.mapHolder.selectedTile]
        var tileInfoString = tile!!::class.simpleName+"\n"
        tileInfoString += "isViewable: "+tile?.isViewable(worldScreen.selectedCiv)+"\n"
//        tileInfoString += "isExplored: "+tile?.isExplored(worldScreen.selectedCiv)+"\n"
        tileInfoString += "tileBaseImages: "+ getPropByPath(tile, "tileBaseImages.size")+"\n"
        tileInfoString += "road: "+ getPropByPath(tile, "roadImages.size")+"\n"
        tileInfoString += "borderImages: "+ getPropByPath(tile, "borderImages.size")+"\n"
        tileInfoString += "fogImage.isVisible: "+getPropByPath(tile, "fogImage.isVisible")+"\n"

        tileGroupLabel.setText(tileInfoString)
    }
}

/** Generates a string representation of [obj] using list of selected properties
 * @param [obj]         any object
 * @param [propList]    List of property names to be used in representation.
 *                      can use nested call: ex "fogImage.isVisible"
 * @return              Text string with property name/value pairs for [obj]
 * */
fun getPropsAsString(obj: Any?, propList: List<String>): String {
    var infoString = ""

    if (obj == null) {
        infoString += "null"
        for (name in propList) infoString += "\n$name: "
    } else {
        infoString += obj::class.simpleName
        for (name in propList) {
            infoString += if (name.contains(".")) "\n$name: " + getPropByPath(obj, name)
            else "\n$name: " + getPropByName(obj, name)
        }
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

    return try { getValue(obj, member) } catch (ex: Exception) { "!error"; printErrorMsg(ex) }
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
    var member = obj!!::class.members.find { it.name == name }
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


private fun printErrorMsg(ex: Exception) {
    println(ex.toString())
    for (line in ex.stackTrace)
        println(line)
}

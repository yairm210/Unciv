package com.unciv.models.gamebasics

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.colorFromRGB

enum class VictoryType{
    Neutral,
    Cultural,
    Domination,
    Scientific
}

class Nation : INamed {
    override lateinit var name: String
    var translatedName=""
    fun getNameTranslation(): String {
        if(translatedName!="") return translatedName
        else return name
    }

    var leaderName=""
    fun getLeaderDisplayName() = if(isCityState()) getNameTranslation()
        else "[$leaderName] of [${getNameTranslation()}]"

    var cityStateType: CityStateType?=null
    var preferredVictoryType:VictoryType = VictoryType.Neutral
    var declaringWar=""
    var attacked=""
    var defeated=""
    var introduction=""
    var tradeRequest=""

    var neutralHello=""
    var hateHello=""

    var neutralLetsHearIt = ArrayList<String>()
    var neutralYes = ArrayList<String>()
    var neutralNo = ArrayList<String>()

    var hateLetsHearIt = ArrayList<String>()
    var hateYes = ArrayList<String>()
    var hateNo = ArrayList<String>()

    var afterPeace=""

    lateinit var outerColor: List<Int>
    var unique:String?=null
    var innerColor: List<Int>?=null
    var startBias = ArrayList<String>()

    @Transient private lateinit var outerColorObject:Color
    fun getOuterColor(): Color = outerColorObject

    @Transient private lateinit var innerColorObject:Color

    fun getInnerColor(): Color = innerColorObject

    fun isCityState()= cityStateType != null
    fun isMajorCiv() = !isBarbarian() && !isCityState()
    fun isBarbarian() = name=="Barbarians"

    // This is its own transient because we'll need to check this for every tile-to-tile movement which is harsh
    @Transient var forestsAndJunglesAreRoads = false

    fun setTransients(){
        outerColorObject = colorFromRGB(outerColor[0], outerColor[1], outerColor[2])

        if(innerColor==null) innerColorObject = Color.BLACK
        else innerColorObject = colorFromRGB(innerColor!![0], innerColor!![1], innerColor!![2])

        if(unique == "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.")
            forestsAndJunglesAreRoads = true
    }

    lateinit var cities: List<String>
}

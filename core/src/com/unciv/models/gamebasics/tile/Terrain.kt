package com.unciv.models.gamebasics.tile

import com.badlogic.gdx.graphics.Color
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.gamebasics.tr
import com.unciv.models.stats.NamedStats
import com.unciv.ui.utils.colorFromRGB

class Terrain : NamedStats(), ICivilopedia {
    override val description: String
        get(){
            val sb = StringBuilder()
            sb.appendln(this.clone().toString())
            var terrainsCanBeBuiltOnString:ArrayList<String>
            terrainsCanBeBuiltOnString= arrayListOf()
            if(occursOn!=null) {
                occursOn.forEach {
                    terrainsCanBeBuiltOnString.add(it.tr())
                }
                sb.appendln("Occurs on [${terrainsCanBeBuiltOnString!!.joinToString(", ")}]".tr())
            }
            val resourcesFound = GameBasics.TileResources.values.filter { it.terrainsCanBeFoundOn.contains(name)}.joinToString()
            if(resourcesFound.isNotEmpty())
                sb.appendln("May contain [$resourcesFound]".tr())
            sb.appendln("{Movement cost}: $movementCost".tr())
            if(defenceBonus!=0f){
                sb.appendln("{Defence bonus}: ".tr()+(defenceBonus*100).toInt()+"%")
            }

            return sb.toString()
        }
    lateinit var type: TerrainType

    var overrideStats = false

    /***
     * If true, other terrain layers can come over this one. For mountains, lakes etc. this is false
     */

    var canHaveOverlay = true

    /***
     * If true, nothing can be built here - not even resource improvements
     */
    var unbuildable = false

    /***
     * For terrain features
     */
    val occursOn: Collection<String>? = null

    /**
     * RGB color of base terrain
     */
    var RGB: List<Int>? = null
    var movementCost = 1
    var defenceBonus:Float = 0f
    var impassable = false

    fun getColor(): Color = colorFromRGB(RGB!![0], RGB!![1], RGB!![2])
}
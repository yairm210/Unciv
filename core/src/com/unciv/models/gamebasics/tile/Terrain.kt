package com.unciv.models.gamebasics.tile

import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.stats.NamedStats

class Terrain : NamedStats(), ICivilopedia {
    override val description: String
        get(){
            val sb = StringBuilder()
            sb.appendln(this.clone().toString())

            if(occursOn!=null)
                sb.appendln("Occurs on: "+occursOn!!.joinToString())

            val resourcesFound = GameBasics.TileResources.values.filter { it.terrainsCanBeFoundOn.contains(name)}.joinToString()
            if(resourcesFound.isNotEmpty())
                sb.appendln("May contain: $resourcesFound")
            sb.appendln("Movement cost: $movementCost")
            if(defenceBonus!=0f){
                sb.appendln("Defence bonus: "+(defenceBonus*100).toInt()+"%")
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
    var occursOn: Collection<String>? = null

    /**
     * RGB color of base terrain
     */
    var RGB: List<Int>? = null

    var movementCost = 1

    var defenceBonus:Float = 0f
}
package com.unciv.models.gamebasics.tech

import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.ui.utils.tr
import java.util.*

class Technology : ICivilopedia {
    override val description: String
        get(){
            val SB=StringBuilder()
            for(unique in uniques) SB.appendln(unique.tr())

            val improvedImprovements = GameBasics.TileImprovements.values.filter { it.improvingTech==name }.groupBy { it.improvingTechStats.toString() }
            for (improvement in improvedImprovements) {
                val impimpString = improvement.value.joinToString { it.name.tr() } +" {provide" + (if(improvement.value.size==1) "s" else "") +"} "+improvement.key
                SB.appendln(impimpString.tr())
            }

            var enabledUnits = GameBasics.Units.values.filter { it.requiredTech==name && (it.uniqueTo==null || it.uniqueTo==UnCivGame.Current.gameInfo.getPlayerCivilization().civName) }
            val replacedUnits = enabledUnits.map { it.replaces }.filterNotNull()
            enabledUnits = enabledUnits.filter { it.name !in replacedUnits}
            if(enabledUnits.isNotEmpty()){
                SB.appendln("{Units enabled}: ")
                for(unit in enabledUnits)
                    SB.appendln(" * "+unit.name.tr() + " ("+unit.getShortDescription()+")")
            }

            var enabledBuildings = GameBasics.Buildings.values.filter { it.requiredTech==name && (it.uniqueTo==null || it.uniqueTo==UnCivGame.Current.gameInfo.getPlayerCivilization().civName) }
            val replacedBuildings = enabledBuildings.map { it.replaces }.filterNotNull()
            enabledBuildings = enabledBuildings.filter { it.name !in replacedBuildings }
            val regularBuildings = enabledBuildings.filter { !it.isWonder }
            if(regularBuildings.isNotEmpty())
                SB.appendln("{Buildings enabled}: "+regularBuildings.map { "\n * "+it.name.tr() + " ("+it.getShortDescription()+")" }.joinToString())
            val wonders = enabledBuildings.filter { it.isWonder }
            if(wonders.isNotEmpty()) SB.appendln("{Wonders enabled}: "+wonders.map { "\n * "+it.name.tr()+ " ("+it.getShortDescription()+")" }.joinToString())

            val revealedResource = GameBasics.TileResources.values.filter { it.revealedBy==name }.map { it.name }.firstOrNull() // can only be one
            if(revealedResource!=null) SB.appendln("Reveals [$revealedResource] on the map".tr())

            val tileImprovements = GameBasics.TileImprovements.values.filter { it.techRequired==name }
            if(tileImprovements.isNotEmpty()) SB.appendln("{Tile improvements enabled}: "+tileImprovements.map { it.name.tr() }.joinToString())

            return SB.toString().trim().tr()
        }
    lateinit var name: String

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    var uniques = ArrayList<String>()

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0

    override fun toString(): String {
        return name
    }

    fun era() = column!!.era
}

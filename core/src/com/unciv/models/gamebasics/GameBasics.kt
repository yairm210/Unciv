package com.unciv.models.gamebasics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.unciv.models.stats.INamed

object GameBasics {
    val Buildings = LinkedHashMap<String, Building>()
    val Terrains = LinkedHashMap<String, Terrain>()
    val TileResources = LinkedHashMap<String, TileResource>()
    val TileImprovements = LinkedHashMap<String, TileImprovement>()
    val Technologies = LinkedHashMap<String, Technology>()
    val Helps = LinkedHashMap<String, BasicHelp>()
    val Units = LinkedHashMap<String, Unit>()
    val Civilizations = LinkedHashMap<String, Civilization>()
    val PolicyBranches = LinkedHashMap<String, PolicyBranch>()
    val Tutorials = LinkedHashMap<String, List<String>>()

    private fun <T> getFromJson(tClass: Class<T>, name: String): T {
        val jsonText = Gdx.files.internal("jsons/$name.json").readString()
        return Json().fromJson(tClass, jsonText)
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    init {
            Buildings += createHashmap(getFromJson(Array<Building>::class.java, "Buildings"))
            Terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "Terrains"))
            TileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "TileResources"))
            TileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "TileImprovements"))
            Helps += createHashmap(getFromJson(Array<BasicHelp>::class.java, "BasicHelp"))
            Units += createHashmap(getFromJson(Array<Unit>::class.java, "Units"))
            PolicyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "Policies"))
            Civilizations += createHashmap(getFromJson(Array<Civilization>::class.java, "Civilizations"))

            // ...Yes. Total Voodoo. I wish I didn't have to do this.
            val x = LinkedHashMap<String,com.badlogic.gdx.utils.Array<com.badlogic.gdx.utils.Array<String>>>()
            val tutorials = getFromJson(x.javaClass, "Tutorials")
            for (tut in tutorials)
                Tutorials[tut.key] = tut.value.map{it.joinToString("\r\n")}

            val techColumns = getFromJson(Array<TechColumn>::class.java, "Techs")
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    tech.cost = techColumn.techCost
                    tech.column = techColumn
                    Technologies[tech.name] = tech
                }
            }
            for (building in Buildings.values) {
                if (building.requiredTech == null) continue
                val column = building.getRequiredTech().column
                if (building.cost == 0)
                    building.cost = if (building.isWonder) column!!.wonderCost else column!!.buildingCost
            }

            for (branch in PolicyBranches.values) {
                branch.requires = ArrayList()
                branch.branch = branch.name
                for (policy in branch.policies) {
                    policy.branch = branch.name
                    if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                }
                branch.policies.last().name = branch.name + " Complete"
            }
        }
}
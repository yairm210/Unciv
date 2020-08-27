package com.unciv.models.ruleset

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.INamed
import java.lang.StringBuilder
import kotlin.collections.set

object ModOptionsConstants {
    const val diplomaticRelationshipsCannotChange = "Diplomatic relationships cannot change"
}

class ModOptions {
    var isBaseRuleset = false
    var techsToRemove = HashSet<String>()
    var buildingsToRemove = HashSet<String>()
    var unitsToRemove = HashSet<String>()
    var nationsToRemove = HashSet<String>()
    var uniques = HashSet<String>()
}

class Ruleset {

    private val jsonParser = JsonParser()

    var name = ""
    val buildings = LinkedHashMap<String, Building>()
    val terrains = LinkedHashMap<String, Terrain>()
    val tileResources = LinkedHashMap<String, TileResource>()
    val tileImprovements = LinkedHashMap<String, TileImprovement>()
    val technologies = LinkedHashMap<String, Technology>()
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val nations = LinkedHashMap<String, Nation>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val difficulties = LinkedHashMap<String, Difficulty>()
    val mods = LinkedHashSet<String>()
    var modOptions = ModOptions()

    fun clone(): Ruleset {
        val newRuleset = Ruleset()
        newRuleset.add(this)
        return newRuleset
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    fun add(ruleset: Ruleset) {
        buildings.putAll(ruleset.buildings)
        for (buildingToRemove in ruleset.modOptions.buildingsToRemove) buildings.remove(buildingToRemove)
        difficulties.putAll(ruleset.difficulties)
        nations.putAll(ruleset.nations)
        policyBranches.putAll(ruleset.policyBranches)
        technologies.putAll(ruleset.technologies)
        for (techToRemove in ruleset.modOptions.techsToRemove) technologies.remove(techToRemove)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        unitPromotions.putAll(ruleset.unitPromotions)
        units.putAll(ruleset.units)
        for (unitToRemove in ruleset.modOptions.unitsToRemove) units.remove(unitToRemove)
        for (nationToRemove in ruleset.modOptions.unitsToRemove) nations.remove(nationToRemove)
        mods += ruleset.mods
    }

    fun clear() {
        buildings.clear()
        difficulties.clear()
        nations.clear()
        policyBranches.clear()
        technologies.clear()
        buildings.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        units.clear()
        mods.clear()
    }


    fun load(folderHandle: FileHandle) {
        val gameBasicsStartTime = System.currentTimeMillis()

        val modOptionsFile = folderHandle.child("ModOptions.json")
        if (modOptionsFile.exists()) {
            try {
                modOptions = jsonParser.getFromJson(ModOptions::class.java, modOptionsFile)
            } catch (ex: Exception) {
            }
        }

        val techFile = folderHandle.child("Techs.json")
        if (techFile.exists()) {
            val techColumns = jsonParser.getFromJson(Array<TechColumn>::class.java, techFile)
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    if (tech.cost == 0) tech.cost = techColumn.techCost
                    tech.column = techColumn
                    technologies[tech.name] = tech
                }
            }
        }

        val buildingsFile = folderHandle.child("Buildings.json")
        if (buildingsFile.exists()) buildings += createHashmap(jsonParser.getFromJson(Array<Building>::class.java, buildingsFile))

        val terrainsFile = folderHandle.child("Terrains.json")
        if (terrainsFile.exists()) terrains += createHashmap(jsonParser.getFromJson(Array<Terrain>::class.java, terrainsFile))

        val resourcesFile = folderHandle.child("TileResources.json")
        if (resourcesFile.exists()) tileResources += createHashmap(jsonParser.getFromJson(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = folderHandle.child("TileImprovements.json")
        if (improvementsFile.exists()) tileImprovements += createHashmap(jsonParser.getFromJson(Array<TileImprovement>::class.java, improvementsFile))

        val unitsFile = folderHandle.child("Units.json")
        if (unitsFile.exists()) units += createHashmap(jsonParser.getFromJson(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = folderHandle.child("UnitPromotions.json")
        if (promotionsFile.exists()) unitPromotions += createHashmap(jsonParser.getFromJson(Array<Promotion>::class.java, promotionsFile))

        val policiesFile = folderHandle.child("Policies.json")
        if (policiesFile.exists()) {
            policyBranches += createHashmap(jsonParser.getFromJson(Array<PolicyBranch>::class.java, policiesFile))
            for (branch in policyBranches.values) {
                branch.requires = ArrayList()
                branch.branch = branch
                for (policy in branch.policies) {
                    policy.branch = branch
                    if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                }
                branch.policies.last().name = branch.name + " Complete"
            }
        }

        val nationsFile = folderHandle.child("Nations.json")
        if (nationsFile.exists()) {
            nations += createHashmap(jsonParser.getFromJson(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = folderHandle.child("Difficulties.json")
        if (difficultiesFile.exists()) difficulties += createHashmap(jsonParser.getFromJson(Array<Difficulty>::class.java, difficultiesFile))

        val gameBasicsLoadTime = System.currentTimeMillis() - gameBasicsStartTime
        println("Loading game basics - " + gameBasicsLoadTime + "ms")
    }

    /** Building costs are unique in that they are dependant on info in the technology part.
     *  This means that if you add a building in a mod, you want it to depend on the original tech values.
     *  Alternatively, if you edit a tech column's building costs, you want it to affect all buildings in that column.
     *  This deals with that
     *  */
    fun updateBuildingCosts() {
        for (building in buildings.values) {
            if (building.cost == 0) {
                val column = technologies[building.requiredTech]?.column
                        ?: throw UncivShowableException("Building (${building.name}) must either have an explicit cost or reference an existing tech")
                building.cost = if (building.isWonder || building.isNationalWonder) column.wonderCost else column.buildingCost
            }
        }
    }

    fun getEras(): List<String> {
        return technologies.values.map { it.column!!.era }.distinct()
    }

    fun getEraNumber(era: String) = getEras().indexOf(era)
    fun getSummary(): String {
        val stringList = ArrayList<String>()
        if (modOptions.isBaseRuleset) stringList += "Base Ruleset\n"
        if (technologies.isNotEmpty()) stringList.add(technologies.size.toString() + " Techs")
        if (nations.isNotEmpty()) stringList.add(nations.size.toString() + " Nations")
        if (units.isNotEmpty()) stringList.add(units.size.toString() + " Units")
        if (buildings.isNotEmpty()) stringList.add(buildings.size.toString() + " Buildings")
        if (tileResources.isNotEmpty()) stringList.add(tileResources.size.toString() + " Resources")
        if (tileImprovements.isNotEmpty()) stringList.add(tileImprovements.size.toString() + " Improvements")
        stringList += ""
        return stringList.joinToString()
    }
}

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache :HashMap<String,Ruleset>() {
    fun loadRulesets(consoleMode:Boolean=false) {
        clear()
        for (ruleset in BaseRuleset.values()) {
            val fileName = "jsons/${ruleset.fullName}"
            val fileHandle = if (consoleMode) FileHandle(fileName)
            else Gdx.files.internal(fileName)
            this[ruleset.fullName] = Ruleset().apply { load(fileHandle) }
        }

        val modsHandles = if(consoleMode) FileHandle("mods").list()
                                else Gdx.files.local("mods").list()

        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            try {
                val modRuleset = Ruleset()
                modRuleset.load(modFolder.child("jsons"))
                modRuleset.name = modFolder.name()
                this[modRuleset.name] = modRuleset
                println("Mod loaded successfully: " + modRuleset.name)
                checkModLinks(modRuleset)
            } catch (ex: Exception) {
                println("Exception loading mod '${modFolder.name()}':")
                println("  ${ex.localizedMessage}")
                println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
            }
        }
    }

    fun checkModLinks(modRuleset: Ruleset) {
        for (unit in modRuleset.units.values) {
            if (unit.requiredTech != null && !modRuleset.technologies.containsKey(unit.requiredTech!!))
                println("${unit.name} requires tech ${unit.requiredTech} which does not exist!")
            if (unit.obsoleteTech != null && !modRuleset.technologies.containsKey(unit.obsoleteTech!!))
                println("${unit.name} obsoletes at tech ${unit.obsoleteTech} which does not exist!")
            if (unit.requiredResource != null && !modRuleset.tileResources.containsKey(unit.requiredResource!!))
                println("${unit.name} requires resource ${unit.requiredResource} which does not exist!")
            if (unit.upgradesTo != null && !modRuleset.units.containsKey(unit.upgradesTo!!))
                println("${unit.name} upgrades to unit ${unit.upgradesTo} which does not exist!")
            if (unit.replaces != null && !modRuleset.units.containsKey(unit.replaces!!))
                println("${unit.replaces} replaces ${unit.replaces} which does not exist!")
        }

        for (building in modRuleset.buildings.values) {
            if (building.requiredTech != null && !modRuleset.technologies.containsKey(building.requiredTech!!))
                println("${building.name} requires tech ${building.requiredTech} which does not exist!")
            if (building.requiredResource != null && !modRuleset.tileResources.containsKey(building.requiredResource!!))
                println("${building.name} requires resource ${building.requiredResource} which does not exist!")
            if (building.replaces != null && !modRuleset.buildings.containsKey(building.replaces!!))
                println("${building.name} replaces ${building.replaces} which does not exist!")
        }

        for (resource in modRuleset.tileResources.values) {
            if (resource.revealedBy != null && !modRuleset.technologies.containsKey(resource.revealedBy!!))
                println("${resource.name} revealed by tech ${resource.revealedBy} which does not exist!")
            if (resource.improvement != null && !modRuleset.tileImprovements.containsKey(resource.improvement!!))
                println("${resource.name} improved by improvement ${resource.improvement} which does not exist!")
        }

        for (improvement in modRuleset.tileImprovements.values) {
            if (improvement.techRequired != null && !modRuleset.technologies.containsKey(improvement.techRequired!!))
                println("${improvement.name} requires tech ${improvement.techRequired} which does not exist!")
        }

        for (tech in modRuleset.technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!modRuleset.technologies.containsKey(prereq))
                    println("${tech.name} requires tech $prereq which does not exist!")
            }
            for (otherTech in tech.column!!.techs) {
                if (tech != otherTech && otherTech.row == tech.row)
                    println("${tech.name} is in the same row as ${otherTech.name}!")
            }
        }

    }

    fun getBaseRuleset() = this[BaseRuleset.Civ_V_Vanilla.fullName]!!

    fun getComplexRuleset(gameParameters: GameParameters): Ruleset {
        val newRuleset = Ruleset()
        val loadedMods = gameParameters.mods.filter { containsKey(it) }.map { this[it]!! }
        if (loadedMods.none { it.modOptions.isBaseRuleset })
            newRuleset.add(this[gameParameters.baseRuleset.fullName]!!)
        for (mod in loadedMods.sortedByDescending { it.modOptions.isBaseRuleset }) {
            newRuleset.add(mod)
            newRuleset.mods += mod.name
            if(mod.modOptions.isBaseRuleset){
                newRuleset.modOptions = mod.modOptions
            }
        }
        newRuleset.updateBuildingCosts() // only after we've added all the mods can we calculate the building costs

        return newRuleset
    }
}
package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import com.unciv.logic.UncivShowableException
import com.unciv.models.Counter
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
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.colorFromRGB
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
    val maxXPfromBarbarians = 30
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
    val quests = LinkedHashMap<String, Quest>()
    val specialists = LinkedHashMap<String, Specialist>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val policies = LinkedHashMap<String, Policy>()
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
        policies.putAll(ruleset.policies)
        quests.putAll(ruleset.quests)
        specialists.putAll(ruleset.specialists)
        technologies.putAll(ruleset.technologies)
        for (techToRemove in ruleset.modOptions.techsToRemove) technologies.remove(techToRemove)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        unitPromotions.putAll(ruleset.unitPromotions)
        units.putAll(ruleset.units)
        for (unitToRemove in ruleset.modOptions.unitsToRemove) units.remove(unitToRemove)
        for (nationToRemove in ruleset.modOptions.nationsToRemove) nations.remove(nationToRemove)
        mods += ruleset.mods
    }

    fun clear() {
        buildings.clear()
        difficulties.clear()
        nations.clear()
        policyBranches.clear()
        policies.clear()
        quests.clear()
        technologies.clear()
        buildings.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        specialists.clear()
        units.clear()
        mods.clear()
    }


    fun load(folderHandle: FileHandle, printOutput: Boolean) {
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

        val questsFile = folderHandle.child("Quests.json")
        if (questsFile.exists()) quests += createHashmap(jsonParser.getFromJson(Array<Quest>::class.java, questsFile))

        val specialistsFile = folderHandle.child("Specialists.json")
        if (specialistsFile.exists()) specialists += createHashmap(jsonParser.getFromJson(Array<Specialist>::class.java, specialistsFile))

        val policiesFile = folderHandle.child("Policies.json")
        if (policiesFile.exists()) {
            policyBranches += createHashmap(jsonParser.getFromJson(Array<PolicyBranch>::class.java, policiesFile))
            for (branch in policyBranches.values) {
                branch.requires = ArrayList()
                branch.branch = branch
                policies[branch.name] = branch
                for (policy in branch.policies) {
                    policy.branch = branch
                    if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                    policies[policy.name] = policy
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
        if (printOutput) println("Loading ruleset - " + gameBasicsLoadTime + "ms")
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


    fun checkModLinks(): String {
        val lines = ArrayList<String>()

        // Checks for all mods
        for (unit in units.values) {
            if (unit.upgradesTo == unit.name)
                lines += "${unit.name} upgrades to itself!"
            if (!unit.unitType.isCivilian() && unit.strength == 0)
                lines += "${unit.name} is a military unit but has no assigned strength!"
            if (unit.unitType.isRanged() && unit.rangedStrength == 0)
                lines += "${unit.name} is a ranged unit but has no assigned rangedStrength!"
        }

        for (tech in technologies.values) {
            for (otherTech in technologies.values) {
                if (tech != otherTech && otherTech.column == tech.column && otherTech.row == tech.row)
                    lines += "${tech.name} is in the same row as ${otherTech.name}!"
            }
        }

        for (building in buildings.values) {
            if (building.requiredTech == null && building.cost == 0)
                lines += "${building.name} must either have an explicit cost or reference an existing tech!"
        }

        if (!modOptions.isBaseRuleset) return lines.joinToString("\n")


        for (unit in units.values) {
            if (unit.requiredTech != null && !technologies.containsKey(unit.requiredTech!!))
                lines += "${unit.name} requires tech ${unit.requiredTech} which does not exist!"
            if (unit.obsoleteTech != null && !technologies.containsKey(unit.obsoleteTech!!))
                lines += "${unit.name} obsoletes at tech ${unit.obsoleteTech} which does not exist!"
            for (resource in unit.getResourceRequirements().keys)
                if (!tileResources.containsKey(resource))
                    lines += "${unit.name} requires resource $resource which does not exist!"
            if (unit.upgradesTo != null && !units.containsKey(unit.upgradesTo!!))
                lines += "${unit.name} upgrades to unit ${unit.upgradesTo} which does not exist!"
            if (unit.replaces != null && !units.containsKey(unit.replaces!!))
                lines += "${unit.name} replaces ${unit.replaces} which does not exist!"
            for (promotion in unit.promotions)
                if (!unitPromotions.containsKey(promotion))
                    lines += "${unit.replaces} contains promotion $promotion which does not exist!"

        }

        for (building in buildings.values) {
            if (building.requiredTech != null && !technologies.containsKey(building.requiredTech!!))
                lines += "${building.name} requires tech ${building.requiredTech} which does not exist!"
            for (resource in building.getResourceRequirements().keys)
                if (!tileResources.containsKey(resource))
                    lines += "${building.name} requires resource $resource which does not exist!"
            if (building.replaces != null && !buildings.containsKey(building.replaces!!))
                lines += "${building.name} replaces ${building.replaces} which does not exist!"
            if (building.requiredBuilding != null && !buildings.containsKey(building.requiredBuilding!!))
                lines += "${building.name} requires ${building.requiredBuilding} which does not exist!"
            if (building.requiredBuildingInAllCities != null && !buildings.containsKey(building.requiredBuildingInAllCities!!))
                lines += "${building.name} requires ${building.requiredBuildingInAllCities} in all cities which does not exist!"
            if (building.providesFreeBuilding != null && !buildings.containsKey(building.providesFreeBuilding!!))
                lines += "${building.name} provides a free ${building.providesFreeBuilding} which does not exist!"
        }

        for (resource in tileResources.values) {
            if (resource.revealedBy != null && !technologies.containsKey(resource.revealedBy!!))
                lines += "${resource.name} revealed by tech ${resource.revealedBy} which does not exist!"
            if (resource.improvement != null && !tileImprovements.containsKey(resource.improvement!!))
                lines += "${resource.name} improved by improvement ${resource.improvement} which does not exist!"
            for (terrain in resource.terrainsCanBeFoundOn)
                if (!terrains.containsKey(terrain))
                    lines += "${resource.name} can be found on terrain $terrain which does not exist!"
        }

        for (improvement in tileImprovements.values) {
            if (improvement.techRequired != null && !technologies.containsKey(improvement.techRequired!!))
                lines += "${improvement.name} requires tech ${improvement.techRequired} which does not exist!"
            for (terrain in improvement.terrainsCanBeBuiltOn)
                if (!terrains.containsKey(terrain))
                    lines += "${improvement.name} can be built on terrain $terrain which does not exist!"
        }

        for (terrain in terrains.values) {
            for (baseTerrain in terrain.occursOn)
                if (!terrains.containsKey(baseTerrain))
                    lines += "${terrain.name} occurs on terrain $baseTerrain which does not exist!"
        }

        for (tech in technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!technologies.containsKey(prereq))
                    lines += "${tech.name} requires tech $prereq which does not exist!"
            }
        }
        return lines.joinToString("\n")
    }
}

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache :HashMap<String,Ruleset>() {
    fun loadRulesets(consoleMode: Boolean = false, printOutput: Boolean = false) {
        clear()
        for (ruleset in BaseRuleset.values()) {
            val fileName = "jsons/${ruleset.fullName}"
            val fileHandle = if (consoleMode) FileHandle(fileName)
            else Gdx.files.internal(fileName)
            this[ruleset.fullName] = Ruleset().apply { load(fileHandle, printOutput) }
        }

        val modsHandles = if (consoleMode) FileHandle("mods").list()
        else Gdx.files.local("mods").list()

        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            if (!modFolder.isDirectory) continue
            try {
                val modRuleset = Ruleset()
                modRuleset.load(modFolder.child("jsons"), printOutput)
                modRuleset.name = modFolder.name()
                this[modRuleset.name] = modRuleset
                if (printOutput) {
                    println("Mod loaded successfully: " + modRuleset.name)
                    println(modRuleset.checkModLinks())
                }
            } catch (ex: Exception) {
                if (printOutput) {
                    println("Exception loading mod '${modFolder.name()}':")
                    println("  ${ex.localizedMessage}")
                    println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
                }
            }
        }
    }


    fun getBaseRuleset() = this[BaseRuleset.Civ_V_Vanilla.fullName]!!.clone() // safeguard, o no-one edits the base ruleset by mistake

    fun getComplexRuleset(mods:LinkedHashSet<String>):Ruleset{
        val newRuleset = Ruleset()
        val loadedMods = mods.filter { containsKey(it) }.map { this[it]!! }
        if (loadedMods.none { it.modOptions.isBaseRuleset })
            newRuleset.add(getBaseRuleset())
        for (mod in loadedMods.sortedByDescending { it.modOptions.isBaseRuleset }) {
            newRuleset.add(mod)
            newRuleset.mods += mod.name
            if (mod.modOptions.isBaseRuleset) {
                newRuleset.modOptions = mod.modOptions
            }
        }
        newRuleset.updateBuildingCosts() // only after we've added all the mods can we calculate the building costs

        return newRuleset
    }

}

class Specialist: NamedStats() {
    var color = ArrayList<Int>()
    val colorObject by lazy { colorFromRGB(color) }
    var greatPersonPoints = Stats()

    companion object {
        internal fun specialistNameByStat(stat: Stat) = when (stat) {
            Stat.Production -> "Engineer"
            Stat.Gold -> "Merchant"
            Stat.Science -> "Scientist"
            Stat.Culture -> "Artist"
            else -> TODO()
        }
    }
}
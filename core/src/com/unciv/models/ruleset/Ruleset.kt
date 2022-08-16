package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.RoadStatus
import com.unciv.models.Counter
import com.unciv.models.ModConstants
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.getRelativeTextDistance
import com.unciv.utils.Log
import com.unciv.utils.debug
import kotlin.collections.set

object ModOptionsConstants {
    const val diplomaticRelationshipsCannotChange = "Diplomatic relationships cannot change"
    const val convertGoldToScience = "Can convert gold to science with sliders"
    const val allowCityStatesSpawnUnits = "Allow City States to spawn with additional units"
    const val tradeCivIntroductions = "Can trade civilization introductions for [] Gold"
    const val disableReligion = "Disable religion"
}

class ModOptions : IHasUniques {
    var isBaseRuleset = false
    var techsToRemove = HashSet<String>()
    var buildingsToRemove = HashSet<String>()
    var unitsToRemove = HashSet<String>()
    var nationsToRemove = HashSet<String>()


    var lastUpdated = ""
    var modUrl = ""
    var author = ""
    var modSize = 0
    var topics = mutableListOf<String>()

    @Deprecated("As of 3.18.15")
    var maxXPfromBarbarians = 30

    override var uniques = ArrayList<String>()

    // If these two are delegated with "by lazy", the mod download process crashes and burns
    // Instead, Ruleset.load sets them, which is preferable in this case anyway
    override var uniqueObjects: List<Unique> = listOf()
    override var uniqueMap: Map<String, List<Unique>> = mapOf()

    override fun getUniqueTarget() = UniqueTarget.ModOptions

    val constants = ModConstants()
}

class Ruleset {

    var folderLocation:FileHandle?=null

    var name = ""
    val beliefs = LinkedHashMap<String, Belief>()
    val buildings = LinkedHashMap<String, Building>()
    val difficulties = LinkedHashMap<String, Difficulty>()
    val eras = LinkedHashMap<String, Era>()
    val speeds = LinkedHashMap<String, Speed>()
    var globalUniques = GlobalUniques()
    val nations = LinkedHashMap<String, Nation>()
    val policies = LinkedHashMap<String, Policy>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val religions = ArrayList<String>()
    val ruinRewards = LinkedHashMap<String, RuinReward>()
    val quests = LinkedHashMap<String, Quest>()
    val specialists = LinkedHashMap<String, Specialist>()
    val technologies = LinkedHashMap<String, Technology>()
    val terrains = LinkedHashMap<String, Terrain>()
    val tileImprovements = LinkedHashMap<String, TileImprovement>()
    val tileResources = LinkedHashMap<String, TileResource>()
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val unitTypes = LinkedHashMap<String, UnitType>()
    var victories = LinkedHashMap<String, Victory>()

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
        beliefs.putAll(ruleset.beliefs)
        buildings.putAll(ruleset.buildings)
        for (buildingToRemove in ruleset.modOptions.buildingsToRemove) buildings.remove(buildingToRemove)
        difficulties.putAll(ruleset.difficulties)
        eras.putAll(ruleset.eras)
        speeds.putAll(ruleset.speeds)
        globalUniques = GlobalUniques().apply { uniques.addAll(globalUniques.uniques); uniques.addAll(ruleset.globalUniques.uniques) }
        nations.putAll(ruleset.nations)
        for (nationToRemove in ruleset.modOptions.nationsToRemove) nations.remove(nationToRemove)
        policyBranches.putAll(ruleset.policyBranches)
        policies.putAll(ruleset.policies)
        quests.putAll(ruleset.quests)
        religions.addAll(ruleset.religions)
        ruinRewards.putAll(ruleset.ruinRewards)
        specialists.putAll(ruleset.specialists)
        technologies.putAll(ruleset.technologies)
        for (techToRemove in ruleset.modOptions.techsToRemove) technologies.remove(techToRemove)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        unitPromotions.putAll(ruleset.unitPromotions)
        units.putAll(ruleset.units)
        unitTypes.putAll(ruleset.unitTypes)
        victories.putAll(ruleset.victories)
        for (unitToRemove in ruleset.modOptions.unitsToRemove) units.remove(unitToRemove)
        modOptions.uniques.addAll(ruleset.modOptions.uniques)
        modOptions.constants.merge(ruleset.modOptions.constants)
        mods += ruleset.mods
    }

    fun clear() {
        beliefs.clear()
        buildings.clear()
        difficulties.clear()
        eras.clear()
        speeds.clear()
        globalUniques = GlobalUniques()
        mods.clear()
        nations.clear()
        policies.clear()
        policyBranches.clear()
        quests.clear()
        religions.clear()
        ruinRewards.clear()
        specialists.clear()
        technologies.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        units.clear()
        unitTypes.clear()
        victories.clear()
    }

    fun allRulesetObjects(): Sequence<IRulesetObject> =
            beliefs.values.asSequence() +
            buildings.values.asSequence() +
            //difficulties is only INamed
            eras.values.asSequence() +
            speeds.values.asSequence() +
            sequenceOf(globalUniques) +
            nations.values.asSequence() +
            policies.values.asSequence() +
            policyBranches.values.asSequence() +
            // quests is only INamed
            // religions is just Strings
            ruinRewards.values.asSequence() +
            // specialists is only NamedStats
            technologies.values.asSequence() +
            terrains.values.asSequence() +
            tileImprovements.values.asSequence() +
            tileResources.values.asSequence() +
            unitPromotions.values.asSequence() +
            units.values.asSequence() +
            unitTypes.values.asSequence()
            // Victories is only INamed
    fun allIHasUniques(): Sequence<IHasUniques> =
            allRulesetObjects() + sequenceOf(modOptions)

    fun load(folderHandle: FileHandle) {
        val gameBasicsStartTime = System.currentTimeMillis()

        val modOptionsFile = folderHandle.child("ModOptions.json")
        if (modOptionsFile.exists()) {
            try {
                modOptions = json().fromJsonFile(ModOptions::class.java, modOptionsFile)
                modOptions.updateDeprecations()
            } catch (ex: Exception) {}
            modOptions.uniqueObjects = modOptions.uniques.map { Unique(it, UniqueTarget.ModOptions) }
            modOptions.uniqueMap = modOptions.uniqueObjects.groupBy { it.placeholderText }
        }

        val techFile = folderHandle.child("Techs.json")
        if (techFile.exists()) {
            val techColumns = json().fromJsonFile(Array<TechColumn>::class.java, techFile)
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    if (tech.cost == 0) tech.cost = techColumn.techCost
                    tech.column = techColumn
                    technologies[tech.name] = tech
                }
            }
        }

        val buildingsFile = folderHandle.child("Buildings.json")
        if (buildingsFile.exists()) buildings += createHashmap(json().fromJsonFile(Array<Building>::class.java, buildingsFile))
        for(building in buildings.values)
            if(building.requiredBuildingInAllCities != null)
                building.uniques.add(UniqueType.RequiresBuildingInAllCities.text.fillPlaceholders(building.requiredBuildingInAllCities!!))

        val terrainsFile = folderHandle.child("Terrains.json")
        if (terrainsFile.exists()) {
            terrains += createHashmap(json().fromJsonFile(Array<Terrain>::class.java, terrainsFile))
            for (terrain in terrains.values) terrain.setTransients()
        }

        val resourcesFile = folderHandle.child("TileResources.json")
        if (resourcesFile.exists()) tileResources += createHashmap(json().fromJsonFile(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = folderHandle.child("TileImprovements.json")
        if (improvementsFile.exists()) tileImprovements += createHashmap(json().fromJsonFile(Array<TileImprovement>::class.java, improvementsFile))

        val erasFile = folderHandle.child("Eras.json")
        if (erasFile.exists()) eras += createHashmap(json().fromJsonFile(Array<Era>::class.java, erasFile))
        // While `eras.values.toList()` might seem more logical, eras.values is a MutableCollection and
        // therefore does not guarantee keeping the order of elements like a LinkedHashMap does.
        // Using map{} sidesteps this problem
        eras.map { it.value }.withIndex().forEach { it.value.eraNumber = it.index }

        val speedsFile = folderHandle.child("Speeds.json")
        if (speedsFile.exists()) {
            speeds += createHashmap(json().fromJsonFile(Array<Speed>::class.java, speedsFile))
        }

        val unitTypesFile = folderHandle.child("UnitTypes.json")
        if (unitTypesFile.exists()) unitTypes += createHashmap(json().fromJsonFile(Array<UnitType>::class.java, unitTypesFile))

        val unitsFile = folderHandle.child("Units.json")
        if (unitsFile.exists()) units += createHashmap(json().fromJsonFile(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = folderHandle.child("UnitPromotions.json")
        if (promotionsFile.exists()) unitPromotions += createHashmap(json().fromJsonFile(Array<Promotion>::class.java, promotionsFile))

        val questsFile = folderHandle.child("Quests.json")
        if (questsFile.exists()) quests += createHashmap(json().fromJsonFile(Array<Quest>::class.java, questsFile))

        val specialistsFile = folderHandle.child("Specialists.json")
        if (specialistsFile.exists()) specialists += createHashmap(json().fromJsonFile(Array<Specialist>::class.java, specialistsFile))

        val policiesFile = folderHandle.child("Policies.json")
        if (policiesFile.exists()) {
            policyBranches += createHashmap(
                json().fromJsonFile(Array<PolicyBranch>::class.java, policiesFile)
            )
            for (branch in policyBranches.values) {
                // Setup this branch
                branch.requires = ArrayList()
                branch.branch = branch
                for (victoryType in victories.values) {
                    if (victoryType.name !in branch.priorities.keys) {
                        branch.priorities[victoryType.name] = 0
                    }
                }
                policies[branch.name] = branch

                // Append child policies of this branch
                for (policy in branch.policies) {
                    policy.branch = branch
                    if (policy.requires == null) {
                        policy.requires = arrayListOf(branch.name)
                    }
                    policies[policy.name] = policy
                }

                // Add a finisher
                branch.policies.last().name =
                    branch.name + Policy.branchCompleteSuffix
            }
        }

        val beliefsFile = folderHandle.child("Beliefs.json")
        if (beliefsFile.exists())
            beliefs += createHashmap(json().fromJsonFile(Array<Belief>::class.java, beliefsFile))

        val religionsFile = folderHandle.child("Religions.json")
        if (religionsFile.exists())
            religions += json().fromJsonFile(Array<String>::class.java, religionsFile).toList()

        val ruinRewardsFile = folderHandle.child("Ruins.json")
        if (ruinRewardsFile.exists())
            ruinRewards += createHashmap(json().fromJsonFile(Array<RuinReward>::class.java, ruinRewardsFile))

        val nationsFile = folderHandle.child("Nations.json")
        if (nationsFile.exists()) {
            nations += createHashmap(json().fromJsonFile(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = folderHandle.child("Difficulties.json")
        if (difficultiesFile.exists())
            difficulties += createHashmap(json().fromJsonFile(Array<Difficulty>::class.java, difficultiesFile))

        val globalUniquesFile = folderHandle.child("GlobalUniques.json")
        if (globalUniquesFile.exists()) {
            globalUniques = json().fromJsonFile(GlobalUniques::class.java, globalUniquesFile)
        }

        val victoryTypesFiles = folderHandle.child("VictoryTypes.json")
        if (victoryTypesFiles.exists()) {
            victories += createHashmap(json().fromJsonFile(Array<Victory>::class.java, victoryTypesFiles))
        }



        // Add objects that might not be present in base ruleset mods, but are required
        if (modOptions.isBaseRuleset) {
            // This one should be temporary
            if (unitTypes.isEmpty()) {
                unitTypes.putAll(RulesetCache.getVanillaRuleset().unitTypes)
            }

            // These should be permanent
            if (ruinRewards.isEmpty()) {
                ruinRewards.putAll(RulesetCache.getVanillaRuleset().ruinRewards)
            }
            if (globalUniques.uniques.isEmpty()) {
                globalUniques = RulesetCache.getVanillaRuleset().globalUniques
            }
            // If we have no victories, add all the default victories
            if (victories.isEmpty()) {
                victories.putAll(RulesetCache.getVanillaRuleset().victories)
            }

            if (speeds.isEmpty()) {
                speeds.putAll(RulesetCache.getVanillaRuleset().speeds)
            }
        }

        debug("Loading ruleset - %sms", System.currentTimeMillis() - gameBasicsStartTime)
    }

    /** Building costs are unique in that they are dependant on info in the technology part.
     *  This means that if you add a building in a mod, you want it to depend on the original tech values.
     *  Alternatively, if you edit a tech column's building costs, you want it to affect all buildings in that column.
     *  This deals with that
     *  */
    fun updateBuildingCosts() {
        for (building in buildings.values) {
            if (building.cost == 0 && !building.hasUnique(UniqueType.Unbuildable)) {
                val column = technologies[building.requiredTech]?.column
                        ?: throw UncivShowableException("Building '[${building.name}]' is buildable and therefore must either have an explicit cost or reference an existing tech.")
                building.cost = if (building.isAnyWonder()) column.wonderCost else column.buildingCost
            }
        }
    }

    /** Used for displaying a RuleSet's name */
    override fun toString() = when {
        name.isNotEmpty() -> name
        mods.size == 1 && RulesetCache[mods.first()]!!.modOptions.isBaseRuleset -> mods.first()
        else -> "Combined RuleSet"
    }

    fun getSummary(): String {
        val stringList = ArrayList<String>()
        if (modOptions.isBaseRuleset) stringList += "Base Ruleset"
        if (technologies.isNotEmpty()) stringList += "[${technologies.size}] Techs"
        if (nations.isNotEmpty()) stringList += "[${nations.size}] Nations"
        if (units.isNotEmpty()) stringList += "[${units.size}] Units"
        if (buildings.isNotEmpty()) stringList += "[${buildings.size}] Buildings"
        if (tileResources.isNotEmpty()) stringList += "[${tileResources.size}] Resources"
        if (tileImprovements.isNotEmpty()) stringList += "[${tileImprovements.size}] Improvements"
        if (religions.isNotEmpty()) stringList += "[${religions.size}] Religions"
        if (beliefs.isNotEmpty()) stringList += "[${beliefs.size}] Beliefs"
        return stringList.joinToString { it.tr() }
    }

    fun checkUniques(
        uniqueContainer: IHasUniques,
        lines: RulesetErrorList,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity,
        forOptionsPopup: Boolean
    ) {
        val name = if (uniqueContainer is INamed) uniqueContainer.name else ""

        for (unique in uniqueContainer.uniqueObjects) {
            val errors = checkUnique(
                unique,
                forOptionsPopup,
                name,
                severityToReport,
                uniqueContainer.getUniqueTarget()
            )
            lines.addAll(errors)
        }
    }

    fun checkUnique(
        unique: Unique,
        forOptionsPopup: Boolean,
        name: String,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity,
        uniqueTarget: UniqueTarget
    ): List<RulesetError> {
        if (unique.type == null) {
            if (!forOptionsPopup) return emptyList()
            val similarUniques = UniqueType.values().filter {
                getRelativeTextDistance(
                    it.placeholderText,
                    unique.placeholderText
                ) <= RulesetCache.uniqueMisspellingThreshold
            }
            val equalUniques =
                similarUniques.filter { it.placeholderText == unique.placeholderText }
            return when {
                // Malformed conditional
                unique.text.count { it=='<' } != unique.text.count { it=='>' } ->listOf(
                        RulesetError("$name's unique \"${unique.text}\" contains mismatched conditional braces!",
                            RulesetErrorSeverity.Warning))

                // This should only ever happen if a bug is or has been introduced that prevents Unique.type from being set for a valid UniqueType, I think.\
                equalUniques.isNotEmpty() -> listOf(RulesetError(
                        "$name's unique \"${unique.text}\" looks like it should be fine, but for some reason isn't recognized.",
                        RulesetErrorSeverity.OK))

                similarUniques.isNotEmpty() -> {
                    val text =
                        "$name's unique \"${unique.text}\" looks like it may be a misspelling of:\n" +
                                similarUniques.joinToString("\n") { uniqueType ->
                                    val deprecationAnnotation =
                                        UniqueType::class.java.getField(uniqueType.name)
                                            .getAnnotation(Deprecated::class.java)
                                    if (deprecationAnnotation == null)
                                        "\"${uniqueType.text}\""
                                    else
                                        "\"${uniqueType.text}\" (Deprecated)"
                                }.prependIndent("\t")
                    listOf(RulesetError(text, RulesetErrorSeverity.OK))
                }
                RulesetCache.modCheckerAllowUntypedUniques -> emptyList()
                else -> listOf(RulesetError(
                        "$name's unique \"${unique.text}\" not found in Unciv's unique types.",
                        RulesetErrorSeverity.OK))
            }
        }

        val rulesetErrors = RulesetErrorList()

        val typeComplianceErrors = unique.type.getComplianceErrors(unique, this)
        for (complianceError in typeComplianceErrors) {
            if (complianceError.errorSeverity == severityToReport)
                 rulesetErrors += "$name's unique \"${unique.text}\" contains parameter ${complianceError.parameterName}," +
                        " which does not fit parameter type" +
                        " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !"
        }

        for (conditional in unique.conditionals) {
            if (conditional.type == null) {
                rulesetErrors.add(
                    "$name's unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                            " which is of an unknown type!",
                    RulesetErrorSeverity.Warning
                )
            } else {
                val conditionalComplianceErrors =
                    conditional.type.getComplianceErrors(conditional, this)
                for (complianceError in conditionalComplianceErrors) {
                    if (complianceError.errorSeverity == severityToReport)
                        rulesetErrors += "$name's unique \"${unique.text}\" contains the conditional \"${conditional.text}\"." +
                                " This contains the parameter ${complianceError.parameterName} which does not fit parameter type" +
                                " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !"
                }
            }
        }


        if (severityToReport != UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific)
        // If we don't filter these messages will be listed twice as this function is called twice on most objects
        // The tests are RulesetInvariant in nature, but RulesetSpecific is called for _all_ objects, invariant is not.
            return rulesetErrors


        val deprecationAnnotation = unique.getDeprecationAnnotation()
        if (deprecationAnnotation != null) {
            val replacementUniqueText = unique.getReplacementText(this)
            val deprecationText =
                "$name's unique \"${unique.text}\" is deprecated ${deprecationAnnotation.message}," +
                        if (deprecationAnnotation.replaceWith.expression != "") " replace with \"${replacementUniqueText}\"" else ""
            val severity = if (deprecationAnnotation.level == DeprecationLevel.WARNING)
                RulesetErrorSeverity.WarningOptionsOnly // Not user-visible
            else RulesetErrorSeverity.Warning // User visible

            rulesetErrors.add(deprecationText, severity)
        }

        if (unique.type.targetTypes.none { uniqueTarget.canAcceptUniqueTarget(it) }
            // the 'consume unit' conditional causes a triggerable unique to become a unit action
            && !(uniqueTarget==UniqueTarget.Unit
                    && unique.isTriggerable
                    && unique.conditionals.any { it.type == UniqueType.ConditionalConsumeUnit }))
            rulesetErrors.add(
                "$name's unique \"${unique.text}\" cannot be put on this type of object!",
                RulesetErrorSeverity.Warning
            )
        return rulesetErrors
    }


    class RulesetError(val text:String, val errorSeverityToReport: RulesetErrorSeverity)
    enum class RulesetErrorSeverity(val color: Color) {
        OK(Color.GREEN),
        WarningOptionsOnly(Color.YELLOW),
        Warning(Color.YELLOW),
        Error(Color.RED),
    }

    class RulesetErrorList : ArrayList<RulesetError>() {
        operator fun plusAssign(text: String) {
            add(text, RulesetErrorSeverity.Error)
        }

        fun add(text: String, errorSeverityToReport: RulesetErrorSeverity) {
            add(RulesetError(text, errorSeverityToReport))
        }

        fun getFinalSeverity(): RulesetErrorSeverity {
            if (isEmpty()) return RulesetErrorSeverity.OK
            return this.maxOf { it.errorSeverityToReport }
        }

        /** @return `true` means severe errors make the mod unplayable */
        fun isError() = getFinalSeverity() == RulesetErrorSeverity.Error
        /** @return `true` means problems exist, Options screen mod checker or unit tests for vanilla ruleset should complain */
        fun isNotOK() = getFinalSeverity() != RulesetErrorSeverity.OK
        /** @return `true` means at least errors impacting gameplay exist, new game screen should warn or block */
        fun isWarnUser() = getFinalSeverity() >= RulesetErrorSeverity.Warning

        fun getErrorText(unfiltered: Boolean = false) =
            filter { unfiltered || it.errorSeverityToReport != RulesetErrorSeverity.WarningOptionsOnly }
                .sortedByDescending { it.errorSeverityToReport }
                .joinToString("\n") { it.errorSeverityToReport.name + ": " + it.text }
    }

    fun checkModLinks(forOptionsPopup: Boolean = false): RulesetErrorList {
        val lines = RulesetErrorList()

        // Checks for all mods - only those that can succeed without loading a base ruleset
        // When not checking the entire ruleset, we can only really detect ruleset-invariant errors in uniques

        val rulesetInvariant = UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        val rulesetSpecific = UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific

        for (unit in units.values) {
            if (unit.upgradesTo == unit.name || (unit.upgradesTo != null && unit.upgradesTo == unit.replaces))
                lines += "${unit.name} upgrades to itself!"
            if (!unit.isCivilian() && unit.strength == 0)
                lines += "${unit.name} is a military unit but has no assigned strength!"
            if (unit.isRanged() && unit.rangedStrength == 0 && !unit.hasUnique(UniqueType.CannotAttack))
                lines += "${unit.name} is a ranged unit but has no assigned rangedStrength!"

            checkUniques(unit, lines, rulesetInvariant, forOptionsPopup)
        }

        for (tech in technologies.values) {
            for (otherTech in technologies.values) {
                if (tech != otherTech && otherTech.column == tech.column && otherTech.row == tech.row)
                    lines += "${tech.name} is in the same row as ${otherTech.name}!"
            }

            checkUniques(tech, lines, rulesetInvariant, forOptionsPopup)
        }

        for (building in buildings.values) {
            if (building.requiredTech == null && building.cost == 0 && !building.hasUnique(UniqueType.Unbuildable))
                lines += "${building.name} is buildable and therefore must either have an explicit cost or reference an existing tech!"

            checkUniques(building, lines, rulesetInvariant, forOptionsPopup)

        }

        for (nation in nations.values) {
            if (nation.cities.isEmpty() && !nation.isSpectator() && !nation.isBarbarian()) {
                lines += "${nation.name} can settle cities, but has no city names!"
            }

            checkUniques(nation, lines, rulesetInvariant, forOptionsPopup)
        }

        for (promotion in unitPromotions.values) {
            checkUniques(promotion, lines, rulesetInvariant, forOptionsPopup)
        }

        for (resource in tileResources.values) {
            checkUniques(resource, lines, rulesetInvariant, forOptionsPopup)
        }

        // Quit here when no base ruleset is loaded - references cannot be checked
        if (!modOptions.isBaseRuleset) return lines

        val vanillaRuleset = RulesetCache.getVanillaRuleset()  // for UnitTypes fallback


        if (units.values.none { it.hasUnique(UniqueType.FoundCity) })
           lines += "No city-founding units in ruleset!"

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
                    lines += "${unit.name} contains promotion $promotion which does not exist!"
            if (!unitTypes.containsKey(unit.unitType) && (unitTypes.isNotEmpty() || !vanillaRuleset.unitTypes.containsKey(unit.unitType)))
                lines += "${unit.name} is of type ${unit.unitType}, which does not exist!"
            for (unique in unit.getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit)) {
                val improvementName = unique.params[0]
                if (tileImprovements[improvementName]==null) continue // this will be caught in the checkUniques
                if ((tileImprovements[improvementName] as Stats).none() &&
                        unit.isCivilian() &&
                        !unit.isGreatPersonOfType("War")) {
                    lines.add("${unit.name} can place improvement $improvementName which has no stats, preventing unit automation!",
                        RulesetErrorSeverity.Warning)
                }
            }

            checkUniques(unit, lines, rulesetSpecific, forOptionsPopup)
        }

        for (building in buildings.values) {
            if (building.requiredTech != null && !technologies.containsKey(building.requiredTech!!))
                lines += "${building.name} requires tech ${building.requiredTech} which does not exist!"

            for (specialistName in building.specialistSlots.keys)
                if (!specialists.containsKey(specialistName))
                    lines += "${building.name} provides specialist $specialistName which does not exist!"
            for (resource in building.getResourceRequirements().keys)
                if (!tileResources.containsKey(resource))
                    lines += "${building.name} requires resource $resource which does not exist!"
            if (building.replaces != null && !buildings.containsKey(building.replaces!!))
                lines += "${building.name} replaces ${building.replaces} which does not exist!"
            if (building.requiredBuilding != null && !buildings.containsKey(building.requiredBuilding!!))
                lines += "${building.name} requires ${building.requiredBuilding} which does not exist!"
            if (building.requiredBuildingInAllCities != null)
                lines.add("${building.name} contains 'requiredBuildingInAllCities' - please convert to a \"" +
                        UniqueType.RequiresBuildingInAllCities.text.fillPlaceholders(building.requiredBuildingInAllCities!!)+"\" unique", RulesetErrorSeverity.Warning)
            checkUniques(building, lines, rulesetSpecific, forOptionsPopup)
        }

        for (resource in tileResources.values) {
            if (resource.revealedBy != null && !technologies.containsKey(resource.revealedBy!!))
                lines += "${resource.name} revealed by tech ${resource.revealedBy} which does not exist!"
            if (resource.improvement != null && !tileImprovements.containsKey(resource.improvement!!))
                lines += "${resource.name} improved by improvement ${resource.improvement} which does not exist!"
            for (improvement in resource.improvedBy)
                if (!tileImprovements.containsKey(improvement))
                    lines += "${resource.name} improved by improvement $improvement which does not exist!"
            for (terrain in resource.terrainsCanBeFoundOn)
                if (!terrains.containsKey(terrain))
                    lines += "${resource.name} can be found on terrain $terrain which does not exist!"
            checkUniques(resource, lines, rulesetSpecific, forOptionsPopup)
        }

        for (improvement in tileImprovements.values) {
            if (improvement.techRequired != null && !technologies.containsKey(improvement.techRequired!!))
                lines += "${improvement.name} requires tech ${improvement.techRequired} which does not exist!"
            for (terrain in improvement.terrainsCanBeBuiltOn)
                if (!terrains.containsKey(terrain) && terrain != "Land" && terrain != "Water")
                    lines += "${improvement.name} can be built on terrain $terrain which does not exist!"
            if (improvement.terrainsCanBeBuiltOn.isEmpty()
                && !improvement.hasUnique(UniqueType.CanOnlyImproveResource)
                && !improvement.hasUnique(UniqueType.Unbuildable)
                && !improvement.name.startsWith(Constants.remove)
                && improvement.name !in RoadStatus.values().map { it.removeAction }
                && improvement.name != Constants.cancelImprovementOrder
            ) {
                lines.add(
                    "${improvement.name} has an empty `terrainsCanBeBuiltOn`, isn't allowed to only improve resources and isn't unbuildable! Support for this will soon end. Either give this the unique \"Unbuildable\", \"Can only be built to improve a resource\" or add \"Land\", \"Water\" or any other value to `terrainsCanBeBuiltOn`.",
                    RulesetErrorSeverity.Warning
                )
            }
            for (unique in improvement.uniqueObjects) {
                if (unique.type == UniqueType.PillageYieldRandom || unique.type == UniqueType.PillageYieldFixed) {
                    if (!Stats.isStats(unique.params[0])) continue
                    val params = Stats.parse(unique.params[0])
                    if (params.values.any { it < 0 }) lines.add(
                        "${improvement.name} cannot have a negative value for a pillage yield!",
                        RulesetErrorSeverity.Error
                    )
                }
            }
            if ((improvement.hasUnique(UniqueType.PillageYieldRandom, StateForConditionals.IgnoreConditionals)
                            || improvement.hasUnique(UniqueType.PillageYieldFixed, StateForConditionals.IgnoreConditionals))
                    && improvement.hasUnique(UniqueType.Unpillagable, StateForConditionals.IgnoreConditionals)) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning
                )
            }
            checkUniques(improvement, lines, rulesetSpecific, forOptionsPopup)
        }

        if (terrains.values.none { it.type == TerrainType.Land && !it.impassable })
            lines += "No passable land terrains exist!"
        for (terrain in terrains.values) {
            for (baseTerrain in terrain.occursOn)
                if (!terrains.containsKey(baseTerrain))
                    lines += "${terrain.name} occurs on terrain $baseTerrain which does not exist!"
            checkUniques(terrain, lines, rulesetSpecific, forOptionsPopup)
        }

        val prereqsHashMap = HashMap<String,HashSet<String>>()
        for (tech in technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!technologies.containsKey(prereq))
                    lines += "${tech.name} requires tech $prereq which does not exist!"

                fun getPrereqTree(technologyName: String): Set<String> {
                    if (prereqsHashMap.containsKey(technologyName)) return prereqsHashMap[technologyName]!!
                    val technology = technologies[technologyName]
                        ?: return emptySet()
                    val techHashSet = HashSet<String>()
                    techHashSet += technology.prerequisites
                    for (prerequisite in technology.prerequisites)
                        techHashSet += getPrereqTree(prerequisite)
                    prereqsHashMap[technologyName] = techHashSet
                    return techHashSet
                }

                if (tech.prerequisites.asSequence().filterNot { it == prereq }
                        .any { getPrereqTree(it).contains(prereq) }){
                    lines.add("No need to add $prereq as a prerequisite of ${tech.name} - it is already implicit from the other prerequisites!",
                        RulesetErrorSeverity.Warning)
                }
            }
            if (tech.era() !in eras)
                lines += "Unknown era ${tech.era()} referenced in column of tech ${tech.name}"
            checkUniques(tech, lines, rulesetSpecific, forOptionsPopup)
        }

        if (eras.isEmpty()) {
            lines += "Eras file is empty! This will likely lead to crashes. Ask the mod maker to update this mod!"
        }

        val allDifficultiesStartingUnits = hashSetOf<String>()
        for (difficulty in difficulties.values){
            allDifficultiesStartingUnits.addAll(difficulty.aiCityStateBonusStartingUnits)
            allDifficultiesStartingUnits.addAll(difficulty.aiMajorCivBonusStartingUnits)
            allDifficultiesStartingUnits.addAll(difficulty.playerBonusStartingUnits)
        }

        val rulesetHasCityStates = nations.values.any { it.isCityState() }
        for (era in eras.values) {
            for (wonder in era.startingObsoleteWonders)
                if (wonder !in buildings)
                    lines += "Nonexistent wonder $wonder obsoleted when starting in ${era.name}!"
            for (building in era.settlerBuildings)
                if (building !in buildings)
                    lines += "Nonexistent building $building built by settlers when starting in ${era.name}"
            // todo the whole 'starting unit' thing needs to be redone, there's no reason we can't have a single list containing all the starting units.
            if (era.startingSettlerUnit !in units && (era.startingSettlerUnit!=Constants.settler || units.values.none { it.hasUnique(UniqueType.FoundCity) }))
                lines += "Nonexistent unit ${era.startingSettlerUnit} marked as starting unit when starting in ${era.name}"
            if (era.startingWorkerCount!=0 && era.startingWorkerUnit !in units)
                lines += "Nonexistent unit ${era.startingWorkerUnit} marked as starting unit when starting in ${era.name}"

            if ((era.startingMilitaryUnitCount !=0 || allDifficultiesStartingUnits.contains(Constants.eraSpecificUnit)) && era.startingMilitaryUnit !in units)
                lines += "Nonexistent unit ${era.startingMilitaryUnit} marked as starting unit when starting in ${era.name}"
            if (era.researchAgreementCost < 0 || era.startingSettlerCount < 0 || era.startingWorkerCount < 0 || era.startingMilitaryUnitCount < 0 || era.startingGold < 0 || era.startingCulture < 0)
                lines += "Unexpected negative number found while parsing era ${era.name}"
            if (era.settlerPopulation <= 0)
                lines += "Population in cities from settlers must be strictly positive! Found value ${era.settlerPopulation} for era ${era.name}"

            if (era.allyBonus.isEmpty() && rulesetHasCityStates)
                lines.add("No ally bonus defined for era ${era.name}", RulesetErrorSeverity.Warning)
            if (era.friendBonus.isEmpty() && rulesetHasCityStates)
                lines.add("No friend bonus defined for era ${era.name}", RulesetErrorSeverity.Warning)


            checkUniques(era, lines, rulesetSpecific, forOptionsPopup)
        }

        for (speed in speeds.values) {
            if (speed.modifier < 0f)
                lines += "Negative speed modifier for game speed ${speed.name}"
            if (speed.yearsPerTurn.isEmpty())
                lines += "Empty turn increment list for game speed ${speed.name}"
        }

        for (belief in beliefs.values) {
            checkUniques(belief, lines, rulesetSpecific, forOptionsPopup)
        }

        for (nation in nations.values) {
            checkUniques(nation, lines, rulesetSpecific, forOptionsPopup)
            if (nation.favoredReligion != null && nation.favoredReligion !in religions)
                lines += "${nation.name} has ${nation.favoredReligion} as their favored religion, which does not exist!"
        }

        for (policy in policies.values) {
            if (policy.requires != null)
                for (prereq in policy.requires!!)
                    if (!policies.containsKey(prereq))
                        lines += "${policy.name} requires policy $prereq which does not exist!"
            checkUniques(policy, lines, rulesetSpecific, forOptionsPopup)
        }

        for (branch in policyBranches.values)
            if (branch.era !in eras)
                lines += "${branch.name} requires era ${branch.era} which does not exist!"


        for (policy in policyBranches.values.flatMap { it.policies + it })
            if (policy != policies[policy.name])
                lines += "More than one policy with the name ${policy.name} exists!"

        for (reward in ruinRewards.values) {
            for (difficulty in reward.excludedDifficulties)
                if (!difficulties.containsKey(difficulty))
                    lines += "${reward.name} references difficulty ${difficulty}, which does not exist!"
            checkUniques(reward, lines, rulesetSpecific, forOptionsPopup)
        }

        for (promotion in unitPromotions.values) {
            // These are warning as of 3.17.5 to not break existing mods and give them time to correct, should be upgraded to error in the future
            for (prereq in promotion.prerequisites)
                if (!unitPromotions.containsKey(prereq))
                    lines.add("${promotion.name} requires promotion $prereq which does not exist!", RulesetErrorSeverity.Warning)
            for (unitType in promotion.unitTypes)
                if (!unitTypes.containsKey(unitType) && (unitTypes.isNotEmpty() || !vanillaRuleset.unitTypes.containsKey(unitType)))
                    lines.add("${promotion.name} references unit type $unitType, which does not exist!", RulesetErrorSeverity.Warning)
            checkUniques(promotion, lines, rulesetSpecific, forOptionsPopup)
        }

        for (unitType in unitTypes.values) {
            checkUniques(unitType, lines, rulesetSpecific, forOptionsPopup)
        }

        for (victoryType in victories.values) {
            for (requiredUnit in victoryType.requiredSpaceshipParts)
                if (!units.contains(requiredUnit))
                    lines.add("Victory type ${victoryType.name} requires adding the non-existant unit $requiredUnit to the capital to win!", RulesetErrorSeverity.Warning)
            for (milestone in victoryType.milestoneObjects)
                if (milestone.type == null)
                    lines.add("Victory type ${victoryType.name} has milestone ${milestone.uniqueDescription} that is of an unknown type!", RulesetErrorSeverity.Error)
            for (victory in victories.values)
                if (victory.name != victoryType.name && victory.milestones == victoryType.milestones)
                    lines.add("Victory types ${victoryType.name} and ${victory.name} have the same requirements!", RulesetErrorSeverity.Warning)
        }

        for (difficulty in difficulties.values) {
            for (unitName in difficulty.aiCityStateBonusStartingUnits + difficulty.aiMajorCivBonusStartingUnits + difficulty.playerBonusStartingUnits)
                if (unitName != Constants.eraSpecificUnit && !units.containsKey(unitName))
                    lines += "Difficulty ${difficulty.name} contains starting unit $unitName which does not exist!"
        }

        @Suppress("DEPRECATION")
        if (modOptions.maxXPfromBarbarians != 30) {
            lines.add("maxXPfromBarbarians is moved to the constants object, instead use: \nconstants: {\n    maxXPfromBarbarians: ${modOptions.maxXPfromBarbarians},\n}", RulesetErrorSeverity.Warning)
        }

        return lines
    }
}

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache : HashMap<String,Ruleset>() {
    /** Whether mod checking allows untyped uniques - set to `false` once all vanilla uniques are converted! */
    var modCheckerAllowUntypedUniques = true

    /** Similarity below which an untyped unique can be considered a potential misspelling.
     * Roughly corresponds to the fraction of the Unique placeholder text that can be different/misspelled, but with some extra room for [getRelativeTextDistance] idiosyncrasies. */
    var uniqueMisspellingThreshold = 0.15 // Tweak as needed. Simple misspellings seem to be around 0.025, so would mostly be caught by 0.05. IMO 0.1 would be good, but raising to 0.15 also seemed to catch what may be an outdated Unique.


    /** Returns error lines from loading the rulesets, so we can display the errors to users */
    fun loadRulesets(consoleMode: Boolean = false, noMods: Boolean = false) :List<String> {
        clear()
        for (ruleset in BaseRuleset.values()) {
            val fileName = "jsons/${ruleset.fullName}"
            val fileHandle =
                if (consoleMode) FileHandle(fileName)
                else Gdx.files.internal(fileName)
            this[ruleset.fullName] = Ruleset().apply {
                load(fileHandle)
                name = ruleset.fullName
            }
        }

        if (noMods) return listOf()

        val modsHandles = if (consoleMode) FileHandle("mods").list()
        else Gdx.files.local("mods").list()

        val errorLines = ArrayList<String>()
        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            if (!modFolder.isDirectory) continue
            try {
                val modRuleset = Ruleset()
                modRuleset.load(modFolder.child("jsons"))
                modRuleset.name = modFolder.name()
                modRuleset.folderLocation = modFolder
                this[modRuleset.name] = modRuleset
                debug("Mod loaded successfully: %s", modRuleset.name)
                if (Log.shouldLog()) {
                    val modLinksErrors = modRuleset.checkModLinks()
                    if (modLinksErrors.any()) {
                        debug("checkModLinks errors: %s", modLinksErrors.getErrorText())
                    }
                }
            } catch (ex: Exception) {
                errorLines += "Exception loading mod '${modFolder.name()}':"
                errorLines += "  ${ex.localizedMessage}"
                errorLines += "  ${ex.cause?.localizedMessage}"
            }
        }
        if (Log.shouldLog()) for (line in errorLines) debug(line)
        return errorLines
    }


    fun getVanillaRuleset() = this[BaseRuleset.Civ_V_Vanilla.fullName]!!.clone() // safeguard, so no-one edits the base ruleset by mistake

    fun getSortedBaseRulesets(): List<String> {
        val baseRulesets = values
            .filter { it.modOptions.isBaseRuleset }
            .map { it.name }
            .distinct()
        if (baseRulesets.size < 2) return baseRulesets

        // We sort the base rulesets such that the ones unciv provides are on the top,
        // and the rest is alphabetically ordered.
        return baseRulesets.sortedWith(
            compareBy(
                { ruleset ->
                    BaseRuleset.values()
                        .firstOrNull { br -> br.fullName == ruleset }?.ordinal
                        ?: BaseRuleset.values().size
                },
                { it }
            )
        )
    }

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: MapParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: GameParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /**
     * Creates a combined [Ruleset] from a list of mods.
     * If no baseRuleset is passed in [optionalBaseRuleset] (or a non-existing one), then the vanilla Ruleset is included automatically.
     * Any mods in the [mods] parameter marked as base ruleset (or not loaded in [RulesetCache]) are ignored.
     */
    fun getComplexRuleset(mods: LinkedHashSet<String>, optionalBaseRuleset: String? = null): Ruleset {
        val baseRuleset =
                if (containsKey(optionalBaseRuleset) && this[optionalBaseRuleset]!!.modOptions.isBaseRuleset)
                    this[optionalBaseRuleset]!!
                else getVanillaRuleset()

        val loadedMods = mods.asSequence()
            .filter { containsKey(it) }
            .map { this[it]!! }
            .filter { !it.modOptions.isBaseRuleset }

        return getComplexRuleset(baseRuleset, loadedMods.asIterable())
    }

    /**
     * Creates a combined [Ruleset] from [baseRuleset] and [extensionRulesets] which must only contain non-base rulesets.
     */
    fun getComplexRuleset(baseRuleset: Ruleset, extensionRulesets: Iterable<Ruleset>): Ruleset {
        val newRuleset = Ruleset()

        val loadedMods = extensionRulesets.asSequence() + baseRuleset

        for (mod in loadedMods.sortedByDescending { it.modOptions.isBaseRuleset }) {
            if (mod.modOptions.isBaseRuleset) {
                // This is so we don't keep using the base ruleset's unqiues *by reference* and add to in ad infinitum
                newRuleset.modOptions.uniques = ArrayList()
                newRuleset.modOptions.isBaseRuleset = true
            }
            newRuleset.add(mod)
            newRuleset.mods += mod.name
        }
        newRuleset.updateBuildingCosts() // only after we've added all the mods can we calculate the building costs

        return newRuleset
    }

    /**
     * Runs [Ruleset.checkModLinks] on a temporary [combined Ruleset][getComplexRuleset] for a list of [mods]
     */
    fun checkCombinedModLinks(
        mods: LinkedHashSet<String>,
        baseRuleset: String? = null,
        forOptionsPopup: Boolean = false
    ): Ruleset.RulesetErrorList {
        return try {
            val newRuleset = getComplexRuleset(mods, baseRuleset)
            newRuleset.modOptions.isBaseRuleset = true // This is so the checkModLinks finds all connections
            newRuleset.checkModLinks(forOptionsPopup)
        } catch (ex: UncivShowableException) {
            // This happens if a building is dependent on a tech not in the base ruleset
            //  because newRuleset.updateBuildingCosts() in getComplexRuleset() throws an error
            Ruleset.RulesetErrorList()
                .apply { add(ex.message, Ruleset.RulesetErrorSeverity.Error) }
        }
    }

}

class Specialist: NamedStats() {
    var color = ArrayList<Int>()
    val colorObject by lazy { colorFromRGB(color) }
    var greatPersonPoints = Counter<String>()
}

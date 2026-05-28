package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.stats.Stat
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.devconsole.CliInput.Companion.findCliInput

internal class ConsoleCivCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "addstat" to ConsoleAction("civ addstat <stat> <amount> [civ]") { console, params ->
            val stat = params[0].toStat()
            if (stat !in Stat.statsWithCivWideField)
                throw ConsoleErrorException("$stat is not civ-wide")
            val amount = params[1].toInt()
            val civ = console.getCivByNameOrSelected(params.getOrNull(2))

            civ.addStat(stat, amount)
            DevConsoleResponse.OK
        },

        "setplayertype" to ConsoleAction("civ setplayertype <civName> <ai/human>") { console, params ->
            val civ = console.getCivByName(params[0])
            if (!civ.isMajorCiv())
                throw ConsoleErrorException("Can only change player type for major civs")
            civ.playerType = params[1].enumValue<PlayerType>()
            DevConsoleResponse.OK
        },

        "revealmap" to ConsoleAction("civ revealmap [civName]") { console, params ->
            val civ = console.getCivByNameOrSelected(params.getOrNull(0))
            civ.gameInfo.tileMap.values.asSequence()
                .forEach { it.setExplored(civ, true) }
            DevConsoleResponse.OK
        },

        "activatetrigger" to ConsoleTriggerAction("civ"),

        "addpolicy" to ConsoleAction("civ addpolicy <civName> <policyName>") { console, params ->
            val civ = console.getCivByName(params[0])
            val policy = console.findCliInput<Policy>(params[1]) // yes this also finds PolicyBranch instances
                ?: throw ConsoleErrorException("Unrecognized policy")
            if (civ.policies.isAdopted(policy.name))
                DevConsoleResponse.hint("${civ.civID} already has adopted ${policy.name}")
            else {
                civ.policies.freePolicies++
                civ.policies.adopt(policy)
                DevConsoleResponse.OK
            }
        },

        "removepolicy" to ConsoleAction("civ removepolicy <civName> <policyName>") { console, params ->
            val civ = console.getCivByName(params[0])
            val policy = console.findCliInput<Policy>(params[1])
                ?: throw ConsoleErrorException("Unrecognized policy")
            if (!civ.policies.isAdopted(policy.name))
                DevConsoleResponse.hint("${civ.civID} does not have ${policy.name}")
            else {
                civ.policies.removePolicy(policy, assumeWasFree = true) // See UniqueType.OneTimeRemovePolicy
                DevConsoleResponse.OK
            }
        },

        "addtech" to ConsoleAction("civ addtechnology <civName> <techName>") { console, params ->
            val civ = console.getCivByName(params[0])
            val tech = console.findCliInput<Technology>(params[1])
                ?: throw ConsoleErrorException("Unrecognized technology")
            if (civ.tech.isResearched(tech.name))
                DevConsoleResponse.hint("${civ.civID} already has researched ${tech.name}")
            else {
                civ.tech.addTechnology(tech.name, false)
                DevConsoleResponse.OK
            }
        },

        "removetech" to ConsoleAction("civ removetechnology <civName> <techName>") { console, params ->
            val civ = console.getCivByName(params[0])
            val tech = console.findCliInput<Technology>(params[1])
                ?: throw ConsoleErrorException("Unrecognized technology")
            if (!civ.tech.isResearched(tech.name))
                DevConsoleResponse.hint("${civ.civID} does not have ${tech.name}")
            else {
                civ.tech.techsResearched.removeAll { it == tech.name } // Can have multiple for researchable techs
                DevConsoleResponse.OK
            }
        },

        "checkcountable" to ConsoleAction("civ checkcountable <countable> [civName]") { console, params ->
            val civ = console.getCivByNameOrSelected(params.getOrNull(1))
            val amount = Countables.getCountableAmount(params[0].originalUnquoted(), GameContext(civ))
            DevConsoleResponse.hint(amount?.toString() ?: "Invalid countable")
        },

        /** This is meant for Scenario building. It adds any Civ in form of one Settler which isn't already in the game. */
        "add" to ConsoleAction("civ add <newCivName>") { console, params ->
            val gameInfo = console.gameInfo
            val ruleset = gameInfo.ruleset
            val civilizations = gameInfo.civilizations
            val nation = params[0].find(ruleset.nations.values.filterNot { civilizations.any { civ -> civ.nation == it } })
            val selectedTile = console.getSelectedTile()
            val settlerLikeUnits = ruleset.units.values.filter {
                (it.uniqueTo == null || it.uniqueTo == nation.name) && it.isCityFounder()
            }
            val baseUnit = settlerLikeUnits.firstOrNull() // Not random - getting a Conquistador might be unfair
                ?: throw ConsoleErrorException("No allowed Settler units in the ruleset")
            val civ = Civilization(nation)
            if (nation.isCityState) {
                val usedMajorCivs = civilizations.asSequence().filter { it.isMajorCiv() }.map { it.nation }
                if (!civ.cityStateFunctions.initCityState(ruleset, gameInfo.gameParameters.startingEra, usedMajorCivs))
                    throw ConsoleErrorException("City-state not allowed in this game")
            }
            civ.gameInfo = gameInfo
            civ.cache.updateState()
            civ.setTransients()
            civilizations.add(civ)
            val location = civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            civ.cache.updateViewableTiles()
            if (location == null) {
                civilizations.remove(civ)
                DevConsoleResponse.error("No place for new Settler found")
            }
            else DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("civ remove <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
            if (civ.civID == console.gameInfo.currentPlayer)
                throw ConsoleErrorException("Suicide is not yet supported")
            val msg = "${civ.civName} has ${civ.cities.size} cities and ${civ.units.getCivUnitsSize()} units.\n\nDo you really want to annihilate the poor buggers?"
            fun annihilate() {
                // kill units. Using destroy() would be slower and might have unwelcome side effects.
                for (unit in civ.units.getCivUnits()) {
                    civ.units.removeUnit(unit)
                    unit.removeFromTile()
                }
                // Relinquish territory
                for (city in civ.cities) {
                    for (tile in city.getTiles()) {
                        tile.setOwningCity(null)
                    }
                    city.getCenterTile().removeImprovement()
                }
                // Remove tile references outside territory
                for (tile in console.gameInfo.tileMap.tileList)
                    if (tile.getRoadOwner() == civ) tile.removeRoadOwner()
                // Remove the civ
                val civilizations = console.gameInfo.civilizations
                civilizations.remove(civ)
                (console.gameInfo.civMap as LinkedHashMap<String, Civilization>).remove(civ.civID)
                // Make othe civs forget the victim
                for (otherCiv in civilizations) {
                    otherCiv.diplomacy.remove(civ.civID)
                    if (otherCiv.isCityState)
                        otherCiv.questManager.removeQuestsFor(civ)
                }
                console.screen.shouldUpdate = true
            }
            if (civ.isDefeated())
                annihilate()
            else
                ConfirmPopup(console.screen, msg, "Yes", action = ::annihilate).open(true)
            DevConsoleResponse.OK
        }
    )
}

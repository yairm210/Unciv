package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.stats.Stat
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
                DevConsoleResponse.hint("${civ.civName} already has adopted ${policy.name}")
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
                DevConsoleResponse.hint("${civ.civName} does not have ${policy.name}")
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
                DevConsoleResponse.hint("${civ.civName} already has researched ${tech.name}")
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
                DevConsoleResponse.hint("${civ.civName} does not have ${tech.name}")
            else {
                civ.tech.techsResearched.removeAll { it == tech.name } // Can have multiple for researchable techs
                DevConsoleResponse.OK
            }
        },
    )
}

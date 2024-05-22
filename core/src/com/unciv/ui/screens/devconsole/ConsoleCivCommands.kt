package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.stats.Stat

class ConsoleCivCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "addstat" to ConsoleAction("civ addstat <stat> <amount> [civ]") { console, params ->
            val stat = Stat.safeValueOf(params[0].replaceFirstChar(Char::titlecase))
                ?: throw ConsoleErrorException("\"${params[0]}\" is not an acceptable Stat")
            if (stat !in Stat.statsWithCivWideField)
                throw ConsoleErrorException("$stat is not civ-wide")

            val amount = console.getInt(params[1])

            val civ = if (params.size == 2) console.screen.selectedCiv
            else console.getCivByName(params[2])

            civ.addStat(stat, amount)
            DevConsoleResponse.OK
        },

        "setplayertype" to ConsoleAction("civ setplayertype <civName> <ai/human>") { console, params ->
            val civ = console.getCivByName(params[0])
            val playerType = PlayerType.values().firstOrNull { it.name.lowercase() == params[1].lowercase() }
                ?: throw ConsoleErrorException("Invalid player type, valid options are 'ai' or 'human'")
            civ.playerType = playerType
            DevConsoleResponse.OK
        },

        "revealmap" to ConsoleAction("civ revealmap <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
            civ.gameInfo.tileMap.values.asSequence()
                .forEach { it.setExplored(civ, true) }
            DevConsoleResponse.OK
        },

        "activatetrigger" to ConsoleAction("civ activatetrigger <civName> <\"trigger\">") { console, params ->
            val civ = console.getCivByName(params[0])
            val unique = Unique(params[1])
            if (unique.type == null) throw ConsoleErrorException("Unrecognized trigger")
            val tile = console.screen.mapHolder.selectedTile
            val city = tile?.getCity()
            UniqueTriggerActivation.triggerUnique(unique, civ, city, tile = tile)
            DevConsoleResponse.OK
        },

        "addpolicy" to ConsoleAction("civ addpolicy <civName> <policyName>")  { console, params ->
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

        "removepolicy" to ConsoleAction("civ removepolicy <civName> <policyName>")  { console, params ->
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
    )
}

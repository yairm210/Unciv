package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.diplomacy.DeclareWarReason
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.WarType

internal class ConsoleDiplomacyCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "list" to ConsoleAction("diplomacy list <civName> <civName>") { console, params ->
            val diplo = getDiplomacy(console, params, noMeet = true) ?:
                return@ConsoleAction DevConsoleResponse.hint("not met")
            val message = buildString {
                appendLine("Relation: ${diplo.relationshipLevel()}")
                if (diplo.civInfo.isCityState) appendLine("Influence: ${diplo.influence}")
                for ((modifier, value) in diplo.diplomaticModifiers)
                    appendLine("$modifier = $value")
                for ((flag, count) in diplo.flagsCountdown)
                    appendLine("$flag: $count turns")
            }
            DevConsoleResponse.hint(message)
        },
        "meet" to ConsoleAction("diplomacy meet <civName> <civName>") { console, params ->
            getDiplomacy(console, params)
            DevConsoleResponse.OK
        },
        "declare-war" to ConsoleAction("diplomacy declare-war <civName> <civName>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            diplo.declareWar(DeclareWarReason(WarType.DirectWar)) // to lazy to allow parameters for this
            DevConsoleResponse.OK
        },
        "make-peace" to ConsoleAction("diplomacy make-peace <civName> <civName>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            diplo.makePeace()
            DevConsoleResponse.OK
        },
        "setflag" to ConsoleAction("diplomacy setflag <civName> <civName> <diplomacyFlag> <amount>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            val flag = params[2].enumValue<DiplomacyFlags>()
            val amount = params[3].toInt()
            diplo.setFlag(flag, amount)
            DevConsoleResponse.OK
        },
        "removeflag" to ConsoleAction("diplomacy removeflag <civName> <civName> <diplomacyFlag>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            val flag = params[2].enumValue<DiplomacyFlags>()
            diplo.removeFlag(flag)
            DevConsoleResponse.OK
        },
        "addmodifier" to ConsoleAction("diplomacy addmodifier <civName> <civName> <diplomaticModifier> <amount>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            val modifier = params[2].enumValue<DiplomaticModifiers>()
            val amount = params[3].toFloat()
            diplo.addModifier(modifier, amount)
            DevConsoleResponse.OK
        },
        "removemodifier" to ConsoleAction("diplomacy removemodifier <civName> <civName> <diplomaticModifier>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            val modifier = params[2].enumValue<DiplomaticModifiers>()
            diplo.removeModifier(modifier)
            DevConsoleResponse.OK
        },
        "setinfluence" to ConsoleAction("diplomacy setinfluence <civName> <civName> <amount>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            if (!diplo.civInfo.isCityState || !diplo.otherCiv.isMajorCiv())
                throw ConsoleErrorException("first civ must be the city-state, second a major civ")
            val amount = params[2].toFloat()
            diplo.setInfluence(amount)
            DevConsoleResponse.OK
        },
        "denounce" to ConsoleAction("diplomacy denounce <civName> <civName>") { console, params ->
            val diplo = getDiplomacy(console, params)!!
            diplo.denounce()
            DevConsoleResponse.OK
        },
    )

    private fun getDiplomacy(console: DevConsolePopup, params: List<CliInput>, noMeet: Boolean = false): DiplomacyManager? {
        if (params.size < 2)
            throw ConsoleErrorException("command needs two civ names")
        val civ1 = console.getCivByName(params[0])
        val civ2 = console.getCivByName(params[1])
        if (civ1.isDefeated() || civ2.isDefeated())
            throw ConsoleErrorException("both civs must be alive")
        return if (noMeet) civ1.getDiplomacyManager(civ2)
            else civ1.getDiplomacyManagerOrMeet(civ2)
    }
}

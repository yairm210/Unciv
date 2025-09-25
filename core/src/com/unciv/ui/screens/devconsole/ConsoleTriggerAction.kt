package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderText

/**
 *  Container for console access to [UniqueTriggerActivation.triggerUnique].
 *
 *  @param topLevelCommand For the beginning of the format string. Also used to control syntax checks.
 */
internal class ConsoleTriggerAction(
    topLevelCommand: String
) : ConsoleAction("$topLevelCommand activatetrigger <triggeredUnique|triggeredUniqueTemplate> [uniqueParam]...", getAction(topLevelCommand)) {
    companion object {
        private fun getAction(topLevelCommand: String): (DevConsolePopup, List<CliInput>) -> DevConsoleResponse {
            return { console: DevConsolePopup, params: List<CliInput> ->
                val paramStack = ArrayDeque(params)
                // The city and tile blocks could be written shorter without try-catch, but this way the error message is easily kept in one place
                val city = try {
                    console.getSelectedCity()
                } catch (ex: ConsoleErrorException) {
                    if (topLevelCommand == "city") throw ex
                    null
                }
                val unit = try {
                    console.getSelectedUnit()
                } catch (ex: ConsoleErrorException) {
                    if (topLevelCommand == "unit") throw ex
                    null
                }
                val tile = try {
                    console.getSelectedTile()
                } catch (ex: ConsoleErrorException) {
                    if (topLevelCommand == "tile") throw ex
                    null
                }
                val civ = getCiv(console, topLevelCommand, paramStack) ?: city?.civ ?: unit?.civ ?: tile?.getOwner()
                    ?: throw ConsoleErrorException("A trigger command needs a Civilization from some source")
                val unique = getUnique(console, paramStack)
                if (UniqueTriggerActivation.triggerUnique(unique, civ, city, unit, tile, null, "due to cheating"))
                    DevConsoleResponse.OK
                else DevConsoleResponse.error("The `triggerUnique` call failed")
            }
        }

        private fun getCiv(console: DevConsolePopup, topLevelCommand: String, paramStack: ArrayDeque<CliInput>): Civilization? {
            if (topLevelCommand != "civ") return null
            // Came from `civ activatetrigger`: We want a civ, but on the command line it should be an optional parameter, defaulting to WorldScreen selected
            val name = paramStack.firstOrNull() ?: return null // not having any param needs a throw, but let the unique getter handle that
            val civ = console.getCivByNameOrNull(name) ?: return console.screen.selectedCiv
            // name was good - remove from deque
            paramStack.removeFirst()
            return civ
        }

        private fun getUnique(console: DevConsolePopup, paramStack: ArrayDeque<CliInput>): Unique {
            var uniqueText = paramStack.removeFirstOrNull()?.toMethod(CliInput.Method.Quoted)
                ?: throw ConsoleErrorException("Parameter triggeredUnique missing")
            val uniqueType = getUniqueType(uniqueText)
            if (paramStack.isNotEmpty() && uniqueText.equals(uniqueType.text)) {
                // Simplification: You either specify a fully formatted Unique as one parameter or the default text and a full set of replacements
                val params = paramStack.map { it.originalUnquoted() }.toTypedArray()
                uniqueText = CliInput(uniqueType.placeholderText.fillPlaceholders(*params), CliInput.Method.Quoted)
            }
            val unique = Unique(uniqueText.content, UniqueTarget.Triggerable, "DevConsole")
            val validator = UniqueValidator(console.gameInfo.ruleset)
            val errors = validator.checkUnique(unique, false, ConsoleRulesetObject(), true)
            if (errors.isNotOK())
                throw ConsoleErrorException(errors.getErrorText(true))
            return unique
        }

        private fun getUniqueType(param: CliInput): UniqueType {
            val filterText = CliInput(param.content.getPlaceholderText(), param.method)
            val uniqueTypes = UniqueType.entries.asSequence()
                .filter { CliInput(it.placeholderText, param.method) == filterText }
                .take(4).toList()
            if (uniqueTypes.isEmpty())
                throw ConsoleErrorException("`$param` not found in UniqueTypes")
            if (uniqueTypes.size > 1)
                throw ConsoleErrorException("`$param` has ambiguous UniqueType: ${uniqueTypes.joinToString(limit = 3) { it.text }}?")
            val uniqueType = uniqueTypes.first()
            if (uniqueType.canAcceptUniqueTarget(UniqueTarget.Triggerable))
                return uniqueType
            throw ConsoleErrorException("`$param` is not a Triggerable")
        }

        private class ConsoleRulesetObject : RulesetObject() {
            override var name = "DevConsole"
            override fun getUniqueTarget() = UniqueTarget.Triggerable
            override fun makeLink() = ""
        }
    }
}

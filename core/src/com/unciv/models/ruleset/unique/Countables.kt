package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import kotlin.math.pow

object Countables {

    fun getCountableAmount(
        countable: String,
        stateForConditionals: StateForConditionals,
        isFromUniqueParameterType: Boolean = false
    ): Int? {
        if (isExpression(countable)) {
            return evaluateExpression(countable, stateForConditionals, isFromUniqueParameterType)
        }
        return simpleCountableAmount(countable, stateForConditionals, isFromUniqueParameterType)
    }

    private fun simpleCountableAmount(
        countable: String,
        stateForConditionals: StateForConditionals,
        isFromUniqueParameterType: Boolean = false
    ): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)
        if (relevantStat != null) {
            return stateForConditionals.getStatAmount(relevantStat)
        }

        val gameInfo = stateForConditionals.gameInfo ?: return if (isFromUniqueParameterType) 0 else null

        if (countable == "turns") return gameInfo.turns
        if (countable == "year") return gameInfo.getYear(gameInfo.turns)

        val civInfo = stateForConditionals.relevantCiv ?: return if (isFromUniqueParameterType) 0 else null

        if (countable == "Cities") return civInfo.cities.size

        val placeholderParameters = countable.getPlaceholderParameters()
        if (countable.equalsPlaceholderText("[] Cities")) {
            val filter = placeholderParameters[0]
            return civInfo.cities.count { it.matchesFilter(filter) }
        }

        if (countable == "Units") return civInfo.units.getCivUnitsSize()
        if (countable.equalsPlaceholderText("[] Units")) {
            val filter = placeholderParameters[0]
            return civInfo.units.getCivUnits().count { it.matchesFilter(filter) }
        }

        if (countable.equalsPlaceholderText("[] Buildings")) {
            val filter = placeholderParameters[0]
            var totalBuildings = 0
            for (city in civInfo.cities) {
                val builtBuildings = city.cityConstructions.getBuiltBuildings()
                totalBuildings += builtBuildings.count { it.matchesFilter(filter) }
            }
            return totalBuildings
        }

        if (countable.equalsPlaceholderText("Remaining [] Civilizations")) {
            val filter = placeholderParameters[0]
            var remainingCivs = 0
            for (civ in gameInfo.civilizations) {
                if (!civ.isDefeated() && civ.matchesFilter(filter)) {
                    remainingCivs++
                }
            }
            return remainingCivs
        }

        if (countable.equalsPlaceholderText("Completed Policy branches")) {
            return civInfo.getCompletedPolicyBranchesCount()
        }

        if (countable.equalsPlaceholderText("Owned [] Tiles")) {
            val filter = placeholderParameters[0]
            var totalTiles = 0
            for (city in civInfo.cities) {
                totalTiles += city.getTiles().count { it.matchesFilter(filter) }
            }
            return totalTiles
        }

        if (gameInfo.ruleset.tileResources.containsKey(countable)) {
            return stateForConditionals.getResourceAmount(countable)
        }

        return if (isFromUniqueParameterType) 0 else null
    }

    fun evaluateExpression(
        expression: String,
        stateForConditionals: StateForConditionals,
        isFromUniqueParameterType: Boolean = false
    ): Int? {
        val tokens = parseExpression(expression)
        if (tokens.isEmpty()) return null

        return calculateExpression(tokens, stateForConditionals, isFromUniqueParameterType)
    }

    private fun parseExpression(expression: String): List<String> {
        val regex = Regex(
            "(\\[[^\\[\\]]*(?:\\[[^\\[\\]]*][^\\[\\]]*)*])|([+\\-*/%^()])|(\\d+)"
        )
        val matches = regex.findAll(expression)
        val tokens = mutableListOf<String>()
        for (match in matches) {
            val token = match.groups.asSequence()
                .filter { it != null && it.value.isNotEmpty() }
                .firstOrNull()?.value
                ?.trim()
            if (!token.isNullOrEmpty()) {
                tokens.add(token)
            }
        }
        return tokens
    }

    private fun calculateExpression(
        tokens: List<String>,
        stateForConditionals: StateForConditionals,
        isFromUniqueParameterType: Boolean
    ): Int? {
        val outputQueue = mutableListOf<String>()
        val operatorStack = mutableListOf<String>()

        for (token in tokens) {
            if (isNumber(token)) {
                outputQueue.add(token)
            } else if (token.startsWith("[") && token.endsWith("]")) {
                val innerToken = token.substring(1, token.length - 1)
                val value = simpleCountableAmount(innerToken, stateForConditionals, isFromUniqueParameterType) ?: return null
                outputQueue.add(value.toString())
            } else if (token == "(") {
                operatorStack.add(token)
            } else if (token == ")") {
                while (operatorStack.isNotEmpty() && operatorStack.last() != "(") {
                    outputQueue.add(operatorStack.removeLast())
                }
                if (operatorStack.isNotEmpty()) {
                    operatorStack.removeLast()
                }
            } else if (isOperator(token)) {
                while (operatorStack.isNotEmpty() && operatorStack.last() != "(" &&
                    getPrecedence(token) <= getPrecedence(operatorStack.last())
                ) {
                    outputQueue.add(operatorStack.removeLast())
                }
                operatorStack.add(token)
            }
        }

        while (operatorStack.isNotEmpty()) {
            outputQueue.add(operatorStack.removeLast())
        }

        val evaluationStack = mutableListOf<Any>()

        for (token in outputQueue) {
            if (isNumber(token)) {
                evaluationStack.add(token.toInt())
            } else if (isOperator(token)) {
                if (evaluationStack.size < 2) return null
                val b = evaluationStack.removeLast() as Int
                val a = evaluationStack.removeLast() as Int
                val result = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b == 0) return 0 else a / b
                    "%" -> if (b == 0) return 0 else a % b
                    "^" -> a.toDouble().pow(b.toDouble()).toInt()
                    else -> return null
                }
                evaluationStack.add(result)
            }
        }

        return if (evaluationStack.size == 1) evaluationStack.first() as Int else null
    }

    private fun isNumber(token: String): Boolean {
        return token.toIntOrNull() != null
    }

    private fun isOperator(token: String): Boolean {
        return token.matches(Regex("[+\\-*/%^]"))
    }

    private fun getPrecedence(operator: String): Int {
        return when (operator) {
            "+", "-" -> 1
            "*", "/", "%" -> 2
            "^" -> 3
            else -> 0
        }
    }

    fun isExpression(countable: String): Boolean {
        return countable.contains('+') || countable.contains('-') ||
            countable.contains('*') || countable.contains('/') || countable.contains('%') ||
            countable.contains('^') || countable.contains('(') || countable.contains(')')
    }
}

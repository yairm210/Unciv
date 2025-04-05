package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import kotlin.math.pow

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (!countable.contains('[') && !countable.contains(']')) {
            return simpleCountableAmount(countable, stateForConditionals)
        }
        return evaluateExpression(countable, stateForConditionals)
    }

    private fun simpleCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)
        if (relevantStat != null) return stateForConditionals.getStatAmount(relevantStat)

        val gameInfo = stateForConditionals.gameInfo ?: return null

        if (countable == "turns") return gameInfo.turns
        if (countable == "year") return gameInfo.getYear(gameInfo.turns)

        val civInfo = stateForConditionals.relevantCiv ?: return null

        if (countable == "Cities") return civInfo.cities.size

        val placeholderParameters = countable.getPlaceholderParameters()
        if (countable.equalsPlaceholderText("[] Cities"))
            return civInfo.cities.count { it.matchesFilter(placeholderParameters[0]) }

        if (countable == "Units") return civInfo.units.getCivUnitsSize()
        if (countable.equalsPlaceholderText("[] Units"))
            return civInfo.units.getCivUnits().count { it.matchesFilter(placeholderParameters[0]) }

        if (countable.equalsPlaceholderText("[] Buildings"))
            return civInfo.cities.sumOf { it.cityConstructions.getBuiltBuildings()
                .count { it.matchesFilter(placeholderParameters[0]) } }

        if (countable.equalsPlaceholderText("Remaining [] Civilizations"))
            return gameInfo.civilizations.filter { !it.isDefeated() }
                .count { it.matchesFilter(placeholderParameters[0]) }

        if (countable.equalsPlaceholderText("Completed Policy branches"))
            return civInfo.getCompletedPolicyBranchesCount()

        if (countable.equalsPlaceholderText("Owned [] Tiles"))
            return civInfo.cities.sumOf { it.getTiles().count { it.matchesFilter(placeholderParameters[0]) } }

        if (gameInfo.ruleset.tileResources.containsKey(countable))
            return stateForConditionals.getResourceAmount(countable)

        return null
    }

    private fun evaluateExpression(expression: String, stateForConditionals: StateForConditionals): Int? {
        val tokens = parseExpression(expression)
        if (tokens.isEmpty()) return null

        return calculateExpression(tokens, stateForConditionals)
    }

    private fun parseExpression(expression: String): List<String> {
        val regex = Regex("([+\\-*/%^()]|\\[[^]]+]|\\b\\w+\\b)")
        val matches = regex.findAll(expression)
        val tokens = mutableListOf<String>()
        for (match in matches) {
            tokens.add(match.value)
        }
        return tokens
    }

    private fun calculateExpression(tokens: List<String>, stateForConditionals: StateForConditionals): Int? {
        val outputQueue = mutableListOf<String>()
        val operatorStack = mutableListOf<String>()

        for (token in tokens) {
            if (isNumber(token)) {
                outputQueue.add(token)
            } else if (token.startsWith("[") && token.endsWith("]")) {
                val innerToken = token.substring(1, token.length - 1)
                val value = getCountableAmount(innerToken, stateForConditionals)
                if (value == null) return null
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
                    "/" -> if (b == 0) return null else a / b
                    "%" -> if (b == 0) return null else a % b
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
}

package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import kotlin.math.pow

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.containsAnyOperator()) {
            return evaluateExpression(countable, stateForConditionals)
        }

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

    private fun String.containsAnyOperator(): Boolean {
        return this.contains(Regex("[+\\-*/%^]"))
    }

    private fun evaluateExpression(expression: String, stateForConditionals: StateForConditionals): Int? {
        val tokens = parseExpression(expression)
        if (tokens.isEmpty()) return null

        return calculateExpression(tokens, stateForConditionals)
    }

    private fun parseExpression(expression: String): List<String> {
        val regex = Regex("([+\\-*/%^()]|\\b\\w+\\b)")
        return regex.findAll(expression).map { it.value }.toList()
    }

    private fun calculateExpression(tokens: List<String>, stateForConditionals: StateForConditionals): Int? {
        val outputQueue = mutableListOf<String>()
        val operatorStack = mutableListOf<String>()

        for (token in tokens) {
            when {
                token.isNumber() || token.isCountable() -> outputQueue.add(token)
                token == "(" -> operatorStack.add(token)
                token == ")" -> {
                    while (operatorStack.lastOrNull() != "(") {
                        outputQueue.add(operatorStack.removeLast())
                    }
                    operatorStack.removeLast()
                }
                token.isOperator() -> {
                    while (operatorStack.isNotEmpty() && operatorStack.last() != "(" &&
                        getPrecedence(token) <= getPrecedence(operatorStack.last())
                    ) {
                        outputQueue.add(operatorStack.removeLast())
                    }
                    operatorStack.add(token)
                }
            }
        }

        while (operatorStack.isNotEmpty()) {
            outputQueue.add(operatorStack.removeLast())
        }

        val evaluationStack = mutableListOf<Any>()

        for (token in outputQueue) {
            when {
                token.isNumber() -> evaluationStack.add(token.toInt())
                token.isCountable() -> {
                    val value = getCountableAmount(token, stateForConditionals) ?: return null
                    evaluationStack.add(value)
                }
                token.isOperator() -> {
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
        }

        return if (evaluationStack.size == 1) evaluationStack.first() as Int else null
    }

    private fun String.isNumber(): Boolean {
        return toIntOrNull() != null
    }

    private fun String.isCountable(): Boolean {
        return !containsAnyOperator() && !isOperator() && !isParenthesis()
    }

    private fun String.isOperator(): Boolean {
        return matches(Regex("[+\\-*/%^]"))
    }

    private fun String.isParenthesis(): Boolean {
        return this == "(" || this == ")"
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

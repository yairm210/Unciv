package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (!countable.contains('[') && !countable.contains(']')) {
            return simpleCountableAmount(countable, stateForConditionals)
        }

        return evaluateExpression(countable, stateForConditionals)
    }

    private fun simpleCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        val number = countable.toIntOrNull()
        if (number != null) return number

        val relevantStat = Stat.safeValueOf(countable)
        if (relevantStat != null) {
            return stateForConditionals.getStatAmount(relevantStat)
        }

        val gameInfo = stateForConditionals.gameInfo
        if (gameInfo != null) {
            if (countable == "turns") return gameInfo.turns
            if (countable == "year") return gameInfo.getYear(gameInfo.turns)
        }

        val civInfo = stateForConditionals.relevantCiv
        if (civInfo != null) {
            if (countable == "Cities") return civInfo.cities.size

            val placeholderParameters = countable.getPlaceholderParameters()
            if (countable.equalsPlaceholderText("[] Cities")) {
                return civInfo.cities.count { it.matchesFilter(placeholderParameters[0]) }
            }

            if (countable == "Units") return civInfo.units.getCivUnitsSize()
            if (countable.equalsPlaceholderText("[] Units")) {
                return civInfo.units.getCivUnits().count { it.matchesFilter(placeholderParameters[0]) }
            }

            if (countable.equalsPlaceholderText("[] Buildings")) {
                var totalBuildings = 0
                for (city in civInfo.cities) {
                    val builtBuildings = city.cityConstructions.getBuiltBuildings()
                    totalBuildings += builtBuildings.count { it.matchesFilter(placeholderParameters[0]) }
                }
                return totalBuildings
            }

            if (countable.equalsPlaceholderText("Remaining [] Civilizations")) {
                var remainingCivs = 0
                if (gameInfo != null) {
                    for (civ in gameInfo.civilizations) {
                        if (!civ.isDefeated() && civ.matchesFilter(placeholderParameters[0])) {
                            remainingCivs++
                        }
                    }
                }
                return remainingCivs
            }

            if (countable.equalsPlaceholderText("Completed Policy branches")) {
                return civInfo.getCompletedPolicyBranchesCount()
            }

            if (countable.equalsPlaceholderText("Owned [] Tiles")) {
                var totalTiles = 0
                for (city in civInfo.cities) {
                    totalTiles += city.getTiles().count { it.matchesFilter(placeholderParameters[0]) }
                }
                return totalTiles
            }
        }

        val gameInfoNotNull = stateForConditionals.gameInfo
        if (gameInfoNotNull != null && gameInfoNotNull.ruleset.tileResources.containsKey(countable)) {
            return stateForConditionals.getResourceAmount(countable)
        }

        return null
    }

    private fun evaluateExpression(expression: String, stateForConditionals: StateForConditionals): Int? {
        val cleanedExpression = expression.replace("[", "").replace("]", "")
        val tokens = parseExpression(cleanedExpression)
        if (tokens.isEmpty()) return null

        return calculateExpression(tokens, stateForConditionals)
    }

    private fun parseExpression(expression: String): List<String> {
        val regex = Regex("([+\\-*/%^()]|\\b\\w+\\b)")
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
            } else if (isCountable(token)) {
                val value = simpleCountableAmount(token, stateForConditionals)
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
                    "^" -> Math.pow(a.toDouble(), b.toDouble()).toInt()
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

    private fun isCountable(token: String): Boolean {
        // 检查 token 是否包含运算符
        val hasOperator = token.matches(Regex(".*[+\\-*/%^].*"))
        return !hasOperator && !isOperator(token) && !isParenthesis(token)
    }

    private fun isOperator(token: String): Boolean {
        return token.matches(Regex("[+\\-*/%^]"))
    }

    private fun isParenthesis(token: String): Boolean {
        return token == "(" || token == ")"
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

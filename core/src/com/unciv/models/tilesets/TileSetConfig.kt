package com.unciv.models.tilesets

import com.badlogic.gdx.graphics.Color

class TileSetConfig {
    var useColorAsBaseTerrain = true
    var unexploredTileColor: Color = Color.DARK_GRAY
    var fogOfWarColor: Color = Color.BLACK
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    @Transient
    private val templatedRuleVariants: HashSet<Pair<TileComposition, Array<RuleContainer>>> = HashSet()
    @Transient
    private val templateDictionary = HashMap<String, Sequence<String?>>()

    fun updateConfig(other: TileSetConfig){
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        unexploredTileColor = other.unexploredTileColor
        fogOfWarColor = other.fogOfWarColor
        for ((tileSetString, renderOrder) in other.ruleVariants){
            ruleVariants[tileSetString] = renderOrder
        }
    }

    fun setTransients(){
        val ruleVariantsToRemove = ArrayList<String>()

        for ((tileCompositionString, renderOrder) in ruleVariants){
            if (tileCompositionString.contains('!')){
                ruleVariantsToRemove.add(tileCompositionString)
                val splitTileCompString = tileCompositionString.split('+')
                if (splitTileCompString.size < 5)
                    continue // forcing the existence of a full composition makes handling templated rule variants easier
                templatedRuleVariants.add(Pair(toTileComposition(splitTileCompString), toRuleContainer(renderOrder)))
            }
        }

        //remove all templatedRuleVariants from ruleVariants
        for (ruleVariant in ruleVariantsToRemove)
            ruleVariants.remove(ruleVariant)
    }

    /**
     * Returns a list if a templated rule variant exists which matches the given sequences or else null.
     * If a template is found the tile composition will be added to ruleVariants. The sequences must contain null
     * for each element which is not existing.
     */
    fun getTemplatedRuleVariant(terrainSequence: Sequence<String?>, resAndImpSequence: Sequence<String?>): List<String>?{
        for ((tileComposition, renderOrder) in templatedRuleVariants){
            templateDictionary.clear()

            //check if template matches
            if (!addToTemplateDictionary(tileComposition.baseTerrain, terrainSequence.first()) ||
                    !addToTemplateDictionary(tileComposition.terrainFeatures, terrainSequence.drop(1)) ||
                    !addToTemplateDictionary(tileComposition.baseTerrain, resAndImpSequence.first()) ||
                    !addToTemplateDictionary(tileComposition.baseTerrain, resAndImpSequence.last()))
                continue

            val finalRenderOrder = ArrayList<String>()
            for (element in renderOrder){
                if (element.isTemplate && templateDictionary[element.name] != null)
                    finalRenderOrder.addAll(templateDictionary[element.name]!!.filterNotNull())
                else
                    element.name
            }

            // This tile composition gets mapped to finalRenderOrder by a templated rule variant.
            // We can add it to ruleVariants to save time next time we search for this composition.
            ruleVariants[(terrainSequence + resAndImpSequence).filterNotNull().joinToString("+")] = finalRenderOrder.toTypedArray()

            return finalRenderOrder
        }

        return null
    }

    private data class RuleContainer(val name: String, val isTemplate: Boolean)

    private data class TileComposition(val baseTerrain: RuleContainer, val terrainFeatures: Array<RuleContainer>, val resource: RuleContainer, val improvement: RuleContainer)

    private fun addToTemplateDictionary(rules: Array<RuleContainer>, tileElements: Sequence<String?>): Boolean {
        var startTemplateStrings = sequenceOf<String?>()
        var endTemplateStrings = sequenceOf<String?>()
        val iterator = tileElements.iterator()
        var nextMustMatch = false

        //We look at all rule elements which are no templates
        nextRuleElement@for (ruleIndex in 1 .. rules.size - 2){
            //we check all tileElements until we find a match for this rule element
            while (iterator.hasNext()){
                val next = iterator.next()
                if (next != rules[ruleIndex].name) {
                    // we had a match before but this one is non -> return false
                    if (nextMustMatch)
                        return false
                    // if non matched yet -> add to start template
                    startTemplateStrings += next
                } else {
                    //After the first match all following once must match
                    nextMustMatch = true
                    continue@nextRuleElement
                }
            }
            // if there are still rule elements but no tile elements this template can not match
            return false
        }

        //Add all remaining elements to end template
        while (iterator.hasNext())
            endTemplateStrings += iterator.next()

        templateDictionary[rules.first().name] = startTemplateStrings
        templateDictionary[rules.last().name] = endTemplateStrings

        return true
    }

    private fun addToTemplateDictionary(rule: RuleContainer, tileElement: String?): Boolean {
        if (rule.isTemplate){
            templateDictionary[rule.name] = sequenceOf(tileElement)
            return true
        }
        if (rule.name == tileElement)
            return true

        return false
    }

    private fun toRuleContainer(input: Array<String>) =
            input.map {
                if (it.startsWith('!'))
                    RuleContainer(it.drop(1), true)
                else
                    RuleContainer(it, false)
            }.toTypedArray()

    private fun toRuleContainer(input: List<String>) =
            input.map {
                if (it.startsWith('!'))
                    RuleContainer(it.drop(1), true)
                else
                    RuleContainer(it, false)
            }.toTypedArray()

    private fun toRuleContainer(input: String) =
            if (input.startsWith('!'))
                RuleContainer(input.drop(1), true)
            else
                RuleContainer(input, false)

    private fun toTileComposition(input: List<String>) =
            TileComposition(
                    toRuleContainer(input.first()),
                    toRuleContainer(input.slice(1..input.size-3)),
                    toRuleContainer(input[input.size-2]),
                    toRuleContainer(input.last())
            )
}
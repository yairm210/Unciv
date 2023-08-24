package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.ui.components.input.KeyboardBinding

//todo bind to existing queue entry buttons
//todo template strings
//todo Kdoc
//todo KeyBindings
//todo Check move/top/end for "place one improvement" buildings
//todo Check add/remove-all for "place one improvement" buildings

class CityScreenConstructionMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val city: City,
    private val construction: IConstruction,
    private val onButtonClicked: () -> Unit
) : AnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {

    // These are only readability shorteners
    private val cityConstructions = city.cityConstructions
    private val name = construction.name
    private val queueSizeWithoutPerpetual = cityConstructions.constructionQueue
        .count { it !in PerpetualConstruction.perpetualConstructionsMap }
    private val myIndex = cityConstructions.constructionQueue.indexOf(name)
    private fun anyCity(predicate: (CityConstructions) -> Boolean) =
        city.civ.cities.map { it.cityConstructions }.any(predicate)
    private fun forAllCities(action: (CityConstructions) -> Unit) =
        city.civ.cities.map { it.cityConstructions }.forEach(action)

    init {
        closeListeners.add {
            if (anyButtonWasClicked) onButtonClicked()
        }
    }

    override fun createContentTable(): Table {
        val table = super.createContentTable()
        if (canMoveQueueTop())
            table.add(getButton("Move to the top of the queue", KeyboardBinding.RaisePriority, ::moveQueueTop)).row()
        if (canMoveQueueEnd())
            table.add(getButton("Move to the end of the queue", KeyboardBinding.LowerPriority, ::moveQueueEnd)).row()
        if (canAddQueueTop())
            table.add(getButton("Add to the top of the queue", KeyboardBinding.AddConstructionTop, ::addQueueTop)).row()
        if (canAddAllQueues())
            table.add(getButton("Add to the queue in all cities", KeyboardBinding.AddConstructionAll, ::addAllQueues)).row()
        if (canAddAllQueuesTop())
            table.add(getButton("Add or move to the top in all cities", KeyboardBinding.AddConstructionAllTop, ::addAllQueuesTop)).row()
        if (canRemoveAllQueues())
            table.add(getButton("Remove from the queue in all cities", KeyboardBinding.RemoveConstructionAll, ::removeAllQueues)).row()
        return table
    }

    private fun canMoveQueueTop(): Boolean {
        if (construction is PerpetualConstruction)
            return false
        return myIndex > 0
    }
    private fun moveQueueTop() = cityConstructions.moveEntryToTop(myIndex)

    private fun canMoveQueueEnd(): Boolean {
        if (construction is PerpetualConstruction)
            return false
        return myIndex in 0 until queueSizeWithoutPerpetual
    }
    private fun moveQueueEnd() = cityConstructions.moveEntryToEnd(myIndex)

    private fun canAddQueueTop() = construction !is PerpetualConstruction &&
        cityConstructions.canAddToQueue(construction)
    private fun addQueueTop() = cityConstructions.addToQueue(construction, addToTop = true)

    private fun canAddAllQueues() = anyCity { it.canAddToQueue(construction) }
    private fun addAllQueues() = forAllCities { it.addToQueue(construction) }

    private fun canAddAllQueuesTop() = construction !is PerpetualConstruction && canAddAllQueues()
    private fun addAllQueuesTop() = forAllCities { it.addToQueue(construction, true) }

    private fun canRemoveAllQueues() = anyCity { it.isBeingConstructedOrEnqueued(name) }
    private fun removeAllQueues() = forAllCities { it.removeAllByName(name) }
}

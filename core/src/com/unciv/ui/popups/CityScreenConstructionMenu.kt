package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.view.CityView
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

//todo Check add/remove-all for "place one improvement" buildings

/**
 *  "Context menu" for City constructions - available by right-clicking (or long-press) in
 *   City Screen, left side, available constructions or queue entries.
 *
 *  @param cityView The [CityView] calling us - we need only `cityConstructions`, but future expansion may be easier having the parent
 *  @param construction The construction that was right-clicked
 *  @param onButtonClicked Callback if closed due to any action having been chosen - to update CityScreen
 */
class CityScreenConstructionMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val cityView: CityView,
    private val construction: IConstruction,
    private val onButtonClicked: () -> Unit
) : AnimatedMenuPopup(stage, positionNextTo) {

    private val constructionName = construction.name
    private val queueSizeWithoutPerpetual get() =
        cityView.constructions.constructionQueue
        .count { it !in PerpetualConstruction.perpetualConstructionsMap }
    private val myIndex = cityView.constructions.constructionQueue.indexOf(constructionName)
    /** Cities (including this one) where changing the construction queue makes sense
     *  (excludes isBeingRazed even though technically that would be allowed) */
    // Can't use CityScreen.canChangeState for other cities
    @Readonly private fun candidateCities() = cityView.civ().cities().asSequence()
        .filterNot { it.isPuppet() || it.isInResistance() || it.isBeingRazed() }
    /** Check whether an "All cities" menu makes sense: `true` if there's more than one city, it's not a Wonder, and any city's queue matches [predicate]. */
    @Readonly private fun allCitiesEntryValid(predicate: (CityView) -> Boolean) =
        cityView.civ().cities().size > 1 &&
        (construction as? Building)?.isAnyWonder() != true &&
        candidateCities().any(predicate)
    private fun forAllCities(action: (CityView) -> Unit) =
        candidateCities().forEach(action)

    init {
        closeListeners.add {
            if (anyButtonWasClicked) onButtonClicked()
        }
    }

    override fun createContentTable(): Table? {
        val table = super.createContentTable()!!
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
        if (canDisable())
            table.add(getButton("Disable in this city", KeyboardBinding.BuildDisabled, ::disableEntry)).row()
        if (canDisableAll())
            table.add(getButton("Disable in all cities", KeyboardBinding.None, ::disableEntryInAllCities)).row()
        if (canEnable())
            table.add(getButton("Enable in this city", KeyboardBinding.BuildDisabled, ::enableEntry)).row()
        if (canEnableAll())
            table.add(getButton("Enable in all cities", KeyboardBinding.None, ::enableEntryInAllCities)).row()
        return table.takeUnless { it.cells.isEmpty }
    }

    @Pure
    private fun canMoveQueueTop(): Boolean {
        if (construction is PerpetualConstruction) return false
        return myIndex > 0
    }
    private fun moveQueueTop() = cityView.tryMoveEntryToTop(myIndex)

    @Pure
    private fun canMoveQueueEnd(): Boolean {
        if (construction is PerpetualConstruction) return false
        return myIndex in 0 until queueSizeWithoutPerpetual - 1
    }
    private fun moveQueueEnd() = cityView.tryMoveEntryToEnd(myIndex)

    @Readonly private fun isConstructionImprovementCreationBuilding() =
        construction is Building && construction.hasCreateOneImprovementUnique()

    @Readonly
    private fun canAddQueueTop() = construction !is PerpetualConstruction &&
        cityView.constructions.canAddToQueue(construction) &&
        !isConstructionImprovementCreationBuilding()

    private fun addQueueTop() = cityView.tryAddToQueueConstruction(construction, addToTop = true)

    @Readonly
    private fun canAddAllQueues() = allCitiesEntryValid {
        it.constructions.canAddToQueue(construction) &&
        !isConstructionImprovementCreationBuilding() &&
        // A Perpetual that is already queued can still be added says canAddToQueue, but here we don't want to count that
        !(construction is PerpetualConstruction && it.constructions.isBeingConstructedOrEnqueued(constructionName))
    }
    private fun addAllQueues() = forAllCities { it.tryAddToQueueConstruction(construction) }

    @Readonly
    private fun canAddAllQueuesTop() = construction !is PerpetualConstruction &&
        allCitiesEntryValid {
            (it.constructions.canAddToQueue(construction) && !isConstructionImprovementCreationBuilding()) ||
            it.constructions.isEnqueuedForLater(constructionName) }

    private fun addAllQueuesTop() = forAllCities {
        val index = it.constructions.constructionQueue.indexOf(constructionName)
        if (index > 0)
            it.tryMoveEntryToTop(index)
        else
            it.tryAddToQueueConstruction(construction, addToTop = true)
    }

    @Readonly private fun canRemoveAllQueues() = allCitiesEntryValid {
        it.constructions.isBeingConstructedOrEnqueued(constructionName)
    }
    private fun removeAllQueues() = forAllCities { it.tryRemoveAllByName(constructionName) }

    @Readonly private fun canDisable() = constructionName !in cityView.getDisabledConstructions()
        && construction != PerpetualConstruction.Idle

    @Readonly private fun canDisableAll() =
        !cityView.civ().isCivConstructionDisabled(constructionName) &&
        construction != PerpetualConstruction.Idle

    /**
     * One-time effect: disables the construction in this city only.
     */
    private fun disableEntry() = cityView.tryDisableConstruction(constructionName)

    /**
     * One-time effect: disables this construction in all cities.
     * Persistent effect: disabled in newly founded cities.
     */
    private fun disableEntryInAllCities() = cityView.civ().tryDisableCivConstruction(constructionName)

    @Readonly private fun canEnable() = constructionName in cityView.getDisabledConstructions()

    @Readonly private fun canEnableAll() = cityView.civ().isCivConstructionDisabled(constructionName)

    /** Similar to [disableEntry] */
    private fun enableEntry() = cityView.tryEnableConstruction(constructionName)

    /** Similar to [disableEntryInAllCities] */
    private fun enableEntryInAllCities() = cityView.civ().tryEnableCivConstruction(constructionName)
}

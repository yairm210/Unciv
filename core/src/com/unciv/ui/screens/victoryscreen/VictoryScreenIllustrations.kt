package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.isNarrowerThan4to3
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

// todo Image sizing
// todo Idea: use victoryCompletePercent to allow "percentage" images - e.g. "Destroy all players" now works all or nothing - boring.

class VictoryScreenIllustrations(
    parent: VictoryScreen,
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {

    companion object {
        private const val fadeDuration = 1.2f
        private const val basePath = "VictoryIllustrations"
        private const val iconPath = "VictoryTypeIcons"
        private val enablingImages = listOf("Won", "Lost", "Background")

        internal fun enablePage(game: GameInfo) = game.getEnabledVictories().values
            .any { it.hasIllustrations() }

        private fun Victory.hasIllustrations() = enablingImages.any { element ->
            ImageGetter.imageExists(getImageName(this, element))
        }

        private fun getImageName(victory: Victory, element: String) =
            "$basePath/${victory.name}/$element"

        private fun getImageOrNull(name: String) =
            if (ImageGetter.imageExists(name)) ImageGetter.getImage(name) else null

        private fun getImageOrNull(victory: Victory, element: String) =
            getImageOrNull(getImageName(victory, element))
    }

    private val game = worldScreen.gameInfo
    private val maxLabelWidth = parent.stage.run { width * (if (isNarrowerThan4to3()) 0.9f else 0.7f) }
    private val victories = game.getEnabledVictories().values
        .filter { it.hasIllustrations() }
        .sortedBy { it.name.tr(hideIcons = true) }
    private val selectedCiv = worldScreen.selectedCiv
    private val completionPercentages = game.civilizations
        .filter { it.isMajorCiv() && it.isAlive() || it == selectedCiv }
        .associateWith { civ ->
            victories.associateWith { victoryCompletePercent(it, civ) }
        }

    private val tabs = TabbedPager(shortcutScreen = parent, capacity = victories.size)
    private val holder = Stack()

    private var selectedVictory = selectVictory()

    init {
        top()
        holder.touchable = Touchable.disabled

        for (victory in victories) {
            val iconName = "$iconPath/${victory.name}"
            val icon = getImageOrNull(iconName)
            val key = KeyCharAndCode(victory.name.first())
            tabs.addPage(victory.name, holder, icon, 20f, shortcutKey = key)
        }

        tabs.selectPage(selectedVictory)
        add(tabs).top().grow()
    }

    private fun select() {
        val victory = victories.firstOrNull { it.name == selectedVictory } ?: return
        tabs.onSelection(null)  // Prevent recursion from replacePage
        val fadeAction = FadeTo(holder, getImages(victory))  // side effect: adds images to holder and packs it
        tabs.replacePage(tabs.activePage, holder)  // Force TabbedPager to measure content
        holder.addAction(fadeAction)
        tabs.onSelection { _, name, _ ->
            selectedVictory = name
            select()
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        select()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
        holder.clear()
    }

    private class FadeTo(
        private val holder: Stack,
        private val newActors: List<Actor>
    ) : TemporalAction(fadeDuration), Disposable {
        private var oldActors: List<Actor>? = holder.children.toList()  // nullable to allow relinquishing the references when done
        private val fadeOutFrom = oldActors!!.firstOrNull()?.color?.a ?: 1f

        init {
            holder.actions.filterIsInstance<FadeTo>().forEach { it.dispose() }
            for (actor in newActors) {
                actor.color.a = 0f
                holder.add(actor)
            }
            holder.pack()
            holder.invalidateHierarchy()
        }

        override fun dispose() {
            end()
        }

        override fun update(percent: Float) {
            val alpha = Interpolation.fade.apply(percent)
            for (actor in newActors) actor.color.a = alpha
            if (oldActors == null) return
            val oldAlpha = Interpolation.fade.apply(1f - percent) * fadeOutFrom
            for (actor in oldActors!!) actor.color.a = oldAlpha
        }

        override fun end() {
            val toRemove = oldActors ?: return
            oldActors = null
            for (actor in toRemove) holder.removeActor(actor)
        }
    }

    private fun selectVictory(): String {
        if (game.victoryData != null) return game.victoryData!!.victoryType
        val victory = victories.asSequence()
            .sortedWith(
                compareByDescending<Victory> { victory ->
                    completionPercentages[selectedCiv]?.get(victory) ?: 0
                }.thenByDescending { victory ->
                    game.civilizations.filter { it != selectedCiv && it.isMajorCiv() && it.isAlive() }
                        .maxOfOrNull { victoryCompletePercent(victory, it) } ?: 0
                }
            ).firstOrNull() ?: victories.first()
        return victory.name
    }

    private fun victoryCompletePercent(victory: Victory, civ: Civilization): Int {
        var points = 0
        var total = 0
        for (milestone in victory.milestoneObjects) {
            val completed = milestone.hasBeenCompletedBy(civ)
            val milestonePoints = when (milestone.type) {
                MilestoneType.AddedSSPartsInCapital -> {
                    total += victory.requiredSpaceshipParts.size
                    civ.victoryManager.currentsSpaceshipParts.sumValues()
                }
                MilestoneType.DestroyAllPlayers -> {
                    total += if (selectedCiv.hideCivCount()) game.gameParameters.maxNumberOfPlayers
                        else game.civilizations.count { it.isMajorCiv() }
                    game.civilizations.count {
                        it != civ && it.isMajorCiv() && civ.knows(it) && it.isDefeated()
                    }
                }
                MilestoneType.CaptureAllCapitals -> {
                    total += if (selectedCiv.hideCivCount()) game.gameParameters.maxNumberOfPlayers
                        else game.getCities().count { it.isOriginalCapital }
                    civ.cities.count { it.isOriginalCapital }
                }
                MilestoneType.CompletePolicyBranches -> {
                    total += milestone.params[0].toInt()
                    civ.policies.completedBranches.size
                }
                MilestoneType.WorldReligion -> {
                    total += game.civilizations.count { it.isMajorCiv() && it.isAlive() }
                    val religion = civ.religionManager.religion?.takeUnless { it.isPantheon() }
                    game.civilizations.count {
                        religion != null &&
                            it.isMajorCiv() && it.isAlive() && civ.knows(it) &&
                            it.religionManager.isMajorityReligionForCiv(religion)
                    }
                }
                MilestoneType.ScoreAfterTimeOut -> {
                    total += game.gameParameters.maxTurns
                    game.turns.coerceAtMost(game.gameParameters.maxTurns)
                }
                else -> {
                    total += 2
                    if (completed) 2 else 0
                }
            }
            points += milestonePoints
        }
        return points * 100 / total
    }

    private fun getImages(victory: Victory): List<Actor> {
        game.victoryData?.run {
            if (victory.name == victoryType && selectedCiv.civName == winningCiv) {
                val image = getImageOrNull(victory, "Won")
                return getWonOrLostStack(image, victory.victoryString, Color.GOLD)
            }
            val image = getImageOrNull(victory, "Lost")
            return getWonOrLostStack(image, victory.defeatString.takeIf { victory.name == victoryType }, Color.MAROON)
        }

        val result = mutableListOf<Actor>()
        result.addImageIf(victory, "Background") { true }
        for (milestone in victory.milestoneObjects) {
            val element = milestone.uniqueDescription.replace("[", "").replace("]", "")
            result.addImageIf(victory, element) { milestone.hasBeenCompletedBy(selectedCiv) }
            if (milestone.type != MilestoneType.AddedSSPartsInCapital) continue
            for ((key, required) in victory.requiredSpaceshipPartsAsCounter) {
                val built = selectedCiv.victoryManager.currentsSpaceshipParts[key]
                result.addImageIf(victory, key) { built > 0 }
                for (i in 1..required)
                    result.addImageIf(victory, "$key $i") { built >= i }
            }
        }
        return result
    }

    private fun getWonOrLostStack(image: Image?, text: String?, color: Color): List<Actor> {
        val container = if (text == null) null
        else {
            val label = text.toLabel(color, 50, Align.bottom)
            label.wrap = true
            label.width = maxLabelWidth
            Container(label).apply { bottom() }
        }
        return listOfNotNull(image, container)
    }

    private fun MutableList<Actor>.addImageIf(victory: Victory, element: String, test: () -> Boolean) {
        if (!test()) return
        val image = getImageOrNull(victory, element)
        if (image != null) add(image)
    }
}

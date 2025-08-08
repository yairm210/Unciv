package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.ImageWithCustomSize  // Kdoc, not used
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

// todo Idea: use victoryCompletePercent to allow "percentage" images - e.g. "Destroy all players" now works all or nothing - boring.
// (requires some rework of the victoryCompletePercent here and similar code in VictoryManager - and likely move stuff to make MilestoneType an intelligent enum)

class VictoryScreenIllustrations(
    parent: VictoryScreen,
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {

    companion object {
        private const val fadeDuration = 1.2f
        private const val basePath = "VictoryIllustrations"
        private const val iconPath = "VictoryTypeIcons"
        private val enablingImages = listOf("Won", "Lost", "Background")

        /** Check whether the entire "Illustrations" tab in VictoryScreen should display */
        internal fun enablePage(game: GameInfo) = game.getEnabledVictories().values
            .any { it.hasIllustrations() }

        /** Check whether a Victory has enough images to display that Victory's sub-tab */
        private fun Victory.hasIllustrations() = enablingImages.any { element ->
            ImageGetter.imageExists(getImageName(this, element))
        }

        /** Build a texture atlas path for a [victory] and an "[element]", which can be a Milestone name or other Decoration part name */
        private fun getImageName(victory: Victory, element: String) =
            "$basePath/${victory.name}/$element"

        /** Gets an image if it exists as [ImageWithFixedPrefSize] */
        private fun getImageOrNull(name: String) =
            if (ImageGetter.imageExists(name))
                // ImageGetter.getImage uses ImageWithCustomSize which interferes incorrectly with our sizing
                ImageWithFixedPrefSize(ImageGetter.getDrawable(name)) else null

        /** Gets an image if a texture for [victory] and [element] exists (see [getImageName]) as [ImageWithFixedPrefSize] */
        private fun getImageOrNull(victory: Victory, element: String) =
            getImageOrNull(getImageName(victory, element))

        /** Readability shortcut for [getImages] */
        private fun MutableList<Actor>.addImageIf(victory: Victory, element: String, test: () -> Boolean) {
            if (!test()) return
            val image = getImageOrNull(victory, element)
            if (image != null) add(image)
        }
    }

    private val game = worldScreen.gameInfo
    private val victories = game.getEnabledVictories().values
        .filter { it.hasIllustrations() }
        .sortedBy { it.name.tr(hideIcons = true) }
    private val selectedCiv = worldScreen.selectedCiv
    private val completionPercentages = game.civilizations
        .filter { it.isMajorCiv() && it.isAlive() || it == selectedCiv }
        .associateWith { civ ->
            victories.associateWith { victoryCompletePercent(it, civ) }
        }

    private val tabs = TabbedPager(backgroundColor = Color.CLEAR, shortcutScreen = parent, capacity = victories.size)
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

    /** Activates content for [selectedVictory]. Images are loaded and [FadeTo] animation started. */
    // Note the little trick - all tabs of our inner per-Victory TabbedPager contain the same holder,
    // So we can fade over from one Victory to the next.
    private fun select() {
        val victory = victories.firstOrNull { it.name == selectedVictory } ?: return
        tabs.onSelection(null)  // Prevent recursion from replacePage
        // todo measure: 265f = PickerPane.bottomTable.height + SplitPane.handle.height + 2* TabbedPager.header.height + separator.height
        val maxHeight = stage.height - 265f
        val fadeAction = FadeTo(holder, getImages(victory), maxHeight)  // side effect: adds images to holder and packs it
        tabs.replacePage(tabs.activePage, holder)  // Force TabbedPager to measure content
        holder.addAction(fadeAction)
        tabs.onSelection { _, name, _ ->
            selectedVictory = name
            select()
        }
    }

    /** When the outer TabbedPager selects `this` page... */
    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        select()
    }

    /** When the outer TabbedPager de-selects `this` page... */
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
        holder.clear()
    }

    /** A specialized Gdx.Action for fading over one set (already loaded into [holder]) of images to
     *  another (from parameter [newActors]).
     *
     *  Initialization has the ***side effects*** of stacking the new images into [holder],
     *  sizing them preserving aspect ratio (which is why it needs `maxHeight`: taking available
     *  width from holder.width works, same for height does not), and invalidating ascendants.
     */
    private class FadeTo(
        private val holder: Stack,
        private val newActors: List<Actor>,
        maxHeight: Float
    ) : TemporalAction(fadeDuration), Disposable {
        private var oldActors: List<Actor>? = holder.children.toList()  // nullable to allow relinquishing the references when done
        private val fadeOutFrom = oldActors!!.firstOrNull()?.color?.a ?: 1f

        init {
            holder.actions.filterIsInstance<FadeTo>().forEach { it.dispose() }
            holder.addAndSize(newActors, maxHeight)
            holder.invalidateHierarchy()
        }

        override fun dispose() {
            finish()
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

        private fun Stack.addAndSize(actors: List<Actor>, maxHeight: Float) {
            // Local and not to be confused with Gdx or awt versions. We need a width/height container only
            class Rectangle(var width: Float, var height: Float) {
                constructor(region: TextureRegion) : this(region.regionWidth.toFloat(), region.regionHeight.toFloat())
            }

            for (actor in actors) {
                actor.color.a = 0f
                // Scale max image dimensions into holder space minus padding preserving aspect ratio
                val imageArea = Rectangle(this.width - 30f, maxHeight - 30f)
                if (actor is ImageWithFixedPrefSize)  {
                    val pixelArea = Rectangle((actor.drawable as TextureRegionDrawable).region)
                    // Determine image aspect ratio, asuming square pixels:
                    // (Image actor.width, actor.height are empirically equal to the image's pixel dimensions
                    // at this moment, before actor has a parent, but I don't trust that happenstance.)
                    if (pixelArea.width * imageArea.height > imageArea.width * pixelArea.height)
                        imageArea.height = imageArea.width * pixelArea.height / pixelArea.width
                    else
                        imageArea.width = imageArea.height * pixelArea.width / pixelArea.height
                    actor.setPrefSize(imageArea.width, imageArea.height)
                }
                actor.setSize(imageArea.width, imageArea.height)
                add(actor)
            }
        }
    }

    /** Determine the Victory to show initially - try to select the most interesting one for the current game. */
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

    /** Calculate a completion percentage for a [victory] -
     *  relative weights for individual milestones are not equal in this implementation!
     *  (weight = number of sub-steps, or 2 if a milestone doesn't have any - very debatable)
     */
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
                    total += if (selectedCiv.shouldHideCivCount()) game.gameParameters.maxNumberOfPlayers
                        else game.civilizations.count { it.isMajorCiv() }
                    game.civilizations.count {
                        it != civ && it.isMajorCiv() && civ.knows(it) && it.isDefeated()
                    }
                }
                MilestoneType.CaptureAllCapitals -> {
                    total += if (selectedCiv.shouldHideCivCount()) game.gameParameters.maxNumberOfPlayers
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
        if (total == 0) return 0  // no milestones, no points - e.g. game just started, there are no capitals
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
        val label = text?.toLabel(color, 24, Align.bottom)?.apply { wrap = true }
        return listOfNotNull(image, label)
    }

    /** Variant of [ImageWithCustomSize] that avoids certain problems.
     *
     *  Reports a `prefWidth`/`prefHeight` set through [setPrefSize], which cannot be otherwise
     *  altered, especially not by ascendant layout methods, since they don't know the interface.
     *  This size defaults to the [drawable]'s minWidth/minHeight - same as what [Image] reports as
     *  `prefWidth`/`prefHeight` directly without the ability to override.
     */
    private class ImageWithFixedPrefSize(drawable: Drawable) : Image(drawable) {
        private var prefW: Float = prefWidth
        private var prefH: Float = prefHeight
        fun setPrefSize(w: Float, h: Float) {
            prefW = w
            prefH = h
        }
        override fun getPrefWidth() = prefW
        override fun getPrefHeight() = prefH
    }
}

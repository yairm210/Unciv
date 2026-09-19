package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

/** Renders an [Event] for [AlertPopup] or a floating tutorial task on [WorldScreen] */
class RenderEvent(
    event: Event,
    val worldScreen: WorldScreen,
    val unit: MapUnit? = null,
    /** Width the texts wrap to - an upper limit rather than a size: [WrappableLabel.optimizePrefWidth]
     *  can bring a label below it, and a child that cannot wrap at all makes the result wider.
     *
     *  It is fixed at construction because it is baked into the children there, and their preferred
     *  width is settled from that moment on: [WrappableLabel.getPrefWidth] is the minimum of the
     *  measured width, the `expectedWidth` handed to the constructor, and the value
     *  [WrappableLabel.optimizePrefWidth] computes once - from that same `expectedWidth`, and
     *  clamped to it. Laying an existing instance out again therefore cannot widen or narrow it -
     *  the Table would follow, it recomputes its columns from zero on every run, but the labels
     *  keep reporting the width they were built for. To change this width, build a new one. */
    val labelWidth: Float = worldScreen.stage.width * 0.5f,
    val onChoice: (EventChoice) -> Unit
) : Table() {
    private val gameInfo get() = worldScreen.gameInfo

    val isValid: Boolean

    //todo check generated translations

    init {
        defaults().fillX().center().pad(5f)

        val gameContext = GameContext(gameInfo.currentPlayerCiv, unit = unit)
        val choices = event.getMatchingChoices(gameContext)
        isValid = choices != null
        if (isValid) {
            if (event.text.isNotEmpty()) {
                add(WrappableLabel(event.text, labelWidth).apply {
                    wrap = true
                    setAlignment(Align.center)
                    optimizePrefWidth()
                }).row()
            }
            if (event.civilopediaText.isNotEmpty()) {
                add(event.renderCivilopediaText(labelWidth, ::openCivilopedia)).row()
            }

            for (choice in choices!!) addChoice(choice)
        }
    }

    private fun addChoice(choice: EventChoice) {
        addSeparator()

        val button = choice.text.toTextButton()
        button.onActivation {
            onChoice(choice)
            choice.triggerChoice(gameInfo.currentPlayerCiv, unit)
        }
        val key = KeyCharAndCode.parse(choice.keyShortcut)
        if (key != KeyCharAndCode.UNKNOWN) {
            button.keyShortcuts.add(key)
            button.addTooltip(key)
        }
        add(button).row()

        val lines = (
            choice.civilopediaText.asSequence()
                + choice.uniqueObjects.filter { it.isTriggerable || it.type == UniqueType.Comment }
                    .filterNot { it.isHiddenToUsers() }
                    .map { FormattedLine(it) }
            ).asIterable()
        add(MarkupRenderer.render(lines, labelWidth, linkAction = ::openCivilopedia)).row()
    }

    private fun openCivilopedia(link: String) = worldScreen.openCivilopedia(link)
}

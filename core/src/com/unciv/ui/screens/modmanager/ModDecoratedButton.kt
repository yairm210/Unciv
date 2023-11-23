package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.metadata.ModCategories
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter

/** A mod button on the Mod Manager Screen...
 *
 *  Used both in the "installed" and the "online/downloadable" columns.
 *  The "installed" version shows indicators for "Selected as permanent visual mod" and "update available",
 *  as read from the [modInfo] fields, but requires a [updateIndicators] call when those change.
 */
internal class ModDecoratedButton(private var modInfo: ModUIData) : Table() {
    private val stateImages: ModStateImages?
    private val textButton: TextButton

    init {
        touchable = Touchable.enabled

        val topics = modInfo.topics()
        val categories = ArrayList<ModCategories.Category>()
        for (category in ModCategories) {
            if (category == ModCategories.default()) continue
            if (topics.contains(category.topic)) categories += category
        }

        textButton = modInfo.buttonText().toTextButton()
        val topicString = categories.joinToString { it.label.tr() }
        if (categories.isNotEmpty()) {
            textButton.row()
            textButton.add(topicString.toLabel(fontSize = 14))
        }

        add(textButton)

        if (modInfo.ruleset == null) {
            stateImages = null
        } else {
            stateImages = ModStateImages()
            add(stateImages).align(Align.left)
            updateIndicators()
        }
    }

    fun updateIndicators() = stateImages?.update(modInfo)

    fun setText(text: String) = textButton.setText(text)
    override fun setColor(color: Color) { textButton.color = color }
    override fun getColor(): Color = textButton.color

    fun updateUIData(newModUIData: ModUIData) {
        modInfo = newModUIData
    }

    /** Helper class keeps references to decoration images of installed mods to enable dynamic visibility
     * (actually we do not use isVisible but refill thiis container selectively which allows the aggregate height to adapt and the set to center vertically)
     */
    private class ModStateImages : Table() {
        /** image indicating _enabled as permanent visual mod_ */
        private val visualImage: Image = ImageGetter.getImage("UnitPromotionIcons/Scouting")
        /** image indicating _online mod has been updated_ */
        private val hasUpdateImage: Image = ImageGetter.getImage("OtherIcons/Mods")

        init {
            defaults().size(20f).align(Align.topLeft)
        }

        fun update(modInfo: ModUIData) {
            clear()
            if (modInfo.isVisual) add(visualImage).row()
            if (modInfo.hasUpdate) add(hasUpdateImage).row()
            pack()
        }

        override fun getMinWidth() = 20f
    }
}

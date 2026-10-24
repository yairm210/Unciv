package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter

/**
 *  A widget containing two [Label]s superimposed with an offset to create a shadow effect.
 *
 *  Reported [prefWidth], [prefHeight], [minWidth] and [minHeight] are always those of the Label plus `shadowOffset`.
 *
 *  If not sized by a parent Layout hierarchy, this starts pre-"pack"ed at its preferred size.
 *
 *  @param text The label text (sic), autotranslated
 *  @param fontSize as the name says
 *  @param labelColor as the name says
 *  @param shadowColor as the name says
 *  @param hideIcons passed to [translation function][String.tr]
 *  @param shadowOffset displacement distance of the shadow to right and to bottom
 */

class ShadowedLabel(
    text: String,
    fontSize: Int = Constants.defaultFontSize,
    labelColor: Color = Color.WHITE,
    shadowColor: Color = ImageGetter.CHARCOAL,
    hideIcons: Boolean = true,
    shadowOffset: Float = 1f
) : Stack() {
    private val widthWithShadow: Float
    private val heightWithShadow: Float

    init {
        touchable = Touchable.disabled
        val shadow = text.toLabel(shadowColor, fontSize, Align.bottomRight, hideIcons)
        shadow.touchable = Touchable.disabled
        addActor(shadow)
        val label = text.toLabel(labelColor, fontSize, Align.topLeft, hideIcons)
        label.touchable = Touchable.disabled
        addActor(label)

        shadow.zIndex = 0
        // Displace the shadow under the label by shadowOffset.
        // Extending our size is enough due to their different Align values.
        widthWithShadow = label.prefWidth + shadowOffset
        heightWithShadow = label.prefHeight + shadowOffset
        // Stack has already initialized width and height to bogus 150x150 units - if we will be part of a Layout hierarchy, we'll get
        // a new size soon enough, but for "floating" use like in BattleTableHelpers.createDamageLabel it's nicer to start with something sensible.
        setSize(widthWithShadow, heightWithShadow)
    }

    override fun getPrefWidth() = widthWithShadow
    override fun getPrefHeight() = heightWithShadow
    // A Label has min=pref, but Stack overrides so we must override too
    // or Stack will return the smaller values directly from Label
    override fun getMinWidth() = widthWithShadow
    override fun getMinHeight() = heightWithShadow
}

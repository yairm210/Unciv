package com.unciv.ui.utils.extensions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Fonts

/**
 * Collection of extension functions mostly for libGdx widgets
 */

/** Disable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties. */
fun Button.disable() {
    touchable= Touchable.disabled
    color = Color.GRAY
}
/** Enable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties. */
fun Button.enable() {
    color = Color.WHITE
    touchable = Touchable.enabled
}
/** Enable or disable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties,
 *  or returns the corresponding state.
 *
 *  Do not confuse with Gdx' builtin [isDisabled][Button.isDisabled] property,
 *  which is more appropriate to toggle On/Off buttons, while this one is good for 'click-to-do-something' buttons.
 */
var Button.isEnabled: Boolean
    get() = touchable == Touchable.enabled
    set(value) = if (value) enable() else disable()

/** Create a new [Color] instance from [r]/[g]/[b] given as Integers in the range 0..255 */
fun colorFromRGB(r: Int, g: Int, b: Int) = Color(r / 255f, g / 255f, b / 255f, 1f)
/** Create a new [Color] instance from r/g/b given as Integers in the range 0..255 in the form of a 3-element List [rgb] */
fun colorFromRGB(rgb: List<Int>) = colorFromRGB(rgb[0], rgb[1], rgb[2])
/** Linearly interpolates between this [Color] and [BLACK][Color.BLACK] by [t] which is in the range [[0,1]].
 * The result is returned as a new instance. */
fun Color.darken(t: Float): Color = Color(this).lerp(Color.BLACK, t)
/** Linearly interpolates between this [Color] and [WHITE][Color.WHITE] by [t] which is in the range [[0,1]].
 * The result is returned as a new instance. */
fun Color.brighten(t: Float): Color = Color(this).lerp(Color.WHITE, t)

fun Actor.centerX(parent: Actor) { x = parent.width / 2 - width / 2 }
fun Actor.centerY(parent: Actor) { y = parent.height / 2 - height / 2 }
fun Actor.center(parent: Actor) { centerX(parent); centerY(parent) }

fun Actor.centerX(parent: Stage) { x = parent.width / 2 - width / 2 }
fun Actor.centerY(parent: Stage) { y = parent.height / 2 - height / 2 }
fun Actor.center(parent: Stage) { centerX(parent); centerY(parent) }

/** same as [onClick], but sends the [InputEvent] and coordinates along */
fun Actor.onClickEvent(sound: UncivSound = UncivSound.Click, function: (event: InputEvent?, x: Float, y: Float) -> Unit) {
    this.addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            launchCrashHandling("Sound") { SoundPlayer.play(sound) }
            function(event, x, y)
        }
    })
}

// If there are other buttons that require special clicks then we'll have an onclick that will accept a string parameter, no worries
fun Actor.onClick(sound: UncivSound = UncivSound.Click, function: () -> Unit) {
    onClickEvent(sound) { _, _, _ -> function() }
}

fun Actor.onClick(function: () -> Unit): Actor {
    onClick(UncivSound.Click, function)
    return this
}

fun Actor.onChange(function: (event: ChangeListener.ChangeEvent?) -> Unit): Actor {
    this.addListener(object : ChangeListener() {
        override fun changed(event: ChangeEvent?, actor: Actor?) {
            function(event)
        }
    })
    return this
}

fun Actor.surroundWithCircle(size: Float, resizeActor: Boolean = true, color: Color = Color.WHITE): IconCircleGroup {
    return IconCircleGroup(size, this, resizeActor, color)
}


fun Actor.addBorder(size:Float, color: Color, expandCell:Boolean = false): Table {
    val table = Table()
    table.pad(size)
    table.background = ImageGetter.getBackground(color)
    val cell = table.add(this)
    if (expandCell) cell.expand()
    cell.fill()
    table.pack()
    return table
}

fun Group.addBorderAllowOpacity(size:Float, color: Color): Group {
    val group = this
    fun getTopBottomBorder() = ImageGetter.getDot(color).apply { width=group.width; height=size }
    addActor(getTopBottomBorder().apply { setPosition(0f, group.height, Align.topLeft) })
    addActor(getTopBottomBorder().apply { setPosition(0f, 0f, Align.bottomLeft) })
    fun getLeftRightBorder() = ImageGetter.getDot(color).apply { width=size; height=group.height }
    addActor(getLeftRightBorder().apply { setPosition(0f, 0f, Align.bottomLeft) })
    addActor(getLeftRightBorder().apply { setPosition(group.width, 0f, Align.bottomRight) })
    return group
}


/** get background Image for a new separator */
private fun getSeparatorImage(color: Color) = ImageGetter.getDot(
    if (color.a != 0f) color else BaseScreen.skin.get("color", Color::class.java) //0x334d80
)

/**
 * Create a horizontal separator as an empty Container with a colored background.
 * @param colSpan Optionally override [colspan][Cell.colspan] which defaults to the current column count.
 */
fun Table.addSeparator(color: Color = Color.WHITE, colSpan: Int = 0, height: Float = 2f): Cell<Image> {
    if (!cells.isEmpty && !cells.last().isEndRow) row()
    val separator = getSeparatorImage(color)
    val cell = add(separator)
        .colspan(if (colSpan == 0) columns else colSpan)
        .minHeight(height).fillX()
    row()
    return cell
}

/**
 * Create a vertical separator as an empty Container with a colored background.
 *
 * Note: Unlike the horizontal [addSeparator] this cannot automatically span several rows. Repeat the separator if needed.
 */
fun Table.addSeparatorVertical(color: Color = Color.WHITE, width: Float = 2f): Cell<Image> {
    return add(getSeparatorImage(color)).width(width).fillY()
}

/** Alternative to [Table].[add][Table] that returns the Table instead of the new Cell to allow a different way of chaining */
fun <T : Actor> Table.addCell(actor: T): Table {
    add(actor)
    return this
}

/** Shortcut for [Cell].[pad][com.badlogic.gdx.scenes.scene2d.ui.Cell.pad] with top=bottom and left=right */
fun <T : Actor> Cell<T>.pad(vertical: Float, horizontal: Float): Cell<T> {
    return pad(vertical, horizontal, vertical, horizontal)
}

/** Sets both the width and height to [size] */
fun Image.setSize(size: Float) {
    setSize(size, size)
}

/** Translate a [String] and make a [TextButton] widget from it */
fun String.toTextButton() = TextButton(this.tr(), BaseScreen.skin)

/** Translate a [String] and make a [Label] widget from it */
fun String.toLabel() = Label(this.tr(), BaseScreen.skin)
/** Make a [Label] widget containing this [Int] as text */
fun Int.toLabel() = this.toString().toLabel()

/** Translate a [String] and make a [Label] widget from it with a specified font color and size */
fun String.toLabel(fontColor: Color = Color.WHITE, fontSize: Int = Constants.defaultFontSize): Label {
    // We don't want to use setFontSize and setFontColor because they set the font,
    //  which means we need to rebuild the font cache which means more memory allocation.
    var labelStyle = BaseScreen.skin.get(Label.LabelStyle::class.java)
    if (fontColor != Color.WHITE || fontSize != Constants.defaultFontSize) { // if we want the default we don't need to create another style
        labelStyle = Label.LabelStyle(labelStyle) // clone this to another
        labelStyle.fontColor = fontColor
        if (fontSize != Constants.defaultFontSize) labelStyle.font = Fonts.font
    }
    return Label(this.tr(), labelStyle).apply { setFontScale(fontSize / Fonts.ORIGINAL_FONT_SIZE) }
}

/**
 * Translate a [String] and make a [CheckBox] widget from it.
 * @param changeAction A callback to call on change, with a boolean lambda parameter containing the current [isChecked][CheckBox.isChecked].
 */
fun String.toCheckBox(startsOutChecked: Boolean = false, changeAction: ((Boolean)->Unit)? = null)
    = CheckBox(this.tr(), BaseScreen.skin).apply {
        isChecked = startsOutChecked
        if (changeAction != null) onChange {
            changeAction(isChecked)
        }
        // Add a little distance between the icon and the text. 0 looks glued together,
        // 5 is about half an uppercase letter, and 1 about the width of the vertical line in "P".
        imageCell.padRight(1f)
    }

/** Sets the [font color][Label.LabelStyle.fontColor] on a [Label] and returns it to allow chaining */
fun Label.setFontColor(color: Color): Label {
    style = Label.LabelStyle(style).apply { fontColor=color }
    return this
}

/** Sets the font size on a [Label] and returns it to allow chaining */
fun Label.setFontSize(size:Int): Label {
    style = Label.LabelStyle(style)
    style.font = Fonts.font
    @Suppress("UsePropertyAccessSyntax") setStyle(style)
    setFontScale(size/ Fonts.ORIGINAL_FONT_SIZE)
    return this
}

/** [pack][WidgetGroup.pack] a [WidgetGroup] if its [needsLayout][WidgetGroup.needsLayout] is true.
 *  @return the receiver to allow chaining
 */
fun WidgetGroup.packIfNeeded(): WidgetGroup {
    if (needsLayout()) pack()
    return this
}

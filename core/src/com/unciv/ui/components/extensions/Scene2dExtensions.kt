package com.unciv.ui.components.extensions

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.GdxKeyCodeFixes.DEL
import com.unciv.ui.components.extensions.GdxKeyCodeFixes.toString
import com.unciv.ui.components.extensions.GdxKeyCodeFixes.valueOf
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.ActorAttachments
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Collection of extension functions mostly for libGdx widgets
 */

private class RestorableTextButtonStyle(
    baseStyle: TextButtonStyle,
    val restoreStyle: ButtonStyle
) : TextButtonStyle(baseStyle)

//todo ButtonStyle *does* have a `disabled` Drawable, and Button ignores touches in disabled state anyway - all this is a wrong approach
/** Disable a [Button] by setting its [touchable][Button.touchable] and [style][Button.style] properties. */
fun Button.disable() {
    touchable = Touchable.disabled
    /** We want disabled buttons to "swallow" the click so that things behind aren't activated, so we don't change touchable
       The action won't be activated due to [ActorAttachments.activate] checking the isDisabled property */
    isDisabled = true
    val oldStyle = style
    if (oldStyle is RestorableTextButtonStyle) return
    val disabledStyle = BaseScreen.skin.get("disabled", TextButtonStyle::class.java)
    style = RestorableTextButtonStyle(disabledStyle, oldStyle)
}
/** Enable a [Button] by setting its [touchable][Button.touchable] and [style][Button.style] properties. */
fun Button.enable() {
    touchable = Touchable.enabled
    val oldStyle = style
    if (oldStyle is RestorableTextButtonStyle) {
        style = oldStyle.restoreStyle
    }
    isDisabled = false
}
/** Enable or disable a [Button] by setting its [touchable][Button.touchable] and [style][Button.style] properties,
 *  or returns the corresponding state.
 *
 *  Do not confuse with Gdx' builtin [isDisabled][Button.isDisabled] property,
 *  which is more appropriate to toggle On/Off buttons, while this one is good for 'click-to-do-something' buttons.
 */
var Button.isEnabled: Boolean
    get() = touchable == Touchable.enabled
    set(value) = if (value) enable() else disable()

fun colorFromHex(hexColor: Int): Color {
    val colorSize = 16 * 16 // 2 hexadecimal digits
    val r = hexColor / (colorSize * colorSize)
    val g = (hexColor / colorSize) % colorSize
    val b = hexColor % colorSize
    return colorFromRGB(r, g, b)
}

/** Create a new [Color] instance from [r]/[g]/[b] given as Integers in the range 0..255 */
fun colorFromRGB(r: Int, g: Int, b: Int) = Color(r / 255f, g / 255f, b / 255f, 1f)

/** Create a new [Color] instance from r/g/b given as Integers in the range 0..255 in the form of a 3-element List [rgb] */
fun colorFromRGB(rgb: List<Int>) = colorFromRGB(rgb[0], rgb[1], rgb[2])

/** Linearly interpolates between this [Color] and [BLACK][ImageGetter.CHARCOAL] by [t] which is in the range [[0,1]].
 * The result is returned as a new instance. */
fun Color.darken(t: Float): Color = Color(this).lerp(Color.BLACK, t)

/** Linearly interpolates between this [Color] and [WHITE][Color.WHITE] by [t] which is in the range [[0,1]],
 * preserving color ratio in RGB. The result is returned as a new instance. */
fun Color.brighten(t: Float): Color = Color(this).let {
    val lightness = maxOf(r, g, b)
    val targetRatio = (lightness + t * (1 - lightness)) / lightness
    return it.mul(targetRatio)
}

/** Ensures that the `lightness` value of the given color
 * in `HSL` scale is at least [minLightness].
 */
fun Color.coerceLightnessAtLeast(minLightness: Float): Color {
    /** see [Color.toHsv] implementation to understand this */
    val lightness = maxOf(r, g, b)
    return if (lightness < minLightness) {
        this.mul(minLightness / lightness)
    } else this
}

fun Actor.centerX(parent: Actor) { x = parent.width / 2 - width / 2 }
fun Actor.centerY(parent: Actor) { y = parent.height / 2 - height / 2 }
fun Actor.center(parent: Actor) { centerX(parent); centerY(parent) }

fun Actor.centerX(parent: Stage) { x = parent.width / 2 - width / 2 }
fun Actor.centerY(parent: Stage) { y = parent.height / 2 - height / 2 }
fun Actor.center(parent: Stage) { centerX(parent); centerY(parent) }


fun Actor.surroundWithCircle(
    size: Float,
    resizeActor: Boolean = true,
    color: Color = Color.WHITE,
    circleImageLocation: String = ImageGetter.circleLocation
): IconCircleGroup {
    return IconCircleGroup(size, this, resizeActor, color, circleImageLocation)
}

fun Actor.surroundWithThinCircle(color: Color=ImageGetter.CHARCOAL): IconCircleGroup = surroundWithCircle(width+2f, false, color)


fun Actor.addBorder(size: Float, color: Color, expandCell: Boolean = false): Table {
    val table = Table()
    table.pad(size)
    table.background = BaseScreen.skinStrings.getUiBackground("General/Border", tintColor = color)
    val cell = table.add(this)
    if (expandCell) cell.expand()
    cell.fill()
    table.pack()
    return table
}

/** Gets a parent of this actor that matches the [predicate], or null if none of its parents match the [predicate]. */
fun Actor.getAscendant(predicate: (Actor) -> Boolean): Actor? {
    var curParent = parent
    while (curParent != null) {
        if (predicate(curParent)) return curParent
        curParent = curParent.parent
    }
    return null
}

/** Gets the nearest parent of this actor that is a [T], or null if none of its parents is of that type. */
inline fun <reified T> Actor.getAscendant(): T? {
    return  getAscendant { it is T } as? T
}

/** The actors bounding box in stage coordinates */
val Actor.stageBoundingBox: Rectangle get() {
    val bottomLeft = localToStageCoordinates(Vector2.Zero.cpy())
    val topRight = localToStageCoordinates(Vector2(width, height))
    return Rectangle(
        bottomLeft.x,
        bottomLeft.y,
        topRight.x - bottomLeft.x,
        topRight.y - bottomLeft.y
    )
}

/** @return the area where this [Rectangle] overlaps with [other], or `null` if it doesn't overlap. */
fun Rectangle.getOverlap(other: Rectangle): Rectangle? {
    val overlapX = if (x > other.x) x else other.x

    val rightX = x + width
    val otherRightX = other.x + other.width
    val overlapWidth = (if (rightX < otherRightX) rightX else otherRightX) - overlapX

    val overlapY = if (y > other.y) y else other.y

    val topY = y + height
    val otherTopY = other.y + other.height
    val overlapHeight = (if (topY < otherTopY) topY else otherTopY) - overlapY

    val noOverlap = overlapWidth <= 0 || overlapHeight <= 0
    if (noOverlap) return null
    return Rectangle(
        overlapX,
        overlapY,
        overlapWidth,
        overlapHeight
    )
}

val Rectangle.top get() = y + height
val Rectangle.right get() = x + width

fun Group.addBorderAllowOpacity(size: Float, color: Color): Group {
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
private fun getSeparatorImage(color: Color) = Image(ImageGetter.getWhiteDotDrawable().tint(
    if (color.a != 0f) color else BaseScreen.skin.getColor("color") //0x334d80
))

/**
 * Create a horizontal separator as an empty Container with a colored background.
 * @param colSpan Optionally override [colspan][Cell.colspan] which defaults to the current column count.
 */
fun Table.addSeparator(color: Color = BaseScreen.skin.getColor("color"), colSpan: Int = 0, height: Float = 1f): Cell<Image> {
    if (!cells.isEmpty && !cells.last().isEndRow) row()
    val separator = getSeparatorImage(color)
    val cell = add(separator)
        .colspan(if (colSpan == 0) columns else colSpan)
        .height(height).fillX()
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

/**
 * When using Tables as touchables, much like buttons,
 * this function renders them disabled: faded and not touchable.
 */
fun Table.setEnabled(enabled: Boolean) {
    color.a = if (enabled) 1f else 0.5f
    touchable = if (enabled) Touchable.enabled else Touchable.disabled 
}

/** Alternative to [Table].[add][Table] that returns the Table instead of the new Cell to allow a different way of chaining */
fun <T : Actor> Table.addCell(actor: T): Table {
    add(actor)
    return this
}

/** Shortcut for [Cell].[pad][Cell.pad] with top=bottom and left=right */
fun <T : Actor> Cell<T>.pad(vertical: Float, horizontal: Float): Cell<T> {
    return pad(vertical, horizontal, vertical, horizontal)
}

fun <T> SelectBox<T>.setItems(newItems: Collection<T>){
    val array = Array<T>()
    newItems.forEach { array.add(it) }
    items = array
}

/** Sets both the width and height to [size] */
fun Image.setSize(size: Float) {
    setSize(size, size)
}

/** Proxy for [ScrollPane.scrollTo] using the [bounds][Actor.setBounds] of a given [actor] for its parameters */
fun ScrollPane.scrollTo(actor: Actor, center: Boolean = false) =
    scrollTo(actor.x, actor.y, actor.width, actor.height, center, center)

/** Translate a [String] and make a [TextButton] widget from it */
fun String.toTextButton(style: TextButtonStyle? = null, hideIcons: Boolean = false): TextButton {
    val text = this.tr(hideIcons)
    return if (style == null) TextButton(text, BaseScreen.skin) else TextButton(text, style)
}

/** Convert a texture path into an Image, make an ImageButton with a [tinted][overColor]
 *  hover version of the image from it, then [surroundWithCircle] it. */
fun String.toImageButton(iconSize: Float, circleSize: Float, circleColor: Color, overColor: Color): Group {
    val style = ImageButton.ImageButtonStyle()
    val image = ImageGetter.getDrawable(this)
    style.imageUp = image
    style.imageOver = image.tint(overColor)
    val button = ImageButton(style)
    button.setSize(iconSize, iconSize)
    return button.surroundWithCircle( circleSize, false, circleColor)
}

/** Return a "close" button, visually a circle with "x" icon that goes red on mouse-over.
 *
 *  For use e.g. in the top-right corner of screens such as CivilopediaScreen.
 *  Automatically binds the BACK key to the [action].
 */
fun getCloseButton(
    size: Float = 50f,
    iconSize: Float = size - 20f,
    circleColor: Color = BaseScreen.skinStrings.skinConfig.baseColor,
    overColor: Color = Color.RED,
    action: () -> Unit
): Group {
    val closeButton = "OtherIcons/Close".toImageButton(iconSize, size, circleColor, overColor)
    closeButton.onActivation(action)
    closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
    return closeButton
}

/**
 * Adds a white-circled (x) close button to [parent], positioned to the top right,
 * slightly shifted outwards.
 */
fun addRoundCloseButton(
    parent: Group,
    action: () -> Unit
): Group {
    val size = 30f
    val button = getCloseButton(size, size-15f, Color.CLEAR, Color.RED, action = action)
        .surroundWithCircle(size, false, BaseScreen.clearColor)
        .surroundWithCircle(size+4f, false, Color.WHITE)
    parent.addActor(button)
    button.setPosition(parent.width - button.width*3/4, parent.height - button.height*3/4)
    return button
}

/** Translate a [String] and make a [Label] widget from it */
fun String.toLabel() = Label(this.tr(), BaseScreen.skin)
/** Make a [Label] widget containing this [Int] as text */
fun Int.toLabel() = this.tr().toLabel()

/** Translate a [String] and make a [Label] widget from it with a specified font color and size */
fun String.toLabel(fontColor: Color = Color.WHITE,
                    fontSize: Int = Constants.defaultFontSize,
                    alignment: Int = Align.left,
                    hideIcons: Boolean = false): Label {
    // We don't want to use setFontSize and setFontColor because they set the font,
    //  which means we need to rebuild the font cache which means more memory allocation.
    var labelStyle = BaseScreen.skin.get(Label.LabelStyle::class.java)
    if (fontColor != Color.WHITE || fontSize != Constants.defaultFontSize) { // if we want the default we don't need to create another style
        labelStyle = Label.LabelStyle(labelStyle) // clone this to another
        labelStyle.fontColor = fontColor
        if (fontSize != Constants.defaultFontSize) labelStyle.font = Fonts.font
    }
    val translatedText = this.tr(hideIcons) 
    return Label(translatedText, labelStyle).apply {
        setFontScale(fontSize / Fonts.ORIGINAL_FONT_SIZE)
        setAlignment(alignment)
    }
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
        imageCell.padRight(Constants.defaultFontSize / 2.0f)
    }

/** Sets the [font color][Label.LabelStyle.fontColor] on a [Label] and returns it to allow chaining */
fun Label.setFontColor(color: Color): Label {
    style = Label.LabelStyle(style).apply { fontColor=color }
    return this
}

/** Sets the font size on a [Label] and returns it to allow chaining */
fun Label.setFontSize(size: Int): Label {
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

/** @return `true` if the screen is narrower than 4:3 landscape */
fun Stage.isNarrowerThan4to3() = viewport.screenHeight * 4 > viewport.screenWidth * 3

/** Wraps and returns an image in a [Group] of a given size*/
fun Image.toGroup(size: Float): Group {
    return Group().apply {
        setSize(size, size)
        this@toGroup.setSize(size, size)
        this@toGroup.center(this)
        this@toGroup.setOrigin(Align.center)
        addActor(this@toGroup) }
}

/** Adds actor to a [Group] and centers it */
fun Group.addToCenter(actor: Actor) {
    addActor(actor)
    actor.center(this)
}

/**
 *  These methods deal with a mistake in Gdx.Input.Keys, where DEL is defined as the keycode actually
 *  produced by the physical Backspace key, while the physical Del key fires the keycode Gdx lists as
 *  FORWARD_DEL. Neither valueOf("Del") and valueOf("Backspace") work as expected.
 *
 *  | Identifier | KeyCode | Physical key | toString() | valueOf(name.TitleCase) | valueOf(toString) |
 *  | ---- |:----:|:----:|:----:|:----:|:----:|
 *  | DEL | 67 | Backspace | Delete | -1 | 67 |
 *  | BACKSPACE | 67 | Backspace | Delete | -1 | 67 |
 *  | FORWARD_DEL | 112 | Del | Forward Delete | -1 | 112 |
 *
 *  This acts as proxy, you replace [Input.Keys] by [GdxKeyCodeFixes] and get sensible [DEL], [toString] and [valueOf].
 *  Differences in behaviour: toString will return an empty string for un-mapped keycodes and UNKNOWN
 *  instead of `null` or "Unknown" respectively,
 *  valueOf will return UNKNOWN for un-mapped names or "" instead of -1.
 */
@Suppress("GDX_KEYS_BUG", "MemberVisibilityCanBePrivate")
object GdxKeyCodeFixes {

    const val DEL = Input.Keys.FORWARD_DEL
    const val BACKSPACE = Input.Keys.BACKSPACE
    const val UNKNOWN = Input.Keys.UNKNOWN

    fun toString(keyCode: Int): String = when(keyCode) {
        UNKNOWN -> ""
        DEL -> "Del"  // Gdx would name this "Forward Delete"
        BACKSPACE -> "Backspace"  // Gdx would name this "Delete"
        else -> Input.Keys.toString(keyCode)
            ?: ""
    }

    fun valueOf(name: String): Int = when (name) {
        "" -> UNKNOWN
        "Del" -> DEL
        "Backspace" -> BACKSPACE
        else -> {
            val code = Input.Keys.valueOf(name)
            if (code == -1) UNKNOWN else code
        }
    }
}

fun Input.isShiftKeyPressed() = isKeyPressed(Input.Keys.SHIFT_LEFT) || isKeyPressed(Input.Keys.SHIFT_RIGHT)
fun Input.isControlKeyPressed() = isKeyPressed(Input.Keys.CONTROL_LEFT) || isKeyPressed(Input.Keys.CONTROL_RIGHT)
fun Input.isAltKeyPressed() = isKeyPressed(Input.Keys.ALT_LEFT) || isKeyPressed(Input.Keys.ALT_RIGHT)
fun Input.areSecretKeysPressed() = isKeyPressed(Input.Keys.SHIFT_RIGHT) &&
        (isKeyPressed(Input.Keys.CONTROL_RIGHT) || isKeyPressed(Input.Keys.ALT_RIGHT))

/** Sets first row cell's minWidth to the max of the widths of that column over all given tables
 *
 * Notes:
 * - This aligns columns only if the tables are arranged vertically with equal X coordinates.
 * - first table determines columns processed, all others must have at least the same column count.
 * - Tables are left as needsLayout==true, so while equal width is ensured, you may have to pack if you want to see the value before this is rendered.
 * - Note: The receiver <Group> isn't actually needed except to make sure the arguments are descendants.
 */
fun equalizeColumns(vararg tables: Table) {
    for (table in tables) {
        table.packIfNeeded()
    }
    val columns = tables.first().columns
    check(tables.all { it.columns >= columns }) {
        "IPageExtensions.equalizeColumns needs all tables to have at least the same number of columns as the first one"
    }

    val widths = (0 until columns)
        .mapTo(ArrayList(columns)) { column ->
            tables.maxOf { it.getColumnWidth(column) }
        }
    for (table in tables) {
        for (column in 0 until columns)
            table.cells[column].run {
                if (actor == null)
                // Empty cells ignore minWidth, so just doing Table.add() for an empty cell in the top row will break this. Fix!
                    setActor<Label>("".toLabel())
                else if (Align.isCenterHorizontal(align)) (actor as? Label)?.run {
                    // minWidth acts like fillX, so Labels will fill and then left-align by default. Fix!
                    if (!Align.isCenterHorizontal(labelAlign))
                        setAlignment(Align.center)
                }
                minWidth(widths[column] - padLeft - padRight)
            }
        table.invalidate()
    }
}

/** Retrieve a texture Pixmap without reload or ownership transfer, useable for read operations only.
 *
 *  (FileTextureData.consumePixmap forces a reload of the entire file - inefficient if we only want to look at pixel values) */
fun TextureData.getReadonlyPixmap(): Pixmap {
    if (!isPrepared) prepare()
    if (this is PixmapTextureData) return consumePixmap()
    if (this !is FileTextureData) throw TypeCastException("getReadonlyPixmap only works on file or pixmap based textures")
    val field = FileTextureData::class.java.getDeclaredField("pixmap")
    field.isAccessible = true
    return field.get(this) as Pixmap
}

fun <T: Actor>Stack.addInTable(actor: T): Cell<T> {
    val table = Table()
    add(table)
    return table.add(actor).grow()
}

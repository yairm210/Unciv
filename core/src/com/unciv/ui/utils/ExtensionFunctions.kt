package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.crashhandling.CrashScreen
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.audio.Sounds
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

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

fun Actor.centerX(parent: Actor){ x = parent.width/2 - width/2 }
fun Actor.centerY(parent: Actor){ y = parent.height/2- height/2}
fun Actor.center(parent: Actor){ centerX(parent); centerY(parent)}

fun Actor.centerX(parent: Stage){ x = parent.width/2 - width/2 }
fun Actor.centerY(parent: Stage){ y = parent.height/2- height/2}
fun Actor.center(parent: Stage){ centerX(parent); centerY(parent)}

/** same as [onClick], but sends the [InputEvent] and coordinates along */
fun Actor.onClickEvent(sound: UncivSound = UncivSound.Click, function: (event: InputEvent?, x: Float, y: Float) -> Unit) {
    this.addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            crashHandlingThread(name = "Sound") { Sounds.play(sound) }
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

/** Gets a clone of an [ArrayList] with an additional item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous arrayList can keep using it because it hasn't changed
 */
fun <T> ArrayList<T>.withItem(item:T): ArrayList<T> {
    val newArrayList = ArrayList(this)
    newArrayList.add(item)
    return newArrayList
}

/** Gets a clone of a [HashSet] with an additional item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous hashSet can keep using it because it hasn't changed
 */
fun <T> HashSet<T>.withItem(item:T): HashSet<T> {
    val newHashSet = HashSet(this)
    newHashSet.add(item)
    return newHashSet
}

/** Gets a clone of an [ArrayList] without a given item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous arrayList can keep using it because it hasn't changed
 */
fun <T> ArrayList<T>.withoutItem(item:T): ArrayList<T> {
    val newArrayList = ArrayList(this)
    newArrayList.remove(item)
    return newArrayList
}

/** Gets a clone of a [HashSet] without a given item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous hashSet can keep using it because it hasn't changed
 */
fun <T> HashSet<T>.withoutItem(item:T): HashSet<T> {
    val newHashSet = HashSet(this)
    newHashSet.remove(item)
    return newHashSet
}

/** Translate a percentage number - e.g. 25 - to the multiplication value - e.g. 1.25f */
fun String.toPercent() = toFloat().toPercent()

/** Translate a percentage number - e.g. 25 - to the multiplication value - e.g. 1.25f */
fun Int.toPercent() = toFloat().toPercent()

/** Translate a percentage number - e.g. 25 - to the multiplication value - e.g. 1.25f */
fun Float.toPercent() = 1 + this/100

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

/** Get one random element of a given List.
 *
 * The probability for each element is proportional to the value of its corresponding element in the [weights] List.
 */
fun <T> List<T>.randomWeighted(weights: List<Float>, random: Random = Random): T {
    if (this.isEmpty()) throw NoSuchElementException("Empty list.")
    if (this.size != weights.size) throw UnsupportedOperationException("Weights size does not match this list size.")

    val totalWeight = weights.sum()
    val randDouble = random.nextDouble()
    var sum = 0f

    for (i in weights.indices) {
        sum += weights[i] / totalWeight
        if (randDouble <= sum)
            return this[i]
    }
    return this.last()
}

/**
 * Standardize date formatting so dates are presented in a consistent style and all decisions
 * to change date handling are encapsulated here
 */
object UncivDateFormat {
    private val standardFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /** Format a date to ISO format with minutes */
    fun Date.formatDate(): String = standardFormat.format(this)
    // Previously also used:
    //val updateString = "{Updated}: " +DateFormat.getDateInstance(DateFormat.SHORT).format(date)

    // Everything under java.time is from Java 8 onwards, meaning older phones that use Java 7 won't be able to handle it :/
    // So we're forced to use ancient Java 6 classes instead of the newer and nicer LocalDateTime.parse :(
    // Direct solution from https://stackoverflow.com/questions/2201925/converting-iso-8601-compliant-string-to-java-util-date

    @Suppress("SpellCheckingInspection")
    private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /** Parse an UTC date as passed by online API's
     * example: `"2021-04-11T14:43:33Z".parseDate()`
     */
    fun String.parseDate(): Date = utcFormat.parse(this)
}


/**
 * Returns a wrapped version of a function that safely crashes the game to [CrashScreen] if an exception or error is thrown.
 *
 * In case an exception or error is thrown, the return will be null. Therefore the return type is always nullable.
 *
 * The game loop, threading, and event systems already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 *
 * Therefore, it usually shouldn't be necessary to manually use this. See the note at the top of [CrashScreen].kt for details.
 *
 * @param postToMainThread Whether the [CrashScreen] should be opened by posting a runnable to the main thread, instead of directly. Set this to true if the function is going to run on any thread other than the main loop.
 * @return Result from the function, or null if an exception is thrown.
 * */
fun <R> (() -> R).wrapCrashHandling(
    postToMainThread: Boolean = false
): () -> R?
    = {
        try {
            this()
        } catch (e: Throwable) {
            if (postToMainThread) {
                Gdx.app.postRunnable {
                    UncivGame.Current.setScreen(CrashScreen(e))
                }
            } else UncivGame.Current.setScreen(CrashScreen(e))
            null
        }
    }

/**
 * Returns a wrapped a version of a Unit-returning function which safely crashes the game to [CrashScreen] if an exception or error is thrown.
 *
 * The game loop, threading, and event systems already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 *
 * Therefore, it usually shouldn't be necessary to manually use this. See the note at the top of [CrashScreen].kt for details.
 *
 * @param postToMainThread Whether the [CrashScreen] should be opened by posting a runnable to the main thread, instead of directly. Set this to true if the function is going to run on any thread other than the main loop.
 * */
fun (() -> Unit).wrapCrashHandlingUnit(
    postToMainThread: Boolean = false
): () -> Unit {
    val wrappedReturning = this.wrapCrashHandling(postToMainThread)
    // Don't instantiate a new lambda every time the return get called.
    return { wrappedReturning() ?: Unit }
}

/** For filters containing '{', apply the [predicate] to each part inside "{}" and aggregate using [operation];
 *  otherwise return `null` for Elvis chaining of the individual filter. */
fun <T> String.filterCompositeLogic(predicate: (String) -> T?, operation: (T, T) -> T): T? {
    val elements: List<T> = removePrefix("{").removeSuffix("}").split("} {")
        .mapNotNull(predicate)
    if (elements.isEmpty()) return null
    return elements.reduce(operation)
}
/** If a filter string contains '{', apply the [predicate] to each part inside "{}" then 'and' (`&&`) them together;
 *  otherwise return `null` for Elvis chaining of the individual filter. */
fun String.filterAndLogic(predicate: (String) -> Boolean): Boolean? =
    if (contains('{')) filterCompositeLogic(predicate) { a, b -> a && b } else null


/** Convert a [resource name][this] into "Consumes [amount] $resource" string (untranslated, using separate templates for 1 and other amounts) */
//todo some day... remove and use just one translatable where this is called
fun String.getConsumesAmountString(amount: Int) = (
            if (amount == 1) "Consumes 1 [$this]"
            else "Consumes [$amount] [$this]"
        )

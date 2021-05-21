package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Collection of extension functions mostly for libGdx widgets
 */

/** Disable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties. */
fun Button.disable(){
    touchable= Touchable.disabled
    color= Color.GRAY
}
/** Enable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties. */
fun Button.enable() {
    color = Color.WHITE
    touchable = Touchable.enabled
}
/** Enable or disable a [Button] by setting its [touchable][Button.touchable] and [color][Button.color] properties,
 *  or returns the corresponding state. *
 *
 *  Do not confuse with Gdx' builtin [isDisabled][Button.isDisabled] property,
 *  which is more appropriate to toggle On/Off buttons, while this one is good for 'click-to-do-something' buttons.
 */
var Button.isEnabled: Boolean
    //Todo: Use in PromotionPickerScreen, TradeTable, WorldScreen.updateNextTurnButton
    get() = touchable == Touchable.enabled
    set(value) = if (value) enable() else disable()

fun colorFromRGB(r: Int, g: Int, b: Int) = Color(r / 255f, g / 255f, b / 255f, 1f)
fun colorFromRGB(rgb:List<Int>) = colorFromRGB(rgb[0], rgb[1], rgb[2])
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
            thread(name = "Sound") { Sounds.play(sound) }
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

fun Actor.onChange(function: () -> Unit): Actor {
    this.addListener(object : ChangeListener() {
        override fun changed(event: ChangeEvent?, actor: Actor?) {
            function()
        }
    })
    return this
}

fun Actor.surroundWithCircle(size: Float, resizeActor: Boolean = true, color: Color = Color.WHITE): IconCircleGroup {
    return IconCircleGroup(size, this, resizeActor, color)
}

fun Actor.addBorder(size:Float, color: Color, expandCell:Boolean=false): Table {
    val table = Table()
    table.pad(size)
    table.background = ImageGetter.getBackground(color)
    val cell = table.add(this)
    if (expandCell) cell.expand()
    cell.fill()
    table.pack()
    return table
}

fun Table.addSeparator(): Cell<Image> {
    row()
    val image = ImageGetter.getWhiteDot()
    val cell = add(image).colspan(columns).height(2f).fill()
    row()
    return cell
}

fun Table.addSeparatorVertical(): Cell<Image> {
    val image = ImageGetter.getWhiteDot()
    val cell = add(image).width(2f).fillY()
    return cell
}

fun <T : Actor> Table.addCell(actor: T): Table {
    add(actor)
    return this
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

/** Translate a [String] and make a [TextButton] widget from it */
fun String.toTextButton() = TextButton(this.tr(), CameraStageBaseScreen.skin)

/** Translate a [String] and make a [Label] widget from it */
fun String.toLabel() = Label(this.tr(), CameraStageBaseScreen.skin)
/** Make a [Label] widget containing this [Int] as text */
fun Int.toLabel() = this.toString().toLabel()

/** Translate a [String] and make a [Label] widget from it with a specified font color and size */
fun String.toLabel(fontColor: Color = Color.WHITE, fontSize:Int=18): Label {
    // We don't want to use setFontSize and setFontColor because they set the font,
    //  which means we need to rebuild the font cache which means more memory allocation.
    var labelStyle = CameraStageBaseScreen.skin.get(Label.LabelStyle::class.java)
    if(fontColor!= Color.WHITE || fontSize!=18) { // if we want the default we don't need to create another style
        labelStyle = Label.LabelStyle(labelStyle) // clone this to another
        labelStyle.fontColor = fontColor
        if (fontSize != 18) labelStyle.font = Fonts.font
    }
    return Label(this.tr(), labelStyle).apply { setFontScale(fontSize/Fonts.ORIGINAL_FONT_SIZE) }
}

fun Label.setFontColor(color: Color): Label { style= Label.LabelStyle(style).apply { fontColor=color }; return this }

fun Label.setFontSize(size:Int): Label {
    style = Label.LabelStyle(style)
    style.font = Fonts.font
    style = style // because we need it to call the SetStyle function. Yuk, I know.
    return this.apply { setFontScale(size/ Fonts.ORIGINAL_FONT_SIZE) } // for chaining
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

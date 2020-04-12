package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.absoluteValue
import kotlin.math.sign

open class PickerScreen : CameraStageBaseScreen() {

    internal var closeButton: TextButton = TextButton("Close".tr(), skin)
    protected var descriptionLabel: Label
    protected var rightSideGroup = VerticalGroup()
    protected var rightSideButton: TextButton
    private var screenSplit = 0.85f
    protected var topTable: Table
    var bottomTable:Table = Table()
    internal var splitPane: SplitPane
    protected var scrollPane: ScrollPane
    private val closeAction = {
        game.setWorldScreen()
        dispose()
    }
    private var keyListener: InputListener
    private data class KeyPressDispatcherEntry (val actions: MutableList<() -> Unit> = mutableListOf(), var next: Int = 0 )
    private val keyPressDispatcher: HashMap<Char,KeyPressDispatcherEntry>
    private val markedCharRegex = Regex("""_([\w])_""")
    private data class ArrowActionEntry (val action: ()->Unit, val x: Int, val y: Int, val index: Int)
    private var arrowIndex = -1
    private val arrowActionList = ArrayList<ArrowActionEntry>()
    private val arrowListIsCircular: Boolean by lazy {
        arrowActionList.all { it.x == 0 && it.y == it.index }
    }
    var arrowKeyStraightLineBias = 2        // added to 'distance' if it's not in a direct horizontal or vertical line
    var arrowKeyWrongQuadrantBias = 12      // distance multiplicator if incorrect general direction
    var arrowKeyThreshold = 12              // cutoff - virtual weighted distances greater than this are not considered at all

    init {
        bottomTable.add(closeButton).pad(10f)

        descriptionLabel = "".toLabel()
        descriptionLabel.setWrap(true)
        val labelScroll = ScrollPane(descriptionLabel)
        bottomTable.add(labelScroll).pad(5f).fill().expand()

        rightSideButton = TextButton("", skin)
        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        bottomTable.add(rightSideGroup).pad(10f).right()
        bottomTable.height = stage.height * (1 - screenSplit)

        topTable = Table()
        scrollPane = ScrollPane(topTable)

        scrollPane.setSize(stage.width, stage.height * screenSplit)

        splitPane = SplitPane(scrollPane, bottomTable, true, skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)

        keyListener = getKeyboardListener()
        keyPressDispatcher = hashMapOf()
        stage.addListener(keyListener)
    }

    override fun dispose() {
        @Suppress ("UNNECESSARY_SAFE_CALL")
            stage?.removeListener(keyListener)    // Compiler says cannot ever be null but ?. is still necessary
        super.dispose()
    }

    private fun getKeyboardListener(): InputListener = object : InputListener() {
        override fun keyTyped(event: InputEvent?, character: Char): Boolean {
            //println("PickerScreen keyTyped ('$character' ${character.toInt()})")
            return keyPressed(character.toLowerCase()) || super.keyTyped(event, character)
        }

        override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
            return when (keycode) {
                Input.Keys.UP -> arrowPressed (0,-1)
                Input.Keys.DOWN -> arrowPressed (0,1)
                Input.Keys.LEFT -> arrowPressed (-1,0)
                Input.Keys.RIGHT -> arrowPressed (1,0)
                else -> super.keyUp(event, keycode)
            }
        }
    }

    private fun keyPressed (char: Char): Boolean {
        if (char !in keyPressDispatcher) return false
        val keyEntry = keyPressDispatcher[char]!!
        if (keyEntry.actions.isNotEmpty()) {
            // When there are more than one handler for a key, do a round-robin
            keyEntry.actions[keyEntry.next].invoke()
            keyEntry.next = (keyEntry.next + 1) %  keyEntry.actions.size
        }
        // All other key handlers get their round-robin reset
        keyPressDispatcher.values.forEach {
            if (it !== keyEntry) it.next = 0
        }
        return true
    }

    private fun arrowPressed(directionX: Int, directionY: Int): Boolean {
        if (arrowActionList.size == 0) return false
        var newIndex = arrowIndex
        if (arrowIndex < 0) {
            newIndex = if (arrowListIsCircular && directionY == -1) arrowActionList.size - 1 else 0
        } else if (arrowListIsCircular) {
            if (directionY!=0) {
                newIndex = (arrowIndex + directionY + arrowActionList.size) % arrowActionList.size
            }
        } else {
            val currentX = arrowActionList[arrowIndex].x
            val currentY = arrowActionList[arrowIndex].y
            var bestDistance = arrowKeyThreshold
            arrowActionList.forEach {
                val deltaX = (it.x - currentX).absoluteValue
                val deltaY = (it.y - currentY).absoluteValue
                if (deltaX > 0 || deltaY > 0) {
                    // In which general direction is this? Ensure the diagonals count for both possible directions
                    val vX = if (deltaX > deltaY || deltaX == deltaY && directionX != 0) (it.x - currentX).sign else 0
                    val vY = if (deltaY > deltaX || deltaX == deltaY && directionY != 0) (it.y - currentY).sign else 0
                    // more of a 'closest to user expectation' than an actual distance
                    val distance =
                            deltaX * (if (vX == directionX) 1 else arrowKeyWrongQuadrantBias) +     // malus for wrong quadrant
                            (if (directionX == 0 && deltaX == 0) 0 else arrowKeyStraightLineBias) + // bonus for straight vertical
                            deltaY * (if (vY == directionY) 1 else arrowKeyWrongQuadrantBias) +     // malus for wrong quadrant
                            (if (directionY == 0 && deltaY == 0) 0 else arrowKeyStraightLineBias)   // bonus for straight horizontal
                    if (distance < bestDistance) {
                        bestDistance = distance
                        newIndex = it.index
                    }
                }
            }
        }
        if (newIndex == arrowIndex) return false
        arrowIndex = newIndex
        arrowActionList[newIndex].action()
        return true
    }

    fun setDefaultCloseAction() = setCloseAction(closeAction)
    fun setCloseAction(action: () -> Unit) {
        closeButton.onClick(action)
        registerKeyHandler (Constants.asciiEscape, action)
    }

    // Label the accept button and associate an accept action (right button click or enter key)
    fun setAcceptButtonAction (buttonText: String, sound: UncivSound, action: () -> Unit) {
        rightSideButton.setText (buttonText.tr())
        rightSideButton.onClick (sound, action)
        registerKeyHandler('\r', { if (rightSideButton.touchable == Touchable.enabled) action.invoke() })
    }
    fun setAcceptButtonAction (buttonText: String, action: () -> Unit) {
        setAcceptButtonAction (buttonText, UncivSound.Click, action)
    }

    protected fun pick(rightButtonText: String) {
        if(UncivGame.Current.worldScreen.isPlayersTurn) rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }

    // Forget registered key handlers except accept and close
    fun clearKeyHandlers() {
        val delKeys = keyPressDispatcher.keys.filter { it!='\r' && it!=Constants.asciiEscape }
        delKeys.forEach { keyPressDispatcher.remove(it) }
    }

    // Register a new key handler for a specific character
    // One key can have multiple actions associated, and new actions can go either to the end or the beginning of the list.
    // The listener will 'play' these actions (when the same key is pressed repeatedly)
    // in a round-robin fashion beginning at the head of the list. All other key handlers will be
    // reset to the beginnings of their respective lists.
    fun registerKeyHandler(char: Char, action: () -> Unit, priority: Boolean = false) {
        val charLower = char.toLowerCase()
        when {
            charLower !in keyPressDispatcher ->
                keyPressDispatcher[charLower] = KeyPressDispatcherEntry(mutableListOf(action))
            priority ->
                keyPressDispatcher[charLower]!!.actions.add(0, action)
            else ->
                keyPressDispatcher[charLower]!!.actions += action
        }
    }

    // Register a key handler for a selectable item
    // Input: label - e.g. the string used as labeling a choice.
    // Passing a label text directly is just a suggestion - only one character is extracted.
    // If the string contains a pattern like _X_ then the handler is registered for key "X"
    // with priority (it goes to the head of the action list for that key).
    // Otherwise, if the string begins with a letter or digit, that character is registered but
    // goes to the end of the action list.
    // Actions registered this way will also be selectable using the Up/Down keys - so
    // it is assumed the supplied action will give a visual feedback.
    // If this doesn't fit your Picker, use setKeyHandler(Char) only.
    // The overload accepting x and y coordinates enables 2D navigation using all four arrow
    // keys, the x/y pairs need not have any specific meaning except expressing a spatial relationship
    // so the user's concept of 'direction' associated with the arrows doesn't get disapponted.
    fun registerKeyHandler(label: String, action: () -> Unit) =
            registerKeyHandler (label, action, 0, arrowActionList.size, false)
    fun registerKeyHandler(label: String, action: () -> Unit, x: Int, y: Int) =
            registerKeyHandler (label, action, x, y, false)
    fun registerKeyHandler(label: String, action: () -> Unit, x: Int, y: Int, selected: Boolean) {
        val matchResult = markedCharRegex.find(label)
        when {
            matchResult != null ->
                registerKeyHandler (matchResult.groups[1]!!.value[0], action, priority = true)
            label[0].isLetterOrDigit() ->
                registerKeyHandler (label[0], action)
        }
        if (selected) arrowIndex = arrowActionList.size
        arrowActionList += ArrowActionEntry (action, x, y, arrowActionList.size)
    }

}
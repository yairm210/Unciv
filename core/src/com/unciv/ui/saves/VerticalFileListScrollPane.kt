package com.unciv.ui.saves

import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameSaver
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyPressDispatcher
import com.unciv.ui.utils.onClick

//todo key auto-repeat for navigation keys?

/** A widget holding TextButtons vertically in a Table contained in a ScrollPane, with methods to
 *  hold file names and FileHandle's in those buttons. Used to display existing saves in the Load and Save game dialogs.
 *
 *  @param keyPressDispatcher optionally pass in a [BaseScreen]'s [keyPressDispatcher][BaseScreen.keyPressDispatcher] to allow keyboard navigation.
 *  @param existingSavesTable exists here for coder convenience. No need to touch.
 */
class VerticalFileListScrollPane(
    keyPressDispatcher: KeyPressDispatcher?,
    private val existingSavesTable: Table = Table()
) : AutoScrollPane(existingSavesTable) {

    private var previousSelection: TextButton? = null

    private var onChangeListener: ((FileHandle) -> Unit)? = null

    init {
        if (keyPressDispatcher != null) {
            keyPressDispatcher[Input.Keys.UP] = { onArrowKey(-1) }
            keyPressDispatcher[Input.Keys.DOWN] = { onArrowKey(1) }
            keyPressDispatcher[Input.Keys.PAGE_UP] = { onPageKey(-1) }
            keyPressDispatcher[Input.Keys.PAGE_DOWN] = { onPageKey(1) }
        }
    }

    fun onChange(action: (FileHandle) -> Unit) {
        onChangeListener = action
    }

    /** repopulate with existing saved games */
    fun updateSaveGames(gameSaver: GameSaver, showAutosaves: Boolean) {
        update(gameSaver.getSaves(showAutosaves)
            .sortedByDescending { it.lastModified() })
    }

    /** repopulate from a FileHandle Sequence - for other sources than saved games */
    fun update(files: Sequence<FileHandle>) {
        existingSavesTable.clear()
        val loadImage = ImageGetter.getImage("OtherIcons/Load")
        loadImage.setSize(50f, 50f) // So the origin sets correctly
        loadImage.setOrigin(Align.center)
        val loadAnimation = Actions.repeat(Int.MAX_VALUE, Actions.rotateBy(360f, 2f))
        loadImage.addAction(loadAnimation)
        existingSavesTable.add(loadImage).size(50f).center()

        // Apparently, even just getting the list of saves can cause ANRs -
        // not sure how many saves these guys had but Google Play reports this to have happened hundreds of times
        launchCrashHandling("GetSaves") {
            // .toList() materializes the result of the sequence
            val saves = files.toList()

            postCrashHandlingRunnable {
                loadAnimation.reset()
                existingSavesTable.clear()
                for (saveGameFile in saves) {
                    val textButton = TextButton(saveGameFile.name(), BaseScreen.skin)
                    textButton.userObject = saveGameFile
                    textButton.onClick {
                        selectExistingSave(textButton)
                    }
                    existingSavesTable.add(textButton).pad(5f).row()
                }
            }
        }
    }

    private fun selectExistingSave(textButton: TextButton) {
        previousSelection?.color = Color.WHITE
        textButton.color = Color.GREEN
        previousSelection = textButton

        val saveGameFile = textButton.userObject as? FileHandle ?: return
        onChangeListener?.invoke(saveGameFile)
    }

    //region Keyboard scrolling

    // Helpers to simplify Scroll positioning - ScrollPane.scrollY goes down, normal Gdx Y goes up
    // These functions all operate in the scrollY 'coordinate system'
    private fun Table.getVerticalSpan(button: TextButton): ClosedFloatingPointRange<Float> {
        val invertedY = height - button.y
        return (invertedY - button.height)..invertedY
    }
    private fun getVerticalSpan() = scrollY..(scrollY + height)
    private fun Table.getButtonAt(y: Float) = cells[getRow(height - y)].actor as TextButton

    private fun onArrowKey(direction: Int) {
        val rowIndex = if (previousSelection == null)
            if (direction == 1) -1 else 0
        else existingSavesTable.getCell(previousSelection).row
        val newRow = (rowIndex + direction).let {
            if (it < 0) existingSavesTable.rows - 1
            else if (it >= existingSavesTable.rows) 0
            else it
        }
        val button = existingSavesTable.cells[newRow].actor as TextButton
        selectExistingSave(button)

        // Make ScrollPane follow the selection
        val buttonSpan = existingSavesTable.getVerticalSpan(button)
        val scrollSpan = getVerticalSpan()
        if (buttonSpan.start < scrollSpan.start)
            scrollY = buttonSpan.start
        if (buttonSpan.endInclusive > scrollSpan.endInclusive)
            scrollY = buttonSpan.endInclusive - height
    }

    private fun onPageKey(direction: Int) {
        scrollY += (height - 60f) * direction  // ScrollPane does the clamping to 0..maxY
        val buttonHeight = previousSelection?.height ?: return
        val buttonSpan = existingSavesTable.getVerticalSpan(previousSelection!!)
        val scrollSpan = getVerticalSpan()
        val newButtonY = if (buttonSpan.start < scrollSpan.start)
            scrollSpan.start + buttonHeight
        else if (buttonSpan.endInclusive > scrollSpan.endInclusive)
            scrollSpan.endInclusive - buttonHeight
        else return
        selectExistingSave(existingSavesTable.getButtonAt(newButtonY))
    }

    //endregion
}

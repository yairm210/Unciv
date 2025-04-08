package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.files.UncivFiles
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

//todo key auto-repeat for navigation keys?

/** A widget holding TextButtons vertically in a Table contained in a ScrollPane, with methods to
 *  hold file names and FileHandle's in those buttons. Used to display existing saves in the Load and Save game dialogs.
 *
 *  @param existingSavesTable exists here for coder convenience. No need to touch.
 */
class VerticalFileListScrollPane(
    private val existingSavesTable: Table = Table()
) : AutoScrollPane(existingSavesTable) {

    private val savesPerButton = mutableMapOf<TextButton, FileHandle>()

    private var previousSelection: TextButton? = null

    private var onChangeListener: ((FileHandle) -> Unit)? = null
    private var onDoubleClickListener: ((FileHandle) -> Unit)? = null

    init {
        keyShortcuts.add(Input.Keys.UP) { onArrowKey(-1) }
        keyShortcuts.add(Input.Keys.DOWN) { onArrowKey(1) }
        keyShortcuts.add(Input.Keys.PAGE_UP) { onPageKey(-1) }
        keyShortcuts.add(Input.Keys.PAGE_DOWN) { onPageKey(1) }
        keyShortcuts.add(Input.Keys.HOME) { onHomeEndKey(0) }
        keyShortcuts.add(Input.Keys.END) { onHomeEndKey(1) }
    }

    fun onChange(action: (FileHandle) -> Unit) {
        onChangeListener = action
    }
    fun onDoubleClick(action: (FileHandle) -> Unit) {
        onDoubleClickListener = action
    }

    /** repopulate with existing saved games */
    fun updateSaveGames(files: UncivFiles, showAutosaves: Boolean) {
        update(files.getSaves(showAutosaves)
            .sortedByDescending { it.lastModified() })
    }

    /** repopulate from a FileHandle Sequence - for other sources than saved games */
    fun update(files: Sequence<FileHandle>) {
        existingSavesTable.clear()
        previousSelection = null
        val loadingImage = LoadingImage(62f, LoadingImage.Style(innerSizeFactor = 0.8f))
        existingSavesTable.add(loadingImage)
        loadingImage.show()

        // Apparently, even just getting the list of saves can cause ANRs -
        // not sure how many saves these guys had but Google Play reports this to have happened hundreds of times
        Concurrency.run("GetSaves") {
            // .toList() materializes the result of the sequence
            val saves = files.toList()

            launchOnGLThread {
                loadingImage.hide()
                existingSavesTable.clear()
                savesPerButton.clear()
                for (saveGameFile in saves) {
                    val textButton = TextButton(saveGameFile.name(), BaseScreen.skin)
                    savesPerButton[textButton] = saveGameFile
                    textButton.onClick {
                        selectExistingSave(textButton)
                    }
                    textButton.onDoubleClick {
                        selectExistingSave(textButton)
                        onDoubleClickListener?.invoke(saveGameFile)
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

        val saveGameFile = savesPerButton[textButton] ?: return
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
        if (existingSavesTable.rows == 0) return
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

    private fun onHomeEndKey(direction: Int) {
        scrollY = direction * maxY
        if (existingSavesTable.rows == 0) return
        val row = (existingSavesTable.rows - 1) * direction
        selectExistingSave(existingSavesTable.cells[row].actor as TextButton)
    }
    //endregion
}

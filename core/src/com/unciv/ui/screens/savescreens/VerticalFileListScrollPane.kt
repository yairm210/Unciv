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
import kotlin.math.abs

//todo key auto-repeat for navigation keys?

/** A widget holding TextButtons vertically in a Table contained in a ScrollPane, with methods to
 *  hold file names and FileHandle's in those buttons. Used to display existing saves in the Load and Save game dialogs.
 *
 *  @param existingSavesTable exists here for coder convenience. No need to touch.
 */
class VerticalFileListScrollPane(
    private val existingSavesTable: Table = Table()
) : AutoScrollPane(existingSavesTable) {

    private class FileHandleButton(val file: FileHandle, val index: Int) : TextButton(file.name(), BaseScreen.skin)

    private val buttonIndex = ArrayList<FileHandleButton>()

    private var selectedIndex = -1
    private var selectedButton: FileHandleButton? = null

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
        selectedIndex = -1
        selectedButton = null
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
                buttonIndex.clear()
                buttonIndex.ensureCapacity(saves.size)
                for ((index, saveGameFile) in saves.withIndex()) {
                    val button = FileHandleButton(saveGameFile, index)
                    buttonIndex.add(button)
                    button.onClick {
                        selectExistingSave(index)
                    }
                    button.onDoubleClick {
                        selectExistingSave(index)
                        onDoubleClickListener?.invoke(saveGameFile)
                    }
                    existingSavesTable.add(button).pad(5f).row()
                }
            }
        }
    }

    private fun selectExistingSave(index: Int) = selectExistingSave(index, buttonIndex[index])
    private fun selectExistingSave(button: FileHandleButton) = selectExistingSave(button.index, button)
    private fun selectExistingSave(index: Int, button: FileHandleButton) {
        selectedIndex = index
        selectedButton?.color = Color.WHITE
        button.color = Color.GREEN
        selectedButton = button

        onChangeListener?.invoke(button.file)
    }

    //region Keyboard scrolling

    // Note: We don't put the "scroll into view" functionality into selectExistingSave,
    //       because the Page/Home+End keys scroll differently or simpler.
    //       PgUp/PgDown will keep the vertical position of the selection mostly constant in long lists.

    // Helpers to simplify Scroll positioning - ScrollPane.scrollY goes down, normal Gdx Y goes up
    // These functions all operate in the scrollY 'coordinate system'
    private fun Table.getVerticalSpan(button: FileHandleButton): ClosedFloatingPointRange<Float> {
        val invertedY = height - button.y
        return (invertedY - button.height)..invertedY
    }
    private fun getVerticalSpan() = scrollY..(scrollY + height)

    private fun getButtonAt(y: Float): FileHandleButton {
        // Note: existingSavesTable.getRow(y) has no advantage
        var l = 0
        var h = buttonIndex.size - 1
        var bestIndex = 0
        var bestDistance = 1e6f
        while (l <= h) {
            val index = (l + h) / 2
            val button = buttonIndex[index]
            val span = existingSavesTable.getVerticalSpan(button)
            val distance = if (span.start > y) y - span.start // go up
                else if (span.endInclusive < y) y - span.endInclusive // go down
                else return button // hit
            if (abs(distance) < bestDistance) {
                bestDistance = abs(distance)
                bestIndex = index
            }
            if (distance < 0) h = index - 1 else l = index + 1
        }
        return buttonIndex[bestIndex]
    }

    private fun scrollTo() {
        // Make ScrollPane follow the selection
        // similar to ScrollPane.scrollTo, but that method fails with some vertical offset
        val button = selectedButton ?: return
        val buttonSpan = existingSavesTable.getVerticalSpan(button)
        val scrollSpan = getVerticalSpan()
        if (buttonSpan.start < scrollSpan.start)
            scrollY = buttonSpan.start - 10f
        if (buttonSpan.endInclusive > scrollSpan.endInclusive)
            scrollY = buttonSpan.endInclusive - height + 10f
    }

    private fun onArrowKey(direction: Int) {
        if (buttonIndex.size == 0) return

        fun wrapAround(index: Int) = when {
            index < 0 -> buttonIndex.size - 1
            index >= buttonIndex.size -> 0
            else -> index
        } 
        val newIndex = wrapAround(selectedIndex + direction)
        selectExistingSave(newIndex)
        scrollTo()
    }

    private fun onPageKey(direction: Int) {
        val distance = (height - 60f) * direction
        scrollY += distance  // ScrollPane does the clamping to 0..maxY
        if (selectedButton == null) return
        val buttonSpan = existingSavesTable.getVerticalSpan(selectedButton!!)
        val newButtonY = (buttonSpan.start + buttonSpan.endInclusive) / 2 + distance;
/*
        // Alternate behaviour: Selection follows "reluctantly" by moving it the least distance enough to stay in viewport
        val buttonHeight = selectedButton?.height ?: return
        val scrollSpan = getVerticalSpan()
        val newButtonY = if (buttonSpan.start < scrollSpan.start)
            scrollSpan.start + buttonHeight
        else if (buttonSpan.endInclusive > scrollSpan.endInclusive)
            scrollSpan.endInclusive - buttonHeight
        else return
*/
        selectExistingSave(getButtonAt(newButtonY))
        scrollTo() // mostly not needed, but some drift may end up selecting a button slightly outside the viewport
    }

    private fun onHomeEndKey(direction: Int) {
        scrollY = direction * maxY
        if (buttonIndex.size == 0) return
        selectExistingSave((buttonIndex.size - 1) * direction)
    }
    //endregion
}

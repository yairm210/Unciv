package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/** A widget holding TextButtons vertically, with methods to hold file names and FileHandle's
 *  in those buttons. Used to display existing saves in the Load and Save game dialogs.
 *
 *  Usage: subclass and override at least one of [onSelect] or [onDoubleClick].
 *  If placed in a vertically scrolling container, override [onScrollTo] and [onScrollPage] as well.
 */
abstract class VerticalFileListTable : Table() {
    private val savesPerButton = mutableMapOf<TextButton, FileHandle>()

    private var selectedButton: TextButton? = null
    private val selectedFile: FileHandle
        get() = savesPerButton[selectedButton] ?: throw IllegalStateException("VerticalFileListTable state out of sync")

    /** Called to tell host when a file is selected */
    open fun onSelect(selection: FileHandle) {}
    /** Called to tell host when a file is by Double-Click ([onSelect] is called first) */
    open fun onDoubleClick(selection: FileHandle) {}
    /** Called to tell host to scroll [actor] into View */
    open fun onScrollTo(actor: Actor) {}
    /** Called to tell host to scroll one page in [direction].
     *  @param actor Can optionally be checked whether it's still in view to decide on a return value.
     *  @return `null` when [actor] is null or selection should not move.
     *          A value should be an inverted y coordinate into this Widget and will be used to select a new entry. 
     */
    open fun onScrollPage(direction: Int, actor: Actor?): Float? = null

    init {
        keyShortcuts.add(Input.Keys.UP) { onArrowKey(-1) }
        keyShortcuts.add(Input.Keys.DOWN) { onArrowKey(1) }
        keyShortcuts.add(Input.Keys.PAGE_UP) { onPageKey(-1) }
        keyShortcuts.add(Input.Keys.PAGE_DOWN) { onPageKey(1) }
        keyShortcuts.add(Input.Keys.HOME) { onHomeEndKey(0) }
        keyShortcuts.add(Input.Keys.END) { onHomeEndKey(1) }
    }

    /** repopulate from a FileHandle Sequence - for other sources than saved games */
    fun update(files: Sequence<FileHandle>) {
        clear()
        selectedButton = null
        savesPerButton.clear()

        val loadingImage = LoadingImage(62f, LoadingImage.Style(innerSizeFactor = 0.8f))
        add(loadingImage)
        loadingImage.show()

        // Apparently, even just getting the list of saves can cause ANRs -
        // not sure how many saves these guys had but Google Play reports this to have happened hundreds of times
        Concurrency.run("GetSaves") {
            // .toList() materializes the result of the sequence
            val saves = files.toList()

            launchOnGLThread {
                loadingImage.hide()
                clear()
                for (saveGameFile in saves) {
                    val textButton = TextButton(saveGameFile.name(), BaseScreen.skin)
                    savesPerButton[textButton] = saveGameFile
                    textButton.onClick {
                        selectEntry(textButton)
                    }
                    textButton.onDoubleClick {
                        selectEntry(textButton)
                        onDoubleClick(selectedFile)
                    }
                    add(textButton).pad(5f).row()
                }
            }
        }
    }

    private fun selectEntry(textButton: TextButton) {
        selectedButton?.color = Color.WHITE
        textButton.color = Color.GREEN
        selectedButton = textButton
        onScrollTo(textButton)
        onSelect(selectedFile)
    }

    // Helpers to simplify Scroll positioning - ScrollPane.scrollY goes down, normal Gdx Y goes up
    // These functions all operate in the scrollY 'coordinate system'
    protected fun getVerticalSpan(button: Actor): ClosedFloatingPointRange<Float> {
        val invertedY = height - button.y
        return (invertedY - button.height)..invertedY
    }
    private fun getButtonAt(y: Float) = cells[getRow(height - y)].actor as TextButton

    //region Keyboard scrolling
    private fun onArrowKey(direction: Int) {
        if (rows == 0) return
        val rowIndex = when {
            selectedButton != null -> getCell(selectedButton).row
            direction == 1 -> -1
            else -> 0
        }
        val newRow = (rowIndex + direction).let {
            if (it < 0) rows - 1
            else if (it >= rows) 0
            else it
        }
        val button = cells[newRow].actor as TextButton
        selectEntry(button)
    }

    private fun onPageKey(direction: Int) {
        val newButtonY = onScrollPage(direction, selectedButton) ?: return
        selectEntry(getButtonAt(newButtonY))
    }

    private fun onHomeEndKey(direction: Int) {
        if (rows == 0) return
        val row = (rows - 1) * direction
        selectEntry(cells[row].actor as TextButton)
    }
    //endregion
}

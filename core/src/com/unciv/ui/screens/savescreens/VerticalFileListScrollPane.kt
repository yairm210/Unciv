package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.files.UncivFiles
import com.unciv.ui.components.widgets.AutoScrollPane

//todo key auto-repeat for navigation keys?

/** A widget holding TextButtons vertically in a Table contained in a ScrollPane, with methods to
 *  hold file names and FileHandle's in those buttons. Used to display existing saves in the Load and Save game dialogs.
 */
class VerticalFileListScrollPane : AutoScrollPane(null) {
    private val existingSavesTable = ExistingSavesTable()
    init {
        setActor(existingSavesTable)
    }

    private var onChangeListener: ((FileHandle) -> Unit)? = null
    private var onDoubleClickListener: ((FileHandle) -> Unit)? = null

    fun onChange(action: (FileHandle) -> Unit) {
        onChangeListener = action
    }
    fun onDoubleClick(action: (FileHandle) -> Unit) {
        onDoubleClickListener = action
    }

    /** repopulate with existing saved games */
    fun updateSaveGames(files: UncivFiles, showAutosaves: Boolean) {
        existingSavesTable.update(files.getSaves(showAutosaves)
            .sortedByDescending { it.lastModified() })
    }

    private fun getVerticalSpan() = scrollY..(scrollY + height)

    private inner class ExistingSavesTable : VerticalFileListTable() {
        override fun onSelect(selection: FileHandle) {
            onChangeListener?.invoke(selection)
        }

        override fun onDoubleClick(selection: FileHandle) {
            onDoubleClickListener?.invoke(selection)
        }

        override fun onScrollTo(actor: Actor) {
            // Make ScrollPane follow the selection
            val buttonSpan = existingSavesTable.getVerticalSpan(actor)
            val scrollSpan = getVerticalSpan()
            if (buttonSpan.start < scrollSpan.start)
                scrollY = buttonSpan.start
            if (buttonSpan.endInclusive > scrollSpan.endInclusive)
                scrollY = buttonSpan.endInclusive - this@VerticalFileListScrollPane.height
        }

        override fun onScrollPage(direction: Int, actor: Actor?): Float? {
            scrollY += (this@VerticalFileListScrollPane.height - 60f) * direction  // ScrollPane does the clamping to 0..maxY
            val actorHeight = actor?.height ?: return null
            val actorSpan = existingSavesTable.getVerticalSpan(actor)
            val scrollSpan = getVerticalSpan()
            return if (actorSpan.start < scrollSpan.start)
                scrollSpan.start + actorHeight
            else if (actorSpan.endInclusive > scrollSpan.endInclusive)
                scrollSpan.endInclusive - actorHeight
            else null
        }
    }
}

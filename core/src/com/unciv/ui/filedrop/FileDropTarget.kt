package com.unciv.ui.filedrop

interface FileDropTarget {
    /** Called per file BEFORE any bytes are read, so keep it cheap; the name is all you get. */
    fun acceptsDroppedFile(name: String): Boolean
    /** Called on the render thread. */
    fun onFilesDropped(files: List<DroppedFile>)
}

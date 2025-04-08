package com.unciv.logic.files

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import java.io.File
import java.io.FileFilter
import com.badlogic.gdx.utils.Array as GdxArray

typealias ResultListener = (success: Boolean, file: FileHandle) -> Unit

/**
 *  A file picker written in Gdx as using java.awt.JFileChooser or java.awt.FileDialog crashes on X11 desktops
 *
 *  Based loosely on [olli-pekka's original][https://jvm-gaming.org/t/libgdx-scene2d-ui-filechooser-dialog/53228]
 */

@Suppress("unused", "MemberVisibilityCanBePrivate")  // This has optional API features
open class FileChooser(
    stageToShowOn: Stage,
    title: String?,
    startFile: FileHandle? = null,
    private val resultListener: ResultListener? = null
) : Popup(stageToShowOn, Scrollability.None) {
    // config
    var filter = FileFilter { true }
        set(value) { field = value; resetList() }
    var directoryBrowsingEnabled = true
        set(value) { field = value; resetList() }
    var allowFolderSelect = false
        set(value) { field = value; resetList() }
    var showAbsolutePath = false
        set(value) { field = value; resetList() }
    var fileNameEnabled
        get() = fileNameCell.hasActor()
        set(value) {
            if (value) fileNameCell.setActor(fileNameWrapper)
            else fileNameCell.clearActor()
        }

    // components
    private val fileNameInput = UncivTextField("Please enter a file name")
    private val fileNameLabel = "File name:".toLabel()
    private val fileNameWrapper = Table().apply {
        defaults().space(10f)
        add(fileNameLabel).growX().row()
        add(fileNameInput).growX().row()
        addSeparator(height = 1f)
    }
    private val fileNameCell: Cell<Actor?>
    private val dirTypeLabel = "".toLabel(Color.GRAY)  // color forces a style clone
    private val pathLabel = "".toLabel(Color.GRAY, alignment = Align.left)
    private val pathLabelWrapper = Table().apply {
        touchable = Touchable.enabled
        defaults().space(10f)
        add(dirTypeLabel).pad(2f)
        add(pathLabel).left().growX()
    }
    private val fileList = FileList(skin)
    private val fileScroll = AutoScrollPane(fileList)
    private val okButton: TextButton

    // operational
    private val maxHeight = stageToShowOn.height * 0.6f
    private val absoluteLocalPath = UncivGame.Current.files.getDataFolder().file().absoluteFile.canonicalPath
    private val absoluteExternalPath = if (Gdx.files.isExternalStorageAvailable)
            Gdx.files.external("").file().absoluteFile.canonicalPath
        else "/\\/\\/"  // impossible placeholder
    private var currentDir: FileHandle? = null
    private var result: String? = null
    private val filterWithFolders = FileFilter {
        directoryBrowsingEnabled && it.isDirectory || filter.accept(it)
    }

    private class FileListItem(val label: String, val file: FileHandle, val isFolder: Boolean) {
        constructor(file: FileHandle) : this(
            label = (if (file.isDirectory) "  " else "") + file.name(),
            file,
            isFolder = file.isDirectory  // cache, it's not trivially cheap
        )
        override fun toString() = label
    }

    private class FileList(skin: Skin) : List<FileListItem>(skin) {
        val saveColor = Color()
        val folderColor = Color(1f, 0.86f, 0.5f, 1f) // #ffdb80, same Hue as Goldenrod but S=50 V=100
        @Suppress("UsePropertyAccessSyntax")
        override fun drawItem(batch: Batch, font: BitmapFont, index: Int, item: FileListItem,
                              x: Float, y: Float, width: Float): GlyphLayout {
            saveColor.set(font.color)
            font.setColor(if (item.isFolder) folderColor else Color.WHITE)
            val layout = super.drawItem(batch, font, index, item, x, y, width)
            font.setColor(saveColor)
            return layout
        }
    }

    init {
        innerTable.top().left()

        fileList.selection.setProgrammaticChangeEvents(false)
        fileNameInput.setTextFieldListener { textField, _ ->
            result = textField.text
            enableOKButton()
        }
        fileNameInput.textFieldFilter = UncivFiles.fileNameTextFieldFilter()

        if (title != null) {
            addGoodSizedLabel(title).colspan(2).center().row()
            addSeparator(height = 1f)
        }
        add(pathLabelWrapper).colspan(2).fillX().row()
        addSeparator(Color.GRAY, height = 1f)
        add(fileScroll).colspan(2).fill().row()
        addSeparator(height = 1f)
        fileNameCell = add().colspan(2).growX()
        super.row()

        addCloseButton(Constants.cancel) {
            reportResult(false)
        }
        okButton = addOKButton(Constants.OK) {
            reportResult(true)
        }.actor
        equalizeLastTwoButtonWidths()

        fileList.onChange {
            val selected = fileList.selected ?: return@onChange
            if (!selected.file.isDirectory) {
                result = selected.file.name()
                fileNameInput.text = result
            }
            enableOKButton()
        }
        fileList.onDoubleClick(UncivSound.Silent) {
            val selected = fileList.selected ?: return@onDoubleClick
            if (selected.file.isDirectory)
                changeDirectory(selected.file)
            else {
                reportResult(true)
                close()
            }
        }
        pathLabelWrapper.onClick(UncivSound.Swap) { switchDomain() }

        showListeners.add {
            if (currentDir == null) initialDirectory(startFile)
            stageToShowOn.scrollFocus = fileScroll
            stageToShowOn.keyboardFocus = fileNameInput
        }
    }

    override fun getMaxHeight() = maxHeight

    private fun reportResult(success: Boolean) {
        val file = getResult()
        if (!(success && fileNameEnabled && file.exists())) {
            resultListener?.invoke(success, file)
            return
        }
        ConfirmPopup(stageToShowOn, "Do you want to overwrite ${file.name()}?", "Overwrite",
            restoreDefault = {
                resultListener?.invoke(false, file)
            }, action = {
                resultListener?.invoke(true, file)
            }
        ).open(true)
    }

    private fun makeAbsolute(file: FileHandle): FileHandle {
        if (file.type() == Files.FileType.Absolute) return file
        return Gdx.files.absolute(file.file().absoluteFile.canonicalPath)
    }

    private fun makeRelative(file: FileHandle): FileHandle {
        if (file.type() != Files.FileType.Absolute) return file
        val path = file.path()
        if (path.startsWith(absoluteLocalPath))
            return UncivGame.Current.files.getLocalFile(path.removePrefix(absoluteLocalPath).removePrefix(File.separator))
        if (path.startsWith(absoluteExternalPath))
            return Gdx.files.external(path.removePrefix(absoluteExternalPath).removePrefix(File.separator))
        return file
    }

    private fun initialDirectory(startFile: FileHandle?) {
        changeDirectory(makeAbsolute(when {
            startFile == null && Gdx.files.isExternalStorageAvailable ->
                Gdx.files.absolute(absoluteExternalPath)
            startFile == null ->
                Gdx.files.absolute(absoluteLocalPath)
            startFile.isDirectory -> startFile
            else -> {
                fileNameInput.text = startFile.name()
                result = startFile.name()
                startFile.parent()
            }
        }))
    }

    private fun switchDomain() {
        val current = currentDir?.path() ?: return
        changeDirectory(Gdx.files.absolute(when {
            !Gdx.files.isExternalStorageAvailable -> absoluteLocalPath
            current.startsWith(absoluteExternalPath) && !current.startsWith(absoluteLocalPath)
                -> absoluteLocalPath
            else -> absoluteExternalPath
        }))
    }

    private fun changeDirectory(directory: FileHandle) {
        currentDir = directory

        val relativeFile = if (showAbsolutePath) directory else makeRelative(directory)
        val (label, color) = when (relativeFile.type()) {
            Files.FileType.External -> "Ⓔ" to Color.CHARTREUSE
            Files.FileType.Local -> "Ⓛ" to Color.TAN
            else -> "" to Color.WHITE
        }
        dirTypeLabel.setText(label)
        dirTypeLabel.color.set(color)
        pathLabel.setText(relativeFile.path())

        val list = directory.list(filterWithFolders)
        val items = GdxArray<FileListItem>(list.size)
        for (handle in list) {
            if (!directoryBrowsingEnabled && handle.isDirectory) continue
            if (handle.file().isHidden) continue
            items.add(FileListItem(handle))
        }
        items.sort(dirListComparator)
        if (directoryBrowsingEnabled && directory.file().parentFile != null) {
            items.insert(0, FileListItem("  ..", directory.parent(), true))
        }
        fileList.selected = null
        fileList.setItems(items)
        enableOKButton()
    }

    private fun getResult() = makeRelative(
        if (result.isNullOrEmpty()) currentDir!! else currentDir!!.child(result)
    )

    private fun resetList() {
        if (!hasParent()) return
        changeDirectory(currentDir!!)
    }

    private fun enableOKButton() {
        fun getLoadEnable(): Boolean {
            val file = fileList.selected?.file ?: return false
            if (!file.exists()) return false
            return (allowFolderSelect || !file.isDirectory)
        }
        fun getSaveEnable(): Boolean {
            if (currentDir?.exists() != true) return false
            if (allowFolderSelect) return true
            return result != null && UncivFiles.isValidFileName(result!!)
        }
        okButton.isEnabled = if (fileNameEnabled) getSaveEnable() else getLoadEnable()
    }

    fun setOkButtonText(text: String) {
        okButton.setText(text.tr())
    }

    companion object {
        private val dirListComparator: Comparator<FileListItem> =
            Comparator { file1, file2 ->
                when {
                    file1.file.isDirectory && !file2.file.isDirectory -> -1
                    !file1.file.isDirectory && file2.file.isDirectory -> 1
                    else -> file1.file.name().compareTo(file2.file.name())
                }
            }

        fun createSaveDialog(stage: Stage, title: String?, path: FileHandle? = null, resultListener: ResultListener? = null) =
            FileChooser(stage, title, path, resultListener).apply {
                fileNameEnabled = true
                setOkButtonText("Save")
            }

        fun createLoadDialog(stage: Stage, title: String?, path: FileHandle? = null, resultListener: ResultListener? = null) =
            FileChooser(stage, title, path, resultListener).apply {
                fileNameEnabled = false
                setOkButtonText("Load")
            }

        fun createExtensionFilter(vararg extensions: String) = FileFilter {
            it.extension.lowercase() in extensions
        }
    }
}

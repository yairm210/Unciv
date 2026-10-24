package com.unciv.app.desktop

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Clipboard as ClipboardAwt
import com.badlogic.gdx.utils.Clipboard as ClipboardGdx

/**
 *  A plug-in replacement for Gdx Lwjgl3Clipboard that goes through AWT instead of GLFW and removes the stack size limitation.
 *
 *  Gdx.app.clipboard on desktop is a Lwjgl3Clipboard instance, which allocates a buffer on the stack for UTF8 conversion at the Lwjgl3-GLFW interface.
 *  The default stack size is a severe limitation, which we **originally** treated by increasing the limit, which can only be done statically at launch time:
```
         // 386 is an almost-arbitrary choice from the saves I had at the moment and their GZipped size.
         // There must be a reason for lwjgl3 being so stingy, which for me meant to stay conservative.
         System.setProperty("org.lwjgl.system.stackSize", "384")
```
 *  - See [setContents](https://github.com/libgdx/libgdx/blob/master/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/Lwjgl3Clipboard.java#L40)
 *  - See [glfwSetClipboardString](https://github.com/LWJGL/lwjgl3/blob/master/modules/lwjgl/glfw/src/generated/java/org/lwjgl/glfw/GLFW.java#L5068-L5077)
 *  - See [Higher available clipboard size](https://github.com/orgs/LWJGL/discussions/769)
 */
class AwtClipboard : ClipboardGdx {
    // A lazy seems to work too, but not keeping a reference when not active is safer (also debuggable while a lazy is not):
    private val clipboard: ClipboardAwt
        get() = Toolkit.getDefaultToolkit().systemClipboard

    override fun hasContents(): Boolean {
        return DataFlavor.stringFlavor in clipboard.availableDataFlavors
    }

    override fun getContents(): String? {
        val transferable = clipboard.getContents(null)
        if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
        return transferable.getTransferData(DataFlavor.stringFlavor) as String
    }

    override fun setContents(content: String?) {
        val selection = StringSelection(content)  // Yes this supports null
        clipboard.setContents(selection, selection)
    }
}

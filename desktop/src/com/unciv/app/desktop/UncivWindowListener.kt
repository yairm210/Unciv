package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.unciv.utils.Log
import org.lwjgl.glfw.GLFWNativeWin32

/**
 *  This is a Lwjgl3WindowListener serving the following purposes:
 *
 *  - Catch and store our Lwjgl3Window instance
 *  - Track whether the Window has focus
 *  - "Flash" the Window to alert "your turn" in multiplayer
 */
class UncivWindowListener : Lwjgl3WindowAdapter() {

    private var window: Lwjgl3Window? = null
    private var hasFocus: Boolean = true

    override fun created(window: Lwjgl3Window?) {
        // In DesktopDisplay we use `(Gdx.graphics as? Lwjgl3Graphics)?.window`, so this entire listener could be made redundant.
        // But we might need other uses in the future, so leave it as is.
        this.window = window
    }

    override fun focusLost() {
        hasFocus = false
    }

    override fun focusGained() {
        hasFocus = true
    }


    /** Asks the operating system to request the player's attention */
    fun turnStarted() {
        flashWindow()
    }

    /**
     * Requests user attention
     *
     * Excerpt from the [GLFW.glfwRequestWindowAttention](https://www.glfw.org/docs/latest/window_guide.html#window_attention) JavaDoc:
     * - This function must only be called from the main thread.
     * - **macOS:** Attention is requested to the application as a whole, not the specific window.
     *
     * On Windows, the OS-specific API is called directly instead.
     * - See [FlashWindowEx](https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-flashwindowex)
     *
     */
    private fun flashWindow() {
        try {
            if (window == null || hasFocus) return
            if (user32 == null)
                // Use Cross-Platform implementation
                return window!!.flash()

            // Windows-specific implementation:
            val flashwinfo = WinUser.FLASHWINFO()
            val hwnd = GLFWNativeWin32.glfwGetWin32Window(window!!.windowHandle)
            flashwinfo.hWnd = WinNT.HANDLE(Pointer.createConstant(hwnd))
            flashwinfo.dwFlags = 3 // FLASHW_ALL
            flashwinfo.uCount = 3
            // FlashWindow (no binding in Java's User32) instead of FlashWindowEx would flash just once
            user32.FlashWindowEx(flashwinfo)
        } catch (e: Throwable) {
            /** try to ignore even if we get an [Error], just log it */
            Log.error("Error while notifying the user of their turn", e)
        }
    }

    private companion object {
        /** Marshals JNA access to the Windows User32 API through [com.sun.jna.platform.win32.User32]
         *
         *  (which, by the way, says "Incomplete implementation to support demos.")
         */
        val user32: User32? = try {
            if (System.getProperty("os.name")?.contains("Windows") == true) {
                Native.load(User32::class.java)
            } else {
                null
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.error("Error while initializing turn notifier", e)
            null
        }
    }
}

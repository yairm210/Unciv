package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.unciv.utils.Log
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWNativeWin32
import java.nio.IntBuffer

class UncivWindowListener: Lwjgl3WindowAdapter() {
    companion object {
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

    private var window: Lwjgl3Window? = null
    private var hasFocus: Boolean = true
    var isMaximized = false
        private set
    private var normalWindowDimensions: java.awt.Rectangle? = null

    override fun created(window: Lwjgl3Window?) {
        this.window = window
    }

    override fun focusLost() {
        hasFocus = false
    }

    override fun focusGained() {
        hasFocus = true
        saveNormalWindowDimensions()
    }

    override fun maximized(isMaximized: Boolean) {
        // lwjgl3Window remembers iconified but not maximized, so we'll have to remember it
        this.isMaximized = isMaximized
        saveNormalWindowDimensions()
    }

    override fun refreshRequested() {
        saveNormalWindowDimensions()
        super.refreshRequested()
    }

    private fun getCurrentWindowDimensions(): java.awt.Rectangle? {
        if (window == null) return null
        val handle = window!!.windowHandle
        val widthBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
        val heightBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
        val xPosBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
        val yPosBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
        GLFW.glfwGetWindowSize(handle, widthBuffer,  heightBuffer)
        // glfwGetWindowPos won't work on Wayland (lwjgl3 hides that fact)
        //todo: Find out **how** the heck a void function "emits" FEATURE_UNAVAILABLE and set x/y to -1 which later will be interpreted as "please center the window"
        GLFW.glfwGetWindowPos(handle, xPosBuffer, yPosBuffer)
        return java.awt.Rectangle(xPosBuffer[0], yPosBuffer[0], widthBuffer[0], heightBuffer[0])
    }

    private fun saveNormalWindowDimensions() {
        if (isMaximized || window?.isIconified == true) return
        normalWindowDimensions = getCurrentWindowDimensions()
        normalWindowDimensions?.run {
            Log.debug("window dimensions saved: x=%d, y=%d, w=%d, h=%d", x, y, width, height)
        }
    }

    internal fun getWindowDimensions(): java.awt.Rectangle? {
        if (isMaximized) return normalWindowDimensions
        return getCurrentWindowDimensions()
    }


    /**
     * For platform independence, use GLFW.glfwRequestWindowAttention via window.flash()
     * (windows-specific code left in so finer control of dwFlags and uCount stays available)
     *
     * See https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-flashwindowex
     *
     * We should've used FlashWindow instead of FlashWindowEx, but for some reason the former has no binding in Java's User32
     */
    fun flashWindow() {
        try {
            if (window == null) return
            if (user32 == null || hasFocus) return window!!.flash()
            val flashwinfo = WinUser.FLASHWINFO()
            val hwnd = GLFWNativeWin32.glfwGetWin32Window(window!!.windowHandle)
            flashwinfo.hWnd = WinNT.HANDLE(Pointer.createConstant(hwnd))
            flashwinfo.dwFlags = 3 // FLASHW_ALL
            flashwinfo.uCount = 3
            user32.FlashWindowEx(flashwinfo)
        } catch (e: Throwable) {
            /** try to ignore even if we get an [Error], just log it */
            Log.error("Error while notifying the user of their turn", e)
        }
    }
}

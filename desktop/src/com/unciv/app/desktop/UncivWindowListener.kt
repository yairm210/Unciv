package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.unciv.models.metadata.WindowState
import com.unciv.utils.Log
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWNativeWin32
import java.nio.IntBuffer

/** Manages Lwjgl3 Window details - maximizing, restore-to dimensions, taskbar flashing.
 *
 *  The constructor takes the last WindowState so it can survive consecutive restarts.
 */
class UncivWindowListener(initialState: WindowState) : Lwjgl3WindowAdapter() {
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

    /** Lwjgl3Window keeps track of isIconified but fails to do so for maximized, so we do that here */
    var isMaximized = initialState.isMaximized
        private set

    /** This holds the window position and size when it is neither maximized nor iconified.
     *  In one of those other states, the underlying GLFW window knows what to restore to (possibly
     *  OS-dependent), and since GLFW does not expose that info, we try to keep track ourselves. */
    private var normalWindowDimensions: java.awt.Rectangle =
        initialState.run { java.awt.Rectangle(x, y, width, height) }

    // Keep these around since getCurrentWindowDimensions might be called often
    private val widthBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    private val heightBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    private val xPosBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    private val yPosBuffer: IntBuffer = BufferUtils.createIntBuffer(1)

    override fun created(window: Lwjgl3Window?) {
        this.window = window
    }

    override fun focusLost() {
        hasFocus = false
        saveNormalWindowDimensions()
    }

    override fun focusGained() {
        hasFocus = true
        saveNormalWindowDimensions()
    }

    override fun closeRequested(): Boolean {
        saveNormalWindowDimensions()
        return true
    }

    override fun maximized(isMaximized: Boolean) {
        // Note: This isn't fired for a window maximized by Lwjgl3WindowConfiguration.windowMaximized,
        // so the flag must be initialized properly or saveNormalWindowDimensions will misbehave.
        this.isMaximized = isMaximized
        saveNormalWindowDimensions()
    }

    private fun getCurrentWindowDimensions(): java.awt.Rectangle? {
        if (window == null) return null
        val handle = window!!.windowHandle
        GLFW.glfwGetWindowSize(handle, widthBuffer,  heightBuffer)
        // GLFW.glfwGetWindowPos won't work on Wayland (see its JDoc, Lwjgl3Window hides that fact in the getPositionX/Y proxies)
        //todo: Find out **how** the heck a void function "emits" FEATURE_UNAVAILABLE, then set x/y to -1 which later will be interpreted as "please center the window"
        GLFW.glfwGetWindowPos(handle, xPosBuffer, yPosBuffer)
        return java.awt.Rectangle(xPosBuffer[0], yPosBuffer[0], widthBuffer[0], heightBuffer[0])
    }

    private fun saveNormalWindowDimensions() {
        if (isMaximized || window?.isIconified == true) return
        getCurrentWindowDimensions()?.run {
            normalWindowDimensions = this
            Log.debug("window dimensions saved: x=%d, y=%d, w=%d, h=%d", x, y, width, height)
        }
    }

    internal fun getWindowState(): WindowState? {
        return (if (isMaximized) normalWindowDimensions else getCurrentWindowDimensions())
            ?.run { WindowState(width, height, isMaximized, x, y, Gdx.graphics.monitor.name) }
    }


    /**
     * For platform independence, use [GLFW.glfwRequestWindowAttention] via window.flash()
     * (windows-specific code left in so finer control of dwFlags, uCount or dwTimeout stays available)
     *
     * See https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-flashwindowex
     *
     * FlashWindow (no binding in Java's User32) instead of FlashWindowEx would flash just once
     */
    fun flashWindow() {
        try {
            if (window == null || hasFocus) return
            if (user32 == null) return window!!.flash()
            val flashwinfo = WinUser.FLASHWINFO()
            val hwnd = GLFWNativeWin32.glfwGetWin32Window(window!!.windowHandle)
            flashwinfo.hWnd = WinNT.HANDLE(Pointer.createConstant(hwnd))
            flashwinfo.dwFlags = WinUser.FLASHW_ALL  // Flash both caption and taskbar button
            flashwinfo.uCount = 3                    // Flash three times
            user32.FlashWindowEx(flashwinfo)
        } catch (e: Throwable) {
            /** try to ignore even if we get an [Error], just log it */
            Log.error("Error while notifying the user of their turn", e)
        }
    }
}

package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.unciv.ui.MultiplayerTurnNotifier
import org.lwjgl.glfw.GLFWNativeWin32

class MultiplayerTurnNotifierWindows: Lwjgl3WindowAdapter(), MultiplayerTurnNotifier {
    companion object {
        val user32: User32? = try {
            Native.load(User32::class.java)
        } catch (e: UnsatisfiedLinkError) {
            println("Error while initializing turn notifier: " + e.message)
            null
        }
    }
    private var window: Lwjgl3Window? = null
    private var hasFocus: Boolean = true

    override fun created(window: Lwjgl3Window?) {
        this.window = window
    }

    override fun focusLost() {
        hasFocus = false
    }

    override fun focusGained() {
        hasFocus = true
    }

    /**
     * See https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-flashwindowex
     *
     * We should've used FlashWindow instead of FlashWindowEx, but for some reason the former has no binding in Java's User32
     */
    override fun turnStarted() {
        try {
            if (user32 == null || hasFocus) return
            val flashwinfo = WinUser.FLASHWINFO()
            val hwnd = GLFWNativeWin32.glfwGetWin32Window((Gdx.graphics as Lwjgl3Graphics).window.windowHandle)
            flashwinfo.hWnd = WinNT.HANDLE(Pointer.createConstant(hwnd))
            flashwinfo.dwFlags = 3 // FLASHW_ALL
            flashwinfo.uCount = 3
            user32.FlashWindowEx(flashwinfo)
        } catch (e: Throwable) {
            /** try to ignore even if we get an [Error], just log it */
            println("Error while notifying the user of their turn: " + e.message)
        }
    }
}

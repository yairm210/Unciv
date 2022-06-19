package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input

/*
 * For now, many combination keys cannot easily be expressed.
 * Pressing Ctrl-Letter will arrive one event for Input.Keys.CONTROL_LEFT and one for the ASCII control code point
 *      so Ctrl-R can be handled using KeyCharAndCode('\u0012') or KeyCharAndCode.ctrl('R')
 * Pressing Alt-Something likewise will fire once for Alt and once for the unmodified keys with no indication Alt is held
 *      (Exception: international keyboard AltGr-combos)
 * An update supporting easy declarations for any modifier combos would need to use Gdx.input.isKeyPressed()
 * Gdx seems to omit support for a modifier mask (e.g. Ctrl-Alt-Shift) so we would need to reinvent this
 *
 * Note: It is important that KeyCharAndCode is an immutable data class to support usage as HashMap key
 */

/**
 * Represents a key for use in an InputListener keyTyped() handler
 *
 * Example: KeyCharAndCode('R'), KeyCharAndCode(Input.Keys.F1)
 */
data class KeyCharAndCode(val char: Char, val code: Int) {
    /** helper 'cloning constructor' to allow feeding both fields from a factory function */
    private constructor(from: KeyCharAndCode): this(from.char, from.code)
    /** Map keys from a Char - will detect by keycode if one can be mapped, by character otherwise */
    constructor(char: Char): this(mapChar(char))
    /** express keys that only have a keyCode like F1 */
    constructor(code: Int): this(Char.MIN_VALUE, code)

    override fun toString(): String {
        // debug helper, but also used for tooltips
        fun fixedKeysToString(code: Int) = when (code) {
            Input.Keys.BACKSPACE -> "Backspace"  // Gdx displaying this as "Delete" is Bullshit!
            Input.Keys.FORWARD_DEL -> "Del"      // Likewise
            else -> Input.Keys.toString(code)
        }
        return when {
            char == Char.MIN_VALUE -> fixedKeysToString(code)
            this == ESC -> "ESC"
            char < ' ' -> "Ctrl-" + (char.toCode() + 64).makeChar()
            else -> "\"$char\""
        }
    }

    companion object {
        /** Tests presence of a physical keyboard - static here as convenience shortcut only */
        val keyboardAvailable = Gdx.input.isPeripheralAvailable(Input.Peripheral.HardwareKeyboard)

        // Convenience shortcuts for frequently used constants
        /** Android back, assigns ESC automatically as well */
        val BACK = KeyCharAndCode(Input.Keys.BACK)
        /** Automatically assigned for [BACK] */
        val ESC = KeyCharAndCode(Input.Keys.ESCAPE)
        /** Assigns [NUMPAD_ENTER] automatically as well */
        val RETURN = KeyCharAndCode(Input.Keys.ENTER)
        /** Automatically assigned for [RETURN] */
        val NUMPAD_ENTER = KeyCharAndCode(Input.Keys.NUMPAD_ENTER)
        val SPACE = KeyCharAndCode(Input.Keys.SPACE)
        val DEL = KeyCharAndCode(Input.Keys.FORWARD_DEL)        // Gdx "DEL" is just plain wrong!
        val TAB = KeyCharAndCode(Input.Keys.TAB)
        /** Guaranteed to be ignored by [KeyShortcutDispatcher] and never to be generated for an actual event, used as fallback to ensure no action is taken */
        val UNKNOWN = KeyCharAndCode(Input.Keys.UNKNOWN)

        // Kludges because we got crashes: java.lang.NoSuchMethodError: 'int kotlin.CharCodeKt.access$getCode$p(char)'
        //TODO fixed by removing UncivServer from the desktop module - clean up comments and all uses
        fun Char.toCode() = code
//            try { code } catch (ex: Throwable) { null }
//                ?: try { @Suppress("DEPRECATION") toInt() } catch (ex: Throwable) { null }
//                ?: 0
        fun Int.makeChar() = Char(this)
//            try { Char(this) } catch (ex: Throwable) { null }
//                ?: try { toChar() } catch (ex: Throwable) { null }
//                ?: Char.MIN_VALUE

        /** mini-factory for control codes - case insensitive */
        fun ctrl(letter: Char) = KeyCharAndCode((letter.toCode() and 31).makeChar(),0)

        /** mini-factory for control codes from keyCodes */
        fun ctrlFromCode(keyCode: Int): KeyCharAndCode {
            val name = Input.Keys.toString(keyCode)
            if (name.length != 1 || !name[0].isLetter()) return KeyCharAndCode(Char.MIN_VALUE, keyCode)
            return ctrl(name[0])
        }

        /** mini-factory for KeyCharAndCode values to be compared by character, not by code */
        fun ascii(char: Char) = KeyCharAndCode(char.lowercaseChar(), 0)

        /** factory maps a Char to a keyCode if possible, returns a Char-based instance otherwise */
        fun mapChar(char: Char): KeyCharAndCode {
            val code = Input.Keys.valueOf(char.uppercaseChar().toString())
            return if (code == -1) KeyCharAndCode(char,0) else KeyCharAndCode(Char.MIN_VALUE, code)
        }
    }
}


data class KeyShortcut(val key: KeyCharAndCode, val priority: Int = 0)


open class KeyShortcutDispatcher {
    private val shortcuts: MutableList<Pair<KeyShortcut, () -> Unit>> = mutableListOf()

    fun add(shortcut: KeyShortcut?, action: (() -> Unit)?): Unit {
        if (action == null || shortcut == null) return
        shortcuts.removeIf { it.first == shortcut }
        shortcuts.add(Pair(shortcut, action))
    }

    fun add(key: KeyCharAndCode?, action: (() -> Unit)?): Unit {
        if (key != null)
            add(KeyShortcut(key), action)
    }

    fun add(char: Char?, action: (() -> Unit)?): Unit {
        if (char != null)
            add(KeyCharAndCode(char), action)
    }

    fun add(keyCode: Int?, action: (() -> Unit)?): Unit {
        if (keyCode != null)
            add(KeyCharAndCode(keyCode), action)
    }

    fun remove(shortcut: KeyShortcut?): Unit {
        shortcuts.removeIf { it.first == shortcut }
    }

    fun remove(key: KeyCharAndCode?): Unit {
        shortcuts.removeIf { it.first.key == key }
    }

    fun remove(char: Char?): Unit {
        shortcuts.removeIf { it.first.key.char == char }
    }

    fun remove(keyCode: Int?): Unit {
        shortcuts.removeIf { it.first.key.code == keyCode }
    }

    open fun isActive(): Boolean = true


    class Resolver(val key: KeyCharAndCode) {
        private var priority = Int.MIN_VALUE
        val trigerredActions: MutableList<() -> Unit> = mutableListOf()

        fun updateFor(dispatcher: KeyShortcutDispatcher) {
            if (!dispatcher.isActive()) return

            for (shortcut in dispatcher.shortcuts) {
                if (shortcut.first.key == key) {
                    if (shortcut.first.priority == priority)
                        trigerredActions.add(shortcut.second)
                    else if (shortcut.first.priority > priority) {
                        priority = shortcut.first.priority
                        trigerredActions.clear()
                        trigerredActions.add(shortcut.second)
                    }
                }
            }
        }
    }
}

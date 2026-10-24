package com.unciv.ui.components.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.ui.components.extensions.GdxKeyCodeFixes


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
 * Represents a key for use in an [InputListener.keyDown] handler
 *
 * Example: KeyCharAndCode('R'), KeyCharAndCode(Input.Keys.F1)
 * @see KeyboardBinding
 * @see KeyShortcutListener
 */
data class KeyCharAndCode(val char: Char, val code: Int) {
    /** helper 'cloning constructor' to allow feeding both fields from a factory function */
    private constructor(from: KeyCharAndCode): this(from.char, from.code)
    /** Map keys from a Char - will detect by keycode if one can be mapped, by character otherwise */
    constructor(char: Char): this(mapChar(char))
    /** express keys that only have a keyCode like F1 */
    constructor(code: Int): this(Char.MIN_VALUE, code)

    //** debug helper, but also used for tooltips */
    override fun toString(): String {
        return when {
            this == UNKNOWN -> ""  // Makes tooltip code simpler. Sorry, debuggers.
            char == Char.MIN_VALUE -> GdxKeyCodeFixes.toString(code)
            this == ESC -> "ESC"
            char < ' ' -> "Ctrl-" + (char.toCode() + 64).makeChar()
            else -> "\"$char\""
        }
    }

    companion object {
        // Convenience shortcuts for frequently used constants
        /** Android back, assigns [ESC] automatically as well */
        val BACK = KeyCharAndCode(Input.Keys.BACK)
        /** Automatically assigned for [BACK] - please use that instead */
        val ESC = KeyCharAndCode(Input.Keys.ESCAPE)
        /** Assigns [NUMPAD_ENTER] automatically as well */
        val RETURN = KeyCharAndCode(Input.Keys.ENTER)
        /** Automatically assigned for [RETURN] */
        val NUMPAD_ENTER = KeyCharAndCode(Input.Keys.NUMPAD_ENTER)
        val SPACE = KeyCharAndCode(Input.Keys.SPACE)
        val DEL = KeyCharAndCode(GdxKeyCodeFixes.DEL)
        val TAB = KeyCharAndCode(Input.Keys.TAB)
        /** Guaranteed to be ignored by [KeyShortcutDispatcher] and never to be generated for an actual event, used as fallback to ensure no action is taken */
        val UNKNOWN = KeyCharAndCode(Input.Keys.UNKNOWN)

        fun Char.toCode() = code
        fun Int.makeChar() = Char(this)

        /** mini-factory for control codes - case insensitive */
        fun ctrl(letter: Char) = KeyCharAndCode((letter.toCode() and 31).makeChar(),0)

        /** mini-factory for control codes from keyCodes */
        fun ctrlFromCode(keyCode: Int): KeyCharAndCode {
            val name = GdxKeyCodeFixes.toString(keyCode)
            if (name.length != 1 || !name[0].isLetter()) return KeyCharAndCode(Char.MIN_VALUE, keyCode)
            return ctrl(name[0])
        }

        /** mini-factory for KeyCharAndCode values to be compared by character, not by code */
        fun ascii(char: Char) = KeyCharAndCode(char.lowercaseChar(), 0)

        /** factory maps a Char to a keyCode if possible, returns a Char-based instance otherwise */
        fun mapChar(char: Char): KeyCharAndCode {
            val code = GdxKeyCodeFixes.valueOf(char.uppercaseChar().toString())
            return if (code == -1) KeyCharAndCode(char,0) else KeyCharAndCode(Char.MIN_VALUE, code)
        }

        /** Parse a human-readable representation into a KeyCharAndCode, inverse of [KeyCharAndCode.toString], case-sensitive.
         *
         *  Understands
         *  - Single characters or quoted single characters (double-quotes)
         *  - Names as produced by the non-conforming String.toString(Int) function in [com.badlogic.gdx.Input.Keys], with fixes for DEL and BACKSPACE.
         *  Not parseable input, including the empty string, results in [KeyCharAndCode.UNKNOWN].
         */
        fun parse(text: String): KeyCharAndCode = when {
                text.length == 1 && text[0].isDefined() -> KeyCharAndCode(text[0])
                text.length == 3 && text[0] == '"' && text[2] == '"' -> KeyCharAndCode(text[1])
                text.length == 6 && text.startsWith("Ctrl-") -> ctrl(text[5])
                text == "ESC" -> ESC
                else -> {
                    val code = GdxKeyCodeFixes.valueOf(text)
                    if (code == -1) UNKNOWN else KeyCharAndCode(code)
                }
            }
    }

    /**
     *  A Serializer that needs to be registered via [Json.setSerializer]
     *  - Output syntax is just the string from toString() that will be parsed on deserialization,
     *    e.g. "keyBindings":{"Diplomacy":"Ctrl-D","DeveloperConsole":"`"}
     *  - Implementing [Json.Serializable] instead would be possible, but not allow the terse json syntax above, since that interface is restrictive:
     *    1) It expects output to always use the object notation and writes the delimiters before you are asked to serialize.
     *    2) it expects you to deserialize into a mutable default instance it has already instantiated for you.
     */
    class Serializer : Json.Serializer<KeyCharAndCode> {
        override fun write(json: Json, key: KeyCharAndCode, knownType: Class<*>?) {
            // Gdx Json is.... No comment. This `Any` is needed to resolve the ambiguity between
            //      public void writeValue (String name, @Null Object value, @Null Class knownType)
            // and
            //      public void writeValue (@Null Object value, @Null Class knownType, @Null Class elementType)
            // - we want the latter. And without the explicitly provided knownType it will _unpredictably_ use
            // `{"class":"java.lang.String","value":"Space"}` instead of `"Space"`.
            json.writeValue(key.toString() as Any, String::class.java, null)
        }

        override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): KeyCharAndCode {
            return parse(jsonData.asString())
        }
    }
}

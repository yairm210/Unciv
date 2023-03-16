package com.unciv.ui.components

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.translations.tr
import com.unciv.ui.screens.basescreen.BaseScreen
import com.badlogic.gdx.utils.Array as GdxArray


/**
 *  A Widget to allow selecting keys from a [SelectBox].
 *
 *  Added value:
 *  *  Easier change callback as direct [changeCallback] parameter
 *  *  Pulls existing keys from Gdx.[Input.Keys] with an exclusion parameter
 *  *  Key names made translatable as e.g. `Key-"A"` or `Key-Space`
 *  *  Supports TranslationFileWriter by providing those translation keys
 */
// Note this has similarities with TranslatedSelectBox, but having entries in the translation
// files that consist only of a punctuation character wouldn't be pretty
class KeysSelectBox(
    private val default: String = "",
    excludeKeys: Set<Int> = defaultExclusions,
    private val changeCallback: (() -> Unit)? = null
) : SelectBox<KeysSelectBox.KeysSelectBoxEntry>(BaseScreen.skin) {

    class KeysSelectBoxEntry(val name: String) {
        val translation = run {
            val translationKey = getTranslationKey(name)
            val translation = translationKey.tr()
            if (translation == translationKey) name else translation
        }

        override fun toString() = translation
        override fun equals(other: Any?) = other is KeysSelectBoxEntry && other.name == this.name
        override fun hashCode() = name.hashCode()
    }

    companion object {
        // Gdx.Input.Keys has a keyNames map internally that would serve,
        // but it's private and only used for valueOf() - So we do the same here...
        private val keyCodeMap = LinkedHashMap<String, Int>(200).apply {
            for (code in 0..Input.Keys.MAX_KEYCODE) {
                val name = Input.Keys.toString(code) ?: continue
                put(name, code)
            }
        }

        val defaultExclusions = setOf(
            Input.Keys.UNKNOWN,         // Any key GLFW or Gdx fail to translate fire this - and we assign our own meaning
            Input.Keys.ESCAPE,          // Married to Android Back, we want to keep this closing the Options
            Input.Keys.CONTROL_LEFT,    // Captured to support control-letter combos
            Input.Keys.CONTROL_RIGHT,
            Input.Keys.PICTSYMBOLS,     // Ugly toString and unknown who could have such a key
            Input.Keys.SWITCH_CHARSET,
            Input.Keys.SYM,
            Input.Keys.PRINT_SCREEN,    // Not captured by Gdx by default, these are handled by the OS
            Input.Keys.VOLUME_UP,
            Input.Keys.VOLUME_DOWN,
            Input.Keys.MUTE,
        )

        fun getKeyNames(excludeKeys: Set<Int>): Sequence<String> {
            val keyCodeNames = keyCodeMap.asSequence()
                .filterNot { it.value in excludeKeys }
                .map { it.key }
            val controlNames = ('A'..'Z').asSequence()
                .map { "Ctrl-$it" }
            return (keyCodeNames + controlNames)
                .sortedWith(
                    compareBy<String> {
                        when {
                            it.length == 1 -> 0  // Characters first
                            it.length in 2..3 && it[0] == 'F' && it[1].isDigit() ->
                                it.drop(1).toInt()  // then F-Keys numerically
                            it.startsWith("Ctrl-") -> 100  // Then Ctrl-*
                            else -> 999  // Rest last
                        }
                    }.thenBy { it }  // should be by translated, but - later
                )
        }

        private fun getTranslationKey(keyName: String): String {
            // Need to take make sure these don't match Translations.squareBraceRegex or Translations.pointyBraceRegex
            // OK because Input.Keys.toString will only return those braces as single characters, they're not used for other key names
            val noSpaces = keyName.replace(' ','-')
            // quote the last character if it isn't a letter or digit - only because it looks nicer for translators
            if (noSpaces.last().isLetterOrDigit()) return "Key-$noSpaces"
            return "Key-${noSpaces.dropLast(1)}\"${noSpaces.last()}\""
        }

        @Suppress("unused")  // TranslationFileWriter integration will be done later
        fun getAllTranslationKeys() = getKeyNames(defaultExclusions).map { getTranslationKey(it) }
    }

    private val normalStyle: SelectBoxStyle
    private val defaultStyle: SelectBoxStyle

    init {
        //TODO:
        // * Dropdown doesn't listen to mouse wheel until after some focus setting click
        //   (like any other SelectBox, but here it's more noticeable)

        items = GdxArray<KeysSelectBoxEntry>(200).apply {
            for (name in getKeyNames(excludeKeys))
                add(KeysSelectBoxEntry(name))
        }
        setSelected(default)

        maxListCount = 12  // or else the dropdown will fill as much vertical space as it can, including upwards

        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                setColorForDefault()
                changeCallback?.invoke()
            }
        })

        // clone style to prevent bleeding our changes to other Widgets
        normalStyle = SelectBoxStyle(style)
        // Another instance of SelectBoxStyle for displaying whether the selection is the default one
        defaultStyle = SelectBoxStyle(normalStyle)
        defaultStyle.fontColor = Color.GRAY.cpy()

        setColorForDefault()
    }

    fun setSelected(name: String) {
        val newSelection = items.firstOrNull { it.name == name }
            ?: return
        selected = newSelection
    }

    /** Update fontColor to show whether the selection is the default one (as grayed) */
    private fun setColorForDefault() {
        // See Javadoc of underlying style - setStyle is needed to update visually
        @Suppress("UsePropertyAccessSyntax")
        setStyle(if (selected.name == default) defaultStyle else normalStyle)
    }
}

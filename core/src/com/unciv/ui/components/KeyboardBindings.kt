package com.unciv.ui.components

import com.unciv.GUI

/**
 *  Manage user-configurable keyboard bindings
 *
 *  A primary instance lives in [UncivGame.Current.settings][com.unciv.models.metadata.GameSettings]
 *  and is read/write accessible through the `KeyboardBindings[]` syntax.
 **/
class KeyboardBindings : HashMap<KeyboardBinding, KeyCharAndCode>() {

    /** Allows adding entries by [KeyboardBinding] as name / [KeyCharAndCode] as string representation */
    private fun put(name: String, value: String) {
        val binding = KeyboardBinding.values().firstOrNull { it.name == name} ?: return
        put(binding, value)
    }

    /** Allows adding entries by [KeyCharAndCode] string representation,
     *  an empty [value] resets the binding to default */
    fun put(binding: KeyboardBinding, value: String) {
        if (value.isEmpty()) {
            remove(binding)
        } else {
            val key = KeyCharAndCode.parse(value)
            if (key != KeyCharAndCode.UNKNOWN)
                put(binding, key)
        }
    }

    /**
     * Adds or replaces a binding or removes it if [value] is the default for [key]
     * @param key the map key defining the binding
     * @param value the keyboard key to assign
     * @return the previously bound key if any
     */
    // Note clearer parameter names gives "PARAMETER_NAME_CHANGED_ON_OVERRIDE" warnings
    override fun put(key: KeyboardBinding, value: KeyCharAndCode): KeyCharAndCode? {
        val result = super.get(key)
        if (key.defaultKey == value)
            remove(key)
        else
            super.put(key, value)
        return result
    }

    /** Indexed access will return default key for missing entries */
    override fun get(key: KeyboardBinding) = super.get(key) ?: key.defaultKey

    companion object {
        // Convenience shortcuts allowing `KeyboardBindings[binding]` globally, accesses global settings
        operator fun get(binding: KeyboardBinding) = default[binding]
        operator fun set(binding: KeyboardBinding, key: KeyCharAndCode) { default[binding] = key }
        val default get() = GUI.getSettings().keyBindings
    }
}

package com.unciv.ui.components.input

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.GUI

/**
 *  Manage user-configurable keyboard bindings
 *
 *  A primary instance lives in [UncivGame.Current.settings][com.unciv.models.metadata.GameSettings]
 *  and is read/write accessible through the `KeyboardBindings[]` syntax.
 **/
class KeyboardBindings : HashMap<KeyboardBinding, KeyCharAndCode>(), Json.Serializable {

    /** this [put] overload helps the Json [Serializer] read method */
    private fun put(element: JsonValue) {
        put(element.name, (element["value"] ?: element).asString())
    }

    /** Allows adding entries by [KeyboardBinding] as name / [KeyCharAndCode] as string representation */
    private fun put(name: String, value: String) {
        val binding = KeyboardBinding.entries.firstOrNull { it.name == name} ?: return
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

    /**
     *  Implementing Json.Serializable helps Gdx Json to read/write a readable, minimal serialization
     *  - without, KeyCharAndCode.Serializer.write will not be used properly
     */
    override fun write(json: Json) {
        for ((binding, key) in this) {
            json.writeValue(binding.name, key, KeyCharAndCode::class.java)
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        if (jsonData.isObject && jsonData.notEmpty())
            for (element in jsonData) put(element)
    }
}

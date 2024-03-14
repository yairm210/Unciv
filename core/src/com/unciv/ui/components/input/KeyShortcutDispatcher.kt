package com.unciv.ui.components.input

open class KeyShortcutDispatcher {
    /**
     * @param key       The hardcoded key that will be bound to an action through the [add] methods -OR-
     * @param binding   The abstract [KeyboardBinding] that will be bound to an action through the [add] methods
     * @param priority  Used by the [Resolver] - only the actions bound to the incoming key with the _highest priority_ will run
     */
    data class KeyShortcut(val binding: KeyboardBinding, val key: KeyCharAndCode, val priority: Int = 0) {
        /** Debug helper */
        override fun toString() = if (binding.hidden) "$key@$priority" else "$binding@$priority"
    }

    private data class ShortcutAction(val shortcut: KeyShortcut, val action: ActivationAction)
    private val shortcuts: MutableList<ShortcutAction> = mutableListOf()

    fun clear() = shortcuts.clear()

    fun add(shortcut: KeyShortcut?, action: ActivationAction?) {
        if (action == null || shortcut == null) return
        shortcuts.removeAll { it.shortcut == shortcut }
        shortcuts.add(ShortcutAction(shortcut, action))
    }

    fun add(binding: KeyboardBinding, priority: Int = 1, action: ActivationAction?) {
        add(KeyShortcut(binding, KeyCharAndCode.UNKNOWN, priority), action)
    }

    fun add(key: KeyCharAndCode?, action: ActivationAction?) {
        if (key != null)
            add(KeyShortcut(KeyboardBinding.None, key), action)
    }

    fun add(char: Char?, action: ActivationAction?) {
        if (char != null)
            add(KeyCharAndCode(char), action)
    }

    fun add(keyCode: Int?, action: ActivationAction?) {
        if (keyCode != null)
            add(KeyCharAndCode(keyCode), action)
    }

    fun remove(shortcut: KeyShortcut?) {
        shortcuts.removeAll { it.shortcut == shortcut }
    }

    fun remove(binding: KeyboardBinding) {
        shortcuts.removeAll { it.shortcut.binding == binding }
    }

    fun remove(key: KeyCharAndCode?) {
        shortcuts.removeAll { it.shortcut.key == key }
    }

    fun remove(char: Char?) {
        shortcuts.removeAll { it.shortcut.key.char == char }
    }

    fun remove(keyCode: Int?) {
        shortcuts.removeAll { it.shortcut.key.code == keyCode }
    }

    open fun isActive(): Boolean = true


    class Resolver(val key: KeyCharAndCode) {
        private var priority = Int.MIN_VALUE
        val triggeredActions: MutableList<ActivationAction> = mutableListOf()

        fun updateFor(dispatcher: KeyShortcutDispatcher) {
            if (!dispatcher.isActive()) return

            for ((shortcut, action) in dispatcher.shortcuts) {
                var (binding, shortcutKey, shortcutPriority) = shortcut
                if (binding != KeyboardBinding.None) {
                    shortcutKey = KeyboardBindings[binding]
                    if (shortcutKey == binding.defaultKey) shortcutPriority--
                }
                if (shortcutKey != key || shortcutPriority < priority) continue
                if (shortcutPriority > priority) {
                    priority = shortcutPriority
                    triggeredActions.clear()
                }
                triggeredActions.add(action)
            }
        }
    }
}

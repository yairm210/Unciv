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

    operator fun contains(key: KeyCharAndCode) =
        shortcuts.any { it.shortcut.key == key || it.shortcut.binding.defaultKey == key }

    open fun isActive(): Boolean = true


    /** Given that several different shortcuts could be mapped to the same key,
     *   this class decides what will actually happen when the key is pressed */
    class Resolver(val key: KeyCharAndCode) {
        private var priority = Int.MIN_VALUE
        val triggeredActions: MutableList<ActivationAction> = mutableListOf()

        fun updateFor(dispatcher: KeyShortcutDispatcher) {
            if (!dispatcher.isActive()) return

            for ((shortcut, action) in dispatcher.shortcuts) {
                // shortcutKey in shortcut is used for non-user-configurable key shortcuts
                var (binding, shortcutKey, shortcutPriority) = shortcut

                // Bindings are used for user-configurable shortcuts
                if (binding != KeyboardBinding.None) {
                    shortcutKey = KeyboardBindings[binding]
                    if (shortcutKey == binding.defaultKey) shortcutPriority--
                }

                if (shortcutKey != key) continue
                // We always want to take the highest priority action, but if there are several of the same priority we do them all
                if (shortcutPriority < priority) continue
                if (shortcutPriority > priority) {
                    priority = shortcutPriority
                    triggeredActions.clear()
                }
                triggeredActions.add(action)
            }
        }
    }
}

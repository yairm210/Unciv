package com.unciv.ui.components

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

    private data class ShortcutAction(val shortcut: KeyShortcut, val action: () -> Unit)
    private val shortcuts: MutableList<ShortcutAction> = mutableListOf()

    fun add(shortcut: KeyShortcut?, action: (() -> Unit)?) {
        if (action == null || shortcut == null) return
        shortcuts.removeIf { it.shortcut == shortcut }
        shortcuts.add(ShortcutAction(shortcut, action))
    }

    fun add(binding: KeyboardBinding, priority: Int = 1, action: (() -> Unit)?) {
        add(KeyShortcut(binding, KeyCharAndCode.UNKNOWN, priority), action)
    }

    fun add(key: KeyCharAndCode?, action: (() -> Unit)?) {
        if (key != null)
            add(KeyShortcut(KeyboardBinding.None, key), action)
    }

    fun add(char: Char?, action: (() -> Unit)?) {
        if (char != null)
            add(KeyCharAndCode(char), action)
    }

    fun add(keyCode: Int?, action: (() -> Unit)?) {
        if (keyCode != null)
            add(KeyCharAndCode(keyCode), action)
    }

    fun remove(shortcut: KeyShortcut?) {
        shortcuts.removeIf { it.shortcut == shortcut }
    }

    fun remove(binding: KeyboardBinding) {
        shortcuts.removeIf { it.shortcut.binding == binding }
    }

    fun remove(key: KeyCharAndCode?) {
        shortcuts.removeIf { it.shortcut.key == key }
    }

    fun remove(char: Char?) {
        shortcuts.removeIf { it.shortcut.key.char == char }
    }

    fun remove(keyCode: Int?) {
        shortcuts.removeIf { it.shortcut.key.code == keyCode }
    }

    open fun isActive(): Boolean = true


    class Resolver(val key: KeyCharAndCode) {
        private var priority = Int.MIN_VALUE
        val triggeredActions: MutableList<() -> Unit> = mutableListOf()

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

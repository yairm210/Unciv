package com.unciv.ui.components

open class KeyShortcutDispatcher {
    data class KeyShortcut(val key: KeyCharAndCode, val priority: Int = 0)

    private data class ShortcutAction(val shortcut: KeyShortcut, val action: () -> Unit)
    private val shortcuts: MutableList<ShortcutAction> = mutableListOf()

    fun add(shortcut: KeyShortcut?, action: (() -> Unit)?) {
        if (action == null || shortcut == null) return
        shortcuts.removeIf { it.shortcut == shortcut }
        shortcuts.add(ShortcutAction(shortcut, action))
    }

    fun add(binding: KeyboardBinding, action: (() -> Unit)?) {
        add(KeyboardBindings[binding], action)
    }

    fun add(key: KeyCharAndCode?, action: (() -> Unit)?) {
        if (key != null)
            add(KeyShortcut(key), action)
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
        remove(KeyboardBindings[binding])
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
                if (shortcut.key == key) {
                    if (shortcut.priority == priority)
                        triggeredActions.add(action)
                    else if (shortcut.priority > priority) {
                        priority = shortcut.priority
                        triggeredActions.clear()
                        triggeredActions.add(action)
                    }
                }
            }
        }
    }
}

package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.KeyCapturingButton
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer


class KeyBindingsTab(
    optionsPopup: OptionsPopup,
    private val labelWidth: Float
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val keyBindings = optionsPopup.settings.keyBindings

    // lazy triggers on activate(), not on init: init runs when Options is opened, even if we never look at this tab.
    private val groupedWidgets by lazy { createGroupedWidgets() }

    private val disclaimer = MarkupRenderer.render(listOf(
        FormattedLine("This is a work in progress.", color = "FIREBRICK", centered = true),
        FormattedLine(),
        // FormattedLine("Do not pester the developers for missing entries!"),  // little joke
        FormattedLine("Please see the Tutorial.", link = "Tutorial/Keyboard Bindings"),
        FormattedLine(separator = true),
    ), labelWidth) {
        GUI.openCivilopedia(it)
    }

    private val showCustomizedOnly = "Show customized only".toCheckBox {
        save()
        update()
    }

    init {
        top()
        pad(10f)
        defaults().pad(5f)
    }

    private fun createGroupedWidgets(): LinkedHashMap<KeyboardBinding.Category, LinkedHashMap<KeyboardBinding, KeyCapturingButton>> {
        // We want: For each category, sorted by their translated label,
        //     a sorted (by translated label) collection of all visible bindings in that category,
        //     associated with the actual UI widget (a KeyCapturingButton),
        //     and we want to easily index that by binding, so it should be a order-preserving map.
        val collator = UncivGame.Current.settings.getCollatorFromLocale()
        return KeyboardBinding.entries.asSequence()
            .filterNot { it.hidden }
            .groupBy { it.category }  // Materializes a Map<Category,List<KeyboardBinding>>
            .asSequence()
            .map { (category, bindings) ->
                category to bindings.asSequence()
                    .sortedWith(compareBy(collator) { it.label.tr() })  // sort bindings within each category
                    .map { it to getBindingWidget(category, it) }  // associate would materialize a map
                    .toMap(LinkedHashMap())  // Stuff into a map that preserves our sorted order
            }
            .sortedWith(compareBy(collator) { it.first.label.tr() })  // sort the categories
            .toMap(LinkedHashMap())
    }

    private fun update() {
        clear()
        onKeyChanged() // Initial conflict marking
        add(disclaimer).center().row()

        add(showCustomizedOnly).center().row()

        val onlyCustomized = showCustomizedOnly.isChecked
        for ((category, bindings) in groupedWidgets) {
            val filteredBindings = if (onlyCustomized)
                bindings.filter { it.value.current != it.key.defaultKey }
                else bindings
            if (filteredBindings.isEmpty()) continue
            add(getCategoryWidget(category, filteredBindings)).row()
        }
    }

    private fun getBindingWidget(category: KeyboardBinding.Category, binding: KeyboardBinding): KeyCapturingButton {
        val button = KeyCapturingButton(binding.defaultKey) { onKeyChanged() }
        button.onRightClick { BindingMenu(category, binding, button) }
        return button
    }

    private fun getCategoryWidget(
        category: KeyboardBinding.Category,
        bindings: Map<KeyboardBinding, KeyCapturingButton>
    ) = ExpanderTab(
        category.label,
        startsOutOpened = false,
        defaultPad = 0f,
        headerPad = 5f,
        // expanderWidth = labelWidth,
        persistenceID = "KeyBindings." + category.name
    ) {
        it.defaults().padTop(5f)
        for ((binding, widget) in bindings) {
            it.add(binding.label.toLabel()).padRight(10f).minWidth(labelWidth / 2)
            it.add(widget).row()
            widget.current = keyBindings[binding]
        }
    }

    private fun onKeyChanged() {
        for ((category, bindings) in groupedWidgets) {
            val conflictingKeys = getConflictingKeys(category) ?: continue
            for (widget in bindings.values) {
                widget.markConflict = widget.current in conflictingKeys
            }
        }
    }

    private data class ConflictScopeEntry(val category: KeyboardBinding.Category, val binding: KeyboardBinding, val widget: KeyCapturingButton)
    private fun getConflictScopeWidgets(category: KeyboardBinding.Category) =
        category.checkConflictsIn()
            .mapNotNull { groupedWidgets[it] }
            .flatMap { scopeMap -> scopeMap.map { ConflictScopeEntry(it.key.category, it.key, it.value) } }

    private fun getConflictingKeys(category: KeyboardBinding.Category): Set<KeyCharAndCode>? {
        val widgetsInScope = getConflictScopeWidgets(category)
        if (widgetsInScope.none()) return null // No need for conflict marking in entire category

        val usedKeys = mutableSetOf<KeyCharAndCode>()
        val conflictingKeys = mutableSetOf<KeyCharAndCode>()

        for ((scopeCategory, _, widget) in widgetsInScope) {
            val key = widget.current
            if (key == KeyCharAndCode.UNKNOWN) continue
            // We shall not use any key of a different category in scope,
            // nor use a key within this category twice - if this category _is_ in scope.
            if (key in usedKeys || scopeCategory != category)
                conflictingKeys += key
            else
                usedKeys += key
        }

        // Now conflictingKeys are **potential** conflicts, some are used elsewhere but not in this category
        // usedKeys are all keys currently used in this category - unless it is not included in its own scope (unit actions)
        if (category !in category.checkConflictsIn())
            usedKeys.addAll(groupedWidgets[category].orEmpty().map { it.value.current })
        return conflictingKeys.intersect(usedKeys)
    }

    fun save() {
        if (!hasChildren()) return // We never initialized the current values, better not save - all widgets still have current==unbound
        for ((binding, widget) in groupedWidgets.asSequence().flatMap { it.value.entries }) {
            keyBindings[binding] = widget.current
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        save()
    }

    private inner class BindingMenu(
        private val category: KeyboardBinding.Category,
        private val binding: KeyboardBinding,
        private val widget: KeyCapturingButton
    ) : AnimatedMenuPopup(stage, getActorTopRight(widget)) {
        private lateinit var nameField: TextField

        override fun createContentTable() = super.createContentTable()!!.apply {
            if (widget.markConflict)
                addConflictLabel()
            if (widget.current != KeyCharAndCode.UNKNOWN)
                addSetToButton("No binding", KeyCharAndCode.UNKNOWN)
            if (widget.current != KeyCharAndCode.BACK)
                addSetToButton("Assign ESC/BACK", KeyCharAndCode.BACK)
            addNameField()
            addSeparator()
            if (widget.current != binding.defaultKey)
                addSetToButton("Reset '[${binding.label}]' to default", binding.defaultKey)
            addResetButton("Reset '[${category.label}]' to defaults", groupedWidgets[category]!!.asIterable())
            addResetButton("Reset all to defaults", groupedWidgets.flatMap { it.value.entries } )
        }

        fun Table.addConflictLabel() {
            val conflictingKeys = getConflictingKeys(category) ?: return
            val text = getConflictScopeWidgets(category)
                .filter { it.binding != binding && it.widget.current in conflictingKeys }
                .joinToString(prefix = "Conflicts with: «FIREBRICK»[", postfix = "]«»!") {
                    if (it.category == category) it.binding.label.tr(hideIcons = true)
                    else "{${it.category.label}}: {${it.binding.label}}".tr(hideIcons = true)
                }
            add(ColorMarkupLabel(text).apply { wrap = true }).maxWidth(labelWidth).row()
            addSeparator()
        }

        fun Table.addSetToButton(text: String, key: KeyCharAndCode) {
            add(getButton(text, KeyboardBinding.None) {
                widget.current = key
                onKeyChanged()
            }).row()
        }

        fun Table.addNameField() {
            nameField = UncivTextField("Key name", widget.current.toString()) { focused ->
                if (focused) return@UncivTextField
                val key = KeyCharAndCode.parse(nameField.text)
                if (key == KeyCharAndCode.UNKNOWN) return@UncivTextField
                widget.current = key
                onKeyChanged()
            }
            add(nameField).growX().row()
        }

        fun Table.addResetButton(text: String, widgets: Iterable<Map.Entry<KeyboardBinding, KeyCapturingButton>>) {
            add(getButton(text, KeyboardBinding.None) {
                ConfirmPopup(stage, "Are you sure you want to reset these key bindings to defaults?", text, false) {
                    for ((binding, widget) in widgets) {
                        widget.current = binding.defaultKey
                    }
                    onKeyChanged()
                }.open(force = true)
            }).row()
        }
    }
}

package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.KeyCapturingButton
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer


class KeyBindingsTab(
    optionsPopup: OptionsPopup,
    private val labelWidth: Float
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val keyBindings = optionsPopup.settings.keyBindings
    private val groupedWidgets: LinkedHashMap<KeyboardBinding.Category, LinkedHashMap<KeyboardBinding, KeyCapturingButton>>
    private val disclaimer = MarkupRenderer.render(listOf(
        FormattedLine("This is a work in progress.", color = "#b22222", centered = true),  // FIREBRICK
        FormattedLine(),
        // FormattedLine("Do not pester the developers for missing entries!"),  // little joke
        FormattedLine("Please see the Tutorial.", link = "Tutorial/Keyboard Bindings"),
        FormattedLine(separator = true),
    ), labelWidth) {
        // This ruleset is a kludge - but since OptionPopup can be called from anywhere, getting the relevant one is a chore
        //TODO better pedia call architecture, or a tutorial render method once that has markup capability
        GUI.pushScreen(CivilopediaScreen(RulesetCache.getVanillaRuleset(), link = it))
    }

    init {
        top()
        pad(10f)
        defaults().pad(5f)

        val collator = UncivGame.Current.settings.getCollatorFromLocale()
        groupedWidgets = KeyboardBinding.values().asSequence()
                .filterNot { it.hidden }
                .groupBy { it.category }  // Materializes a Map<Category,List<KeyboardBinding>>
                .asSequence()
                .map { (category, bindings) ->
                    category to bindings.asSequence()
                        .sortedWith(compareBy(collator) { it.label.tr() })
                        .map { it to KeyCapturingButton(it.defaultKey) { onKeyHit() } }  // associate would materialize a map
                        .toMap(LinkedHashMap())
                }
                .sortedBy { it.first.name.tr() }
                .toMap(LinkedHashMap())
    }

    private fun update() {
        clear()
        add(disclaimer).center().row()

        for ((category, bindings) in groupedWidgets)
            add(getCategoryWidget(category, bindings)).row()
    }

    private fun getCategoryWidget(
        category: KeyboardBinding.Category,
        bindings: LinkedHashMap<KeyboardBinding, KeyCapturingButton>
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

    private fun onKeyHit() {
        for ((category, bindings) in groupedWidgets) {
            val scope = category.checkConflictsIn()
            if (scope.none()) continue

            val usedKeys = mutableSetOf<KeyCharAndCode>()
            val conflictingKeys = mutableSetOf<KeyCharAndCode>()
            val widgetsInScope = scope
                .mapNotNull { groupedWidgets[it] }
                .flatMap { scopeMap -> scopeMap.map { it.key.category to it.value } }

            for ((scopeCategory ,widget) in widgetsInScope) {
                val key = widget.current
                // We shall not use any key of a different category in scope,
                // nor use a key within this category twice - if this category _is_ in scope.
                if (key in usedKeys || scopeCategory != category)
                    conflictingKeys += key
                else
                    usedKeys += key
            }

            for (widget in bindings.values) {
                widget.markConflict = widget.current in conflictingKeys
            }
        }
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
}

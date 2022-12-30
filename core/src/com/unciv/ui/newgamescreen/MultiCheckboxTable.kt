package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toCheckBox

/**
 * A widget containing one expander for check boxes.
 *
 * @param title title of the ExpanderTab
 * @param persistenceID persistenceID for the ExpanderTab
 * @param values In/out set of checked boxes, modified in place
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class MultiCheckboxTable(
    title: String,
    persistenceID: String,
    private val values: HashSet<String>,
    isPortrait: Boolean = false,
    onUpdate: (String) -> Unit
): Table(){
    private val checkBoxes = ArrayList<CheckBox>()

    init {

        for (name in values) {
            val checkBox = name.toCheckBox(true)
            checkBox.onChange {
                if (checkBoxChanged(checkBox, name)) {
                    onUpdate(name)
                }
            }
            checkBoxes.add(checkBox)
        }

        val padTop = if (isPortrait) 0f else 16f

        if (checkBoxes.any()) {
            add(ExpanderTab(title, persistenceID = persistenceID) {
                it.defaults().pad(5f,0f)
                for (checkbox in checkBoxes) it.add(checkbox).row()
            }).padTop(padTop).growX().row()
        }
    }

    private fun checkBoxChanged(
        checkBox: CheckBox,
        name: String
    ): Boolean {
        if (checkBox.isChecked) {
            values.add(name)
        } else {
            values.remove(name)
        }

        return true
    }
}

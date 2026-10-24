package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.ExpanderTab

/**
 * A widget containing one expander for check boxes.
 *
 * @param title title of the ExpanderTab
 * @param persistenceID persistenceID for the ExpanderTab
 * @param values In/out set of checked boxes, modified in place
 * @param onUpdate Callback, parameter is the String value of the check box that changed.
 */
class MultiCheckboxTable(
    title: String,
    persistenceID: String,
    private val values: HashSet<String>,
    onUpdate: (String) -> Unit
) : Table() {
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

        if (checkBoxes.any()) {
            add(ExpanderTab(title, persistenceID = persistenceID, startsOutOpened = false) {
                it.defaults().pad(5f,0f)
                for (checkbox in checkBoxes) it.add(checkbox).row()
            }).pad(0f).padTop(10f).colspan(2).growX().row()
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

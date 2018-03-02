package com.unciv.ui.cityscreen

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.ImageGetter

class YieldGroup : HorizontalGroup() {

    fun setStats(stats: Stats) {
        clearChildren()
        for (entry in stats.toHashMap().filter { it.value>0 }) {
            addActor(getStatIconsTable(entry.key.toString(), entry.value.toInt()))
        }
        pack()
    }

    private fun getStatIconsTable(statName: String, number: Int): Table {
        val table = Table()
        when (number) {
            1 -> table.add(ImageGetter.getStatIcon(statName))
            2 -> {
                table.add(ImageGetter.getStatIcon(statName)).row()
                table.add(ImageGetter.getStatIcon(statName))
            }
            3 -> {
                table.add(ImageGetter.getStatIcon(statName)).colspan(2).row()
                table.add(ImageGetter.getStatIcon(statName))
                table.add(ImageGetter.getStatIcon(statName))
            }
            4 -> {
                table.add(ImageGetter.getStatIcon(statName))
                table.add(ImageGetter.getStatIcon(statName)).row()
                table.add(ImageGetter.getStatIcon(statName))
                table.add(ImageGetter.getStatIcon(statName))
            }
            else -> {
                val largeImage = ImageGetter.getStatIcon(statName)
                table.add(largeImage).size(largeImage.width * 1.5f, largeImage.height * 1.5f)
            }
        }
        table.pack()
        return table
    }
}

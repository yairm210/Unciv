package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.stats.Stats
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.surroundWithCircle

class YieldGroup : HorizontalGroup() {
    init {
        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    var currentStats = Stats()

    fun setStats(stats: Stats) {
        if (currentStats.equals(stats)) return // don't need to update - this is a memory and time saver!
        currentStats = stats
        clearChildren()
        for ((stat, amount) in stats) {
            if (amount > 0f)  // Defense against upstream bugs - negatives would show as "lots"
                addActor(getStatIconsTable(stat.name, amount.toInt()))
        }
        pack()
    }

    fun getIcon(statName: String) =
            ImageGetter.getStatIcon(statName).surroundWithCircle(20f)
                    .apply { circle.color = Color.BLACK;circle.color.a = 0.5f }

    private fun getStatIconsTable(statName: String, number: Int): Table {
        val table = Table()
        when (number) {
            1 -> table.add(getIcon(statName))
            2 -> {
                table.add(getIcon(statName)).row()
                table.add(getIcon(statName))
            }
            3 -> {
                table.add(getIcon(statName)).colspan(2).row()
                table.add(getIcon(statName))
                table.add(getIcon(statName))
            }
            4 -> {
                table.add(getIcon(statName))
                table.add(getIcon(statName)).row()
                table.add(getIcon(statName))
                table.add(getIcon(statName))
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

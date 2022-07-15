package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.surroundWithCircle

class YieldGroup : HorizontalGroup() {
    init {
        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    val isSimplified = true

    var currentStats = Stats()

    fun setStats(stats: Stats) {
        if (currentStats.equals(stats)) return // don't need to update - this is a memory and time saver!
        currentStats = stats
        clearChildren()
        for ((stat, amount) in stats) {
            if (amount > 0f)  // Defense against upstream bugs - negatives would show as "lots"
                addActor(getStatIconsTable(stat, amount.toInt()))
        }
        pack()
    }

    fun getIcon(stat: Stat): IconCircleGroup {
        if (isSimplified) return ImageGetter.getCircle().apply { color = stat.color }
            .surroundWithCircle(15f)
            .apply { circle.color = Color.BLACK;circle.color.a = 0.5f }
        return ImageGetter.getStatIcon(stat.name).surroundWithCircle(20f)
            .apply { circle.color = Color.BLACK;circle.color.a = 0.5f }
    }

    private fun getStatIconsTable(stat: Stat, number: Int): Table {
        val table = Table()
        when (number) {
            1 -> table.add(getIcon(stat))
            2 -> {
                table.add(getIcon(stat)).row()
                table.add(getIcon(stat))
            }
            3 -> {
                table.add(getIcon(stat)).colspan(2).row()
                table.add(getIcon(stat))
                table.add(getIcon(stat))
            }
            4 -> {
                table.add(getIcon(stat))
                table.add(getIcon(stat)).row()
                table.add(getIcon(stat))
                table.add(getIcon(stat))
            }
            else -> {
                val largeImage = ImageGetter.getStatIcon(stat.name)
                table.add(largeImage).size(largeImage.width * 1.5f, largeImage.height * 1.5f)
            }
        }
        table.pack()
        return table
    }
}

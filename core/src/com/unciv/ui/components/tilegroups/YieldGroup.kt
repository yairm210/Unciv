package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.addToCenter
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel

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
            ImageGetter.getStatIcon(statName).surroundWithCircle(12f, circleImageLocation = "StatIcons/Circle")
                    .apply { circle.color = ImageGetter.CHARCOAL; circle.color.a = 0.5f }

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

                val group = Group().apply { setSize(22f, 22f) }
                val largeImage = ImageGetter.getStatIcon(statName).surroundWithCircle(22f)
                    .apply { circle.color = ImageGetter.CHARCOAL;circle.color.a = 0.5f }
                group.addToCenter(largeImage)

                if (number > 5) {
                    val text = if (number < 10) number.tr() else "*"
                    val label = text.toLabel(
                        fontSize = 8,
                        fontColor = Color.WHITE,
                        alignment = Align.center
                    )
                    val amountGroup = label.surroundWithCircle(10f, true, ImageGetter.CHARCOAL)
                    label.y -= 0.5f
                    amountGroup.x = group.width - amountGroup.width * 3 / 4
                    amountGroup.y = -amountGroup.height / 4
                    group.addActor(amountGroup)
                }

                table.add(group)
            }
        }
        table.pack()
        return table
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    override fun act(delta: Float) = super.act(delta)
}

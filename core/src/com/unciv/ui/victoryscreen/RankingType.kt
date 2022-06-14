package com.unciv.ui.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.ui.images.ImageGetter

enum class RankingType(val getImage: ()->Image?) {
    // production, gold, happiness, and culture already have icons added when the line is `tr()`anslated
        Score({ ImageGetter.getImage("OtherIcons/Cultured").apply { color = Color.FIREBRICK } }),
        Population({ ImageGetter.getStatIcon("Population") }),
        Crop_Yield({ ImageGetter.getStatIcon("Food") }),
        Production({ null }),
        Gold({ null }),
        Territory({ ImageGetter.getImage("OtherIcons/Hexagon") }),
        Force({ ImageGetter.getImage("OtherIcons/Shield") }),
        Happiness({ null }),
        Technologies({ ImageGetter.getStatIcon("Science") }),
        Culture({ null })
    }

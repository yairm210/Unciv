package validation

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Building
import com.unciv.models.skins.SkinConfig
import com.unciv.utils.ScreenOrientation
import com.unciv.ui.screens.pickerscreens.PromotionScreenColors

object JsonContract {
    @JvmStatic
    fun main(args: Array<String>) {
        val settings = json().fromJson(GameSettings::class.java, """
            {"language":"Hebrew","showMinimap":false,"soundEffectsVolume":0.25,"displayOrientation":"Portrait",
             "autoPlay":{"autoPlayMaxTurns":31},"multiplayer":{"userId":"fixture-user"}}
        """.trimIndent())
        check(settings.language == "Hebrew" && !settings.showMinimap)
        check(settings.displayOrientation == ScreenOrientation.Portrait)
        check(settings.soundEffectsVolume == 0.25f && settings.autoPlay.autoPlayMaxTurns == 31)
        check(settings.multiplayer.getUserId() == "fixture-user")
        println("settings read")
        val settingsCopy = json().fromJson(GameSettings::class.java, json().toJson(settings))
        check(settingsCopy.language == settings.language)
        check(settingsCopy.displayOrientation == ScreenOrientation.Portrait)
        check(settingsCopy.multiplayer.getUserId() == "fixture-user")
        check(settingsCopy.autoPlay.autoPlayMaxTurns == 31)

        println("settings round trip")
        val game = json().fromJson(GameInfo::class.java, """
            {"turns":17,"gameId":"fixture-game","unitNamesTaken":["עברית","العربية"],
             "victoryData":{"winningCiv":"Rome","victoryType":"Scientific","victoryTurn":16}}
        """.trimIndent())
        check(game.turns == 17 && game.gameId == "fixture-game")
        check(game.unitNamesTaken == listOf("עברית", "العربية"))
        check(game.victoryData?.winningCiv == "Rome" && game.victoryData?.victoryTurn == 16)
        println("game read")
        val gameCopy = json().fromJson(GameInfo::class.java, json().toJson(game))
        check(gameCopy.turns == 17 && gameCopy.gameId == "fixture-game")
        check(gameCopy.victoryData == game.victoryData)
        check(gameCopy.unitNamesTaken == game.unitNamesTaken)

        println("game round trip")
        val building = json().fromJson(Building::class.java,
            """{"name":"Fixture library","cost":73,"science":2.5,"uniques":["Fixture unique"]}""")
        check(building.name == "Fixture library" && building.cost == 73)
        check(building.science == 2.5f && building.uniques == listOf("Fixture unique"))
        println("building read")
        // Rulesets are loaded from JSON; unlike saves/settings, Building isn't serialized by the game.

        val skin = json().fromJson(SkinConfig::class.java, """
            {"baseColor":{"r":0.25,"g":0.5,"b":0.75,"a":1},
             "skinVariants":{"Fixture":{"image":"fixture-image","alpha":0.5}}}
        """.trimIndent())
        check(skin.baseColor.r == 0.25f && skin.baseColor.g == 0.5f)
        check(skin.skinVariants["Fixture"]?.image == "fixture-image")
        check(skin.skinVariants["Fixture"]?.alpha == 0.5f)
        val promotionColors = json().fromJson(Any::class.java, """
            {"class":"com.unciv.ui.screens.pickerscreens.PromotionScreenColors",
             "selected":{"r":0.125,"g":0.25,"b":0.5,"a":1}}
        """.trimIndent()) as PromotionScreenColors
        check(promotionColors.selected.r == 0.125f && promotionColors.selected.b == 0.5f)
        println("Unciv JSON contract passed")
    }
}

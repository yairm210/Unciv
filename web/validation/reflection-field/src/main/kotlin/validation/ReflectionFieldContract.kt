package validation

import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.migrateCivID
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.DeprecatedUniqueType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.UncivDateFormat.formatDate
import java.util.Date

object ReflectionFieldContract {
    @JvmStatic
    fun main(args: Array<String>) {
        val data = FileTextureData(null, null, null, false)
        val pixmap = FileTextureData::class.java.getDeclaredField("pixmap")
        check(pixmap.name == "pixmap")
        pixmap.isAccessible = true
        check(pixmap.get(data) == null)
        pixmap.set(data, null)
        check(pixmap.get(data) == null)

        check(MapResourceSetting.activeLabels() == listOf("Sparse", "Default", "Abundant"))
        check(Countables.valueOf("CityStates").getDeprecationAnnotation() != null)
        check(DeprecatedUniqueType.valueOf("FoodConsumptionBySpecialists").getDeprecationAnnotation() != null)
        // Current UniqueType has no deprecated constants; its reflective lookup must return null.
        check(UniqueType.Stats.getDeprecationAnnotation() == null)

        val browserSave = BrowserSaveFixture.text()
        val versionOnly = json().fromJson(com.unciv.logic.GameInfoSerializationVersion::class.java, browserSave)
        check(versionOnly.version.number == 4)

        // Mirrors LoadOrSaveScreen.showSaveInfo: parse its lightweight view, apply
        // the civilization ID migration, build player labels, and format save time.
        val preview = json().fromJson(GameInfoPreview::class.java, browserSave)
        preview.migrateCivID()
        check(preview.turns == 1 && preview.currentPlayer == "England")
        val playerCivs = preview.civilizations.filter { it.isPlayerCivilization() }
        check(playerCivs.map { it.civName } == listOf("England"))
        check(playerCivs.single().civID == "England")
        check(Date(1_793_937_871_176L).formatDate().isNotBlank())
        check(preview.gameParameters.mods.isEmpty())

        val originalGame = json().fromJson(GameInfo::class.java, browserSave)
        check(originalGame.turns == 1 && originalGame.currentPlayer == "England")
        val originalEngland = originalGame.civilizations.first { it.civID == "England" }
        check(originalEngland.cities.any { it.name == "London" })

        // The captured browser save predates Vector2 metadata and wrote these bounds as empty
        // objects. Give that same complete payload nonzero saved values to test both read and write.
        val emptyBounds = "\"exploredRegion\":{\"topLeft\":{},\"bottomRight\":{}}"
        check(browserSave.windowed(emptyBounds.length).count { it == emptyBounds } == 1)
        val populatedSave = browserSave.replaceFirst(
            emptyBounds,
            "\"exploredRegion\":{\"topLeft\":{\"x\":11,\"y\":7},\"bottomRight\":{\"x\":-5,\"y\":-3}}"
        )
        val game = json().fromJson(GameInfo::class.java, populatedSave)
        val england = game.civilizations.first { it.civID == "England" }
        check(england.cities.any { it.name == "London" })
        assertExploredBounds(england, 11f, 7f, -5f, -3f)

        val serialized = json().toJson(game)
        val serializedRoot = JsonReader().parse(serialized)
        val serializedEngland = serializedRoot.get("civilizations").first { it.getString("civID") == "England" }
        val serializedBounds = serializedEngland.get("exploredRegion")
        check(serializedBounds.get("topLeft").getFloat("x") == 11f)
        check(serializedBounds.get("topLeft").getFloat("y") == 7f)
        check(serializedBounds.get("bottomRight").getFloat("x") == -5f)
        check(serializedBounds.get("bottomRight").getFloat("y") == -3f)

        val roundTrip = json().fromJson(GameInfo::class.java, serialized)
        val roundTripEngland = roundTrip.civilizations.first { it.civID == "England" }
        check(roundTrip.turns == 1 && roundTrip.currentPlayer == "England")
        check(roundTripEngland.cities.any { it.name == "London" })
        assertExploredBounds(roundTripEngland, 11f, 7f, -5f, -3f)
        println("Unciv reflective field contract passed")
    }

    private fun assertExploredBounds(civ: com.unciv.logic.civilization.Civilization,
                                    leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        val region = civ.exploredRegion
        val topLeftField = region.javaClass.getDeclaredField("topLeft").apply { isAccessible = true }
        val bottomRightField = region.javaClass.getDeclaredField("bottomRight").apply { isAccessible = true }
        val topLeft = topLeftField.get(region) as Vector2
        val bottomRight = bottomRightField.get(region) as Vector2
        check(topLeft.x == leftX && topLeft.y == leftY)
        check(bottomRight.x == rightX && bottomRight.y == rightY)
    }
}

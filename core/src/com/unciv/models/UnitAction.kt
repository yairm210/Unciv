package com.unciv.models

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.darken


/** Unit Actions - class - carries dynamic data and actual execution.
 * Static properties are in [UnitActionType].
 * Note this is for the buttons offering actions, not the ongoing action stored with a [MapUnit][com.unciv.logic.map.MapUnit]
 */
data class UnitAction(
        val type: UnitActionType,
        val title: String = type.value,
        val isCurrentAction: Boolean = false,
        val uncivSound: UncivSound = type.uncivSound,
        val action: (() -> Unit)? = null
) {
    fun getIcon(): Actor {
        if (type.imageGetter != null) return type.imageGetter.invoke()
        return when (type) {
            UnitActionType.Upgrade -> {
                ImageGetter.getUnitIcon(title.getPlaceholderParameters()[0])
            }
            UnitActionType.Create -> {
                ImageGetter.getImprovementIcon(title.getPlaceholderParameters()[0])
            }
            UnitActionType.SpreadReligion -> {
                val religionName = title.getPlaceholderParameters()[0]
                ImageGetter.getReligionImage(
                    if (ImageGetter.religionIconExists(religionName)) religionName
                    else "Pantheon"
                ).apply { color = Color.BLACK }
            }
            UnitActionType.Fortify, UnitActionType.FortifyUntilHealed -> {
                val match = fortificationRegex.matchEntire(title)
                val percentFortified = match?.groups?.get(1)?.value?.toInt() ?: 0
                ImageGetter.getImage("OtherIcons/Shield").apply {
                    color = Color.GREEN.darken(1f - percentFortified / 80f)
                }
            }
            else -> ImageGetter.getImage("OtherIcons/Star").apply { color = Color.BLACK }
        }
    }
    companion object {
        private val fortificationRegex = Regex(""".* (\d+)%""")
    }
}

/** Unit Actions - generic enum with static properties
 *
 * @param value         _default_ label to display, can be overridden in UnitAction instantiation
 * @param imageGetter   optional lambda to get an Icon - `null` if icon is dependent on outside factors and needs special handling
 * @param key           keyboard binding - can be a [KeyCharAndCode], a [Char], or omitted.
 * @param uncivSound    _default_ sound, can be overridden in UnitAction instantiation
 */

// Note for Creators of new UnitActions: If your action uses a dynamic label overriding UnitActionType.value,
// then you need to teach [com.unciv.testing.TranslationTests.allUnitActionsHaveTranslation] how to deal with it!

enum class UnitActionType(
    val value: String,
    val imageGetter: (()-> Actor)?,
    val key: KeyCharAndCode,
    val uncivSound: UncivSound = UncivSound.Click
) {
    SwapUnits("Swap units",
        { ImageGetter.getImage("OtherIcons/Swap") }, 'y'),
    Automate("Automate",
        { ImageGetter.getUnitIcon("Great Engineer") }, 'm'),
    StopAutomation("Stop automation",
        { ImageGetter.getImage("OtherIcons/Stop") }, 'm'),
    StopMovement("Stop movement",
        { imageGetStopMove() }, '.'),
    Sleep("Sleep",
        { ImageGetter.getImage("OtherIcons/Sleep") }, 'f'),
    SleepUntilHealed("Sleep until healed",
        { ImageGetter.getImage("OtherIcons/Sleep") }, 'h'),
    // Note: Both Fortify actions are a special case. The button starting fortification uses the `value` here,
    // the button label as shown when the unit is already fortifying is "Fortification".tr() + " nn%".
    // For now we keep it simple, and the unit test `allUnitActionsHaveTranslation` does not know about the latter.
    Fortify("Fortify",
        null, 'f', UncivSound.Fortify),
    FortifyUntilHealed("Fortify until healed",
        null, 'h', UncivSound.Fortify),
    Explore("Explore",
        { ImageGetter.getUnitIcon("Scout") }, 'x'),
    StopExploration("Stop exploration",
        { ImageGetter.getImage("OtherIcons/Stop") }, 'x'),
    Promote("Promote",
        { imageGetPromote() }, 'o', UncivSound.Promote),
    Upgrade("Upgrade",
        null, 'u', UncivSound.Upgrade),
    Pillage("Pillage",
        { ImageGetter.getImage("OtherIcons/Pillage") }, 'p'),
    Paradrop("Paradrop",
        { ImageGetter.getUnitIcon("Paratrooper") }, 'p'),
    SetUp("Set up",
        { ImageGetter.getUnitIcon("Catapult") }, 't', UncivSound.Setup),
    FoundCity("Found city",
        { ImageGetter.getUnitIcon(Constants.settler) }, 'c', UncivSound.Silent),
    ConstructImprovement("Construct improvement",
        { ImageGetter.getUnitIcon(Constants.worker) }, 'i'),
    Create("Create",
        null, 'i', UncivSound.Chimes),
    HurryResearch("Hurry Research",
        { ImageGetter.getUnitIcon("Great Scientist") }, 'g', UncivSound.Chimes),
    StartGoldenAge("Start Golden Age",
        { ImageGetter.getUnitIcon("Great Artist") }, 'g', UncivSound.Chimes),
    HurryWonder("Hurry Wonder",
        { ImageGetter.getUnitIcon("Great Engineer") }, 'g', UncivSound.Chimes),
    HurryBuilding("Hurry Construction",
        { ImageGetter.getUnitIcon("Great Engineer") }, 'g', UncivSound.Chimes),
    ConductTradeMission("Conduct Trade Mission",
        { ImageGetter.getUnitIcon("Great Merchant") }, 'g', UncivSound.Chimes),
    FoundReligion("Found a Religion",
        { ImageGetter.getUnitIcon("Great Prophet") }, 'g', UncivSound.Choir),
    TriggerUnique("Trigger unique",
        { ImageGetter.getImage("OtherIcons/Star") }, 'g', UncivSound.Chimes),
    SpreadReligion("Spread Religion",
        null, 'g', UncivSound.Choir),
    RemoveHeresy("Remove Heresy",
        { ImageGetter.getImage("OtherIcons/Remove Heresy") }, 'h', UncivSound.Fire),
    EnhanceReligion("Enhance a Religion",
        { ImageGetter.getUnitIcon("Great Prophet") }, 'g', UncivSound.Choir),
    DisbandUnit("Disband unit",
        { ImageGetter.getImage("OtherIcons/DisbandUnit") }, KeyCharAndCode.DEL),
    GiftUnit("Gift unit",
        { ImageGetter.getImage("OtherIcons/Present") }, UncivSound.Silent),
    Wait("Wait",
        null, 'z', UncivSound.Silent),
    ShowAdditionalActions("Show more",
        { imageGetShowMore() }, KeyCharAndCode(Input.Keys.PAGE_DOWN)),
    HideAdditionalActions("Back",
        { imageGetHideMore() }, KeyCharAndCode(Input.Keys.PAGE_UP)),
    AddInCapital( "Add in capital",
        { ImageGetter.getUnitIcon("SS Cockpit")}, 'g', UncivSound.Chimes),
    ;

    // Allow shorter initializations
    constructor(value: String, imageGetter: (() -> Actor)?, key: Char, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode(key), uncivSound)
    constructor(value: String, imageGetter: (() -> Actor)?, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode.UNKNOWN, uncivSound)

    companion object {
        // readability factories
        private fun imageGetStopMove() = ImageGetter.getStatIcon("Movement").apply { color = Color.RED }
        private fun imageGetPromote() = ImageGetter.getImage("OtherIcons/Star").apply { color = Color.GOLD }
        private fun imageGetShowMore() = ImageGetter.getArrowImage(Align.right).apply { color = Color.BLACK }
        private fun imageGetHideMore() = ImageGetter.getArrowImage(Align.left).apply { color = Color.BLACK }
    }
}

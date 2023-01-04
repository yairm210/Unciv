package com.unciv.models

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.surroundWithThinCircle


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
            .surroundWithCircle(20f)
            .surroundWithThinCircle()
        return when (type) {
            UnitActionType.Create -> {
                ImageGetter.getImprovementIcon(title.getPlaceholderParameters()[0])
            }
            UnitActionType.SpreadReligion -> {
                val religionName = title.getPlaceholderParameters()[0]
                ImageGetter.getReligionImage(
                    if (ImageGetter.religionIconExists(religionName)) religionName
                    else "Pantheon"
                ).apply { color = Color.BLACK }
                    .surroundWithCircle(20f).surroundWithThinCircle()
            }
            else -> ImageGetter.getImage("UnitActionIcons/Star").apply { color = Color.BLACK }
        }
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
    val isSkippingToNextUnit: Boolean = true,
    val uncivSound: UncivSound = UncivSound.Click
) {
    SwapUnits("Swap units",
        { ImageGetter.getImage("UnitActionIcons/Swap") }, 'y', false),
    Automate("Automate",
        { ImageGetter.getImage("UnitActionIcons/Automate") }, 'm'),
    StopAutomation("Stop automation",
        { ImageGetter.getImage("UnitActionIcons/Stop") }, 'm', false),
    StopMovement("Stop movement",
        { ImageGetter.getImage("UnitActionIcons/StopMove") }, '.', false),
    Sleep("Sleep",
        { ImageGetter.getImage("UnitActionIcons/Sleep") }, 'f'),
    SleepUntilHealed("Sleep until healed",
        { ImageGetter.getImage("UnitActionIcons/Sleep") }, 'h'),
    Fortify("Fortify",
        { ImageGetter.getImage("UnitActionIcons/Fortify") }, 'f', UncivSound.Fortify),
    FortifyUntilHealed("Fortify until healed",
        { ImageGetter.getImage("UnitActionIcons/FortifyUntilHealed") }, 'h', UncivSound.Fortify),
    Explore("Explore",
        { ImageGetter.getImage("UnitActionIcons/Explore") }, 'x'),
    StopExploration("Stop exploration",
        { ImageGetter.getImage("UnitActionIcons/Stop") }, 'x', false),
    Promote("Promote",
        { ImageGetter.getImage("UnitActionIcons/Promote") }, 'o', false, UncivSound.Promote),
    Upgrade("Upgrade",
        { ImageGetter.getImage("UnitActionIcons/Upgrade") }, 'u', UncivSound.Upgrade),
    Pillage("Pillage",
        { ImageGetter.getImage("UnitActionIcons/Pillage") }, 'p', false),
    Paradrop("Paradrop",
        { ImageGetter.getImage("UnitActionIcons/Paradrop") }, 'p', false),
    AirSweep("Air Sweep",
        { ImageGetter.getImage("UnitActionIcons/AirSweep") }, 'a', false),
    SetUp("Set up",
        { ImageGetter.getImage("UnitActionIcons/SetUp") }, 't', false, UncivSound.Setup),
    FoundCity("Found city",
        { ImageGetter.getImage("UnitActionIcons/FoundCity") }, 'c', UncivSound.Silent),
    ConstructImprovement("Construct improvement",
        { ImageGetter.getImage("UnitActionIcons/ConstructImprovement") }, 'i'),
    Repair(Constants.repair,
        { ImageGetter.getImage("UnitActionIcons/Repair") }, 'r', UncivSound.Construction),
    Create("Create",
        null, 'i', UncivSound.Chimes),
    HurryResearch("Hurry Research",
        { ImageGetter.getImage("UnitActionIcons/HurryResearch") }, 'g', UncivSound.Chimes),
    StartGoldenAge("Start Golden Age",
        { ImageGetter.getImage("UnitActionIcons/StartGoldenAge") }, 'g', UncivSound.Chimes),
    HurryWonder("Hurry Wonder",
        { ImageGetter.getImage("UnitActionIcons/HurryConstruction") }, 'g', UncivSound.Chimes),
    HurryBuilding("Hurry Construction",
        { ImageGetter.getImage("UnitActionIcons/HurryConstruction") }, 'g', UncivSound.Chimes),
    ConductTradeMission("Conduct Trade Mission",
        { ImageGetter.getImage("UnitActionIcons/ConductTradeMission") }, 'g', UncivSound.Chimes),
    FoundReligion("Found a Religion",
        { ImageGetter.getImage("UnitActionIcons/FoundReligion") }, 'g', UncivSound.Choir),
    TriggerUnique("Trigger unique",
        { ImageGetter.getImage("UnitActionIcons/Star") }, 'g', false, UncivSound.Chimes),
    SpreadReligion("Spread Religion",
        null, 'g', UncivSound.Choir),
    RemoveHeresy("Remove Heresy",
        { ImageGetter.getImage("UnitActionIcons/RemoveHeresy") }, 'h', UncivSound.Fire),
    EnhanceReligion("Enhance a Religion",
        { ImageGetter.getImage("UnitActionIcons/EnhanceReligion") }, 'g', UncivSound.Choir),
    DisbandUnit("Disband unit",
        { ImageGetter.getImage("UnitActionIcons/DisbandUnit") }, KeyCharAndCode.DEL, false),
    GiftUnit("Gift unit",
        { ImageGetter.getImage("UnitActionIcons/Present") }, UncivSound.Silent),
    Wait("Wait",
        { ImageGetter.getImage("UnitActionIcons/Wait") }, 'z', UncivSound.Silent),
    ShowAdditionalActions("Show more",
        { ImageGetter.getImage("UnitActionIcons/ShowMore") }, KeyCharAndCode(Input.Keys.PAGE_DOWN), false),
    HideAdditionalActions("Back",
        { ImageGetter.getImage("UnitActionIcons/HideMore") }, KeyCharAndCode(Input.Keys.PAGE_UP), false),
    AddInCapital( "Add in capital",
        { ImageGetter.getImage("UnitActionIcons/AddInCapital")}, 'g', UncivSound.Chimes),
    ;

    // Allow shorter initializations
    constructor(value: String, imageGetter: (() -> Actor)?, key: Char, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode(key), true, uncivSound)
    constructor(value: String, imageGetter: (() -> Actor)?, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode.UNKNOWN, true,uncivSound)
    constructor(value: String, imageGetter: (() -> Actor)?, key: Char, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode(key), isSkippingToNextUnit, uncivSound)
    constructor(value: String, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, KeyCharAndCode.UNKNOWN, isSkippingToNextUnit, uncivSound)


}

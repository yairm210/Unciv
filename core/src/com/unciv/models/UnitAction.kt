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
        if (type.imageGetter != null)
            return type.imageGetter.invoke()
        return when (type) {
            UnitActionType.Create -> {
                ImageGetter.getImprovementPortrait(title.getPlaceholderParameters()[0])
            }
            UnitActionType.SpreadReligion -> {
                val religionName = title.getPlaceholderParameters()[0]
                ImageGetter.getReligionPortrait(
                    if (ImageGetter.religionIconExists(religionName)) religionName
                    else "Pantheon", 20f
                )
            }
            else -> ImageGetter.getUnitActionPortrait("Star")
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
        { ImageGetter.getUnitActionPortrait("Swap") }, 'y', false),
    Automate("Automate",
        { ImageGetter.getUnitActionPortrait("Automate") }, 'm'),
    StopAutomation("Stop automation",
        { ImageGetter.getUnitActionPortrait("Stop") }, 'm', false),
    StopMovement("Stop movement",
        { ImageGetter.getUnitActionPortrait("StopMove") }, '.', false),
    Sleep("Sleep",
        { ImageGetter.getUnitActionPortrait("Sleep") }, 'f'),
    SleepUntilHealed("Sleep until healed",
        { ImageGetter.getUnitActionPortrait("Sleep") }, 'h'),
    Fortify("Fortify",
        { ImageGetter.getUnitActionPortrait("Fortify") }, 'f', UncivSound.Fortify),
    FortifyUntilHealed("Fortify until healed",
        { ImageGetter.getUnitActionPortrait("FortifyUntilHealed") }, 'h', UncivSound.Fortify),
    Explore("Explore",
        { ImageGetter.getUnitActionPortrait("Explore") }, 'x'),
    StopExploration("Stop exploration",
        { ImageGetter.getUnitActionPortrait("Stop") }, 'x', false),
    Promote("Promote",
        { ImageGetter.getUnitActionPortrait("Promote") }, 'o', false, UncivSound.Promote),
    Upgrade("Upgrade",
        { ImageGetter.getUnitActionPortrait("Upgrade") }, 'u', UncivSound.Upgrade),
    Transform("Transform",
        { ImageGetter.getUnitActionPortrait("Transform") }, 'k', UncivSound.Upgrade),
    Pillage("Pillage",
        { ImageGetter.getUnitActionPortrait("Pillage") }, 'p', false),
    Paradrop("Paradrop",
        { ImageGetter.getUnitActionPortrait("Paradrop") }, 'p', false),
    AirSweep("Air Sweep",
        { ImageGetter.getUnitActionPortrait("AirSweep") }, 'a', false),
    SetUp("Set up",
        { ImageGetter.getUnitActionPortrait("SetUp") }, 't', false, UncivSound.Setup),
    FoundCity("Found city",
        { ImageGetter.getUnitActionPortrait("FoundCity") }, 'c', UncivSound.Silent),
    ConstructImprovement("Construct improvement",
        { ImageGetter.getUnitActionPortrait("ConstructImprovement") }, 'i'),
    Repair(Constants.repair,
        { ImageGetter.getUnitActionPortrait("Repair") }, 'r', UncivSound.Construction),
    Create("Create",
        null, 'i', UncivSound.Chimes),
    HurryResearch("Hurry Research",
        { ImageGetter.getUnitActionPortrait("HurryResearch") }, 'g', UncivSound.Chimes),
    StartGoldenAge("Start Golden Age",
        { ImageGetter.getUnitActionPortrait("StartGoldenAge") }, 'g', UncivSound.Chimes),
    HurryWonder("Hurry Wonder",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, 'g', UncivSound.Chimes),
    HurryBuilding("Hurry Construction",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, 'g', UncivSound.Chimes),
    ConductTradeMission("Conduct Trade Mission",
        { ImageGetter.getUnitActionPortrait("ConductTradeMission") }, 'g', UncivSound.Chimes),
    FoundReligion("Found a Religion",
        { ImageGetter.getUnitActionPortrait("FoundReligion") }, 'g', UncivSound.Choir),
    TriggerUnique("Trigger unique",
        { ImageGetter.getUnitActionPortrait("Star") }, 'g', false, UncivSound.Chimes),
    SpreadReligion("Spread Religion",
        null, 'g', UncivSound.Choir),
    RemoveHeresy("Remove Heresy",
        { ImageGetter.getUnitActionPortrait("RemoveHeresy") }, 'h', UncivSound.Fire),
    EnhanceReligion("Enhance a Religion",
        { ImageGetter.getUnitActionPortrait("EnhanceReligion") }, 'g', UncivSound.Choir),
    DisbandUnit("Disband unit",
        { ImageGetter.getUnitActionPortrait("DisbandUnit") }, KeyCharAndCode.DEL, false),
    GiftUnit("Gift unit",
        { ImageGetter.getUnitActionPortrait("Present") }, UncivSound.Silent),
    Wait("Wait",
        { ImageGetter.getUnitActionPortrait("Wait") }, 'z', UncivSound.Silent),
    ShowAdditionalActions("Show more",
        { ImageGetter.getUnitActionPortrait("ShowMore") }, KeyCharAndCode(Input.Keys.PAGE_DOWN), false),
    HideAdditionalActions("Back",
        { ImageGetter.getUnitActionPortrait("HideMore") }, KeyCharAndCode(Input.Keys.PAGE_UP), false),
    AddInCapital( "Add in capital",
        { ImageGetter.getUnitActionPortrait("AddInCapital")}, 'g', UncivSound.Chimes),
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

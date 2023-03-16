package com.unciv.models

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.images.ImageGetter


/** Unit Actions - class - carries dynamic data and actual execution.
 * Static properties are in [UnitActionType].
 * Note this is for the buttons offering actions, not the ongoing action stored with a [MapUnit][com.unciv.logic.map.mapunit.MapUnit]
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
 * @param binding       keyboard binding - omitting it will look up the KeyboardBinding of the same name (recommended)
 * @param isSkippingToNextUnit if "Auto Unit Cycle" setting and this bit are on, this action will skip to the next unit
 * @param uncivSound    _default_ sound, can be overridden in UnitAction instantiation
 */

// Note for Creators of new UnitActions: If your action uses a dynamic label overriding UnitActionType.value,
// then you need to teach [com.unciv.testing.TranslationTests.allUnitActionsHaveTranslation] how to deal with it!

enum class UnitActionType(
    val value: String,
    val imageGetter: (()-> Actor)?,
    binding: KeyboardBinding? = null,
    val isSkippingToNextUnit: Boolean = true,
    val uncivSound: UncivSound = UncivSound.Click
) {
    SwapUnits("Swap units",
        { ImageGetter.getUnitActionPortrait("Swap") }, false),
    Automate("Automate",
        { ImageGetter.getUnitActionPortrait("Automate") }),
    StopAutomation("Stop automation",
        { ImageGetter.getUnitActionPortrait("Stop") }, false),
    StopMovement("Stop movement",
        { ImageGetter.getUnitActionPortrait("StopMove") }, false),
    Sleep("Sleep",
        { ImageGetter.getUnitActionPortrait("Sleep") }),
    SleepUntilHealed("Sleep until healed",
        { ImageGetter.getUnitActionPortrait("Sleep") }),
    Fortify("Fortify",
        { ImageGetter.getUnitActionPortrait("Fortify") }, UncivSound.Fortify),
    FortifyUntilHealed("Fortify until healed",
        { ImageGetter.getUnitActionPortrait("FortifyUntilHealed") }, UncivSound.Fortify),
    Explore("Explore",
        { ImageGetter.getUnitActionPortrait("Explore") }),
    StopExploration("Stop exploration",
        { ImageGetter.getUnitActionPortrait("Stop") }, false),
    Promote("Promote",
        { ImageGetter.getUnitActionPortrait("Promote") }, false, UncivSound.Promote),
    Upgrade("Upgrade",
        { ImageGetter.getUnitActionPortrait("Upgrade") }, UncivSound.Upgrade),
    Transform("Transform",
        { ImageGetter.getUnitActionPortrait("Transform") }, UncivSound.Upgrade),
    Pillage("Pillage",
        { ImageGetter.getUnitActionPortrait("Pillage") }, false),
    Paradrop("Paradrop",
        { ImageGetter.getUnitActionPortrait("Paradrop") }, false),
    AirSweep("Air Sweep",
        { ImageGetter.getUnitActionPortrait("AirSweep") }, false),
    SetUp("Set up",
        { ImageGetter.getUnitActionPortrait("SetUp") }, false, UncivSound.Setup),
    FoundCity("Found city",
        { ImageGetter.getUnitActionPortrait("FoundCity") }, UncivSound.Silent),
    ConstructImprovement("Construct improvement",
        { ImageGetter.getUnitActionPortrait("ConstructImprovement") }, false),
    Repair(Constants.repair,
        { ImageGetter.getUnitActionPortrait("Repair") }, UncivSound.Construction),
    Create("Create",
        null, false, UncivSound.Chimes),
    HurryResearch("{Hurry Research} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryResearch") }, UncivSound.Chimes),
    StartGoldenAge("Start Golden Age",
        { ImageGetter.getUnitActionPortrait("StartGoldenAge") }, UncivSound.Chimes),
    HurryWonder("{Hurry Wonder} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, UncivSound.Chimes),
    HurryBuilding("{Hurry Construction} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, UncivSound.Chimes),
    ConductTradeMission("{Conduct Trade Mission} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("ConductTradeMission") }, UncivSound.Chimes),
    FoundReligion("Found a Religion",
        { ImageGetter.getUnitActionPortrait("FoundReligion") }, UncivSound.Choir),
    TriggerUnique("Trigger unique",
        { ImageGetter.getUnitActionPortrait("Star") }, false, UncivSound.Chimes),
    SpreadReligion("Spread Religion",
        null, UncivSound.Choir),
    RemoveHeresy("Remove Heresy",
        { ImageGetter.getUnitActionPortrait("RemoveHeresy") }, UncivSound.Fire),
    EnhanceReligion("Enhance a Religion",
        { ImageGetter.getUnitActionPortrait("EnhanceReligion") }, UncivSound.Choir),
    DisbandUnit("Disband unit",
        { ImageGetter.getUnitActionPortrait("DisbandUnit") }, false),
    GiftUnit("Gift unit",
        { ImageGetter.getUnitActionPortrait("Present") }, UncivSound.Silent),
    Wait("Wait",
        { ImageGetter.getUnitActionPortrait("Wait") }, UncivSound.Silent),
    ShowAdditionalActions("Show more",
        { ImageGetter.getUnitActionPortrait("ShowMore") }, false),
    HideAdditionalActions("Back",
        { ImageGetter.getUnitActionPortrait("HideMore") }, false),
    AddInCapital( "Add in capital",
        { ImageGetter.getUnitActionPortrait("AddInCapital")}, UncivSound.Chimes),
    ;

    // Allow shorter initializations
    constructor(value: String, imageGetter: (() -> Actor)?, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, null, true, uncivSound)
    constructor(value: String, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click)
            : this(value, imageGetter, null, isSkippingToNextUnit, uncivSound)

    val binding: KeyboardBinding =
            binding ?:
            KeyboardBinding.values().firstOrNull { it.name == name } ?:
            KeyboardBinding.None
}

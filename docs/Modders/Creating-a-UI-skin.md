# Creating a UI skin

**You should read the [Mods](Mods.md) page first before proceeding**

In order to add a UI skin mod (yes, UI skins are just another type of mod), all you need to do is add your images under `Images/Skins/MyCoolSkinExample` and enable the mod as a permanent visual mod.

The game will then recognize the skin, and allow you to pick it in the options menu.

Just like [tilesets](Creating-a-custom-tileset.md), UI skins can be used to alter the appearance of Unciv. Please note that UI skins do not support custom icons and fonts and not every UI element can be customized yet too.

We use so called 9.png (or Ninepatch) files for every skin image because UI elements need a way to be resized based on game window size and resolution. Ninepatch files can be created manually by adding black pixels around your custom images in a specific manner or by using [Android Studio's Draw 9-patch tool](https://developer.android.com/studio/write/draw9patch) or [this tool by romannurik](https://romannurik.github.io/AndroidAssetStudio/nine-patches.html) for example. You may also check if your favorite image creation tool supports nine patches itself to generate them more easily.

A skin image can either be gray scale and later be colored in game by modifying the `tint` in the [skinConfig](Creating-a-UI-skin.md#tint) or be colored directly in the image. When coloring the image directly it is important to set the tint of the UI element to white. Please note that tileable ninepatches and ninepatches with multiple stretch areas are not supported because of technical restrictions by libgdx.

There are 6 basic shapes which can be placed inside the `Images/Skins/MyCoolSkinExample` folder:
 - checkbox
 - checkbox-pressed
 - rectangleWithOutline
 - roundedEdgeRectangle
 - select-box
 - select-box-pressed

These shapes are used all over Unciv and can be replaced to make a lot of UI elements change appearance at once. To change just one specific element use the [table](Creating-a-UI-skin.md#Available-UI-elements) below to create an image at the specified directory using the specified name inside `Images/Skins/MyCoolSkinExample`. See the image below for an example file structure. ![skinExample](https://user-images.githubusercontent.com/24532072/198904598-0d298035-5b02-431b-bfb4-7da4b9c821c9.png)

## Limitations

- UI elements which change color because they have multiple states can not be given multiple colors based on their state using tint
  - When coloring the image directly, setting the tint of the UI element to white overwrites these states
- Tileable ninepatches and ninepatches with multiple stretch areas are not supported because of technical restrictions by libgdx

## Available UI elements

<!--- We should add an image to every identifier so its easier for modders to know which UI elements are which -->
<!--- The following table is auto generated and should not be modified manually. If you want to change it see UiElementDocsWriter.kt -->

<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->
| Directory | Name | Default shape | Image |
|---|:---:|:---:|---|
| AnimatedMenu/ | Button | roundedEdgeRectangleMid | |
| CityScreen/ | CityPickerTable | roundedEdgeRectangle | |
| CityScreen/CitizenManagementTable/ | AvoidCell | null | |
| CityScreen/CitizenManagementTable/ | FocusCell | null | |
| CityScreen/CitizenManagementTable/ | ResetCell | null | |
| CityScreen/CityConstructionTable/ | AvailableConstructionsTable | null | |
| CityScreen/CityConstructionTable/ | ConstructionsQueueTable | null | |
| CityScreen/CityConstructionTable/ | Header | null | |
| CityScreen/CityConstructionTable/ | PickConstructionButton | null | |
| CityScreen/CityConstructionTable/ | PickConstructionButtonSelected | null | |
| CityScreen/CityConstructionTable/ | QueueEntry | null | |
| CityScreen/CityConstructionTable/ | QueueEntrySelected | null | |
| CityScreen/CityScreenTileTable/ | Background | null | |
| CityScreen/CityScreenTileTable/ | InnerTable | null | |
| CityScreen/CityStatsTable/ | Background | null | |
| CityScreen/CityStatsTable/ | InnerTable | null | |
| CityScreen/ConstructionInfoTable/ | Background | null | |
| CityScreen/ConstructionInfoTable/ | SelectedConstructionTable | null | |
| CivilopediaScreen/ | EntryButton | null | |
| DiplomacyScreen/ | LeftSide | null | |
| DiplomacyScreen/ | RightSide | null | |
| DiplomacyScreen/ | SelectedCiv | null | |
| General/ | AnimatedMenu | roundedEdgeRectangle | |
| General/ | Border | null | |
| General/ | ExpanderTab | null | |
| General/ | HealthBar | null | |
| General/ | KeyCapturingButton | roundedEdgeRectangleSmall | |
| General/ | TabbedPager | null | |
| General/ | Tooltip | roundedEdgeRectangle | |
| General/Popup/ | Background | null | |
| General/Popup/ | InnerTable | layerContainer | |
| LanguagePickerScreen/ | LanguageTable | null | |
| LoadGameScreen/ | BottomTable | null | |
| LoadGameScreen/ | TopTable | null | |
| MainMenuScreen/ | Background | null | |
| MainMenuScreen/ | Version | roundedEdgeRectangle | |
| MapEditor/MapEditorToolsDrawer/ | Handle | null | |
| ModManagementOptions/ | ExpanderTab | null | |
| ModManagementScreen/ | BottomTable | null | |
| ModManagementScreen/ | TopTable | null | |
| MultiplayerScreen/ | BottomTable | null | |
| MultiplayerScreen/ | TopTable | null | |
| NewGameScreen/ | BottomTable | null | |
| NewGameScreen/ | GameOptionsTable | null | |
| NewGameScreen/ | MapOptionsTable | null | |
| NewGameScreen/ | PlayerPickerTable | null | |
| NewGameScreen/ | TopTable | null | |
| NewGameScreen/NationTable/ | Background | null | |
| NewGameScreen/NationTable/ | BorderTable | null | |
| NewGameScreen/NationTable/ | RightInnerTable | null | |
| NewGameScreen/NationTable/ | Title | null | |
| OverviewScreen/DiplomacyOverviewTab/ | CivTable | null | |
| OverviewScreen/NotificationOverviewTable/ | Notification | roundedEdgeRectangle | |
| OverviewScreen/ReligionOverviewTab/ | BeliefDescription | null | |
| OverviewScreen/TradesOverviewTab/ | OffersTable | null | |
| OverviewScreen/UnitOverviewTab/ | UnitSupplyTable | null | |
| PlayerReadyScreen/ | Background | null | |
| PolicyScreen/ | PolicyBranchAdoptButton | roundedEdgeRectangleSmall | |
| PolicyScreen/ | PolicyBranchAdoptButtonBorder | roundedEdgeRectangleSmall | |
| PolicyScreen/ | PolicyBranchBackground | rectangleWithOutline | |
| PolicyScreen/ | PolicyBranchBackgroundBorder | rectangleWithOutline | |
| PolicyScreen/ | PolicyBranchHeader | rectangleWithOutline | |
| PolicyScreen/ | PolicyBranchHeaderBorder | rectangleWithOutline | |
| PolicyScreen/Colors/ | BranchBGAdopted | 50,45,5 | |
| PolicyScreen/Colors/ | BranchBGCompleted | 255,205,0 | |
| PolicyScreen/Colors/ | BranchBGNotAdopted | 5,45,65 | |
| PolicyScreen/Colors/ | BranchHeaderBG | 47,90,92 | |
| PolicyScreen/Colors/ | BranchLabelAdopted | 150,70,40 | |
| PolicyScreen/Colors/ | BranchLabelNotPickable | 0xffffff7f | |
| PolicyScreen/Colors/ | BranchLabelPickable | WHITE | |
| PolicyScreen/Colors/ | ButtonBGAdopted | 1,17,19 | |
| PolicyScreen/Colors/ | ButtonBGAdoptedSelected | 1,17,19 | |
| PolicyScreen/Colors/ | ButtonBGNotPickable | 20,20,20 | |
| PolicyScreen/Colors/ | ButtonBGNotPickableSelected | 20,20,20 | |
| PolicyScreen/Colors/ | ButtonBGPickable | 32,46,64 | |
| PolicyScreen/Colors/ | ButtonBGPickableSelected | 37,87,82 | |
| PolicyScreen/Colors/ | ButtonIconAdopted | GOLD | |
| PolicyScreen/Colors/ | ButtonIconAdoptedSelected | GOLD | |
| PolicyScreen/Colors/ | ButtonIconNotPickable | 0xffffff33 | |
| PolicyScreen/Colors/ | ButtonIconNotPickableSelected | 0xffffff33 | |
| PolicyScreen/Colors/ | ButtonIconPickable | WHITE | |
| PolicyScreen/Colors/ | ButtonIconPickableSelected | WHITE | |
| PromotionScreen/ | PromotionButton | roundedEdgeRectangleMid | |
| PromotionScreen/ | PromotionButtonBorder | roundedEdgeRectangleMidBorder | |
| TechPickerScreen/ | Background | null | |
| TechPickerScreen/ | BottomTable | null | |
| TechPickerScreen/ | CurrentTechColor | 72, 147, 175 | |
| TechPickerScreen/ | QueuedTechColor | 7*2, 46*2, 43*2 | |
| TechPickerScreen/ | ResearchableTechColor | 28, 170, 0 | |
| TechPickerScreen/ | ResearchedFutureTechColor | 127, 50, 0 | |
| TechPickerScreen/ | ResearchedTechColor | 255, 215, 0 | |
| TechPickerScreen/ | TechButtonIconsOutline | roundedEdgeRectangleSmall | |
| VictoryScreen/ | CivGroup | roundedEdgeRectangle | |
| WorldScreen/ | AirUnitTable | null | |
| WorldScreen/ | BattleTable | null | |
| WorldScreen/ | Notification | roundedEdgeRectangle | |
| WorldScreen/ | PickTechButton | roundedEdgeRectangle | |
| WorldScreen/ | TutorialTaskTable | null | |
| WorldScreen/CityButton/ | AirUnitTable | roundedEdgeRectangleSmall | |
| WorldScreen/CityButton/ | AirUnitTableBorder | roundedEdgeRectangleSmall | |
| WorldScreen/CityButton/ | DefenceTable | roundedTopEdgeRectangleSmall | |
| WorldScreen/CityButton/ | DefenceTableBorder | roundedTopEdgeRectangleSmallBorder | |
| WorldScreen/CityButton/ | IconTable | roundedEdgeRectangleMid | |
| WorldScreen/CityButton/ | IconTableBorder | roundedEdgeRectangleMidBorder | |
| WorldScreen/CityButton/ | InfluenceBar | null | |
| WorldScreen/Minimap/ | Background | null | |
| WorldScreen/Minimap/ | Border | null | |
| WorldScreen/NextTurn/ | ProgressBar | null | |
| WorldScreen/NextTurn/ | ProgressColor | FOREST | |
| WorldScreen/TopBar/ | LeftAttachment | roundedEdgeRectangle | |
| WorldScreen/TopBar/ | ResourceTable | null | |
| WorldScreen/TopBar/ | RightAttachment | roundedEdgeRectangle | |
| WorldScreen/TopBar/ | StatsTable | null | |
| WorldScreenMusicPopup/TrackList/ | Down | null | |
| WorldScreenMusicPopup/TrackList/ | Over | null | |
| WorldScreenMusicPopup/TrackList/ | Up | null | |
<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->

## SkinConfig

The skinConfig is similar to the [tilesetConfig](Creating-a-custom-tileset.md#tileset-config) and can be used to define different colors and shapes for unciv to use.

To create a config for your skin you just need to create a new .json file under `jsons/Skins/`. Just create a .txt file and rename it to MyCoolSkinExample.json. You only have to add things if you want to change them. Else the default values will be used.

This is an example of such a config file that will be explain below:

```json
{
    "baseColor": {"r":1,"g":0,"b":0,"a":1},
    "defaultVariantTint": {"r":1,"g":1,"b":1,"a":1},
    "skinVariants": {
        "MainMenuScreen/MenuButton": {
            "image": "MyCoolNewDesign",
            "foregroundColor": {"r": 0, "g": 0, "b": 1, "a": 1},
            "iconColor": {"r": 0, "g": 1, "b": 0, "a": 1}
        },
        "TechPickerScreen/TechButton": {
            "image": "MyCoolNewDesign",
            "alpha": 0.7
        },
        "WorldScreen/TopBar/ResourceTable": {
            "alpha": 0.8
        },
        "WorldScreen/UnitTable": {
            "tint": {"r": 1, "g": 0, "b": 0},
            "image": "WorldScreen/TopBar/ResourceTable",
            "alpha": 0.4
        },
        "WorldScreen/Minimap/Background": {
            "tint": {"r": 0.2, "g": 0.4, "b": 0.45, "a": 1}
        }
    }
}
```

### baseColor

A color defined with normalized RGBA values. Default value: `{"r": 0, "g": 0.251, "b": 0.522, "a": 0.749}`

Defines the color unciv uses in most ui elements as default

### fallbackSkin

A string. Default value: "Minimal".

The name of another skin to use as a fallback if an image is not found or not specified in this skin.
Set to null to disable fallback.

### defaultVariantTint

A color defined with normalized RGBA values. Default value: null

The tint all skinVariants should use if not explicitly specified in a skinVariant.
If you mostly use colored images set this to white (`{"r": 1, "g": 1, "b": 1, "a": 1}`) to get
the correct colors.

### skinVariants

A dictionary mapping string to a SkinElement. Default value: empty

These variants can be used to define a different image, tint and/or alpha for a specified UI element. The string used to identify the UI element can be taken from the [table](Creating-a-UI-skin.md#Available-UI-elements) above by appending the name to the directory.
```
| Directory             | Name          |
|-----------------------|---------------|
| WorldScreen/          | Notification  | -> WorldScreen/Notification
| WorldScreen/TopBar/   | StatsTable    | -> WorldScreen/TopBar/StatsTable
```

#### image

A path to an image. The file is expected to be located alongside the 6 basic shapes inside the `Images/Skins/MyCoolSkinExample` folder if just a name like `MyCoolNewDesign` is given. The image path can also be another ui element like `WorldScreen/TopBar/ResourceTable` so images can be reused by other elements.

#### tint

A color defined with normalized RGBA values. Default value: null

The tint this UI element should get. Is applied as a tint on top of the image. This means that if the
image is colored and the tint is not white the tint color will merge with the image color and not override it.

#### alpha

A float value. Default value: null

The alpha this UI element should have. Overwrites the alpha value of tint if specified.

### foregroundColor

A color defined with normalized RGBA values. Default value: null

The color this UI element should use for its font and icons. To color icon and font differently use
the `iconColor` in addition to this.

### iconColor

A color defined with normalized RGBA values. Default value: null

The color this UI element should use for its icons. Overrides the `foregroundColor` for icons if specified.
# How to create a UI skin for Unciv

**You should read the [Mods](Mods.md) page first before proceeding**

In order to add a UI skin mod (yes, UI skins are just another type of mod), all you need to do is add your images under `Images/Skins/MyCoolSkinExample` and enable the mod as a permanent visual mod - the game will recognize the skin, and allow you to pick it in the options menu.

Just like [tilesets](Creating-a-custom-tileset.md), UI skins can be used to alter the appearance of Unciv. Please not that UI skins do not support custom icons and fonts and not every UI element can be customized yet too.

We use so called 9.png (or Ninepatch) files for every skin image because UI elements need a way to be resized based on game window size and resolution. Ninepatch files can be created manually by adding black pixels around your custom images in a specific manner or by using [Android Studio's Draw 9-patch tool](https://developer.android.com/studio/write/draw9patch) or [this tool by romannurik](https://romannurik.github.io/AndroidAssetStudio/nine-patches.html) for example. You may also check if your favorite image creation tool supports nine patches itself to generate them more easily.

Every skin image needs to be **gray scale** since colors are applied later in game. The color for the image can be modified using the [skinConfig](Creating-a-UI-skin.md#tint). Please note that tileable ninepatches and ninepatches with multiple stretch areas are not supported because of technical restrictions by libgdx.

There are 6 basic shapes which can be placed inside the `Images/Skins/MyCoolSkinExample` folder:
 - checkbox
 - checkbox-pressed
 - rectangleWithOutline
 - roundedEdgeRectangle
 - select-box
 - select-box-pressed

These shapes are used all over Unciv and can be replaced to make a lot of UI elements change appearance at once. To change just one specific element use the [table](Creating-a-UI-skin.md#Available-UI-elements) below to create an image at the specified directory using the specified name inside `Images/Skins/MyCoolSkinExample`. See the image below for an example file structure. ![skinExample](https://user-images.githubusercontent.com/24532072/198879776-43e8d7ce-e203-44f1-a129-84ea16cb2435.PNG)

## Available UI elements

<!--- We should add an image to every identifier so its easier for modders to know which UI elements are which -->

| Directory | Name | Default shape | Image |
|---|:---:|:---:|---|
| CivilopediaScreen/ | EntryButton | null | |
| CityScreen/CityConstructionTable/ | AvailableConstructionsTable | null | |
| CityScreen/CityConstructionTable/ | ConstructionsQueueTable | null | |
| CityScreen/CityConstructionTable/ | Header | null | |
| CityScreen/CityConstructionTable/ | PickConstructionButton | null | |
| CityScreen/CityConstructionTable/ | QueueEntry | null | |
| CityScreen/ | CityInfoTable | null | |
| CityScreen/CitizenManagementTable/ | AvoidCell | null | |
| CityScreen/CitizenManagementTable/ | FocusCell | null | |
| CityScreen/CitizenManagementTable/ | ResetCell | null | |
| CityScreen/ | CityPickerTable | roundedEdgeRectangle | |
| CityScreen/CityScreenTileTable/ | Background | null | |
| CityScreen/CityScreenTileTable/ | InnerTable | null | |
| CityScreen/CityStatsTable/ | Background | null | |
| CityScreen/CityStatsTable/ | InnerTable | null | |
| CityScreen/ConstructionInfoTable/ | Background | null | |
| CityScreen/ConstructionInfoTable/ | SelectedConstructionTable | null | |
| General/ | Border | null | |
| General/ | ExpanderTab | null | |
| General/ | HealthBar | null | |
| General/ | TabbedPager | null | |
| General/ | Tooltip | roundedEdgeRectangle | |
| General/Popup/ | Background | null | |
| General/Popup/ | InnerTable | null | |
| LanguagePickerScreen/ | LanguageTable | null | |
| MainMenuScreen/ | MenuButton | roundedEdgeRectangle | |
| MapEditor/MapEditorToolsDrawer/ | Handle | null | |
| ModManagementOptions/ | ExpanderTab | null | |
| NewGameScreen/NationTable/ | Background | null | |
| NewGameScreen/NationTable/ | BorderTable | null | |
| NewGameScreen/NationTable/ | InnerTable | null | |
| NewGameScreen/NationTable/ | Title | null | |
| NewGameScreen/PlayerPickerTable/ | PlayerTable | null | |
| OverviewScreen/DiplomacyOverviewTab/ | CivTable | null | |
| OverviewScreen/NotificationOverviewTable/ | Notification | roundedEdgeRectangle | |
| OverviewScreen/ReligionOverviewTab/ | BeliefDescription | null | |
| OverviewScreen/TradesOverviewTab/ | OffersTable | null | |
| OverviewScreen/UnitOverviewTab/ | UnitSupplyTable | null | |
| TechPickerScreen/ | TechButton | roundedEdgeRectangle | |
| VictoryScreen/ | CivGroup | roundedEdgeRectangle | |
| WorldScreen/ | AirUnitTable | null | |
| WorldScreen/CityButton/ | AirUnitTable | roundedEdgeRectangle | |
| WorldScreen/CityButton/ | IconTable | roundedEdgeRectangle | |
| WorldScreen/CityButton/ | InfluenceBar | null | |
| WorldScreen/ | Notification | null | |
| WorldScreen/ | PickTechButton | roundedEdgeRectangle | |
| WorldScreen/ | TutorialTaskTable | null | |
| WorldScreen/TopBar/ | ResourceTable | null | |
| WorldScreen/TopBar/ | StatsTable | null | |
| WorldScreen/TopBar/ | LeftAttachment | roundedEdgeRectangle | |
| WorldScreen/TopBar/ | RightAttachment | roundedEdgeRectangle | |
| WorldScreen/ | BattleTable | null | |
| WorldScreen/ | TileInfoTable | null | |
| WorldScreen/Minimap/ | Background | null | |
| WorldScreen/Minimap/ | Border | null | |
| WorldScreen/ | UnitTable | null | |

## SkinConfig

The skinConfig is similar to the [tilesetConfig](Creating-a-custom-tileset.md#tileset-config) and can be used to define different colors and shapes for unciv to use.

To create a config for your skin you just need to create a new .json file under `Jsons/Skins/`. Just create a .txt file and rename it to MyCoolSkinExample.json. You only have to add things if you want to change them. Else the default values will be used.

This is an example of such a config file that will be explain below:

```json
    "baseColor": {"r":1,"g":0,"b":0,"a":1},
    "skinVariants": {
        "MainMenuScreen/MenuButton": {
          "image": "MyCoolNewDesign",
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
        },
    }
```

### baseColor

A color defined with normalized RGBA values. Default value: "{"r": 0, "g": 0.251, "b": 0.522, "a": 0.749}"

Defines the color unciv uses in most ui elements as default

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

A path to an image. The file is expected to be located alongside the 6 basic shapes inside the `Images/Skins/MyCoolSkinExample` folder in if just a name like `MyCoolNewDesign` is given. The image path can also be another ui element like `WorldScreen/TopBar/ResourceTable` so images can be reused by other elements.

#### tint

A color defined with normalized RGBA values. Default value: null

The color this UI element should have.

#### alpha

A float value. Default value: null

The alpha this UI element should have. Overwrites the alpha value of tint if specified.

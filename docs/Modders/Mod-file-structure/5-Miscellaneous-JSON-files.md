# Miscellaneous JSON files

## Difficulties.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Difficulties.json)

This file defines the difficulty levels a player can choose when starting a new game.

Each difficulty level has the following structure:

| Attribute                         | Type             | Default  | Notes                                                                                                                                                                                                 |
|-----------------------------------|------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                              | String           | Required |                                                                                                                                                                                                       |
| baseHappiness                     | Integer          | 0        |                                                                                                                                                                                                       |
| extraHappinessPerLuxury           | Float            | 0        |                                                                                                                                                                                                       |
| researchCostModifier              | Float            | 1        |                                                                                                                                                                                                       |
| unitCostModifier                  | Float            | 1        |                                                                                                                                                                                                       |
| unitSupplyBase                    | Integer          | 5        |                                                                                                                                                                                                       |
| unitSupplyPerCity                 | Integer          | 2        |                                                                                                                                                                                                       |
| buildingCostModifier              | Float            | 1        |                                                                                                                                                                                                       |
| policyCostModifier                | Float            | 1        |                                                                                                                                                                                                       |
| unhappinessModifier               | Float            | 1        |                                                                                                                                                                                                       |
| barbarianBonus                    | Float            | 0        |                                                                                                                                                                                                       |
| barbarianSpawnDelay               | Integer          | 0        |                                                                                                                                                                                                       |
| playerBonusStartingUnits          | List of Strings  | empty    | Can also be 'Era Starting Unit', maps to `startingMilitaryUnit` of the Eras file. All other units must be in [Units.json](4-Unit-related-JSON-files.md#Units.json). Applies only to human player civs |
| aiCityGrowthModifier              | Float            | 1        |                                                                                                                                                                                                       |
| aiUnitCostModifier                | Float            | 1        |                                                                                                                                                                                                       |
| aiBuildingCostModifier            | Float            | 1        |                                                                                                                                                                                                       |
| aiWonderCostModifier              | Float            | 1        |                                                                                                                                                                                                       |
| aiBuildingMaintenanceModifier     | Float            | 1        |                                                                                                                                                                                                       |
| aiUnitMaintenanceModifier         | Float            | 1        |                                                                                                                                                                                                       |
| aiUnitSupplyModifier              | Integer          | 5        |                                                                                                                                                                                                       |
| aiFreeTechs                       | List of Strings  | empty    | Must be in [Techs.json](2-Civilization-related-JSON-files.md#techsjson)                                                                                                                               |
| aiMajorCivBonusStartingUnits      | List of Strings  | empty    | Same rules as playerBonusStartingUnits, See above. Applies only to AI major civs                                                                                                                      |
| aiCityStateBonusStartingUnits     | List of Strings  | empty    | Same rules as playerBonusStartingUnits, See above. Applies only to city-state civs                                                                                                                    |
| aiUnhappinessModifier             | Float            | 1        |                                                                                                                                                                                                       |
| turnBarbariansCanEnterPlayerTiles | Integer          | 0        |                                                                                                                                                                                                       |
| clearBarbarianCampReward          | Integer          | 25       |                                                                                                                                                                                                       |

## Eras.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Eras.json)

This file should contain all the era's you want to use in your mod.

Each era can have the following attributes:

| Attribute                 | Type                                   | Default         | Notes                                                                                                                                                                                                                                              |
|---------------------------|----------------------------------------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                      | String                                 | Required        |                                                                                                                                                                                                                                                    |
| researchAgreementCost     | Integer (≥0)                           | 300             | Cost of research agreements when the most technologically advanced civ is in this era                                                                                                                                                              |
| iconRGB                   | [List of 3× Integer](#rgb-colors-list) | white           | RGB color that icons for technologies of this era should have in the Tech screen                                                                                                                                                                   |
| startingSettlerCount      | Integer (≥0)                           | 1               | Amount of settler units that should be spawned when starting a game in this era (setting this to zero is discouraged [^1])                                                                                                                         |
| startingSettlerUnit       | String                                 | "Settler"       | Name of the unit that should be used for the previous field. Must be in [Units.json](4-Unit-related-JSON-files.md#unitsjson), or a unit with the "Founds a new city" unique must exist                                                             |
| startingWorkerCount       | Integer (≥0)                           | 0               | Amount of worker units that should be spawned when starting a game in this era                                                                                                                                                                     |
| startingWorkerUnit        | String                                 | "Worker"        | Name of the unit that should be used for the previous field. If startingWorkerCount>0, then it must exist in [Units.json](4-Unit-related-JSON-files.md#unitsjson), or a unit with the "Can build [filter] improvements on tiles" unique must exist |
| startingMilitaryUnitCount | Integer (≥0)                           | 1               | Amount of military units that should be spawned when starting a game in this era                                                                                                                                                                   |
| startingMilitaryUnit      | String                                 | "Warrior"       | Name of the unit that should be used for the previous field. Must be in [Units.json](4-Unit-related-JSON-files.md#unitsjson)                                                                                                                       |
| startingGold              | Integer (≥0)                           | 0               | Amount of gold each civ should receive when starting a game in this era                                                                                                                                                                            |
| startingCulture           | Integer (≥0)                           | 0               | Amount of culture each civ should receive when starting a game in this era                                                                                                                                                                         |
| settlerPopulation         | Integer (>0)                           | 1               | Amount of population each city should have when settled when starting a game in this era                                                                                                                                                           |
| settlerBuildings          | List of Strings                        | empty           | Buildings that should automatically be built whenever a city is settled when starting a game in this era                                                                                                                                           |
| startingObsoleteWonders   | List of Strings                        | empty           | Wonders (and technically buildings) that should be impossible to built when starting a game in this era. Used in the base game to remove all wonders older than 2 era's                                                                            |
| baseUnitBuyCost           | Integer                                | 200             | Default value used for the unique `Can be purchased with [stat] [cityFilter]`                                                                                                                                                                      |
| embarkDefense             | Integer                                | 3               | Default defense for embarked unit in this era                                                                                                                                                                                                      |
| startPercent              | Integer                                | 0               | When starting, percentage (\[0\]%-\[100\]%) of turns skipped in total turns specified in [Speed.json](#speedsjson)                                                                                                                                 |
| citySound                 | String                                 | "cityClassical" | Sound used when city is founded in this era                                                                                                                                                                                                        |

[^1]: Successfully setting startingSettlerCount to zero in a mod (idea: conquer or die) is not easy. Some player-controlled settings require at least one Settler, through any source (see difficulties for other possible settler sources), or you won't be able to start a game: Once City Challenge requires one for all players, and allowing any city-states requires one for those. Would also affect defeat rules.

## Speeds.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Speeds.json)

This file should contain all the speeds you want to use in your mod.

Each speed can have the following attributes:

| Attribute                       | Type         | Default          | Notes                                                                                                       |
|---------------------------------|--------------|------------------|-------------------------------------------------------------------------------------------------------------|
| name                            | String       | Required         | Name of the speed                                                                                           |
| modifier                        | Float (≥0)   | 1.0              | Overall game speed modifier                                                                                 |
| productionCostModifier          | Float (≥0)   | `modifier` value | Scales production cost of units and buildings                                                               |
| goldCostModifier                | Float (≥0)   | `modifier` value | Scales gold costs                                                                                           |
| scienceCostModifier             | Float (≥0)   | `modifier` value | Scales science costs                                                                                        |
| cultureCostModifier             | Float (≥0)   | `modifier` value | Scales culture costs                                                                                        |
| faithCostModifier               | Float (≥0)   | `modifier` value | Scales faith costs                                                                                          |
| improvementBuildLengthModifier  | Float (≥0)   | `modifier` value | Scales the time it takes for a worker to build tile improvements                                            |
| barbarianModifier               | Float (≥0)   | `modifier` value | Scales the time between barbarian spawns                                                                    |
| goldGiftModifier                | Float (≥0)   | `modifier` value | Scales the influence gained from gifting gold to city-states                                                |
| cityStateTributeScalingInterval | Float (≥0)   | 6.5              | The number of turns it takes for the amount of gold a player demands from city-states to increase by 5 gold |
| goldenAgeLengthModifier         | Float (≥0)   | `modifier` value | Scales the length of golden ages                                                                            |
| religiousPressureAdjacentCity   | Integer (≥0) | 6                | Defines how much religious pressure a city exerts on nearby cities                                          |
| peaceDealDuration               | Integer (≥0) | 10               | The number of turns a peace deal lasts                                                                      |
| dealDuration                    | Integer (≥0) | 30               | The number of turns a non-peace deal (research agreement, open borders, etc.) lasts                         |
| startYear                       | Float        | -4000            | The start year of the game (negative is BC/BCE)                                                             |
| turns                           | List         | Required         | List of time interval per turn, [see below](#time-interval-per-turn)                                        |

### Time interval per turn

The "turns" attribute defines the number of years passed between turns. The attribute consists of a list of hashmaps, each hashmaps in turn having 2 required attributes: "yearsPerTurn" (Float) and "untilTurn" (Integer)

| Attribute    | Type    | Default  | Notes                                                                                    |
|--------------|---------|----------|------------------------------------------------------------------------------------------|
| yearsPerTurn | Integer | Required | Number of years passed between turns                                                     |
| untilTurn    | Integer | Required | Which turn that this "speed" is active until (if it is the last object, this is ignored) |

The code below is an example of a valid "turns" definition and it specifies that the first 50 turns of a game last for 60 years each, then the next 30 turns (and any played after the 80th) last for 40 years each.

```json
"turns": [
    {"yearsPerTurn": 60, "untilTurn":  50},
    {"yearsPerTurn": 40, "untilTurn":  80}
]
```

## Events.json

Events allow users to choose between options of triggers to activate.

| Attribute       | Type                 | Default  | Notes                                                                         |
|-----------------|----------------------|----------|-------------------------------------------------------------------------------|
| name            | String               | Required | Used for triggering via "Triggers a [event] event" unique                     |
| text            | String               | None     | Flavor text displayed to user                                                 |
| civilopediaText | List                 | Optional | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text) |
| choices         | List of EventChoices |          | User can choose to trigger one of the viable choices                          |

You can use text and/or civilopediaText, if both are present both are shown (but why would you?)

Event choices are comprised of:

| Attribute        | Type                        | Default    | Notes                                                                                                                |
|------------------|-----------------------------|------------|----------------------------------------------------------------------------------------------------------------------|
| text             | String                      | Required   | Displayed to user as button. Should be an action name - "Do X"                                                       |
| triggeredUniques | List of trigger uniques     | Required   | The triggers that this choice activates upon being chosen                                                            |
| conditions       | List of conditional uniques | Empty list | If any conditional is not met, this option becomes unpickable (not shown)                                            |
| keyShortcut      | key to select (name)        | none       | Key names see [Gdx.Input.Keys](https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/Input.java#L69) |
| civilopediaText  | List                        | Optional   | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                        |

Here, civilopediaText is shown outside the active Button, before the triggeredUniques.

## ModOptions.json

<!-- [Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/ModOptions.json) -->

This file is a little different:

- Does not exist in Vanilla ruleset
- Is entirely optional but will be created after downloading a mod

Note that this file controls _declarative mod compatibility_ (Work in progress) - e.g. there's [uniques](../../uniques.md#modoptions-uniques) to say your Mod should only or never be used as 'Permanent audiovisual mod'.
Incompatibility filtering works so far between extension and base mods, but feel free to document known extension-to-extension incompatibilities using the same Unique now. Stay tuned!

The file can have the following attributes, not including the values Unciv sets automatically:

| Attribute         | Type    |       | Notes                                                                                                                                                                                  |
|-------------------|---------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| isBaseRuleset     | Boolean | false | Replaces vanilla ruleset if true                                                                                                                                                       |
| uniques           | List    | empty | Mod-wide specials, [see here](../../uniques.md#modoptions-uniques)                                                                                                                     |
| techsToRemove     | List    | empty | List of [Technologies](2-Civilization-related-JSON-files.md#techsjson) or [technologyFilter](../../Unique-parameters.md#technologyfilter) to remove (isBaseRuleset=false only)         |
| buildingsToRemove | List    | empty | List of [Buildings or Wonders](2-Civilization-related-JSON-files.md#buildingsjson) or [buildingFilter](../../Unique-parameters.md#buildingfilter) to remove (isBaseRuleset=false only) |
| unitsToRemove     | List    | empty | List of [Units](4-Unit-related-JSON-files.md#unitsjson) or [unitFilter](../../Unique-parameters.md#baseunitfilter) to remove (isBaseRuleset=false only)                                |
| nationsToRemove   | List    | empty | List of [Nations](2-Civilization-related-JSON-files.md#nationsjson) or [nationFilter](../../Unique-parameters.md#nationfilter) to remove (isBaseRuleset=false only)                    |
| constants         | Object  | empty | See [ModConstants](#modconstants)                                                                                                                                                      |

The values normally set automatically from github metadata are:

| Attribute     | Type    |        | Notes                                                                                 |
|---------------|---------|--------|---------------------------------------------------------------------------------------|
| modUrl        | String  |        | The github page the mod was downloaded from, or empty if a freely hosted zip was used |
| defaultBranch | String  | master | The repo's default branch                                                             |
| author        | String  |        | Repo owner                                                                            |
| lastUpdated   | String  |        | ISO date                                                                              |
| modSize       | Integer | 0      | Size in kB                                                                            |
| topics        | List    | empty  | A list of "unciv-mod-*" github topics                                                 |

To clarify: When your Mod is distributed via github, including these in the Mod repo has no effect.
However, when a Mod is distributed _without_ a github repository, these values can and _should_ be set by the author in the distributed `ModOptions.json`.

### ModConstants

Stored in ModOptions.constants, this is a collection of constants used internally in Unciv.
This is the only structure that is _merged_ field by field from mods, not overwritten, so you can change XP from Barbarians in one mod
and city distance in another. In case of conflicts, there is no guarantee which mod wins, only that _default_ values are ignored.

| Attribute                                | Type   | Default                       | Notes |
|------------------------------------------|--------|-------------------------------|-------|
| maxXPfromBarbarians                      | Int    | 30                            | [^A]  |
| cityStrengthBase                         | Float  | 8.0                           | [^B]  |
| cityStrengthPerPop                       | Float  | 0.4                           | [^B]  |
| cityStrengthFromTechsMultiplier          | Float  | 5.5                           | [^B]  |
| cityStrengthFromTechsExponent            | Float  | 2.8                           | [^B]  |
| cityStrengthFromTechsFullMultiplier      | Float  | 1.0                           | [^B]  |
| cityStrengthFromGarrison                 | Float  | 0.2                           | [^B]  |
| unitSupplyPerPopulation                  | Float  | 0.5                           | [^C]  |
| minimalCityDistance                      | Int    | 3                             | [^D]  |
| minimalCityDistanceOnDifferentContinents | Int    | 2                             | [^D]  |
| unitUpgradeCost                          | Object | [See below](#unitupgradecost) | [^J]  |
| naturalWonderCountMultiplier             | Float  | 0.124                         | [^E]  |
| naturalWonderCountAddedConstant          | Float  | 0.1                           | [^E]  |
| ancientRuinCountMultiplier               | Float  | 0.02                          | [^F]  |
| spawnIceBelowTemperature                 | Float  | -0.8                          | [^G]  |
| maxLakeSize                              | Int    | 10                            | [^H]  |
| riverCountMultiplier                     | Float  | 0.01                          | [^I]  |
| minRiverLength                           | Int    | 5                             | [^I]  |
| maxRiverLength                           | Int    | 666                           | [^I]  |
| religionLimitBase                        | Int    | 1                             | [^K]  |
| religionLimitMultiplier                  | Float  | 0.5                           | [^K]  |
| pantheonBase                             | Int    | 10                            | [^L]  |
| pantheonGrowth                           | Int    | 5                             | [^L]  |
| workboatAutomationSearchMaxTiles         | Int    | 20                            | [^M]  |

Legend:

- [^A]: Max amount of experience that can be gained from combat with barbarians
- [^B]: Formula for city Strength:
    Strength = baseStrength + strengthPerPop + strengthFromTiles +
    ((%techs \* multiplier) ^ exponent) \* fullMultiplier +
    (garrisonBonus \* garrisonUnitStrength \* garrisonUnitHealth/100) +
    defensiveBuildingStrength
    where %techs is the percentage of techs in the tech tree that are complete
    If no techs exist in this ruleset, %techs = 0.5 (=50%)
- [^C]: Formula for Unit Supply:
    Supply = unitSupplyBase (difficulties.json)
    unitSupplyPerCity \* amountOfCities + (difficulties.json)
    unitSupplyPerPopulation \* amountOfPopulationInAllCities
    unitSupplyBase and unitSupplyPerCity can be found in difficulties.json
    unitSupplyBase, unitSupplyPerCity and unitSupplyPerPopulation can also be increased through uniques
- [^D]: The minimal distance that must be between any two cities, not counting the tiles cities are on
    The number is the amount of tiles between two cities, not counting the tiles the cities are on.
    e.g. "C__C", where "C" is a tile with a city and "_" is a tile without a city, has a distance of 2.
    First constant is for cities on the same landmass, the second is for cities on different continents.
- [^E]: NaturalWonderGenerator uses these to determine the number of Natural Wonders to spawn for a given map size. The number scales linearly with map radius: #wonders = radius * naturalWonderCountMultiplier + naturalWonderCountAddedConstant. The defaults effectively mean Tiny - 1, Small - 2, Medium - 3, Large - 4, Huge - 5, Custom radius >=109 - all G&K wonders.
- [^F]: MapGenerator.spreadAncientRuins: number of ruins = suitable tile count * this
- [^G]: MapGenerator.spawnIce: spawn Ice where T < this, with T calculated from temperatureExtremeness, latitude and perlin noise.
- [^H]: MapGenerator.spawnLakesAndCoasts: Water bodies up to this tile count become Lakes
- [^I]: RiverGenerator: river frequency and length bounds
- [^J]: A [UnitUpgradeCost](#unitupgradecost) sub-structure.
- [^K]: Maximum foundable Religions = religionLimitBase + floor(MajorCivCount * religionLimitMultiplier)
- [^L]: Cost of pantheon = pantheonBase + CivsWithReligion * pantheonGrowth
- [^M]: When the AI decidees whether to build a work boat, how many tiles to search from the city center for an improvable tile

#### UnitUpgradeCost

These values are not merged individually, only the entire sub-structure is.

| Attribute     | Type  |    | Notes |
|---------------|-------|----|-------|
| base          | Float | 10 |       |
| perProduction | Float | 2  |       |
| eraMultiplier | Float | 0  |       |
| exponent      | Float | 1  |       |
| roundTo       | Int   | 5  |       |

The formula for the gold cost of a unit upgrade is (rounded down to a multiple of `roundTo`):
        (
            max((`base` + `perProduction` \* (new_unit_cost - old_unit_cost)), 0)
            \* (1 + eraNumber \* `eraMultiplier`) \* `civModifier`
        ) ^ `exponent`
With `civModifier` being the multiplicative aggregate of ["\[relativeAmount\]% Gold cost of upgrading"](../../uniques.md#global-uniques) uniques that apply.

## GlobalUniques.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/GlobalUniques.json)

GlobalUniques defines uniques that apply globally. e.g. Vanilla rulesets define the effects of Unhappiness here.
Only the `uniques` field is used, but a name must still be set (the Ruleset validator might display it).
When extension rulesets define GlobalUniques, all uniques are merged. At the moment there is no way to change/remove uniques set by a base mod.

## Tutorials.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Tutorials.json)

**Note a Base Ruleset mod can define a "welcome page" here by adding a "Tutorial" with a name equal to the name of the mod!**
As an exception to the general rule, this file in a Base Ruleset mod will not _replace_ the default, but add to it like extension mods do.
Also, place it under `<mod>/jsons/` normally even if the original is found one level above the vanilla jsons.

Each tutorial has the following structure:

| Attribute       | Type            | Default  | Notes                                                                         |
|-----------------|-----------------|----------|-------------------------------------------------------------------------------|
| name            | String          | Required | Entry name                                                                    |
| civilopediaText | List            | Optional | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text) |
| steps           | List of Strings | Optional | Plain text                                                                    |

If an entry contains both `steps` and `civilopediaText` attributes, the `civilopediaText` is shown first.
Tutorials shown as Popup can show an show an external image (not part of the texture atlases) if there is an image unter ExtraImages (directly under assets or the Mod folder) having the same name.
This is searched for, meaning the mod defining the Tutorial is irrelevant, mods can override builtin ExtraImages, and case sensitivity depends on the OS.

## VictoryTypes.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/VictoryTypes.json)

These files contain which victories this mod provides, and what milestones must be reached for someone to win a victory.
Most of the file contains of strings that are shown to the user in the victory screen, with the rest being the requirements for winning.

Each victory have the following structure:

| Attribute              | Type            | Default  | Notes                                                                                      |
|------------------------|-----------------|----------|--------------------------------------------------------------------------------------------|
| name                   | String          | Required | Name of the victory                                                                        |
| victoryScreenHeader    | String          | none     | Shown in the footer of the victory in the `our status` in the victory screen               |
| victoryString          | String          | none     | Shown in the footer of the victory screen when you won the game with this victory          |
| defeatString           | String          | none     | Shown in the footer of the victory screen when someone else won the game with this victory |
| hiddenInVictoryScreen  | Boolean         | false    | Whether progress of this victory is hidden in the victory screen                           |
| requiredSpaceshipParts | List of Strings | empty    | What spaceship parts must be added to the capital for the corresponding milestone          |
| Milestones             | List of Strings | Required | List of milestones that must be accomplished to win, [see below](#milestones)              |

### Milestones

Currently the following milestones are supported:

| Milestone                          | Requirement                                                                                                  |
|------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Build [building]                   | Build the building [building] in any city                                                                    |
| Anyone should build [building]     | Anyone must build the building [building] for all players to have this milestone                             |
| Add all [comment] in capital       | Add all units in the `requiredSpaceshipParts` field of this victory to the capital                           |
| Destroy all players                | You must be the only major civilization with any cities left                                                 |
| Capture all capitals               | Capture all the original capitals of major civilizations in the game                                         |
| Complete [amount] Policy branches  | Fully complete at least [amount] policy branches                                                             |
| Win diplomatic vote                | At any point in the game win a diplomatic vote (UN). You may lose afterwards and still retain this milestone |
| Become the world religion          | Have your religion be the majority religion in a majority of cities of all major civs                        |
| Have highest score after max turns | Basically time victory. Enables the 'max turn' slider and calculates score when that amount is reached       |

## Civilopedia text

Any 'thing' defined in json and listed in the Civilopedia can supply extra text, specifically for the Civilopedia. This can be used to explain special considerations better when the automatically generated display is insufficient, or for 'flavour', background stories and the like. Such text can be formatted and linked to other Civilopedia entries, within limits.

An example of the format is:

```json
"civilopediaText": [
    { "text": "Ancient ruins provide a one-time random bonus when explored" },
    { "separator": true },
    {
        "text": "This line is red and links to the Scout including icons",
        "link": "Unit/Scout",
        "color": "red"
    },
    {
        "text": "A big fat header sporting a golden star",
        "header": 1,
        "starred": true,
        "color": "#ffeb7f"
    },
],
```

List of attributes - note not all combinations are valid:

| Attribute    | Type    | Description                                                                                                                       |
|--------------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `text`       | String  | Text to display                                                                                                                   |
| `link`       | String  | Create link and icon, format: Category/Name or _external_ link ('http://','https://','mailto:')                                   |
| `icon`       | String  | Show icon without linking, format: Category/Name                                                                                  |
| `extraImage` | String  | Display an Image instead of text. Can be a path found in a texture atlas or or the name of a png or jpg in the ExtraImages folder |
| `imageSize`  | Float   | Size in world units of the [extraImage], the smaller coordinate is calculated preserving aspect ratio. available width            |
| `header`     | Integer | Header level. 1 means double text size and decreases from there                                                                   |
| `size`       | Integer | Text size, is 18. Use `size` or `header` but not both                                                                             |
| `indent`     | Integer | Indent level. 0 means text will follow icons, 1 aligns to the right of all icons, each further step is 30 units                   |
| `padding`    | Float   | Vertical padding between rows, 5 units                                                                                            |
| `color`      | String  | Sets text color, accepts names or 6/3-digit web colors (e.g. #FFA040)                                                             |
| `separator`  | Boolean | Renders a separator line instead of text. Can be combined only with `color` and `size` (line width, default 2)                    |
| `starred`    | Boolean | Decorates text with a star icon - if set, it receives the `color` instead of the text                                             |
| `centered`   | Boolean | Centers the line (and turns off automatic wrap). For an `extraImage`, turns on crop-to-content to equalize transparent borders    |

The lines from json will 'surround' the automatically generated lines such that the latter are inserted just above the first json line carrying a link, if any. If no json lines have links, they will be inserted between the automatic title and the automatic info. This method may, however, change in the future.

Note: `text` now also supports inline color markup. Insert `«color»` to start coloring text, `«»` to stop. `color` can be a name or 6/8-digit hex notation like `#ffa040` (different from the `color` attribute notation only by not allowing 3-digit codes, but allowing the alpha channel).
Effectively, the `«»` markers are replaced with `[]` _after_ translation and then passed to [gdx markup language](https://libgdx.com/wiki/graphics/2d/fonts/color-markup-language).

Note: Using an ExtraImages folder in a mod was not working until version 4.11.5

## RGB colors list

Certain objects can be specified to have its own unique color. The colors are defined by a list of 3× Integer in this order: red, green, blue. The range of color is from \[0, 0, 0\] (black) to \[255, 255, 255\] (white).

Note: The default of some objects are [gdx color classes](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/graphics/Color.html). The values of the constants are as follows:

| name  | value           |
|-------|-----------------|
| gold  | [225, 215, 0]   |
| white | [255, 255, 255] |
| black | [0, 0, 0]       |

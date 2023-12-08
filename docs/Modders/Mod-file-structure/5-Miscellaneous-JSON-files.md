# Miscellaneous JSON files

## Difficulties.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Difficulties.json)

This file defines the difficulty levels a player can choose when starting a new game.

Each difficulty level can have the following attributes:

| Attribute | Type | Optional | Notes                                                                                                                                                                                                  |
| --------- | ---- | -------- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name | String | Required | Name of the difficulty level                                                                                                                                                                           |
| baseHappiness | Integer | Default 0 |
| extraHappinessPerLuxury | Float | Default 0 |
| researchCostModifier | Float | Default 1 |
| unitCostModifier | Float | Default 1 |
| buildingCostModifier | Float | Default 1 |
| policyCostModifier | Float | Default 1 |
| unhappinessModifier | Float | Default 1 |
| barbarianBonus | Float | Default 0 |
| playerBonusStartingUnits | List of Units | Default empty | Can also be 'Era Starting Unit', maps to `startingMilitaryUnit` of the Eras file. All other units must be in [Units.json](4-Unit-related-JSON-files.md#Units.json)]. Applies only to human player civs |
| aiCityGrowthModifier | Float | Default 1 |
| aiUnitCostModifier | Float | Default 1 |
| aiBuildingCostModifier | Float | Default 1 |
| aiWonderCostModifier | Float | Default 1 |
| aiBuildingMaintenanceModifier | Float | Default 1 |
| aiUnitMaintenanceModifier | Float | Default 1 |
| aiFreeTechs | List of Techs | Default empty |
| aiMajorCivBonusStartingUnits | List of Units | Default empty | Same rules as playerBonusStartingUnits, See above. Applies only to AI major civs                                                                                                                       |
| aiCityStateBonusStartingUnits | List of Units | Default empty | Same rules as playerBonusStartingUnits, See above. Applies only to city-state civs                                                                                                                     |
| aiUnhappinessModifier | Float | Default 1 |
| aisExchangeTechs | Boolean | | Unimplemented                                                                                                                                                                                          |
| turnBarbariansCanEnterPlayerTiles | Integer | Default 0 |
| clearBarbarianCampReward | Integer | Default 25 |

## Eras.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Eras.json)

This file should contain all the era's you want to use in your mod.

Each era can have the following attributes:

| Attribute | Type | Optional | Notes                                                                                                                                                                                                                                              |
| --------- | ---- | -------- |----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name | String | required | Name of the era                                                                                                                                                                                                                                    |
| researchAgreementCost | Integer (≥0) | defaults to 300 | Cost of research agreements when the most technologically advanced civ is in this era                                                                                                                                                              |
| iconRGB | List of 3 Integers | defaults to [255, 255, 255] | RGB color that icons for technologies of this era should have in the Tech screen                                                                                                                                                                   |
| unitBaseBuyCost | Integer (≥0) | defaults to 200 | Base cost of buying units with Faith, Food, Science or Culture when no other cost is provided                                                                                                                                                      |
| startingSettlerCount | Integer (≥0) | defaults to 1 | Amount of settler units that should be spawned when starting a game in this era (setting this to zero is discouraged [^1])                                                                                                                         |
| startingSettlerUnit | String | defaults to "Settler" | Name of the unit that should be used for the previous field. Must be in [Units.json](4-Unit-related-JSON-files.md#unitsjson), or a unit with the "Founds a new city" unique must exist                                                             |
| startingWorkerCount | Integer (≥0) | defaults to 0 | Amount of worker units that should be spawned when starting a game in this era                                                                                                                                                                     |
| startingWorkerUnit | String | defaults to "Worker" | Name of the unit that should be used for the previous field. If startingWorkerCount>0, then it must exist in [Units.json](4-Unit-related-JSON-files.md#unitsjson), or a unit with the "Can build [filter] improvements on tiles" unique must exist |
| startingMilitaryUnitCount | Integer (≥0) | defaults to 1 | Amount of military units that should be spawned when starting a game in this era                                                                                                                                                                   |
| startingMilitaryUnit | String | defaults to "Warrior" | Name of the unit that should be used for the previous field. Must be in [Units.json](4-Unit-related-JSON-files.md#unitsjson)                                                                                                                       |
| startingGold | Integer (≥0) | defaults to 0 | Amount of gold each civ should receive when starting a game in this era                                                                                                                                                                            |
| startingCulture | Integer (≥0) | defaults to 0 | Amount of culture each civ should receive when starting a game in this era                                                                                                                                                                         |
| settlerPopulation | Integer (>0) | defaults to 1 | Default amount of population each city should have when settled when starting a game in this era                                                                                                                                                   |
| settlerBuildings | List of Strings | defaults to none | Buildings that should automatically be built whenever a city is settled when starting a game in this era                                                                                                                                           |
| startingObsoleteWonders | List of Strings | defaults to none | Wonders (and technically buildings) that should be impossible to built when starting a game in this era. Used in the base game to remove all wonders older than 2 era's                                                                            |

[^1]: Successfully setting startingSettlerCount to zero in a mod (idea: conquer or die) is not easy. Some player-controlled settings require at least one Settler, through any source (see difficulties for other possible settler sources), or you won't be able to start a game: Once City Challenge requires one for all players, and allowing any city-states requires one for those. Would also affect defeat rules.

## Speeds.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Speeds.json)

This file should contain all the speeds you want to use in your mod.

Each speed can have the following attributes:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | required | Name of the speed |
| modifier | Float (≥0) | defaults to 1.0 | Overall game speed modifier |
| productionCostModifier | Float (≥0) | defaults to the value of `modifier` | Scales production cost of units and buildings |
| goldCostModifier | Float (≥0) | defaults to the value of `modifier` | Scales gold costs |
| scienceCostModifier | Float (≥0) | defaults to the value of `modifier` | Scales science costs |
| cultureCostModifier | Float (≥0) | defaults to the value of `modifier` | Scales culture costs |
| faithCostModifier | Float (≥0) | defaults to the value of `modifier` | Scales faith costs |
| improvementBuildLengthModifier | Float (≥0) | defaults to the value of `modifier` | Scales the time it takes for a worker to build tile improvements |
| barbarianModifier | Float (≥0) | defaults to the value of `modifier` | Scales the time between barbarian spawns |
| goldGiftModifier | Float (≥0) | defaults to the value of `modifier` | Scales the influence gained from gifting gold to city-states |
| cityStateTributeScalingInterval | Float (≥0) | defaults to 6.5 | The number of turns it takes for the amount of gold a player demands from city-states to increase by 5 gold |
| goldenAgeLengthModifier | Float (≥0) | defaults to the value of `modifier` | Scales the length of golden ages |
| religiousPressureAdjacentCity | Integer (≥0) | defaults to 6 | Defines how much religious pressure a city exerts on nearby cities |
| peaceDealDuration | Integer (≥0) | defaults to 10 | The number of turns a peace deal lasts |
| dealDuration | Integer (≥0) | defaults to 30 | The number of turns a non-peace deal (research agreement, open borders, etc.) lasts |
| startYear | Float | defaults to -4000 | The start year of the game (negative is BC/BCE) |
| turns | List of HashMaps | required | The amount of time passed between turns ("yearsPerTurn") and the range of turn numbers ("untilTurn") that this duration applies to |

The below code is an example of a valid "turns" definition and it specifies that the first 50 turns of a game last for 60 years each, then the next 30 turns (and any played after the 80th) last for 40 years each.

```json
"turns": [
{"yearsPerTurn": 60, "untilTurn":  50},
{"yearsPerTurn": 40, "untilTurn":  80}
]
```

## ModOptions.json

<!-- [Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/ModOptions.json) -->

This file is a little different:

-   Does not exist in Vanilla ruleset
-   Is entirely optional but will be created after downloading a mod

Note that this file controls _declarative mod compatibility_ (Work in progress) - e.g. there's [uniques](../uniques.md#modoptions-uniques) to say your Mod should only or never be used as 'Permanent audiovisual mod'.
Incompatibility filtering works so far between extension and base mods, but feel free to document known extension-to-extension incompatibilities using the same Unique now. Stay tuned!

The file can have the following attributes, including the values Unciv sets (no point in a mod author setting those):

| Attribute           | Type    | Optional | Notes                                                                                                                                                                               |
|---------------------|---------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| isBaseRuleset       | Boolean | false    | Differentiates mods that change the vanilla ruleset or replace it                                                                                                                   |
| maxXPfromBarbarians | Integer | 30       | *Deprecated*, see [constants](#ModConstants)                                                                                                                                        |
| uniques             | List    | empty    | Mod-wide specials, [see here](../uniques.md#modoptions-uniques)                                                                                                             |
| techsToRemove       | List    | empty    | List of [Technologies](2-Civilization-related-JSON-files.md#techsjson) or [-filters](../Unique-parameters.md#technologyfilter) to remove (isBaseRuleset=false only)           |
| buildingsToRemove   | List    | empty    | List of [Buildings or Wonders](2-Civilization-related-JSON-files.md#buildingsjson) or [-filters](../Unique-parameters.md#buildingfilter) to remove (isBaseRuleset=false only) |
| unitsToRemove       | List    | empty    | List of [Units](4-Unit-related-JSON-files.md#unitsjson) or [-filters](../Unique-parameters.md#baseunitfilter) to remove (isBaseRuleset=false only)                            |
| nationsToRemove     | List    | empty    | List of [Nations](2-Civilization-related-JSON-files.md#nationsjson) or [-filters](../Unique-parameters.md#nationfilter) to remove (isBaseRuleset=false only)                  |
| lastUpdated         | String  | empty    | Set automatically after download - Last repository update, not necessarily last content change                                                                                      |
| modUrl              | String  | empty    | Set automatically after download - URL of repository                                                                                                                                |
| author              | String  | empty    | Set automatically after download - Owner of repository                                                                                                                              |
| modSize             | Integer | empty    | Set automatically after download - kB in entire repository, not sum of default branch files                                                                                         |
| constants           | Object  | empty    | see [ModConstants](#ModConstants)                                                                                                                                                   |

### ModConstants

Stored in ModOptions.constants, this is a collection of constants used internally in Unciv.
This is the only structure that is _merged_ field by field from mods, not overwritten, so you can change XP from Barbarians in one mod
and city distance in another. In case of conflicts, there is no guarantee which mod wins, only that _default_ values are ignored.

| Attribute                                | Type   | Default   | Notes |
|------------------------------------------|--------|-----------|-------|
| maxXPfromBarbarians                      | Int    | 30        | [^A]  |
| cityStrengthBase                         | Float  | 8.0       | [^B]  |
| cityStrengthPerPop                       | Float  | 0.4       | [^B]  |
| cityStrengthFromTechsMultiplier          | Float  | 5.5       | [^B]  |
| cityStrengthFromTechsExponent            | Float  | 2.8       | [^B]  |
| cityStrengthFromTechsFullMultiplier      | Float  | 1.0       | [^B]  |
| cityStrengthFromGarrison                 | Float  | 0.2       | [^B]  |
| unitSupplyPerPopulation                  | Float  | 0.5       | [^C]  |
| minimalCityDistance                      | Int    | 3         | [^D]  |
| minimalCityDistanceOnDifferentContinents | Int    | 2         | [^D]  |
| unitUpgradeCost                          | Object | see below | [^J]  |
| naturalWonderCountMultiplier             | Float  | 0.124     | [^E]  |
| naturalWonderCountAddedConstant          | Float  | 0.1       | [^E]  |
| ancientRuinCountMultiplier               | Float  | 0.02      | [^F]  |
| maxLakeSize                              | Int    | 10        | [^H]  |
| riverCountMultiplier                     | Float  | 0.01      | [^I]  |
| minRiverLength                           | Int    | 5         | [^I]  |
| maxRiverLength                           | Int    | 666       | [^I]  |
| religionLimitBase                        | Int    | 1         | [^K]  |
| religionLimitMultiplier                  | Float  | 0.5       | [^K]  |
| pantheonBase                             | Int    | 10        | [^L]  |
| pantheonGrowth                           | Int    | 5         | [^L]  |


Legend:

- [^A]: Max amount of experience that can be gained from combat with barbarians
- [^B]: Formula for city Strength:
    Strength = baseStrength + strengthPerPop + strengthFromTiles +
    ((%techs * multiplier) ^ exponent) * fullMultiplier +
    (garrisonBonus * garrisonUnitStrength * garrisonUnitHealth/100) +
    defensiveBuildingStrength
    where %techs is the percentage of techs in the tech tree that are complete
    If no techs exist in this ruleset, %techs = 0.5 (=50%)
- [^C]: Formula for Unit Supply:
    Supply = unitSupplyBase (difficulties.json)
    unitSupplyPerCity * amountOfCities + (difficulties.json)
    unitSupplyPerPopulation * amountOfPopulationInAllCities
    unitSupplyBase and unitSupplyPerCity can be found in difficulties.json
    unitSupplyBase, unitSupplyPerCity and unitSupplyPerPopulation can also be increased through uniques
- [^D]: The minimal distance that must be between any two cities, not counting the tiles cities are on
    The number is the amount of tiles between two cities, not counting the tiles the cities are on.
    e.g. "C__C", where "C" is a tile with a city and "_" is a tile without a city, has a distance of 2.
    First constant is for cities on the same landmass, the second is for cities on different continents.
- [^E]: NaturalWonderGenerator uses these to determine the number of Natural Wonders to spawn for a given map size. The number scales linearly with map radius: #wonders = radius * naturalWonderCountMultiplier + naturalWonderCountAddedConstant. The defaults effectively mean Tiny - 1, Small - 2, Medium - 3, Large - 4, Huge - 5, Custom radius >=109 - all G&K wonders.
- [^F]: MapGenerator.spreadAncientRuins: number of ruins = suitable tile count * this
- [^H]: MapGenerator.spawnLakesAndCoasts: Water bodies up to this tile count become Lakes
- [^I]: RiverGenerator: river frequency and length bounds
- [^J]: A [UnitUpgradeCost](#UnitUpgradeCost) sub-structure.
- [^K]: Maximum foundable Religions = religionLimitBase + floor(MajorCivCount * religionLimitMultiplier)
- [^L]: Cost of pantheon = pantheonBase + CivsWithReligion * pantheonGrowth

#### UnitUpgradeCost

These values are not merged individually, only the entire sub-structure is.

| Attribute | Type | Default | Notes |
| --------- | ---- | -------- | ----- |
| base | Float | 10 |  |
| perProduction | Float | 2 |  |
| eraMultiplier | Float | 0 |  |
| exponent | Float | 1 |  |
| roundTo | Int | 5 |  |

The formula for the gold cost of a unit upgrade is (rounded down to a multiple of `roundTo`):
        ( max((`base` + `perProduction` * (new_unit_cost - old_unit_cost)), 0)
            * (1 + eraNumber * `eraMultiplier`) * `civModifier`
        ) ^ `exponent`
With `civModifier` being the multiplicative aggregate of ["\[relativeAmount\]% Gold cost of upgrading"](../uniques.md#global-uniques) uniques that apply.

## GlobalUniques.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/GlobalUniques.json)

Defines uniques that apply globally. e.g. Vanilla rulesets define the effects of Unhappiness here.
Only the `uniques` field is used, but a name must still be set (the Ruleset validator might display it).
When extension rulesets define GlobalUniques, all uniques are merged. At the moment there is no way to change/remove uniques set by a base mod.

## Tutorials.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Tutorials.json)

**Note a Base Ruleset mod can define a "welcome page" here by adding a "Tutorial" with a name equal to the name of the mod!**
As an exception to the general rule, this file in a Base Ruleset mod will not _replace_ the default, but add to it like extension mods do.
Also, place it under `<mod>/jsons/` normally even if the original is found one level above the vanilla jsons.

| Attribute       | Type         | Optional | Notes                                                    |
|-----------------|--------------| -------- |----------------------------------------------------------|
| name            | String       | Required | Entry name                                               |
| civilopediaText | List         | Optional | [see here](5-Miscellaneous-JSON-files.md#civilopedia-text) |
| steps           | List(String) | Optional | Plain text                                               |

If an entry contains both `steps`and `civilopediaText` attributes, the `civilopediaText` is shown first.


## VictoryTypes.json

[link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/VictoryTypes.json)

These files contain which victories this mod provides, and what milestones must be reached for someone to win a victory.
Most of the file contains of strings that are shown to the user in the victory screen, with the rest being the requirements for winning.

Each victory can have the following attributes:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | Required | Name of the victory |
| victoryScreenHeader | String | Defaults to "" | Shown in the footer of the victory in the `our status` in the victory screen |
| victoryString | String | Defaults to "" | Shown in the footer of the victory screen when you won the game with this victory |
| defeatString | String | Defaults to "" | Shown in the footer of the victory screen when someone else won the game with this victory |
| hiddenInVictoryScreen | Boolean | Defaults to false | Whether progress of this victory is hidden in the victory screen |
| requiredSpaceshipParts | List of Strings | Defaults to "" | What spaceship parts must be added to the capital for the corresponding milestone |
| Milestones | List of Strings | Required | List of milestones that must be accomplished to win, see [below](#Milestones) |

### Milestones

Currently the following milestones are supported:

| Milestone | Requirement |
| --------- | ----------- |
| Build [building] | Build the building [building] in any city |
| Anyone build [building] | Anyone must build the building [building] for all players to have this milestone |
| Add all [comment] in capital | Add all units in the `requiredSpaceshipParts` field of this victory to the capital |
| Destroy all players | You must be the only major civilization with any cities left |
| Capture all capitals | Capture all the original capitals of major civilizations in the game |
| Complete [amount] Policy branches | Fully complete at least [amount] policy branches |
| Win diplomatic vote | At any point in the game win a diplomatic vote (UN). You may lose afterwards and still retain this milestone |
| Become the world religion | Have your religion be the majority religion in a majority of cities of all major civs |
| Have highest score after max turns | Basically time victory. Enables the 'max turn' slider and calculates score when that amount is reached |


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

| Attribute    | Type    | Description                                                                                                                         |
|--------------|---------|-------------------------------------------------------------------------------------------------------------------------------------|
| `text`       | String  | Text to display.                                                                                                                    |
| `link`       | String  | Create link and icon, format: Category/Name or _external_ link ('http://','https://','mailto:').                                    |
| `icon`       | String  | Show icon without linking, format: Category/Name.                                                                                   |
| `extraImage` | String  | Display an Image instead of text. Can be a path found in a texture atlas or or the name of a png or jpg in the ExtraImages folder.  |
| `imageSize`  | Float   | Size in world units of the [extraImage], the smaller coordinate is calculated preserving aspect ratio. Defaults to available width. |
| `header`     | Integer | Header level. 1 means double text size and decreases from there.                                                                    |
| `size`       | Integer | Text size, default is 18. Use `size` or `header` but not both.                                                                      |
| `indent`     | Integer | Indent level. 0 means text will follow icons, 1 aligns to the right of all icons, each further step is 30 units.                    |
| `padding`    | Float   | Vertical padding between rows, defaults to 5 units.                                                                                 |
| `color`      | String  | Sets text color, accepts names or 6/3-digit web colors (e.g. #FFA040).                                                              |
| `separator`  | Boolean | Renders a separator line instead of text. Can be combined only with `color` and `size` (line width, default 2).                     |
| `starred`    | Boolean | Decorates text with a star icon - if set, it receives the `color` instead of the text.                                              |
| `centered`   | Boolean | Centers the line (and turns off automatic wrap). For an `extraImage`, turns on crop-to-content to equalize transparent borders.     |

The lines from json will 'surround' the automatically generated lines such that the latter are inserted just above the first json line carrying a link, if any. If no json lines have links, they will be inserted between the automatic title and the automatic info. This method may, however, change in the future.

Note: `text` now also supports inline color markup. Insert `«color»` to start coloring text, `«»` to stop. `color` can be a name or 6/8-digit hex notation like `#ffa040` (different from the `color` attribute notation only by not allowing 3-digit codes, but allowing the alpha channel).
Effectively, the `«»` markers are replaced with `[]` _after_ translation and then passed to [Gdx markup language](https://libgdx.com/wiki/graphics/2d/fonts/color-markup-language).


* [Difficulties.json](#difficultiesjson)
* [Eras.json](#erasjson)
* [ModOptions.json](#modoptionsjson)
* [Generic Civilopedia Text](#civilopedia-text)


## Difficulties.json
[Link to original](/jsons/Civ%20V%20-%20Vanilla/Difficulties.json)

This file defines the difficulty levels a player can choose when starting a new game.

Each difficulty level can have the following attributes:
| Attribute | Type | Mandatory | Notes |
| --------- | ---- | ------- | ----- |
| name | String | Required | Name of the difficulty level |
| baseHappiness | Integer | Default 0 |
| extraHappinessPerLuxury | Float | Default 0 |
| researchCostModifier | Float | Default 1 |
| unitCostModifier | Float | Default 1 |
| buildingCostModifier | Float | Default 1 |
| policyCostModifier | Float | Default 1 |
| unhappinessModifier | Float | Default 1 |
| barbarianBonus | Float | Default 0 |
| playerBonusStartingUnits | List of Units | Default empty | Can also be 'Era Starting Unit', maps to `startingMilitaryUnit` of the Eras file. All other units must be in [Units.json](Unit-related-JSON-files.md#unitsjson)] |
| aiCityGrowthModifier | Float | Default 1 |
| aiUnitCostModifier | Float | Default 1 |
| aiBuildingCostModifier | Float | Default 1 |
| aiWonderCostModifier | Float | Default 1 |
| aiBuildingMaintenanceModifier | Float | Default 1 |
| aiUnitMaintenanceModifier | Float | Default 1 |
| aiFreeTechs | List of Techs | Default empty |
| aiMajorCivBonusStartingUnits | List of Units | Default empty | See above |
| aiCityStateBonusStartingUnits | List of Units | Default empty | See above |
| aiUnhappinessModifier | Float | Default 1 |
| aisExchangeTechs | Boolean | | Unimplemented |
| turnBarbariansCanEnterPlayerTiles | Integer | Default 0 |
| clearBarbarianCampReward | Integer | Default 25 |


## Eras.json
[Link to original](/jsons/Civ%20V%20-%20Vanilla/Eras.json)

This file should contain all the era's you want to use in your mod.

Each era can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the era |
| researchAgreementCost | Integer (≥0) | defaults to 300 | Cost of research agreements were the most technologically advanced civ is in this era |
| iconRGB | List of 3 Integers | defaults to [255,255,255] | RGB color that icons for technologies of this era should have in the Tech screen |
| unitBaseBuyCost | Integer (≥0) | defaults to 200 | Base cost of buying units with Faith, Food, Science or Culture when no other cost is provided |
| startingSettlerCount | Integer (≥0) | defaults to 1 | Amount of settler units that should be spawned when starting a game in this era |
| startingSettlerUnit | String | defaults to "Settler" | Name of the unit that should be used for the previous field. Must be in [Units.json](Unit-related-JSON-files.md#unitsjson) |
| startingWokerCount | Integer (≥0) | defaults to 0 | Amount of worker units that should be spawned when starting a game in this era |
| startingWorkerUnit | String | defaults to "Worker" | Name of the unit that should be used for the previous field. Must be in [Units.json](Unit-related-JSON-files.md#unitsjson) |
| startingMilitaryUnitCount | Integer (≥0) | defaults to 1 | Amount of military units that should be spawned when starting a game in this era |
| startingMilitaryUnit | String | defaults to "Warrior" | Name of the unit that should be used for the previous field. Must be in [Units.json](Unit-related-JSON-files.md#unitsjson)|
| startingGold | Integer (≥0) | defaults to 0 | Amount of gold each civ should receive when starting a game in this era |
| startingCulture | Integer (≥0) | defaults to 0 | Amount of culture each civ should receive when starting a game in this era |
| settlerPopulation | Integer (>0) | defaults to 1 | Default amount of population each city should have when settled when starting a game in this era |
| settlerBuildings | List of Strings | defaults to none | Buildings that should automatically be built whenever a city is settled when starting a game in this era |
| startingObsoleteWonders | List of Strings | defaults to none | Wonders (and technically buildings) that should be impossible to built when starting a game in this era. Used in the base game to remove all wonders older than 2 era's |


## ModOptions.json
This file is a little different:
- Does not exist in Vanilla ruleset
- Is entirely optional but will be created after downloading a mod

The file can have the following attributes, including the values Unciv sets (no point in a mod author setting those):
| Attribute | Type | Defaults | Notes |
|-----------|------|-----------|-------|
| isBaseRuleset | Boolean | false | Differentiates mods that change the vanilla ruleset or replace it |
| maxXPfromBarbarians | Integer | 30 | ...as the name says... |
| uniques | List | empty | Mod-wide specials, [see here](../Modders/Unique-parameter-types.md#modoptions-uniques) |
| techsToRemove | List | empty | List of [Technologies](Civilization-related-JSON-files.md#techsjson) to remove (isBaseRuleset=false only) |
| buildingsToRemove | List | empty | List of [Buildings or Wonders](Civilization-related-JSON-files.md#buildingsjson) to remove (isBaseRuleset=false only) |
| unitsToRemove | List | empty | List of [Units](Unit-related-JSON-files.md#unitsjson) to remove (isBaseRuleset=false only) |
| nationsToRemove | List | empty | List of [Nations](Civilization-related-JSON-files.md#nationsjson) to remove (isBaseRuleset=false only) |
| lastUpdated | String | empty | Set automatically after download - Last repository update, not necessarily last content change |
| modUrl | String | empty | Set automatically after download - URL of repository |
| author | String | empty | Set automatically after download - Owner of repository |
| modSize | Integer | empty | Set automatically after download - kB in entire repository, not sum of default branch files |


## Civilopedia text
Any 'thing' defined in json and listed in the Civilopedia can supply extra text, specifically for the Civilopedia. This can be used to explain special considerations better when the automatically generated display is insufficient, or for 'flavour', background stories and the like. Such text can be formatted and linked to other Civilopedia entries, within limits.

An example of the format is:
```json
        "civilopediaText": [
			{"text":"Ancient ruins provide a one-time random bonus when explored"},
			{"separator":true},
			{"text":"This line is red and links to the Scout including icons", "link":"Unit/Scout", "color":"red"},
			{"text":"A big fat header sporting a golden star", "header":1, "starred":true, "color":"#ffeb7f"},
		],
```
List of attributes - note not all combinations are valid:
|attribute|type|description|
|---------|----|-----------|
|`text`|String|Text to display.|
|`link`|String|Create link and icon, format: Category/Name or _external_ link ('http://','https://','mailto:').|
|`icon`|String|Show icon without linking, format: Category/Name.|
|`extraImage`|String|Display an Image instead of text. Can be a path found in a texture atlas or or the name of a png or jpg in the ExtraImages folder.|
|`imageSize`|Float|Width in world units of the [extraImage], height is calculated preserving aspect ratio. Defaults to available width.|
|`header`|Integer|Header level. 1 means double text size and decreases from there.|
|`size`|Integer|Text size, default is 18. Use `size` or `header` but not both.|
|`indent`|Integer|Indent level. 0 means text will follow icons, 1 aligns to the right of all icons, each further step is 30 units.|
|`padding`|Float|Vertical padding between rows, defaults to 5 units.|
|`color`|String|Sets text color, accepts names or 6/3-digit web colors (e.g. #FFA040).|
|`separator`|Boolean|Renders a separator line instead of text. Can be combined only with `color` and `size` (line width, default 2).|
|`starred`|Boolean|Decorates text with a star icon - if set, it receives the `color` instead of the text.|
|`centered`|Boolean|Centers the line (and turns off automatic wrap).|

The lines from json will 'surround' the automatically generated lines such that the latter are inserted just above the first json line carrying a link, if any. If no json lines have links, they will be inserted between the automatic title and the automatic info. This method may, however, change in the future.

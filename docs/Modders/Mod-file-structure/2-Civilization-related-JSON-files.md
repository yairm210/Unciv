# Civilization-related JSON files

## Beliefs.json

[link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Beliefs.json)

This file contains the beliefs that can be chosen for religions in your mod.

Each belief has the following structure:

| Attribute       | Type            | Default  | Notes                                                                             |
|-----------------|-----------------|----------|-----------------------------------------------------------------------------------|
| name            | String          | Required |                                                                                   |
| type            | Enum            | Required | Type of belief. Value must be Pantheon, Founder, Follower or Enhancer             |
| uniques         | List of Strings | empty    | List of [unique abilities](../uniques.md) this belief adds to cities following it |
| civilopediaText | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)     |

## Buildings.json

[link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Buildings.json)

This file contains all the buildings and wonders you want to use in your mod.

Each building has the following structure:

| Attribute                                             | Type            | Default  | Notes                                                                                                                                                                                                                                            |
|-------------------------------------------------------|-----------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                                                  | String          | Required |                                                                                                                                                                                                                                                  |
| cost                                                  | Integer         | -1       | Amount of production required to build the building. If -1, the `buildingCost` from `requiredTech` [column](#column-structure) is used                                                                                                           |
| [`<stats>`](3-Map-related-JSON-files.md#general-stat) | Float           | 0        | Per-turn yield produced by the building                                                                                                                                                                                                          |
| maintenance                                           | Integer         | 0        | Maintenance cost of the building                                                                                                                                                                                                                 |
| isWonder                                              | Boolean         | false    | Whether this building is a global wonder                                                                                                                                                                                                         |
| isNationalWonder                                      | Boolean         | false    | Whether this building is a national wonder                                                                                                                                                                                                       |
| requiredBuilding                                      | String          | none     | A building that has to be built before this building can be built. Must be in [Buildings.json](#buildingsjson)                                                                                                                                   |
| requiredTech                                          | String          | none     | The tech that should be researched before this building may be built. Must be in [Techs.json](#techsjson)                                                                                                                                        |
| requiredResource                                      | String          | none     | The resource that is consumed when building this building. Must be in [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)                                                                                                        |
| requiredNearbyImprovedResources                       | List of Strings | empty    | The building can only be built if any of the resources in this list are within the borders of this city and have been improved. Each resource must be in [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)                     |
| replaces                                              | String          | none     | The name of a building that should be replaced by this building. Must be in [Buildings.json](#buildingsjson)                                                                                                                                     |
| uniqueTo                                              | String          | none     | If supplied, only the nation with this name can build this building. Must be in [Nations.json](#nationsjson)                                                                                                                                     |
| cityStrength                                          | Integer         | 0        | Strength bonus the city in which this building is built receives                                                                                                                                                                                 |
| cityHealth                                            | Integer         | 0        | Health bonus the city in which this building is built receives                                                                                                                                                                                   |
| hurryCostModifier                                     | Integer         | 0        | When this building is bought using gold or faith, the price is increased by this much percent                                                                                                                                                    |
| quote                                                 | String          | none     | If this building is a (national) wonder, this string will be shown on the completion popup                                                                                                                                                       |
| uniques                                               | List of Strings | empty    | List of [unique abilities](../../uniques) this building has                                                                                                                                                                                      |
| replacementTextForUniques                             | String          | none     | If provided, this string will be shown instead of all of the uniques                                                                                                                                                                             |
| percentStatBonus                                      | Object          | none     | Percentual bonus for stats provided by the building. Same format as [specialized stats](3-Map-related-JSON-files.md#specialized-stats) (numbers are in percent. i.e. `[30]` represents 30% __bonus__ to a stat)                                  |
| greatPersonPoints                                     | Object          | none     | Great person points by this building generated per turn. Valid keys are the names of units (Great Scientist, Warrior, etc.), valid values are Integers                                                                                           |
| specialistSlots                                       | Object          | none     | Specialist slots provided by this building. Valid keys are the names of specialists (as defined in [Specialists.json](3-Map-related-JSON-files.md#specialistsjson)), valid values are Integers, the amount of slots provided for this specialist |
| civilopediaText                                       | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                                                                                                                    |

## Nations.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Nations.json)

This file contains all the nations and city states, including Barbarians and Spectator.

Each nation has the following structure:

| Attribute            | Type                                                                | Default  | Notes                                                                                                                                           |
|----------------------|---------------------------------------------------------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| name                 | String                                                              | Required |                                                                                                                                                 |
| leaderName           | String                                                              | none     | Omit only for city states! If you want LeaderPortraits, the image file names must match exactly, including case                                 |
| style                | String                                                              | none     | Modifier appended to pixel unit image names                                                                                                     |
| cityStateType        | String                                                              | none     | Distinguishes major civilizations from city states (must be in [CityStateTypes.json](#citystatetypesjson))                                      |
| startBias            | List of strings                                                     | empty    | Zero or more of: [terrainFilter](../Unique-parameters.md#terrainfilter) or "Avoid [terrainFilter]". [^S]                                    |
| preferredVictoryType | String                                                              | Neutral  | The victory type major civilizations will pursue (need not be specified in [VictoryTypes.json](5-Miscellaneous-JSON-files.md#victorytypesjson)) |
| personality          | String                                                              | none     | The name of the personality specified in [Personalities.json](#personalitiesjson)                                                               |
| favoredReligion      | String                                                              | none     | The religion major civilization will choose if available when founding a religion. Must be in [Religions.json](#religionsjson)                  |
| startIntroPart1      | String                                                              | none     | Introductory blurb shown to Player on game start... [^V]                                                                                        |
| startIntroPart2      | String                                                              | none     | ... second paragraph. ___NO___ "TBD"!!! Leave empty to skip that alert.                                                                         |
| declaringWar         | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| attacked             | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| defeated             | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| denounced            | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                   |
| introduction         | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| neutralHello         | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| hateHello            | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| tradeRequest         | String                                                              | none     | Another greeting, voice hook supported [^V]                                                                                                     |
| innerColor           | [List of 3× Integer](5-Miscellaneous-JSON-files.md#rgb-colors-list) | black    | RGB color for outer ring of nation icon                                                                                                         |
| outerColor           | [List of 3× Integer](5-Miscellaneous-JSON-files.md#rgb-colors-list) | Required | RGB color for inner circle of nation icon                                                                                                       |
| uniqueName           | String                                                              | none     | Decorative name for the special characteristic of this nation                                                                                   |
| uniqueText           | String                                                              | none     | Replacement text for "uniques". If empty, uniques are listed individually                                                                       |
| uniques              | List                                                                | empty    | List of [unique abilities](../../uniques) this civilisation has                                                                                 |
| cities               | List                                                                | empty    | City names used sequentially for newly founded cities. Required for major civilizations and city states                                         |
| civilopediaText      | List                                                                | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                   |

[^S]: A "Coast" preference (_unless_ combined with "Avoid") is translated to a complex test for
coastal land tiles, tiles next to Lakes, river tiles or near-river tiles, and such civs are
processed first. Other startBias entries are ignored in that case.
Other positive (no "Avoid") startBias are processed next. Multiple positive preferences are treated
equally, but get no "fallback".
Single positive startBias can get a "fallback" region if there is no (or no more) region with that
primary type: any leftover region with as much of the specified terrain as possible will do.
Multiple "Avoid" entries are treated equally (and reduce chance for success - if no region is left
avoiding _all_ specified types that civ gets a random one).
When combining preferred terrain with "Avoid", the latter takes precedence, and preferred terrain
only has minor weight when choosing between regions that are not of a type to avoid.
These notes are __only__ valid when playing on generated maps, loaded maps from map editor get no "
regions" and startBias is processed differently (but you can expect single-entry startBias to work
best).
[^V]: See [Supply Leader Voices](../../Images-and-Audio.md#supply-leader-voices)

## Personalities.json

This file contains all Personalities for computer players.

Each personality has the following structure:

| Attribute                                                                                                                           | Type   | Default  | Notes                                                                                                                                           |
|-------------------------------------------------------------------------------------------------------------------------------------|--------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| name                                                                                                                                | String | Required |                                                                                                                                                 |
| preferredVictoryType                                                                                                                | String | Neutral  | The victory type major civilizations will pursue (need not be specified in [VictoryTypes.json](5-Miscellaneous-JSON-files.md#victorytypesjson)) |
| [`<stats>`](3-Map-related-JSON-files.md#general-stat), [`<behaviors>`](2-Civilization-related-JSON-files.md#personality-behaviours) | Float  | 5        | Amount of focus on the stat the computer player will have. Typically ranges from 0 (no focus) to 10 (double focus)                              |
| priorities                                                                                                                          | Object | none     | Priorities for each policy branch [^B]                                                                                                          |
| uniques                                                                                                                             | List   | empty    | List of [unique abilities](../../uniques) this personality has                                                                                  |
| civilopediaText                                                                                                                     | List   | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                   |

[^B]: Similar to [policy priorites](#branch-priorities) The "priorities" object defines the priority
major civilizations' AI give to a policy branch. The AI chooses the policy branch with the highest
number for their preferred victory type. If two or more candidate branches have the same priority,
the AI chooses a random branch among the candidates.

The object maps policy branches to priority values for the major civilization using the policy
branches name and integers. Any branches not listed have a default value of 0

The code below is an example of a valid "priorities" definition.

```
"priorities": {
    "Tradition": 30,
    "Liberty": 20,
    "Honor": 10
}
```

### Personality Behaviours

Personality Behaviours are not implemented yet and their names may change. Using them before they
are ready might make the mod unplayable.
[//]: # (There are 6 defining behaviours that influnce an AI Civilization's behaviour. A higher
value means they will behave more like the attribute.)


# (- Military: Determines how much does the civilization prioritizes building a military, but not necessarily using it. A higher value means more focus on military, a lower value means it is likely more peaceful.)

# (- Agressive: Determines how the civilization uses it's units while at war and which buildings they prioritise. A higher value means the civilization is more aggressive, a lower value means it is more defensive.)

# (- War: Determines how likely the civilization is to declare war. A 0 means the civ won't declare war at all)

# (- Commerce: Determines how open the civilization is to trade, value open borders, and liberate city-states. A higher value means more trading frequency even with civilizations they don't like.)

# (- Diplomacy: Determines how likely the civilization is to declare friendship, a defensive pact, peace treaty, or other diplomatic actions.)

# (- Loyal: Determines how much the civilization values a long-lasting alliance, how willing they are to join wars with them, and how much they despise other unreliable civilizations.)

# (- Expansion: Determines how focused the civilization is on founding or capturing new cities. A lower value means they might focus on culture more.)

## CityStateTypes.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/CityStateTypes.json)

This optional file is used for defining new types of city states. These types determine the benefits
major civilizations gets when they befriend or ally the city state with influence. If the file is
ommitted, the following are automatically added:
Cultured, Maritime, Mercantile, Militaristic, Religious.

Each city state type has the following structure:

| Attribute          | Type                                                                | Default         | Notes                                                                                                      |
|--------------------|---------------------------------------------------------------------|-----------------|------------------------------------------------------------------------------------------------------------|
| name               | String                                                              | Required        |                                                                                                            |
| friendBonusUniques | List of Strings                                                     | empty           | List of [unique abilities](../../uniques) granted to major civilizations when friends with this city state |
| allyBonusUniques   | List of Strings                                                     | empty           | List of [unique abilities](../../uniques) granted to  major civilizations when allied to city state        |
| color              | [List of 3× Integer](5-Miscellaneous-JSON-files.md#rgb-colors-list) | [255, 255, 255] | RGB color of text in civilopedia                                                                           |

## Policies.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Policies.json)

This file contains all the available social policies that can be "bought" with culture.

They are organized in 'branches', each branch has an 'opener', one or more 'member' policies, and
a 'finisher'. Therefore this file is organized using two levels - branch and member policy.

The properties of the 'opener' are defined with the branch level, while the 'finisher' is an entry
on the member level which _must_ be named as `branch name + " Complete"`, case sensitive. For
example, the finisher of a policy branch "Tradition" will have the name "Tradition Complete".

### Branch structure

Each policy branch has the following structure:

| Attribute  | Type   | Default  | Notes                                                                                                                                               |
|------------|--------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | String | Required |                                                                                                                                                     |
| era        | String | Required | Unlocking era as defined in [Eras.json](5-Miscellaneous-JSON-files.md#Eras.json)                                                                    |
| priorities | Object | none     | Priorities for each victory type, [see here](#branch-priorities)                                                                                    |
| uniques    | List   | empty    | List of [unique abilities](../../uniques) this policy branch grants upon adopting it                                                                |
| policies   | List   | empty    | List of [member policies](#member-policy-structure) and [branch 'finisher'](#branch-finisher-structure) - pay attention to the nesting of {} and [] |

#### Member policy structure

| Attribute | Type    | Default  | Notes                                                                                |
|-----------|---------|----------|--------------------------------------------------------------------------------------|
| name      | String  | Required |                                                                                      |
| row       | Integer | Required | Placement in UI, each unit approximately half the icon size                          |
| column    | Integer | Required | Placement in UI, each unit approximately half the icon size                          |
| requires  | List    | empty    | List of prerequisite policy names                                                    |
| uniques   | List    | empty    | List of [unique abilities](../../uniques) this policy member grants upon adopting it |

#### Branch finisher structure

| Attribute | Type   | Default  | Notes                                                                                                             |
|-----------|--------|----------|-------------------------------------------------------------------------------------------------------------------|
| name      | String | Required |                                                                                                                   |
| uniques   | List   | empty    | List of [unique abilities](../../uniques) this finisher grants upon adopting all the policy members in the branch |

### Branch priorities

The "priorities" object defines the priority major civilizations' AI give to a policy branch. The AI
chooses the policy branch with the highest sum of the peferred victory type listed here and the
number flisted in the personality's priority. If two or more candidate branches have the same
priority, the AI chooses a random branch among the candidates.

The object maps victory types to priority values for the major civilization using strings and
integers. If the preferred victory type is not specified, the default priority value is set to 0.

The code below is an example of a valid "priorities" definition.

```json
"priorities": {
"Neutral": 0,
"Cultural": 10,
"Diplomatic": 0,
"Domination": 0,
"Scientific": 10
}
```

## Quests.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Quests.json)

This file contains the quests that may be given to major civilizations by city states.

Each quest has the following structure:

| Attribute              | Type    | Default    | Notes                                                                                                                                                                                   |
|------------------------|---------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                   | String  | Required   | Defines criteria of quest, [see below](#quest-name)                                                                                                                                     |
| description            | String  | Required   | Description of the quest shown to players. Can add extra information based on `name`, [see below](#quest-name)                                                                          |
| type                   | Enum    | Individual | Individual or Global                                                                                                                                                                    |
| influence              | Float   | 40         | Influence reward gained on quest completion                                                                                                                                             |
| duration               | Integer | 0          | Maximum number of turns to complete the quest. If 0, there is no turn limit                                                                                                             |
| minimumCivs            | Integer | 1          | Minimum number of Civs needed to start the quest. It is meaningful only for type = Global                                                                                               |
| weightForCityStateType | Object  | none       | Relative weight multiplier to this quest for each [city state type](#citystatetypesjson) or city state personality (Friendly, Neutral, Hostile, Irrational), [see below](#quest-weight) |

### Quest name

The name of the quest defines the criteria for the quest. If they are not defined in the predefined
enum, they will have no behavior. In the description, square brackets `[]` in the description of the
quest is replaced with extra information (except for `Invest`). The list of predefined quest names
are as follows:

| Name                  | Criteria                                                                            | Additional info                                                                                  |
|-----------------------|-------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Route                 | Connect the city state to the major civilization's capital using roads or railways  |                                                                                                  |
| Clear Barbarian Camp  | Destroy the target barbarian camp                                                   |                                                                                                  |
| Construct Wonder      | Construct the target wonder                                                         | target `wonder`                                                                                  |
| Connect Resource      | Connect the target resource to the major civilization's trade network               | target `tileResource`                                                                            |
| Acquire Great Person  | Acquire the target great person                                                     | target `greatPerson`                                                                             |
| Conquer City State    | Defeat the target city state                                                        | target `cityState`                                                                               |
| Find Player           | Meet the target major civilization                                                  | target `civName`                                                                                 |
| Find Natural Wonder   | Find the target natural wonder                                                      | target  `naturalWonder`                                                                          |
| Give Gold             | Donate gold to the city state (amount does not matter)                              | `civName` "bully" for city state                                                                 |
| Pledge to Protect     | Pledge to protect city state                                                        | `civName` "bully" for city state                                                                 |
| Contest Culture       | Be the major civilization with the highest increase to culture during the duration  | major civilization's `cultureGrowth`                                                             |
| Contest Faith         | Be the major civilization with the highest increase to faith during the duration    | major civilization's `faithGrowth`                                                               |
| Contest Technology    | Be the major civilization with the most technologies researched during the duration | major civilization's `techsResearched`                                                           |
| Invest                | Donating gold yield extra Influence based on value provided                         | __IMPORTANT__: value in square brackets is the extra influence in percent. i.e. \[50\] means 50% |
| Bully City State      | Demand tribute from the target city state                                           | target `city state`                                                                              |
| Denounce Civilization | Denounce the major civilization which "bullied" the city state                      | `civName` "bully" for city state                                                                 |
| Spread Religion       | Spread major civilization's religion to the city state                              | major civilization's `religionName`                                                              |

### Quest weight

The "weightForCityStateType" object determines the quest's weight multiplier. When a city state
initiates a quest, the initial weight is 1, and it is multiplied by values based
on [city state type](#citystatetypesjson) and personality (Friendly, Neutral, Hostile, Irrational).
The AI then randomly selects a quest based on the final weighted values.

The object maps city state type and personality to the weight multipliers for the city state using
strings to floats. If the preferred victory type is not found, the default multiplier is 1.

The code below is an example of a valid "weightForCityStateType" definition. In this case, a
friendly militaristic city state will be 0.4 (0.2 × 2) times as likely to pick this quest than a
quest with weight 1.

```json
"weightForCityStateType": {
"Hostile": 2,
"Friendly": 0.2,
"Militaristic": 2
}
```

## Religions.json

[Link to original](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Religions.json)

This is just a list of Strings specifying all predefined religion names. Corresponding icons must
exist, that's all to it. After all, they're just containers for [beliefs](#beliefsjson).

## Specialists.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Specialists.json)

This file should contain a list of all possible specialists that citizens can be assigned to.

Each specialist has the following structure:

| Attribute                                             | Type                                                                | Default  | Notes                                                                                                                                                    |
|-------------------------------------------------------|---------------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                                                  | String                                                              | Required |                                                                                                                                                          |
| [`<stats>`](3-Map-related-JSON-files.md#general-stat) | Float                                                               | 0        | Per-turn yield produced by the specialist                                                                                                                |
| color                                                 | [List of 3× Integer](5-Miscellaneous-JSON-files.md#rgb-colors-list) | Required | Color of the image for this specialist                                                                                                                   |
| greatPersonPoints                                     | Object                                                              | none     | Great person points generated by this specialist per turn. Valid keys are the names of units (Great Scientist, Warrior, etc.), valid values are Integers |

## Techs.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Techs.json)

This file contains all the technologies that can be researched with science. It is organized into an
outer list of 'columns', which in turn contains one or more tech each.

### Column structure

Each tech column has the following structure:

| Attribute    | Type    | Default  | Notes                                                                                                                                     |
|--------------|---------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| columnNumber | Integer | Required | Horizontal placement in the Tech Tree                                                                                                     |
| era          | String  | Required | Determines era reached after researching any technologies in this column. Must be in [Eras.json](5-Miscellaneous-JSON-files.md#Eras.json) |
| techCost     | Integer | 0        | Default cost of the techs in this column                                                                                                  |
| buildingCost | Integer | Required | Default cost of buildings requiring this tech                                                                                             |
| wonderCost   | Integer | Required | Default cost of wonders requiring this tech                                                                                               |
| techs        | List    | Required | List of [techs](#tech-structure) - pay attention to the nesting of {} and []                                                              |

#### Tech structure

| Attribute       | Type            | Default                              | Notes                                                                                                     |
|-----------------|-----------------|--------------------------------------|-----------------------------------------------------------------------------------------------------------|
| name            | String          | Required                             |                                                                                                           |
| row             | Integer         | 0                                    | Vertical placement in the Tech Tree, must be unique per column                                            |
| cost            | Integer         | [Column techCost](#column-structure) | The amount of science required to research this tech                                                      |
| prerequisites   | List of Strings | empty                                | A list of the names of techs that are prerequisites of this tech. Only direct prerequisites are necessary |
| quote           | String          | none                                 | A nice story presented to the player when they research this tech                                         |
| uniques         | List of Strings | empty                                | List of [unique abilities](../../uniques) this technology grants                                          |
| civilopediaText | List            | empty                                | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#Civilopedia-text)                             |

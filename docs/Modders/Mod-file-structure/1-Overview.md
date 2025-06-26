#  Mod file structure Overview

These pages are a work in progress. Information they contain may be incomplete.

The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.

## Table of Contents

-   [General Overview of JSON files](#general-overview-of-json-files)
-   [Civilization-related JSON files](2-Civilization-related-JSON-files.md)
    -   [Beliefs.json](2-Civilization-related-JSON-files.md#beliefsjson)
    -   [Buildings.json](2-Civilization-related-JSON-files.md#buildingsjson)
    -   [CityStateTypes.json](2-Civilization-related-JSON-files.md#citystatetypesjson)
    -   [Nations.json](2-Civilization-related-JSON-files.md#nationsjson)
    -   [Policies.json](2-Civilization-related-JSON-files.md#policiesjson)
    -   [Quests.json](2-Civilization-related-JSON-files.md#questsjson)
    -   [Religions.json](2-Civilization-related-JSON-files.md#religionsjson)
    -   [Specialists.json](2-Civilization-related-JSON-files.md#specialistsjson)
    -   [Techs.json](2-Civilization-related-JSON-files.md#techsjson)
-   [Map-related JSON files](3-Map-related-JSON-files.md)
    -   [Terrains.json](3-Map-related-JSON-files.md#terrainsjson)
    -   [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)
    -   [TileImprovements.json](3-Map-related-JSON-files.md#tileimprovementsjson)
    -   [Ruins.json](3-Map-related-JSON-files.md#ruinsjson)
    -   [Tileset-specific json](3-Map-related-JSON-files.md#tileset-specific-json)
-   [Unit-related JSON files](4-Unit-related-JSON-files.md)
    -   [Units.json](4-Unit-related-JSON-files.md#unitsjson)
    -   [UnitPromotions.json](4-Unit-related-JSON-files.md#unitpromotionsjson)
    -   [UnitTypes.json](4-Unit-related-JSON-files.md#unittypesjson)
-   [Miscellaneous JSON files](5-Miscellaneous-JSON-files.md)
    - [Difficulties.json](5-Miscellaneous-JSON-files.md#difficultiesjson)
    - [Eras.json](5-Miscellaneous-JSON-files.md#erasjson)
    - [ModOptions.json](5-Miscellaneous-JSON-files.md#modoptionsjson)
    - [GlobalUniques.json](5-Miscellaneous-JSON-files.md#globaluniquesjson)
    - [Speeds.json](5-Miscellaneous-JSON-files.md#speedsjson)
    - [Tutorials.json](5-Miscellaneous-JSON-files.md#tutorialsjson)
    - [VictoryTypes.json](5-Miscellaneous-JSON-files.md#victorytypesjson)
-   [Stats](3-Map-related-JSON-files.md#stats)
-   [Sounds](../Images-and-Audio.md#sounds)
-   [Civilopedia text](5-Miscellaneous-JSON-files.md#civilopedia-text)

## General Overview of JSON files

Resources: [json.org](https://www.json.org/), [ISO standard](https://standards.iso.org/ittf/PubliclyAvailableStandards/c071616_ISO_IEC_21778_2017.zip)

Almost all Unciv JSON files start with a "[" and end with a "]". In between these are different objects of the type you are describing, each of which is contained between a "{" and a "}". For example, a very simple units.json may look like:

```
[
    {
        "name": "Warrior",
        "cost": 16
    },
    {
        "name": "Spearman",
        "cost": 24,
        "promotions": ["Shock I", "Drill I"]
    }
]
```

This file contains two unit objects, one for a warrior and one for a spearman. These objects have different attributes, in this case "name", "cost" and "promotions". All these attributes have a certain type, a String (text) for "name", an Integer for "cost" and a List of Strings for "promotions".

There are different types of attributes:

| type           | notes                                                                                                                                                                                                                                                                          |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| String         | A word or sentence. Should be between double quotes (")                                                                                                                                                                                                                        |
| Integer        | A number. Can be both positive or negative. Should **not** be between quotes                                                                                                                                                                                                   |
| Boolean        | A value that can either be 'true' or 'false'. Should **not** be between quotes                                                                                                                                                                                                 |
| List of [type] | If multiple values could apply (such as with the promotions above), they should be put inside a list. Each element of the list should be written like a normal attribute, separated by commas, and enclosed between square braces. E.g.: ["Shock I", "Shock II"] or [1, 2, 3]. |
| Object         | The most complicated type of attribute. An object is comprised of multiple attributes, each of which again has a type. These attributes have a key (the part before the ":") and a value (the part behind it). For an example, see below.                                      |

Example of a Buildings.json adding a new "Cultural Library" building which gives +50% science and +50% culture:

```
[
    {
        "name": "Cultural Library"
        "percentStatBonus" : {"science": 50, "culture": 50}
    }
]
```

The keys in this example are "science" and "culture", and both have the value "50".

In some sense you can see from these types that JSON files themselves are actually a list of objects, each describing a single building, unit or something else.

## Uniques

"Uniques" are a label used by Unciv for extensible and customizable effects. Nearly every "ruleset object" allows a set of them, as a List with the name "uniques".

Every Unique follows a general structure: `Unique type defining name [placeholder] more name [another placeholder] <condition or trigger> <condition or trigger>...`
The entire string, excluding all `<>`-delimited conditionals or triggers with their separating blanks, and excluding the placeholders but not their `[]` delimiters, are used to look up the Unique's implementation.
The content of the optional `[placeholder]`s are implementation-dependant, they are parameters modifying the effect, and described in [Unique parameters](../Unique-parameters.md).
All `<condition or trigger>`s are optional (but if they are used the spaces separating them are mandatory), and each in turn follows the Unique structure rules for the part between the `<>` angled brackets, including possible placeholders, but not nested conditionals.

Example: `"uniques":["[+1 Gold] <with a garrison>"]` on a building - does almost the same thing as the `"gold":1` attribute does, except it only applies when the city has a garrison. In this example, `[]` and `with a garrison` are the keys Unciv uses to look up two Uniques, an effect (of type `Stats`) and a condition (of type `ConditionalWhenGarrisoned`).

All Unique "types" that have an implementation in Unciv are automatically documented in [uniques](../uniques.md). Note that file is entirely machine-generated from source code structures. Also kindly note the separate sections for [conditionals](../uniques.md#conditional-uniques) and [trigger conditions](../uniques.md#triggercondition-uniques).
Uniques that do not correspond to any of those entries (verbatim including upper/lower case!) are called "untyped", will have no _direct_ effect, and may result in the "Ruleset Validator" showing warnings (see the Options Tab "Locate mod errors", it also runs when starting new games).
A legitimate use of "untyped" Uniques is their use as markers that can be recognized elsewhere in **filters** (example: "Aircraft" in the vanilla rulesets used as [Unit filter](../Unique-parameters.md#baseunitfilter)).
This use is recognized by the "Ruleset Validator" and not flagged as invalid - but a filtering Unique must also use _no placeholders or conditionals_ to pass the test.
If you get the "not found in Unciv's unique types" warning, but are sure you are using a correct filtering Unique, please look for exactly identical spelling in all places, including upper/lower case.
Note: Currently some mods use untyped Uniques not for filtering purposes, but as purely informational tool. The team will try to think of an approach for that use that won't trigger validation warnings without reducing validation quality, but as of now, those are unavoidable.

## Information on JSON files used in the game

Many parts of Unciv are moddable, and for each there is a separate json file. There is a json file for buildings, for units, for promotions units can have, for technologies, etc. The different new buildings or units you define can also have lots of different attributes, though not all are required. Below are tables documenting all the different attributes everything can have. Only the attributes which are noted to be 'required' must be provided. All others have a default value that will be used when it is omitted.

The individual files are described on [separate pages](#Table-of-Contents).

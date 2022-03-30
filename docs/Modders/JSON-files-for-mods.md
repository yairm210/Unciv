# JSON files for mods

These pages are a work in progress. Information they contain may be incomplete.

The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.

## Table of Contents

-   [General Overview of JSON files](#general-overview-of-json-files)
-   [Civilization-related JSON files](../Other/Civilization-related-JSON-files.md)
    -   [Beliefs.json](../Other/Civilization-related-JSON-files.md#beliefsjson)
    -   [Buildings.json](../Other/Civilization-related-JSON-files.md#buildingsjson)
    -   [Nations.json](../Other/Civilization-related-JSON-files.md#nationsjson)
    -   [Policies.json](../Other/Civilization-related-JSON-files.md#policiesjson)
    -   [Quests.json](../Other/Civilization-related-JSON-files.md#questsjson)
    -   [Religions.json](../Other/Civilization-related-JSON-files.md#religionsjson)
    -   [Specialists.json](../Other/Civilization-related-JSON-files.md#specialistsjson)
    -   [Techs.json](../Other/Civilization-related-JSON-files.md#techsjson)
-   [Map-related JSON files](../Other/Map-related-JSON-files.md)
    -   [Terrains.json](../Other/Map-related-JSON-files.md#terrainsjson)
    -   [TileResources.json](../Other/Map-related-JSON-files.md#tileresourcesjson)
    -   [TileImprovements.json](../Other/Map-related-JSON-files.md#tileimprovementsjson)
    -   [Ruins.json](../Other/Map-related-JSON-files.md#ruinsjson)
    -   [Tileset-specific json](../Other/Map-related-JSON-files.md#tileset-specific-json)
-   [Unit-related JSON files](../Other/Unit-related-JSON-files.md)
    -   [Units.json](../Other/Unit-related-JSON-files.md#unitsjson)
    -   [UnitPromotions.json](../Other/Unit-related-JSON-files.md#unitpromotionsjson)
    -   [UnitTypes.json](../Other/Unit-related-JSON-files.md#unittypesjson)
-   [Miscellaneous JSON files](../Other/Miscellaneous-JSON-files.md)
    -   [Difficulties.json](../Other/Miscellaneous-JSON-files.md#difficultiesjson)
    -   [Eras.json](../Other/Miscellaneous-JSON-files.md#erasjson)
    -   [ModOptions.json](../Other/Miscellaneous-JSON-files.md#modoptionsjson)
-   [Stats](../Other/Map-related-JSON-files.md#stats)
-   [Sounds](../Other/Unit-related-JSON-files.md#sounds)
-   [Civilopedia text](../Other/Miscellaneous-JSON-files.md#civilopedia-text)

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
| type | notes |
| --------- | ----- |
| String | A word or sentence. Should be between double quotes (") |
| Integer | A number. Can be both positive or negative. Should **not** be between quotes |
| Boolean | A value that can either be 'true' or 'false'. Should **not** be between quotes |
| List of [type] | If multiple values could apply (such as with the promotions above), they should be put inside a list. Each element of the list should be written like a normal attribute, seperated by comma's, and enclosed between square braces. E.g.: ["Shock I", "Shock II"] or [1, 2, 3]. |
| Object | The most complicated type of attribute. An object is comprised of multiple attributes, each of which again has a type. These attributes have a key (the part before the ":") and a value (the part behind it). For an example, see below. |

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

## Information on JSON files used in the game

Many parts of Unciv are moddable, and for each there is a seperate json file. There is a json file for buildings, for units, for promotions units can have, for technologies, etc. The different new buildings or units you define can also have lots of different attributes, though not all are required. Below are tables documenting all the different attributes everything can have. Only the attributes which are noted to be 'required' must be provided. All others have a default value that will be used when it is omitted.

The individual files are described on [separate pages](#Table-of-Contents).

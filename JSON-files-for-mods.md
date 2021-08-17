These pages are a work in progress. Information they contain may be incomplete.


The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.

# Table of Contents
* [General Overview of JSON files](#general-overview-of-json-files)
* [Civilization-related JSON files](../Civilization-related-JSON-files)
    * [Beliefs.json](Civilization-related-JSON-files#beliefsjson)
    * [Buildings.json](../Civilization-related-JSON-files#buildingsjson)
    * [Nations.json](../Civilization-related-JSON-files#nationsjson)
    * [Policies.json](../Civilization-related-JSON-files#policiesjson)
    * [Quests.json](../Civilization-related-JSON-files#questsjson)
    * [Religions.json](../Civilization-related-JSON-files#religionsjson)
    * [Specialists.json](../Civilization-related-JSON-files#specialistsjson)
    * [Techs.json](../Civilization-related-JSON-files#techsjson)
* [Map-related JSON files](../Map-related-JSON-files)
    * [Terrains.json](../Map-related-JSON-files#terrainsjson)
    * [TileResources.json](../Map-related-JSON-files#tileresourcesjson)
    * [TileImprovements.json](../Map-related-JSON-files#tileimprovementsjson)
    * [Ruins.json](../Map-related-JSON-files#ruinsjson)
    * [Tileset-specific json](../Map-related-JSON-files#tileset-specific-json)
* [Unit-related JSON files](../Unit-related-JSON-files)
    * [Units.json](../Unit-related-JSON-files#unitsjson)
    * [UnitPromotions.json](../Unit-related-JSON-files#unitpromotionsjson)
    * [UnitTypes.json](../Unit-related-JSON-files#unittypesjson)
* [Miscellaneous JSON files](../Miscellaneous-JSON-files)
    * [Difficulties.json](../Miscellaneous-JSON-files#difficultiesjson)
    * [Eras.json](../Miscellaneous-JSON-files#erasjson)
    * [ModOptions.json](../Miscellaneous-JSON-files#modoptionsjson)
* [Stats](../Map-related-JSON-files#stats)
* [Sounds](../Unit-related-JSON-files#sounds)
* [Civilopedia text](../Miscellaneous-JSON-files#civilopedia-text)


# General Overview of JSON files

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


# Information on JSON files used in the game

Many parts of Unciv are moddable, and for each there is a seperate json file. There is a json file for buildings, for units, for promotions units can have, for technologies, etc. The different new buildings or units you define can also have lots of different attributes, though not all are required. Below are tables documenting all the different attributes everything can have. Only the attributes which are noted to be 'required' must be provided. All others have a default value that will be used when it is omitted.

The individual files are described on separate pages:

* [Civilization-related JSON files](../Civilization-related-JSON-files)
* [Map-related JSON files](../Map-related-JSON-files)
* [Unit-related JSON files](../Unit-related-JSON-files)
* [Miscellaneous JSON files](../Miscellaneous-JSON-files)

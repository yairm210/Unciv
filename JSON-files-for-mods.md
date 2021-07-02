This page is a work in progress. Information it contains may be incomplete.


The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.


Almost all JSON files start with a "[" and end with a "]". In between these are different objects of the type you are describing, each of which is contained between a "{" and a "}". For example, a very simple techs.json may look like:
```
[
    {
        "name": "Agriculture",
        "cost": 50
    },
    {
        "name": "Mining",
        "cost": 100,
        "prerequisites": ["Agriculture"]
    }
]
```
This file contains two technology objects, one for Agriculture and one for Mining. These objects have different attributes, in this case "name" and "cost". All these attributes have a certain type, a string (text) for Agriculture, an integer for cost or a list for "prerequisites".


### techs.json

Technologies can have the following attributes:
- name: String - The name of the technology
- cost: Integer - The amount of science required to research this tech
- prerequisites: List of strings - A list of the names of techs that are prerequisites of this tech. Only direct prerequisites are necessary.



I'll work more on this later
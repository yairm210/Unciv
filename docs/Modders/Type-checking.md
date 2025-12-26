# Type Checking

Mistakes happen. Misnamed fields, things we forgot to add, or even stuff we didn't know existed. Computers can handle a lot of that themselves, so we can let them do the work to ensure that our json files are correct, by using *JSON Schemas*.

The JSON Schema files also enable autocomplete, warnings, and some intellisense directly within your editor.

## Android Studio

1. Double-click space, search "json schema mappings", enter
2. Click the small '+' (top, under 'language & frameworks' text)
3. Put the URL as `https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Buildings.schema.json`
4. Click the '+' under the 'Schema version' text, add 'File pattern', put pattern as `*/Buildings.json`

Android Studio will now recognize all Buildings.json files as belonging to that schema, and will warn you of inconsistencies.

## VSCode or Similar

There are two ways to enable the JSON schemas in VSCode: By either installing the [VSCode Unciv Extension](https://github.com/robloach/vscode-unciv), or adding them manually. It's easier to use the extension.

### Extension

The [VSCode Unciv Extension](https://github.com/robloach/vscode-unciv) enables the JSON Schemas, sets the correct file associations, along with a few other features.

1. Open VSCode
2. Press *CTRL+P* to open the command palette
3. Write the following, and press enter:
    ```
    ext install robloach.unciv
    ```
4. Congratulations, you now have the Unciv JSON Schema files installed, with autocomplete, intellisense, and file checking.

### Manually

If you'd rather not use the VSCode extension, it is possible enable the schemas manually...

1. Press *CTRL+SHIFT+P* to open up the command palette
2. Search "Open User Settings (JSON)", enter
3. Copy the following into your settings:
```json
"json.schemas": [
    {
        "fileMatch": ["jsons/Beliefs.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Beliefs.schema.json"
    },
    {
        "fileMatch": ["jsons/Buildings.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Buildings.schema.json"
    },
    {
        "fileMatch": ["jsons/CityStateTypes.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/CityStateTypes.schema.json"
    },
    {
        "fileMatch": ["jsons/Difficulties.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Difficulties.schema.json"
    },
    {
        "fileMatch": ["jsons/Eras.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Eras.schema.json"
    },
    {
        "fileMatch": ["jsons/Units.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Units.schema.json"
    },
    {
        "fileMatch": ["jsons/Nations.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Nations.schema.json"
    },
    {
        "fileMatch": ["jsons/Personalities.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Personalities.schema.json"
    },
    {
        "fileMatch": ["jsons/Policies.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Policies.schema.json"
    },
    {
        "fileMatch": ["jsons/Quests.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Quests.schema.json"
    },
    {
        "fileMatch": ["jsons/Religions.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Religions.schema.json"
    },
    {
        "fileMatch": ["jsons/Ruins.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Ruins.schema.json"
    },
    {
        "fileMatch": ["jsons/Specialists.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Specialists.schema.json"
    },
    {
        "fileMatch": ["jsons/Speeds.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Speeds.schema.json"
    },
    {
        "fileMatch": ["jsons/TileImprovements.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/TileImprovements.schema.json"
    },
    {
        "fileMatch": ["jsons/TileSets/*.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/TileSetConfig.schema.json"
    },
    {
        "fileMatch": ["jsons/Techs.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Techs.schema.json"
    },
    {
        "fileMatch": ["jsons/UnitTypes.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/UnitTypes.schema.json"
    },
    {
        "fileMatch": ["jsons/UnitPromotions.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/UnitPromotions.schema.json"
    },
    {
        "fileMatch": ["jsons/TileResources.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/TileResources.schema.json"
    },
    {
        "fileMatch": ["jsons/Events.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Events.schema.json"
    },
    {
        "fileMatch": ["jsons/GlobalUniques.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/GlobalUniques.schema.json"
    },
    {
        "fileMatch": ["jsons/ModOptions.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/ModOptions.schema.json"
    },
    {
        "fileMatch": ["jsons/Terrains.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Terrains.schema.json"
    },
    {
        "fileMatch": ["jsons/Tutorials.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Tutorials.schema.json"
    },
    {
        "fileMatch": ["jsons/UnitNameGroups.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/UnitNameGroups.schema.json"
    },
    {
        "fileMatch": ["jsons/VictoryTypes.json"],
        "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/VictoryTypes.schema.json"
    }
]
```

## Online Tool

If you don't use any of these editors, you can check your file online using [this tool](https://www.jsonschemavalidator.net/). However, it can't handle the missing commas that VSCode and Android Studio handle, so you may need to get your json up to spec to use it.

The schema you want to validate against is:
```json
{
	"$ref": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/Buildings.schema.json"
}
```

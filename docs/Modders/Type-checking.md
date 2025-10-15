# Type checking

Mistakes happen. Misnamed fields, things we forgot to add, or even stuff we didn't know existed.

Computers can handle a lot of that themselves, so we can let them do the work to ensure that our json files are correct, by using *json schemas*.

This also allows autocompletion when writing jsons!

As of now, only Buildings and Units have proper schema

## Using Android Studio


- Double-click space, search "json schema mappings", enter
- Click the small '+' (top, under 'language & frameworks' text)
- Put the URL as `https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/buildings.json`
- Click the '+' under the 'Schema version' text, add 'File pattern', put pattern as `*/Buildings.json`

Tada! Now Android Studio will recognize all Buildings.json files as belonging to that schema, and will warn you of inconsistencies!

## Using VSCode

- ctrl-shift-p, search "open user settings (json)", enter
- Copy this into the settings:
```json
    "json.schemas": [
        {
            "fileMatch": [
                "*/Buildings.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/buildings.json"
        },
        {
            "fileMatch": [
              "*/Units.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/units.json"
        },
        {
            "fileMatch": [
              "*/Nations.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/nations.json"
        },
        {
            "fileMatch": [
              "*/TileImprovements.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/tileImprovements.json"
        },
        {
            "fileMatch": [
              "*/Techs.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/techs.json"
        },
        {
            "fileMatch": [
              "*/UnitTypes.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/unitTypes.json"
        },
        {
            "fileMatch": [
              "*/UnitPromotions.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/unitPromotions.json"
        },
        {
            "fileMatch": [
              "*/TileResources.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/tileResources.json"
        },
        {
            "fileMatch": [
              "*/Events.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/events.json"
        },
        {
            "fileMatch": [
              "*/Terrains.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/terrains.json"
        },
        {
            "fileMatch": [
              "*/HistoricalFigures.json"
            ],
            "url": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/historicalFigures.json"
        }
    ]
```

## Using an online tool

If you don't use any of these tools, you can check your file online using [this tool](https://www.jsonschemavalidator.net/)

However, it can't handle the missing commas that vscode and Android Studio handle, so you may need to get your json up to spec to use it.

The schema you want to validate against is:
```json
{
	"$ref": "https://raw.githubusercontent.com/yairm210/Unciv/master/docs/Modders/schemas/buildings.json"
}
```

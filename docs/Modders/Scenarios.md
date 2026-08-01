# Scenarios

Scenarios are specific game states, set up so a player has a specific experience.

These can range from just having cities and units in specific places, to having full-blown custom rulesets to support them.

When creating a mod, we differentiate the *ruleset* from the *scenario* - the scenario is just a specific game state, or in other words - a saved game.


To create a scenario:

- Create a new game with some of the players you want, AND a spectator
- Enter the game as the spectator, and edit the save using the console
- Note there's commands like `civ add`, `civ remove`, `city add` or `unit action` to set up the players exactly as you want them
- Save the game, copy the game save file to a "scenarios" folder in your mod

To play a scenario:

- Get a mod with scenarios
- On the new game screen, choose "Map Type: Scenario" on the map options
- Choose the scenario from the next selectbox

When loading a scenario:

- The Spectator will be removed.
- All civilizations are set to AI-controlled.
- Humans will be assigned from the choices on the new game screen.
  At the moment, only the number of humans can be chosen, the locked "Random" nation choice will be respected, and your player will be assigned a random scenario civilization.
  Contact us if you need the ability to determine which nations can be played.
- No civilizations will be added or removed (Numbers selected on the new game screen will be ignored).
- It will now be the turn of the civilization active when the scenario was saved (except it's probably the Spectator, which will be skipped).

## Console

### Opening

To open the console from the world screen, click the `` ` `` button on your keyboard. It's the one top left, just below `Esc`, independent of international layout.

On mobile:

- Long-press the hamburger menu (3 lines, top left of the world screen)
- Tap the "developer console" button

To see available commands, click enter. This works for subcommands as well (e.g. when you entered `tile`).

### Command formatting

Object names (units buildings civs etc.) are case-insensitive.

Unit and building names with spaces in them, like "Great General", can be inputted in 2 ways:

- **"great general"** - with quotation marks around them
- **great-general**  - with dashes instead of spaces

### Autocomplete

The console has autocompletion - for commands and arguments with Tab. On Android, use the button to the left of the input field.

* When two or more options match, the possibilities are displayed, and the longest common substring is entered for you (e.g. you enter 'c', Tab: commands 'city' or 'civ' match, the console partially completes 'ci' for you).
* When you haven't yet entered a partial text (console input is empty or ends in a space), autocompletion will display all options.
* A partial text beginning with a `"` quotation mark will use quoted format for auto-complete, and use the capitalization as defined in the ruleset.

### Other

Some commands operate on a tile or unit you need to select on the map. You can also do so while the console is open.

The console intentionally does not follow all rules defined by the ruleset - e.g. it allows Farms on hills without fresh water or the Mobility promotion on a Worker.
Any unexpected consequences are your responsibility.

You can access command history using the arrow keys, the `history` command, or on Android using the up/down buttons to the right of the input field.
Note the entries that `history` displays are clickable.

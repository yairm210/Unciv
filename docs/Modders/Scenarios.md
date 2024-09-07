# Scenarios

Scenarios are specific game states, set up so a player has a specific experience.

These can range from just having cities and units in specific places, to having full-blown custom rulesets to support them.

When creating a mod, we differentiate the *ruleset* from the *scenario* - the scenario is just a specific game state, or in other words - a saved game.


To create a scenario:

- Create a new game with the players you want, AND a spectator
- Enter the game as the spectator, and edit the save using the console
- Save the game, copy the game save file to a "scenarios" folder in your mod

## Console

To open the console from the world screen, click the `` button on your keyboard.

On mobile:

- Long-click menu hamburger (3 lines)
- Click "developer console" button

To see available commands, click enter. This works for subcommands as well (e.g. when you entered `tile`).

Object names (units buildings civs etc) are case-insensitive.

Unit and building names with spaces in them, like "Great General", can be inputted in 2 ways:

- **"great general"** - with quotation marks around them
- **great-general**  - with dashes instead of spaces

The console has autocompletion:

* Enter a partial command, subcommand or argument and hit Tab...
* When the entered part, compared from the start, matches exactly one of the possible options, that option is completed and a space added for the next subcommand or parameter.
* When no option matches, nothing happens.
* When two or more options match, the possibilities are displayed, and the longest common substring is entered for you (e.g. you enter 'c', Tab: commands 'city' or 'civ' match, the console partially completes 'ci' for you).
* When you haven't yet entered a partial text (console input is empty or ends in a space), autocompletion will display all options.

Some commands operate on a tile or unit you need to select on the map before opening the console.

The console does intentionally not follow all rules defined by the ruleset - e.g. it allows Farms on hills without fresh water or the Mobility promotion on a Worker. Any unexpected consequences are your responsibility.

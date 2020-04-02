### Overview
The Music Controller will generally play one track after another, with a pause (currently fixed at five seconds) between. When the main menu is opened playback will pause and can resume when it is closed. The load game, new game or map editor screens should stay silent (except the map editor when we place a nation starting location).

There are various 'triggers' in the game code initiating a choice for a new track. The new track will, if necessary, fade out the currently playing track quickly before it starts playing. Track choice involves context provided by the trigger and a random factor, and an attempt is made to not repeat any track until at least five others have played. 

Mods can provide their own music folder, and if they are active its contents will be treated exactly the same as those in the main music folder. Mods should control usage of their tracks by careful choice of file name. Mod developers can watch console output for messages logging track choice with trigger parameters or loading errors.

One track is special: The Thatched Villagers (see also credits.md). The game is able to download it if the music folder is empty, and it is played when the music volume slider is used. It is also a fallback track should certain problems occur (a broken file, however, will shut down the player until another trigger happens).

### List of Triggers
Triggers indicate context (call it intent, mood, whatever, it doesn't matter) by optionally providing a prefix and/or suffix to match against the file name. There are a few flags as well influencing choice or behaviour - one flag function is to make prefix or suffix mandatory, meaning if no available file matches the track chooser will do nothing. Otherwise, a next track will always be chosen from the available list by sorting and then picking the first entry. Sorting is done by in order of precedence: Prefix match, Suffix match, Recently played, and a random number. Therefore, as currently no triggers have an empty prefix, files matching none of the prefixes will never play unless there are less than five files matching the requested prefix.

The current list of triggers is as follows:

| Description | Prefix | [M] | Suffix | [M] | Flags |
| ----------- |:------ |:---:| ----:|:---:|:---:|
| Automatic next-track, game launch | Ambient | | | | |
| Every 5th turn | (civ name) | M | Peace or War[1] | | [F] |
| New game: Select a mod | (mod name) | M | Theme | | [S] |
| New game: Pick a nation for a player | (nation name) | M | Theme | | [S] |
| Diplomacy: Select player | (nation name) | M | Peace or War[2] | | [S] |
| First contact[3] | (civ name) | M | Peace | M | |
| War declaration | (civ name) | M | War | M | |
| Civ defeated | (civ name) | M | Defeat | M | |
| Golden Age | (civ name) | M | Golden | M | |
| Wonder built | (wonder name) | M | Built | M | |
| Tech researched | (tech name) | M | Researched | M | |
| Map editor: Select nation start location | (nation name) | M | Theme | | [S] |
| Options: Volume slider | | | | | [D] |

Legend:
[M]: Must Match. If no matching file is found, the trigger will do nothing.
[S]: Stop after playback. No automatic next choice.
[F]: Slow fadeout of replaced track.
[D]: Always plays the default file.
[1]: Whether the active player is at war with anybody
[2]: According to your relation to the picked player
[3]: Including Citystates
# Audiovisual Mods
## (**Work in progress**)

- Mods can override built-in sounds
- Mods can supply additional music tracks
- Mods can override built-in graphics
- Mods can supply additional tilesets


### Adding Wonder Splash Screens

You can add wonder images to mods and they'll be displayed instead of the standard icon when a wonder is finished. The image needs to be a .png and 2:1 ratio so for example 200x100 px.

Add the images to `/Images/WonderImages/`. They need to be named according to the name field in `Buildings.json`, so for example "Temple of Artemis.png" or "Stonehenge.png"

Remember, to be compatible with mobile devices, a fresh atlas needs to be generated including these.

### Adding Leader Portraits

The base game comes without Leader Portraits, but is able to display them in greetings, Civilopedia, diplomacy screens, or the nation picker. A mod can supply these, by adding their images to `/Images/LeaderIcons/`. The file name must correspond exactly with the leader name of a nation as defined in Nations.json, or they will be ignored.

These work best if they are square, between 100x100 and 256x256 pixels, and include some transparent border within that area.

For example, [here](https://github.com/yairm210/Unciv-leader-portrait-mod-example) is mod showing how to add leader portraits, which can complement the base game.
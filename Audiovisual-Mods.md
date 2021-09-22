# Audiovisual Mods
- [The 'Permanent audiovisual mod' feature](#permanent-audiovisual-mod)
- [Mods can override built-in graphics](#override-built-in-graphics)
- [Mods can supply additional tilesets - see separate page](./Creating-a-custom-tileset)
- [Mods can supply additional graphics not included in the base game](#supply-additional-graphics)
- [Mods can override built-in sounds](#override-built-in-sounds)
- [Mods can supply additional music tracks](#supply-additional-music)

## Permanent audiovisual mods
The following chapters describe possibilities that will work while a mod is ***active***. It is either selected for the current game (during new game creation, cannot be changed after that for saved games), meaning all its rules and resources will be used. _Or_ it is marked as 'Permanent audiovisual mod' in the mod manager (you must select it in the 'installed' column to get the checkbox). In that case only graphics and audio will be active, the rule changes will be ignored (if it contains any) unless the first way is _also_ used.


## Override built-in graphics
If a mod supplies an image with the same name and path as one included in the base game (and its [atlas](./Mods#more-on-images-and-the-texture-atlas) is up to date), and the mod is active, the mod's graphics will be used instead of the built-in one.

For example, if you include a file named "Images/OtherIcons/Link.png" in your mod, you will be overriding the little chain links icon denoting linked lines in Civilopedia.

Please note, as for adding items, your graphics should keep the size and color choices of the original, or the result may be surprising, e.g. when the game tries to tint such an image.


## Supply additional graphics
Currently there are two kinds where the game has display capability but does not supply graphics itself, as described in the next paragraphs:

### Adding Wonder Splash Screens
You can add wonder images to mods and they'll be displayed instead of the standard icon when a wonder is finished. The image needs to be a .png and 2:1 ratio so for example 200x100 px.

Add the images to `/Images/WonderImages/`. They need to be named according to the name field in `Buildings.json`, so for example "Temple of Artemis.png" or "Stonehenge.png"

Remember, to be compatible with mobile devices, a fresh atlas needs to be generated including these.

### Adding Leader Portraits
The base game comes without Leader Portraits, but is able to display them in greetings, Civilopedia, diplomacy screens, or the nation picker. A mod can supply these, by adding their images to `/Images/LeaderIcons/`. The file name must correspond exactly with the leader name of a nation as defined in Nations.json, or they will be ignored.

These work best if they are square, between 100x100 and 256x256 pixels, and include some transparent border within that area.

For example, [here](https://github.com/yairm210/Unciv-leader-portrait-mod-example) is mod showing how to add leader portraits, which can complement the base game.


## Override built-in sounds
This works like graphics, except no atlas is involved. E.g. you include a sounds/Click.mp3, it will play instead of the normal click sound. These files must stay short and small. A sound larger than 1MB when uncompressed may break or not play at all on mobile devices. Unciv tries to standardize on 24kHz sample rate, joint stereo, low-bitrate VBR (-128kbps) mp3. Only mp3 and ogg formats will be recognized (but an existing mp3 can be overridden with an ogg file).


## Supply additional music
Sound files (mp3 or ogg) in a mod /music folder will be recognized and used when the mod is active. Tracks will play randomly from all available tracks (with a little bias to avoid close repetition of tracks). There is no overriding - a "thatched-villagers.mp3" in a mod will play in addition to and with the same likelihood as the file that the base game offers to download for you. There is no hard technical limit on bitrate or length, but large bandwidth requirements may lead to stuttering (The end of a "next turn", right before the world map is updated, and with very large maps, is the most likely to cause this).

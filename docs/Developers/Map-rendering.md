# Map rendering

## Introduction - how does LibGDX render images?

Images in LibGDX are displayed on screen by a SpriteBatch, which uses GL to bind textures to load them in-memory, and can then very quickly display them on-screen.
The actual rendering is then very fast, but the binding process is slow.
Therefore, ideally we'd want as little bindings as possible, so the textures should contain as many images as possible.
This is why we compile images (ImagePacker.packImages()) into large PNGs.

However, due to limitations in different chipsets etc, these images are limited to a maximum size of 2048*2048 pixels, and the game contains more images than would fit into a single square of that size.
What we do, then, is separate them by category, and thus rendering proximity.
The 'android' folder contains Images, but also various sub-categories - Images.Flags, Images.Tech, etc.
Each of these sub-categories are compiled into a separate PNG file in the 'android/assets' folder.

When rendering, the major time-sink is in rebinding textures. We therefore need to be careful to minimize the number of -rebinds, or 'swapping between different categories'.

## Layering

Each map tile is comprised of several layers, and each layer needs to be rendered for all tiles before the next layer is.
For example, we don't want one tile's unit sprite to be overlayed by another's improvement.
This layering is done in TileGroupMap, where we take the individual parts for all tiles, separate them into the layers, and add them all to one big group.
This also has a performance advantage, since e.g. text and construction images in the various city buttons are not rendered until the very end, and therefore swap per the number of of cities and not for every single tile.
This also means that mods which add their own tilesets or unit sprites have better performance than 'render entire tile; would provide, since we first render all terrains, then all improvements, etc,
so if my tileset provides all terrains, it won't be swapped out until we're done.

## Debugging

Android Studio's built-in profiler has a CPU profiler which is perfect for this.
Boot up the game on your Android device, open a game, start recording CPU, move the screen around a bit, and stop recording.
Select the "GL Thread" from the list of threads, and change visualization to a flame graph. You'll then see what's actually taking rendering time.

You can find various games to test on [here](https://github.com/yairm210/Unciv/issues?q=label%3A%22Contains+Saved+Game%22) - [This](https://github.com/yairm210/Unciv/issues/4840) for example is a crowded one.

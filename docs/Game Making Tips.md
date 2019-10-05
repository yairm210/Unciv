# Tips and tricks for making a LibGDX game

Here are a bunch of things I've learned from by brief excursion into the world of game making.

Some of our will be obvious to you, some will not.

## Use Kotlin

Unciv started its life as a Unity project in C#, was shifted to Java and LibGDX, and finally to Kotlin.

I regret every minute that I spent writing events in Java, this is probably the most significant change that your application could see.

## Use Scene2d

Unless you plan on creating images on the fly, you'll probably be using prerendered assets.

Placing them manually of akin to manually positioning html tags, instead of using html heirarchy and css to guide positions.

## Ignore Horizontal and Vertical groups - use Table

I personally found that table has all the functionality of the above, and more. Each class has a different syntax too, so I found it much simpler to just stick with Table for everything.

## If your game is getting slow, use the Android profiler in Android Studio

The top-down CPU chart is the best code profiler I've ever seen, use it to your advantage!

### Cache everything

caching is a trade-off between purer, state-agnostic code and higher performance.
Coming from a PC background, I automatically assume that anything less than O(n^2) is less than a milisecond and therefore, not a cachinhg candidate.
This is not so in mobile development.


 This becomes especially relevant when you need to save and load game data which has lots of connected parts - you have to avoid circular references, and you want to minimise the save size, but you need to reconstruct the missing links when loading.

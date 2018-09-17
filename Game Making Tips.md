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

## If you're game is getting slow, use the Android profiler in Android Studio

The top-down CPU chart is the best code profiler I've ever seen, use it to your advantage!

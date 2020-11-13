# Tips and tricks for making a LibGDX game

Here are a bunch of things I've learned from by brief excursion into the world of game making.

Some of our will be obvious to you, some will not.

## Use Kotlin

Unciv started its life as a Unity project in C#, was shifted to Java and LibGDX, and finally to Kotlin.

I regret every minute that I spent writing events in Java, this is probably the most significant change that your application could see.

## Use Scene2d

Unless you plan on creating images on the fly, you'll probably be using prerendered assets.

Placing them manually is akin to manually positioning html tags, instead of using html heirarchy and css to guide positions.

So too is Scene2d - as a placement framework. it's relatively simple to understand, especially when you...

## Ignore Horizontal and Vertical groups - use Table

I personally found that table has all the functionality of the above, and more.

Each class has a different syntax too, so I found it much simpler to just stick with Table for everything.

Table does just about EVERYTHING! It's insanely amazing!

## If your game is getting slow, use the Android profiler in Android Studio

The top-down CPU chart is the best code profiler I've ever seen, use it to your advantage!

### Cache everything

Caching is a trade-off between purer, state-agnostic code and higher performance.
Coming from a PC background, I automatically assume that anything less than O(n^2) is less than a milisecond and therefore, not a cachinhg candidate.
This is not so in mobile development.


This becomes especially relevant when you need to save and load game data which has lots of connected parts - you have to avoid circular references, and you want to minimise the save size, but you need to reconstruct the missing links when loading.

### Minimize String operations


All the tip and tricks you've heard to minimize String operations? Use them!

String constants should be consts, use StringBuilders (or just ArrayLists of strings that you later .joinToString())

### Sequences everywhere!

One thing I did not expect to be such an issue is intermediate lists when sorting and mapping.

But appparently, the memory allocation for these tasks is Serious Business.

So whenever possible, take your list and .asSequence() it before actiating list operations - this results in huge savings of both time and memory!

The only time you shouldn't be doing this, though, is when you want to cache the specific values for future use -
 sequences will go through the whole process every time you iterate on them, so just .toList() them when you've gotten the final results!
 
# General tips for making an Open Source game

## Lower the entry bar - for both programmers and players

I think that most Open Source games suffer from this problem - those that are in are way in, but those that are out and want to join have to learn the ecosystem.

Documentation is a big issue here, but so are detailed instructions - and I mean "Spoonfeeding".

Treat new developers as if they've never used Git before - it's possible they haven't!

Explain how to dowload the sourecode, the tools, how to get the game running locally, how to make changes and how to submit them.

Same think with new players - getting the game up and running should be AS SIMPLE AS HUMANLY POSSIBLE - you want people to play your game, don't you?

This includes:

- Source-To-Executable automation - I use Travis
- Play stores and the like
- Internal game tutorials - your players will NEVER BE SATISFIED with this last point, but at least do what you candidate

## Community, Community, Community!

I, personally, underestimated this point for about a year after launch.

I communicated with players through the Google Play Store and Github issues, and that seemed to be enough.

It was only after repeated urgings from players that I opened a Discord server - and that gradually lead to a massive change!

You see, it's not ABOUT programmer-to-player interaction. There will always be a small number of core devs relative to the large playerbase.

The key to the community is the player-to-player interaction. Explaining things, questions, ideas, things that players bounc off each other,
not only make the amorphous community a better pllace, but actually lead to a better game!

Another think to remember is that there's a larger community around you - the Open Source community, the Linux community, etc.

There are lots of people who will play your game only because it's open source, and it also means they don't have as many options.

For example... 

- Being the best 4X game means competing with the biggest names out there
- Being the best 4X game for Linux means many less competitors, but All The Cool Kids (tm) are multiplatforming nowadays so you're still outperformed.
- Being the best Open Source 4X game means about 5 competitors, and no money is involved either so the average entry is not as polished.
- Being the best Open Source 4X game for Android... means having so few competitors that it's totally doable.

## Everything is marketing.

Your game's name, the icon, screenshots, everythig a player sees about your game is marketing.

Icons and bylines are especially important, since they're the first things your players will probably see.

I saw an almost 50% (!) by changing the icon, after seveeral experiments, which Google Play lets you conduct very easily.

## Translations are part of your source code

This may be slightly contraversial, so I'll explain.

We went though a number of iterations regarding how to save translations until we arrived at the current format.

The important parts are:

- Game translation files should be AUTO GENERATED. This allows you to add new objects into the game with impunity,
 knowing that corresponding lines will be auto-added to the translations.
 
- Translations for each language should be stored separately - this allows concurrent modification of several independant languages with no risk of conflict

- Translations should be PR'd in! This allows other speakers to question or change the proposed translations, and allows you to run tests on your translations.
If you require a specific format, this is invaluable as it means that bad translations will be rejected at the door.

## Open source problems require open (source?) solutions

TL;DR, consider using APIs that are free, even if they're not Open Source.

Multiplayer requires syncing game files beween clients, even when one of them is not currently online.

The 'correct' way to solve this would probably be to have an online DB and a service which handles user requests.

Since this is an Open Source game, I'm working on a 0$ budget, so we just store all the files in Dropbox and upload/download there.

Is this secure? No, but does it need to be? You need to think of the cost vs the value.

Same thing with Mods. Steam is big and secure so it handles its mods itself.

We are small and open, so we just allow to download from Github, which lets us use all of Github's built in functions (user management, readmes, stars, versioning...) at no extra cost.

And unlike the Dropbox usage, which is basically abuse, Github is built for thiss kind of thing!
This is exactly the kind of use case they were thinking of to start with!


# The Reckoning

There comes a time in every project where the cool stuff is done. All the cutting-edge awesomeness and algorithmic playdough is done, and now all (hah) it needs is polish.

You know who loves polish? Players! Sure, there are some that say "a good game is good even if it's basic" but they have *standards* for what a basic game should have as well.

And the numbers don't lie. Polished games sell themselves better, and so are played more.

You know who **doesn't** love polish? DEVELOPERS.

When your game is relatively simple, then the options for polish are more limited, but the more complex the game, the more polish-venues there are.

And it can be an ABSOLUTE GRIND. Another weird use-case, another ingame option, "better performance" (I must have spent dozens of hours on different performance related actions)

And the worst thing is, that everyone notices when it's missing, but no one notices when it's there. A hundred versions of polish - literally - and the average player may notice only a slight change.

And then comes the moment when you ask yourself, why bother? What are we even doing here?

For me, the answers are as follows:

A. To build something truly great, you have to keep going way beyond when it stops being fun.
B. There's a community of people that like what you're doing and want there to be more of it :)
C. You know you want to keep coding, and what, you think you're going to start another project and it'll work out as well? You've tried that multiple times, and let's face it the chance of you making a second game that goes so well is really small unless you invest in it as much time as you have in this, and yeah, then you'll be back in this position again.

And that's basically the loop I've been in for the last hundred versions or so! Solve bugs, fix edge cases, improve AI, accept PRs. Lots of mod-related changes, both to stop the game breaking when people do things in mods that they shouldn't and to allow them more freedom in making them.

I don't think I'll ever really continue to finish G&K, I'm DEFINITELY not planning on implementing BNW mechanics which frankly I think are...not great.

That's where I am right now. Kind of done with the game, but considering that I thought that half a year ago and releases are still releasing roughly every week, also kind of not.

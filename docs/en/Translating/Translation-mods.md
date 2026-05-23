# Translation generation - for modders

## Generating "needs translation" entries
If you can run desktop with the mod installed, then provide at least one valid translation of something that is present in your mod or the base game in that file. The file can be empty otherwise. Do this for each language you want to support.
Now run Unciv and use options-advanced-"Generate translation files". Reload your translation file and it will have added all the necessary "requires translation" entries specific to your mod
(I repeat, works only if there's at least one valid entry already there).
You can also override base game translations, but those won't be output by the "Generate translation files" tool.

Here's an example:
Say you have a new nation in your mod named "The Borg". You create the translations folder, create an empty file named, say, "Hungarian.properties", add "The Borg = The Borg" to that, run Unciv and run the translation generator from options. Reload the new file, bingo all what Unciv would like to see is there.

If you're modding on Android only - don't. That said, it's not impossible, just make do without the described tool and add everything yourself, test, rinse, repeat. Be aware that the game does not read changed files from disk if it doesn't need to, so on Droid you could either edit locally and force-stop to ensure changes are read, or edit on a github repo and re-install from there, or...

Adding new languages in a mod is not supported (because the completionPercentages.properties file determines which languages Unciv deems as known, and I'm not saying impossible as one could manipulate their GameSettings.json).

Remember, exact case is important both in translations left of the "=" and file names.

## More about translating
Sometimes you'll see a `English.properties` in the translation folder. For example, if you see `gold = credits` in `English.properties`, It means the word 'gold' will be displayed as 'credits' in the English version.
So in your translation file, though 'gold' is already translated in vanilla unciv, you should still translate the line.
```
gold = credits ( <- in your language)
NOT:gold = gold ( <- in your language)
```
(The example comes from the mod [Alpha-Frontier](https://github.com/carriontrooper/Alpha-Frontier). Thanks @carriontrooper.)

Most Base Ruleset mods contain this feature, so you'd better be careful translating those, or you'll make the translation work really 'amuzing':D

Another thing about translation is 'extra translating'. The aim of 'extra translating' is to make your mod translation closer to the gaming content and give the players a better gaming experience.
A great example is from @SpacedOutChicken's mod Deciv. @The Bucketeer made some 'extra translations' which are excellent.[(link is here)](https://github.com/SpacedOutChicken/DeCiv-Redux/blob/main/jsons/translations/Traditional_Chinese.properties)I've got a few lines here so you can take it as a reference.
```
Your warmongering ways are unacceptable to us. = 即使在野蠻的荒土世界，窮兵黷武的行徑還是無法容忍的！
(English meaning: These warmongering ways are still unaccepable enen in this world of savage)
```

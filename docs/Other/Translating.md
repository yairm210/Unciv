# Translating

## Starting out

The translation files are at [/android/assets/jsons/translations](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/translations)

If you're adding a new language, you'll need to create a new file ('Create a new file' to the right of the folder name in the UI), and copy into it the contents of template.properties

If you're adding stuff to an existing language, simply start editing the file!

You don't need to download anything, all translation work can be done on the Github website :)

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

## App store text

There are two special entries that won't show in the game but are automatically used to provide short and long descriptions for F-Droid (and possibly other stores soon). They're near the beginning of each language file and marked "Fastlane". See the comments just above each for help, and where to find the actual english original to translate. Do not overlook the note on line breaks in [Other notes](#Other_notes) for the full description!

## App store images

The stores can show screenshots. To show translated versions of these images a different approach is necessary: they must be merged into appropriate subfolders of [fastlane/metadata/android] in the Unciv repository. If in doubt on how to do this, look at the existing ones for the proper dimensions and offer your version using an issue. Hints: relative paths and names must match the 'en-US' subfolder with 'en-US' replaced with the _two-letter_ ISO code of your language. You can use Add file - Upload files if the folder you need already exits. Using the github site to create a PR with new folders is possible, but outside the scope of this document.

## Pitfalls

-   If a translation template (the stuff to the left of "` = `") contains square brackets, you will have to include each of them _verbatim_ in your translation, but you can move them. Upper/lower case is relevant! e.g. `All [personFilter] are cool` can be translated as `Tous les [personFilter] sont cool`, but ***not*** as `Tous les [personnages] sont cool`, and neither as `Nous sommes vraiment cool`. Failing this is the main cause of your PR's showing up with red "x"es and "checks failed".
-   Blanks: Watch out for blanks at the start of a line or two of them before the equals sign. If you got such a line - those blanks are part of the translation key and must not be deleted on the left side, and you should probably also include them in your translation (unless your language doesn't need spaces to separate things).
-   Changes in the templates: When we find a typo in the english texts and fix it, or marginally change a wording, the template changes. Often the old template will not be automatically fixed in the existing translations, because it's a lot of work _and_ in most cases the developers cannot be sure the translation is still correct. For you, that might look like your translations are simply disappearing with an update. In such a case, you have the option to use github's history to look up old versions, copy the old translation, place it where the new template now says "requires translation" - and proofread and adapt it to the new english version. The history link for each file is in the top right area and has a nice round clock icon.

## Wait, what just happened?

Like most open-source projects, Unciv is developed at Github, so if you don't have a user you'll first have to create one. The way Github works is the following:

1. You create a 'fork' repo, i.e. copy, of Unciv that belongs to your user (myUser/Unciv)
2. You make changes to your copy. These changes are called a 'commit'.
3. You make a pull request, which is basically asking for the changes you made on myUser/Unciv to be merged into the main repo (yairm210/Unciv)

When you ask to 'edit' a file in yairm210/Unciv, these stages happen _automatically_ - but it's important to understand what's happening behind the scenes do you understand where the changes actually are!

## Why not use a crowdsourcing translation website like <...>?

1. Testing. Currently, translations undergo a number of tests for verification. This allows some language changes to be accepted and others not, and it's all in the same platform with the same tests. External translation tools don't allow for this.
2. History and revisions. This is what Git was made for, and nothing like it exists in the world. I'm not exaggerating.
3. Release cycle. We release versions weekly. If we need to take information from an external website every time, and for many that I've checked - you need to download the info as a csv or something and convert it. Every extra step hurts.
4. Discussions. Most crowdsourcing translation websites don't allow for discussions and corrections on translations. Github does.
5. Mass changes. If we're changing the source of the translation but want to keep the various destinations (say, we change "Gold from trade routes +[amount]%" to "+[amount]% Gold from trade routes"), if all the translation files are in Git we can do that in 1 minute. If it's external, this varies greatly.

## Other notes

Make sure that you make the changes in the 'master' branch in your repo!

Each untranslated phrase will have a "requires translation" line before it, so you can quickly find them. You don't need to remove them yourself if you don't want to - they will be automatically removed the next time we rebuild the file.

Order of lines does not matter, they will be rearranged automatically each release.

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

Some entries have line breaks expressed as `\n`: Your translation can and in most cases should use them as well, but you do not need to distribute them exactly as in the original. Try to find a translation that reads nicely, then place the line break codes at roughly the same intervals as the original uses (less if your language's glyphs are wider than latin ones). Important: You cannot use normal line breaks, you must use the `\n` codes, normal line breaks are not part of a translation.

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/

# Translation generation - for developers

## The automatic template generation

Before releasing every version, we regenerate the translation files.

Sometimes, new strings (names, uniques, etc) are added in the json files. In order to not have to add every single one to the translation files manually, we have a class - TranslationFileWriter - that, for every language:

-   Goes over the template.properties and copies translation lines
-   For every json file in the jsons folder
    -   Selects all string values - both in objects, and in arrays in objects, to any inheritance or nesting level.
        (Collections that can be parsed must be derived from List or AbstractCollection)
    -   Generates a 'key = value' line
-   Scans knowledge from UniqueType and UniqueParameterType instances and generates 'key = value' lines for them

This means that every text that ISN'T in the jsons or the UniqueType system needs to be added manually to the template.properties in order to be translated!
That also means if you've been adding new json structures you (or someone) should check TranslationFileWriter and see if it is able to cope with them.

## Rules for templates added manually

Building a new UI and doing something like `popup.add("Hello world".toLabel())` is a typical case: This is not contained in json data, so you'll have to add the template to `template.properties` yourself. For this example, adding `Hello world = ` somewhere in a line of its own could suffice.

Note the space at the end - it's absolutely required, and see to it your editor does not destroy your work. If you want to make sure, use Android Studio for git integration, but edit the file in an external editor, then run the unit tests locally before pushing. (to do: add link for instructions how to do that)

Leading spaces on a translation line or more than one space between the text and the `=` would mean these spaces are a _key part of the string to be translated_. That can work, but be warned: translators often overlook that those spaces are a required part of _both_ template _and_ translation, so if you _can_ do without, then doing without is safer.

Translation templates can use placeholders, and there's two varieties: `[]` and `{}`. Square ones take precedence over curly ones, and nesting works only with a single level of curly nested inside one level of square. I both cases the symbols themselves (`[]{}`) are removed by the translation engine.

Square brackets `[]` mean the outer and inner components are both translated individually. The outer template will use alias names inside the brackets - example: Your code outputs "Everyone gains [5000] gold!", then the translation template should be "Everyone gains [amount] gold! = ". The translation engine would translate the "Everyone gains [] gold!" and "5000" individually and reassemble them - of course, the number is simply passed through. But in other cases that could be e.g. a Unit name that would be translated, and you could trust that translations for units are already handled just fine. Note that [uniques](../Modders/uniques) often use the feature, but it is in no way limited to them. It it makes life easier for translators, use it.

Curly brackets `{}` are simpler - the contents within the brackets are translated individually, while the outer parts are passed through verbatim. Example: `"+$amount${Fonts.gold} {Gold}".toLabel()` - note the first `${}` is a kotlin template while the second pair becomes part of the string. It tells the translation engine to ignore the numbers and the symbol but to translate the single word "Gold".

## Rules for all sources

The [], {} and <> bracket types are used internally and cannot be part of a translatable text. Use () instead.

# Translation generation - for modders
If you can run desktop with the mod installed, then provide at least one valid translation of something that is present in your mod or the base game in that file. The file can be empty otherwise. Now run Unciv and use options-advanced-"Generate translation files". Reload your translation file and it will have added all the necessary "requires translation" entries specific to your mod (I repeat, works only if there's at least one valid entry already there). AFAIK you can also override base game translations, but those won't be output by the "Generate translation files" tool.

Here's an example:
Say you have a new nation in your mod named "The Borg". You create the translations folder, create an empty file named, say, "Hungarian.properties", add "The Borg = The Borg" to that, run Unciv and run the translation generator from options. Reload the new file, bingo all what Unciv would like to see is there. 

If you're modding on Android only - don't. That said, it's not impossible, just make do without the described tool and add everything yourself, test, rinse, repeat. Be aware that the game does not read changed files from disk if it doesn't need to, so on Droid you could either edit locally and force-stop to ensure changes are read, or edit on a github repo and re-install from there, or...

Adding new languages in a mod is not supported (because the completionPercentages.properties file determines which languages Unciv deems as known, and I'm not saying impossible as one could manipulate their GameSettings.json).

Remember, exact case is important both in translations left of the "=" and file names.

## More about translating
Sometimes you'll see a `English.properties` in the translation folder. For example, if you see `gold = credits` in `English.properties`, It means the word 'gold' will be displayed as 'credits' in the English version.
So in your translation file, though 'gold' is already translated in vanilla unciv, you should sill translate the line.
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

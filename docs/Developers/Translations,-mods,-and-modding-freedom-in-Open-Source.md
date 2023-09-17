# Translations, mods, and modding freedom in Open Source

Unciv is, at its core, a remake of Civ V, meaning mechanics-wise there's almost by definition not much place for innovation.
In terms of UI, there's nothing here that hasn't been done dozens of times, with far greater polish.
However, there is one area where Unciv is groundbreaking: in its accessibility of translations, the possibility space of its mods, and the relationship between them.

## Translations

### The translation process

So let's start with translation. Surely this is a solved problem, right? Source text + language = translated text, and this information needs to be in a file so the game can read it. What makes us different from, for example, Firaxis?

There are a couple of things, but the most significant is that this is an open-source game, and thus the *translations* are open-source as well.
This means translators are both amateurs and not *obligated* to translate, so if translating is difficult, they simply won't.

Amateurs can make mistakes, which is why it's vital that mistakes are easy to spot. That means that formats like "translation key" - e.g. `DIPLOMACY_GREETING = Siamo lieti di fare la vostra conoscenza.` are much less effective than `A pleasure to meet you. = Siamo lieti di fare la vostra conoscenza.` This format lends itself both the easier translation (it's immediately obvious what needs to be translated) and actual collaboration.

A common suggestion that we get (by people with little familiarity with the project) is to "use a website for translation". This is not bad advice for a small open source game, but there are multiple disadvantages that (for now) no translation website provides enough advantage to outweigh:

1. **Testing**. Currently, translations undergo a number of tests for verification - more on that later! This allows some language changes to be accepted and others not, and it's all in the same platform with the same tests. External translation tools don't allow for this.
2. **History and revisions**. This is what Git was made for, and nothing like it exists in the world. By itself this would not
3. **Release cycle**. We release versions semiweekly, and if we needed to upload changes to the translation website for every in-game change, and download them for every release, that's extra work. For some websites this is automate-able - for most it is not.
4. **Discussions**. Most crowdsourcing translation websites don't allow for discussions and corrections on translations. Github makes every translation collaborative work.
5. **Mass changes**. If we're changing the source of the translation but want to keep the various destinations (say, we change "Gold from trade routes +[amount]%" to "+[amount]% Gold from trade routes"), if all the translation files are in Git we can do that in 1 minute. If it's external, this varies greatly.

Here are some ways that we managed to go wrong in the past:

-   Putting all languages into the same file ("one big translation dictionary") - when multiple people edit this file for different languages, they can conflict with each other. Separate to different files for simpler management.

-   Using json - json is great for machines, but less so for humans, who can easily make mistakes. Json format is surprisingly finnicky, miss a closing " and the whole file is now unreadable.

The format we decided to go for is one file per language, delimited by " = " for visual separation, in a .properties file. Lines starting in # are considered comments, so we can add comments for translators.

### Building the translation files

As stated, Unciv releases versions semiweekly, and very often these changes include new objects or new UI elements. How do we keep all translation files up to date?

In Unciv, all object data is stored in json format. This allows us to iterate on all objects, regardless of type, and extract the various text fields (strings or lists of strings). We avoid duplication by saving all translation texts we've already added, and use the *existing* translations to populate the "value" for each translation "key" we found in the json files.

Since we rebuild the entire translation file every time, there's currently no way for translators to retain their own comments for future translators.
But on the other hand, since for each line that we add we already know if it's translated or not, this allows us to add a `# Requires translation` line before every non-translated line, which helps translators for languages that are almost fully translated to easily locate the new or changed terms for translation with ctrl+f (and of course this marking will disappear the next time we rebuild the file).

Since there are UI texts that are not part of any specific object (like "Start new game"), we have a separate template.properties file for texts to translate that are not in the json files. Unlike adding objects, where the developer doesn't need to address the translation files at all since it's all linked, when adding UI elements with new texts devs need to remember to add the texts to template.properties file.

### Translation placeholders

This is all well and good for specific text-to-text translations, but what about translating "A Temple has been built in Rome"? The same template could potentially be any building name, or any city name!

We do this with placeholders, which looks something like this: `[construction] has been built in [cityName] = [cityName] ha costruito [construction]`.
As you can see, the *placement* of the parameters can change between languages, so we have to name all parameters.

This also means that there can be explicitly *wrong* translations - if any parameter that appears in the source does not appear in the translated version, we won't be able to display this in-game! This is one of the translation tests that we mentioned earlier - when a translator opens a PR, the game undergoes build & test via the Github Actions, and will notify on failures. Finding the text that warns of the failure within the action output is currently mostly done by devs, but I hope to be able to automate this too someday.

To translate a text like "[Temple] has been built in [Rome]", therefore, we need to:

-   Find the relevant translation (we do this by erasing all text between square brackets in input and finding the relevant translation text)
-   Map placeholder names to input text (construction = Temple, cityName = Rome)
-   Replace placeholders in translation with TRANSLATED input text (in `[cityName] ha costruito [construction]`, replace "[cityName]" with translation of "Rome", and "[construction]" with translation of "Temple")

### Translating mod data

The translation generation reads information from "a ruleset", i.e. the set of jsons defining the game's objects.
Every mod is also a ruleset, either replacing or adding to the base ruleset defined in the game.
This means that the same translation generation that we do for the base game can also be applied to mods, and so each modder can decide (from within the game) to generate translation files for his mod, and since mods are uploaded to Github to be widely available as part of the mod release methodology, translators will be able to translate those files the exact same way that they translate Unciv's base ruleset.

## Uniques

### Moddable unique effects

Every object in Unciv can include "uniques" - a list of strings, each granting a unique effect that is not applicable for every object of its type.

For example, the Palace building has the unique "Indicates the capital city", and the settler has the unique "Founds a new city".
This allows us to share effects between multiple units, and to avoid hardcoding and allow modders to add *any* effect to *any* object.

Here too we encounter the problem of "generic" uniques - how can we have these effects grant a building, some stats, etc, using the same unique for all objects? Why, with placeholders of course! For example, one building has "Requires a [Library] in all cities", where "Library" can be replaced with any other building for similar effects. We can then extract the parameters from the unique at runtime, to know how to resolve the unique's effects.

Since the translation template is the same as the unique template, these uniques are instantly translatable as well!

We do have a slight problem, though - since translation texts come directly from the json files, and the json files have "Requires a [Library] in all cities", how do we tell the translators not to directly translate "Library" but the take the parameter name verbatim?
Well, 95% of translation parameters fit nicely into a certain type - units, buildings, techs, terrains etc. So we can search for an object with than name, and since we find a Library building, we can put "Requires a [buildingName] in all cities = " as our translation line.

### Filters

As time went on, we noticed that many of our "uniques" weren't so unique after all. Many were the same but with slightly different conditions. One affects all cities, one only coastal cities, and one only the city the building is built in. One affects Mounted units, one affects wounded units, one affects all water units, etc. We started compiling these conditions into "filters", which limited the number of uniques while expanding their range considerably.

Take the following example unique for a building: "[+1 Food] from [Deer] tiles [in this city]".

In its "placeholder" form, this is "[stats] from [tileFilter] tiles [cityFilter]".
stats can accept any list of stats, e.g. '-2 Gold, +1 Science', '+3 Culture', etc.
tileFilter can accept any number of tile parameters (base terrain e.g. 'Plains', terrain type eg. 'Land'/'Water', terrain features e.g. 'Forest', improvements e.g. 'Mine', resources e.g. 'Iron'.
cityFilter can accept 'in this city', 'in all cities', 'in capital', 'in coastal cities', etc.

There are also filters for units, all acceptable values are documented [here](../Modders/unique%20parameters).

### Unique management with Enums

The further along we go, the more generic the uniques become, and the more of them there are.
Older uniques become new ones, by being merged or made more generic, and the older ones are deprecated. Deprecation notices are put on Discord, but a one-time message is easy to miss, and if you come back after a while you don't know what's changed.
Modders discover during gameplay that the values they put for uniques were incorrect.

All these problems are solved with a single solution - since all uniques are defined by their text, we can create an enum with ALL existing uniques, which lets us:

-   Find all usages of a unique in the IDE instantly
-   Mark deprecated uniques as such using `@Deprecated("as of <versionNumber">)` for devs (and modders!)
-   Compare uniques using enum values, which is faster

What's more, with a little bit of autodetection magic, we can determine the *type* of the parameter using its text.
Using the above example, "[stats] from [tileFilter] tiles [cityFilter]", we can tell by the names of the parameters what each one is supposed to be,.
We can then check at loading time for each unique, if its parameter values matches the parameter type it's supposed to have, which lets us catch incorrect parameters.
The "autodetection" of parameter types for translations can also be fed from here, leading to much more accurate translation texts - instead of detecting from an example (e.g. "Requires a [Library] in all cities" from the json), we now use a dev-inputted value like "Requires a [buildingName] in all cities". This allows us to accept multiple types, like for e.g. "Requires [buildingName/techName/policyName]".

Deprecated values can be detected due to the `@Deprecated` annotation, and can be displayed to the modders when loading the mod, together with the correct replacement.

### Conditionals

Beyond the existing filters for units, buildings, tiles etc, there are some conditions that are global. For example, uniques that take effect when the empire is happy; when a tech has been researched; when the empire is at war; etc.
Rather than being 'build in' to specific uniques, these conditions can be seen as extensions of existing uniques and thus globally relevant.

For example, instead of "[+1 Production] [in all cities] when empire is happy", we can extract the conditional to "[+1 Production] [in all cities] <when empire is happy>". This does two things:
A. Turns the 'extra' unique back into a regular "[stats] [cityFilter]" unique
B. Turns the conditional into an extra piece that can be added onto any other unique

Conditionals have a lot of nuance, especially regarding translation and ordering, so work in that field is more gradual.

### What's next?

We have yet to fully map all existing uniques and convert all textual references in the code to Enum usages, and have yet to extract all conditionals from their uniques.

We already have a map of what uniques can be put on what objects - it won't take much to add that check as well and warn against uniques that are put on the wrong sorts of objects.

Once we build the full inventory of the uniques, instead of the wiki page that needs to be updated manually we'll be able to generate a list of all acceptable uniques and their parameters directly from the source of truth. Put that in a webpage, add hover-links for each parameter type, generate and upload to github.io every version, and watch the magic happen.

We'll also be able to notify modders if they use "unknown" uniques.

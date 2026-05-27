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

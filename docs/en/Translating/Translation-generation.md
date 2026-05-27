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

Square brackets `[]` mean the outer and inner components are both translated individually. The outer template will use alias names inside the brackets - example: Your code outputs "Everyone gains [5000] gold!", then the translation template should be "Everyone gains [amount] gold! = ". The translation engine would translate the "Everyone gains [] gold!" and "5000" individually and reassemble them - of course, the number is simply passed through. But in other cases that could be e.g. a Unit name that would be translated, and you could trust that translations for units are already handled just fine. Note that [uniques](../Modders/uniques.md) often use the feature, but it is in no way limited to them. If it makes life easier for translators, use it.

Curly brackets `{}` are simpler - the contents within the brackets are translated individually, while the outer parts are passed through verbatim. Example: `"+$amount${Fonts.gold} {Gold}".toLabel()` - note the first `${}` is a kotlin template while the second pair becomes part of the string. It tells the translation engine to ignore the numbers and the symbol but to translate the single word "Gold".

## Rules for all sources

The [], {} and <> bracket types are used internally and cannot be part of a translatable text. Use () instead.

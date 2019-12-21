Like most open-source projects, Unciv is developed at Github, so if you don't have a user you'll first have to create one.

The translation files are at https://github.com/yairm210/UnCiv/tree/master/android/assets/jsons/translationsByLanguage

If you're adding a new language, you'll need to create a new file ('Create a new file' to the right of the folder name in the UI), and copy into it the contents of template.properties

If you're adding stuff to an existing language, simply start editing the file!

Each untranslated phrase will have a "requires translation" line before it, so you can quickly find them. You don't need to remove them yourself if you don't want to - they will be automatically removed the next time we rebuild the file.

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

You don't need to download anything, all translation work can be done on the Github website :)

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/

Beyond the regular translations, there are 2 more files that contain special translations: the [Tutorials](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Tutorials) and the [Nations](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/Nations) json files.

Both of these are much more rigorous in their type-checking, but the basic instructions are the same - copy the base file (Nations, Tutorials_English) to a new file with your language.

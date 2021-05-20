## Starting out

The translation files are at https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/translations

If you're adding a new language, you'll need to create a new file ('Create a new file' to the right of the folder name in the UI), and copy into it the contents of template.properties

If you're adding stuff to an existing language, simply start editing the file!

You don't need to download anything, all translation work can be done on the Github website :)

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

## Wait, what just happened?

Like most open-source projects, Unciv is developed at Github, so if you don't have a user you'll first have to create one. The way Github works is the following:

1. You create a 'fork' repo, i.e. copy, of Unciv that belongs to your user (myUser/Unciv)

2. You make changes to your copy. These changes are called a 'commit'.

3. You make a pull request, which is basically asking for the changes you made on myUser/Unciv to be merged into the main repo (yairm210/Unciv)

When you ask to 'edit' a file in yairm210/Unciv, these stages happen *automatically* - but it's important to understand what's happening behind the scenes do you understand where the changes actually are!

## Other notes

Make sure that you make the changes in the 'master' branch in your repo!

Each untranslated phrase will have a "requires translation" line before it, so you can quickly find them. You don't need to remove them yourself if you don't want to - they will be automatically removed the next time we rebuild the file.

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/

# Translation generation - for developers

Before releasing every version, we regenerate the translation files.

Sometimes, new strings (names, uniques, etc) are added in the json files. In order to not have to add every single one to the translation files manually, we have a class - TranslationFileWriter - that, for every language:

- Goes over the template.properties and copies translation lines
- For every json file in the jsons folder
    - Selects all string values - both in objects, and in arrays in objects
    - Generates a 'key = value' line

This means that every text that ISN'T in the jsons needs to be added manually to the template.properties in order to be translated!
Like most open-source projects, Unciv is developed at Github, so if you don't have a user you'll first have to create one. The way Github works is the following:

1. You create a 'fork' repo, i.e. copy, of Unciv that belongs to your user (myUser/Unciv)

2. You make changes to your copy. These changes are called a 'commit'.

3. You make a pull request, which is basically asking for the changes you made on myUser/Unciv to be merged into the main repo (yairm210/Unciv)

When you ask to 'edit' a file in yairm210/Unciv, these stages happen *automatically* - but it's important to understand what's happening behind the scenes do you understand where the changes actually are!

Make sure that you make the changes in the 'master' branch in your repo!

The translation files are at https://github.com/yairm210/Unciv/tree/master/android/assets/jsons/translations

If you're adding a new language, you'll need to create a new file ('Create a new file' to the right of the folder name in the UI), and copy into it the contents of template.properties

If you're adding stuff to an existing language, simply start editing the file!

Each untranslated phrase will have a "requires translation" line before it, so you can quickly find them. You don't need to remove them yourself if you don't want to - they will be automatically removed the next time we rebuild the file.

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

You don't need to download anything, all translation work can be done on the Github website :)

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/

If you're done with all of that, then there's always the Google Play page to translate... ;)

For the Google Play translation, we'll need you to translate 2 parts.

The first is the short description for the game, `Open source 4X civilization-building game`

The second is the long description for the game:

```
An open-source reimplementation of the most famous civilization-building game ever - fast, small, no ads, free forever!

Build your civilization, research technologies, expand your cities and defeat your foes!

Requests? Bugs? Todo list for the application is https://github.com/yairm210/UnCiv/issues, every small help is welcome!

Questions? Comments? Just bored? Join us on the new discord server at https://discord.gg/bjrB4Xw ;)

Want to help translating the game into your language? See https://github.com/yairm210/UnCiv#how-can-i-translate-to-language

Grok Java, Kotlin or C#? Join us at https://github.com/yairm210/UnCiv!

Play on a 10' or 7' Tablet? We'd be glad to have a good-looking screenshot!

The world awaits! Will you build 
your civilization into an empire that will stand the test of time?

* Internet permissions required for Multiplayer

* If you want to donate, I have a Paypal at https://www.paypal.me/yairm210
```
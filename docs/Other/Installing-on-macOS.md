# Installing on MacOS

There are currently several ways to install Unciv on MacOS.

It is recommended that you do not install from source, since the end result will be the same.

# Installing using MacPorts

Details [here](https://ports.macports.org/port/unciv/) - simply run `sudo port install unciv` from command line and you're good to go!

Does not require a JDK to be preinstalled.

## Installing using JAR

1. If you don't already have Java 8 or OpenJDK (versions 11 and 18 do work) installed on your Mac, either
  * Download it from the [official website](https://java.com/en/download/). Once you have downloaded the file, open it and follow the instructions on screen.
  * If you use [Homebrew](https://brew.sh/), just run `brew install java`
2. Now that you have Java installed, it's time to download the latest Unciv JAR. This can be done from the [releases](https://github.com/yairm210/Unciv/releases) screen here on Github. Download the file called *Unciv.jar*.
3. To run the game, you'll need to run `java -jar Unciv.jar` from a Terminal.
4. Alternatively, you could create an 'Unciv.sh' file containing that line, and then run the new file to allow you to create shortcuts, etc.

_(Sadly Unciv does not auto update when installing it using this method on MacOS so you will need to download the latest Unciv.jar from Github every time you want to update the game.)_

## Installing from source

For instructions on how to install Unciv from source see [Building locally without Android Studio](../Developers/Building-Locally.md). It is not recommended to use this method as it achieves the same result as the first method whilst being much more complicated and prone to errors along the way.

_(Sadly Unciv does not auto update when installing it using this method on MacOS so you will need to follow these steps every time you want to update the game.)_

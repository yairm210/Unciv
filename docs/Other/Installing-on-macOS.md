# Installing on macOS

There is currently two ways to install UnCiv on macOS. It is recommended that you use the first method as the second one is overly complicated and the end result will be the same. Both installation methods require that you have Java 8 installed on your mac.

## Installing using JAR

1. If you don't already have Java 8 installed on your mac make sure you download it from the [official website](https://java.com/en/download/). Once you have downloaded the file open it and follow the instructions on screen.
2. Now that you have Java 8 installed it's time to download the latest Unciv JAR. This can be done from the [releases](https://github.com/yairm210/UnCiv/releases) screen here on Github. Download the file called Unciv.jar.
3. To run the game, you'll need to create to run `java -XstartOnFirstThread -Djava.awt.headless=true -jar Unciv.jar` from a Terminal.
4. Alternatively, you could create a 'Unciv.sh' file containing that line, and then run the new file, to allow you to create shortcuts etc.

_(Sadly UnCiv dose not auto update when installing it using this method on macOS so you will need to download the latest Unciv.jar from Github every time you want to update the game.)_

## Installing from source

For instructions on how to install UnCiv from source see [Building locally without Android Studio](../Developers/Building-Locally.md). It is not recommended to use this method as it achieves the same result as the first method whilst being much more complicated and prone to errors along the way.

_(Sadly UnCiv dose not auto update when installing it using this method on macOS so you will need to follow these steps every time you want to update the game.)_

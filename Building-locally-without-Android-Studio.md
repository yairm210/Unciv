If you also have JDK 8 installed, you can compile Unciv on your own by cloning (or downloading and unzipping) the project, opening a terminal in the Unciv folder and run the following commands:

### Windows

Running: `gradlew desktop:run`

Building: `gradlew desktop:dist`

### Linux/Mac OS

Running: `./gradlew desktop:run`

Building: `./gradlew desktop:dist`

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` first. *This is a one-time procedure.*

If you get an error that Android SDK folder wasn't found, firstly install it by doing in terminal:

`sudo apt update && sudo apt install android-sdk` (Debian, Ubuntu, Mint etc.)

After that you should put its folder to the file `local.properties` by adding this line:

`sdk.dir = /path/to/android/sdk` which can be `/usr/lib/android-sdk` or something other.

If during the first launch it throws an error that the JDK version is wrong try [this JDK installation](https://www.azul.com/downloads/zulu-community/?package=jdk).

Gradle may take up to several minutes to download files. Be patient.
After building, the output .JAR file should be in /desktop/build/libs/Unciv.jar

For actual development, you'll probably need to download Android Studio and build it yourself - see Contributing :)

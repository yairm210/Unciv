# Hosting a Multiplayer server

Due to certain limitations on Dropbox's API, with the current influx of players, we've many times reached the point that Dropbox has become unavailable.

Therefore, you can now host your own Unciv server on any computer that can run Java programs.

This guide is written for people with a moderate amount of technical knowledge about computer software and who are able to search the web to learn stuff they might not know. If you're completely new to this, you'll likely not be able to follow without some larger time investment to learn.

If you're proficient in server hosting, there's another how-to for you at the end.

## How To

Before starting, you must have a Java JDK installed. You'll also have to download the [latest UncivServer.jar](https://github.com/yairm210/Unciv/releases/latest/download/UncivServer.jar).

From the directory where the `UncivServer.jar` file is located, create a folder named "MultiplayerFiles", open a terminal (in Windows, Shift+RightClick in the folder) and run the following command in the directory:
`java -jar UncivServer.jar`

Your server has now started!

To check if everything works, you can start Unciv on the same computer, go to "Options > Multiplayer", then enter `http://localhost` as the "Server address" and click "Check connection to server". You should now get a "Success!" result, which means it's working!

To connect with other devices, you'll need the port (default 80) the server is running on to be visible externally (port forwarding) and know your external IP-address.

On the other device, enter the URL to your server, click 'check connection' from the new device, and if you get the same "Success!" result - congratulations, you're connected to the same server and can start a multiplayer game!

Please note:
* Devices *not* connected to the same server will *not* be able to participate in multiplayer games together
* In many places, your external IP address changes periodically. If that is the case, you either have to update the IP all the time or use something like a dynamic DNS service.

## How To for people with hosting experience

* Have a Java JDK installed
* Download the [latest UncivServer.jar](https://github.com/yairm210/Unciv/releases/latest/download/UncivServer.jar) (can also use that link to automatically update probably)
* See options with `java -jar UncivServer.jar --help`
    * The server will run on a specified port (`-p`, default `80`), writing files in a folder (`-f`, default `./MultiplayerGames/`), so it needs appropriate permissions.
* Run it: `java -jar UncivServer.jar -p 8080 -f /some/folder/`
    * It basically just does simple file storage over HTTP.
    * Files are not cleaned automatically if a game ends or is deleted on the client-side

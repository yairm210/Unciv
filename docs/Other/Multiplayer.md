# Multiplayer

Multiplayer in Unciv is based on simple save file up/download, which is why it is based on a free Dropbox account by default. However, a lot of people use this default, so it is uncertain if you'll actually be able to access it consistently. See [Hosting a Multiplayer server](#hosting-a-multiplayer-server) for hosting your own server.

## How to play

0. Make sure you are all using the same multiplayer server (`Main Menu -> Options -> Multiplayer`)
0. Let all players send you their user ID (`Main Menu -> Multiplayer -> Copy user ID`).
   * (Optional) Add those user IDs to your friend list (`Main Menu -> Multiplayer -> Friends list`).
0. In `Main Menu -> Start new game`, check `Online multiplayer` on the left. On the right, add more human players and input the user IDs of the players you want to play with. Press `Start game!`.
0. The game ID will be automatically put in your clipboard. (If you lost it, you can get it again from `Main Menu -> Multiplayer -> Copy game ID`). Send this game ID to the other players.
0. The other players need to go to `Main Menu -> Multiplayer -> Add multiplayer game` and enter the game ID you just sent them. They can then join the game from this multiplayer screen.


## Hosting a Multiplayer server

Due to certain limitations on Dropbox's API, with the current influx of players, we've many times reached the point that Dropbox has become unavailable.

Therefore, you can now host your own Unciv server on any computer that can run Java programs.

This guide is written for people with a moderate amount of technical knowledge about computer software and who are able to search the web to learn stuff they might not know. If you're completely new to this, you'll likely not be able to follow without some larger time investment to learn.

If you're proficient in server hosting, there's another how-to for you at the end.

### How To

Before starting, you must have a Java JDK installed. You'll also have to download the [latest UncivServer.jar](https://github.com/yairm210/Unciv/releases/latest/download/UncivServer.jar).

From the directory where the `UncivServer.jar` file is located, create a folder named "MultiplayerFiles", open a terminal (in Windows, Shift+RightClick in the folder) and run the following command in the directory:
`java -jar UncivServer.jar`

Your server has now started!

To check if everything works, you can start Unciv on the same computer, go to "Options > Multiplayer", then enter `http://localhost` as the "Server address" and click "Check connection to server". You should now get a "Success!" result, which means it's working!

To connect with other devices, you'll need the port (default 80) the server is running on to be visible externally (port forwarding) and know your external IP-address.

On the other device, enter the URL to your server (`http://<your IP address>:<your chosen port>`), click 'check connection' from the new device, and if you get the same "Success!" result - congratulations, you're connected to the same server and can start a multiplayer game!

Please note:
* Devices *not* connected to the same server will *not* be able to participate in multiplayer games together
* In many places, your external IP address changes periodically. If that is the case, you either have to update the IP all the time or use something like a dynamic DNS service.

### How To for people with hosting experience

* Have a Java JDK installed
* Download the [latest UncivServer.jar](https://github.com/yairm210/Unciv/releases/latest/download/UncivServer.jar) (can also use that link to automatically update probably)
* See options with `java -jar UncivServer.jar --help`
    * The server will run on a specified port (`-p`, default `80`), writing files in a folder (`-f`, default `./MultiplayerFiles/`), so it needs appropriate permissions.
* Run it: `java -jar UncivServer.jar -p 8080 -f /some/folder/`
    * It basically just does simple file storage over HTTP.
    * Files are not cleaned automatically if a game ends or is deleted on the client-side

## Third-party (unofficial) software for hosting your own Unciv server

* [https://github.com/Mape6/Unciv_server](https://github.com/Mape6/Unciv_server) (Python)
* [https://gitlab.com/azzurite/unciv-server](https://gitlab.com/azzurite/unciv-server) (NodeJS)
* [https://github.com/oynqr/rust_unciv_server](https://github.com/oynqr/rust_unciv_server) (Rust)
* [https://github.com/touhidurrr/UncivServer.xyz](https://github.com/touhidurrr/UncivServer.xyz) (NodeJS)

## Third-party (unofficial) publicly hosted Unciv servers

These servers are run by the community and **not** official servers. These servers may become (temporarily or permanently) unavailable and lose your saves. They might also collect data like your IP, how often you play, or other data. Use these only if you accept these risks and trust the server owners.

* [`https://uncivserver.xyz/`](https://uncivserver.xyz/) - Run by PikaPika#2315 on [their Discord](https://discord.gg/H9em4ws8XP) ([Source Code](https://github.com/touhidurrr/UncivServer.xyz/))

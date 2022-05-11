# Hosting a Multiplayer server

Due to certain limitations on Dropbox's API, with the current influx of players, we've many times reached the point that Dropbox has become unavailable.

Therefore, you can now host your own Unciv server, when not on Android.

To do so, you must have a JDK installed.

From the directory where the UncivServer.jar file is located, create a folder named "MultiplayerFiles", open a terminal and run the following line:
`java -jar UncivServer.jar`

Don't forget to use 'cd' to switch to the correct dictionary. Here's an example in Windows.

```
D:
cd Games
cd unciv
mkdir MultiplayerFiles
java -jar UncivServer.jar
```

Your server has now started!

In Unciv itself, from the same computer, enter Options > Multiplayer.

Enter the URL of the computer you ran the server on (or http://localhost)

If you click "check connection to server" you should now get "Return result: true", which means it's working!

For other devices, you'll need an external IP, which is out of scope for this documentation since there are many ways of achieving it.

On the other device, do the same - enter the URL, click 'check connection' from the new device, and if you got the same result - congratulations, you're both connected to the same server and can start a multiplayer game on the server!

Android has some restrictions and does not allow unencrypted HTTP traffic from the Unciv app to a server. So you need to have a reverse proxy that sits between your (Android) client(s) and the Unciv server. The reverse proxy then needs to have a valid certificate and handles the TLS sessions for your Unciv server.

Please note that devices NOT connected to the same server will NOT be able to participate in multiplayer games together!

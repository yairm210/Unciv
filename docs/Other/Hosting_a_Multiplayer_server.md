
# Hosting a Multiplayer server

Due to certain limitations on Dropbox's API, with the current influx of players, we've many times reached the point that Dropbox has become unavailable.

Therefore, you can now host your own Unciv server, when not on Android.

To do so, you must have a JDK installed.

From the directory where the Unciv.jar file is located, open a terminal and run the following line:
`java -cp Unciv.jar com.unciv.app.desktop.UncivServer`

Don't forget to use 'cd' to switch to the correct dictionary.
To make it easy, just put unciv in disk C directly

Your server has now started!


In Unciv itself, from the same computer, enter Options > Multiplayer.

Click the first text (Current IP address) to copy the IP to the clipboard.
Then, click the second the second (Server IP address) to put your computer's IP in the "Server IP" slot.

If you click "check connection to server" you should now get "Return result: true", which means it's working!


So far you ran the server and connected yourself to it, but now for the interesting part - connecting other people!

The IP should still be in your clipboard - if not, just click the 'copy to clipboard' button again.
Send the IP to the other device, there - copy it, and click 'copy from clipboard'.
You can of course enter the IP manually if that's easier for you.

Click 'check connection' from the new device, and if you got the same result - congratulations, you're both connected to the same server and can start a multiplayer game on the server!


Please note that devices NOT connected to the same server will NOT be able to participate in multiplayer games together!

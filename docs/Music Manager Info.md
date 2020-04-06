# The 'Music Manager'

**So far just  a brainstorm list here**

Meant to complement the 'New Music Player' branch
('New Music Player' picks situation-dependent music tracks out of a pool using filename prefixes and suffixes)

## json
Note on Modding: The Music Manager treats all mods, if they contain a MusicDownloadInfo.json file, as equal part of the whole, their groups just get listed last. Any tracks will go into the mod's music folder and their presence is tested in that folder only. However, cover images go into and are loaded from the global cover cache.

#### MusicDownloadInfo
Just a container for zero to N MusicDownloadGroup's
* [groups] - array of MusicDownloadGroup

#### MusicDownloadGroup
Meant as a playlist, album, collection sort of thing.
Prerequisite: All tracks **must** have the same legal status. Common source is strongly recommended.
In case of compilations from different sources, all individual tracks must be re-listed with their source in the group description field. A track has only one row in the UI, the group has unlimited screen space.
Cover images are not processed prior to display, choice of URL should consider this. The effective resolution once the user sees it will typically be a square box of no more than 600x600 pixels. 
* [title] - Caption, displayed prominently at the top of the Music Manager page.
* [coverUrl] - Link to a suitable 'cover' image (URL). Will automatically be downloaded for display and cached if not already present locally. 
* [coverLocal] - Local file name. Used as key to find cached copy. Cache is kept in <assets>/cover-cache. This should be globally unique including mods, whenever 
* [description] - Detailed description of the group. Text will wrap, manual line breaks (as newline characters \n) are supported.
* [credits] - Should describe the source/author conforming to the author's attribution requirements. The display field is touchable if a URL is found.  
* [license] - Verbatim license text conforming to the author's attribution requirements. The display field is touchable if a URL is found.
* [tracks] - array of MusicDownloadTrack
   
#### MusicDownloadTrack
Note: this entity has no provisions for legal attribution, composer, artist or source. Such information **must** go in the MusicDownloadGroup container.
Note: a track is accepted as present and not re-downloaded by the manager if a local copy exists with a different extension as defined in the json, as long as it's in the list of supported audio formats (mp3,ogg,m4a).
* [title] - Display label for the track. Suggestion: Exactly as the author presented it on her site.
* [info] - Displayed in the Music Manager to the right of title in lighter grey. Meant for track lengths.
* [localFile] - file name including extension excluding path, see note above.
* [url] - Download link. Currently, plain URI's without parameters are supported.

## TO-DO
* ~~Current download code crashes instantiating a callback delegate~~
* ~~Watchdog timeout should be dynamic (Platform independent knowledge about uplink bandwidth? Need to add filesize to track data?)~~
* Translation support
* Connection loss can create 0 byte files
* Integrate Dropbox code, allow this source for the thatched villagers
* On mobile: is the info whether the device is on a metered network (or any at all) easily accessible? If so, block access / setting for metered?  
* ~~Play/stop buttons are single-use: Why is the onClick handler only functional once?~~
* Double-quotes and underscores in descriptions, track titles etc disappear - fonts have only 98 glyphs but "_ are among them?
* Some images display like a negative: Does Gdx do some 'translate one pixel color to transparency' thing???
* Integrate properly with Music Player branch (and handle play button going back to play once track finishes)
* Code needs checking for the case of zero groups -> should disable manager screen button? Screen is empty and non-crashing.
* ~~Needs inspection: URL parameters (?x=1&y=2) - do they work as is or does the API require separating them?~~
* Support additional header fields - referer? Accept-*? Auth?
* Cover download can be started in parallel several times for the same image, if it is defined identically in several groups. Last one arriving will eventually win, unless a file lock prevents that.
* MusicMgrDownloader.stopDownload() currently does next to nothing. Maybe it can prevent arriving responses from saving files? I can see no way to have it forcefully close the TCP sockets...
* ~~How to keep user informed about download progress?~~
* Allow file deletion from manager?
* Allow sequentially playing a whole group?
* ~~Allow select all groups?~~
* ~~Stop downloads button~~
* ~~Update track availability while DL is running~~

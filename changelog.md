## 4.1.9

Resolved  - Peace cooldown with city-states

refactor: Simplified city-state war declaration

All "attacked city state" functions should only activate when attacking directly, not when declaring war due to alliances

Resolves  - tileFilter matches resource name and uniques

Resolves  - mapholder size reset after resize

By Azzurite:
- Add multiplayer turn sound notification 
- Fix crash during next turn automation 

disable worldWrap if its disabled in settings  - By alexban011

Removed incorrectly translated strings from indonesian translation  - By xlenstra

## 4.1.8

Resolved crashes when centering on a city-state with no cities

By OptimizedForDensity:
- Certain projects cannot be hurried by great engineer
- Fix scout not upgrading through ruins
- Fix rare case where ruins would delete an AI unit while trying to upgrade it
- Prevent duplicate ruin reward

WordScreenTopBar reworked, portrait-friendlier  - By SomeTroglodyte

Fixed minimap fog of war for spectators - By alexban011

Fix "Free technology" allowing restricted techs - By MindaugasRumsa

## 4.1.7

By OptimizedForDensity:
- Fix medic and amphibious promotions
- Better Great Prophet AI
- More rankings & demographics screen icons

By SomeTroglodyte:
- Better Tutorials
- Prevent city-to-city Battle Table
- Minor fixes

By alexban011:
- spectators can no longer move and attack with units
- Better friends list UI

Bugfixes for units' teleportation - By JackRainy

By Azzurite:
- Fix map editor zoom in

HexaRealm tileset update  - By GeneralWadaling

## 4.1.6

Multiplayer friends list - by alexban 011

By Azzurite:
- Improve performance of worldmap panning
- Fix multiplayer sometimes duplicating games
- Allow non-SSL-encrypted HTTP traffic & warn Dropbox users

By OptimizedForDensity:
- Fix aircraft disappearing when carrier is teleported
- Pillaging certain improvements yields stats

By SomeTroglodyte:
- Fix Pixel unit nation colors after combat

Autoassign population when the manual assignment fails - By JackRainy

## 4.1.5

Better minimap buttons

By SomeTroglodyte:
- Draw borders under pixel units
- Fix rare NextTurnButton crash

Improve unconstructable improvement suggestions - By doublep

By alexban011:
- Disable state-changing buttons for puppet cities
- Add optional on-screen buttons to zoom in and out

Corrected some broken policy uniques - By OptimizedForDensity

Dynamically adjust StatsTable height - By itanasi

Fix multiplayer turn checker writing to wrong locations - By Azzurite

## 4.1.4

AI counteroffers will no longer contain items already offered by the player

By itanasi:
- Citizen management area expandable
- Citizen Management buttons disabled if Spectator
- Make Locked Tiles clickable

Puppet cities only focus on gold - By alexban011

Multiplayer Status Display - By Azzurite

Fix piety complete faith discount - By OptimizedForDensity

## 4.1.3

Implement 'wait' command - by doublep

By SomeTroglodyte:
- Re-hide Enable Portrait option on desktop
- Show required resource for upgrades
- Fix Right-Click attacks made no sound

Fix missing icons in civilopedia from main menu - By OptimizedForDensity

By Azzurite:
- Return to current game from main menu "Resume"

By alexban011:
- Fixed slider sound when opening screens
- Added confirmation option for "next turn"

Adding spaceship sprites for FantasyHex tileset - By GeneralWadaling

## 4.1.2

Fixed multiplayer bugs (double files, turn checker problems) - By Azzurite

By SomeTroglodyte:
- Fix Autocracy Complete bug
- Fix effect of new Beliefs not immediately visible

Fix crashes when a civ does not have a capital - By OptimizedForDensity

By xlenstra:
- Great improvements buildable on removable terrain features

By itanasi:
- Fix Zone of Control
- Assign Population Improvements

By JackRainy:
- Performance improvements
- Better city connection quest check

## 4.1.1

HexaRealm update - By GeneralWadaling

Performance improvements - By Azzurite

Great General typed uniques and improved moddability - By JackRainy

Great improvements can again be constructed on forest - By xlenstra

Fixed MP refresher not working after rate limit - By GGGuenni

Fix "May Withdraw" modifier calculation - By OptimizedForDensity

Allow generation of "Default" Deciv redux maps - By SomeTroglodyte

Improve autofix when typing multiplayer server URL - By touhidurrr

## 4.1.0

Upgraded to libGDX 1.11.0 - enables Unciv on M1 chips

Allow Android Unciv more memory than standard apps - By Azzurite

Allow liberating a traded city - By JackRainy

By xlenstra:
- Disabled CS buttons when at war; CS keep influence when at war with ally
- Fixed bug where roads could not be removed

By SomeTroglodyte:
- Accelerate custom map selection
- Map Editor improvements
- Notifications no longer become unscrollable past a point

Capital movement tweaks  - By OptimizedForDensity

## 4.0.16

AI will not declare war if it definitely can't take a city

Civilooedia from mainmenu  - By SomeTroglodyte

Rate limit handling for Dropbox  - By GGGuenni

By OptimizedForDensity:
- Use ranged strength when defending against ranged attacks
- Hide cities where wonders are built until city is explored

By xlenstra:
- Fixed "improvements could no longer be built by workers" bug
- Fixed "roads seemingly remove improvements" bug

Hexarealm tileset added to base game - By GeneralWadaling

## 4.0.15

Fixed proxy issues when starting new multiplayer games - By alexban011

By SomeTroglodyte:
- Optional gzipping of saved games
- Modding: Typed unit promotion effects

By Azzurite:
- Add UncivServer.jar to github release
- Improvements and city buttons visible to Spectator

By touhidurrr:
- Autofix Multiplayer Server URL on input

Hide Unmet Civ and Capital Names in Victory Screen - By OptimizedForDensity

Improved clarity & moddability of building improvements  - By xlenstra

## 4.0.14

Performance improvements

By SomeTroglodyte:
- Move UncivServer to own module
- Font choice rework
- Reworked "Creates improvement on a specific tile" Unique

By Azzurite:
- Fixed Barbarian Camps revealed by Honor not showing immediately in multiplayer

By JackRainy:
- Auto-downloading missing mods for save files
- Typed missing uniques

## 4.0.13

Extensive performance work

Can no longer gift units to enemy City States

Can now have owner-style-specific improvement images (e.g. Mine-Medieval-European)

Resolved rare crashes

By Azzurite:
- Create turn notifier for when the game is running for Windows
- Fix Sweden not being able to gift great people to city states

By Jack Rainy:
- Allow trade routes via city-states
- Better unit expulsion when declaring war/after open borders ended

New Demographics Scoreboard  - By letstalkaboutdune

## 4.0.12

By SomeTroglodyte:
- Show available resources from CityScreen
- F-droid integration improvements!

By JackRainy:
- Allow city connections via open borders and own harbors only
- Enable scrolling for the oversized popups

Performance improvements - By Azzurite

By xlenstra:
- Fixed `provides [amount] [resource]` on buildings not accepting conditionals
- Implemented most things for moddable victory conditions

Check internet before starting mp game to avoid freeze - By alexban011

## 4.0.11

Enabled nested translations!

Don't allow trade routes through enemy cities

By JackRainy:
- Protect the cities from the fallout spawning
- Correct swap of the full-loaded carriers

By SomeTroglodyte:
- Map editor2.2
- Show number of global followers in Religion Overview

By xlenstra:
- Fixed improvements with unfulfilled 'Only Available' still buildable
- Fixed a crash when a plane tried to enter a full city

## 4.0.10

By JackRainy:
- Correct calculation of production for Settlers
- Fixed swapping carriers with payloads
- Display wonders built in other civs correctly

By SomeTroglodyte:
- Resources UI
- Some improvements to Map Editor - step 1
- Implement fastlane step 1 - minimal framework

Made Specialist allocation table expandable  - By ultradian

Fix typo in vanilla - By touhidurrr

Healing values per Civ V - By letstalkaboutdune

## 4.0.9

By xlenstra: Moddable victories!

Fixed crashing bug with aircraft rejection reasons

Mod land units with missile and nuke uniques no longer crash the game

improvementFilter accepts uniques as well

terrainFilter accepts tile resource uniques

Unique docs writer makeover  - By SomeTroglodyte

Add checks against 0 Strength combat  - By itanasi

## 4.0.8

Redone Map Editor - By SomeTroglodyte

Added very basic "flash red on attack" battle animations

Air units with no space in the city cannot be selected for construction

Unpillagable improvements removed entirely on nuke

AIs don't add double agreements to counteroffers

Clearer "owned" resource on AI trade offer

By xlenstra:
- Made 'go to on map' translatable & fixed crash
- Fixed two small bugs

## 4.0.7

Resolved problems in hosting Unciv server

By xlenstra:
- Added 'unit upgrades for free' unique
- Added a 'go to capital'-button in diplomacy screen
- Fixed vanilla policies regression

By itanasi:
- Air Units take damage in Combat
- Updating XP Rewards. Interception now gives XP
- When capturing Civilian, stop current action

Fixed TurnChecker crash when using custom server  - By GGGuenni

Add modding translation instruction to the wiki  - By heipizhu4

## 4.0.6

Got rid of ANRs from multiple sources

Performance improvements, including framerate

Improved continuous rendering's framerate, to solve some ANRs

Discord suggestion - have allied city states see all tiles of their ally, so they can support them militarily

MusicController improvements  - By SomeTroglodyte

Fixed a bug where production would not be retained when changing from an obsolete unit to its upgraded unique unit - By xlenstra

## 4.0.5

Added borders to minimap

Resolved Diplomatic Victory check not triggering for human player

Cities that become capital no longer continue being razed

By SomeTroglodyte:
- Make Icons clickable in religions per city display
- Nicer NotificationScroll

Fixed a bug where crosshairs are everywhere with the 'attack when embarked' unique  - By xlenstra

Fixed turn checker crashes   - By GGGuenni

## 4.0.4

Caught bad URL parsing exceptions

Resolved ANRs when opening Options menu

Solved 'check server connection' errors on Android

By SomeTroglodyte:
- Fix Goddess of Protection error
- Tabbed pager architecture update

## 4.0.3

Changed server connection to URL-based to allow connection to uncivserver.xyz

By xlenstra:
- Added a unique for attacking when embarked
- Generalized Denmark's unique

By HaneulCheong:
- Improved city name generation
- Fixed the official wiki

By SomeTroglodyte:
- Remove re-orientation for OptionsPopup
- Fix enabled buildings not displaying in games without nukes

## 4.0.2

Solved out of memory errors for modded base rulesets

By SomeTroglodyte:
- City Info Table Expanders improvements
- Empire Overview tweaks
- Translation writer improvements
- Reactivate Worldscreen Ctrl key bindings

Generalized a few nation uniques  - By xlenstra

## 4.0.1

Custom server port  - By HAHH9527

Add Moddable Policy Priorities  - By HaneulCheong

By SomeTroglodyte:
- Enable ModOptions uniques and ModConstants from non-base mods
- Improved Widgets - Fixing Tabbed Pager Scrolling
- Make max Zoom out a setting
- More thorough workaround for Char.code and Char(Int) crashing
- Fix crashes and better crash info

## 4.0.0

Can now host your own Unciv server for Multiplayer - details in the wiki!

By SomeTroglodyte:
- Prevent Char-to-code crash
- Moddable Ice generation
- Empire Overview improvements
- Fix CS unit gift crash

By xlenstra:
- Split 6 tiles visible unique into its parts, making it more moddable
- Fixed a bug where the resource supply overview would not add up
- Added an AI for building & using spaceship parts
- Unified & generalized a few uniques

Custom desktop font - by HAHH9527

## 3.19.18

Double Zoom Out Range  - By itanasi

By SomeTroglodyte:
- Redesign of Empire Overview Screen - more info saves, better portrait display, and many more changes!
- World Screen unit supply deficit icon now updates properly
- Close little loophole allowing promoting a unit after moving or attacking

## 3.19.17

Better displaying of units that cannot be built

By SomeTroglodyte:
- Support more freely modded Worker-like units
- Religion overview improved
- Mod checker minor improvements
- Fixed Spectator & AI games in seemingly endless loop

Multiplayer code cleanup  - By GGGuenni

## 3.19.16

Wiki improvements

Unique units abilities that should be inherited by upgrades  - By SomeTroglodyte

By SpacedOutChicken:
- Add Amphibious promotion to Songhai units
- Petra fix

## 3.19.15

By SomeTroglodyte:
- UI improvements of Religion Picker Screen
- Better Notification locations
- Code cleanup
- Fix Mughal Fort unique

By itanasi:
- Return Stacking Terrain Bonus to Civ5 Rules

By lishaoxia1985:
- UI improvements

Added "Starts with [policy] adopted" unique  - By HaneulCheong

## 3.19.14

By SomeTroglodyte:
- Sort maps & accelerate playing a newly edited map
- Fixed many minor UI bugs
- WLTK decorations
- Improve unique replacement for +/- amounts
- Locate Mod Errors - choose base ruleset to do complex check against
- Move automated units button was showing when it didn't do anything

Added deep link to multiplayer games  - By GGGuenni

Block Embarked from capturing Civilians on Water  - By itanasi

Translations update

## 3.19.13

Minor performance improvements

By SomeTroglodyte:
- Fixed Spaceship production bonuses
- Fixed Ctrl-Letter key bindings
- Fixed Polynesia's Wayfinding
- Fixed Petra and Garden not allowed in some cases

Align Embarked Defense Strength per Era  - By itanasi

## 3.19.12

Modding: Multiple Unique documentation improvement

By SomeTroglodyte:
- Slider fixes
- Rudimentary AI control over goldPercentConvertedToScience

Fix Navies capturing Land Civilians - By itanasi

Translation updates

## 3.19.11

Mod autoupdate improvements

Performance improvements

Added "copy to clipboard" button for mod errors

Multiple small improvements

By SomeTroglodyte:
- Resolved no-barbarian mod error
- Minimum window size

By xlenstra:
- Reorganized the way city states grant resources
- Made spaceship parts units instead of buildings
- Added mod constants for the distance between two cities

By itanasi:
- Updating Embarking Tutorial with more details
- Made multiple tile defense bonuses stack

## 3.19.10

Show json parsing errors for mods in the options menu

AI only builds work boats for buildable improvements

Trigger uniques by sacrificing units with conditional

By xlenstra:
- Damage in battle table is now the average damage done
- Added conditional checking for tiles

By itanasi:
- Show Improvements that are buildable after Removing TerrainFeature
- Embarking penalty logic fix
- Prevent Civilians from capturing Civilians

Fix for music resumes after minimizing on android - By SomeTroglodyte

## 3.19.9

Transported units reveal tiles as if they passed through the path of the transporting unit

Captured unit notifications now sent to the correct civ :)

Modding: Better unique typechecking and autoreplace, added new conditionals

Can see improvement removal icons in Civilopedia

Fixed untranslated texts, mainly in Civilopedia

Add Amphibious penalty to Land attacking into Water and vice versa - By itanasi

## 3.19.8

Framerate improvements

Performance improvements for wartime AI

Modding: Arbitrary uniques can become timed uniques with a special conditional!

Correctly recognize mod changes of content, not just metadata

Destroyed units on capture provide the correct notification

Loading a new game while nextTurn is running no longer reverts you to that game

## 3.19.7

Better conditionals for modding

Withdraw chances can stack

By itanasi:
- Sea Unit can't capture Land Civilian (and vice versa)
- Notify when Barbs don't give more XP

## 3.19.6

UI improvements across the board

Map performance improvements

Better mod loading error messages, added options button to reload all rulesets

Exploring and automating workers are some of the most common actions, they don't deserve to be behind a 'get additional actions' click

Flood plains no longer generate on desert hills

Also capture Civilian Unit when capturing during battle - By itanasi

Fixed a bug where units requiring nearby units for bonuses could find themselves - By xlenstra

## 3.19.5

HUGE reduction is memory consumption!

Performance improvements!

Greatly reduced loading times when mods are installed

Better terrain moddability + Added unique to convert terrains if adjacent to something

"Must be next to [terrainFilter]" now applicable on improvements

Added mod warnings for empty ally and friend bonuses

By xlenstra:
- Fixed a bug where stats from uniques would exponentially grow
- Fixed a bug where open borders, war declarations and cities could not be traded

## 3.19.4

Caught more mod failure conditions, removed certain assumptions from map creation

Unique replacement warnings show the correct replacement with filled parameters

By xlenstra:
- Fixed a bug where unit discounts would not work
- Fixed a crash when opening and closing the options menu in quick succession

Fix art for farms on hills  - By SpacedOutChicken

## 3.19.3

Huge performance improvements to "next turn"

Removed mod dependency on specific terrains and resources

By xlenstra:
- Resources can now again provide uniques applying to the entire civ
- Fixed a few rare mod-specific crashes

New caravel image - By touhidurrr

Minor logic cleanup  - By itanasi

## 3.19.2

Stat bonus drilldown in cities

Performance improvements

By will-ca:
- Try to fix potential typos in stock rulesets.
- Wiki improvements!
- Make sure units always have starting promotions.

By xlenstra:
- Fixed the problems with the food carry-over unique
- Made unhappiness effects moddable by adding a global uniques json
  added revolts when < -20 happiness
- Fixed a missing percentage sign in uniques

Ranged capture  - By itanasi

## 3.19.1

Better drilldown to stat sources in city screen

Start bias includes neighboring tiles for better effect

Cleaner tech order display

Better unique documentation - By xlenstra

By will-ca:
- Check rulesets for potential typos.
- Avoid potential crashes when deleting mods.
- Fix uneven fonts, unify font sizes.
- Make "Help" button clearer and translatable, random nation indicators and labels translatable.

Destroy Arsenal when city is captured  - By SpacedOutChicken

## 3.19.0

Vastly improved worker AI for mods, and AI utilization of workers

Added button to update an installed mod from its action menu

Converted all stat percent uniques to be iterated on efficiently once!

Fixed a conversion error in "% city strength from defensive buildings" unique - By xlenstra

## 3.18.19

Worker AI improvements for modded improvements and terrains

Performance improvements

Minor UI improvements

By will-ca:
- Unified icon button UI
- Added missing translation terms

## 3.18.18

"Cannot be built with" unique catches building equivalents as well

Unique deprecation and textual improvements

By will-ca:
- Explain when cities can't be razed in Civilopedia
- Solved "overlapping tiles" in modded tilesets
- Fix a tiny and limited memory leak

Made attacked civilians lose 40 hp as in civ5.  - By ravignir

Capturing Civilians not considered an Attack  - By itanasi

## 3.18.17

Handling for multiplayer download errors

Fixed fringe-case crashes

By xlenstra:
- Fixed a bug that occasionally placed hills on top of mountains
- Made all the other constants determining the strength of cities moddable
- Fixed a bug where citadels did not damage nearby units
- Updated the natural wonders for vanilla

Fix incorrect Archer obsolete  - By AdityaMH

## 3.18.16

Deprecation of requiredBuildingInAllCities

Removed support for stat-named specialists

Checks for parameter types of conditionals in mods

By xlenstra:
- Added a way to add moddable constants
- Fixed 'cannot built on [strategic resource]' not working
- Expanded the buildingFilter to include options for national wonders

More informative reports for crashes in threads - By will-ca

Fix missing siege unit resources needs for vanilla  - By AdityaMH

## 3.18.15

Detailed sources of battle modifiers

Performance improvements

By xlenstra:
- Disables '[cityState] is afraid of your military power' for spectators & other non-major civs
- Fixed a bug where great improvements could not be repaired after being pillaged
- Fixed border image alpha

Revert "Remove periodic saving again " (#5883) - By touhidurrr

Added `tileScale` in `TileSetConfig` - By will-ca

## 3.18.14

By will-ca:
- Unify and improve moddability of more tile-based images, including border images.
- Arrows in Default tileset.

By xlenstra:
- Added more yield icons to the city screen
- Reworked nukes again
- Updated uniques
- Fixed a bug with unit discount

Stop promoting units with 0 movement via the promotion screen - By yairm210

Delete unused image - By hundun000

Remove periodic saving again - By GGGuenni

Dispose object Graphics2D if it isn't used - By lishaoxia1985

## 3.18.13

Stat names also include the stat icon :)

Better map-to-ruleset incompatibility checks

'tile to expand to' choice incorporates city-specific bonuses

Fixed a ton of very rare, but crashing, edge-case bugs

By will-ca:
- Save attacks per civ for arrows for cities, missiles, dead units.
- Solved 'white blocks' on default tileset.

By xlenstra:
- Fixed a rare diplomacy voting bug in one-more-turn mode
- Fixed a few combat bugs and changed the religions founded label

## 3.18.12

AI cities now build workboats for use in other cities

Caught Out Of Memory error for large saved games

By will-ca:
- Show arrows on map for unit actions

By xlenstra:
- Added score and time victory
- Fixed a policy not working
- Fixed a bug where an empty improvement picker screen could open

## 3.18.11

Resolved mod dependencies leading to incompatible rulesets

By will-ca:
- Add new universal crash handlers and error reporting screen.
- Refactor MiniMapHolder's little green map overlay toggle icons.

By xlenstra:
- Extended use for "in [tileFilter] tiles" conditionals
- Replaced illegal / questionably legal assets
- Fixed an infinite loop where mod units could upgrade to the unit they replaced

Fixed warnings in linux desktop file - By touhidurrr

## 3.18.10

Sort Maintenance Using Fixed Point - By itanasi

Moved Coal discovery back to industrialization - By xlenstra

Improvements to TurnChecker data usage - By GGGuenni

AI for Inquisitor and Missionary - By Interdice

Add a couple missing template strings - By SimonCeder

## 3.18.9

Uniques and conditionals for translating are taken directly from the uniquetypes

Can gift improvements to city states also on water tiles / when other improvements have been built on the resource

Enabled code minify, hopefully shrinks apk size

Resolved crash when selecting resources in map editor

## 3.18.8

Solved bug that made civilian units uncapturable

Don't show 'fortify until healed' if the unit won't actually heal in this tile

By xlenstra:
- Band-aided a bug where players in multiplayer games were waiting for themselves.
- Fixed a crash that occasionally happened when liberating a city to a dead civ
- Added icon for telegraph
- Fixed the unique for giving sight to units no longer working

Regions part 3 - resource placement, resource settings  - By SimonCeder

## 3.18.7

Multiplayer game info is updated as each intermediate player finishes their turn

Solved crash - AIs ignore trade requests that have become invalid mid-turn

By xlenstra:
- Fixed a bug where 'requires a [buildingName] in this city' would not work
- Fixed a bug where WLTKD would continue after conquering/trading a city

Counteroffer fixes  - By SimonCeder

## 3.18.6

Fixed niche bug that let you try and capture civilians in territory you can't enter

New Recycling center building - By itanasi

By will-ca:
- Center Agriculture in Tech tree.
- Show which cities are missing required buildings for National Wonders.

Fixed a bug where statue of Zeus would not work  - By xlenstra

## 3.18.5

By SimonCeder:
- We Love The King Day
- Counteroffer mechanic, updated trade valuations

Added health conditionals  - By xlenstra

Make Guided Missile Free (and Maintenance overhaul)  - By itanasi

Stop putting Wonders as start buildings  - By SpacedOutChicken

Performance improvements!

Added autogenerated unique docs

Fixed terrace farms not improving with Civil Service and Fertilizer techs

Resolved edge case crash

Fixed bug when attempting to load a game that uses mods you don't have

## 3.18.4

Can safely convert maps between rulesets!

Policy screen keeps scroll position when adding new policy

Cannot add 2 of the same buildings to the queue visually

Viewable tiles update after capturing city

AI won't declare war at the very beginning of games for little reason

Exiting Civilopedia always brings you o the previous screen

## 3.18.3

Caught out of memory errors on autosave

By xlenstra:
- Updated vanilla policies and fixed a few oversights from G&K policies
- Cleaned religion from the Vanilla ruleset

## 3.18.2

Pre-solved potential bugs

Fixed tileset config conflicts between mods

Fixed crashing music bug

Caught out of memory errors when updating tiles with a catch-all popup

Performance improvements, should help mitigate existing ANRs

Resolved crashes in game options table when changing base ruleset before the mod list was defined

Fixed a crash when changing the base ruleset while in portrait mode - By xlenstra

## 3.18.1

Performance improvements

Resolved edge-case AI crashes

Solved map editor bug for rulesets without grassland

Regions part 2 - City state placements, start normalization  - By SimonCeder

By xlenstra:
- Split Vanilla & G&K rulesets
- Removed a wrongly implemented BNW-only building

## 3.18.0

Performance improvements!

Upgraded Desktop to new rendering methods, solving many existing problems

Can upgrade/promote units as per Civ V rules

By SimonCeder:
- Better map generation for civ placements
- Quest fixes
- can remove fallout on oases
- Bombard notification
- fix bug when city states bullied
- Encampments revealed by ruins effects have lastSeenImprovement updated

Possibly fixed a bug where replacement buildings would not be granted - By xlenstra

## 3.17.14

Large performance boosts

Solved ANRs caused by slow "quickstarts"

Fixed music download error

Show notification to cycle through visible resources when clicking on resource icon in Resource Overview.  - By will-ca

By xlenstra:
- Reworked buying buildings & units with stats a bit
- Fixed a bug where hagia sophia could be build in non-faith games

Made rich presence text not change with language  - By logicminimal

## 3.17.13

Show construction icons in Cities Overview.  - By will-ca

Remove Discord RPC checks for unsuitable devices  - By asda488

Allow unit movement after unit automation steps

By SimonCeder:
- Barbarian units
- Barbarian fixes

By SomeTroglodyte:
- Fix PercentProductionBuildings and PercentProductionWonders
- Minor hardening of music against OpenAL quirks

GameInfoPreview upload as Metadata  - By GGGuenni

## 3.17.12

Clarified Oil well / Refrigeration relation - By SomeTroglodyte

Improved multiplayer screen performance - By GGGuenni

By SimonCeder:
- Improve AI performance vs barbarians & AI settlers
- Conquistadors only settle other continents
- Fixed contest quests bug

By xlenstra:
- Religion UI improvements
- Fixed multiple faith bonuses from ruins
- Fixed unconventional great prophets not gaining wonder bonuses

Provide more information to waiting players in multiplayer - By thepianoboy

## 3.17.11

City construction speedup with caching stats from tiles

Fixed "[stats] from all [stat] buildings" check for stat relatedness

By xlenstra:
- Fixed Siam's unique applying multiple times
- Added missing unit type filter
- Enumified all remaining resource & improvement uniques
- Fixed a bug where AI would not found religions
- Fixed a bug where buying units with faith would not increase in cost

Inner Sea map type  - By SimonCeder

## 3.17.10

Global uniques from buildings register correctly for units

Solved multiple movement bugs

Performance improvements

By xlenstra:
- Band-aided a bug with building unique application
- Fixed belief increasing city-state influence resting point

By SimonCeder:
- Can now raze cities Austria has married
- Improvements for fog of war

Added resource tech requirement to tile info - By p-bystritsky

## 3.17.9

By xlenstra:
- Fixed bug where denmark's pillaging unique doesn't work
- Implemented holy warriors follower belief
- Fixes bug where culture gain from killing units no longer works
- Choose a better visible color for the religious symbol on the city button

City State Barbarian Invasion and War with Major pseudo-quests  - By SimonCeder

Music pause on "leave game" question, not world screen menu  - By SomeTroglodyte

## 3.17.8

By avdstaaij:
- Fixed the visual gaps in territory borders
- Fixed the base cost of the Grand Temple

Strength bonuses apply from civ bonuses as well

By SimonCeder:
- Disable religious quest with religion disabled
- Variable resource quantities
- Free buildings are truly free

By xlenstra:
- Fixed a bug where "[+amount] population [in this city]" did not work
- Fixed bugs with diplomatic victory
- Free buildings provided in other cities are also free
- Fixed crashes from era

## 3.17.7

Mayas nation - By SomeTroglodyte

Better AI choice for constructing carriers

By xlenstra:
- Solved bugs with unit movement through fog of war
- Fixed multiple small bugs
- Made it impossible to cut short peace treaties
- Fixed a bug where religious units would be expelled when an open borders agreement ended
- Fixed bug with byzantine unique

Civ icon redirects to civilopedia  - By logicminimal

By SimonCeder:
- Return Civilians captured by Barbarians to original owner
- cs units wander

## 3.17.6

Considerable optimizations for "next turn" performance

Unit maintenance discount corrected

Fixed conditionals display when locating mod errors, which ws broken due to translation reordering all conditional-like text

Fixed crash when AI is picking religions  - By xlenstra

Minimum city distance across continents  - By SomeTroglodyte

Fix Educated Elite  - By SimonCeder

## 3.17.5

Better unique and mod checking

By SimonCeder:
- Barbarians capture civilians and take gold from cities
- AI rationing of strategic resources; Hydro Plant re-enabled
- prevent city states from taunting you

By xlenstra:
- Fixed Byzantine not applying
- Advanced AI choosing of beliefs
- Combat uniques converted to conditionals
- Nations now have a favoured religion

By SomeTroglodyte:
- Added Ethiopia Nation
- New map for map editor shares settings storage

## 3.17.4

By SimonCeder:
- Barbarian spawning and camp placements
- Fixed Fountain of Youth

By xlenstra:
- Added default values for supply to fix almost all mods being broken
- Fixed bugs with pentagon, "mandate of heaven", and fallout
- Added icons to resource trades & war declarations

By SomeTroglodyte:
- The Celtic People Rebooted
- Music moddability and visual improvements

Add looping minimap viewport if worldwrap enabled  - By Thyrum

## 3.17.3

Added game name to turn notification  - By GGGuenni

By xlenstra:
- Added the Byzantine empire
- First step into unifying strength bonuses using conditionals

By SimonCeder:
- Fix Polynesian vision when embarked
- Proper great general points

By SomeTroglodyte:
- Civilopedia category icons & keyboard navigation
- Allow Deciv Redux start with >0 City States
- Fix era of Wonders without tech in Wonder overview
- Double movement unique parameterized

Change NationIcon for 4 nation  - By AdityaMH

## 3.17.2

Warn modders for uniques located on the wrong objects, and usages of deprecated fields

By SomeTroglodyte:
- Mini-UI to see Religion info on foreign cities
- World and Natural Wonders Overview
- Natural Wonders un-hardcoded
- Texture pack/load for mods also distributes by Images.

By SimonCeder:
- Quests fixes and additions
- Can't trade resources from other trades or city-states

Fix missed sound for Sea Beggar  - By AdityaMH

## 3.17.1

We now find and report deprecated uniques in the command line!

Resolved crash from unit civilopedia lines

Music controller with fade-over and mod capabilities - By SomeTroglodyte

By xlenstra:
- Added support for conditionals to some more uniques
- Save the sources of uniques with the uniques themselves
- Fixed bug where a unit auto-exploring ancient ruins would in some cases disappear after upgrading

## 3.17.0

Type-checking for Unique parameters in mods, basis for new Unique management

By xlenstra:
- Many bugfixes
- Added "conditionals" to increase unique moddability
- Embarked units only have 1 vision (except marines)

By SomeTroglodyte:
- Better nation picker UI
- Ask before resetting game setup to defaults
- Modmanager sort and filter

Implemented Unit Supply - By r3versi

By SimonCeder:
- Carthage civ
- Bugfixes

## 3.16.15

City-States can become wary of civs - By SimonCeder

Added Religious city states - By Interdice

By xlenstra:
- Made CN tower functional, and free buildings are removed on capture
- Updated piety policy tree
- Added "Consumes [amount] [resource]" for improvements

By SomeTroglodyte:
- Panzer can upgrade according to fandom wiki
- Handle maps with invalid mapSize more gracefully

By ravignir:
- Add missing Natural Wonders
- Made camps buildable on jungles.

## 3.16.14

Fixed crashes when mod had religion but no great prophet

Can no longer fast-tap to confuse policy/construction screens

By xlenstra:
- Fixed some bugs
- Fix Civil Society policy
- Wrote an extensive tutorial documenting most of religion

By SimonCeder:
- Proper pledge to protect implementation
- Unique units from Militaristic city states
- Icons for city states

By SomeTroglodyte:
- Fixed crash with no-barbarian mods
- Fix era notification
- Mod manager portrait and auto scroll

## 3.16.13

By xlenstra:
- From the industrial era onwards, things change in religion
- Fixed bugs with unit movement, renamed units, and default starting era
- Unique additions for modding

By SomeTroglodyte:
- New game is more wrap and shape aware
- No right-click on Android
- Code cleanup

By SimonCeder:
- CS vulnerable to ally unhappiness
- checks for protection, delays
- Force ranking, bullying improvements
- City state intrusion anger

## 3.16.12

Can no longer enter city-screen that is not yours

Spectator cannot take over player diplomacy options

Better check for units with no unitType defined

Fixed crash where deleting mods meant you could never start a game again

By xlenstra:
- You can now input distinct numbers when trading gold
- Fixed religion bugs

By SimonCeder:
- Correct year shown when starting in later eras

By SomeTroglodyte
- Options displays well for portrait mode
- Fixed rare map generation crash

## 3.16.11

Resolved crash due to evaluating distance to city state when we have no cities

By xlenstra:
- Great Prophets now always have your religion as their religion
- Implemented renaming of religions
- Finishing the later five policy trees now allows you to buy great people with faith
- Added Religious wonders
- Fixed bug making enhancing religions impossible
- Added UI to show what cities are holy cities to the player

By SimonCeder:
- fix duplicated city-state bonus bug

## 3.16.10

By SimonCeder:
- Add Austrian civ
- units get promotions and xp bonuses from CS buildings
- Demanding tribute from city states

By xlenstra:
- AI will now found & enhance religions - minor improvement to civilian AI
- Fixed ambush bonus amount

By SomeTroglodyte:
- Diplomacy Screen Nation relation indicator
- Persistent new game setup
- Anti-Armor, negative tile yield, LoadScreen

For modders: Mass unique deprecation

## 3.16.9

By xlenstra:
- Implemented the enhancing of religions
- Submarines are now visible to adjacent units, and once turned visible, can be attacked by all enemy units

By SomeTroglodyte:
- Multiple Civilopedia improvements
- Better mod problem detection

## 3.16.8

Upload APK files to Github release

Can now play as 2 separate Civs with the same userId

Fixed Krepost unique

Conquering a city destroys buildings inside the city  - By xlenstra

By SomeTroglodyte:
- Starting locations reworked
- Stat Icons Redone

## 3.16.7

By xlenstra:
- Implemented Inquisitors
- Implemented a cap for the production boost of great engineers
- Scouts still ignore terrain costs after upgrades
- Fixes bug where upgrading units would no longer provide their default upgrades

By SomeTroglodyte:
- City keyboard buy construction and tile
- getLastTerrain simple patch
- StartingLocation-Improvements-be-gone phase 1

Can now raze non-original capitals at capture  - By SimonCeder

## 3.16.6

City-states grant copies of ALL resources

By SomeTroglodyte:
- Fixed multiple crashing errors
- Ancient Ruins Civilopedia and Translations
- WorkerAutomation cached per Civ - BFS cached

Made great people and boats uncapturable  - By logicminimal

Added Bucketeer unit images - By AdityaMH

By xlenstra:
- Added a UI for viewing the religions inside a city
- Implemented almost all missing founder & follower beliefs

## 3.16.5-googlePlayPushTest

A test to ensure that publishing new versions to Google Play works properly

## 3.16.5

By SomeTroglodyte:
- Expander tab persist
- UI improvements for city screen
- Unit action constants and worker unique cleanup

By avdstaaij:
- Removed the civ introduction trade option
- Made water oil wells require the Refrigeration tech
- Removed the sight bonus from hills
- Gave anti-air units a bonus vs helicopters
- Disabled pillaging your own tiles

Fixes crashes from loading mods without an eras.json file - xlenstra

CS bonuses graded according to relationship level  - By SimonCeder

Improve horse sound  - By AdityaMH

## 3.16.4-patch1

Fixed crash from conquering cities - By xlenstra

Fix DOS attack perpetrated by CityInfoReligionManager on Json Serializer - By SomeTroglodyte

## 3.16.4

Implemented Zone of Control mechanic - by avdstaaij

Runtime optimizations - By SomeTroglodyte

Implemented religious pressure - By xlenstra

Fix units not entering cities upon capture - By avdstaaij

## 3.16.3

By SomeTroglodyte:
- Civilopedia - Difficulty
- City screen stats double separators
- Unit rename UI

By xlenstra:
- Added founder beliefs, updates to pantheon spreading
- Added an overview screen for religions

Resources changed to match civ5 G&K - By ravignir

Add Holy Site for FantasyHex  - By AdityaMH

## 3.16.2-patch1

Fixed diplomacy screen crash for city-states with no cities

Added mod check for units whose unitType is not defined

Fixed crash when selecting certain buildings in the civilopedia - by xlenstra

## 3.16.2

Fixed crashing Diplomatic victory bug

By xlenstra:
- Added follower beliefs for buying religious buildings
- Hides 'automate' unit action and unhides 'stop exploring' unit action
- Ruins now have their own file
- Architecture is now a prerequiste of Archaeology
- Fixed bug where units could still be purchased if they used a depleted resource
- Fixed crash when borrowing names
- Fixes bug where Russia's unique no longer works
- One with nature yield for spain is now doubled

By SomeTroglodyte:
- MapGenerator optimization
- Diplomacy: City State resource UI, improvement gift effect
- Mod description translation

Pikeman upgrades only to Lancer  - By ravignir

Fix banking's required techs  - By logicminimal

City state resources  - By Interdice

## 3.16.1

By xlenstra:
- Added missionairy units, which can spread religion and bought with faith
- Replaced the last promotion effects with uniques
- Removed $ signs from translatable strings

By SomeTroglodyte:
- Bring `allUnitActionsHaveTranslation` test up to date
- Change defeat conditions

By ravignir:
- Minor fix to Great Prophets cost not increasing

## 3.16.0-patch1

Bugfixes from unitTypes so promotions work again  - By xlenstra

By SomeTroglodyte:
- Allow civ-unique buildings to be created by startingEra
- Mod manager concurrency

Randomize Plains/Grasslands around deserts  - By ravignir

## 3.16.0

By xlenstra:
- Added Diplomatic victory!
- Unit types are now moddable!

Bugfixes

Atomic bomb interception works as intended

Ai now cares about city distances   - By Interdice

By SomeTroglodyte:
- Civilopedia phase 9 - Technologies
- Harden map editor map loader against most bad maps
- UI improvements

General fixes - By lishaoxia1985

## 3.15.18

600th version!

Solved crash where city states would try to gift great people without cities

By avdstaaij:
- Fixed captured units not tp-ing out of illegal tiles

By SomeTroglodyte:
- TranslationFileWriter support for CivilopediaText
- Fix Civilopedia Unique auto-linking when Ruleset changes
- Reduce atlas - The Huns was 4x larger than the other nations, and a dirty Hexagon dupe
- Better crude maps - zero uncovered tiles impossible

By xlenstra:
- Added modoptions unique for disabling city-state spawning with only a settler
- Fixed bug where production from cutting down forests could apply to perpetual constructions

Improve River for FantasyHex  - By AdityaMH

Fix Hagia Sophia and CN Tower not giving civ 5 bonuses  - By logicminimal

## 3.15.17

Influence-by-game-progress works as intended

One-city-challengers no longer get multiple settlers for later eras

Maori Warrior debuff only applies to enemy units

AI accepts research agreement offers

Buildings from era are applied before buildings from policies

By SomeTroglodyte:
- Civilopedia phase 8 - Nations and Promotions
- UnitActionType now knows keys, sounds and most icons

And new unit pixel and some improvement - By AdityaMH

Map climate overhaul - By ravignir

## 3.15.16

City states no longer grant Great Prophets when religion is not enabled

Background work for moddable uit types :)

Mercantile CS resources - By SimonCeder

Civilopedia phase7 - By SomeTroglodyte

Petra as in G&K fix - By ravignir

## 3.15.15

Great Person points are now moddable!

By SomeTroglodyte:
- Minimap Slider UI
- Unit name translation
- Rename Railroad tech to Railroads
- Fix canImprovementBeBuiltHere regression
- Newgame screen overhaul for portrait mode
- Deprecate "Can only be built on coastal tiles" unique
- A Civilopedia category for Religion

By SimonCeder:
- Optimized spawn placement algorithm
- City states adjustments

Resolved #4394 - corrected misspelled city names

## 3.15.14

By xlenstra:
- Fixed bug where "[All] units" would not apply to city combatants
- Fixed comodification errors under certain circumstances
- Fixed a crash in badly defined mods
- Implemented temples
- Hide lesser used action buttons to free up space
- Fixed bug where 'remove road' would also remove other improvements under specific circumstances
- Fixed units not being removed from open borders area after declaring war
- Fixed bug where all great people suddenly were scientists
- Fix autocracy bonus accidentally being disabled

By SomeTroglodyte:
- Resolve #4589
- Spruced up Civilopedia - phase 6 - uniques
- City construction Civilopedia-linked

By SimonCeder:
- Map generation and start locations
- Added Sweden Civ

Performance boost - should resolve some ANRs

## 3.15.13

By SomeTroglodyte:
- Fix for missing Farm images
- Better keyboard shortcuts
- Rewritten Tooltip class
- Sort Civilopedia entries using locale
- Spruced up Civilopedia - phase 5 - buildings

By xlenstra:
- Implemented Follower beliefs for religions
- Fixed unique of Persian immortal not working

Unit gifting - By SimonCeder

Added Polder image

## 3.15.12

By xlenstra:
- Founding Religions
- Updated the tile choosing algorithm for city expansion
- Disabled city state diplomacy buttons when it is not your turn
- Fixed bug where great prophets could be given when religion was disabled
- Fixed bug where effects of all aquaducts nationwide stacked in each city

Wonder build screens redux - By SimonCeder

By SomeTroglodyte:
- Better Slider UI
- 'Swap units' sound replaced

## 3.15.11

Civs with no cities can no longer pick policies

Spectator no longer appears on Diplomacy overview

By xlenstra:
- Fixed crashes on loading save games with religion
- Fixed bug where submarines could not attack embarked units
- Fixed bug where tile construction time was increased instead of decreased

By lishaoxia1985:
- Fix worldSizeModifier in TechManager
- Make map symmetrical if it's not wrapped

Fix Hun city names - By freddyhayward

## 3.15.10

Automated atomic bombs no longer cause crashes

Fix for placeholder parameters changing names and becoming out of sync with existing translations.

By xlenstra:
- Add missing pantheons
- City states give gold when met; updates to city state gold gifts
- Fixed many bugs
- City Centers can no longer be removed by nukes
- Added a simplified version of great prophets, implemented a basic city religion UI
- Updated TranslationFileWriter to include the new values that filters can have
- Fixed a bug where one city challengers could capture enemy cities
- Refactored the way cities determine what uniques should apply when

Fixed spurious notifications of revealed resources too far away or in foreign territory  - By freddyhayward

Quick salvage of some lost translations  - By SomeTroglodyte

## 3.15.9

By SomeTroglodyte:
- Better mod download and error display
- Spruced up Civilopedia - phase 4 - Visual candy, Units
- 'Swap units' sound and more attack sounds
- Unified separators, CheckBox helper
- ExpanderTab UI update

By xlenstra:
- Fixed crash when a city had negative population due to faster razing
- Fixed bug where logistics _still_ did not work
- Fixed bug where city-states would not share their science income even if the player had the right policy
- Added the nation of the Netherlands

Fixed Denmark's unique

More concurrency problem fixes in nuke effects

## 3.15.8

By xlenstra:
- Made eras more moddable
- Updated and generalized more promotions
- Added Privateer unit; updated Coastal Raider promotion

By SomeTroglodyte:
- Fix mod custom maps unavailable when no local ones exist
- Spruced up Civilopedia - phase 3 - Interface, flavour text, new Tutorial

## 3.15.7-announcementTest

I'm checking if this information gets to the Github release and the Discord announcements

## 3.15.7

Resolved 'getting stuck when there are no more pickable Pantheon beliefs'

Removed final vestiges of old Bonus/Penalty effects.

By xlenstra:
- Fixed bug where all units could move after attacking
- Fix a few bugs related to nukes
- Research Tech Button shows progress; Small bug fix
- Updated promotions - make more generalizable, update to G&K

By SomeTroglodyte:
- Translate nested placeholders for English
- Fixed sound problems on Android

## 3.15.6

Faster 'false' results for isStats, as proposed by @SomeTroglodyte  in #4259

By SomeTroglodyte:
- Hide notifications for incompatible policy branches
- Nicer distribution of policy picker branches
- Fix "National Wonder is being built elsewhere" not displayed
- Respect visualMods for Sound - CheckBox, formats, modchange detect

By xlenstra:
- Added Shrine, option for enabling religion
- Added Nuclear Submarines & Missile Cruisers, capable of transporting missiles
- Fixed crash when selecting worker
- Fixed bug where on quick game speed, educated elite would yield a great person every turn

By avdstaaij:
- Added Stealth tech and Stealth Bombers
- Added Drama and Poetry tech and replaced Temples with Amphitheaters

## 3.15.5

Solved Discord RPC not crashing devices in which it is unsupported

Resolved #4200 - Cities in resistance cannot bombard

More generic "gain stat" for some uniques

By xlenstra:
- Generalized building of improvements
- Added Telecommunications tech
- Added Advanced Ballistics Tech, Atomic Bomb Unit, Updated how nukes work
- Fixed rare bug where building improvements would increase tile base yield

Promotion picker keeps vertical scroll pos on promote or resize  - By SomeTroglodyte

## 3.15.4

Deprecated 'download map' in favor of mod-based map sharing

By xlenstra:
- Created Patronage policy branch
- Fixed a bug where excess food would not be converted to production for settlers

By avdstaaij:
- Added Nuclear Fusion tech and Giant Death Robot
- Fixed open terrain bonus working in rough terrain
- Fixed captured units not tp-ing out of liberated cities
- Fixed naval units not tp-ing out of razed cities

Split off all individual OverviewScreen panes  - By SomeTroglodyte

## 3.15.3

By xlenstra:
- Created Order branch with G&K policies
- Fixed bug where coastal buildings cannot be built
- Stop AI from pillaging their own tiles. Fixes #4203
- Fixed autocracy complete bonus not continuing after updating

Corrected many building production costs, tech requirements and wonder effects  - By avdstaaij

Change improvement key indicators to tooltip - By SomeTroglodyte

Resolved #4209 - AI city-founding no longer plays music

## 3.15.2

By xlenstra:
- Updated Autocracy and Freedom branches to G&K
- Fixed honor policy not adding bonus vs barbarians
- Fixed old worker speed improvement uniques no longer working

Wake up units when enemy sighted or displaced or attacked  - By SomeTroglodyte

By avdstaaij:
- Made atlas textures use mipmaps again
- Fixed units not teleporting out of sold city tiles

Resolved #4170 - updated deprecated Polynesian unique - By SpacedOutChicken

Unitfilter now accepts multiple filters (see wiki/uniques for details)

## 3.15.1

By xlenstra:
- Updated the culture victory so it now requires the Utopia Project to be built
- Updated Commerce and Commerce branches to G&K

By SomeTroglodyte:
- Show promises not to settle
- Shortcut tooltips indicators

Map RNG reproducibility fix - By r3versi

## 3.15.0

Updated Tradition, Honor and Liberty branches to G&K rules - By xlenstra

Enabled various G&K buildings and units - by By avdstaaij

Nation start intros - By SomeTroglodyte

By r3versi:
- New borders images
- Display Movement Paths on map
- Unified Menu Popups

Better button images - By lishaoxia1985

## 3.14.16

Cities in resistance cannot bombard, as per Civ V - #663

By SomeTroglodyte:
- Hopefully fixed F-Droid missing libgdx.so problem
- Trade UI improvements - Leader portraits, keys, layout
- Nations spellchecking

Implemented production overflow - By Thyrum

By r3versi:
- Map Generation Seedable
- Map Generation Fixes and Tweaks

## 3.14.15

Unit swapping - By avdstaaij

Refund wasted production as gold  - By Thyrum

By SomeTroglodyte:
- More power to improvement uniques
- Fix gold able to over- and underflow
- Map editor save / load / download keys
- TileInfoTable translation and padding

Paratrooper bugfixes - By xlenstra

## 3.14.14

Fixed app resize crash in MacOS - By lishaoxia1985

Added the paratrooper unit - By xlenstra

Add global alerts for certain constructions  - By avdstaaij

Temple of Artemis bonuses reflect civ5 behavior  - By ravignir

Resolved #3967 - City-states can no longer 'gift' you your unique units

Resolved #3926 - Wheat+Farm gets location-appropriate farm

Keyboard shortcut order with multiple popups  - By SomeTroglodyte


## 3.14.13

New Swedish translations!

City construction queue: Subsequent units no longer displays construction progress towards first unit of its kind

By SomeTroglodyte:
- Sound upgrade - enabled custom unit attack sounds
- Spruced up ModManagementScreen
- Patch ModManager exit to allow deactivating a selected tileset
- Spruced up Civilopedia - phase 2 - external links

Declare & Revoke protection for city-states - By ninjatao

Added Marine unit and Amphibious promotion - By xlenstra

## 3.14.12

Added unit action icons on map units, so it'll be immediately obvious what each unit is doing

Added city strength label to city button

City-states get all civs known by at least half of major civs, not 'by any civ'

Better camera square image

By SomeTroglodyte:
- Added key shortcuts to popups
- Improved location notifications

## 3.14.11

AI much less motivated to attack city-states

Better peace agreement evaluations, for when there is no great power imbalance between nations

Minimap slider has better values to accommodate screen sizes

Better "in this city" unique filtering

Added unit icons in unit overview screen

Changes to terrain combat bonuses - By ravignir

## 3.14.10

Unified "progress bars can't go beyond 100%"

By SomeTroglodyte:
- Follow screen rotation even to Portrait on Android with Opt-in
- Fix custom map sizes - saves match, size obeyed, limit UI

Add guided missile which acts differently from nuclear missile. - By ninjatao

Fix spaceship part production boosts - By avdstaaij

## 3.14.9

Caught exception when map fails to load

By SomeTroglodyte:

- Fix Ctrl-S going to save game screen will not stop scrolling the map
- Options screen cleanup
- Split off stuff from CameraStageBaseScreen that isn't the class itself
- Minimap resizeable and scroll position indicator redone
- Trim down custom save to export/import only
- Fix crash when a mod allows a citadel >1 tile outside borders

Technology and construction bars no longer extend past their maximum - By xlenstra

## 3.14.8

New line-of-sight rules, with new "Blocks line-of-sight from tiles at same elevation" unique!

More FantasyHex combinations (both mine and GGGuenni's)

Fixed terrace farm's 'fresh water' bonus - kudos @1.7.4

By GGGuenni:
- Fixed inconsistent map size
- Fixed Forest on Hill visibility

By SomeTroglodyte:
- Accelerate Load Game Screen Info
- Save game UI patch

Translations update

## 3.14.7

Custom map size  - By alkorolyov

Fixed Civilopedia crash

Fixed double consumption of resources for "Comsumes [amount] [resource]" unique

Resolved #3888 - added template lines for mod management screen

Tradition works again

Terrain height now defined by a unique

Refixed denunciation effects - sorry Chek!

Genericized "No Maintenance costs for improvements in [] tiles", tile city-strength bonus, and extra sight for unit types

Show tech progress for next turn in tech button

By SomeTroglodyte:
- Resource revealed notification point to all reveals
- Worldscreen key bindings
- City overview force consistent row height

## 3.14.6

Hills are now changed to terrain features - by GGGuenni!

Fixed denouncement effects on third-party civs, trade evaluation, and decay to diplomatic denunciation - kudos @Chek

Resolved #3865 - Kudos @SomeTroglodyte

Great general unique no longer restricted to civilian units

By SomeTroglodyte:
- Overview screen category decoration + key hint
- Fixed shortcuts for improvements
- Option Screen choices visible on minimap toggle buttons immediately

## 3.14.5

New tile layering is live for all users!

Tile options in map editor screen no longer 'click' on tiles behind them

Added construction production info to city screen

Specify original owner when showing "Liberate city"

Added "Self-destructs when attacking" unique

By SomeTroglodyte:
- City expansion notification points to acquired tile
- Visual improvements for the City Overview

Fix screen bugs when you don't use splitpane in pickscreen  - By lishaoxia1985

## 3.14.4

Resolved #3524 - Happiness in city overview now calculated correctly

Added Happiness (and Faith for Religion mods) to stats list - #3524

Resolved #3837 - Harad -> Harald in Denmark greeting

By SomeTroglodyte:

- Keyboard navigation to switch panes within overview screen
- Some visual improvements for the Mod Manager Screen
- Bigger target on "next city" button
- Fixed #3729 "Android crash on loading from custom location"

By GGGuenni:
- Added fogOfWarColor and unexploredTileColor
- Fixed internal TileSetConfigs not getting loaded on android

## 3.14.3

Added 'update time', 'open Github page', and marked updated mods, in mod management screen

Cannot enter diplomacy screen for irrelevant civs through diplomacy overview

Resolved #3817 - don't display resource requirements twice

By SomeTroglodyte:

- Mod translations now appear in new game screen
- Citadel tiles don't attach to razing cities if possible
- City center now unpillagable

Parameterize civ-wide sight bonus - By SpacedOutChicken

Translation updates

## 3.14.2

Put world wrap behind setting again and added warning for world wrap for Android - I'm seeing a lot of ANRs in recent versions, but it's not something that seems solvable.

Fixed - 'Water units' now can be capitalized, as they should be.

"[] from every []" can accomodate specialist names

Fixed unitType parameters in changed unique

## 3.14.1

Natural wonders are standalone tiles in new layering

Modding:

- Rough terrain specified through uniques
- Added check to remove clutter in tech trees
- Added building-maintenance-decreasing unique
- Can handle unique capital indicators in mods
- Added "Friendly Land" and "Foreign Land" as tile filter options

By SomeTroglodyte:
- Fix Citadel not buildable where it should be

By GGGuenni:
- Fixed Multiplayer bugs
- Preparation for hill as terrain feature
- Added TileSetConfigs

## 3.14.0

World wrap is publicly released!

Added empty hexagon when none of the images exist - this fixes the default tileset for the new rendering method

Manhattan project is not disabled for no-nuclear-weapon games

Behind-the-scenes work on Religion

## 3.13.13

Resolved #3753 - Fallout is no longer added multiple times

ALL tile images now support era-specific images! But only if the base tile exists as well.

Added new experimental tile layering, including new tileset - see #3716

Can now handle mods with any default branch name!

Added Faith icon and display for games with Religion

Redraw CivilopediaScreen - By lishaoxia1985

Fixed roads not getting wrapped correctly - By GGGuenni

## 3.13.12

Added Bulgarian, by antonpetrov145!

HUGE memory savings (120MB -> 75MB) By saving atlases between ruleset resets!

Start of work on Religion!

Resolved #3740 - units retain individual names when upgrading

Fixed can't press nextTurn in multiplayer game - By GGGuenni

'Years of peace' modifier reset when war is declared

Added mod pagination - even when we exceed 100 mods, we'll be able to download them all

Button for current civilopedia entry is now marked

Modded "Remove" commands to nonexistant features no longer crashes the game

AI no longer tries to attack with carriers, crashing the game.

National wonder does not require building to be built in puppeted cities

City attack notifications show icon

Custom improvements for water resources now moddable

## 3.13.11

Resolved #3732 - Mark target tile while moving toward it

Resolved #3734 - "Loading" popup when loading game remains until game is fully loaded

Resolved #3735 - The civ launching - not receiving - the nuke is considered the civ that declared war

Resolved #3721 - Fixed edge-case "images are temporarily applied from mods set in other places"

Resolved #3722 - fixed resistance icon display in notifications

Carriers cannot attack - By lishaoxia1985

## 3.13.10

Converted all color-coding of notifications to multi-icon notifications

Resolved #3713 - Fixed misspelled "fresh water" in farm unique check

Barbarians only heal by pillaging, simplified barbarian automation

## 3.13.9

Added "permanent visual mods" option to mod management

Resolved #3614 - tileFilter works with natural wonders

More uniform Trade overview

Resolved #3705 - loading game popup stays until the game is loaded

Added "Provides yield without assigned population" uniques to tile improvements

Can now add leader portrait images to mods

Unit name is translated when unit has a unique name

By GGGuenni:
- More terrainFeature refactoring
- Toast popup not screen blocking

## 3.13.8

Resolved #3401, #3598, #3643 - game can be instantly closed and reopened on Android

Remove tile improvements not in the current ruleset on normalizeTile

Resolved #3686 - world wrapped 'continents' map now separates continents properly

Resolved #3691 - 'new map' from map editor copies existing map parameters

Game no longer crashes due to incorrect building-improvement modding

UI improvements  - By lishaoxia1985

Fixed Military Caste policy  - By absolutebull

## 3.13.7

Fixed Great Barrier Reef spawn rules

Resolved #3681 - translation fix, "in every city" -> "in all cities"

Mods with no techs should work again

Resolved #3663 - fixed settler automation bug

Added titles to mod management screen

Withdraw before melee is the same as original game - By lishaoxia1985

Initial checks for 'multiple terrain feature support' by GGGuenni

## 3.13.6

Can convert maps between different rulesets!

Resolved #3635 - can remove natural wonders and roads in map editor

Fixed hurry cost modifiers, and unit gold costs scaling by game speed

Resolved #3651 - "Free great person" unique compatible with techs and Spectator

Resolved #3653 - Settler AI no longer aims for far tiles

Tech info can display more than one revealed resource

"Obsolete with [techName]" unique works with improvements

## 3.13.5

Experimental World Wrap - By GGGuenni!

Resolved #3639 - City-States that can't be connected by land no longer assign road connection quests

Fix when Trade Gold = 0 it also shows in offer - By lishaoxia1985

Translation updates

## 3.13.4

Merged save map functionality into load map screen - map saving is more streamlined!

Resolved #3626 - Can rename multiplayer games

Resolved #3622 - Can no longer try to send air units into unexplored tiles

Strength bonus from capital is now part of the Palace bonuses to make it moddable

Allow unit rename on promote - by David Howard

Translations update

## 3.13.3

Map generation parameters are moddable, allowing players to create custom terrains for map generation!

Custom mods with no water or grassland can now work!

Changed "Gold" resource to be called "Gold Ore" and "Siege" promotion to "Besiege" to not conflict with the yield type for translations - #2458

Maps incompatible with ruleset no longer popup errors when generating a new map

All resource stats from buildings converted to building uniques

Translation updates

## 3.13.2

Resolved #3601 - selected current tech no longer looks like unresearchable tech

Resolved #3610 - city sorting in overview is now done by translated, not original, name

Resolved #3586 - Added 'Destroy' translation for capturing cities in one-city challenge

Cleaned up map editor

tileFilter now works with resources for most uniques!

Added mod check for 'provides free building' which does not exist

Translation updates

## 3.13.1

Resolved #3600 - multiplayer game reloading and screen resize no longer reset map zoom and position

Resolved #3495 - Added scrollbars to civilopedia and picker screens

Map editor knows to remove resources that don't exist in mods

Can now create as many tech rows as you wish in mods :)

Display scroll position on minimap - By devbeutler

Translation updates

## 3.13.0

Mod-specific maps are go! :D

Movement algorithm updated - can no longer see whether you can move to unknown tiles

Solved "AI doesn't declare war" bug

Removed Scenario Maps entirely - was not a well thought-out concept and caused more confusion than actual fun.

Better tech descriptions for increased improvement stats

## 3.12.14

Catch for bug in new movement algorithm when you can't move to a unknown tile, but CAN pass through (but not move to) intermediate tiles.

Units manually moved cancel existing move action

Now get up to 100 mods in mod list (up from 30) - kudos @ravignir for noticing there were missing mods!

Fixed queue showing "Consumes 1" when no resource is consumed

Updated to latest LibGDX and Kotlin versions

Game saves can now always be deleted - By GGGuenni

## 3.12.13

Added "Consumes [amount] [resource]" unique to units and buildings

Hopefully mitigated some weird crashes and concurrency problems

Game can handle mods removing tile features between versions

Solved "quantum tunneling" bug for new movement algorithm

Added custom victory conditions

New cityFilter for cities connected to capital

Deprecated old uniques

Resolved #3578 - More readable colors for Korea civ, kudos @ravignir

City-state allies are always considered to have open borders

AI uses same calculation for declaring both war and peace - so it won't declare war only to immediately declare peace.

Scroll indicators are displayed more consistently on the NewGameScreen - By devbeutler

## 3.12.12

Added "Irremovable" unique to tile improvements

Added Unsellable unique to buildings

Added improvement-constructing buildings

Added mutually exclusive tech paths using "Incompatible with [otherTech]" unique for techs

Translation updates for new cityFilter

By GGGuenni:
- Refactoring of MultiplayerScreen
- Avoid overflow of the construction bar
- Adding resign function for multiplayer

By SpacedOutChicken:
- Add "Land" as possible input to tile-related uniques
- New uniques for border expansion - "-[]% Gold cost of acquiring tiles []" and "-[]% Culture cost of acquiring tiles []"

## 3.12.11

Added experimental movement which assumes unknown tiles are impassible - hopefully will resolve #3009

Solved mod incompatibility with Legalism issues

Multiple unique parametrization improvements

Added 'replacementTextForUniques' parameter to buildings and units for custom text

Add a "Unlocked at [tech/era/policy]" unique to buildings and units

"Save game" errors are now correctly caught and displayed to the user

Added mutually exclusive policy branches :)

Parameterize Civ Unique for increased XP gain - By SpacedOutChicken

## 3.12.10

Solved ANRs when loading large maps in map editor

Mitigated some concurrency related crashes

Resolved #3436 - parametrized "+[]% [] in all cities"

Smoother map panning

Multiple game support for TurnChecker - By GGGuenni

Avoid overflow of the health bar in the overkill situation - By JackRainy

Translation updates

## 3.12.9

Solved common ANR in city screen

Can handle and detect mods where the requiredBuildingInAllCities does not exist in the ruleset

Made some memory errors clearer to the user

Problems when saving game are now user-visible

Resolved #3533 - Added confirmation when saving over existing save

Fixed "Unique GP available from GP picker screen" bug

Resolved #3526 - stats drilldown remains when moving between cities

Buildings not displayed in civilopedia are not show to be obsoleted in tech tree

Improved MultiplayerScreen loading speed - By GGGuenni

## 3.12.8

Game can handle policies and ongoing constructions "disappearing" between mod versions

Resolved #3520 - picker screens go back on Back button / Esc

Resolved #3519 - Added 'exit game from back button' to main menu screen

Removed placeholder science and production boost from Computers tech

Resolved #3503 - Civilopedia and Overview show what 'panel' you're currently on

Resolved #3501 - Added city-state toggle to diplomacy overview, clicking on civ names in overview opens diplomacy screen

Better default tile colors - By ravingir

## 3.12.7

Resolved #3473 - show city's happiness drilldown

Better 'conflicting tech' check for mods

Resolved #3469 - more readable Inca colors

Resolved #3497 - city state quests always show correctly when diplomacy screen accessed from city button

Fixed crash when attempting to issue a 'connect to capital' quest for a civ with no capital

Translation updates

## 3.12.6

Resolved #3483 - settlers require at least 2 population to construct, as per Civ V

Set a max cap on unit maintenance - does not increase past the base turn limit

Resolved #3472 - can purchase 'free' tiles in cities even with negative gold

Resolved #3490 - fixed formatting problem in trade popup

Resolved #3489 - City state influence is affected by war/peace with their enemies

Resolved #3475 - capturing settlers moves us to the captured units' tile

Better visual aircraft indicators

Solved ANRs when loading big maps in editor screen

By 9kgsofrice:

- Modded buildings never lead cities to negative production
- "happiness from garrison" effect was duplicated

## 3.12.5

Resolved #3470 - popups now make the rest of the screen unclickable to avoid exploits

Resolved #3431 - Redesigned the player picker, to scroll through civs and display them separately

Resolved #3476 - captured civilian units no longer move on the same turn

Resolved #3331 - resources for city-state quests are taken from resources on the map

Resolved #3464 - units only advance improvements when they have movement points left

Fixed minor automation bug for modded terrains

## 3.12.4

Resolved #3424 - Added blink on event location

AI declares peace with civs they can't reach, solving 'endless stalemate war' problems


Game can handle mods removing units and technologies between versions

"Free great person" can no longer grant units unique to another civ

Added required building dependency check for mods

Caught modding errors - classic.

Modded water units with worker unique no longer build roads in water

Fixed chance of Arabian unique activating - By 9kgsofrice

## 3.12.3

Fixed starting positions not activating on new game

Resolved #3445 - national wonders no longer shown when already built

Replaced hardcoded Settler and Great General checks with their uniques

Resolved #3384 - Civ uniques now take all researched tech uniques!

Added road and railroad costs to improvement description

Resolved #3437 - reselecting improvement in progress does not reset progress

Resolved #3441 - fixed reverse maintenance cost unique

## 3.12.2

Resolved #3422 - added fast switch between adjacent cities in city screen

Resolved #3428 - added a toggle for displaying yield icons

Resolved #3427 - "player ready" screen appears when loading game in Hotseat multiplayer

City-states make peace with enemies when their allies do

Long tech descriptions are now scrollable

"Requires a {building}" notifications show the civ's equivalent to the building, if overridden

Korean UA activates on buildings added from buying and on national wonders

Translation updates

## 3.12.1

By nob0dy73:

- AI no longer tries to heal units in dangerous tiles
- AI units try to take back captured cities
- AI units try to head towards sieged cities

AI knows not to try and heal units which would heal anyway

Forced disband now provides gold

Solved ANR on load screen when loading large games

Deprecation of old unique formats in favor of newer, more generalized ones

Implemented missing Korean UA - By kasterra

## 3.12.0

Option to display tile yields on world screen - by jnecus

Added much-needed "+[]% Production when constructing [] units" unique

Added "All" filter for units

Resolved #3408 - Unit maintenance cost reduction generalized, now works for Ottomans

Resolved #3409 - American unique grants extra sight only to military land units

Fixed crash when nuking Barbarian units

By 9kgsofrice:

- GG bonus generation now checks for civinfo uniques
- City-state resources from all sources shared with ally civ

## 3.11.19

Reassign population after selling a specialist-providing building

Resolved #3289 - can place unbuildable improvements that can exist on tiles

Added nation icons to the leader names in the diplomacy screen

Fixed ANR caused by too many saved games

Selected unit stays selected when single-tap moved into a tile with another unit


By 9kgsofrice:

- resources can be added by tile improvement with unique "Provides [] []"
- Specialists can add happiness
- "Should not be displayed without []" unique for constructions accomodates resources and buildings

## 3.11.18

Improvements can't be built in neutral areas, as per Civ V

Added button to add construction items directly to the queue

Mods can handle removing existing buildings

Don't allow AI to offer peace to city states allied with their enemies

Helicopter Gunship - By givehub99

Workers stop building (most) duplicate roads connecting cities - By ninjatao

Translation updates

## 3.11.17

Fixed rare errors

First attempt at making Unciv Android-TV-compatible

By 9kgsofrice:

- adds val to modoptions and check to battle.kt to adjust max xp from barbarians
- "Uncapturable" unique
- unique "[] units gain the [] promotion" affects exisiting units
- Hide build menu constructions requiring resources with unique
- Nation "style" can define unit appearance
- Changes check to remove national wonders on city ownership changes to rely on building.isNationalWonder

Translation updates

## 3.11.16

Resolved #3364 - Fixed certain battle modifiers not activating

Resources provided by buildings are affected by resource amount uniques - by 9kgsofrice

Fixed minor crashing bugs in misconfigured mods

Display mod incompatibilities  when attempting to start a new game

Translation updates

## 3.11.15

HUGE framerate improvements! :D

Resolved #3347 - units spawned by buildings are spawned in the city the building was built in

Added mod checks that combat units have strength and ranged units have rangedStrength

Can now handle units upgrading to units with no required tech

Resolved #3360 - notify peace treaty to all common known civs

AI settlers can no longer settle after movement with no movement points

Fixed Free Thought trading post bonus - By ravignir

Units/buildings with "Will not be displayed in Civilopedia" now will not show in tech tree - By 9kgsofrice

Translation updates

## 3.11.14

500th version, my goodness 0_0

Resolved #3330 - Fixed black images on specific chipsets

Multiple framerate improvement tricks - should feel smoother!

Hide hotkeys on devices without keyboard - by jnecus

Fixed a few more crash possibilities from badly configured mods

## 3.11.13

Probably solved the Mysterious Disappearing C's once and for all! #3327

We now check compatibility of newly selected mods to the existing mod ruleset

Resolved #3341 - City-state diplomacy screen is shown properly when entering from a city button

Resolved #3071 - Disabled annoying camera momentum on Desktop

Autoload previous autosave when current autosave file is corrupted

## 3.11.12

AI no longer nukes single units

Can right-click to attack when a unit is selected

City states can now conquer cities, as per Civ V

Mods can now remove promotions between versions without breaking existing saves

Fixed archaeological dig being built by workers in Civ V expansion mod

Spectator can handle free-policy-giving techs in mods

Translation updates

## 3.11.11

Resolved #3324 - Great Person units no longer require a military unit to accompany them if they're close enough to the destination

Resolved #3326 - Settling a city removes the improvement in progress

Resolved #3323 - improvement uniques are no longer added twice

AI won't declare war if it doesn't know the location of any enemy city

Fixed key shortcuts in improvement picker screen

Translation updates

## 3.11.10

AI no longer tries to construct work boats that can't reach their intended destination

Can no longer see other players' IDs in a multiplayer game through the new game screen

We now remove resources and improvements that are not in the ruleset when loading the game

Tile rendering performance improvement

Diplomatic penalty of stealing lands decreases by time - By ninjatao

Fixed bug Carrier-based aircraft not healing - By jnecus

Translation updates

## 3.11.9

Tile editor can handle resources that don't naturally appear on any terrain

AI can no longer raze capital cities

Stats unique can no longer crash badly defined mods

Added mod check for unit promotions and upgrades, and building costs

"Unable to capture cities" unique prevents the unit from conquering/capturing a city - By givehub99

Translation updates

## 3.11.8

Improved performance, especially in the City screen

Avoided more badly-defined-mod crashes, and some rare non-mod crashes and ANRs

Added mods to crash report, many crashes are caused by incorrectly defined mods

Helicopter Gunship uniques - By givehub99

Translation updates

## 3.11.7

Resolved #3285 - added a notification when cities are no longer in resistance

More tile variants are enabled (e.g. "baseTile+resource+improvement")

Fixed some more silly bugs caused by badly configured mods

New snow-versions of existing tiles :)

Can no longer start a new game with an incorrectly defined mod! :)

## 3.11.6

Added "locate mod errors" button in the options menu for discovering broken links in base ruleset mods

Resolved another crash caused by incorrect ruleset mod definitions

Checking something that might solve the Mystery of the Disappearing C's

Added culture and science colors to resources

Split civilopedia "buildings" category into "buildings" and "wonders"

Resolved #3274 - empty maps start with ocean tiles

Consolidated unit kill bonus uniques - By givehub99

Translation updates

## 3.11.5

Performance improvements for main screen rendering - should be much less laggy now

Mod management screen is now generally available :)

Resolved #3265 - added keyboard shortcuts to tile improvements

City connections work when road and railroad required techs are changed (in mods)

Can now move around the world screen with the arrow keys as well as WASD

By r3versi:

- Diplomacy Screen right table is scrollable
- Fix for barbarian quest

Translation updates

## 3.11.4

Added city images by The Bucketeer for all eras up to Modern

Influence bar not displayed for city-states that don't know the viewing civ

Resolved #3254 - Solved bug in calculating turns to production for settlers

New parameterized uniques - By givehub99

City-states personalities - By r3versi

Translation updates

## 3.11.3

AI doesn't construct science constructions when all techs are researched

Added "Must be next to []" unique for tile improvements

Resolved #3239 - simplified unit actions

Added resource-providing building unique

Confirmation popup when clearing the map in the map editor

Files in the mod folder no longer crash the game on startup

By r3versi:

- Clear Barbarian Camp quest
- Pixel unit colors based on civilization colors
- City states influence bar

Translation updates

## 3.11.2

Find Player, Find Natural Wonder and Acquire Great Person quests implemented - By r3versi

Android now handles internally-downloaded and externally-given mods together well

Multiple small changes to make life easier for modders

Era-specific city tiles for Ancient, Classical and Medieval eras

Fixed Polynesia's unique - By givehub99

Translation updates

## 3.11.1

MODDABLE SPECIALISTS ARE GO!

Mods downloaded in-game on Android don't disappear

Fixes for minor bugs from the previous version

Fixed Windmill unique


Tech lines are colored for tracing paths on complex trees

Fixed minimap framerate-lowering bug

Minor UI fixes - By mrahimygk

Connect Resource Quest implemented - By r3versi

Translation updates

## 3.11.0

City-States Quests introduced! - by r3versi

Moddable Specialists, part 1 of 2

New unit images by The Bucketeer!

Rendering fix for specific GPUs and drivers - by CrispyXYZ

Promotion exploit fixed - by Jack Rainy

Modding - Buildings can be rendered obsolete (unbuildable) by techs

"[] units gain the [] promotion" unique - By givehub99

Translation updates

## 3.10.13

Better checking for unloadable scenarios

Resolved #3085 - reconquering our cities while they were still in resistance leads to them not having resistance against us

Cannot open multiple gold selection popups in trade table

"No Maintenance costs for improvements in []" genericified - By givehub99

Translation updates

## 3.10.12

Resolved #3186 - Diplomacy overview displays war/peace status and not relationship level.

Better trade cancellation when all cities are offered (also includes player-to-player cases, not only AI)

Fixed modded images not loading properly

Removed edge case option where the AI can trade you all of their cities

Parameterized few uniques, fixed "Mass Media" tech in mods - By HadeanLake

Hide Unit or Building as Unique - By givehub99

Update translations

## 3.10.11

Trade table options are now mousewheel-scrollable - #2824

By HadeanLake:

- Added national wonders
- Fixed some minor building issues

By givehub99:

- Allowed mods to override unique text for nations
- Added "Start with [] technology" unique

Implement custom save locations for Android and Desktop - By wbrawner

Translation updates

## 3.10.10

Generified "[X] free [] units"

Resolved #3130 - "All policies adopted" shown in policy picker screen when relevant

Multiselect applies to civilian units and from city overlays

UI Upgrade - By lishaoxia1985

Translation updates

## 3.10.9

Resolved #3115 - AI no longer congregates great people in cities where it can't improve tiles

Fixed AI unit upgrading - can now 'skip' over intermediate units, the way the "promote unit" action works. #3115

Parameterized some uniques, fixes some minor bugs - By HadeanLake

UI update - By lishaoxia1985

Experimental - Can now move multiple military units to nearby tiles at the same turn in Desktop, via shift-click

Translation updates

## 3.10.8

Resolved #3048 - Fixed ANRs on 'Resume' on huge save files

Mod management screen improvements

Resolved #3059 - better city expansion rules

Resolved #3081 - fixed bug in air interception

Show that air units can move to tiles within attack range - By bringert

Fixed #3066, crash in chooseMilitaryUnit and some great people actions - By HadeanLake

Translation updates

## 3.10.7

Mods can handle situations where there is no military unit that is available for construction

Getting the mod list for download now works from Android as well

Resolved #3076 - automation now happens at the end of turn rather than the beginning

UI fixes for rounded edge boxes - By lishaoxia1985

Can construct Farms near freshwater in all terrains - By HadeanLake

Translation updates

## 3.10.6

Options button now appears in main menu

Added "automated workers don't replace improvements" setting (#3050)

Separated base ruleset mods in new game screen - cannot activate multiple base ruleset mods

Resolved #2886 - new Plains+Forest tiles by The Bucketeer that don't hide the rivers behind them :)

Resolved #3053 - improvements can be built on neutral tiles

Resolved #3065 - Ottomans' unique is now according to Vanilla

By HadeanLake:

- Added Aztecs nation
- A Few UI fixes
- New uniques and stuff for mods

UI fixes - By lishaoxia1985

## 3.10.5

Added Mod Management screen!

Can now download Mods directly from Github, with mod discoverability!

Can now add Maps to mods, to dissimenate them through Github as well!

Resolved #3035 - added nationsToRemove in modOptions

Translation options

## 3.10.4

Resolved #2979 - Display countdown to negotiate peace in diplomacy screen

Resolved #2844 - Can now immediately move units in Desktop with right-click

Added unit symbols for Turn, Strength, Ranged Strength, Range and Movement as 'emojis'

Resolved #2937 - can add large increments of gold in trades

Keep the perpetual construciton going, if the user set it manually.

Tech picker screen auto-handles eras of different lengths

Can remove tile features outside your borders

A mishmash of different fixes: Unit uniques and promotions, happiness calculation, etc.

Added a new test that ensures no two placeholders are the same


## 3.10.3

Settler 'by name' recognition changed to 'by unique' recognition, allowing for modded settler-like units

Can now disable diplomatic relationship changes in a mod

MapGen doesn't place ancient ruins if they're not defined in the current ruleset

Resolved #3016 - Policy branch uniques are translated properly

Borders consist of both civ colors, making some borders (esp. Germany) much clearer

Roads and railoads can be removed outside your borders

More generifications!

Merged Building and Policy unique activations

Translation updates

## 3.10.2

Added link checks when loading mods, so you'll know if you messed something up when making them

Dealt with some game assumptions about what exists, which may not be true in mods

TechPickerScreen centers small tech trees nicely

By HadeanLake:
- Bugfixes
- Fixed AI being stuck doing science or gold per turn

Translation updates

## 3.10.1

Scenarios can handle tech trees that aren't continually researchable - this allows for very small modded tech trees

Can mod continually researchable techs

Nuke can now no longer destroy Capitals, instead of destroying only capitals - By HadeanLake

Finnish translations added!

Translation updates

## 3.10.0

Can now create Scenarios and release them with mods!

Comes with Scenario Editor mode in the main game screen

AI chooses to fortify in non-bombardable tiles if possible

Resolved #2985 - fixed Embark/Disembark costs

Resolved #2986 - Knight now obsoletes properly

Map editor UI improvements

Spectator and City-State civs are no logner considered as having 'discovered' a natural wonder

Translation updates

## 3.9.20

Scenario changes:
- Can now play an entire scenario with no improvements or techs defined
- Added Scenario victory condition
- Scenario now no longer spawns starting units

Denounce now has a confirmation popup

Ancient Ruins can now provide Culture

Resolved #2951 - only ancient ruins improvements are removed around players' starting locations, and not other improvements

AI now has 5 favored policy trees for each preferred victory type, making them more likely to win culturally

AI no longer uses all its aluminum on units and leaves some for spaceship construction

Translation updates


## 3.9.19

Resolved #2818 - Can no longer build improvements outside your borders

Resolved #2944 - Air units intercept range fixed

All nations converted to uniques!

Solved unit purchase discount being 100x what it was supposed to be

Translation updates

## 3.9.18

Resolved #2872 - Diplomacy screen now scrollable when there's too much text

Performance improvements

Fixed civ name translation in the top bar and great person point percentage bonuses

Resolved #2929 - Can no longer destroy original capitals by nuke

Resolved #2928 - contact with other civs is now also when encountering their cities

Changed how great unit recognition works

Resolved #2914 - Can no longer exploit button to 'skip' promotions

Resolved #2893 - all maps are shown when searching - By vainiovano

Top bar selected civ refactor, increase performance for updates - By alkorolyov

Translation updates

## 3.9.17

All policies converted!

Map editor should no longer crash in scenarios

Scouts ignore river movement penalties

Pillage action now has a confirmation popup

Spaceship parts not shown to user until Apollo Program is built

Units can pass through cities of other friendly civs

Resolved #2907 - University unique registers properly

Added civ-wide per-building stat bonus

New Diplomacy Overview UI - By lishaoxia1985

Fog of war implementation - By alkorolyov


## 3.9.16

Resolved #3901 - nuclear weapon setting remains between games

Game can now handle modded unique buildings that don't replace anything existing

All improvement placing units are automated in the same way - this allows for the AI to control modded units that place other improvements!

Railroad connection propagates correctly over harbor connections

Resolved #2894 - Map editor button only opens popup once

Translation updates

## 3.9.15

Removed confusing extra lines in the diplomacy overview

Started splitting up Policy uniques - they're usable as Building uniques now!

Upgradable units show the final unit they're upgrading to in the Overview screen

Created stat parameter parsing and translation - uniques are much more moddable!

Resolved #2838 - cities in resistance can no longer be traded

Fix bug when city states gets all techs from spectators - By alkorolyov

Translation updates

## 3.9.14

Added Pinglish translations

Genericified "free [unit] appears", "must be next to []" unique for buildings

Greatly improved performance of worker's automated city connecting

Resolved #2853 - Sped up loading of saved game list

Resolved #2852 - cannot make peace with a City-State while at war with its ally

Resolved #2864 - Locks on tiles are removed when the tile is no longer under your control

Buildings that are missing resources are still displayed in city constructions

Unit 'unbuildable' parameter converted to unique

By alkorolyov:

- Spectator can view other civ stats: Tech, Trades, Cities, Units, Gold
- Skip spectator turn in multiplayer games

## 3.9.13

Simplified translation file generation

Background work for "generic-ifying" unit and building uniques for modding purposes

Worker unique is now moddable to other units

By alkorolyov:
- Spectators can enter and view other player cities
- Fix map editor gameparameters layout

Translation updates

## 3.9.12

Added Water Mill building

Add mod compatibility for extended map editor - By alkorolyov

Main menu buttons no longer require scrolling

By lishaoxia1985:
- Close button on map management screen no longer disappears when deleting all maps
- Scout obsoletes per Civ V

Translation updates

## 3.9.11

Better Civilopedia icons for buildings and units

Resolved #2822 - normalized the amount of strategic resources

Resolved #2819 - units no longer gain XP from attacking already defeated cities

Resolved #2820 - resurrected civs are at peace with everyone

 By alkorolyov:

- Fixed two empty mods checkbox in game options
- Two experimental switches: Spectator mode & Extended Map Editor

 By ninjatao:

- Do not generate fallout on impasssible terran.
- Fix AI nuke radius

Translation updates

## 3.9.10

Resource toggle button is consistent with population toggle - by @lishaoxia1985

Unremovable terrain features e.g. Flood plains are no longer removed by great improvements

Resolved #2640 - Difficulty level shown in victory status screen

Great improvements are no longer hardcoded, so new great improvements can be modded in =)

Resolved #2811 - Offering the same resource to 2 civs when you only have 2 left no longer causes 'trade no longer valid' for the second one

Dispose main menu screen when exiting to save space

Translation updates

## 3.9.9

Civ is properly destroyed when liberating the last city of the civ

Replaced units are not shown in tech button even when replacing unique unit is in a different tech

Fixed main menu crash when returning from certain modded games

Resolved #2794 - Save games and maps cannot have slashes/backslashes, to avoid foldername/filename confusion

Civ is properly destroyed when liberating the last city of the civ

By alkorolyov:
- Basic spectator functionality - POC
- Console mode for multiple game automation - POC

Added an installation problem solution on Ubuntu - By illantalex

## 3.9.8

Resolved #2787 - AIs MUCH more likely to build the Apollo Program and win a Scientific Victory

Resolved #2789 - losing a resource no longer cancells all trades with that resource, only as many as is necessary to reach equilibrium

Removed tech exchange, as per Civ V

Resolved #2759 - Mark tiles for air unit attack range - By ninjatao

Translation updates🍎

## 3.9.7

Resolved #2749 - show current improvement and remaining time to build in improvement picker screen

Resolved #2112 - Show current resource amounts on incoming trade requests

Hopefully mitigated some very odd multiplayer-checker-related crashes

Fixed natural wonders not being considered 'impassible' for certain things (e.g. ancient ruins spawn)

Translation updates

## 3.9.6

Resolved #2761 - Tutorial titles are auto-added to the translation files

Resolved #2703 - placeholder translations now check active mods for translation values - by dbaelz

Background work and POC for Scenario editor - By alkorolyov

Improve performance of multiplayer load poll - By soggerr

Translation updates

## 3.9.5

Fixed coast tiles around natural wonders spawning land-type layers

By lyrjie:

- Map generation speedup
- Fixed strategic resources generation

By alkorolyov:

- Now clearCurrentMapButton and TerrainsAndResources clears rivers.

By ninjatao:

- Fix oil generation in sea.
- Fix forest display in Default tileset

Translation updates

## 3.9.4

Opening the New Game screen from within a game saves the previous game parameters, map generation parameters work again

Resolved #2662 - left side of the screen no longer becomes unresponsive to player input after changing from a selected unit to a selected city

Resolved #2735 - Diplomatic "Friends with friend/enemy" modifiers are recalculated every turn

Great improvements are marked as such in the Civilopedia

Translation updates

## 3.9.3

Resolved #2723 - resource-specific improvement bonuses are *in addition to* the regular improvement bonuses, and not instead.

Resolved #2708 - added rivers to plain tileset

Medium-sized translation updates

Meanwhile, in Google Play, we've reached 500K downloads!

## 3.9.2

Move units out of cities when liberating

Thread crash fixes - By vainiovano

New translation language - Lithuanian

Clicking the menu button when it is open closes the menu

Translation updates

## 3.9.1

Buildings requiring a nearby resource can be constructed even when the tile belongs to another city

Natural wonders spawned before rivers, so we don't retroactively get rivers on coast tiles

Added civilopedia info for great people and great improvements, removing terrain features, and strategic resource provision - see #1492

Resolved #2613 - added a close button to the Civ-picking popup in the New Game screen

Battle calculation takes into account the tile that the unit will attack from

Translation updates

## 3.9.0

Added rivers, and river generation!

Game now saves save files in external storage on Android when possible.

Resolved #2672 - Difficulties are sorted by ascending difficulty in Civilopedia

Resolved #2660 - Remove Fallout now enabled by Atomic Theory, not Agriculture

Great person uniques can be added to any modded unit

Translation updates

## 3.8.12

By dbaelz:

- Minor UI improvements construction menu
- Evaluate translations for mods only when the mod is active in a game. Fixes #2622
- Add remove button for construction queue items

Resolved #2647 - Automated workers run away from enemy military units

Advanced sliders work on New Map Screen

Translation updates

## 3.8.11

Gold deficit only affects science when the civ has negative gold

Resolved #2642 - added difficulty settings to Civilopedia

Resolved #2549 - fixed New Game Screen capitalization, options alignment and placing

Resources no longer spawn under unbuildable, unremovable terrain features

Resolved #2638 - Auto-assign of population no longer "double books" tiles

Uniformed the size of buttons on LanguagePickerScreen and MultiplayerScreen - By panchenco

Don't reveal submarine position by city markers - By JackRainy

Size optimization - By lishaoxia1985

Translation updates

## 3.8.10

Font system rewrite to accept all characters and speed up game loading - By vainiovano

More new tiles by The Bucketeer!

New river-ready tiles by The Bucketeer!

Fixed Manhattan Project being inversely affected by nuclear weapons disabling

Translation updates

## 3.8.9

City-states no longer spawn Great Generals

Can no longer see other human players' assigned tiles

Resolved #2618 - better inter-city navigation in city screen

Resolved #2611 - City button shrinks on zoom-in

Fixed rare citadel crash

Better trading posts by The Bucketeer!

Years per turn normalized to game speed - By AcridBrimistic

By JackRainy:

- Civilian units no longer move when bought
- Fixed translation for civ start biases

Translation updates

## 3.8.8

Redid layout for the New Game screen - see #2549

Better jungles and lakes - by The Bucketeer

Normalized "Buy" and "Fortify" sounds, so they're not overly loud

Solved ANR when loading game to display its metadata

Population assigned to tiles of other cities do not auto-unassign

Settler automation takes into account which tiles already belong to other civs

Translation updates

## 3.8.7

Small update this time!

Resolved #2588 - instead of tile ownership being transferrable between cities, cities can now work tiles belonging to other cities.

Size optimization - By vainiovano

Translation updates

## 3.8.6

Performance improvement when selecting a tile to move to - By vainiovano

Autosaves no longer garbled when exiting extremely large games - By JackRainy

By SomeTroglodyte:
- Better Resource order in empire overview screen
- Typo fixes

Resolved #2576 - Clicking on "Encountering" notifications now moves the map to the encounter location

Differentiated between Portuguese and Brazilian Portuguese translations

Translation updates

## 3.8.5

Maps no longer spawn ancient ruins in immediate vicinity of civ spawns

Invisible Romanian characters are now visible

Fixed thread crashes due to concurrent actor changes in multiplayer update popups

Translation updates

## 3.8.4

Better "declare war" and city battle decisions (hopefully) for AI

Minimap shows entire map again - now looks good for both rectangular and hexagonal maps

Resolved #2536 - cities correctly expand to the last available tile

Game always resume previous screen on resume(), and autosaves on pause

By SomeTroglodyte:
- Performance improvements
- Translation generation for mods doesn't add entries that exist in base translation

Translation updates

## 3.8.3

Performance improvement - by vainiovano

By SomeTroglodyte:

- Terrace Farms: Bonus resource restriction
- Map editor: Placed improvement check updated, resolves #2489

Fixed 'auto assign production' not working when changing from manual to auto assign

Fixed crashing error when loading mods

Many performance improvements

Fixed modification exception when destroying transported units

Resource bonus from Fascism effective immediately

Translation updates

## 3.8.2

Main screen buttons fit in all languages - by Jack Rainy

Typo fixes and character organization - By SomeTroglodyte

AI great people no longer raise improvements on tiles with great improvements

Iroquois movement unique only applies to friendly territory

Resolved #2503 - Resizing game no longer returns to main menu

New game screen adjusts to base rulesets with small amounts of civs, including barbarians

Saving a map from the map editor screen no longer changes the screen

Translation updates

## 3.8.1

Solved concurrency problems

Fixed #2492 - trading cities with units in them no longer crashes the game

Can start a new game from within a game, to copy over the game's parameters

Fixed resource display bug in tile table in multiplayer

Added a randomly-generated map as background for the main menu

Decrease CPU load for multiplayer game - By Jack Rainy

Translation updates

## 3.8.0

Game starts and defaults to a new Main Menu - should help solve errors as well as allow for cleaner disambiguation of problems

Map Elevation normalized to feasable amounts of mountains

Resolved #1936 - can sign Declarations of Friendship in Multiplayer

Resolved #2360 - can now change the current user ID for multiplayer when changing devices

Much more turn-efficient exploration!

City tiles are always contiguous, otherwise loads of wierd bugs happen

Fixed the auto-unassigning extra specialists

Allow scandinavian lowercase vowels (capitalized are very rare) - By SomeTroglodyte

Translation updates

## 3.7.6

Can specify a mod as a 'base ruleset' -
supports mods with no techs, alternate tech trees, No Barbarians, no workers

Unique units need not replace existing ones

Tile variants!

By SomeTroglodyte:

- Fix minimap mouse dragging
- Fixed modded strategic resource without tech prerequisite

By JackRainy:

- Highlight unique offer suggestions
- Sort trades by expiration
- Citadel improvements + improved AI for forts
- Map Editor UI improvements

## 3.7.5

By SomeTroglodyte:
- Keyboard: Left/Right arrows work in city screen
- Small optimizations
- Rationalism effect visible in city UI
- Mods can have improvements above terrain features

By Jack Rainy:
- Player's automated workers don't build forts
- Better Domination victory checks
- Can continue turns after defeat, as spectator

New screen for "Add Game" - By GGGuenni

Resolved #2348 - construction queue 'cleaned' after every construction

Resolved #2413 - Hotkeys for unit actions always displayed

Translation updates

## 3.7.4

By Jack Rainy:
- Use correct icons for great improvements
- World view stats clickable
- Fix crash for tiny cities

By SomeTroglodyte:
- Fix options disabling next turn
- Next turn button colors
- Cultural expansion maximum
- Icons for stats overview

Cannot accept multiple conflicting offers - #2146

Added city expansion tutorial - #2322

Reveal all civs when won/lost - #2407

Various exploration-related improvements - #2278

Unit placement improvements - #2406

Translation updates


## 3.7.3

By Jack Rainy:
- New Civilization: Denmark
- Performance improvement
- Better mod exceptions

By SomeTroglodyte:
- Civilopedia cleanup
- Better map saving

Resolved #2285 - workers no longer try to work within range of enemy city

Resolved #2221 - buildings in mods can reference techs in original ruleset

Resolved #2381 - can now remove buildings, units and techs in mods

Free policies with no adoptable policies no longer 'stuck' the game

Translation updates

## 3.7.2

By rh-github-2015:
- Keyboard support for unit actions!
- Can cancel improvments!
- Better tracking of language picking
- Conditionally pack images
- Scroll panes autofocus
- Code optimization

By Jack Rainy:
- Improved great improvement build rules
- Fixed Woodsman promotion
- Civilian units don't wake up from enemies if they're protected

"Natural Wonders" tutorial - by Smashfanful & Jack Rainy

Resources immediately come back after declined trades

Translation updates

## 3.7.1

Hopefully resolved #2361 - should work on 32-bit linux

By Jack Rainy:
- Better colors for Katmandu and Almaty
- Autosize of the tech buttons
- Display the crosshair in alternate color for distant targets
- Display price for unavailable purchases

By rh-github-2015:
- Fortify until healed disabled if no more movement
- Better thread handling

Resolved #2340 - cannot open multiple 'disband' popups for cash hack

Resolved #2305 - Added city-states and influence tutorial

Translation updates

## 3.7.0

By Jack Rainy:
- Forts and citadels (with AI)
- Crash fixed in specific circumstances of Map Editor

"Free policy" freezes fixed

Desktop window size restore - By rh-github-2015

Not being able to read the settings file shouldn't make you crash

Translation updates

## 3.6.15

By Jack Rainy:
- New wonders: Mausoleum of Halicarnassus, Statue of Zeus
- UI bugfixes: city info and aircraft
- Display the hidden units indicators under city buttons
- Map generation places resources 'under' terrain features

Resolved #2135 - added tutorials for Research Agreements, Combat and Experience

Nuclear weapons setting moved to a per-game parameter

Railroad is now just a black line - much clearer than the old, "nicer" image

Translation updates

## 3.6.14

Harbors immediately connect cities

Revealed resources near cities generate notifications

Cities in unit overview are translated

Resolved #1885 - Both sides of per-turn trades end at the same time, resources offered in trade requests are not considered
yours for that turn

Resoved #1869 - added WASD support for map panning

Resolved #1779 - Can lock worked tiles to prevent them from being unassigned

Resources in Civilopedia state consuming units and buildings - #1964

Translation updates

## 3.6.13

By rh-github-2015:
- New Nation added - Inca!
- Show improvement bonus for improving tech in Civilopedia
- Add unit ability 'withdraw before melee' to Caravel and Destroyer

By Jack Rainy:
- Carrier is ranged
- Mod's name is translatable

City stats and resources update after creating great improvement

Resolved #2214 - Civilopedia entries are now left-aligned

Fixed very rare crashing bugs

Translation updates

## 3.6.12

City names now translated in overview and tile info

Resolved some ANRs in Multiplayer

Performance improvements in finding cities connected to capital

Mod translation generation - by Jack Rainy

When moving between units to units in cities, tiles they can move to are now shown

Adding mods auto-adds relevant civs - by rh-github-2015

Added progress bars for constructions in city screen

Nicer, more consistent specialist allocation tables

Translation updates

## 3.6.11

By rh-github-2015:
 - More civilopedia info for nations and improvements -
 - Better error handling for Mods

Fixed "other civ doesn't get duration on timed trades" bug

Removed "Declare war" trade option when there's a peace treaty

Resolved #2175 - Can no longer queue multiple perpetual builds

Resolved #2150 - Added resource type to civilopedia description

Resolved #2224 - can no longer enter promotions screen from overview screen if no valid promotions

LOADS of Translation updates

## 3.6.10

By Jack Rainy:
- Double range of rebase for air units
- Translation files now take values directly from data files - no more value mismatches!

By rh-github-2015:
- Clearer free promotions
- UI cleanup

Unit purchasing limits - by EdinCitaku

Unit Action buttons stick to the left

Translation updates

## 3.6.9

By rh-github-2015:
- Trade offers better sorting with user choice
- Resources always stay up-to-date
- Promotion and health columns in units overview

Preparation for recognizing ID types - by tobo

Translation updates

## 3.6.8

Loads of stuff by Jack Rainy!

- UI improvements
- Energy saving for music and sound
- Allow selection of non-buildable items
- Sort overview resources by name and amount
- Turn number of trades changes with game speed
- Filter for the custom maps
- Ice is Impassable for all except submarines

Translation updates


## 3.6.7

By Reversi:
- Fixed city buy/sell exploits
- Snow, Ice, Atoll & Map Generation
- Borders made of oriented triangles rather than circles

By Jack Rainy:
- Major translation changes
- Minor UI changes
- Brought specific things more in line with Civ V

Starting locations work again =)

Translation updates

## 3.6.6

Resolved #2071 - AI will wait 20 turns between proposing research agreements if declined

Ranking by culture is by number of adopted policies

Player cannot nuke a civilization it has a peace treaty with - by EdinCitaku

Resolved #2074 - Fixed domination victory

Resolved #2040 - Moved the turn counter on the top bar to the second level, to even out both levels
Water units can no longer see over hills etc.

By Jack Rainy:
- Keshiks are ranged units
- Distinct color for Korea

Translation updates

## 3.6.5

Resolved #2065 - Units can no longer sleep while fortified - by Vladimir Tanakov

Resolved #2033 - Happiness from tiles is always considered correctly in regards to food consumption

Resolved #2035 - Strategic resources are affected by map generation parameters

Resolved #2055 - cities once again bombard melee units

Fixed rare crash when diplomacy values change while ending the turn

UI updates - by lishaoxia1985

Translation updates

## 3.6.4

By Jack Rainy:
- Civilization rankings (Richest, Strongest, Most Fertile, Largest, etc.)
- Can now buy buildings from queue
- Include Nations in the calculation of a translation's percentage

Eiffel Tower effect changed to reflect original Civ - by lishaoxia1985

Translation updates

## 3.6.3

Workers automate roads/railroads overseas, railroads connect through harbors - by przystasz

Multiplayer Notification Fix - by tobo

Fixed wrong placement of purchased/upgraded units - by Jack Rainy

Tile stats in city now shown in a row - by reversi

Refactor for unit actions - by Kentalot

Resolved #1986 - research agreement cost consistency

Resolved #2012 - starting locations no longer visible

Victory screen shows the viewing player in multiplayer games

Translation updates

## 3.6.2

Resolved #1982 - Can now pic civ-equivalent Great People in great person picker screen

By Jack Rainy:
- Fixed rare bug of clicking on an unknown civilizations
- Display the current amount of gold in the "buy" prompt
- Do not allow to purchase extra units requiring unavailable resources


Removed pebble symbol from Unciv icon when showing persistent notification - by tobo

Performance improvements - by Kentalot

Translation updates

## 3.6.1

Resolved #1963 - can now unautomate embarked workers

Resolved #1697 by adding information to the special production constructions - by Kentalot

By Jack Rainy:
 - Future Tech can't be picked as a free technology until requirements are met
 - Can sort cities in overview by food, production etc

Resolved #1962 - "infinite zoom" from capacative scrolling is no longer irrecoverable

Resolved #1975 - Museum and Factory now give 2 specialist slots

Fixed rare crashes when pausing

Translation updates

## 3.6.0

Massive multiplayer improvements!

Multiplayer screen reworked - by GGGueni

Added Multiplayer Turn Notification Service - by wrov/tobo

UI of New Game screen updated - by lishaoxia1985

Resolved #1930 - national wonders no longer require built buildings in puppeted cities

Nuking a Civ's land is considered an act of war - by Jack Rainy

Translation updates

## 3.5.14

Resolved #1926 - fixed unique improvements

Resolved #1927- changing new game parameters and exiting the new game screen doesn't change the current game's parameters

Resolved #1818 - Marble bonus now displayed in Civilopedia

Resolved #1918 - added missing translations

Fix tile yields in city screen when in multiplayer - by reversi

By Jack Rainy:
 - Forge increases production of spaceparts
 - Preview tile improvements


Minimap improvements - by lishaoxia1985

Translation updates

## 3.5.13

Moai no longer buildable on terrain features - by lyrjie

Resolved #1902 - buildings requiring worked resources can be built in cities that are built on that resource

Resolved #1841 - Legalism grants enqueued buildings - by reversi

Minimap can show the whole worldscreen and display cities better - by lishaoxia1985

Captured Khans now automate properly

Translation updates

## 3.5.12

Mongolian civ added! - by reversi

By Jack Rainy:
- Improvements to the Diplomacy overview: better spread and can select specific civs
- Research screen centered on current tech

Resolved #1859 - skip defeated players' turns in multiplayer

Fixed bug where air units in transports couldn't upgrade

Tied the unit upkeep scaling to game speed - by lyrjie

Translation updates

## 3.5.11

Rectangular maps and better map generation - by reversi

Resolved #1847 - Civs defeated by a nuke are now properly destroyed

Resolved #1844 - improved worker automation

Resolved #1852 - buildings requiring an improved resource now accept resources with great improvements

Solved the 'infinite warmongering penalty' bug

Resolved #1858 - Automated workers build unique improvements

Translation updates

## 3.5.10

Resolved #1827 - can no longer see other (current) player's city production in multiplayer

Resolved #1839 - Civs no longer declare war and offer things in the same turn

AI no longer sends 'please don't settle cities near us' warnings when at war

When disbanding carriers, transported air units move to nearby tiles if they can. If they can't then they're disbanded.

Resolved #1457 - no AI-to-AI trades are 'automatically accepted'

Translation updates

## 3.5.9

Resolved #1820 - Fixed a crashing bug with the AI trying to ally with defeated City-States, as well as many other minor bugs

By Jack Rainy:
- Nuclear missile is able to target any tile within the range
- Added "Sleep/Fortify until healed" functionality
- Fixed relationship bug when capturing cities

By lyrjie:
- Notification when City-states advance an era disabled
- Fixed bug pertaining to unit healing

Half-ready Japanese translation added! - by paonty

Show name when icon is tapped in resource overview in a label above the icon - by ltrcao

Translation updates

## 3.5.8

Buying current construction no longer removes other items from the queue - by reversi

Resolved #1757 - Can now see version when running from a desktop Jar

Fixed crash in city-state influence notification

Solved ANR when waiting for the list of maps

Resolved #1808 - Disabled problematic declaration of friendship


Translation updates


## 3.5.7


Added Non-continuous rendering setting, to disable animations and save battery - by reversi

Add missing Forge and Seaport production bonus uniques - by Teague Lander

Fixed anti air units intercept range

Fixed aerial transportation crashing bug

Add specialist slot description for buildings that provide them - by Teague Lander

Quick access to diplomacy screen with other civilizations by tapping city buttons - by ltrcao

Translation updates


## 3.5.6

Research agreements! - by lishaoxia1985

Exploring units no longer auto-enter City-States

Map editor menu fix - by lyrjie

Can no longer acquire another player's tiles in multiplayer - by lyrjie

Specialist allocation is immediately viewable on the City screen

Legalism fix - by reversi

Fix #1759: correct 'turns to construction' for multiple units of the same type - by reversi

Translation updates

## 3.5.5

Aircraft Carriers added - by ltrcao and Jack Rainy

Popups streamlined - by Azzurite

Fixes to Oligarchy - by lyrjie

Better support for tile images

More tile images by The Bucketeer

Translation updates


## 3.5.4

Turkish translations added - by rayray61

By lyrjie:
- Starting units no longer spawn on Ancient Ruins/Barbarian encampments
- Ships no longer 'teleport' into landlocked cities
- Barbarians no longer 'pillage' city ruins

Barbarians don't move/attack on the turn of their spawning

By reversi:
- Legalism policy fix
- Fixed "empty entry in construction queue" bug

New Resource+Improvement images by The Bucketeer

Translation updates

## 3.5.3

By lyrjie:
- Can no longer buy the same building multiple times
- "Pick construction" tutorial now completes again
- Map is revealed after singleplayer defeat


More resource images by The Bucketeer

Ancient ruins are no longer save-scummable

Resolved #1700 - As per original civ, you no longer start with a scout.

Translation updates

## 3.5.2

By JackRainy:
- Disable France culture boost after Steam Power
- Back button prompts dialog for game exit
- Janissaries heal after killing

Legalism grants culture buildings asap - by r3versi

Fix Map Editor Lag - by r3versi


Cities can now bombard all tiles within range of 2 - by Vladimir Tanakov

barbarian automation - by Vladimir Tanakov

Idle units select properly after settling a city - by lyrjie

Proper destruction notification for City-States

Added Fur resource image

Translation updates

## 3.5.1

City constructions queue - by r3versi

Songhai Civ added - by Smashfanful and r3versi

Game no longer confuses cities with the same name - by r3versi

Can no longer buy tiles outside the 3-tile radius - by lyrjie

Added the "crudely-drawn map" to ancient ruins outcomes - by lyrjie

Visual bugfix for city button growth bar - by drwhut

Translation updates

## 3.5.0

Modding for Desktop is now available!

More resource images by The Bucketeer

Added a growth progress bar to CityButton - by drwhut

Translation updates

## 3.4.8

Huge Map Editor update - bush sizes and paint-dragging! - by r3versi

"Stop" actions now work again - by r3versi

Battery saving rendering (also removes the 'current unit' animation but that's the price to pay) - by r3versi

"Sell building" no longer disabled when player has little gold - by lyrjie

Performance improvements

Tutorial updates

## 3.4.7

Spain civ added - by r3versi

Background work towards enabling mods

Resolved #1598 - we now save Map Options for new games started

Specific AI automation for Missile units means they won't try to move to tiles that they can't move to

Translation updates

## 3.4.6

AI no longer attempts to get rid of barbarian encampments with nuclear strikes.

All natural wonders now in the game, kudos to The Bucketeer for the art!

Can now display pixel resources on the map - for now we have the mineral resources

Resolved #1569 - "Patreon" button actually opens Patreon now

Natural wonders fixes - by r3versi

Civilopedia and Tutorial fixes - by Vladimir Tanakov

Added notifications for diplomacy between other civs - by lyrjie

Translation updates

## 3.4.5

Resolved #1533 - Defeated City-States no longer "declare war" if you attack their ally

Ancient Ruins are now spread out instead of randomized

Automated units no longer advance towards enemies they can't attack without dying - by lyrjie

Can no longer purchase constructions from cities in resistance

New language getting translated: Indonesian!

Translation updates

## 3.4.4

Grand Mesa and Mount Fuji artwork by The Bucketeer means they're now in the game!

Happiness from worked tiles now computed - by r3versi

Polynesian galleys can enter ocean - by lyrjie

Barbarians enter owned tiles (difficulty dependant) - by lyrjie

Cities no longer connected to capital by water without harbor - by lyrjie

Email popup for crash reports on Android - by Vladimir Tanakov

Translation updates

## 3.4.3

Most players said Cultural victory was too easy - now requires 5 branches instead of 4

By r3versi:
- Unique promotions listed in Promotion screen
- Fixed Greece City-States influence unique
- Added notifications on losing city state relationship

Misc. rare bugfixes when:
-liberating cities when you haven't met their original owners
-activating "Conduct trade mission"
-conquering cities
-obsoleting scouts
-razing the capital city
-first entering a large multiplayer game

Translation updates

## 3.4.2

Diplomacy graph size scales with screen space available - by r3versi

Game crash popup no longer affected by tutorial settings

Fix error due changing language on Android < 4.4 - by r3versi & Vladimir Tanakov

Resolved #1493 - Added Nation information to Civilopedia

Added Promotion information to Civilopedia

Translation updates

## 3.4.1

Fixed more crashes and ANRs related to translations

Added loading screen

Considerably sped up initial loading time

Translation updates

## 3.4.0

Natural wonders are go! :smile: - by r3versi

Great scientist now generates science like in original game - by lishaoxia1985

Solved the "0 production for settler" bug

Tutorial task table now becomes visible when turning displayTutorials on - kudos @r3versi

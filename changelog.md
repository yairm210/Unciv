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

Map height normalized to feasable amounts of mountains

Resolved #1936 - can sign Declarations of Friendship in Multiplayer

Resolved #2360 - can now change the current user ID for multiplayer when changing devices

Much more turn-efficient exploration!

City tiles are always contiguous, otherwise loads of wierd bugs happen

Fixed the auto-unassigning extra specialists

Allow scandinavian lowercase vowels (capitalized are very rare) - By SomeTroglodyte

Translation updates

## 3.7.6

Can specify a mod as a 'base ruleset' - 
supports mods with no techs, alternate tech trees, no barbarians, no workers

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

Resolved #1598 - we now save map options for new games started

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

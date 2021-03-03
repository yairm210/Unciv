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

Resolved #2852 - cannot make peace with a city state while at war with its ally

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

Resolved #2787 - AIs MUCH more likely to build the Apollo Program and win a scientific victory

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

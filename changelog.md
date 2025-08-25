## 4.17.17

Force rankings doesn't evaluate all unit conditionals as multiplicative

Handle mods removing techs, adding/removing eras

Post-battle movement doesn't occur if during the battle the unit lost movement points

conditionals in event choices work again

AI updates - By EmperorPinguin

## 4.17.16

AI spy city selection greatly improved

Changing mods while images load no longer displays multiple images on the last mod

Removed city-state icons for cities in unit overview

Replacement improvements also provide resources that require the original improvement

Only heal on current tile if it's not a dangerous tile

Fixed several problems with improvement-creating buildings

## 4.17.15

Allow AI to move-and-settle

AI: declare less war against humans on higher difficulties - By EmperorPinguin

Harden new game screen against bad scenarios - By SomeTroglodyte

By RobLoach:
- Add Victory Type for Brave New World 
- Civilopedia: Add Victory Types

## 4.17.14

Fixed map editor tile click not displaying tile stats

Ranged strength comparison for unique units correctly translated

By SeventhM:
- Fix Ruin rewards with multiple triggerables only giving the first effect 
- Add in Target Unit check for uniques when entering combat 

Add countable for `[stat] Per Turn` - By RobLoach

## 4.17.13

By EmperorPinguin:
- AI: build more workers
- AI: Better settler automation

Fix combat conditionals not working correctly - By SeventhM

## 4.17.12

Fixed spy surveillance progressing tech stealing when no techs are available to steal

Bugfix: Building 'improvement to create' no longer cached across rulesets

Fixed city states getting all techs when only 1 major civ remains

Many small automation improvements - By EmperorPinguin

"Upon entering war" uniques - By PLynx01

## 4.17.11

Fix Ancient Ruin benefit on higher difficulties - By RobLoach

Multiplayer: Only bring forth `AuthPopup` if server explicitly returns `401` - By touhidurrr 

Modding: "can settle" unique able accept tile filters for water and impassible tiles - By Emandac

## 4.17.10

Fix crash when opening victory screen on the first turn

modding: Add misspelling corrections to unique parameters in mod checker

By SomeTroglodyte:
- Fix unit icon disappearing on right-click of unit's tile 
- Fix autoplay+tutorials softlock

By EmperorPinguin:
- AI: exclude great general from escort logic 
- AI: better utilization of promoted units 
- AI: lower threshold for minimum acceptable newcity tile 

Add unit action icons for triggerables - By RobLoach

## 4.17.9

Remove irrelevant counteroffer notifications when the trade request is invalid

By EmperorPinguin: 
- AI: better explore logic 
- AI: reduce food weight for small cities 
- AI: able to choose instant heal promotions 
- AI: don't annex cities if it causes severe unhappiness 

Add 'on [difficulty] or higher' conditional - By RobLoach

By metablaster:
- Show religion display name when enhancement is made 
- Fix embassy trade offer crashes

## 4.17.8

By EmperorPinguin:
- AI: Update city construction evaluation of buildings + workboats 
- Automated Workers no longer run away after starting work on a tile  

By SomeTroglodyte:
- Minor UI fix: Don't display WLTK fireworks over detailed stats popup 
- Enable "triggers global alert" unique for most objects 

Civilopedia: Add Revealed By on Tile Resource screen - By RobLoach

Multiplayer Chat Support - By touhidurrr

## 4.17.7

By metablaster:
- Implementation for establishing embassies in diplomacy 
- Fix for AI civilian unit escape from threat 
- Fixed event names not being autotranslatable in mods

Hide icon for city in religion overview

Got rid of fake Github error messages when attempting to download mod preview images

## 4.17.6

Better way to brighten colors - By touhidurrr

By metablaster:
- Fix AI civilian unit not escaping enemy 
- Fix translation for an unknown civilization 

## 4.17.5

By metablaster:
- Nukes don't require line of sight to hit target tile 
- Display city defense and health in cities overview tab 
- Get notification when pantheon, religion or religion enhancement is made by other civ 

Add 'upon entering combat trigger' - By SeventhM

By SomeTroglodyte:
- Workaround for "Mod has no graphics for configured tilesets" false positive 
- No environment checks in Gradle project configs 

## 4.17.4

AI: swap spaceship parts into capital - By EmperorPinguin

Allow proposing peace between warring civs in trade window - By metablaster

Unique: `May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away` - By RobLoach

Added missing demand translations

## 4.17.3

Added global tiles countable  - By PhiRite

By RobLoach:
- Vanilla, G&K: Fix Samurai ability to build Fishing Boats 
- Add unit state for tile improvement conditionals 

By SomeTroglodyte:
- Changing gold trade amounts using buttons reflected in popup UI 

## 4.17.2

Fixed ancient ruins spawning

Possilby fixed Android dev console - requires testing

Fixed Happiness being found as a global stat

Unique builder - trigger conditionals are not a superset of global uniques

Added "Unowned" terrainFilter

By SomeTroglodyte:
- Parse localized numbers correctly 
- Better Validation of Nation colors 

By RobLoach:
- When capturing settlers, fix finding the Worker units with conditionals 
- Add ability to remove policies with ModOptions 

## 4.17.1

CPU performance improvements

Fixed dev console requiring scrolling on small UI settings

By SomeTroglodyte: 
- Fix console `tile find` for quoted input or filters, requiring correct uppercase 
- Fix mod checker not offering to auto-update Uniques for extension mods 
- Speed and Difficulty uniques treated as part of GlobalUniques

Make Machu Picchu and Neuschwanstein need a non-Natural Wonder mountain - By SpacedOutChicken

Fix display for mod names with dashes in them - By RobLoach

## 4.17.0

AI considers tile damage when deciding on which tile to heal on

Fixed building maintenance unique

By SomeTroglodyte:
- Improve ModChecker UI 
- Allow mods with no global uniques, no ruins or no difficulties file

AI: better handling of improvement-buildings - By EmperorPinguin

## 4.16.19

AI performance optimization

By RobLoach:
- Fix number popup with commas in the number 
- Add `Gain control over [positiveAmount] tiles [cityFilter]` Triggerable 

AI: update getStatDifferenceFromBuilding - By EmperorPinguin

Improve ruleset validator - By SomeTroglodyte

## 4.16.18

Sprites do not change to base color when moving

Barbarian workers no longer construct improvements

modding: Allowed stat reserve to get happiness

console: Can set game turn

modding validation: Catch empty altases.json file

Add `[relativeAmount]% [resourceFilter] resource production` Unique - By RobLoach

Refactor RulesetValidator into two files - By SomeTroglodyte

Fix broken chart in victory status - By metablaster

Edge restructure update - By hackedpassword

## 4.16.17

Increased font size so it looks less blurry on large screens

'connect road' works when railroads don't exist in the ruleset

'connect road' acknowledges availability uniques on road/railroad

Simplified requirements for adding a new demand

By EmperorPinguin:
- Autoplay: don't assign citizens according to AI Personality 
- AI: remove thingsToFocusOnForVictory 

## 4.16.16

By metablaster:
- update missing world screen image annotation 
- Table and colors for diplomatic relations between human players in diplomacy screen. 
- Fix automated units not auto upgrading when enabled
- Fix broken translations 

By RobLoach:
- Allow using `[relativeAmount]% Gold from Great Merchant trade missions` on units 
- Fix Civilopedia "required Building" links for Wonders 

"Stacked with unit" conditional unique - By PLynx01

Hexarealm edge tiles restructuring - by hackedpassword

## 4.16.15

Fixed Gold being translated in trade offer with peace treaty

By metablaster:
- Improved diagram colors in global politics 
- Fix crash for automated Fighter units 
- Fix automated long range units not heading to enemy city

modding: Show status page for civilian units - By SomeTroglodyte

Add "don't spy on us" has a demand - By Emandac

## 4.16.14

Mod checking: Catch unexpected validation exceptions 

Add uniques to remove tile resources and improvements - By RobLoach

Upon Ending Golden Age unique - By PLynx01

Fix Civilopedia when opened for a religion-free ruleset without a loaded game - By SomeTroglodyte

## 4.16.13

Fixed techs showing "0 turns" and yet not being researched

Fixed trigger uniques with broken city filters

Only buildable improvements are viable comparisons for determining if a City Center can provide a resource

Added "era number", "speed modifier for stat" countables - by SomeTroglodyte 

Add buildingFilter to building maintenance unique - By PLynx01

Add Civ unique dialogue when asking for Declaration of Friendship - By ReallyBasicName

Improved AI road planning - By metablaster

## 4.16.12

Modding:
- Warn of rendering performance problems for split atlases
- Avoid crash from "in this city" uniques when not applied in the context of a city 

New countable for adopted policies supports policyFilter - By SomeTroglodyte 

Added UponLosingCity unique - By PLynx01

By RobLoach:
- Vanilla, Gods & Kings: Fix Hagia Sophia effects
- Add Great Person group names 

AI: updated wonder evaluation - By EmperorPinguin

## 4.16.11

Don't spawn prophet in holy city if it's been taken over

City location ranking takes modded city work range into account

Modding: Avoid crashes from "free buildings that no longer apply once they're in the city / change which buildings are free"

City stats table immediately scrollable again, AND scrollable all the way to the end

By RobLoach:
- Add unique `denounced` quotes 
- Mods can add voiceovers for start intros 

Fix showing negative mod download progress - By SomeTroglodyte

## 4.16.10

Disabled android-specific behaviour of text fields since it's broken currently :|

Fixed city screen buildings panel not fully scrollable

Only add mod search textboxes if there are at least 10 mods (cleaner UI)

uniques: Add a "not constructed by anybody" conditional unique - By RobLoach

## 4.16.9

AI: Don't choose one-time-action promotions

Display city-state bonuses per relationship level

Conditional city-state bonuses colored green only when conditional applies

By RobLoach:
- Add ability to remove entire policy branches 
- Gods & Kings: Great Generals cannot enter a golden age 

Netherlands trade bugfix - By EmperorPinguin

## 4.16.8

Unit triggerables with triggers, no longer trigger on new units

Clicking on statuses opens promotion screen

AI: 
- City states disband great prophets
- Don't offer peace when we're just about to take a city

Multiplayer:
- Better, less buggy resign/skip

Modding:
- Fix global unique validation
- Validation catches 'global triggers attached to unit triggerables', which will have no effect

By SomeTroglodyte:
- Fix Resources Overview to work as intended 
- Add an 'XP' column to Units Overview

## 4.16.7

Countable can accept arbitrary mathematical Expressions - Kudos AutumnPizazz for kicking this off, SomeTroglodyte for parser implementation!

Added 'search mods' in new game screen and mod checker

Fixed bugs in unique validation and in countable validation

Allow automation for settlers with conditional settling uniques

Add "Cannot embark" unit unique - By PLynx01

## 4.16.6

Demands/Requests from other civs do not stop other events registering

Better 'Pay to improve CS resource'

Seed pillage randomness to avoid save-scum

Modding: 
- Added misspelling tests for uniqueTo for buildings, units and improvements
- Triggerable uniques on promotions only activate once when added on creation
- Beliefs can have AI decision modifiers, and do in base rulesets

Finish city-state quests upon acquiring great person

Prevent AI placing units near citadels - by EmperorPinguin

## 4.16.5

Added improvement image toggle next to minimap

Added indication when attacking, for which tile we will attack from

Fix opening Civilopedia from main menu when easter eggs enabled

Remove messages from defeated civilizations

Don't automate naval units in cities that only lead to enemies via impassible water routes (e.g. ice)

Uniques are never sorted alphabetically

Modding: Added validation for global unit uniques

By Ouaz:
- Fix untranslated string 
- Fix personality names 

## 4.16.4

Tile swapping with transported units in the tile, checks *who* is transporting the units

City states don't get continually researchable techs automatically

Modding:
- Resolved automation crash for units with "gain free [building]" uniques
- Allow multipying cached uniques

By touhidurrr:
- more detailed multiplayer authentication

By SomeTroglodyte:
- CityScreen's top-right widget better sized for mobile
- Better save/load in map editor

## 4.16.3

Ruleset validation: Hide performance suggestions from users, they're now mod-checker only

Maintenance for improved Code quality

Prevent wrong multiplicators when using the "for every [countable]" Unique in complex ways

Resolved  - City states don't get continually researchable techs automatically

By SomeTroglodyte:
- Map editor save/load now support the PgUp/PgDown keyboard keys
- Improve validation of Countables
- Fix NullPointerException from Countables evaluation

## 4.16.2

Added settings for circles vs hexagons for movable tiles

Modding:
- Added 'civ checkcountable' console command
- Trigger 'on tile enter' before removing barb camps / ruins
- Suggest conditional order in uniques, for performance
- Harden unique parameters to disallow negative numbers where not relevant

Eureka unique - By EmperorPinguin

By SomeTroglodyte:
- Improve save file name and errors handling
- Better Countable handling

Fix untranslated strings - By Ouaz

## 4.16.1

Changed "tiles we can move to" indication from circles to covering the tile

Convert 'conditional settlers' to workers upon capture

Selected unit fadein/fadeout it much more visible

Added notification when unit set to sleep/defend until healed has fully healed

Faster listing for existing saves - By SomeTroglodyte

Pillage uniques moddability - By EmperorPinguin

Civ V - Gods & Kings: Fix William personality - By RobLoach

## 4.15.20

Fixed autosave crashes when saving to external files on some Android devices

Modding:
- Replaced semi-working policy branch restriction with countable + Unavailable
- Negative stat percentages from buildings displayed properly in city screen.

Experimental UI animations change. - By k-oa

By EmperorPinguin:
- Add pillage yield uniques 
- Add game progress modifier

By SomeTroglodyte:
- Minor refactor: Save/Load game loading image uses `LoadingImage` 
- Mod download robustness 

## 4.15.19

Modding: Warn against Resource uniques cannot use countables that depend on citywide resources

Animated healthbards in battle menu - By k-oa

Forest chopping moddability - By EmperorPinguin

## 4.15.18

Hide bomb shelters when nuclear weapons are disabled

Decrease Out Of Memory errors on crash screen

Modding: 
- "Costs [amount] [stockpiledResource]" accepts game speed modifier
- "Must be next to [tileFilter]" includes the center tile as well

Stats in notifications no longer with black icons

Console: Allowed alt-navigation and deletion

Support BNW score formula - By EmperorPinguin

## 4.15.17

Science points no longer 're-bonused' on overflow

Updated docs regarding event choice fields

By k-oa:
- Added animation for unit movement button 
- Changed the Settle sprite to match style of AbsoluteUnits 

Auto promotions fixes - by Emandac

## 4.15.16

Sort city religion overview by number of followers

Made Prince difficulty "truly balanced"

Modding: 
- "Not shown on world screen" applies to promotions and statuses
- "Never appears as a Barbarian unit" also affects upgrades
- Remove great person point accumulation for units no longer available in this ruleset
- Allow atlas generation when using --data-dir option (e.g. Windows, installed via MSI)

Correct puppet city description - By Ouaz

## 4.15.15

Added uniques to make AI value resources at set prices 

Allow trading stockpiled resources

Added unique to change promotion XP cost - By Emandac

By EmperorPinguin:
- Bugfix: puppet science cost increase 
- Fix puppet city description 

Add a filter for religions - By SeventhM

## 4.15.14

Modding:
- Added 'worked' and 'pillaged' tile filters
- unitFilter catches status names
- before/after/while researching tech uniques accept techFilter
- "Will not be chosen for new games" works for major nations

Fixed crash when moving selected spies via long-click

Console tile checkfilter works for resources

By EmperorPinguin:
- Add tech cost uniques 
- Move CS tribute modifiers to mod constants 

tooltip for purchase blocked by unit - By saejo

## 4.15.13

Add .ico file to Windows zip

MacOS dock icon

Added 'tile setpillaged' to dev console

Fixed duplicate units by spamming upgrade

"upon gaining the [promotion] promotion" activates for free promotions

Added statuses to promotion screen

Hide 'hidden in world screen' resources from overview tab

Solved 'duplicate resource' bug

Fix free populatoion buildings not working when settling cities - By SeventhM

AbsoluteUnits ancient era Settler - by Basil

## 4.15.12

"Abundant resources" with mods with loads of resources no longer crash

AI: choose policy branch at random between those with the least remaining policies

modding: rename "experience" with "XP" for unified naming

Move Spy tech steal modifiers to mod constants - By EmperorPinguin

## 4.15.11

UI: Fixed edges for edge tiles on word wrap maps

Fixed automated road connections attempting to go through impassible tiles

Modding: Changed stockpile names to be human readable

Units with logistics that attacked but did not move, no longer heal

Added feature to save unitType promotion - By Emandac

Add "Will be destroyed when pillaged" unique - By PLynx01

## 4.15.10

modding: Validate tech row value

UI: More visible railroads

Display unexplored tiles 1-tile out from explored tiles

Retain zoom when moving between different city screens

Stats in notifications no longer have number format 'baked in'

Map no longer makes map options left-right scrollable

Only show 'move spies' button if there are places to move spies

Add Shuffle Civ option - By itanasi

## 4.15.9

Rendering performance improvements

Map example for new games is *only an example* and does not cause lag

Generate map tab in map editor properly scrollable

Added missing attack notification translations

Fixed unit statuses causing game to be unloadable 

By itanasi:
- Add small Skip/Cycle Button
- Settling Suggestion Improvements

Add turn start unique - By SeventhM

## 4.15.8

"Triggers a global alert upon build start" works for units

UX:
- Added generated map type preview in New Game screen :D
- Better panel sizing in New Game screen

Fixed multiple buy buttons in construction info table

## 4.15.7

Revert all texture packing - solves several bugs at the expense of the faster rendering

Avoid rare crash when failing to load mods on Android

Fixed Unciv not starting on MacOS

modding: 
- Added 'upon entering a [tileFilter] tile' trigger
- Added validation for UnitType.movementType
- populationFilter now accepts Specialist names

Fix Assignment Cycling - By itanasi

## 4.15.6

Fixed white blocks on new game from new game screen

Added stat categories for buildings in the construction list

Spy randomness is different for different spies in same city

Resize map with drag gesture - By sulai

Add info about adding project - By itanasi

## 4.15.5

Huge rendering performance changes for modded images - merely "major" change for non-modded images

Automation handles "Found City" uniques with limited uses correctly

By sulai:
- UnitTable: show a summary when no unit is selected 
- Fix sticky tooltips 

## 4.15.4

Greeting stats translated correctly in notifications

Multiplayer screen correctly handles errors when downloading mods

"Cannot attack" accepts "vs" conditionals

By SeventhM:
- Add a field for global unit uniques 
- Allow building improvements on terrains that only allow some improvements when it has multiple uniques 
- Add conditional for when you aren't in a golden age 

Added an UniqueType to Found puppet city. - By Emandac

By itanasi:
- Set Avoid Growth=false on city capture

## 4.15.3

Tech screen performance improvements

Notification for city conversion when removing heresy

Fixed unique buildings/improvements with zeroed stat not showing before vs after

By sulai:
- Show "x units due" on Big Button, setting for cycling units 
- Constructions table: avoid vertical movement of lower table 

Added option to change the Maximum Autosave turns stored - By Emandac

Add hotkey for Idle Unit cycle buttons - By itanasi

## 4.15.2

Added notification when enemy religion spread converts a city 

Added "has denied your trade request" notification 

Ruins stat gifts modified by game speed

Fixed padding for map view icons for small minimaps

Fixed rare crash

Remove invalid "last seen improvements"

By sulai:
- Tweaks for Screen Size small-portrait 
- Map pinching, revised 

Hide invisible resources for AI - By EmperorPinguin

Move Until Healed wake up to startTurn so Fortify lasts until start of turn - By itanasi

## 4.15.1

Show total number of cities in city table

Avoid ANRs when loading games

Resolve rare crash for corrupted game settings

By sulai:
- Add GoogleMaps-like pinching (!!!)
- Tweak UI city screen
- Correct alignment of text to icons

## 4.15.0

Modding:
- "Costs [amount] [stockpiledResource]" works for improvements
- "free building" unique respects replacement buildings
- Adjacency checks do not check the current tile
- Allow city level stockpiles - By SeventhM

UI:
- Larger 'per turn' text for gold and faith
- disabled buttons no longer cause click-through
- Improved city screen queue - By sulai
- Edit Babylon's icon - By SpacedOutChicken

City states will get angry at you if you steal their lands - By Emandac

## 4.14.19

Memory performance improvements

City-state stat percent bonuses apply correctly

More uniques work with "in this city" conditional

Terrain images in fonts no longer flipped

Pathfinding: Avoid unfriendly city state tiles when this doesn't affect movement speed 

Change Babylon's image to Lamassu  - By SpacedOutChicken

Let AI choose healing promotions - By EmperorPinguin

## 4.14.18

Performance - Faster map update on click

"close unit table" button does not cycle units

By SeventhM:
- Allow stat from battle uniques to also give stockpiles 
- Fix gaining resources twice when it is gained from a city 

By czyh2022 (NEW!):
- Allow civs to trade with each other before settling their first cities 
- Delete duplicate trade denied message 
- Cancel the chain reaction of defense pact 

## 4.14.17

Fixed ANRs for:
- URL checking for Github URL with query
- Global politics table
- Playing overlay music in city screen

Flank attack unique works with 'vs' conditionals

Fix rare Android crash where we don't have permissions to copy external mods on app start

Add unique for increased improvement rates rather than decreased build times  - By SeventhM

UI: various improvements mostly relating to centering and WorldScreenTopBar  - By Toxile

## 4.14.16

CPU performance improvements

UI: 
- Better "close unit table" button 
- Better options checkboxes, slider buttons, multiplayer server UI  - By Toxile

Bugfixes:
- Buy button active when civ can purchase items in puppets
- Paused music no longer resumes on game resume

By SeventhM:
- Unified unique for gaining stats/stockpiles 
- Pantheon cost respects game speed modifer 

AI: 
- Better Food Weights for citizen management - By itanasi
- Prevent incorrect settler retreat - By EmperorPinguin

## 4.14.15

UI improvements:

- Tile Info Table 
- Changed Black to Charcoal 
- Unit description table

Fixed cities built on pillages roads colored red

Fixed scroll position indicator

Fixed Thai diacritic support

Added "exit" button in world screen popup menu

Fixed AI religion belief assessment

Increase starting Luxury amount to match Civ 5 - By SeventhM

UI fixes: dividers, checkbox-to-text spacing, multiplayer tab connection button  - By Toxile (new contributor!)

## 4.14.14

Automated air units respect "Cannot move" unique

Resolved rare New Game Screen "application not responding" errors

AI: 
- Don't pick most expensive tech as free tech, if it's marked as "0 weight for AI decisions"
- Decreased base Fort value to not build it instead of useful improvements

Remove images of expended units

Display city state type name for battle bonuses

"Unit built" notification selects the built unit

## 4.14.13

SIGNIFICANT memory performance improvements for large maps

"Stats from tiles" uniques work with terrain + improvement filter combos

Layout for resource icons in city screen when in resistance - fixed

Remove server notifications - bad user experience

hexarealm: Fix Snow-Lake edges - By RobLoach

## 4.14.12

Allow unit triggers to be used in unit-triggered events

Memory and rendering performance improvements

Solved ANRs while building crash screen

Spies assigned to cities moved to other civs are returned to hideout

Automated workers don't remove terrain features without the proper tech

Removed 'please' from Civ demands to make them more demand-y

Fix promotion uniques being ignored if it didn't match a promotion names  - By SeventhM

Notifications can link to URLs  - By touhidurrr

## 4.14.11

Added Github + Discord icon links on main menu

City construction progress is reset when puppeting, not when annexing

Mods:
- Allow loading games where a buildings' "replaces" has been removed
- Allow loading games with natural wonders that have been removed from mods
- Only add city ruins improvements if they exist in the ruleset

Added lake-land edge tiles - by legacymtgsalvationuser69544

## 4.14.10

Fixed Flood Plains generation

Fixed crash when swapping while retreating

Added debug option to show tile image locations

Worker units do not try and swap with non-adjacent tiles

Better MP update error handling

Resolved crashes due to incorrect music state

Console cannot change player type for non-major civs or allow adding cities to spectator/barbarian

## 4.14.9

CPU performance improvements

Uniques hidden from users do not show icons in tech tree

Disallow slashes in mp game names

Added more options for UI skin mods  - By GGGuenni

By SeventhM:
- Fix capital indicating uniques ignoring gamestate 
- Fix tile defence uniques ignoring unit state

## 4.14.8

Avoid 'application not responding' errors on Android when attempting to start a multiplayer game

Avoid out of memory errors when updating multiplayer games

Don't reject constructions for missing stockpiled resource costs, AFTER you've already paid them

Memory performance improvements - By SeventhM

Growth changes - By itanasi

## 4.14.7

CPU performance improvements

Modding:
- Mod checker accepts civ filters as tile filters
- Great General unique works with conditionals
- Nat wonders land->water conversion no longer causes rivers on water tiles

Growth nullifying uniques do not nullify starvation

Removed Scenarios button, since the 'new game' screen handles scenarios better

Allow filter uniques to have conditionals and work with modifiers  - By SeventhM

## 4.14.6

Rendering performance improvements

AI does not offer open borders trade if other side already has open borders

Don't allow trading away max gold/max gpt to multiple civs

Added 'unit remove all' console command

In trade, if one side has *negative* resources of a certain type, the resource on the other side is colored green

## 4.14.5

Coast edge images for HexaRealm tiles by legacymtgsalvationuser69544 :)

Place edge images over terrains and under improvements, where possible

City-state unique units are not taken from civs within this game

Don't assume the city-state ally knows the civ that attacked the city-state

Modding:
- Comment text is displayed on event choices
- Don't allow triggers to decrease city population below 1

Added owned tiles countable - By PLynx01

## 4.14.4

Memory optimizations

AI: Use Great Artists for Golden Ages 

modding: Added "on [difficulty] difficulty" conditional 

Avoid Growth and Food Ranking Improvements  - By itanasi

## 4.14.3

Fixed Wait action when auto unit cycle is disabled

Unresearchable techs not added when starting in advanced era

modding:
- Added validation to event uniques
- "upon gaining/losing the [promotion] status" triggers correctly
- "upon building a [improvementFilter] improvement" triggers correctly
- "Becomes [terrainName] when adjacent to [terrainFilter]" accepts conditionals

## 4.14.2

DoF popup requires choosing an option

Downgraded back to LibGDX 1.12.1 to solve Wayland and AWT issues

Resource uniques are initialized correctly

Memory performance improvements

Workers will repair pillaged Great improvement tiles - By Emandac

Avoid Growth blocks New Population - By itanasi

## 4.14.1

Modding: 'upon gaining/losing the [promotion] status/promotion' triggers now work correctly

Allow AI to use perpetual culture/faith conversions

Added version number to main menu

By itanasi:
- New 'Guard' action for units that can retreat from combat 
- Align Civilopedia on Idle Units and Wait command to current behavior 

## 4.14.0

Wait action selects next unit

By sulai:
- Better city screen buy button location 
- Do not create resource notification for unresearched resources 

Fix constructions that's always visible showing when belonging to another civ  - By SeventhM

## 4.13.19

Update mods even if we have cached data 

Modding: Unit icon falls back to UnitTypeIcons/<unitType> successfully

Cities reduce tile movement cost to 1 (on e.g. hills), per Civ V

Add Specialists Tutorial - By itanasi 

Add `<when espionage is enabled>` conditional  - By RobLoach

Allow purchasing wonders with gold when explicitly given a unique - By SeventhM 

## 4.13.18

Better AI decisions for policy branches

Modding: 
- Allow fallback to "UnitTypeIcons/$unitName" if "UnitIcons/$unitName" does not exist
- Added unique-weighted decision for policy branches
- Better "hidden when" uniques for disabled religion, victory types

Resolved rare crash when map contains improvements not in ruleset

Add unique to show construction when unbuildable - By SeventhM

## 4.13.17

Mod management screen:
- Cache online mod list for fast loading
- Always allow mod search

Modding: 
- Allow removing free policies
- Resolved crash on modded game with no capital city indicator

AI: Improved automated worker tile selection

Solve ANRs due to resuming music player which is in an unplayable state

Don't auto-replace holy sites in G&K - By EmperorPinguin

## 4.13.16

AI: Better rules to not build unit-carrying units

Units that can withdraw before melee do not do so when escorting civilian units

Modding: 
- Filtering uniques are also checked for in unit types
- Added "if [modFilter] is not enabled" conditional

UI: Free tech pickable in any way you enter the tech screen

## 4.13.15

Fixed spies stealing multiple tech steals in one turn

Resolved new game ANRs in a better way

AI: 
- Keep 'don't spread religion' promises better
- Greatly discourage attacking stronger enemies

Modding:
- Added "Remaining [civFilter] Civilizations" as countable value
- Conditional phrasing: "for [civFilter]" -> "for [civFilter] Civilizations"
- Resolved badly configured ruins causing crashes
- Added 'City-State' as value for nationFilter

## 4.13.14

Ruleset validation for personalities with victory types not present in ruleset

Added mod download percentage tracking

Adjacent tiles updated when tile in changed in map editor, to update relevant edge tile images

Remove "-0 HP" from city attack notifications

Improve AI city settling, science game, and belief picking  - By EmperorPinguin

Add two population-related conditional uniques  - By PLynx01

## 4.13.13

Minimized MP game update IO by ignoring games older than 2 weeks for 'update all'

Resolved ANRs when pausing game due to game clone time

Resolved ANRs when starting a new game (checking for multuplayer server connection)

Protect terrainImage against incorrectly configured mods (natural wonder turnsInto is not in ruleset)

Resolved race-condition error for loading terrain icons

## 4.13.12

Cannot have 2 research agreements at once due to counteroffers

Modding: Added "upon losing/gaining the [promotion] status" unit triggers

Mod branch parsing (downloading from user input url) can now handle branch names containing "/"

Fixed "Top" edge tiles not showing - kudos @legacymtgsalvationuser69544

## 4.13.11

Better AI evaluation for 'win the game' buildings

Modding:
- Trigger uniques from religious beliefs activate correctly
- Added "removing the [promotion] promotion/status" unit action modifier
- Added 'upon gaining/losing [promotionName] promotion' unit trigger uniques 
- Allow comment uniques and timed uniques in event choices

Re-activate Thai language, now with diacritic support

Improve AI tech and policy choices  - By EmperorPinguin

## 4.13.10

Tilesets: Added edge tile images!

Great prophets bought in city with different religion do not get incorrect warning popup

Getting all resources does not eliminate WLTK day

modding: Added validations for 'replaces' being set when 'uniqueTo' is not (units/buildings/improvements)

Invalid MP games cannot be 'joined'

Changing rulesets in new game screen no longer leads to fake error warnings

## 4.13.9

Significant memory improvements - should allow for much larger maps on memory-constrained devices!

CPU performance improvements 

Solved rare automation crashes with escorted units

## 4.13.8

Allowed starting Scenarios, including Multiplayer, through the "New Game" screen! :D

WLTK + continuous rendering no longer cause city tiles to be dimmed

Fixed Scenario mods being undownloadable and needing to restart game to access Scenario picker

Added Barbarian Musketman and Worker variants for AbsoluteUnits

Images are restored to ruleset correctly when resuming open game screen

BNW unit sprites for HexaRealm - By GeneralWadaling

## 4.13.7

Performance improvements!

Added Barbarian image variants for AbsoluteUnits by Pelo

AI is displeased when you become the new ally of a city-state it was the ally of

kick/skip turn in mutliplayer only active if the game contains the current player ID

## 4.13.6

Performance improvements!

AI prioritizes unit upgrades over purchasing new constructions

Units are not added to cities in resistance if non-resistant cities are available

## 4.13.5

Modding:
- Make event choices ruleset objects, with standard "uniques" field
- Added "AI choice weight" for event choices, techs, policies and promotions 

Moved screen orientation setting from advanced tab to display tab

Performance: only trigger population reassignment on new buildings when it really changes something

Better stat-related checks for buildings in cities 

## 4.13.4

Don't allow constructing stockpiled-resource-requiring constructions when lacking the resources

Consume stockpiled resources when purchasing constructions that require them

Don't show "ok" ruleset validations when starting a new game

Set "auto assign city production" to false for new players

Automated units retreat from Barbarians when not at war - By EmperorPinguin

## 4.13.3

Fixed 'conquer city' automation crash

Minor memory performance improvements

Natural wonders uniques generalized to work for terrain feature as well

Prep work for unit-based Events :)

Dev console displays enum options correctly when given incorrect options

## 4.13.2

Added "upon damaging a [mapUnitFilter] unit" which can trigger a unique on the *enemy* unit -
All unit trigger uniques start with a targetting parameter to reflect this

Added "[unitTriggerTarget] is destroyed" unit triggerable

City overview updates when entering & exiting city

Fixed "don't settle cities" demand triggering "stop spreading religion" demand

Skipping turns for a game updates the MP screen

AI worker improvements - By EmperorPinguin

## 4.13.1

Unit statuses, which are temporary promotions!
- Can be applied with "This Unit gains the [promotion] status for [positiveAmount] turn(s)"
- Can be removed with "This Unit loses the [promotion] status"

When selecting a unit, show only arrows relevant to selected unit

Better AI conquering of cities

Allowed specifying custom colors for unit promotions

"ok" warnings now colored in 'accept mod errors' popup

Discourage spreading religion by AI to civs they've promised to not do so

## 4.13.0

Fixed Civilopedia not showing non-unique buildings and units on techs

UI: Show terrain icons in text
UI: Fade in newly-explored tiles

Added "don't spread religion to us" demand

Modding: Improvement, Unit and Building 'uniqueTo' field can apply to civ *filters*

AI changes  - By EmperorPinguin

## 4.12.19

Multiplayer: Add button to skip current player after 24h inactivity 

Strategic balance applies only to major civs, as per Civ V

Automated settlers take conditionals on settling locations into account

Modding: Added ruleset validation that 2 policies in the same branch do not have the same position

"Land to water" natural wonders do not cause ruins on water tiles

## 4.12.18

Reduced (uncompressed) save file size by 15%, with 15% pending later versions

Multiplayer improvements:
- Add descriptor (you/friend name/unknown) to current player
- Auto-download missing mods when joining multiplayer game
- Can force-resign any human, if 'admin' spectator or player is inactive for 48h
- Disable resign button on games where it's not your turn

Fixed city console rename to set exact text (not quoted/lowercased)

Conditional that tests if a mod is enabled - by @SomeTroglodyte

## 4.12.17

Modding:
- More unit uniques applicable globally
- Recognize Tutorials.json file in ruleset validation

"Unavailable" promotions are unavailable in UI as well

Can go to war with no warmongering penalties if allied or protected city-states have been attacked

City states no longer gift units that push us over resource limits 

Allow unitset and tileset overrides for base ruleset mods

Enhance modding freedom for Natural Wonders  - By SomeTroglodyte

## 4.12.16

Fixed world wrap for games saved during 4.12.15

'gain control over tiles' trigger leaves your tiles alone 

By SomeTroglodyte:
- Fix new improvements becoming visible on non-observed tiles 
- Fix rare crash opening overview on turn 0 
- Console command to change difficulty 

AI is less motivated to declare war at higher difficulty levels  - By tuvus

By itanasi:
- Civilopedia Entries: Food, Production, Science, and Gold 
- Add Air Intercept Range to Civilopedia card 

## 4.12.15

Modding:
- Validate mod folder names and catch misspellings
- Improvement Unique converted to trigger - "Gain control over [tileFilter] tiles in a [amount]-tile radius"
- Resolved map type generation errors kudos @SomeTroglodyte

Automated workers prioritize replacing features to get to lux/strategic resources

Changed 'default map' parameters to rectangular + world wrap

Fixed "when friends" / "when allies" translations

Resolved 'move to next unit' problems - kudos @vincemolnar

## 4.12.14

Add CLI argument to specify the data directory - this will allow native install on Windows, hopefully

Modding: New unit triggers to gain/lose movement points

AI citizen focus improvements - By EmperorPinguin

Correct spelling of 'Svannah' to 'Savannah' across project.  - By aaronjfeingold

## 4.12.13

Special nuke animation, to make it feel more momentous

Better repeatable unique randomization

Modding:
- mapUnitFilter no longer errors for correct values
- Better display of "object is missing a name" errors
- Fix endless loop when many units can transfer movement to each other

By tuvus:
- Made Gold Gifting moddable 
- Make AI difficulty moddable 

Repair Qingmin holiday  - By SomeTroglodyte

Civilopedia Updates: Trade Routes and Air Combat  - By itanasi

## 4.12.12

Terrain civilopedia displays improvements that can be placed

Fixed paradrop to areas outside of movement range

Fix number translation removing 0 digits from strings like "1,023"

Modding:

- Catch & fix unknown json filenames
- Added autoreplace for deprecated modifiers
- "counted unit actions" can handle different parameters of same unique

AI can no longer buy wonders

Gift gold fix  - By tuvus

Fix parameter mapping for UnitSupplyPerPop  - By SomeTroglodyte

## 4.12.11

Modding: Added unique builder screen, accessible from mod checker :D

Fixed Workboat construction automation ignoring existing workboat in city

Fixed air unit movement on map

Fixed icons in Wonder location "near city" for cities with nation names

Rome/Babylon capital takeover no longer renames like city states

By tuvus:
- Animations now show escort units 
- Escort movement fix 

More Numbers Translations Coverage  - By touhidurrr

Correct the colors of the flag of Ukraine  - By kostia1st

## 4.12.10

UNIT MOVEMENT ANIMATION!

New uniques:
- "Can only start games from the starting era" mod option
- "if [buildingFilter] is not constructed" unique

AI no longer declares war against defeated civs

Unified AI and Human gold purchase logic

Ruleset validation: Catch "building required for victory milestone but does not exist" errors

"Will not be replaced by automated units" unique fix

Fix cases where Numbers Translations does not work properly  - By touhidurrr

## 4.12.9

Solved edge-case missing images bug

Hidden conditionals are hidden in more places

Duplicate notifications are all shown

Fixed infinite air units in cities

Friendship-based modifiers calculated correctly

By SomeTroglodyte:
- UX: Notifications for map units select better when tapped 
- Fix GlobalPoliticsOverviewTable table layout after switching back from diagram 

By SpacedOutChicken:
- Correct Unique parameters doc to display terrain quality 

## 4.12.8

Performance improvements

Better simulation automation

Added missing deprecation validation for unit uniques

By SomeTroglodyte:
- Translation updates work for diacritic-using languages 

For languages with special number characters, translate numbers to selected language  - By touhidurrr

## 4.12.7

AI: Better placement for Great Improvements

City focus resets to default when annexed

Allow multiplication for event triggers

"Consumes resources" unique not displayed twice for constructions in city screen

Added promotionName as possible value for mapUnitFilter

Fixed team war giving "they declared war on us" notification to a civ declaring war - By tuvus

New language translation - Bangla - by touhidurr

## 4.12.6

Many performance improvements!

By SomeTroglodyte: 
- Support for languages using Diacritics (e.g. Bangla)  

By tuvus:
- Improved unit automation for defending cities 
- Warmongering doesn't apply to civs that are angry at the target civ 

Optimize screen orientation  - By HChenX - *NEW CONTRIBUTOR!*

AI behaviour changes  - By EmperorPinguin - *NEW CONTRIBUTOR!*

## 4.12.5

Re-add 'construct great improvement' automation for great units that can't do their main actions

Promotion added to unit already containing that promotion, does not retrigger trigger uniques

"Unavailable" units cannot be upgraded to

By SeventhM:
- Consider passive strength bonuses for force value 
- Pass in civ for building on tiles 

By SomeTroglodyte:
- Larger clipboard size on Desktop
- Add a Unique allowing an Improvement to specify which Resource(s) it improves 

## 4.12.4

Modding version!

- Triggered uniques accept multiplying modifiers
- Better "Withdraws before melee combat" unique
- Clearer "no damage penalty for wounded units" unique
- Countables for Cities, Units, Buildings allow filters

Performance improvements for religion

UI: Improve load game screen - better feedback, missing templates  - By SomeTroglodyte

## 4.12.3

Modding: 
- "after [amount] turns" -> "after turn number [amount]"
- "before [amount] turns" -> "before turn number [amount]"
- "when number of [countable] is greater than [countable]" -> "when number of [countable] is more than [countable]"
- Clean improvement queue from improvements no longer in ruleset
- Better unique documentation

Fix performance problem for displaying air units in cities

Fix "edit existing trade" exploit

Fix console city add/remove building format - By SomeTroglodyte

## 4.12.2

Tech, policy, unit and terrain uniques provide multiplied uniques with "for every [countable]" / "for every [amount] [countable]" modifiers

Many performance improvements

Units teleport out of open borders on war declaration

Inquisitors go out of city centers when spaceship parts need to be added in

Add personality uses  - By tuvus

Improvement picker fixes - By SomeTroglodyte 

## 4.12.1

Performance improvements 

"join war" offers only valid if can declare war

Added "Will not be replaced by automated units" unique

By tuvus:
- Defeated civilizations don't use spies
- Spies deselect when moved on map 
- Civs can no longer declare war right after peace with a city-state 
- Moved automation settings to AutoPlayTab

By SomeTroglodyte:
- Fix disband spamming 
- Fix coastal rivers near Rock of Gibraltar 

Turn Privateer's ability into promotion - By SpacedOutChicken

## 4.12.0

Fixed spy automation crash

By tuvus:
- Personality implementation
- AI is more likely to sign Defensive pacts
- Giving the AI good trades is stored as credit
- Fix election crash

By SomeTroglodyte:
- Map editor can place improvements again
- Reduce size of the save game json
- Improvement queue

## 4.11.19

New unique trigger: "<upon expending a [mapUnitFilter] unit>"

Console:
- `city addtile <cityName>` takes an optional `[radius]` parameter
- `civ addtech` / `civ removetech` commands

By SomeTroglodyte:
- Use Events for moddable, floating "Tutorials"!
- Allow EmpireOverview persistence across game launches
- Moddable images for special characters

By tuvus:
- Moddable city ranges
- Extra Civ and Spy moddability

## 4.11.18

We passed 1000 versions! :D

"(modified by game speed)" modifier

Fixed "Promotes all spies [amount] time(s)" crash

By SomeTroglodyte:
- UX: Dev Console easier to use without installing keyboard apps
- Improve update of "Last seen improvement"

Reworked AI war evaluation and plans  - By tuvus

## 4.11.17

Unavailable techs work well with tech picker screen

Added ruleset check for resource uniques with resource conditionals

By tuvus:
- Added an option to disable move by long press
- Fixed spy steal tech timer
- Spy max rank can be modded

By SomeTroglodyte:
- Sortable unit overview
- Console: Improve `civ activatetrigger` command
- UI candy: WLTK fireworks
- ModOptions unique for mods to control which map gets preselected

## 4.11.16

AI no longer trusts you on resource trades if you cut deals short

Added "per every X countables" modifier

Add unit name and building name countables

By SomeTroglodyte:
- Allow mod sounds to be selected as multiplayer notification sound
- Allow access to the Dev Console on mobile devices
- Better "work has started" notifications
- Console: create natural wonder and `tile find`

By tuvus:
- Removed espionage debug setting
- Added spy steal tech timer

## 4.11.15

Modding: "for every [countable]" unique modifier

Added links to base ruleset template in docs

Fixed "don't allow era select" if the game has no techs

By SomeTroglodyte:
- Support for Zulu language

By tuvus:
- Clicking the spy button no longer allows the spy to be moved when it isn't their turn
- Added the Espionage civilopedia entry
- Construction automation rework
- Espionage button cancels moving spy

## 4.11.14

Allow rulesets to forgo capital city indicators entirely!

Default city for hexarealm does NOT have a question mark

Added ruleset validation for preferred victory type

By SomeTroglodyte:
- Mod checker reports some problems with texture atlases or their source images
- ImagePacker detects changed settings file
- Make random conditionals depend on turn

By tuvus:
- Fixed gold ruin not displaying notification
- AI worker build roads improvement

## 4.11.13

Barbarian water units no longer pillage

By SomeTroglodyte:
- Console: tile setowner, civ removepolicy
- Fix crash when a starting unit has a random conditional
- Espionage icons

By tuvus:
- Espionage: Spies can be moved on map
- Espionage: City state coup
- AI doesn't settle very unfavorable locations

## 4.11.12

By tuvus:
- More espionage UI improvements
- City state election rigging

By SomeTroglodyte:
- Fix "Move a unit" tutorial isn't completed by moving via right-click
- Fix "Translating" wiki link

Added the culture-refunding remove policy unique  - By PLynx01

Corrected Coast yields to give 1 Food and 1 Gold  - By Skekdog

## 4.11.11

Nuclear weapon uniques accept conditionals

By SomeTroglodyte:
- Console: `civ addpolicy`
- Resource Overview: Info on unavailable strategic and unimproved by allies
- Spy UI improvements
- Fix potential crash in console autocomplete

## 4.11.10

By tuvus:
- Add missing espionage uniques
- Next turn button shows move spies notification icon
- Added diplomatic repercussions for spying on a civ
- Spy rank UI and fixes
- Fixed city-state alliance join war notification

Better "hidden in civilopedia" logic - By SomeTroglodyte

Added victoryType conditionals  - By PLynx01

## 4.11.9

Added "checkfilter" console commands for city, tile, and unit, for easy mod checking

Unit movement changes - should solve some edge-case problems

"Adjacent unit" conditional takes civilians into account

Better UX for multiplayer game add & rename

Fixed "Open terrain" filter

Better "escort settler" logic

Automation fixes - By tuvus

## 4.11.8

Performance: Memory and CPU optimizations

Civilian AI wandering avoids enemy melee units correctly

Color lands by owner on max zoom out, for better overview

MP Spectator can scroll entire map

Resolved  - Safeguard against uniques specifying non-existant promotions

Fix: Android pause/resume cycle not working  - By SomeTroglodyte

## 4.11.7

By tuvus:
- Declare War Reason
- Autoplay can run on a different thread, to update game UI continuously

By SomeTroglodyte:
- Units with "no sight" should still see their own tile
- Modding: Validation for civilopediaText
- Fix: Potential crash on new game after deleting a base ruleset mod
- UX: Auto rename new capital in rare cases to prevent confusing notifications later

## 4.11.6

Military unit capturing respects "Uncapturable" unique

By SomeTroglodyte:
- Do not preselect custom map option and defer map file loading
- Prettier Events - that now respect 'hidden from users'

Fix Puppets building wonders  - By SeventhM

## 4.11.5

Better multiplayer game screen

Solved some problems with resigning MP games

Disallow creating "multiplayer" games with only AI and spectator

By SomeTroglodyte:
- Fix Invest quest stays forever
- Minor Scenarios UX improvements
- Console autocompletion can display *all* possibilities

## 4.11.4

Can nuke barbarians - By tuvus

By SomeTroglodyte:
- Two extension features for custom maps
- Images for Escort Formation
- Can click behind OptionPopup to close

By SeventhM:
- Allow policy removal unique to remove multiple policies
- Fix personality being ignored for tile rankings in small cities

## 4.11.3

Cannot trade with civs you're at war with through notification action

Remove city-state construction bonuses from difficulty

By tuvus:
- Espionage Uniques, Buildings and Policy
- Civs with spies in a foreign cities get some information

By SomeTroglodyte:
- Fix "Connect road" through Mountains
- "New game" UI improvements

Implement Same-majority-religion diplomatic modifier  - By TommasoPetrolito (new contributor!)

AI civilian units consider more triggerable uniques  - By woo1127

## 4.11.2

Mod checker warns against deprecated conditionals

Resolved edge-case crashes

Added triggerable unique to remove policy  - By woo1127

By SomeTroglodyte:
- Work boat construction automation tweaks
- Fix new game screen mod selection

By SeventhM:
- Fix softlock for spectator with free policies
- Allow Civilian units to promote

## 4.11.1

Performance improvements

By SomeTroglodyte:
- Improve diplomatic vote result screen

By tuvus:
- Water units can enter lakes-near-cities
- Workboats improve resources outside of city work range

By SeventhM:
- Allow AI to consider building stats more accurately
- Allow for replacement improvements

Add configurable natural wonder discovery stat bonuses  - By PLynx01

## 4.11.0

Hide battle table after attack if we can move, but not attack again

Maps with mods change mods visible on new game screen accordingly

Barbarian units always placed next to encampment, so they don't "jump over" tiles they can't enter to the other side

Remove mod blacklist - By SeventhM

UI Tips article additions - By Ouaz

Add UnitActionModifier for Stockpile Cost - By itanasi

Countable comparison conditional uniques - By PLynx01

fix misimplemented Dromon - By ravignir

## 4.10.22

Policy tables no longer repeat on some height/width configurations

Discard all pending trade requests on both sides when war is declared

Memory performance improvements

Personalities.json no longer precludes generating translations

Fix loop when AI is trying to remove an improvement with the same name as a terrain feature  - By SeventhM

Fixed uniques of marble - By woo1127

## 4.10.21

Fixed ruleset-dependant building filter activating *when initializing ruleset*

Fixed endless loop when unit tries to reach a tile it can pillage, but can't

Fixed rare crash on city-state diplomatic relationship update

Fix loop when improvement is unbuildable and removements feature  - By SeventhM

## 4.10.20

Modded units can construct improvements on impassible tiles

By woo1127:
- Added multi filter support for BuildingFilter
- Fixed error message of ConditionalBuilding

Better tundra color - By Caballero-Arepa

Allow improvements that don't need feature removal to be built on features  - By SeventhM

## 4.10.19

Fixed group natural wonders only spawning in single tile

Fixed crash entering trade from overview on other player's turn

Fix visual bug in event when more than one trigger is activated by a choice

Workers cannot repair improvements in enemy territory, thus avoiding repair-pillage exploit

Modding: Zero-cost constructions no longer cause automation crash

Melee Escort Attacking Fix  - By tuvus

New UnitActionModifiers to enable Stats and Minimum Movement  - By itanasi

## 4.10.18

Performance improvements!

Religious victory no longer causes crash

Worker automation takes city focus and civ personality into account evaluating stats

Free buildings granted properly when era-free cities also granted

"Connect road" unit action doesn't build on unbuildable tiles

Allow resources from follower beliefs  - By SeventhM

Add "upon entering a new era" trigger - By PLynx01

CanOnlyBeBuilt is its own conditional-friendly unique  - By itanasi

## 4.10.17

Added Events, moddable choices for triggering uniques!

By tuvus:
- Fixed swapping a unit with a unit that is escorting
- Next turn button reactivates after closing a popup menu

River terraform  - By SomeTroglodyte

New language - Norwegian - by Floxudoxu

## 4.10.16

Allow multifilter uniques to count for filtering

By tuvus:
- Skip next unit button (right-click option)
- Better Unit Actions Sorting

By SomeTroglodyte:
- City filters for cities in resistance and being razed
- Competition quests in progress display tied leaders (and your place if you're behind)

AI consider production bonuses when building  - By SeventhM

Added ConditionalWhenBetweenStatResource unique  - By woo1127 (new contributor!)

## 4.10.15

Changed tech trigger to accept tech filters

By SomeTroglodyte:
- Improve DiplomacyScreen UX (nation icons) on cramped screens
- Move DiplomacyScreen close button to top right
- Validation warning Suppression as Unique or modifier
- UI: Fix options popup "spilling" in cramped screen conditions

Allow city state uniques for nation descriptions  - By SeventhM

Add MovedToNewCapital buiding unique  - By PLynx01

Better military unit retreat  - By tuvus

## 4.10.14

AI tile evaluation considers Faith

Civ-wide uniques for city-wide resources

Added 'city addbuilding', 'city removebuilding' console commands

Add unique to conditionally control construction costs

Added conditional for exact amount of population in a city

Mod checker:
- Unique conditional corrections, and better correction
- Limit tech column building/wonder costs warnings to when required

Unit upgrade menu can scroll - By SomeTroglodyte

Military unit healing improvement  - By tuvus

## 4.10.13

Add AI for land-based nuke units

Keep opened mods open and at top of list when reloading mods in 'locate mod errors' tab

above/below HP conditionals work outside of combat

update uniques upon taking damage and other situations

By SomeTroglodyte:
- New notifications bell icon with actual count
- Fix TabbedPager geometry - the cell for the close button needs to be ignored in the rest of the Table!

Added ConditionalAboveHappiness unique - By PLynx01

## 4.10.12

Terraform unique triggerable from improvements

By SomeTroglodyte:
- Right-click/longpress for World screen city buttons
- Fix some uses of "hidden from users" modifer
- Fix fortified units upgraded to unfortifyable ones keeping fortification
- Empire Overview Screen closing now with same UX as Civilopedia

Added unit escorting formation!  - By tuvus

Allow conditional timed triggers for unit actions &c  - By SeventhM

Allow Barbarians to make set-up ranged units  - By SpacedOutChicken

## 4.10.11

Terraforming unique! 'Turn this tile into a [terrainName] tile'

Fix timed uniques without other conditionals

Only allow a trigger-based unit action if actionable

By SomeTroglodyte:
- Conditional 'While Researching'
- External links: Right-click and some housecleaning
- Fix Maya "Long Count" unlock translations

By SeventhM:
- Fix victory focus being ignored
- Add unique for Personality to avoid building object

## 4.10.10

By SomeTroglodyte:
- Great Person Point breakdown UI in city
- Fix creating odd-width rectangular no-wrap maps

By SeventhM:
- Allow barb camps to function after giving ruins effects
- Conditional for building in amount of cities

AutoAssign Population with Food Converts to Production  - By itanasi

## 4.10.9

Allow lower-case "all" for all filters
Initial Civ Personality implementation  - By SeventhM

By SomeTroglodyte:
- Autoplay menu cleanup
- Key binding categories properly sorted in options popup

By tuvus:
- Workers wake up on tile expansion

## 4.10.8

Fixed unhappiness effect when at 0 happiness

All BaseUnit uniques, e.g. Never appears as a Barbarian unit, Limited to [amount] per Civilization, can be placed on unit type

By SeventhM:
- Add unique for increasing price every time it's built
- Fix mistakes with unavailable unique

By SomeTroglodyte:
- Fix Capture when a teleport was necessary
- Tweak Overview Politics Diagram for a defeated player
- Patch AlertPopup to correctly close when it cannot find a required asset

## 4.10.7

Allow city filters to be multifilters and not throw ruleset errors

Korean science boost only applies for buildings *in capital*

By SeventhM:
- Avoid crash from city combantants with combatant conditional
- Split Strat Balance and Legend Start into their own checkboxes

ThreatManager improvement  - By tuvus

## 4.10.6

Fixed crash due to ranged unit trying to capture civilian but being unable to reach the tile

Added unit conditional support to pillage yield uniques

Policy picker colors are moddable - By SomeTroglodyte

Players can't move spies when it is not their turn  - By tuvus

## 4.10.5

Added 'copy to clipboard' button on map errors so we can debug them

Happiness building performance improvement

By SeventhM:
- Add additional cityFilters
- Fix gain stat modifier by speed unique action text

By SomeTroglodyte:
- RulesetValidator: Raise severity for untyped uniques with parameters

## 4.10.4

Modding:
- New tileFilter `your`, for tiles belonging directly to you
- Mods can use the Hills and mountains distribution uniques on Land or Feature terrains

By SomeTroglodyte:
- World screen resize delayed
- Unit actions dynamic paging
- Minor Mod manager fix, lints and dox

By tuvus:
- Workers now build forts
- Worker automation option fix
- Ranged units capture civilian
- Spectators can now see selected civ city-state influence bars

Unify unit and civ triggers  - By SeventhM

## 4.10.3

Modding:
- Add "Unavailable" unique (counterpart to Only Available)
- Unified resource generation checks to include all uniques always
- 'not shown on world screen' unique accepts civ conditionals
- Added adjacency conditionals
- CityCombatant.matchesFilter contains multifilter and civ filter

By tuvus:
- Air unit automation improvement
- Espionage automation

By SeventhM:
- Fix errors when starting games as Maya

## 4.10.2

By SeventhM:
- Fix Spectator stats when viewing another civ
- Allow units to upgrade to more than one unit
- Fix problems with stats from tiles and improvements

By SomeTroglodyte:
- A Conditional checking for a Building globally
- Unit actions "paging" for smaller screens
- Fix RequiresBuildingInSomeCities not being displayed in city constructions as rejection reason

Improved Spectator selected Civ coloring  - By tuvus

## 4.10.1

'cities auto-bombard at end of turn' is a user option

Improved Console autocomplete - By SomeTroglodyte

By tuvus:
- AI worker road priority rework
- Spectators can see the diplomacy screen of the civ that they have selected
- Checking if a tile is in work range now checks all cities

Assume the relevant city for triggered uniques  - By SeventhM

## 4.10.0

Natural wonders no longer spawn next to start locations

Automated spectator in multiplayer takes no actions

Added console commands to remove roads, change city name

By SomeTroglodyte:
- The console key is now bindable
- Allow map editor to generate smaller than "tiny" Pangaea maps

By SeventhM:
- Treat all timed uniques as functioning as always true regardless of conditionals
- Fix conditionals being ignored for some triggers

Increase AI workers - By tuvus

## 4.9.19

Modding:
- "Cannot be traded" unique accepts Civ conditionals
- Allow comment uniques on follower beliefs

By tuvus:
- Spectators receive era notifications again
- Fixed AI Worker feature removal

By SomeTroglodyte:
- Fix Paradrop crossing World-wrap seam
- ModManagementScreen gets a loading indicator

Add modified nation descriptions  - By Ouaz

## 4.9.18

Performance improvements for movement and ruleset validation

Added unique MayBuyConstructionsInPuppets  - By rpolitex

Polynesia can immediately embark on turn zero - By SeventhM

Fix NullPointerException on founding a pantheon  - By dHannasch

Fixed AutoPlay not working after victory  - By tuvus

## 4.9.17

Experimental pathfinding - activate in Options > Gameplay > Experimental movement

Console: autocomplete ALL THE THINGS!

By SeventhM:
- Allow mapUnitFilter to use CivFilter
- Add terrain filters for resources, any terrain, or "improved"
- Fix 'infinite Great Generals' bug

Spectator receives diplomatic notifications again - By tuvus

Download mod releases or any mod zip  - By SomeTroglodyte

Add <every [positiveAmount] turns> Conditional - By PLynx01

## 4.9.16

Console: civ/city names better matching

By SeventhM:
- Change the icons from some units/buildings
- Add unique to allow for generalized great generals

Show carried production from mid-turn obsoleted units  - By soggerr

By tuvus:
- Fixed crash when a worker tries to build a cached improvement it can't build
- Fixed crash evaluating alliance with unmet city-state

By SomeTroglodyte:
- City-stationed unit icons get circular touchable area

set minimum max coast extension to 1  - By remdu

## 4.9.15

Added Tile Breakdown table, accessible by clicking on the stats in tile info table

Solved rare pillage-related crash

Added smoothing to vector images

Console:
- Add activatetrigger command for civ!
- Allow "-delimited strings

'capture all capitals' victory accepts defeat of civs that did not found capitals

Map editor generation steps don't add multiple terrain features of the same type

Worker remove feature far away from cities fix  - By tuvus

## 4.9.14

Fixed Scenario crashes

Updating server URL allows checking connection immediately

New Citizen Focus Options - by Itanasi

By tuvus:
- Worker AI short distance priority fix
- AI focuses city-state gold gifting

By SeventhM:
- Spawn multiple great people if eligible
- Initial Great Writer functionality

## 4.9.13

Religion button respects unique "hidden from users" modifier

Trigger 'capture city' as a unit trigger

By tuvus:
- Worker AI Rework
- AI doesn't counteroffer and request a treaty on the same turn
- Civs now have a 50% chance of picking their favored religion

By SeventhM:
- Fix stat on tile uniques doubling on improvement tiles
- Avoid crash if "Only available" unique in policy branch has 2 or more params
- Allow for improvement removal "improvements"

Randomize seed checkbox  - By remdu

## 4.9.12

Added experimental scenarios!

Fixed Android status bar not disappearing ("immersion mode")

Console: added 'set player type' command, for scenario setup

Fix ShadowedLabel - By SomeTroglodyte

## 4.9.11

Fixed tech dependency related crash

Unit by default not selected on turn start

Added 'upon turn end' trigger

By SomeTroglodyte:
- City-state bonuses respect 'hidden from users' modifier
- (UI) Politics overview diagram: Add legend popup

By tuvus:
- Added AutoPlay until end setting
- if all players are defeated, one player will be processed

Enable Domination to Capture All Capitals - By itanasi

## 4.9.10

Add remove unit promotion unique  - By PLynx01

Fix key bindings in edge case - By SomeTroglodyte

By remdu:
- Coast spread algorithm
- don'y initialize with non naturally generated terrain

Stop movement on Path Blocked - By itanasi

Only units that build roads have connect roads automation. - By willjallen

By SeventhM:
- Fix Great Scientist calculation
- Show stat percent differences in replacement building

Don't list Unique requiring a tech in the Civilopedia for that tech - By dHannasch

## 4.9.9

Finally deprecated old religion uniques

By SeventhM:
- Avoid crash in mod checker for mods with undefined tech requirements
- Fix stats from tileFilter unique not working on improvements
- Add damage dealing unit trigger
- Fix not getting unique unit from tile based free unit trigger (For real this time)

By soggerr:
- Show average damage in battle calculations
- Allow battle calculations when not your turn

## 4.9.8

Add new map types  - By remdu

Avoid ANRs when users select mods that take above 500ms to run checks on

Add warning when buying a religious unit not of your religion  - By WhoIsJohannes

Allow arbitrary victory types for AI policy picking  - By SeventhM

By SomeTroglodyte:
- Civilopedia key bindings
- Great Person Points - Rounding changes, Breakdown UI
- Fix ended Leader Voice not cleared and resumed on un-pause

Fix 'Sleep Until Healed' action missing  - By soggerr

## 4.9.7

Allow city conditionals on units to upgrade to

Captured military units trigger a notification for the target civ

Fixed city-state type coloring

"Can instantly construct a [improvementFilter] improvement" works with improvementFilter

Allow conditionals for trigger-type unit actions  - By SeventhM

Fix connect road button when auto unit cycle turned on - By willjallen

Add resource support to stat gamespeed conditional - By PLynx01

Better Frigate and Ship of the Line sound - By tuvus

## 4.9.6

Connect roads automation  - By willjallen

Fix not getting unique unit from tile based free unit trigger  - By SeventhM

By Ouaz:
- Fix Carthage civilopedia article
- Add "UI tips" civilopedia article

## 4.9.5

Start turn with unit selected

Add trigger from building improvements and trigger conditional for building improvements  - By SeventhM

Preparation for multiple required uniques per ruleset object - By dHannasch

Fixed trading with city-state through notifications  - By tuvus

Mention the Railroad production bonus in the Civilopedia  - By Caballero-Arepa

By SomeTroglodyte:
- Notification for "Policy branch unlocked" clickable
- Dev Console: Linting + add Stat

## 4.9.4

Stats from followers unique fixed

City-state units work with 'get era' function

Ruleset validation for negative-weight ruin rewards

By SomeTroglodyte:
- Fix crash for trade notifications as Spectator / waiting for player
- Fix Unit rename popup offering up the icon

Solved worker automation crash  - By willjallen

By SeventhM:
- Add "Unable to pillage tiles" unique
- Fix objects being purchable with a blocking conditional

Stats per Stat unique - By PLynx01

## 4.9.3

Cities you haven't bombarded with will auto-bombard at turn end

Defeated (no units/cities) hotseat multiplayer no longer appears for turns ("player X ready")

Console: Nicer available command display

By willjallen:
- Add tech queuing on right-click / doubleclick

By tuvus:
- Initial AutoPlay implementation
- Fixed exploration for automated units

By SomeTroglodyte:
- Wesnoth map import polished up
- City sounds again

Fix founding cities removing city center tile improvement  - By SeventhM

## 4.9.2

Console:
- Added set/remove tile improvement
- Show available commands on empty command
- Added add/remove for cities

By SomeTroglodyte:
- Fix OpenAL error Windows Events after application ends
- Tweak Language Pickers to scroll the selected one into view when appropriate, and allow selection with letter keys
- Modding: "Comment" unique
- Snappier sounds
- Fix City ambient sounds

Get distance to nearest enemy rework  - By tuvus

## 4.9.1

Initial scenario/dev console!

By SomeTroglodyte:
- Map overlay toggle buttons rework
- Add a map import tool able to read "Battle for Wesnoth" maps

Fix free buildings triggering from conditionals in incorrect places  - By SeventhM

Add a setting to forbid closing popups by clicking behind them  - By karmaBonfire

[Translation] Add back "general" unit types  - By Ouaz

## 4.9.0

City centers don't provide resources you don't have tech to extract

AI: Settlers no longer stuck in endless loops

modding: Fixed certain unit uniques with tile conditionals

AI clears inquisitors from city centers to make way for spaceship units

Add the SellBuilding Unique  - By PLynx01

Add 'gain stat by game speed' and 'improvement speed with filter' uniques  - By SeventhM

Add a TriggerUponDiscoveringTile unique  - By karmaBonfire

Support for Leader voices - By SomeTroglodyte

## 4.8.19

Modding: Added "non-[filter]" filtering for unit filters, and multi-value filtering to all filters

Modding: Added "[relativeAmount] Air Interception Range" unique

Dim resources on tiles not immediately visible

By SomeTroglodyte:
- Allow controlling Android fullscreen from options
-
Fix unit triggers not triggering/triggering off the wrong units  - By SeventhM

AI diplomacy balancing  - By tuvus

Updated FantasyHex missile cruiser - By GeneralWadaling

## 4.8.18

Modding: Added Human and AI filters, separated civFilter from nationFilter

By SomeTroglodyte:
- Patch for on-screen keyboard hiding pedia search results
- Rivers... Moddable Stats and Civilopedia
- World screen top bar scales down to available width
- "Civ destroyed" Notification includes location
- Lots of other issue fixes!

By tuvus:
- Fixed land/sea nukes trying to act like air units
- Fixed promise not to settle

Fix Multiplayer spectator ids not logging  - By SeventhM

## 4.8.17

modding: Negative tile damage cannot heal more than max health

Mods with atlases that reference non-existent files will no longer cause crashes

By SomeTroglodyte:
- Reorg SpecialistAllocationTable

Improved settler AI  - By tuvus

By SeventhM:
- Fix Multiplayer Specatator being unable to move the screen
- Fix free stat buildings not giving unique stat buildings in certain cases

## 4.8.16

Ruleset validation refactor - easier location of affected objects, correctly display parameter-type mismatches, ignore uniques used for filtering

By SomeTroglodyte:
- Correct notifications for modded Citadel
- Music: Fix mini-player showing last track during inter-track silence
- Allow modders to hide individual Uniques from Civilopedia
- Ruins can no longer be save-scummed for better results

Fixed null reference error related to nuking  - By tuvus

## 4.8.15

By tuvus:
- AI units swap-retreat
- Melee units are now more likely to attack cities
- Nukes AI tweaks
- AI values traded gold using inflation

By SomeTroglodyte:
- Tighten Ruleset validation for Terrain
- Automated units stay automated after upgrade
- Fix Mod checker crash on RekMod
- Account for badly-defined Android font
- City overview refreshes for changes done in city

💚 add tests for city population manager - By Framonti

Fix [stats] unique adding multiple times - By SeventhM

## 4.8.14

Allow "[stats]" unique on terrains  - By SeventhM

By SomeTroglodyte:
- PolicyPickerScreen description links to Civilopedia
- Fix music errors on android pause-via-homescreen-button
- Remove UniqueTarget.Terrain from UniqueType.Stats as there is no implementation

By PLynx01:
- Added new trigger unique "Remove [buildingFilter] [cityFilter]"
- "when above [amount] [stat]" conditional, with gamespeed-modified version

## 4.8.13

By SeventhM:
- Fix auto assign production not working after a building is built
- Fix consuming resources not being affected by conditionals

By SomeTroglodyte:
- Allow games with zero researched techs to be 'before' the Ancient Era
- Minor Fix: VictoryScreenIllustrations
- Fix font "symbols" not showing

## 4.8.12

Automated units can fortify/set up/other actions

AI now uses free tech points  - By tuvus

By SomeTroglodyte:
- Align ruleset icons in text to font metrics

## 4.8.11

Religion fixes:
- Great Prophets spawn again
- Civilian units can get promotions upon being built (Great Mosque of Djenne)
- Missionaries consumed upon expending all usages

By SomeTroglodyte:
- Fade in/out for City Ambiance Sounds
- Fix Tutorial loader for mods on Android
- Fix ai tile purchase

By tuvus:
- Defensive pact button shows on both sides when a DoF is about to end
- Defensive pact functionality is now canceled with otherCiv before calling in defensive pact allies

## 4.8.10

Performance enhancement for first turn AI settling

Modding:
- Added UnitAction unique type for modder clarity and ruleset validation
- Converted "May enhance a religion" , "May found a religion" uniques to UnitAction

Golden age points decrease with negative happiness  - By Framonti

City-States don't trigger defensive pacts  - By tuvus

By SomeTroglodyte:
- City overview top bar fix
- Fixed crashes in Android for unit art in civilopedia

Fixed free building errors - By SeventhM

## 4.8.9

New online multiplayer no longer stuck when first player is human spectator

Modding:
- Replaced old religion style actions! Paves the way for unit action generalization
- Mod checker displays *all* unknown uniques

By SomeTroglodyte:
- Pedia pixel units
- Fix top bar layout
- City overview restore fixed header

Apply conditionals for free buildings to the destination city instead of the originating city  - By SeventhM

Test city conquest functions  - By Framonti

## 4.8.8

performance:
- Faster ruleset validation
- Faster ruleset loading

modding: Added json schemas for autocomplete and error detection

By tuvus:
- AI Open Borders Offer fix
- Fix Nuke Notification

Fix City construction context menu changing Puppets  - By SomeTroglodyte

## 4.8.7

Reload images when downloading or removing a mod

Better mod compatibility autochanges (remove removed units/improvements correctly)

By tuvus:
- Added 'civ returned worker' notification
- Liberating civ grants open borders

By SomeTroglodyte:
- Reorganized World Screen Top Bar in small screens
- Allow Space Key to close 'Player Ready' screen (hotseat)

Added tests for most nuke functionalities  - By Framonti

## 4.8.6

Mod checker accepts era for unit type

AI Declaration of Friendship rework  - By tuvus

By SomeTroglodyte:
- A "Status" column for City Overview
- Fix maximum window bounds for zoomed-in displays
- Generic Widget/Provider framework for sortable grids

Remove Faith bonus from Vanilla Siam  - By SpacedOutChicken

Unit tests for Battle.kt  - By Framonti

## 4.8.5

Removed double notifications and processing of treaties when traded  - By tuvus

By SomeTroglodyte:
- Global Constructions Blacklist
- Prevent selling free buildings
- Defense against Circular upgrade paths in mods

Modding: "Receive free [unit] when you discover [tech]" deprecation start - replaced "Free [unit] appears <upon discovering [tech]>"

## 4.8.4

By SeventhM:
- Fix unique Great Prophets not having the correct cost when buying at an increasing cost
- Fix free units with a build limit not spawning
- Fix AI getting stuck when it can't promote with enough xp

Allow the Space key to close Alert popups with no actual choice  - By SomeTroglodyte

By tuvus:
- Fixed politics tab not showing defensive pacts
- Defensive pact Tests

## 4.8.3

Allow unique parameters to contain square brackets

Library updates for performance and stability

Targetting refactor - By Framonti

## 4.8.2

performance: Don't autoupdate stale multiplayer games (more than a week old)

Buildings missing from ruleset are removed from loaded games *properly*

By SomeTroglodyte:

- Fix Map Editor double map holders after ruleset change
- Improve "does this unit found cities" check
- Fix Gdx not forced to UTF-8 when saving a game

Defensive pact notification fix  - By tuvus

## 4.8.1

Fixed AI attack targetting - By tuvus

By SeventhM:
- Add unique for a promotion to be free
- Fix cities getting the resource list of other cities

Fix ChangesTerrain unique for base terrains - By SomeTroglodyte

Units teleport away from city center when liberating

Icons are not added to  selection boxes

## 4.8.0

By SomeTroglodyte:
- Civilopedia Search
- City construction right-click menu

Many performance improvements!

Pillaged tile improvements  - By GeneralWadaling

Fix: "Only available" not working properly for religions or transforming/upgrading units  - By SeventhM

Make "Defensive Pact" button translatable - By Ouaz

## 4.7.19

"Jump To Destination" Unit Action Button for units that are moving  - By huckdogg

Automated units do not autopromote by default (changeable by options setting)

Defensive pact allies meet aggressor civ so they can declare war on them

Modding: GPP validation

Performance:
- Faster population reassignment
- Faster improvement stats simulation

Clarify when trade decision is made

Battle Damage tests - By Framonti

UnitTable close button mouseover - By SomeTroglodyte

## 4.7.18

Resolved ANRs on new game screen with a lot of maps

Fixed errors when cloning civs that should open policy picker

By SeventhM:
- Fix promotions being available when they shouldn't
- Allow buildings to require population, Allow buildings to use condtionals

By SomeTroglodyte:
- Fix Permanent Audiovisual toggle

By tuvus:
- AI nuke improvement
- AI move units closer to enemy first in wartime
- Improved AI attack targetting

Add tests for city class and introduce small refactor  - By Framonti

## 4.7.17

Free naval units are always added to coastal cities

Can offer Research agreements with gold, if other civ can't cover the cost  - By tuvus

By SomeTroglodyte:
- Can select tile north of city for bombarding
- Fixed 'Transfer Movement' stopping healing
- Fix next-turn not offering Policy Picker for free Policies
- Fix intended Longpress-to-move on Android not working
- A few more useful notification actions

## 4.7.16

Undo button in Multiplayer no longer changes 'next turn' button

By SomeTroglodyte:
- Fix minimum votes needed for a diplomatic victory
- Mods can add Victory illustrations

Add setting for unit upgrades for automated units  - By jlmcdonnell

By huckdogg:
- Visual indicator for building outside workable tiles
- ImprovementPicker screen displays tile owner civ and city

General Starting locations in map editor - By tuvus

Fix City-States giving untradeable resources - By SeventhM

## 4.7.15

By tuvus:
- Added defensive pact logic (not yet active, will activate in a week for multiplayer reasons)
- Nuke blast simulation no longer shows invisible units

Show impassable tile percentage on Map Editor View summary - By SomeTroglodyte

Add "Improvement" as an improvement filter - by SeventhM

## 4.7.14

Improvements from buildings can activate 'take over adjacent tiles' unique

Safeguard against Github connection errors

AI:
- Better policy selection
- Finer-tuned Food ranking vs other stats for cities
- Build melee naval units to defend coastal cities, and move them there
- Do not waste promotions on Heal Instantly

Mod manager smallish overhaul  - By SomeTroglodyte

Spectators don't get gold on new g                                      ///////ames - By tuvus

Promotion tree improvements  - By SeventhM

## 4.7.13

Improvement improvements!

- More accurate improvement stat previews for edge cases (e.g. removing Forest on Forest + Lumber Mill)
- 'Create improvement' uniques can create roads and remove features

Set initial screen color on Desktop so it's not black-to-blue

Fixed rare AI City State Influence crash

By SomeTroglodyte:
- Ruleset validator: Tilesets
- Minor Mod Manager fixes (mods having dashes in their repo name not shown right away)

## 4.7.12

AI workers remove detrimental features

Free buildings are converted to civ-specific replacements

Double-click on worked tile icon locks tile

Update Windows JDK to Adoptium JDK 11

Better untyped unique recognition in mod checker - By SomeTroglodyte

Gifting a unit transport gifts the contained units - By tuvus

Allow free unit triggers for any location - By SeventhM

## 4.7.11

By SomeTroglodyte:
- Civilopedia shows origin mod for objects
- Key shortcuts for CityScreen
- Nukes behave closer to Civ V
- Local mod folder names preserved for strangely-named mods
- modding: Better unique warnings

By SeventhM:
- Fixed behaviour for units that can move on water
- Fix: Resources with the same source subtract correctly
- Fix: Free buildings from other buildings show up correctly

Fix: Spectator can see all invisible units - By tuvus

Fix: Better Fog Busting AI  - By itanasi

## 4.7.10

BaseUnit unique-finding always takes Type uniques into account

Automated AI workers now replace forts - By tuvus

Fix issues when adding/removing buildings - By SeventhM

NotificationAction compatibility patch  - By SomeTroglodyte

Text correction for Ottomans war declaration - By LenaBullens

## 4.7.9

Minor memory improvement

By SomeTroglodyte:
- Little Promotion UX improvements
- Modding: Better unique-to-object compliance testing

By SeventhM:
- Fix issues from gaining free beliefs
- Fix issues when transferring capitals
- Avoid built buildings

## 4.7.8

Modding: City-level resources!

Display resource uniques in civilopedia

Memory improvements

Added notification for destruction of tile improvements via unit ability  - By random271

Translation updates

## 4.7.7

Fixed India's 'double unhappiness' unique

By SeventhM:
- Fix buildings/units not triggering and golden age stat updates
- Avoid crashes with incorrect condtionals
- Scaling purchase costs for faith/culture/science/etc. with speed
- Better support for lacking a capital

By SomeTroglodyte:
- AI support for Alpha Frontier-like Workers
- Prevent activation of disabled actors via keyboard
- Key shortcuts for Main Menu Screen

Fix civilopedia gold cost  - By Skekdog

Other modding fixes :)

## 4.7.6

"Requires a [buildingFilter] in at least [amount] cities" works correctly with filters that aren't building names

AI only buys tiles contiguous to the current city tiles

Undo Move button moved to the right, so other buttons stay in place

By SeventhM:
- Check for trigger conditions on new game techs
- Fix when units can be purchased

On City Raze, previous owner doesn't pay Road Maintenance  - By itanasi

By SomeTroglodyte:
- Defense against circular references in Promotions

## 4.7.5

Solved concurrency crashes due to players keypress-activating disabled buttons

'liberate city and resurrect civ' no longer crashes

AI no longer purchases non-contiguous tiles

City name translation for conquered popup does not get icon

Promotion picker allowing picking chains in one go - By SomeTroglodyte

Fixing workers dying in mountains bug for Carthage - By random271

Check for trigger uniques when starting and recaluating population - By SeventhM

## 4.7.4

Zoom in/out of history charts  - By JackRainy

Removing old buildings on enemy capital (when not last city) no longer crashes

Liberating city with multiple units in it no longer errors

By SomeTroglodyte:
- Fixed ruleset error crashes
- Fixed loading of mods with unconventional names
-
By SeventhM:
- Modding: Allow Great People to have different counters
- Tech column validation for mods
- Solved crashes from undefined building costs

Ensure more unit uniques work - By xlenstra

## 4.7.3

Linked Unit Types and Promotions in Civilopedia

Added new unique - "Automatically built in all cities where it is buildable"

By SeventhM:
- Added unique "May travel on Water tiles without embarking"
- Change the default cost of buildings and the default time of tile improvements

By xlenstra:
- Spies now occasionally steal technologies
- Spies in cities that are captured or destroyed now go to the hideout

Better mod conflict prevention - By SomeTroglodyte

## 4.7.2

Resolved performance problem

Performance improvements!

Automated air units no longer lose "automated" state after moving between cities

By SomeTroglodyte:
- Fix conditionals for trigger upon declaring friendship running twice
- More keyboard binding work - World, World Menu Popup, WASD
- Fixed CannotMove unique
- Fix translation problems due to nested brackets in getDifferences

## 4.7.1

AI:
- AI prioritizes purchace path to highly desirable tiles
- AI prioritizes work boats, and creates work boats for close non-contiguous cities
- Workers try to build roads utilizing existing roads, and railroads overriding existing roads

Modding: UnitFilter allows TechFilter for unit's required tech

By SomeTroglodyte:
- Long press support
- Improve Alert Popup scrolling
- Fix vulnerability of new NationPickerPopup icon view
- Keyboard bindings - collision check

## 4.7.0

AI:
- Improved AI city location picking
- AI more willing to risk happiness to create a new city
- AI doesn't construct Settler before Worker
- AI Workers remove fallout

Global politics table includes current civ

Fixed crash when resuming Overview screen

Fixed background errors

By SomeTroglodyte:
- Nation picker - Icon View & improvements
- Expander tab animations :)
- Key bindings options
- Improved "connected to Capital" handling

Always select military unit first - By WhoIsJohannes

## 4.6.19

Modding:
- "before adopting / after adopting" conditionals accept beliefs
- 'Transform' and 'double movement' uniques accept conditionals
- AI evaluation of BuyItemsDiscount no longer crashes

'random generated map type' is actually random

By WhoIsJohannes:
- Show line color in more cases
- AI aircraft only consider *visible* attackable enemies
-
By SomeTroglodyte:
- Fixed Hakkapeliitta ability
- Fix translated sorting
- Fix 'missing mod' display

Pantheon Mod fix - By SeventhM

## 4.6.18

Pillaged improvements have a visual indication on the icon

Fixed great engineer automation trying to reach cities it can't

Fixed later civs not getting any CS quests

By SomeTroglodyte:
- Mod checks against sellable, missing or multiple Palace(s)
- Fix tooltips on Android with keyboard detected
- Upgraded music player popup
- Popups can scroll the content without the buttons
- Translation fixes

Winning player can continue MP game - By CrsiX

Added docker build and push workflow  - By haimlm

## 4.6.17

More efficient use of the charts space  - By JackRainy

By SomeTroglodyte:
- Allow image overlay and changing world wrap in map editor
- Reapply 'city focus' on yield changes
- Upgrading from Unit overview improved

Remove "does not support server feature set" error for uncivserver.xyz

Trigger resource recalculation upon gaining a unit that requires resources

Guard against '><' text causing translation recursion

Badly configured era conditional no longer causes crashes

## 4.6.16

By SomeTroglodyte:
- Unit Overview: Improving a tile is also "what the unit is doing"
- Replacement PlatformSaverLoader for Linux X11 systems
- All notifications from Overview are now temporary
- Politics overview no longer discloses random number of players

Worker will now replace improvements to get resources - By janarvid

Spectators do not get natural wonder discovery notifications

Added checksum to be added to uploaded multiplayer games

Policy adoption triggers resource recalculation

## 4.6.15

'Crop yield' chart displays correct data

Modding:
- Units can transform to any unit
- Fixed policy softlock from conflicting mod policies
- 'Unpurchaseable' units are properly unpurchaseable

By SomeTroglodyte:
- Map editor update - concurrency, resource amounts, file double-click
- Fix road maintenance unique being ignored
- Fix mouse wheel on Notifications scroll
- Unit overview remembers scroll position after promoting

Allow game loading even when no saved games exist - By alexban011

## 4.6.14

By SomeTroglodyte:
- Next-Turn Progressbar
- Fixed crash when puppeting city

Population icon gets locked with doubleclick, clicks cycle assigned-unassigned

Resolved crashes when clicking mod in mod management

AI civilian improvement: keep working in tiles where enemy units can't reach

Prophets are not expelled on open borders end

City-state-owned great merchants can no longer 'conduct trade mission' on their own tiles

Game saves are regular json, not libgdx-specific format

## 4.6.13

Failure to get mod preview image no longer causes crash

Modding: Added 'upon adopting [belief]' trigger, allowed belief adoption to trigger uniques

Added turn number to victory replay

Clicking locally-added mods no longer crashes

By SomeTroglodyte:
- Fix off-by-one error in autoAssignPopulation
- Happiness change from bought buildings can reapply citizen focus
- Local and jpg mod previews
- Fix UnitTable layout

## 4.6.12

Animate battle damage numbers - By SomeTroglodyte

When picking custom map: Display map ruleset, don't show unloadable maps

Modding: Allow mods to contain a "preview.png" file for visual indication

Multi-server preparations :)

## 4.6.11

Update UI after founding city when breaking promise

By SomeTroglodyte:
- Fixed 4.6.10 no longer loading some older games
- Prevent some state changing actions during next-turn
- Policy images in red text, policy branch icons in pedia

Added a fix for multiplayer with 1 human player  - By CrsiX

Add `May not annex cities` unique  - By Skekdog

## 4.6.10

Automated workers no longer stay on unimprovable tile if another tile is improvable

Solved rare concurrency bug for explored tiles

Songhai Civilian units no longer gain Amphibious promotion

Fixed 'enhance religion' crash

Improperly configured mod conditionals do not cause crash

## 4.6.9

Hide buildings requiring multiple cities in one city challenge

Show Strategic resources you have by trade even if you have not researched tech for it yet

Scale down unit overlays on zoom in to allow selecting bombard target above city

Added Policy icons in text

Solved AI 'found religion' crash

Solved "get vanilla ruleset" errors after downloading mods

AbsoluteUnits - Hussar, Cossack, Panzer  - By letstalkaboutdune

Close the friend selection in NewGameScreen by outside click/ESC/BACK  - By CrsiX

By SomeTroglodyte:
- Fix memory leak from repeatedly resetting the font
- Limit saved window size to available desktop
- Show preview of custom maps on new game screen
- Allow closing Popup by clicking outside its area

## 4.6.8

Reduce a few memory allocations  - By SomeTroglodyte

AbsoluteUnits - Norwegian Ski Infantry, Mehal Sefari, Carolean, Foreign Legion  - By letstalkaboutdune

Various performance improvements  - By WhoIsJohannes

## 4.6.7

Modding: terrainFilter now accommodates nationFilter for tile owner

Minor UI improvements

Performance improvements  - By WhoIsJohannes

Allow to reveal explored resources from a city's demanding resources in `CityOverviewTab`  - By chr56

By SomeTroglodyte:
- Visual clue a load game from clipboard is underway
- Allow modders to use culture/faith conversion without providing the icons
- "can be promoted" notification only when it's actually new
- Fix wrapping for promotions in unit overview

## 4.6.6

Testing: Damage animations on damaged units

Units sprites move towards the enemy they're attacking

Removed deprecated settings

By WhoIsJohannes:
- Performance improvements
- AI does not consider Barbarian as 'enemy civ' for 'should we declare war' decisions
- Don't leak other civs in the game through LineChart colors.

Avoid first contact alerts for dead City-States  - By SomeTroglodyte

## 4.6.5

Solved AI Great Scientist crash

Great Merchant doesn't try to go to unreachable tiles

By letstalkaboutdune:
- AbsoluteUnits - Sipahi, Hakkapeliitta, Janissary, Tercio, Musketeer, Minuteman

By SomeTroglodyte:
- Make AI diplomatic marriage safe from concurrent modification issues
- Fix Main Menu BG map cycle not stopping on user action

## 4.6.4

Avoid font-related crash

Modding: Configurable embarked sight

UnitFilter accepts NationFilter (e.g. "vs [England] units")

Resolved image gliches in font icons, hopefully

By SomeTroglodyte:
- Reassign workers when resistance ends or improvement created
- Fix "religions to be founded" expanders accumulating
- Annexed cities in resistance cannot buy tiles

By WhoIsJohannes:
- Show replay after 5 rounds and don't reveal where player is on the map.
- Chart improvements (Highlight & performance)

## 4.6.3

Hopefully solved RAM-related crashes

Removed more double icons from many places

Aircraft attack/move range colors entire tile

By WhoIsJohannes:
- Show garrison in city screen
- Great people automation
- Order defeated civs after alive civs even if the alive civs score is negative (e.g. for happiness)
- Fix golden age length action text

Notifications can be "selected"  - By SomeTroglodyte

## 4.6.2

Added "in this city", "in other cities" conditionals for city-based uniques

More UI cleanup

Removed many double / badly-placed icons

Tileset no longer reverts on Android after restarting game

Solved randomness issues with Retreat chance and unit gain from defeat

By SomeTroglodyte:
- Unhappiness effects
- Fix more leaks of the actual Player count in random mode
- Allow city & tile conditionals on production-to-stat-enabling unique

Make new "Charts" button translatable  - By Ouaz

## 4.6.1

All game object images shown in text!

'Display' options subcatagorized - kudos @Ouaz

By WhoIsJohannes:
- highlight suitable city-founding tiles
- Show replay after 50 turns
- Charts improvements

By SomeTroglodyte:
- Fix startBias regional assignments
- Victory detection improvements
- Fix map editor resource label

AbsoluteUnits - Turtle Ship, Ship of the Line, Sea Beggar - By letstalkaboutdune

AI: Military units w/ Civilian uniques Automation  - By MioBestWaifu

## 4.6.0

Modding: Resources can now be optionally stockpiled, as in Gathering Storm, Endless Legend, etc!

Resolved crash screen copy error

UI: Better wrapping for long construction item names in city streen

Modding: Added conditional to filter by nation type

By SomeTroglodyte:
- User option to control NotificationScroll behaviour

## 4.5.17

Battle table bonuses according to tile to attack from

AI cannot buy tiles not contiguous to city

By SomeTroglodyte:
- Notifications can be hidden
- "Random Nations" fixes
- Victory screen reorg/cleanup
- MapEditor 'pinch zoom' painting fix

By WhoIsJohannes:
- Victory screen score charts!
- AI: Don't buy tiles in the very early game.
- AI: Military production when under stress

AbsoluteUnits - Mandekalu Cavalry, Conquistador, Inquisitor (Post-Industrial)  - By letstalkaboutdune

## 4.5.16

Many many performance improvements!

By SomeTroglodyte:
- Music player controls

## 4.5.15

Automated units don't try and conquer the same city twice

Constructed units that can't be placed are put on hold till the next turn

Solve 'desktop tries to create window of size 0/0' bug

Make AI buy city tiles.  - By WhoIsJohannes

By SomeTroglodyte:
- Untinted stat symbols in tinted text
- Moddable Civilopedia Welcome
- Fix disbanding units a civ cannot afford
- Main menu cancels background map creation when obsolete

## 4.5.14

Mods that remove 'repair' improvement no longer cause crashes down the line

Archipelago creates water again

By SomeTroglodyte:
- TileSet mods can no longer lock user out
- Obsoleted units replaced in construction queues by Nation-unique upgrades
- Fix exploit allowing promotion with 0 movement
- Fix notifications for pillage loot
- A renamed unit shows that new name in can promote notifications

AbsoluteUnits - Hwach'a, Camel Archer  - By letstalkaboutdune

## 4.5.13

Gold per turn is evaluated less the more turns 'in the future' it will be payed

Returned Perlin map type

By SomeTroglodyte:
- Map scroll speed
- Fix MiscLayer not respecting fog of war for spectator
- More Spectator/Barbarians fixes related to income

By itanasi:
- Automated Civilians don't multi-turn path through Enemy Territory
- More variety in Main Menu map

City-state music plays when first meeting them - By Skekdog

Add AbsoluteUnits license info - By letstalkaboutdune

## 4.5.12

Spectators no longer affect games they are in

Triggered notification text for unit triggers sent correctly

By SomeTroglodyte:
- "Sleep until healed" knows when you can't
- Tiny tweak to Max Turns slider

By Gualdimar:
- Better multiplayer button location in portrait mode
- Improvements in science display

AbsoluteUnits - Landsknecht, Knight_v2, Missionary (post-Industrial)  - By letstalkaboutdune

## 4.5.11

Fixed 'upon gaining unit' trigger activating for all units

modding: 'Only available when' applies to beliefs

Better promotion positioning in unit overview

Stat updates propagate where they didn't before

Hide bombard notification after bombarding

By SomeTroglodyte:
- Desktop starting size fix
- Fix stats reward for GP consumption escalation
- Fix Great Person Create Improvement w/ Resource

By Gualdimar:
- Victory screen fixed button position
- Fixed foreign city religion table exploit

## 4.5.10

Tilesets: Separated *unexplored* tiles from *not visible* tiles

Add city size (population) to TradeOffer.kt - By WhoIsJohannes

By SomeTroglodyte:
- Fixed header in city detailed stats popup
- Map editor explorable with arrow keys
- Fix Garrison bonus logic

Save settings when closing the options popup  - By Gualdimar

## 4.5.9

UI: Mod checker tab aligns mods

Modding:
- Units from triggers respect "limited to [amount] per civilization"
- Added notification for several unit triggers
- 'upon being defeated' applies to destroyed civilians as well

By SomeTroglodyte:
- Show garrison in City overview
- Harden and improve "Download Mod from Url" parser
- Future tech fix

By Gualdimar:
- Trade ending notification
- Fixed popup positioning after changing screen orientation

Improved Minimap colors  - By Caballero-Arepa

## 4.5.8

By Gualdimar:
- World wrap scrolling fix
- Notifications scroll pane position fix
- Double trades fix + Diplomacy screen layout fix
- Minimap hotseat fix

By SomeTroglodyte:
- Fix SelectBox ScrollPane being transparent

## 4.5.7

Add Replay feature in VictoryScreen  - By WhoIsJohannes

All units can be automated

By SomeTroglodyte:
- Make City center minimum tile yields moddable

## 4.5.6

By SomeTroglodyte:
- Custom key bindings
- Fix city desert tiles with Petra selectable
- Fix possible crash involving right-click attack
- Improve completeness and consistency of Technology descriptions

AbsoluteUnits - Great Prophet, Settler (Industrial Era)  - By letstalkaboutdune

## 4.5.5

Dynamic minimap - By Gualdimar

Better AI evaluation of which improvement to build on a tile

Added 'additional times' to limited actions

Android: selectable orientation  - By vegeta1k95

## 4.5.4

Fixed endless loop for mod checker

Modding: global alert available as triggerable for all objects

Better order of unit actions

Better checks for when units are purchasable

Fixed flanking bonus calculation when attacking unit is not adjacent to enemy

Automated workers do not remove Forest tiles for Camp improvements

Fix ExploredRegion rectangular maps support + Zoomout flicker prevention reworked  - By Gualdimar

Make "Borderless" display option translatable  - By Ouaz

## 4.5.3

Unit Action moddability!
- 'founds a new city' now accepts action modifiers
- 'for [amount] movement' modifier
- '[amount] times', 'once', 'after which this unit is consumed'  modifiers for limited actions

Android: "Screen Mode" option  - By vegeta1k95

By SomeTroglodyte:
- Better Religion info and some moddability
- Diplomacy trade layout fix
- Show terrain overriding yields in Civilopedia

Fixed multiplayer password  - By GGGuenni

Fixed map editor painting while dragging - By Gualdimar

## 4.5.2

Show death symbol next to actions that expend the unit

Modding:
- 'create improvement' action, <consuming this unit> and <as an action> modifiers

- gifting cities no longer causes crash

By GGGuenni:
- Fixed wrong implementation of Basic auth
- Fixed TurnChecker can not authenticate

Fix missing desert for flood plains in hexarealm tileset  - By AdityaMH

Fix random nations pool popup  - By SomeTroglodyte

## 4.5.1

Introduced unit triggers!

Added triggered uniques to grant specific tech / policy

Civilopedia does not crash when displaying techs on fresh start

'upon declaring friendship' triggers for both sides

all unpillagable improvements are not destroyed by 'destroy improvements' unique

By SomeTroglodyte:
- Fix broken random nations pool
- City Screen displays "free" tile yields undimmed

AbsoluteUnits - Chu-Ko-Nu, Longbowman, Crossbowman v2  - By letstalkaboutdune

## 4.5.0

Added password authentication as server feature  - By GGGuenni

Map exploring disables undo button + ExploredRegion smallfixes  - By Gualdimar

By SomeTroglodyte:
- Fix problems with dual save folder support
- Show UnitTypes in Civilopedia

Modding: "must be on/next to" unique for buildings can accepts tile filter

## 4.4.19

By Gualdimar:
- Limit camera movement within explored region

modding:
- Added "upon gaining a [unitFilter] unit" trigger condition
- Added religion state conditionals

Reload world screen after tileset/unitset change from in main menu screen - Ryg-git

Android: use best possible device frame rate  - By vegeta1k95

Allow filters in the ModOptions "ToRemove" lists  - By SomeTroglodyte

Play city-state music if available  - By Skekdog

Add 'neutral' Flood plains  - By Caballero-Arepa

## 4.4.18

By vegeta1k95:
- Modding: allow mods to supply custom fonts
- Fix TextureArraySpriteBatch missing method issue

By Gualdimar:
- Option to disable max zoom limit
- Slider tip permanent by default

Better error message for multiplayer games that get corrupted data from the server

Civilopedia entry for policy links to units and buildings that it enables/disables

Wonders with no tech requirement displayed in separate category

Attacking of any sort prevents undo of unit move

## 4.4.17

Added Undo button for unit moves!

Better trade screen for portrait mode

"Gift" trades to AI civs make them more friendly towards you

Add improvement action image to workers actively building improvement

Replace settlers with modded worker-like units in mods

Removed zoom limit for world-wrap maps - By Gualdimar

## 4.4.16

Display mod categories in mod page, mod sizes larger than 2 MB are displayed in MB

By Gualdimar:
- Display pending trades in trade overview
- Fixed unit upgrade check

Desktop: world camera autoscroll, selectable window mode  - By vegeta1k95

## 4.4.15

Tile improvements can now have pillaged versions of images

By vegeta1k95:
- Blockade mechanics
- Out-of-move units are half-opaque relative to base setting
- Fix selection opacity for non-full-opaque flags.
- Experimental: mitigate texture swapping with TextureArraySpriteBatch

Improve UX  - By Gualdimar

## 4.4.14

Fixed Guruship belief

By vegeta1k95:
- Mod sizes are updated to proper values upon selection
- Fix UI bugs
- Fix centering of unit HP bar
- Improvements to construction table

AbsoluteUnits - Berserker, Samurai, Longswordsman v2  - By letstalkaboutdune

By Gualdimar:
- Selecting an improvement switches to another unit only if enabled
- Fixed missing unimproved resources in the overview table
- Research agreement cost display
- Offers we receive for more resources than we have are invalid

## 4.4.13

Enemy indicator, city culture hex outline + misc - By vegeta1k95

Fixed air sweep crash

Some modifications for performance optimization - By lishaoxia1985

modding: conditionally-unbuildable buildings display their cost

Can no longer 'upgrade unit' between turns

By Gualdimar:
- Fixed getDiplomacyManager() Exception
- Revert knows() changes

## 4.4.12

Resolved "dead population working tiles" bug

Order indicators in TechScreen like original Civ V - By lishaoxia1985

By Gualdimar:
- Fixed the display of political relations
- Modding: fixed +percentage resource unique
- Pillaged improvements no longer provide bonuses

Better AI Great General placement

Generated maps no longer contain forbidden tile arrangements

Games where it's your turn are displayed first in multiplayer popup

Fix MapEditor world-wrap flickering  - By vegeta1k95

## 4.4.11

AI tweaks for better targeting

Cannot alter city build queue in between turns

Fixed 'destroy civilian unit before attack' crash

Terrain-Specific Natural Wonder Sprite Support  - By OptimizedForDensity

By vegeta1k95:
- City Screen improvements
- Fix crash when attacking city with disabled overlays
- PolicyScreen: branch progress + fix spectator

Trade button initially disabled - By lishaoxia1985

By Gualdimar:
- Collapsible tutorial tasks
- Bigger header on policy and mod screens

## 4.4.10

By vegeta1k95:
- Performance optimization: fast and smooth zoom

By Gualdimar:
- Policies screen top button reworked
- Android autosave location fix
- Fix unresponsive policy screen header

AbsoluteUnits - Cataphract, Companion Cavalry, Horseman - By letstalkaboutdune

Fix UX of random nations pool - By alexban011

Double Click on Tech to Exit Tech Screen  - By OptimizedForDensity

modding: "Unbuildable" accepts conditionals

Stats serialize to notifications

## 4.4.9

By vegeta1k95:
- Better World-Wrap
- Performance optimization: don't draw empty tile layers
- Fix promotion screen buttons clickhandler
- Fix city table selection

By OptimizedForDensity:
- Performance Improvements to Construction Automation
- Refactor Construction Rejection Reasons

By Gualdimar:
- Select next unit after closing the improvement screen
- Add Reset tutorials button
- City rename popup from world screen

Fixed 5hex image issues

## 4.4.8

By OptimizedForDensity:
- Fix Faith Healers healing enemies
- Notification log fix

By vegeta1k95:
- Add next-turn-progress bar for growth/production on CityButtons
- Fix incorrect dimming

Don't heal units when pillaging roads  - By itanasi

By Gualdimar:
- Autosave fix
- Remember "Show autosaves" setting state

## 4.4.7

AI battle automation vastly improved for taking over cities

AI automation: Don't nuke cities that we're already winning against

By vegeta1k95:
- Rework of TileGroup: split into dedicated layers, initial preparation for slot-mechanics
- Optimized BorderedTable, refactored CityButton, new air table for units + misc

Better check for inquisitors blocking religion

Modding: Allow unique ruins stat gain notifications

Wonder overview uses viewing civ, not current player

## 4.4.6

Fixed Android custom location save

Fixed 'infinite stat' from city-states

Clearer city tiles

Do not allow placing of 'repair' in map editor

Fixed spawn ignoring i/4 of the map

Better main menu map

Modding:
- allow roads/railroads with no required tech
- Mods without unittype for a unit will trigger correct warnings
- Fallback images for all major objects

Modification of the random part of Prize Ship to avoid save scumming  - By AlatarTheYoung

## 4.4.5

Modding:
- Added "upon discovering a Natural Wonder", "upon founding a city" triggers
- Added conditionals for above and below resource amounts
- Great general unique accepts conditionals - @SpacedOutChicken

Allow placing Barbarian encampments in map editor

Added option to allow players to choose randomly selected civs  - By alexban011

Fix crash in Tactical Analysis Map  - By OptimizedForDensity

AbsoluteUnits - Mohawk Warrior, Swordsman v2 - By letstalkaboutdune

## 4.4.4

Better generation for all map types

Include forked repos in github search - By FiretronP75

AI doesn't declare war until it's sufficiently built up

## 4.4.3

Version 800!

Can pick a policy with enough culture even if you have no cities

Korea's unique applies to Library

Notifications scroll retains position

Displayed Great Person point requirements always take game speed into account

Conditioned trigger uniques do not 'go off' without the trigger (e.g. when tech researched, building built)

Global alerts translated properly

Add options to use random number of players and city states - By FiretronP75

Refactor Cannot Move Unique  - By itanasi

## 4.4.2

Added trigger conditions framework!

Advanced game options hidden by default for faster initial game

Great prophets do not spawn to pantheons that cannot become religions

Fixed Reliquary belief

## 4.4.1

New tile visibility framework!
 - Differentiated attackable from visible tiles per Civ V
 - Tiles 1 step out of bounds of visibility are visible if they're higher than current tile
 - Higher tiles can be visible beyond non-visible, hidden tiles

Damage bonuses are additive, per Civ V, not multiplicative

Resolved health bar overflow problems

Preparation for Tactical AI Rework: analysis map, domination zones  - By vegeta1k95

## 4.4.0

Fixed battle table uneven when only one side has modifiers

"Street fighter" style of health bars, different colors for 'definite damage' and 'possible damage'

Pathfinding avoids embarkation when possible

Modding: 'transform' action - by itanasi

Consider tile happiness for start-of-turn computations

Better Himeji Castle unique text

Fixed sleep not showing on units in tiles with improvements in repair

Fixed Great Person speed modifier

Civilians no longer 'attack' on rightclick

## 4.3.21

Notifications sorted by category

Game speed affects additional aspects

Better bonus resource color

Better nation colors

Modding: Recognize uncontrasting colors for nations according to WCAG guidelines, and suggest tinted versions to conform

Fixed city screen bug for pillaged improvements

AbsoluteUnits - Roman Uniques - By letstalkaboutdune

Fixed some warnings  - By alexban011

## 4.3.20

Show numbers on attack damage and final strength comparison for battles

Changed Windows icon for Unciv to new icon

Unit icons no longer overlap unit action images, improved render time for unit images

MoveTo image visible

AbsoluteUnits - Unique Triremes - By letstalkaboutdune

By vegeta1k95:
- Modding: allow custom ResourcePortraits
- Fix foreign units flags fading
- Fix AI being able to bombard non-visible tiles + optimizations

## 4.3.19

Updated Unciv Android icons

Performance improvement for automated units looking for cities to connect

Popup for multiplayer when someone else has won

Politics shortcut fix

Limit the number of workers an AI creates

Sleeping units wake if there's an enemy in 3-tile radius

Do not allow liberating city-states you were at war with

AI peace deals don't pay more gold than they have

Higher chance of starting bias for human players

You can use missionaries of foreign religions

## 4.3.18

Units are not displaced to enemy land when kicked out of borders

Fixed edge-case crash - one city challenge when conquering capital and enemy has only puppeted cities left

UI: Better unit table

Fix Faith tutorial text

By vegeta1k95:
- Rework of PromotionPicker UI
- Fix City plates aircraft table shape
- Better visibility for city status icons

By nacro711072:
- Refactor maptype & resolve map setting issue
- update screen after disband unit

## 4.3.17

Fixed Air Sweep mechanic

AI tries to stop civs who are about to win Scientific/Cultural victory

No double copies of offers on AI trade counterproposals

Disbanding unit moves to next unit only after disbanding

Promote button sticks out more

Translated display sizes

Modding: Worker automation no longer considers foreign unique improvements when deciding if to remove features

Modding: Non-replacing buildings display nicely in nation picker

## 4.3.16

Modding: Humidity/Temperature limits on terrain features

Allow mass unit upgrades from the unit overview screen

Calculate movement correctly for road + railroad combinations

Modding: Workers pick the best improvement for resources

By FiretronP75:
- Add random select for map options
- Fixed flat world generation

By vegeta1k95:
- Resource icons colored by type
- Modding: allow custom TechPortraits
- Fixed PolicyScreen branches requirements text

Translate "Sell" in city screen  - By Ouaz

## 4.3.15

Modding: Validate unique parameters for mods

Display protecting civs for city-states

Movement fix: units check if they can capture enemy units by whether they *will* be embarked, not by whether they *are* embarked

By letstalkaboutdune:
- SFX Update - Arrow, Crossbow (New), Metal Hit

Fixed crash when policy prerequisite is a branch  - By vegeta1k95

Fix another untranslated String in Policy Screen  - By CrispyXYZ

added buttons for controlling music  - By alexban011

## 4.3.14

AI moves civilians off capital to make way for spaceship parts

AI considers capital city strength when deciding to declare war

By vegeta1k95:
- Big rework of City buttons
- Fix PolicyScreen for odd-numbered branches
- Better tech buttons

By CrispyXYZ:
- Fixed removing improvements in map editor

Solved 'tiles still belonging to razed cities' - By nacro711072

Negative stats UI Updates - By itanasi

Add setting to disable easter eggs. - By FiretronP75

## 4.3.13

World wrap available by default for all players

Units are no longer double-added to construction when clicking the 'add unit' button

Units passed with 'next unit' are not returned to

By vegeta1k95:
- Corrected some Civilization colors
- Fix perpetual constructions info
- Civ 5-style unit selection and cycling behaviour, "Wait" action

fix translation problem in policy screen  - By CrispyXYZ

fixed screens displaying yourself as an unknown civilization  - By alexban011

## 4.3.12

Big rework of Policy Picker UI - By vegeta1k95

G&K Neutral Tile Road Maintenance - By itanasi

Unknown civs displayed as `Unknown civilization` in GP and Diplomacy screens - By alexban011

Fix: tech screen zooms to current tech when opened

Get correct civ-wide stats from City-States for Siam bonuses

By letstalkaboutdune:
- ctrl+U toggles World Screen UI elements
- Trim Nuke SFX

Add new game option: No City Razing - By FiretronP75

## 4.3.11

Modding: Unbuildable units can still be upgraded to

Modding: Fallback image for modded buildings and techs

By vegeta1k95:
- Rework of City Screen
- Huge update of Technology Picker screen UI to match Civ 5

By FiretronP75:
- Continent and Islands  map
- Map editor responds to max zoom setting
- Map generation improvements (conflicting terrains, water generation)

AbsoluteUnits - Mongolian Uniques - By letstalkaboutdune

Added missing translation terms - By Ouaz

## 4.3.10

Better unit action icons

Solved temporary unique parsing error

bugfix: players can no longer get citystate nations when selecting mod

Thin lines around many round icons

By vegeta1k95:
- Rework of City Screen: new current buildings list + misc changes
- Construction table: ordering change (Civ 5) + add/remove on double-click
- Fix bug, where units do not reveal tiles  ()

By alexban011:
- fixed global politics screen from revealing unknown civs and CityStates

## 4.3.9

Modding: Can provide resources as a global unique

Flat Earth Hexagonal map shape - By FiretronP75

By vegeta1k95:
- Change styles and behaviour of Unit flags as in Civ 5
- "Auto Unit Cycle" and "Automated units move on turn start" options implemented (per Civ 5)
- Next Turn actions (pick tech/policy/etc) now have icons

Added icon for `Conduct Trade Mission` button - By alexban011

By itanasi:
- Actually fix AI from pillaging neutral roads

New map options translatable - By Ouaz

## 4.3.8

By nacro711072:
- Modding: Avoid destroying transported units when upgrading carrier units
- update score icon

AI Pillages Neutral Roads only at War - By itanasi

By FiretronP75:
- Three Continents map type
- Two Continents split map according to dimension ratio

Modding: Units/Buildings can now be given optional Portraits to be displayed instead of flags - By vegeta1k95

## 4.3.7

'loading' popup between turns only appears if there's a significant delay

xyz server as default multiplayer server

Fixes for Repair

Fixed all CSs getting unique units and unique luxuries

UnitActions icons separated for modding purposes - By vegeta1k95

Added button to paste from clipboard when downloading a mod - By alexban011

## 4.3.6

Resolved single turn repair, repair turn inconsistencies

Replaced misunderstood 'virtual resolutions' with more intuitive 'screen size'

Max screen size takes Windows taskbar into account

After-combat notification for promotable units

modding: Double Happiness from Natural Wonders -> [stats] from every known Natural Wonder

Volley only for Siege in G&K - By itanasi

fix civilopedia category bug - By nacro711072

## 4.3.5

Made yields smaller so heavy yields look better on tiles

Modding improvements:
- buildingName -> buildingFilter in all possible unique types
- improvement uniques can be assigned to tilefilter
- "Occurs at temperature between [amount] and [amount] and humidity between [amount] and [amount]" now applicable to resources

AbsoluteUnits - Unique Chariot Archers  - By letstalkaboutdune

## 4.3.4

Changes to moddable UI - By GGGuenni

Add Repair and Pillaging Roads - By itanasi

AbsoluteUnits - Unique Elephants - By letstalkaboutdune

HexaRealm units are here, finally!  - By GeneralWadaling

Added conditional to apply uniques only if the game starts in a specific era

Generalized "Stats per policies" unique

## 4.3.3

Resolved map latency when city-states exist

Fixed city-state type in civilopedia

'impossible' worked tiles not under your control are now cleaned up

Resolved corner case where entire path to destination is full and destination is unenterable

Fix policy counting in global politics  - By jmuchemb

## 4.3.2

City State Type overhaul!

Can now use `[stats]` unique to add happiness globally

Can now use `[stats]` unique for e.g. techs, policies, etc, to add gold/faith/culture/science to the global pool

## 4.3.1

City-state overhaul, part 1!

- Allowed adding arbitrary global uniques to city state bonuses
- Moddable quest weighting for city-states
- Moved city state icons to separate folder
- Removed backwards compatibility (pre-3.19.4) for missing city-state uniques
- Added conditional support to 'CS gift military units' unique

Removed single-pixel gap in top bar

AbsoluteUnits - Unique Spearmen - By letstalkaboutdune

## 4.3.0

Units now receive correct healing in friendly territory

Tile info table no longer reveals hidden units

AI no longer nukes if as consequence it will mean declaring war on someone

Empire overview screen updates after changing info in city screen

Fixed civilopedia text that says city-states don't conquer other cities

## 4.2.20

Main map and mini-map no longer show unexplored tiles!

New "Unitset" option translatable - By Ouaz

Renamed AbsoluteUnits unitset @letstalkaboutdune

Automated workers no longer try to improve enemy tiles

White lines in menus no longer change width when changing display settings

Set 'text/plain' content type for multiplayer server requests

## 4.2.19

Fixed promotions for mods conflicting with base ruleset promotions

Added unitset selection to options menu  - By GGGuenni

Added diplomatic repercussions for attacking your own ally city-state, and discouraged AIs from doing so

Absolute Units - Unique Archers - By letstalkaboutdune

## 4.2.18

Fixed mod promotions conflicting with existing promotion locations

Fixed rare thread crashes

Added many new before/after X turns and before/after Pantheon conditionals

Generalized ruins reward limitations with "only available when"

Unique Misspelling replacement text contains conditionals

Current naming for manually downloaded mods

## 4.2.17

Added layout option for promotions, added promotion layout for G&K

Added AbsoluteUnits as default units for Hexarealm tileset :D

## 4.2.16

Automated workers no longer improve unworkable tiles

Improved construction AI choices

By nacro711072:
- fix wrong glyph when switch between different mod.
- Fixed another memory leak
- check whether the city has been a puppet in "Pick construction" action.

## 4.2.15

Fixed memory leak

Fixed Great Person Picker screen not responding

Fixed stat conversion constructions (Science, Gold) not appearing

Fixed units obsoleting before their replacements could be constructed

Unit era variants added to FantasyHex tileset  - By GeneralWadaling

## 4.2.14

Fixed Temple of Artemis production bonuses

Fixed tile yields due to caching conditional uniques

Resolved crash when mods make 2 techs require each other

By itanasi:
- Add Temperature Offset Slider
- Change Default Map Generation to Perlin

Fixed holy city blocking - By qwerty2586

Show wars in global politics  - By MindaugasRumsa51

## 4.2.13

Don't show hidden improvements in nation info

Fixed translations missing when cancelling a new game then resuming the old one

Handle errors when renaming multiplayer games to impossible names

Military units take most efficient route to capture civilians

Translation fixes

## 4.2.12

Slight performance improvements

Create Cannot Move Unique - By itanasi

Fixed softlock forcing you to found a pantheon without available beliefs - By xlenstra

## 4.2.11

By xlenstra:
- Fixed crash when getting a spy and expanding a city simultaneously
- Modding: Added conditional for 'We Love The King day'

By nacro711072:
- fix: display "null" text on battleTable when policy branch Autocracy complete

Fix multiplayer turn checker option not showing up on Android  - By Azzurite

Translation updates

## 4.2.10

New Desktop downloads start at fullscreen

Special images for embarked units

Moddable UI skins - By GGGuenni

Global politics overview screen  - By alexban011

## 4.2.9

Starting in later eras triggers era uniques in all previous eras  - By xlenstra

Generalize Great Wall unique  - By OptimizedForDensity

By nacro711072:
- fix wrong happiness point on resume game if adopted 'Cultural Diplomacy' policy.
- fix no victoryTypes for the first time gaming with "quick game" opion.
- Fixed: double unit while loading game from customFile.

By xk730:
- Increased help button size

Unit art updates  - By GeneralWadaling

## 4.2.8

Show Unciv icon when loading game, instead of happy face

AI: Recognize when no further techs can be researched, even if some techs are blocked

Don't re-raise tech popups for previously researched techs

By xlenstra:
- Fixed bug disabling pantheon founding

UI fix - By GGGuenni

Add "UI Skin" to make it translatable  - By Ouaz

Fixed bug on 'resume game' - By nacro711072

## 4.2.7

Better milestones for world religion

Correct filtering of civs that need to have majority religion for world religion to activate

Moddable UI skins - by GGGuenni

Disable spectators from changing unit names  - By xlenstra

Lots of translations

## 4.2.6

Fixed unit not giftable in one-sided open borders agreement - By huckdogg

Better Mac support for running JARs - By nacro711072

Added game option to disable unwanted spectators from a multiplayer game  - By alexban011

Close app completely when clicked 'Exit' button  - By CrispyXYZ

Lots of background work for future features :)

## 4.2.5

By JackRainy:
- Suggest to adopt policy when the game starts with some culture

Removed legacy tileset code  - By GGGuenni

Improved options to rename units  - By huckdogg

By xlenstra:
- Fixed a crash when marrying a city-state without cities

By Ouaz:
- Translation and Tutorial improvements
- Add an icon for "Wait" unit action

Notificiations scroll fix in empire overview  - By MindaugasRumsa51

Fix infinite city-state tribute bug  - By OptimizedForDensity

## 4.2.4

Mod translations are now loaded upon mod download

By OptimizedForDensity:
- List which city owns each tile in the city screen UI
- Add free belief unique + refactor a few religion functions
- Several pathfinding optimizations

Fix missing "HP" in battle notifications - By Ouaz

## 4.2.3

By OptimizedForDensity:
- Add unit type to Civilopedia
- Fix bug related to resuming games
- Fix mod translation file generation

By xlenstra:
- Occupied cities have +2 extra unhappiness
- Added 'without resource' conditional
- Added a unique for destroying improvements on attack

Add missing string to the translations template file  - By estorski

Made invisible units not become visible just by being next to an owned tile.  - By kralinc

## 4.2.2

By itanasi:
- Add Damage numbers to Battle Notifications
- Fix Air Sweep Notification Translations
- Allow Citizen Management to pick any tile owned within 3 tiles

Disabled image packing when running from JAR

Removed duplicate "Consumes ..." lines of city screen  - By kasradzenika

Add translation for "XP" - By OptimizedForDensity

Tutorial rewording - By xlenstra

Updated nation introduction in civilopedia - By xk730

## 4.2.1

Resolved missionary-related crash

Fix erroneous relationship decay notification  - By oynqr

Rename "Show minimap" to "Minimap size"  - By J0anJosep

By SimonCeder:
- Invalid players removed from global quests
- Fix Natural Wonder placement

By OptimizedForDensity:
- Allow passable natural wonders

Removed duplicate placeholders  - By xlenstra

## 4.2.0

Add Air Sweep  - By itanasi

Allows inquisitors to block holy cities  - By xlenstra

Performance improvements

By SimonCeder:
- workers will replace city ruins
- avoid potential marriage bug

Fix games not being loadable  - By Azzurite

Construction automation optimization  - By OptimizedForDensity

Allow HexaRealm to render jungles on hills  - By ArchDuque-Pancake

## 4.1.21

Memory performance improvements

HexaRealm is now default tileset

Mod categories - By alexban011

Replace "moveTo" in unit overview with "Moving"  - By itanasi

## 4.1.20

Can no longer receive negative gold offers from AI

Keep progress in notification scroll when updating

Gray out city state friend bonus when allied - By Azzurite

By OptimizedForDensity:
- Minor reweight of AI policy selection
- Stop transported units from being able to pillage tiles

Fix getting settlers from ancient ruins on one-city challenge - By MindaugasRumsa51

## 4.1.19

Interception always takes an attack  - By itanasi

By OptimizedForDensity:
- Add more music triggers
- Move border below icons
- Significantly reduce AI turn time
- City health updates when finishing health-increasing buildings

By Azzurite:
- Fix potential race condition in multiplayer game update
- Fix game crashing when a multiplayer game can not be read

Sorted Civilopedia eras - By alexban011

Remove in-game mentions of 1.5x unhappiness for puppeted cities  - By Ouaz

## 4.1.18

Fix multiple capture uniques resulting in double-capture

Resolved ANRs caused by fonts taking too long to load

By OptimizedForDensity:
- Fixed image problems in combat
- Stop automate production setting from affecting other players' production in MP
- Sort game speeds in Civilopedia by speed
- Fix monastery purchase cost

Remove double XP gain from Intercept  - By itanasi

Multiplayer options UI fix - By Azzurite

## 4.1.17

Puppet cities generate no extra unhappiness (per Civ V)

Resolved crash when resuming game after closing it quickly

Modding: Removed deprecated uniques

By OptimizedForDensity:
- Improve AI belief picking
- Unstack enemy unit strength modifiers
- Prevent theme music tracks from randomly playing
- Fix latest untranslated strings

By alexban011:
- Increase mod search request page size
- Puppeted cities can no longer become capitals

## 4.1.16

By OptimizedForDensity:
- Better AI targeting
- Generalize production-to-stat conversion uniques
- Performance improvements

Unit icon opacity control - By letstalkaboutdune

## 4.1.15

By OptimizedForDensity:
- Add support for era-specific unit sprites
- Don't wake civilians to danger if they're in a city
- Fix translation issues from nested brackets and braces

By alexban011:
- Fixed brackets in notification logs
- Exception handling when loading mod options

Moddable prettier Tutorials - By SomeTroglodyte

By Azzurite:
- Save compatibility handling
- Fix OutOfMemoryError when loading a game and another is already loaded

Fix crash when exploring - By Skekdog

## 4.1.14

By OptimizedForDensity:
- Make mounted vs city penalties attack-only
- Logistics allows move after attack
- Fix crash when melee unit captures civilian then tries to attack it
- Allow modded harbor-type buildings to connect cities
- Fix "Religions to be founded" count

By Azzurite:
- Save uncaught exception to file

Better exploring AI for ruins - By Skekdog

By alexban011:
- added gameParameter option to disable start bias
- Added option to select font size
- Add Notifications Log

## 4.1.13

By Azzurite:
- Make popups and text fields nicer to interact with on Android

Rework Policy and Diplomacy buttons  - By SomeTroglodyte

By OptimizedForDensity:
- Fix cases where AI GP get stuck building improvements
- Fix disappearing terrain when switching between mods
- Fix policies not contributing stats from city-states
- AI missionaries avoid cities with inquisitors

By alexban011:
- CityScreen plays sound when opened
- Religion no longer "majority" when it's exactly 50% of the cities

## 4.1.12

By Azzurite:
- Many, many UX improvements!
- Fixed memory leaks

By OptimizedForDensity:
- Moddable game speeds
- Population reassignment bug fixes

Civilopedia tweaks - By SomeTroglodyte

Translation improvements - By J0anJosep

Trigger Time Victory only if enabled  - By JackRainy

Fix crash for "no strategic resource" mods - By Skekdog

Shortcut fixes and additions - By doublep

Maintain Fortify bonus after Fortify until Healed  - By itanasi

## 4.1.11

AI considers liberating city-states from other civilizations  - By OptimizedForDensity

By Azzurite:
- Fix OutOfMemory error when loading game state after already having a game loaded
- Fix unit overlay not being closed when performing an action with a new unit
- Fix option change not reloading main menu properly
- Fix cutout options crash

Better key handling  - By doublep

## 4.1.10

Added cutout support - By alexban011

By OptimizedForDensity:
- Improvements to AI military unit usage
- Fix spectator's fog of war toggle
- Stop AI puppets from building settlers and military

By Azzurite:
- Fix scroll to wonder in the map editor
- Fix unit being captured two times
- Fix NPE in Nation selection & editor mods popup
- Add more extensive multiplayer documentation

UI: Do not enter city while performing air strikes - By JackRainy

## 4.1.9

Peace cooldown with city-states

"attacked city state" functions activate only when attacking directly, not when declaring war due to alliances

tileFilter matches resource name and uniques

Fixed map position after portrait mode enabled

By Azzurite:
- Add multiplayer turn sound notification
- Fix crash during next turn automation

disable worldWrap when disabled in settings  - By alexban011

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
- City Screen stats double separators
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

Improved performance, especially in the City Screen

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

Specialist allocation is immediately viewable on the City Screen

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

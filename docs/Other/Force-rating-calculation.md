# Force rating calculation

Since the question has come up several times, here is a summary of how Force ratings are calculated.

## Base Unit Force Evaluation

First the base unit gets a force evaluation.
If the unit has a ranged attack, the starting force is the ranged strength ^ 1.45. Otherwise the starting force is strength ^ 1.5.
This is multiplied by the unit's movement ^ 0.3. Nukes get +4000.

Then this is multiplied by a bunch of modifiers:

-   0.5 if ranged naval
-   0.5 if self-destructs when attacking
-   Half the city attack bonus (So +25% if the unit has +50% when attacking cities)
-   A Quarter of attack bonuses vs things other than cities
-   Half the bonus "when attacking"
-   Half the bonus "when defending"
-   +25% if paradrop able
-   -20% if needs to set up to attack
-   Half the bonus from certain terrain
-   +20% bonus per extra attack per turn

## Individual Unit Force Evaluation

Each individual unit has a Force equal to the Base Unit Force,

-   multiplied by (number of times promoted +1) ^ 0.3.
-   multiplied by current health as a percentage.

## Civ Force Ranking

The civs Force Ranking is based on the sum of all their units' Force Evaluation (cities are not counted).
Only half the Force of naval units is counted.
This is multiplied by a gold modifier equal to the square root of current gold, as a percentage.
The gold multiplier is constrained to be between 1 and 2, so the max multiplier is 2 which is reached at 10000 gold.

## Show Me Some Numbers

-   `Scout 13`
-   `Archer 19`
-   `Slinger 19`
-   `Dromon 23`
-   `Warrior 27`
-   `Maori Warrior 27`
-   `Brute 27`
-   `Bowman 29`
-   `Jaguar 36`
-   `Catapult 39`
-   `Composite Bowman 39`
-   `Galleass 41`
-   `Chariot Archer 42`
-   `War Elephant 44`
-   `War Chariot 45`
-   `Horse Archer 45`
-   `Trireme 46`
-   `Spearman 49`
-   `Ballista 55`
-   `Persian Immortal 56`
-   `Horseman 62`
-   `Hoplite 63`
-   `Swordsman 64`
-   `Chu-Ko-Nu 66`
-   `Quinquereme 69`
-   `African Forest Elephant 72`
-   `Battering Ram 80`
-   `Cataphract 80`
-   `Crossbowman 81`
-   `Longbowman 81`
-   `Companion Cavalry 84`
-   `Legion 86`
-   `Mohawk Warrior 86`
-   `Pikeman 87`
-   `Landsknecht 87`
-   `Trebuchet 88`
-   `Keshik 89`
-   `Frigate 100`
-   `Hwach'a 110`
-   `Longswordsman 118`
-   `Camel Archer 124`
-   `Samurai 126`
-   `Berserker 133`
-   `Knight 134`
-   `Conquistador 134`
-   `Mandekalu Cavalry 134`
-   `Caravel 134`
-   `Ship of the Line 139`
-   `Musketman 144`
-   `Cannon 151`
-   `Minuteman 154`
-   `Janissary 162`
-   `Gatling Gun 169`
-   `Musketeer 182`
-   `Tercio 182`
-   `Naresuan's Elephant 194`
-   `Lancer 204`
-   `Hakkapeliitta 204`
-   `Sipahi 218`
-   `Privateer 222`
-   `Rifleman 243`
-   `Carolean 243`
-   `Sea Beggar 244`
-   `Artillery 245`
-   `Battleship 269`
-   `Great War Bomber 290`
-   `Cavalry 300`
-   `Hussar 320`
-   `Triplane 325`
-   `Turtle Ship 327`
-   `Cossack 337`
-   `Norwegian Ski Infantry 345`
-   `Guided Missile 378`
-   `Carrier 408`
-   `Submarine 420`
-   `Bomber 425`
-   `Great War Infantry 434`
-   `Machine Gun 465`
-   `Fighter 470`
-   `Foreign Legion 477`
-   `Ironclad 486`
-   `Zero 508`
-   `Anti-Tank Gun 542`
-   `B17 551`
-   `Marine 645`
-   `Landship 703`
-   `Infantry 720`
-   `Nuclear Submarine 735`
-   `Stealth Bomber 771`
-   `Paratrooper 806`
-   `Anti-Aircraft Gun 819`
-   `Destroyer 870`
-   `Missile Cruiser 888`
-   `Rocket Artillery 930`
-   `Tank 948`
-   `Jet Fighter 988`
-   `Helicopter Gunship 992`
-   `Mechanized Infantry 1186`
-   `Panzer 1223`
-   `Mobile SAM 1376`
-   `Modern Armor 1620`
-   `Giant Death Robot 2977`
-   `Atomic Bomb 4714`
-   `Nuclear Missile 7906`

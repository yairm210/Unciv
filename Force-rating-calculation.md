# Force rating
Since the question has come up several times, here is a summary of how Force ratings are calculated.

## Base Unit Force Evaluation
First the base unit gets a force evaluation.
If the unit has a ranged attack, the starting force is the ranged strength ^ 1.45. Otherwise the starting force is strength ^ 1.5.
This is multiplied by the unit's movement ^ 0.3. Nukes get +4000.

Then this is multiplied by a bunch of modifiers:
* 0.5 if ranged naval
* 0.5 if self-destructs when attacking
* Half the city attack bonus (So +25% if the unit has +50% when attacking cities)
* A Quarter of attack bonuses vs things other than cities
* Half the bonus "when attacking"
* Half the bonus "when defending"
* +25% if paradrop able
* -20% if needs to set up to attack
* Half the bonus from certain terrain
* +20% bonus per extra attack per turn

## Individual Unit Force Evaluation
Each individual unit has a Force equal to the Base Unit Force,
* multiplied by (number of times promoted +1) ^ 0.3.
* multiplied by current health as a percentage.

## Civ Force Ranking
The civs Force Ranking is based on the sum of all their units' Force Evaluation (cities are not counted).
Only half the Force of naval units is counted.
This is multiplied by a gold modifier equal to the square root of current gold, as a percentage.
The gold multiplier is constrained to be between 1 and 2, so the max multiplier is 2 which is reached at 10000 gold.

## Overview

Every type of object has some traits that are shared across all, or most, objects of its kind. For example, a building's stat increase, cost and required tech; a unit's type, movement and attack; a resource's type, improvement and bonus stats from improvement. All such traits have their own fields in the said object types.

But there are also other traits, that are only in a small subset of objects will have. Units that can attack submarines, or can move after attacking, or  has a combat bonus against a certain other type of unit. Buildings that give a free great person, or improve stats dependent on the population of a city, or provide extra yield to certain tiles. These traits cannot be given their own fields due to the huge number of them.

Instead, every special trait that an object has is encoded into a single parameter: the list of unique traits, or "uniques".

In the json files, this looks something like `"uniques": ["Requires a [Market] in all cities", "Cost increases by [30] per owned city"]`.

As seen in the above example, in order to provide flexibility and generalization, Uniques have certain *parameters*, marked by the fact that they are inside square braces. These parameters can be changed, and the game will recognize the text inside them and act accordingly.

## Parameter types

Parameters come in various types, and will be addressed as such inside the [square brackets].

- amount - This indicates a whole, (usually) positive number, like "2" or "13".
- unitName, buildingName, improvementName etc - Rather self explanatory. Examples: "Warrior", "Library', and "Mine", accordingly.
- stat - This is one of the 6 major stats in the game - "Gold", "Science", "Production", "Food", "Happiness" and "Culture". Note that the stat names need to be capitalized!
- stats - which we will discuss shortly

### [stats]

This indicates a text comprised of specific stats and is slightly more complex.

Each stats is comprised of several stat changes, each in the form of "+{amount} {stat}", where 'stat' is one of the size major stats mentioned above.
For example: "+1 Science".

These can be strung together with ", " between them, for example: "+2 Production, +3 Food".

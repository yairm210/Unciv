# Uniques

## What Modders Need To Know

Objects in the game - terrains, units, buildings, improvements, etc - differ by their stats, but what makes them truly different mechanically are their special abilities, or as we call them - *Uniques*.

Each game object can have any number of these Uniques.

The different possible types of uniques are available [here](../Modders/uniques.md), with each unique having a string value, e.g. `"Gain [amount] [stat/resource]"`

These are unique types, because they are in fact *templates* for possible *concrete* uniques, where the parameters in square brackets are filled in with specific values - e.g. `"Gain [20] [Gold]"`
Game objects should have *concrete* uniques (parameters filled in)

Every parameter in square brackets, is defined by its type, a list of which is available [here](../Modders/Unique-parameters.md) - each parameter type has its own text value, e.g. "amount" means an integer.
That determines possible values that this parameter can contain, e.g. "amount" should only contain strings that can be serialized as integers.

Concrete uniques that contain *incorrect values* (e.g. `"Gain [three] [money]"`) are warned against in the mod checker, and if they're serious enough, also when starting a new game with the mod

Sometimes uniques are deprecated - Unciv provides autoupdating, meaning you only need to click a button to update the deprecated uniques in your mod!

### Conditionals and Modifiers

Uniques can be modified to do certain things, using special Uniques that are shown in the [uniques list](../Modders/uniques.md) within `<these brackets>`.

This is done by adding these modifiers after the unique like so: `"Gain [30] [Gold] <after discovering [Steam Power]>"`

The most common type of modifier is a conditional - basically limiting the unique to only apply under certain conditions - so all modifiers are sometimes refered to as conditionals.

Other more specialized types of modifiers exist:

- Trigger uniques can get *under what circumstances they activate*
- Unit actions can get *costs, side effects, and limited uses*

As you can see, these conditionals *also* can contain parameters, and these follow the same rules for parameters as the regular uniques.

## What Developers Need To Know

We parse the unique by comparing the string given by the modder minus square bracket contents, to the known list of uniques.
If we find a match - Congrats, we set that as the unique type.

When we check for uniques in the code, it's always for uniques of a specific type, so we can just check the 'unique type' we previously assigned

We then take the parameters of that unique, which we also determined previously by scanning for all strings within square brackets, and use their values in determining the effect of the unique

There is a LOT of caching involved everywhere to make this all as fast as possible, but you really don't need to worry about that, that's my job ;)

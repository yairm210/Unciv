# Guiding Principles

## The AI plays to win

In a perfect world, the AI would pass the "Turing test" of gameplay - you would't be able to tell if you're playing against a human or AI.

Examples:

- No "what would you offer me for this"
- AI will choose to attack you if your military is weak (WILL kick you when you're down)

There is a fine line here between "exploitable" and "no fun" regarding trade - regular players may refuse any trade you offer them on principle.
We don't want that from the AI, which leaves us slightly open to exploits, but that's a trade-off we make knowingly.

## Modding philosophy - minimal objects, maximum interactions

As a new modder it's easy to get lost in the sheer number of uniques.

Our aim is to minimize the *number* of uniques as much as possible, but enable "emergent modding" by allowing combinations.

Examples:

- Parameters in uniques > multiple uniques
- Conditions should be Conditionals, so they can be applied to all uniques
- Triggered uniques and unique triggers - all combinations
- Unit Action modifiers, rather than special attributes for specific unit actions

## Crash early, crash often

A crash stacktrace is halfway to a solution - a game save which reliably produces it is 90% there.

Whenever an unexpected situation occurs - the game has reached an incorrect state - we should crash, to allow the problem to be fixed as soon as possible.

Persisting with an incorrect state makes the eventual resulting problems further from the cause, and complicates debugging.

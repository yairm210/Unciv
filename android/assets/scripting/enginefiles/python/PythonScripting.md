<!-- This file is read by main.py to dynamically set some docstrings. Keep that in mind if editing, renaming, or deleting this file. -->

The Python API described by this document is built on the [IPC protocols and execution model described in `/core/Module.md`](../../../../../core/Module.md#package-comuncivscriptingprotocol).

---

## Overview

There are basically two types of Python objects in this API:

*	Wrappers.
*	Tokens.

---

## Foreign Object Wrappers

A **wrapper** is an object that stores a list of attribute names, keys, and function call parameters corresponding to a path in the Kotlin/JVM namespace. E.G.: `"civInfo.civilizations[0].population.setPopulation"` is a string representation of a wrapped path that begins with two attribute accesses, followed by one array index, and two more attribute names.

A wrapper object does not store any more values than that. When it is evaluated, it uses a simple IPC protocol to request an up-to-date real value from the game's Kotlin code. The `unciv_lib.api.real()`/`unciv_pyhelpers.real()` function can be used to manually get a real Python value from a foreign instance wrapper.

However, because the wrapper class implements many Magic Methods that automatically call its evaluation method, many programming idioms common in Python are possible with them even without manual evaluation. Comparisons, equality, arithmetic, concatenation, in-place operations and more are all supported.

Generally, any foreign objects that are accessible in this API start out as foreign object wrappers.

Accessing an attribute on a wrapper object returns a new wrapper object that has an additional name set to "Property" at the end of its path list. Performing an array or dictionary index returns a new wrapper with an additional "Key" element in its path list.

```python3
print(civInfo)
# Wrapper object.

print(civInfo.cities[0])
# Also a wrapper object, with two extra path elements.

print(civInfo.cities[0].isCapital)
# Still a wrapper object, with another extra path element.

a = civInfo.cities
b = civInfo.cities
print(id(a) == id(b))
# False, because each attribute access dynamically creates a new wrapper object.
```

Calling a wrapper object as a function or method also creates a new path list with an extra "Call" element at the end. But in this case, the new path list is immediately sent as a request to Kotlin/the JVM instead of being used in a new wrapper object, and the returned value is the naked result from the requested function call in the Kotlin/JVM namespace.

```python3
print(civInfo.cities[0].isCapital())
# Goes through four wrapper objects, but ultimately sends its path as a request on the function call, and returns a real value.
```

Likewise, assigning to an attribute or an index/key on a wrapper object sends an IPC request to assign to the Kotlin/JVM value at its path, instead of modifying the wrapper object.

```python3
civInfo.cities[0].name = "Metropolis"
# Uses IPC request to modify Kotlin/JVM property.

civInfo.cities[0].cityConstructions.constructionQueue[0] = "Missile Cruiser"
# Uses IPC request to modify Kotlin/JVM container.
```

When a Kotlin/JVM class implements a property for size or keys, Python wrappers for its instances can be iterated like a `tuple` or a `dict`, such as in `for` loops and iterable comprehensions.

```python3
print([real(city.name)+str(real(city.population.population)) for city in civInfo.cities])
print({name: real(empire.cities and empire.cities[0]) for name, empire in gameInfo.ruleSet.nations.items()})
```

In the Python implementation of the IPC protocol, wrapper objects are automatically evaluated and serialized as their resolved values when used in IPC requests and responses.

```python3
somePythonVariable = gameInfo.turns
# Assigns a wrapper object to somePythonVariable. Does not evaluate real value for `gameInfo.turns`.

civInfo.tech.freeTechs = real(gameInfo.turns)
# Explicitly evaluate gameInfo.turns before using it in IPC assignment.
# 1. Makes IPC request for gameInfo.turns.
# 2. Receives IPC response for gameInfo.turns as integer.
# 3. Makes IPC request to assign resulting integer to civInfo.tech.freeTechs.

civInfo.techs.freeTechs = gameInfo.turns
# Does the same thing as above, because the gameInfo.turns wrapper object is resolved at the point of serialization.
```

The magic methods implemented on wrapper objects also automatically evaluate wrappers into real Python values if they are used in Python-space operations.

```python3
gameInfo.turns + 5
5 + gameInfo.turns
x = 5
x += gameInfo.turns
# All works, because the gameInfo.turns wrapper automatically sends and receives IPC packets to resolve into its real value as an integer when it's added.
```

In-place operations on wrapper objects are implemented by performing the operation using Python semantics and then making an IPC request to assign the result in Kotlin/the JVM.

```python3
gameInfo.turns += 5

# Equivalent to:

gameInfo.turns = real(gameInfo.turns) + real(5)
```

---

## Foreign Object Tokens

A **token** is a string that has been generated by `InstanceTokenizer.kt` to represent a Kotlin instance.

The `unciv_lib.api.isForeignToken()`/`unciv_pyhelpers.api.isForeignToken()` function can be used to check whether a Python object is either a foreign token string or a wrapper that resolves to a foreign token string.

When a Kotlin/JVM path requested by a script resolves to an immutable primitive like a number or boolean, or something that is otherwise practical to serialize, then the value returned to Python is usually a real object.

However, if the value requested is an instance of a complicated Kotlin/JVM class, then the IPC protocol instead creates a unique string to identify it.

```python3
isForeignToken("Some random string.")
# False.

isForeignToken(uncivGame.version)
# False. Version is stored as a simple string.

isForeignToken(gameInfo.turns)
# False. Turn count is stored as a simple integer.

isForeignToken(real(uncivGame))
# True. Unserializable instances are turned into token strings on evaluation.

isForeignToken(civInfo.getWorkerAutomation())
# True. This method returns a complicated type that gets turned into a token string.

isForeignToken(uncivGame)
# True. `uncivGame` is technically a wrapper object, but `isForeignToken` returns True based on evaluated results.
```

The original instance is stored in the JVM in a mapping as a weak reference. The string doesn't have any special properties as a Python object. But if the string is sent back to Kotlin/the JVM at any point, then it will be parsed and substituted with the original instance (provided the original instance still exists).

This is meant to allow Kotlin/JVM instances to be, E.G., used as function arguments and mapping keys from scripts.

```python3
civunits = civInfo.getCivUnits()
# List of token strings representing `MapUnit()` instances.

unit = civunits[0]
# Single token string.

civInfo.removeUnit(unit)
# Token string gets get transformed back into original `MapUnit()` when used as function argument.
```

The rules for which classes are serialized as JSON values and which are serialized as token strings may be a little bit fuzzy and variable, as they are designed to maximise use cases.

In general, Kotlin/JVM instances that *can* be cast into a type compatible with a JSON type will be serialized as such— Unless they are of classes defined within the Unciv packages themselves, in which case they will always be tokenized. The exemption for certain classes prevents everything that inherits from iterable interfaces— Like `Building()`, which inherits from `Stats:Iterable<Stats.StatValuePair>`— From being stripped down into JSON arrays, as having access to their members and instances is often much more useful.

---

## Assigning Tokens to Paths to Get Wrappers

Sometimes, you may want to access a path or call a method on a foreign object that you have only as a token string— For example, an object returned from a foreign function or method call.

Usually this would be impossible because you need a path in order to access foreign attributes. Without a valid path to an object, the wrapper code and the IPC protocol have no way to identify where an object is or what to do with it. In fact, if the Kotlin/JVM code hasn't kept its own references to the object, the object may not even exist anymore.

To get around this, you can use the foreign token to assign the object it represents to a concrete path in Kotlin/the JVM.

The `apiHelpers.registeredInstances` helper object can be used for this:

```python3
token = civInfo.cities[0].getCenterTile()
# Token string representing a `TileInfo()` instance.

print(type(token))
# <class 'str'>. Cannot be used for foreign attribute access.

apiHelpers.registeredInstances["centertile"] = token
# Token string gets transformed back into `TileInfo()` in Kotlin/JVM assignment.

print(type(apiHelpers.registeredInstances["centertile"]))
# <class 'ForeignObject'>. A full wrapper with path, that can be used for full attribute, key, and method access.

print(apiHelpers.registeredInstances["centertile"].baseTerrain)
# Successful attribute access.

del apiHelpers.registeredInstances["centertile"]
# Delete the reference so it doesn't become a memory leak.
```

In order to use this technique properly, the assignment of an object to a concrete path should be done within the same REPL loop as the generation of the token used to assign it. This is because the Kotlin code responsible for generating tokens and managing the REPL loop keeps references to all returned objects within each REPL loop. Afterwards, these references are immediately cleared, so any objects that do not have references elsewhere in Kotlin/the JVM are liable to be garbage-collected in between REPL loops.

Note that because of this, it is also perfectly safe to use token strings as arguments for foreign functions without assigning them to concrete paths, as long as they are requested and used within the same REPL loop.

```python3
# Each ">>>" represents a new script execution initiated from Kotlin— E.G., A new command entered into the console screen, or a new handler execution from the modding API— And not just a new line of code. Code on multiple lines can still be run in the same REPL loop, as long as the script's control isn't handed back to Kotlin/the JVM in between.

>>> worldScreen.mapHolder.setCenterPosition(apiHelpers.Factories.Vector2(1,2), True, True)
# Works, because the instance creation and the call with a tokenized argument happen in the same REPL execution.

>>> apiHelpers.registeredInstances["x"] = apiHelpers.Factories.Vector2(1,2)
>>> worldScreen.mapHolder.setCenterPosition(apiHelpers.registeredInstances["x"], True, True) #TODO: This doesn't actually use any subpath.
# Works, because the instance creation and token-based assignment in Kotlin are done in the same REPL execution.

>>> x = apiHelpers.Factories.Vector2(1,2); civInfo.endTurn(); apiHelpers.registeredInstances["x"] = x
>>> worldScreen.mapHolder.setCenterPosition(apiHelpers.registeredInstances["x"], True, True)
# Also works.
```

```python3
>>> x = apiHelpers.Factories.Vector2(1,2)
>>> apiHelpers.registeredInstances["x"] = x
>>> worldScreen.mapHolder.setCenterPosition(apiHelpers.registeredInstances["x"], True, True)
# May not work, because the created instance has no reference in Kotlin between the first two script executions and can be garbage-collected.

>>> x = apiHelpers.Factories.Vector2(1,2)
>>> worldScreen.mapHolder.setCenterPosition(x, True, True)
# Also may not work.
```

**It is very important that you delete concrete paths you have set after you are done with them.** Any objects held at paths you do not delete will continue to occupy system memory for the remaining run time of the application's lifespan. We can't rely on Python's garbage collection in this case because it doesn't control the Kotlin objects, nor can we rely on the JVM's garbage collector because it doesn't know whether Python code still needs the objects in question, so you will have to manage the memory yourself by keeping a reference as long as you need an object and deleting it to free up memory afterwards.

For any complicated script in Python, it is suggested that you write a context manager class to automatically take care of saving and freeing each object where appropriate.

It is also recommended that all scripts create a separate mapping with a unique and identifiable key in `apiHelpers.registeredInstances`, instead of assigning directly to the top level.

```python3
apiHelpers.registeredInstances["python-module:myName/myCoolScript"] = {}

memalloc = apiHelpers.registeredInstances["python-module:myName/myCoolScript"]

memalloc["capitaltile"] = civInfo.cities[0].getCenterTile()

worldScreen.mapHolder.setCenterPosition(memalloc["capitaltile"].position, True, True)

del memalloc["capitaltile"]

del apiHelpers.registeredInstances["python-module:myName/myCoolScript"]
```

```python3
apiHelpers.registeredInstances["python-module:myName/myCoolScript"] = {}

memalloc = apiHelpers.registeredInstances["python-module:myName/myCoolScript"]
# Wrapper object.

class MyForeignContextManager:
	def __init__(self, *tokens):
		self.tokens = tokens
		self.memallocKeys = []
	def __enter__(self):
		for token in self.tokens:
			assert isForeignToken(token)
			key = f"{random.getrandbits(30)}_{time.time_ns()}"
			# Actual uses should locally check for key uniqueness.
			memalloc[key] = token
			self.memallocKeys.append(key)
		return tuple(memalloc[k] for k in self.memallocKeys)
	def __exit__(self, *exc):
		for key in self.memallocKeys:
			del memalloc[key]
		self.memallocKeys.clear()

with MyForeignContextManager(apiHelpers.Factories.MapUnit(), ) as mapUnit, :
	mapUnit

del apiHelpers.registeredInstances["python-module:myName/myCoolScript"]
```

The recommended format for keys added to `apiHelpers.registeredInstances` is as follows:

```
<Language>-<'mod'|'module'|'package'>:<Author>/<Filename>
```

---

## API Modules

The top-level namespace of the API can be imported as the `unciv` module in any script running in the same interpreter as it.\
Further tools can be imported as `unciv_pyhelpers`.

This is useful when writing modules that are meant to be imported from the main Unciv Python namespace.

```python3
# MyCoolModule.py
# In PYTHONPATH/sys.path.

import unciv
import unciv_pyhelpers

def printCivilizations():
	for civ in unciv.gameInfo.civilizations:
		print(f"{unciv_pyhelpers.real(civ.nation.name)}: {len(civ.cities)} cities")
```

```python3
# In Unciv.

>>> import MyCoolModule
>>> MyCoolModule.printCivilizations()
```

---

## Examples

---

## Performance and Gotchas

Initiating a foreign action is likely to be expensive. They have to be encoded as request packets, serialized as JSON, sent to Kotlin/the JVM, decoded there, and evaluated in Kotlin/JVM using slow reflective mechanisms. The results then have to go through this entire process in reverse in order to return a value to Python.

However, code running in Kotlin/the JVM is also likely to be much faster than code running in Python. The danger is in wasting lots of time bouncing back and forth just to exchange small amounts of data.

Efficient scripts should try to do as much of their work in the same environment as possible.

If something can be done with a single foreign action, then it probably should be, as that way the statically compiled and JIT-optimized JVM bytecode can do most of the heavy lifting. However, if a task can't be done in a single foreign action, then as much work should be done completely in Python as possible, in order to reduce the number of high-overhead IPC calls used.

```python3
def slow():
	for tile in gameInfo.tileMap.values:
	# Iteration implicitly uses 1 IPC "length" action at the start.
		tile.naturalWonder = "Krakatoa" if random.random() < 1/len(gameInfo.tileMap.values)*20 else tile.naturalWonder
		# On every loop:
		#  +1 IPC "length" action in the "if".
		#  +1 IPC "read" action for the current .naturalWonder if reaching the "else" expression. (Only gets resolved on serialization in the next step.)
		#  +1 IPC "assign" action to update .naturalWonder, even if it's not changing.
		# This happens once for every tile— Hundreds or thousands of times in total.
# Total IPC actions: ~1,000 to ~15,000. Just below 3 on average for every tile on the map.

def faster():
	sizex = len(gameInfo.tileMap.tileMatrix) - 1
	sizey = len(gameInfo.tileMap.tileMatrix[0]) - 1
	# 2 IPC "read" actions for max map bounds at the start.
	targetcount = random.randint(15, 25)
	i = 0
	while i < targetcount:
		x = random.randint(0, sizex)
		y = random.randint(0, sizey)
		# Instead of iterating over every tile on the map, generate the coords in Python, and then work with foreign objects only after having determined the coordinates.
		if real(gameInfo.tileMap.tileMatrix[x][y]) is not None:
		# +1 IPC "read" action.
		# On hexagonal maps, check for validity. To be faster yet, this could also be done numerically in Python, or short-circuited on rectangular maps.
			gameInfo.tileMap.tileMatrix[x][y].naturalWonder = "Krakatoa"
			# +1 IPC "assign" action.
			# Only done after already selecting coordinates and checking validity.
			i += 1
			# Only iterate for as long as needed to change the target number of tiles.
# Total IPC actions: ~40 to ~60. Only one assignment, plus one check, for each tile that actually changes.

def fastest():
	apiHelpers.scatterRandomFeature("Krakatoa", random.randint(15, 25))
# Total IPC actions: 1. All the heavy lifting is done in the Kotlin function it calls.
# The "scatterRandomFeature" function doesn't actually exist. But the point is that when available, a single IPC call that causes all of the work to then be done in the JVM is likely to be faster than a script-micromanaged solution. E.G., use one call to List<*>.addAll() instead of many calls to List<*>.add().
```

Every time you access an attribute or item on a foreign wrapper in Python creates and initializes a new foreign wrapper object. So for code blocks that use a wrapper object at the same path multiple times, it may be worth saving a single wrapper at the start instead.

```python3
def slow():
	for i in len(civInfo.cities[0].cityStats.cityInfo.tilesInRange):
		print(civInfo.cities[0].cityStats.cityInfo.tilesInRange[i]) # FIXME: This doesn't actually take indices, does it?
		# Every loop starts out with civInfo, and then constructs a new wrapper object in Python for every attribute and item access.

def faster():
	tilesInRange = civInfo.cities[0].cityStats.cityInfo.tilesInRange
	for i in len(tilesInRange):
		print(tilesInRange[i])
		# Saves 5 Python object instantiations with every loop!
```

Every element in the path sent by a wrapper object to Kotlin/the JVM also requires the Kotlin side to perform an additional reflective member resolution step.

```python3
def slow():
	for i in range(1000):
		pass # TODO


def alsoSlow():
	uselesscache =
	for i in range(1000):
		uselesscache +=
	# Assigning the wrapper object to a name in Python saves on Python attribute access time. But it doesn't actually shorten its Kotlin/JVM path, so the same number of steps still have to be taken when the Kotlin/JVM side processes the packet sent by Python.

def fast():
	apiHelpers.registeredInstances["usefulcache"] =
	usefulcache = apiHelpers.registeredInstances["usefulcache"]
	for i in range(1000):
		usefulcache. +=
		# Assigning the leaf wrapper to a single Python name reduces the number of new wrapper objects built in Python each loop to .
		# Assigning the foreign object to
	del apiHelpers.registeredInstances["usefulcache"]

```

Because iteration over wrapper objects is currently implemented in Python by returning a new wrapper for every index within their length, foreign set-like containers without indices and generator-like iterables without fixed lengths cannot be idiomatically iterated over from Python.

To get around this, you can simply resolve them into their serialized JSON forms. This turns them into JSON arrays and Python lists of primitive values and foreign token strings, on which regular Python iteration can take operate.

```python3
for e in civInfo.cities[0].cityStats.cityInfo.tiles:
	print(e)
	# Fails. CityInfo.tiles is a set-like instance that does not take indices.

for i in range(len(civInfo.cities[0].cityStats.currentCityStats.values)):
	#civInfo.units() returns a sequence.
	print(i)
	# Also fails. CityStats.currentCityStats.values is an iterator-like instance without a known length.

for e in real(civInfo.cities[0].cityStats.currentCityStats.values):
	print(e)
	# Works. But yields only primitive JSON-serializable values and/or token strings, not wrappers.
```

Because the elements yielded this way do not have equivalent paths in the Kotlin/JVM namespace, and are not foreign object wrappers, any complex objects will have to be assigned as token strings to a concrete path in order to do anything with them.

When using every value from a Kotlin/JVM container, iterating over its wrapper object is also likely to be slower than iterating over its resolved value. This is because iteration over wrappers is implemented on the Python side by creating a new wrapper at the next index for every item, so every use of the yielded value requires another IPC call, while evaluating the container itself means that the object being iterated over is a real container deserialized from a JSON array.

```
def slow():
	for name in civInfo.naturalWonders:
		print("We have the natural wonder: " + name)
		# Uses IPC call on every loop, as name is a foreign wrapper.
		# Equivalent to accessing real(civInfo.naturalWonders[i]) on every loop.

def fast():
	for name in real(civInfo.naturalWonders):
		print("We have the natural wonder: " + name)
		# Uses only one IPC call at the start of the loop, and iterates over resulting JSON array.
		# Name is a real Python string.

def alsofastish():
	for name in civInfo.naturalWonders:
		print("We have a natural wonder!")
		# Even though name is a foreign object wrapper here too, it's never used, so no extra IPC calls are generated.
```

---

## Error Handling

Usually, errors in Kotlin/the JVM during foreign calls are caught by the Kotlin implementation of the IPC protocol. They are then gracefully serialized and returned in a specially flagged packet, which causes a `unciv_lib.ipc.ForeignError()`/`unciv_pyhelpers.ForeignError()` to be raised in Python.

```python3
>>> uncivGame.fakeAttributeName
#TODO

>>> civInfo.addGold("Fifty-Nine")

```

Because of this, malformed foreign actions requested by Python usually cannot crash Unciv. In fact, it is possible to catch such errors in Python to create Python-style exception-controlled program flow.

```python3
try:
	print(gameInfo.civilizations)
	# gameInfo is null in the main menu screen.
except ForeignError:
	# Comes here with ForeignError("java.lang.NullPointerException").
	print("Currently not in game!")

# (I still don't like it personally. I guess it doesn't incur any major overhead since you'd need an IPC action anyway to check validity/LBYL, but this seems kinda gross.)
```

The only major caveat to the robustness of this error handling is that it does not protect against valid Kotlin/JVM actions that lead to unexpected states which then cause exceptions in later use by unrelated game code. Assigning an inappropriate value to a Kotlin/JVM member, or deleting a key-value pair where it is required by internal game code, for example, will likely cause the core game to crash the next time the invalid value is used.

```python3
>>> gameInfo.tileMap.values[0].naturalWonder = "Crash"
# Executes and sets .naturalWonder to "Crash" successfully.
# But the game crashes when you click on the changed tile because there aren't any textures or stats for the "Crash" natural wonder.

>>> del gameInfo.ruleSet.technologies["Sailing"]
# Executes and removes "Sailing" technology from tech tree successfully.
# But the game crashes if you try to select any techs that required "Sailing", because they still have "Sailing" in their prerequisites.

>>> civInfo.cities[0].tiles.add("Crash")
# Executes and adds "Crash" string to the set containing your capital's tiles' coordinates.
# But the game breaks when you press "Next Turn", because the JVM thread processing the turn tries to use the string "Crash" as a Vector2.
```

---

## Other Languages

The Python-specific behaviour is not meant as a hard standard, in that it doesn't have to be copied exactly in any other languages. Some other design may be more suited for ECMAScript, Lua, and other possible backends. If implementing another language, I think some attempt should still be made to keep a similar API and feature equivalence, though.

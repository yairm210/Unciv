# Package com.unciv

## Table of Contents

* [Package `com.unciv.scripting`](#package-comuncivscripting)
	* [Design Principles](#design-principles)
	* [Class Overview](#class-overview)
* [Package `com.unciv.scripting.protocol`](#package-comuncivscriptingprotocol)
	* [REPL Loop](#repl-loop)
	* [IPC Protocol](#ipc-protocol)
	* [Python Binding Implementation](#python-binding-implementation)

# Package com.unciv.scripting

## Design Principles

**The Kotlin/JVM code should neither know nor care about the language running on the other end of its scripting API.** If a behaviour is specific to a particular language, then it's also too messy and complex to try to take special account for from the other side of an IPC channel. Instead, the complexity of each specific scripting language should be handled entirely within that language itself, such that the only thing exposed to the Kotlin code is a common interface built around structures that exist in most computer programming languages (like code strings, attributes, keys, calls, assignments, collections, etc). This not only keeps the scripting protocols and interfaces compatible with multiple backends; It also serves as a test that helps keep their design relatively clean and maintainable by forcing messy or complicated behaviours to be implemented in more appropriate places than the IPC interface.

**Parts should be kept as modular and interchangeable as possible.** Each component type should have a somewhat well-defined job, and should not contain or be inseparably entwined with code that does things aside from that job. If a base class's primary role is to expose an REPL, for example, then extra features like command history or implementation details like running a subprocess can be moved into either another class or a subclass. If an interface's job is just to propagate code strings or resolve request packets into arbitrary Kotlin objects, then TODO: Finish this sentence. Again, IMO this both makes it easy to support versatile configurations and helps with keeping a reasonably neat codebase and architecture.

**Different levels of execution/evaluation should not mix.** The IPC protocol defines the packet structures, types, and communication order that are for *implementing* scripting language semantics for accessing Kotlin/JVM data. Therefore, the IPC protocol should not itself become a *part of* scripting language semantics; No user/mod script in any language should ever have to manually create and send or receive and parse IPC packets. Certain API functions have been defined to provide additional capabilities that are *accessible through* scripting language semantics (in class ApiHelpers). Therefore, those functions should never be used in *implementing* scripting language semantics; No overloaded operator presented to a user script as part of the core Unciv API should ever implicitly call such a method as part of its basic functionality. The entrypoints for the scripting system have the roles of taking code strings (from user input, from mods, etc) and returning a result string (to print out, log, etc) (and possibly an exception Boolean flag). Therefore, they should never have to understand, or even be able to use, any data aside from opaque strings (such as IPC packets or structured return results).

**In an API meant for dynamic scripting languages, dynamic behaviours are better than static ones.** The Unciv Kotlin codebase was around 60k lines when I started on this scripting API. By using reflection in the JVM and operator overloading in scripting languages, nearly all of the classes and structures defined in there can be mirrored directly in the scripting environment without having to write or maintain a single line of hardcoded API. Because API endpoints exposed in scripting languages are already all dynamically generated at runtime, when the class structure in Kotlin changes, the attributes and methods available from all scripting backends also immediately match the new Kotlin code without requiring any effort to update.

**In the IPC mechanisms, the specification and architecture come before implementation.** IPC actions aren't statically checked like the Kotlin code is. They aren't even syntax-checked like Python code. I do have them spitting out exceptions showing the offending packets in both Kotlin and Python in cases of obvious desync, but even that's breakable. The only thing really keeping them working is simultaneous adherence on both ends to a common protocol, which is easier when the protocol is fairly simple. If implementing a particular syntax for a scripting language would require adding a new packet type or changing the REPL loop's control flow, consider whether the use case for it would be better served by adding an API-level helper function instead. (I.E. Consider adding something to ScriptingScope.ApiHelpers and letting scripts handle it themselves, instead of adding something to ScriptingProtocol and E.G. a magic method in wrapping.py.)

## Class Overview

The major classes involved in the scripting API are structured as follows. `UpperCamelCase()` and parentheses means a new instantiation of a class. `lowerCamelCase` means a reference to an already-existing instance. An asterisk at the start of an item means zero or multiple instances of that class may be held. A question mark at the start of an item means that it may not exist in all implementations of the parent base class/interface. A question mark at the end of an item means that it is nullable, or otherwise may not be available in all states.

```JS
UncivGame():
	ScriptingState(): // Persistent per UncivGame().
		ScriptingScope():
			civInfo? // These are set by WorldScreen init, and unset by MainMenuScreen.
			gameInfo?
			uncivGame
			worldScreen?
		*ScriptingBackend():
			scriptingScope
			?ScriptingReplManager():
				Blackbox() // Common interface to wrap foreign interpreter with pipes, STDIN/STDOUT, queues, sockets, embedding, JNI, etc.
				scriptingScope
				ScriptingProtocol():
					scriptingScope
			?folderHandler: setupInterpreterEnvironment() // If used, a temporary directory with file structure copied from engine and shared folders in `assets/scripting`.
	ConsoleScreen(): // Persistent as long as window isn't resized. Recreates itself and restores most of its state from scriptingState if resized.
		scriptingState
WorldScreen():
	consoleScreen
	scriptingState // ScriptingState has getters and setters that wrap scriptingScope, which WorldScreen uses to update game info.
MainMenuScreen():
	consoleScreen
	scriptingState // Same as for worldScreen.
InstanceTokenizer() // Holds WeakRefs used by ScriptingProtocol. Unserializable objects get strings as placeholders, and then turned back into into objects if seen again.
Reflection() // Used by some hard-coded scripting backends, and essential to dynamic bindings in ScriptingProtocol().
SourceManager() // Source of the folderHandler and setupInterpreterEnvironment() above.
TokenizingJson() // Serializer and functions that use InstanceTokenizer.
```


# Package com.unciv.scripting.protocol

## REPL Loop

*Implemented by `class ScriptingProtocolReplManager(){}`.*

1. A scripted action is initiated from the Kotlin side, by sending a command string to the script interpreter.
	1. While the script interpreter is running, it has a chance to request values from the Kotlin side by sending back packets encoding attribute/property, key, and call, and assignment stacks.
	2. When the Kotlin side receives a request for a value, it uses reflection to access the requested property, call the requested method, or assign to the requested property, and it sends the result to the script interpreter. No changes to gameInfo state should happen during this loop except for what is specifically requested by the running script.
	3. When the script interpreter finishes running, it sends a special packet to the Kotlin side communicating that the script interpreter has no more requests to make. The script interpreter then sends the REPL output of the command to the Kotlin side.
2. When the Kotlin interpreter receives the packet marking the end of the command run, it stops listening for value requests packets. It then receives the command result as the next value, and passes it back to the console screen or script handler.

From Kotlin:
```Python
fun ExecuteCommand(command:String):
	SendToInterpreter(command)
	LockGameInfo()
	while True:
		packet:Packet = ReceiveFromInterpreter().parsed()
		if isPropertyRequest(packet):
			UnlockGameInfo()
			response:Packet = ResolvePacket(scriptingScope, packet)
			LockGameInfo()
			SendToInterpreter(response)
		else if isCommandEndPacket(packet):
			break
	UnlockGameInfo()
	PrintToConsole(ReceiveFromInterpreter().parsed().data:String)
```

The "packets" should probably all be encoded as strings, probably JSON. The technique used to connect the script interpreter to the Kotlin code shouldn't matter, as long as it's wrapped up in and implements the `Blackbox` interface. IPC/embedding based on pipes, STDIN/STDOUT, sockets, queues, embedding, JNI, etc. should all be interchangeable and equally functional.

I'm not sure if there'd be much point to or a good technique for letting the script interpreter run constantly and initiate actions on its own, instead of waiting for commands from the Kotlin side.

You would presumably have to interrupt the main Kotlin-side thread anyway in order to safely run any actions initiated by the script interpreter— Which means that you may as well just register a handler to call the script interpreter at that point.

Plus, letting the script interpreter run completely in parallel would probably introduce potential for all sorts of issues with non-deterministic synchronicity, and performance issues  Calling the script interpreter from the Kotlin side means that the state of the Kotlin side is more predictable at the moment of script execution.

![This simple, thirty-step process is all it takes to execute a single scripted command…](/extraImages/ScriptingCallTrace.png)

(There may be more calls to TokenizingJson than shown in the above diagram, because it's used as the universal serialization tool for encoding and decoding every packet, as well as the arbitrarily-structured JsonElement data in every packet.)

## IPC Protocol

*Implemented by `ScriptingProtocol.kt`, `ipc.py`, and `wrapping.py`.*

A single IPC action consists of one request packet and one response packet.\
A request packet should always be followed by a response packet if it has an action.\
If a request packet has a null action, then it should not be followed by a response. This is to let flags be sent without generating useless responses.

Responses do not have to be sent in the same order as their corresponding requests. New requests can be sent out while old ones are left "open"— E.G., if creating a response requires requesting new information.

(So far, I think all the requests and responses follow a "stack" model, though. If request B is sent out before response A is received, then response B gets received before response A, like parentheses. The time two requests remain open can be completely nested subsets or supersets, or they can be completely separated in series, but they don't currently ever partially overlap.)

(That said, none of these are hard requirements. If you want to do something fancy with coroutines or whatever and dispatch to multiple open request handlers, and you can make it both stable and language-agnostic, go right ahead.)

Both the Kotlin side and the script interpreter can send and receive packets, but not necessarily at all times.

(The current loop is described in the section above. Kotlin initiates a scripting exec, during which the script interpreter can request values from Kotlin, and at the end of which the script interpreter sends its STDOUT response to the Kotlin side.)

A single packet is a JSON string of the form:

*Implemented by `data class ScriptingPacket(){}` and `class ForeignPacket()`.*

```JS
{
	"action": String?,
	"identifier": String?,
	"data": Any?,
	"flags": Collection<String>
}
```

Identifiers should be set to a unique value in each request.\
Each response should have the same identifier as its corresponding request.\
Upon receiving a response, both its action and identifier should be checked to match the relevant request.

---

*Implemented by `object InstanceTokenizer{}` and `object TokenizingJson{}`.*

The data field is allowed to represent any hierarchy, of instances of any types.

If it must represent instances that are not possible or not useful to serialize as JSON hierarchies, then unique identifying token strings should be generated and sent in the places of those instances.

If those strings are received at any hierarchical depth in the data field of any later packets, then they are to be substituted with their original instances in all uses of the information from those packets.\
If the original instance of a received token string no longer exists, then an exception should be thrown, and handled as would any exception at the point where the instance is to be accessed.

Example Kotlin-side instance requested by script interpreter:

```Kotlin
SomeKotlinInstance@M3mAdDr
```

Example response packet to send this instance to the script interpreter:

```JSON
{
	"action": "read_response",
	"identifier": "ABC001",
	"data": {
		"value": "_someStringifiedTokenForSomeKotlinInstance",
		"exception": null
	},
	"flags": []
}
```

Example subsequent request packet from script interpreter using the token string:

```JSON
{
	"action": "assign",
	"identifier": "CDE002",
	"data": {
		"path": [{"type": "Property", "name": "someProperty", "params": []}],
		"value": [5, "ActualStringValue", "_someStringifiedTokenForSomeKotlinInstance"]
	},
	"flags": []
}
```

Equivalent Kotlin-side assignment operation resulting from this later request packet:

```Kotlin
someProperty = listOf(5, "ActualStringValue", SomeKotlinInstance@M3mAdDr)
```

(Caveats in practice: The scripting API design is highly asymmetric in that script interpreter needs a lot of access to the Kotlin side's state, but the Kotlin side should rarely or never need the script interpreter's state, so the script interpreter doesn't have to bother implementing its own arbitrary object tokenization. Requests sent by the Kotlin side also all have very simple response formats because of this, while access to and use of complicated Kotlin-side instances is always initiated by a request from the script interpreter while in the execution loop of its REPL, so the Kotlin side bothers implementing arbitrary instance tokenization only when receiving requests and not when receiving responses. Exceptions from reifying invalid received tokens on the Kotlin side should be handled as would any other exceptions at their code paths, but because such tokens are only used on the Kotlin side when preparing a response to a received request from the scripting side, that currently means sending a response packet that is marked in some way as representing an exception and then carrying on as normal.)

---

Some action types, data formats, and expected response types and data formats for packets sent from the Kotlin side to the script interpreter include:

*Implemented by `class ScriptingProtocol(){}` and `class UncivReplTransceiver()`.*

	```
	'motd': null ->
		'motd_response': String
	```

	```
	'autocomplete': {'command': String, 'cursorpos': Int} ->
		'autocomplete_response': Collection<String> or String
		//List of matches, or help text to print.
	```

	```
	'exec': String ->
		'exec_response': String
		//REPL print.
		//Response may include 'Exception' flag. (Not implemented.)
	```

	```
	'terminate': null ->
		'terminate_response': String?
		//Error message or null.
	```

The above are basically a mirror of ScriptingBackend, so the same interface can be implemented in the scripting language.

---

Some action types, data formats, and expected response types and data formats for packets sent from the script interpreter to the Kotlin side include:

*Implemented by `class ScriptingProtocol(){}` and `class ForeignObject()`.*

	```
	'read': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'read_reponse': Any? or String
		//Attribute/property access, by list of `PathElement` properties, relative to the root object if given or a default scope otherwise.
		//The use_root and root fields are optional.
		//Response must be String if sent with Exception flag.
	```

	```
	'assign': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>, 'value': Any} ->
		'assign_response': String?
		//Error message or null.
	```

	```
	'delete': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'delete_response': String?
		//Error message or null.
		//Only meaningful and implemented for MutableMap() keys and MutableList() indices.
	```

	```
	'dir': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'dir_response': Collection<String> or String
		//Names of all members/properties/attributes/methods.
		//Response must be String if sent with Exception flag.
	```

	```
	//'hash': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		//'hash_response': Any? or String
		//Response must be String if sent with Exception flag.
		//Implemented, but removed. I'm not actually sure what the use of this would be. Hashes are usually just a shortcut for (in)equality, so I think a Kotlin-side equality or identity operator might be needed for this to be useful.
	```

	```
	'keys': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'keys_response': Collection<Any?> or String
		//Response must be String if sent with Exception flag.
		//Keys of Map-interfaced instances. Used by Python bindings for iteration and autocomplete.
	```

	```
	'length': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'length_response': Int or String
		//Response must be String if sent with Exception flag.
		//Used by Python bindings for length and also for iteration.
	```

	```
	'contains': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>, 'value': Any?} ->
		'contains_response': Boolean or String
		//Doing this through an IPC call instead of in the script interpreter should let tokenized instances be checked for properly.
		//Response must be String if sent with Exception flag.
	```

	```
	//'isiterable': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		//'isiterable_response': Boolean or String
		//Response must be String if sent with Exception flag.
		//Not implemented. Implement if needed.
	```

	```
	'ismapping': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'isiterable_response': Boolean or String
		//Response must be String if sent with Exception flag.
		//Used by Python bindings to hide Python-emulating .values, .keys, and .entries to allow access to Kotlin objects when not a mapping.
	```

	```
	'callable': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'callable_response': Boolean or String
		//Response must be String if sent with Exception flag.
		//Used by Python autocompleter to add opening bracket to methods and function suggestions. Quite useful for exploring API at a glance.
	```

	```
	'args': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'args_response': Map<String, List<Pair<String?, String>>> or String
		//Map of dispatchable signatures as strings to lists of pairs of names and types of arguments accepted by a function.
		//Response must be String if sent with Exception flag.
		//Currently just used by Python autocompleter to generate help text.
		//Could also be used to control function signatures in scripting environment. If so, then names of types should be standardized.
	```

	```
	'docstring': {'use_root' Boolean, 'root': Any?, 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
		'docstring_response': String or String
		//Response must be String if sent with Exception flag.
		//Used by Python wrappers and autocompleter to get help text showing arguments and types for callables. Useful for exploring API without having to browse code.
	```

The path elements in some of the data fields mirror PathElement.

---

Flags are string values for communicating extra information that doesn't need a separate packet or response. Depending on the flag and action, they may be contextual to the packet, or they may not. I think I see them mostly as a way to semantically separate meta-communication about the protocol from actual requests for actions:

*Implemented by `enum class KnownFlag(){}` and `class UncivReplTransceiver()`.*

	```
	'PassMic'
		//Indicates that the sending side has no more requests to make, and that the receiving side should either send the next request or expect a response to an open request.
		//Sent by Kotlin side at start of script engine startup/MOTD, autocompletion, and execution to allow script to request values, and should be sent by script interpreter immediately before sending response with results of execution.
	```

	```
	'Exception'
		//Indicates that this packet is associated with an error.
		//Currently sent only by Kotlin side and handled only by Python backend. Modding API and build tests will need this to be implemented in the other direction too.
	```

	```
	//'BeginIteration'
	//'EndIteration'
		//Not implemented. Probably needed if iteration over non-sized objects is needed. Probably not worth the trouble.
		//Deprecated without ever being implemented. Lack of indices for such objects means you wouldn't be able to directly do anything with their iterated results anyway in the same way that you can for "lists" "iterated" by appending indices to their paths, as you wouldn't have a path by which to refer back to them.
		//Alternate solutions: Sets are already serialized as JSON arrays, which can be resolved in running scripts. Script-accessible Kotlin-side helper functions can be defined to convert other containers to lists, or if needed, to yield their values per call. Instance tokens arising from these operations can be assigned by running scripts to a name on the Kotlin side, creating a concrete path by which to reflectively access their own members.
	```

---

Thus, at the IPC level, all foreign backends will actually use the same language, which is this JSON-based protocol. Differences between Python, JS, Lua, etc. will all be down to how they interpret the "exec", "autocomplete", and "motd" requests differently, and how they use and expose the Kotlin/JVM-access request types differently, which each high-level scripting language is free to implement as works best for it.

## Python Binding Implementation

A description of how this REPL loop and IPC protocol are used to build a scripting langauage binding [is at `/android/assets/scripting/enginefiles/python/PythonScripting.md`](../android/assets/scripting/enginefiles/python/PythonScripting.md).

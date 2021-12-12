# This should never be used to run untrusted code. AFAICT, Python is basically impossible to sandbox, short of running it in a VM.
# Example: https://lwn.net/Articles/574215/
# Even if Python's sandboxed, the full reflective access on the Kotlin/JVM side isn't.

# Huh: https://stackoverflow.com/questions/15093663/packaging-linux-binary-in-android-apk


#"""Due to the massive standard library and third-party libraries available to Python, due to the similarly heavy footprint of the CPython interpreter, the recommended use cases of this scripting backend are user automation, custom tools, prototyping, and experimentation or research. For mods, use the JS backend instead.

#It is not considered feasible to support scripting by Python on mobile platforms."""


try:
	import os
	with open(os.path.join(os.path.dirname(__file__), "PythonScripting.md"), 'r') as f:
		__doc__ = f.read()
except Exception as e:
	try:
		__doc__ = f"{repr(e)}"
	except:
		pass


try:

	import sys, types

	stdout = sys.stdout

	import unciv_lib


	uncivModule = types.ModuleType(name='unciv', doc=__doc__)

	sys.modules['unciv'] = uncivModule
	# Let the entire API be imported from external scripts.
	# None of this will work on Upy.

	# uncivModule.help = lambda thing=None: print(__doc__) if thing is None else print(unciv_lib.api.get_doc(thing)) if isinstance(thing, unciv_lib.wrapping.ForeignObject) else help(thing)

	replScope = {'help': lambda thing=None: print(__doc__) if thing is None else print(unciv_lib.api.get_doc(thing)) if isinstance(thing, unciv_lib.wrapping.ForeignObject) else help(thing)}

	# exec('from unciv_pyhelpers import *', replScope, replScope)
	# TODO: This, and the scope update in UncivReplTransceiver, should probably be a default exec in Kotlin-side game options instead.


	foreignAutocompleter = unciv_lib.autocompletion.PyAutocompleteManager(replScope, **unciv_lib.api.autocompleterkwargs)

	foreignActionReceiver = unciv_lib.api.UncivReplTransceiver(scope=replScope, apiscope=uncivModule.__dict__, autocompleter=foreignAutocompleter)

	foreignActionReceiver.ForeignREPL()

	raise RuntimeError("No REPL. Did you forget to uncomment a line in `main.py`?")

except Exception as e:
	# try:
		# import unciv_lib.utils
		# exc = unciv_lib.utils.formatException(e)
		# Disable this. A single line with undefined format is more likely to be printed than multiple.
	# except:
		# exc = repr(e)
	print(f"Fatal error in Python interepreter: {repr(e)}", file=stdout, flush=True)

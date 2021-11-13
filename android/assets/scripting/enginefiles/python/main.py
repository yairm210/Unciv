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

	apiScope = uncivModule.__dict__
	# None of this will work on Upy.

	apiScope.update(unciv_lib.api.Expose)

	apiScope['help'] = lambda thing=None: print(__doc__) if thing is None else print(unciv_lib.api.get_doc(thing)) if isinstance(thing, unciv_lib.wrapping.ForeignObject) else help(thing)


	foreignAutocompleter = unciv_lib.autocompletion.PyAutocompleteManager(apiScope, **unciv_lib.api.autocompleterkwargs)

	foreignActionReceiver = unciv_lib.api.UncivReplTransciever(scope=apiScope, autocompleter=foreignAutocompleter)

	foreignActionReceiver.ForeignREPL()

	raise RuntimeError("No REPL. Did you forget to uncomment a line in `main.py`?")

except Exception as e:
#	try:
#		import unciv_lib.utils
#		exc = unciv_lib.utils.formatException(e)
#		# Disable this. A single line with undefined format is more likely to be printed than multiple.
#	except:
#		exc = repr(e)
	print(f"Fatal error in Python interepreter: {repr(e)}", file=stdout, flush=True)

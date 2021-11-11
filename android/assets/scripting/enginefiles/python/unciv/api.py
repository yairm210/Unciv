import json, os, builtins

from . import ipc

_enginedir = os.path.dirname(__file__)

def _readlibfile(fp):
	try:
		# In an Unciv scripting backend, `SourceManager.kt` uses LibGDX to merge `sharedfiles/` with `enginedaata/{engine}/` in a temporary directory.
		with open(os.path.join(_enginedir, "..", fp)) as file:
			return file.read()
	except:
		# For debug with standalone Python, `sharedfiles` has to be accessed manually.
		with open(os.path.join(_enginedir, "../../../sharedfiles", fp)) as file:
			return file.read()
		
_apiconstants = json.loads(_readlibfile("ScriptAPIConstants.json"))


Expose = {}

def expose(name=None):
	def _expose(obj):
		Expose[name or obj.__name__] = obj
		return obj
	return _expose


def _get_keys(obj):
	try:
		return obj.keys()
	except (AttributeError, ipc.ForeignError):
		return ()

def _get_doc(obj):
	try:
		if isinstance(obj, wrapping.ForeignObject):
			doc = f"\n\n{str(obj._docstring_() or wrapping.stringPathList(obj._getpath_()))}\n\nArguments:\n"
			doc += "\n".join(f"\t{argname}: {argtype}" for argname, argtype in obj._args_())
			return doc
		else:
			return obj.__doc__
	except AttributeError:
		return None
		

@expose()
def callable(obj):
	if isinstance(obj, wrapping.ForeignObject):
		return obj._callable_(raise_exceptions=False)
	else:
		return builtins.callable(obj)


_autocompleterkwargs = {
	'get_keys': _get_keys,
	'get_doc': _get_doc,
	'check_callable': callable
}


@expose()
def real(obj):
	"""Evaluate a foreign object wrapper into a real Python value, or return a value unchanged if it is not a foreign object wrapper."""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._getvalue_()
	return obj

@expose()
def isForeignToken(obj):
	"""Return whether an object represents a token for a non-serializable foreign object."""
	resolved = real(obj)
	return isinstance(resolved, str) and resolved.startswith(_apiconstants['kotlinInstanceTokenPrefix'])


from . import wrapping
# Should only need it at run time anyway, so makes a circular import more predictable. Basically, this whole file gets prepended to `wrapping` under the `api` name.

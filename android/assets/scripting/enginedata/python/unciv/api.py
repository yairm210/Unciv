import json, os

_enginedir = os.path.dirname(__file__)

def _readlibfile(fp):
	try:
		# In an Unciv scripting backend, `SourceManager.kt` uses LibGDX to merge `shareddata/` with `enginedaata/{engine}/` in a temporary directory.
		with open(os.path.join(_enginedir, "..", fp)) as file:
			return file.read()
	except:
		# For debug with standalone Python, `shareddata` has to be accessed manually.
		with open(os.path.join(_enginedir, "../../../shareddata", fp)) as file:
			return file.read()
		
_apiconstants = json.loads(_readlibfile("ScriptAPIConstants.json"))


Expose = {}

def expose(name=None):
	def _expose(obj):
		Expose[name or obj.__name__] = obj
		return obj
	return _expose


@expose()
def real(obj):
	"""Evaluate a foreign object wrapper into a real Python value, or return a value unchanged if it is not a foreign object wrapper."""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._getvalue()
	return obj

@expose()
def isForeignToken(obj):
	"""Return whether an object represents a token for a non-serializable foreign object."""
	resolved = real(obj)
	return isinstance(resolved, str) and resolved.startswith(_apiconstants['kotlinObjectTokenPrefix'])


from . import wrapping
# Should only need it at run time anyway, so makes a circular import more predictable. Basically, this whole file gets prepended to `wrapping` under the `api` name.

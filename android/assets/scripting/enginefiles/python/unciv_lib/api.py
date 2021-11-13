import json, os, builtins, sys

from . import ipc, utils


enginedir = os.path.dirname(__file__)

def readlibfile(fp):
	"""Return the text contents of a file that should be available"""
	try:
		# In an Unciv scripting backend, `SourceManager.kt` uses LibGDX to merge `sharedfiles/` with `enginedaata/{engine}/` in a temporary directory.
		with open(os.path.join(enginedir, "..", fp)) as file:
			return file.read()
	except OSError:
		# For debug with standalone Python, `sharedfiles` has to be accessed manually.
		with open(os.path.join(enginedir, "../../../sharedfiles", fp)) as file:
			return file.read()

apiconstants = json.loads(readlibfile("ScriptAPIConstants.json"))


Expose = {}

def expose(name=None):
	"""Returns a decorator that adds objects to a mapping of names to expose in the Unciv Python scripting API."""
	def _expose(obj):
		Expose[name or obj.__name__] = obj
		return obj
	return _expose


def get_keys(obj):
	"""Get keys of object. Fail silently if it has no keys. Used to let PyAutocompleter work with ForeignObject."""
	try:
		return obj.keys()
	except (AttributeError, ipc.ForeignError):
		return ()

def get_doc(obj):
	"""Get docstring of object. Fail silently if it has none, or generate one if it's a ForeignObject(). Used for PyAutocompleter."""
	try:
		if isinstance(obj, wrapping.ForeignObject):
			doc = f"\n\n{str(obj._docstring_() or wrapping.stringPathList(obj._getpath_()))}\n\nArguments:\n"
			# TODO: This should proably be in ForeignObject.__getattr__/__getattribute__.
			doc += "\n".join(f"\t{argname}: {argtype}" for argname, argtype in obj._args_())
			return doc
		else:
			return obj.__doc__
	except AttributeError:
		return None


@expose()
def callable(obj):
	"""Return whether or not an object is callable. Used to let PyAutocompleter work with ForeignObject"""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._callable_(raise_exceptions=False)
	else:
		return builtins.callable(obj)


autocompleterkwargs = {
	'get_keys': get_keys,
	'get_doc': get_doc,
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
	return isinstance(resolved, str) and resolved.startswith(apiconstants['kotlinInstanceTokenPrefix'])


class UncivReplTransciever(ipc.ForeignActionReceiver, ipc.ForeignActionSender):
	"""Class that implements the Unciv IPC and scripting protocol by receiving and responding to its packets."""
	def __init__(self, *args, autocompleter=None, **kwargs):
		ipc.ForeignActionReceiver.__init__(self, *args, **kwargs)
		self.autocompleter = autocompleter
	def populateApiScope(self):
		"""Use dir() on a foreign object wrapper with an empty path to populate the execution scope with all available names."""
		names = dir(wrapping.ForeignObject((), foreignrequester=self.GetForeignActionResponse))
		for n in names:
			if n not in self.scope:
				self.scope[n] = wrapping.ForeignObject(n, foreignrequester=self.GetForeignActionResponse)
	def passMic(self):
		"""Send a 'PassMic' packet."""
		self.SendForeignAction({'action':None, 'identifier': None, 'data':None, 'flags':('PassMic',)})
	@ipc.receiverMethod('motd', 'motd_response')
	def EvalForeignMotd(self, packet):
		"""Populate the exeuction scope, and then reply to a MOTD request."""
		self.populateApiScope()
		self.passMic()
		return f"""
sys.implementation == {str(sys.implementation)}

Press [TAB] at any time to trigger autocompletion at the current cursor position, or display help text for an empty function call.

"""
	@ipc.receiverMethod('autocomplete', 'autocomplete_response')
	def EvalForeignAutocomplete(self, packet):
		assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
		res = self.autocompleter.GetAutocomplete(packet.data["command"], packet.data["cursorpos"]) if self.autocompleter else "No autocompleter set."
		self.passMic()
		return res
	@ipc.receiverMethod('exec', 'exec_response')
	def EvalForeignExec(self, packet):
		line = packet.data
		assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
		with ipc.FakeStdout() as fakeout:
			print(f">>> {str(line)}")
			try:
				try:
					code = compile(line, 'STDIN', 'eval')
				except SyntaxError:
					exec(compile(line, 'STDIN', 'exec'), self.scope, self.scope)
				else:
					print(eval(code, self.scope, self.scope))
			except Exception as e:
				print(utils.formatException(e))
			finally:
				self.passMic()
				return fakeout.getvalue()

	@ipc.receiverMethod('terminate', 'terminate_response')
	def EvalForeignTerminate(self, packet):
		return None


from . import wrapping
# Should only need it at run time anyway, so import at end makes a circular import more predictable. Basically, this whole file gets prepended to `wrapping` under the `api` name.

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
		return obj.keys() # FIXME: This results in function calls over IPC since hiding the .keys() IPC protcol-based method for non-mappings in ForeignObject.
	except (AttributeError, ipc.ForeignError):
		return ()

def get_help(obj):
	"""Get docstring of object. Fail silently if it has none, or get one through its IPC methods if it's a ForeignObject(). Used for PyAutocompleter."""
	try:
		if isinstance(obj, wrapping.ForeignObject):
			doc = f"\n\n{str(obj._docstring_() or wrapping.stringPathList(obj._getpath_()))}\n"
			# TODO: Can this be in ForeignObject.__getattr__/__getattribute__?
			for funcsig, funcargs in obj._args_().items():
				doc += f"\n{funcsig}\n"
				doc += "\n".join(f"\t{argname}: {argtype}" for argname, argtype in funcargs)
			return doc
		else:
			with ipc.FakeStdout() as fakeout:
				print()
				help(obj)
				return fakeout.getvalue()
	except Exception as e:
		return f"Error accessing help text: {repr(e)}"


@expose()
def callable(obj):
	"""Return whether or not an object is callable. Used to let PyAutocompleter work with ForeignObject by calling the latters' IPC callability method."""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._callable_(raise_exceptions=False)
	else:
		return builtins.callable(obj)


autocompleterkwargs = {
	'get_keys': get_keys,
	'get_help': get_help,
	'check_callable': callable
}


@expose()
def real(obj):
	"""Evaluate a foreign object wrapper into a real Python value, or return a value unchanged if not given a foreign object wrapper."""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._getvalue_()
	return obj

@expose()
def isForeignToken(obj):
	"""Return whether an object represents a token for a non-serializable foreign object."""
	resolved = real(obj)
	return isinstance(resolved, str) and resolved.startswith(apiconstants['kotlinInstanceTokenPrefix'])

@expose()
def pathcodeFromWrapper(wrapper):
	return wrapping.stringPathList(wrapper._getpath_())
	# TODO

expose()(ipc.ForeignError)


class UncivReplTransceiver(ipc.ForeignActionReceiver, ipc.ForeignActionSender):
	"""Class that implements the Unciv IPC and scripting protocol by receiving and responding to its packets. See Module.md."""
	def __init__(self, *args, apiscope=None, autocompleter=None, **kwargs):
		ipc.ForeignActionReceiver.__init__(self, *args, **kwargs)
		self.autocompleter = autocompleter
		self.apiscope = {} if apiscope is None else apiscope
	def populateApiScope(self):
		"""Use dir() on a foreign object wrapper with an empty path to populate the execution scope with all available names."""
		uncivscope = wrapping.ForeignObject(path=(), foreignrequester=self.GetForeignActionResponse)
		#self.apiscope['unciv'] = uncivscope
		for n in dir(uncivscope):
			if n not in self.apiscope:
				self.apiscope[n] = wrapping.ForeignObject(path=n, foreignrequester=self.GetForeignActionResponse)
		# self.scope.update({**self.apiscope, **self.scope})
		# TODO: Populate module, let scripts import it themselves.
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

Current imports:
	from unciv import *
	from unciv_pyhelpers import *

Run "help()", or read PythonScripting.md, for an overview of this API.

Extensive example scripts can be imported as the "unciv_scripting_examples" module.
These can also also accessed from the game files either externally or through the API:
	print(apiHelpers.assetFileString("scripting/enginefiles/python/unciv_scripting_examples/PlayerMacros.py"))

Press [TAB] at any time to trigger autocompletion at the current cursor position, or display help text for an empty function call.

""", ()#TODO: Replace current imports with startup command managed by ConsoleScreen and GameSettings.
	@ipc.receiverMethod('autocomplete', 'autocomplete_response')
	def EvalForeignAutocomplete(self, packet):
		assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
		res = self.autocompleter.GetAutocomplete(packet.data["command"], packet.data["cursorpos"]) if self.autocompleter else "No autocompleter set."
		self.passMic()
		return res, ()
	@ipc.receiverMethod('exec', 'exec_response')
	def EvalForeignExec(self, packet):
		line = packet.data
		assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
		with ipc.FakeStdout() as fakeout:
			print(f">>> {str(line)}")
			isException = False
			try:
				try:
					code = compile(line, 'STDIN', 'eval')
				except SyntaxError:
					exec(compile(line, 'STDIN', 'exec'), self.scope, self.scope)
				else:
					print(repr(eval(code, self.scope, self.scope)))
			except Exception as e:
				print(utils.formatException(e))
				isException = True
			finally:
				self.passMic()
				return fakeout.getvalue(), (('Exception',) if isException else ())
	@ipc.receiverMethod('terminate', 'terminate_response')
	def EvalForeignTerminate(self, packet):
		return None, ()


from . import wrapping
# Should only need it at run time anyway, so import at end makes a circular import more predictable. Basically, this whole file gets prepended to wrapping under the api name.

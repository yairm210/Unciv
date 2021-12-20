import os, atexit, random, time, sys

import unciv, unciv_pyhelpers


RegistryKey = "python-package:Unciv/unciv_scripting_examples"

registeredInstances = unciv.apiHelpers.registeredInstances

if RegistryKey not in registeredInstances:
	registeredInstances[RegistryKey] = {}

memalloc = registeredInstances[RegistryKey]


def singleton(*args, **kwargs):
	def _singleton(cls):
		return cls(*args, **kwargs)
	return _singleton


def exampleAssetPath(*path):
	return os.path.join(os.path.dirname(__file__), "example_assets", *path)


class TokensAsWrappers:
	def __init__(self, *tokens):
		self.tokens = tokens
		self.memallocKeys = []
	currentRegisteredKeys = set()
	@classmethod
	def genUniqueKey(cls):
		key = None
		while key is None or key in cls.currentRegisteredKeys:
			key = f"{random.getrandbits(30)}_{time.time_ns()}"
		cls.currentRegisteredKeys.add(key)
		return key
	@classmethod
	def freeUniqueKey(cls, key):
		cls.currentRegisteredKeys.remove(key)
	def __enter__(self):
		global memalloc
		for token in self.tokens:
			assert unciv_pyhelpers.isForeignToken(token)
			key = self.genUniqueKey()
			memalloc[key] = token
			self.memallocKeys.append(key)
		return tuple(memalloc[k] for k in self.memallocKeys)
	def __exit__(self, *exc):
		global memalloc
		for key in self.memallocKeys:
			del memalloc[key]
			self.freeUniqueKey(key)
		self.memallocKeys.clear()


def execCodeInModule(moduleQualname, code):
	exec(code, sys.modules[moduleQualname].__dict__, None)

def makeLocalLambdaCode(moduleQualname, code):
	"""Return a Python code string that, when executed, will execute the given code string inside the module at the given qualified name."""
	return f'import {__name__}; {__name__}.execCodeInModule({repr(moduleQualname)}, {repr(code)})'
	# Could cache a compile(code) if Python performance is a huge issue.


@atexit.register
def on_exit():
	# I don't think this actually works.
	del unciv.apiHelpers.instanceRegistry[RegistryKey]

import os, atexit, random, time

import unciv, unciv_pyhelpers


RegistryKey = "python-package:Unciv/unciv_scripting_examples"

unciv.apiHelpers.registeredInstances[RegistryKey] = {}

memalloc = unciv.apiHelpers.registeredInstances[RegistryKey]


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


@atexit.register
def on_exit():
	# I don't think this actually works.
	del unciv.apiHelpers.instanceRegistry[RegistryKey]

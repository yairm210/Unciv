"""
Automated testing of practical Python scripting examples.
Intended to also catch breaking changes to the scripting API, IPC protocol, and reflective tools.


Call TestRunner.run_tests() to use.

Pass debugprint=False if running from Kotlin as part of build tests, because running scripts' STDOUT is already captured by the Python REPL and sent to Kotlin code, which should then check for the presence of the 'Exception' IPC packet flag.
"""

import os

import unciv, unciv_pyhelpers#, unciv_lib

from . import EndTimes, ExternalPipe, MapEditingMacros, Merfolk, PlayerMacros, ProceduralTechtree, Utils


# from unciv_scripting_examples.Tests import *; TestRunner.run_tests()
# from unciv_scripting_examples.Tests import *; InMapEditor.__enter__()

try:
	assert False
	# Can also check __debug__. Meh. Explicit(ly using the behaviour) is better here than implicit(ly relying on related behaviour).
except:
	pass
else:
	raise RuntimeError("Assertions must be enabled to run Python tests.")


with open(Utils.exampleAssetPath("Elizabeth300"), 'r') as save:
	# TODO: Compress this.
	# Unciv uses Base64 and GZIP.
	Elizabeth300 = save.read()


def getTestGame():
	return unciv.Unciv.GameSaver.gameInfoFromString(Elizabeth300)

def goToMainMenu():
	unciv.uncivGame.setScreen(unciv.apiHelpers.Jvm.constructorByQualname['com.unciv.MainMenuScreen']())#unciv.apiHelpers.Jvm.Gui.MainMenuScreen())


@Utils.singleton()
class InGame:
	"""Context manager object that loads a test save on entrance and returns to the main menu on exit."""
	def __enter__(self):
		unciv.uncivGame.loadGame(getTestGame())
	def __exit__(self, *exc):
		goToMainMenu()

@Utils.singleton()
class InMapEditor:
	"""Context manager object that loads a test map in the map editor on entrance and returns to the main menu on exit."""
	def __enter__(self):
		with Utils.TokensAsWrappers(getTestGame()) as (gameinfo,):
			unciv.uncivGame.setScreen(
				unciv.apiHelpers.Jvm.constructorByQualname['com.unciv.ui.mapeditor.MapEditorScreen'](gameinfo.tileMap)
				# SetScreen doesn't seem to be needed here. But that seems like a glitch in the core Unciv code.
			)
	def __exit__(self, *exc):
		goToMainMenu()


@Utils.singleton()
class TestRunner:
	"""Class for registering and running tests."""
	# No point using any third-party or Standard Library testing framework, IMO. The required behaviour's simple enough, and the output format to Kotlin ('Exception' flag or not) is direct enough that it's easier and more concise to just implement everything here.
	def __init__(self):
		self._tests = []
	class _TestCls:
		"""Class to define and run a single test. Accepts the function to test, a human-readable name for the test, a context manager with which to run it, and args and kwargs with which to call the function."""
		def __init__(self, func, name=None, runwith=None, args=(), kwargs={}):
			self.func = func
			self.name = getattr(func, '__name__', None) if name is None else name
			self.runwith = runwith
			self.args = args
			self.kwargs = kwargs
		def __call__(self):
			if self.runwith is None:
				self.func(*self.args, **self.kwargs)
			else:
				with self.runwith:
					self.func(*self.args, **self.kwargs)
	def keys(self):
		return [t.name for t in self._tests]
	def __getitem__(self, key):
		return next(t for t in self._tests if t.name == key)
	def Test(self, *args, **kwargs):
		"""Return a decorator that registers a function to be run as a test, and then returns it unchanged. Accepts the same configuration arguments as _TestCls."""
		# Return values aren't checked. A call that completes is considered a pass. A call that raises an exception is considered a fail.
		# If you need to check return values for a function, then just wrap them in another function with an assert.
		def _testdeco(func):
			self._tests.append(self._TestCls(func, *args, **kwargs))
			return func
		return _testdeco
	def run_tests(self, *, debugprint=True):
		"""Run all registered tests, printing out their results, and raising an exception if any of them fail."""
		failures = {}
		def _print(*args, **kwargs):
			print(*args, **kwargs)
			if debugprint:
				# When run as part of build, the Kotlin test-running code should be capturing the Python STDOUT anyway.
				unciv.apiHelpers.Sys.printLine(str(args[0]) if len(args) == 1 and not kwargs else " ".join(str(a) for a in args))
		for test in self._tests:
			try:
				test()
			except Exception as e:
				failures[test] = e
				n, t = '\n\t'
				_print(f"Python test FAILED: {test.name}\n\t{repr(e).replace(n, n+t)}")
			else:
				_print(f"Python test PASSED: {test.name}")
		_print("\n")
		if failures:
			failcounts = {}
			for exc in failures.values():
				exc_name = exc.__class__.__name__
				if exc_name not in failcounts:
					failcounts[exc_name] = 0
				failcounts[exc_name] += 1
			del exc_name
			exc = AssertionError(f"{len(failures)} Python tests FAILED: {[test.name for test in failures]}\nFailure types: {failcounts}\n\n")
			_print(exc)
			raise exc
		else:
			_print(f"All {len(self._tests)} Python tests PASSED!\n\n")



#### Tests begin here. ####


@TestRunner.Test(runwith=InGame)
def LoadGameTest():
	"""Example test. Explicitly tests that the InGame context manager is working."""
	# The other tests below are all set up the same, just by explicitly passing existing functions to the registration function instead of using it as a decorator.
	assert unciv.apiHelpers.isInGame
	for v in (unciv.gameInfo, unciv.civInfo, unciv.worldScreen):
		assert unciv_pyhelpers.real(v) is not None
		assert unciv_pyhelpers.isForeignToken(v)


@TestRunner.Test(runwith=InGame, name="NoPrivatesTest-InGame", args=(unciv, 2))
@TestRunner.Test(runwith=InMapEditor, name="NoPrivatesTest-InMapEditor", args=(unciv, 2))
def NoPrivatesTest(start, maxdepth, *, _depth=0, _failures=None, _namestack=None):
	# Would have to differentiate between unitialized properties and the like, and privates.
	if _failures is None:
		_failures = []
	if _namestack is None:
		_namestack = ()
	try:
		names = dir(start)
	except:
		_failures.append('.'.join(_namestack))
	else:
		for name in dir(start):
			namestack = (*_namestack, name)
			try:
				v = getattr(start, name)
			except:
				_failures.append('.'.join(namestack))
			else:
				if _depth < maxdepth:
					NoPrivatesTest(v, maxdepth, _depth=_depth+1, _failures=_failures, _namestack=namestack)
	if _depth == 0:
		assert not _failures, _failures

# Tests for PlayerMacros.py.

TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities, 0.5))(
	PlayerMacros.gatherBiggestCities
)
TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities,))(
	PlayerMacros.clearCitiesProduction
)
TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities, ("Scout", "Warrior", "Worker")))(
	PlayerMacros.addCitiesProduction
)
TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities,))(
	PlayerMacros.clearCitiesSpecialists
)
TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities,))(
	PlayerMacros.focusCitiesFood
)
TestRunner.Test(runwith=InGame, args=(unciv.civInfo.cities, ("Monument", "Shrine", "Worker")))(
	PlayerMacros.buildCitiesQueue
)
TestRunner.Test(runwith=InGame)(
	PlayerMacros.rebaseUnitsEvenly
)


# Tests for MapEditingMacros.py.

_m = MapEditingMacros

for _func in (
	_m.spreadResources,
	_m.dilateTileTypes,
	_m.erodeTileType,
	_m.floodFillSelected,
	_m.makeMandelbrot,
	_m.graph2D,
	_m.graph3D,
	_m.loadImageHeightmap,
	_m.loadImageColours
):
	for _cm in (InGame, InMapEditor):
		TestRunner.Test(runwith=_cm, name=f"{_func.__name__}-{_cm.__class__.__name__}")(_func)

del _m, _func, _cm


# Tests for ProceduralTechTree.py.

_m = ProceduralTechtree

for _func in (
	_m.extendTechTree,
	_m.clearTechTree,
	_m.scrambleTechTree
):
	TestRunner.Test(runwith=InGame)(_func)

del _m, _func


#TODO: Add tests. Will probably require exception field in IPC protocol to use.

#Basic IPC protocol specs and Pythonic operators.

#No error in any examples.

#ScriptingScope properties correctly set and nullified by different screens.

#Token reuse, once that's implemented.

#Probably don't bother with DOCTEST, or anything. Just use assert statements where needed, print out any errors, and check in the build tests that there's no exceptions (by flag, or by printout value).



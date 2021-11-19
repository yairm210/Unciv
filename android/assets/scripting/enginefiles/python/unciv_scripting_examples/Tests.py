import unciv, unciv_lib

from . import EndTimes, ExternalPipe, MapEditingMacros, Merfolk, PlayerMacros


try:
	assert False
	# Can also check __debug__.
except:
	pass
else:
	raise RuntimeError("Assertions must be enabled to run Python tests.")


tests = []

class Test:
	def __init__(self, func, name=None, runwith=None, args=(), kwargs={}):
		#Args and kwargs here, but not used.
		self.func = func
		self.name = func.__name__ if name is None else name
		self.runwith = runwith
		self.args = args
		self.kwargs = kwargs
	def __call__(self):
		if self.runwith is None:
			self.func(*self.args, **self.kwargs)
		else:
			with self.runwith:
				self.func(*self.args, **self.kwargs)

def test(name=None, *, runwith=None):
	def _testmaker(func):
		tests.append(Test(test, name=name, runwith=runwith))
		return func
	return _testmaker


class NewGameTest:
	pass

class MapEditorTest:
	pass

class MainMenuTest:
	pass


#TODO: Add tests. Will probably require exception field in IPC protocol to use.

#Basic IPC protocol specs and Pythonic operators.

#No error in any examples.

#ScriptingScope properties correctly set and nullified by different screens.

#Token reuse, once that's implemented.

#Probably don't bother with DOCTEST, or anything. Just use assert statements where needed, print out any errors, and check in the build tests that there's no exceptions (by flag, or by printout value).


def run_tests():
	failures = []
	for test in tests:
		try:
			test()
		except Exception as e:
			failures.append(e)
			print(f"Python test FAILED: {test._testname}\n{unciv_lib.utils.formatException(exc)}")
		else:
			print(f"Python test PASSED: {test._testname}")
	assert not failures


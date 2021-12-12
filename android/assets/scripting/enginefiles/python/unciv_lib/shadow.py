"""
Micro-library meant to allow Unciv scripts to be run even without running the game, by implementing fake versions of as many Python operators as possible. Script logic may error, but the basic control flow shouldn't.

The objects resulting from the shadowed API keep track of how they were created and any further access to them.
The repr() of each object visualizes the tree of everything done under it.

E.G.: Shadowed result from a function that shows an event popup when run in Unciv:

	<Unciv:.apiHelpers.Jvm.constructorByQualname['com.unciv.ui.utils.Popup'](<Unciv:.uncivGame.getScreen()>)>
	│
	├ .addGoodSizedLabel
	│ ├ (Something has happened in your empire!, 24)
	│ │ ├ .row
	│ │ │ ├ ()
	├ .addSeparator
	│ ├ ()
	│ │ ├ .row
	│ │ │ ├ ()
	├ .addGoodSizedLabel
	│ ├ (A societally and politically s...lay a political decision: , 18)
	│ │ ├ .row
	│ │ │ ├ ()
	├ .add
	│ ├ (<Unciv:.apiHelpers.Jvm.constru....ui.utils.BaseScreen'].skin>)>)
	│ │ ├ .row
	│ │ │ ├ ()
	├ .add
	│ ├ (<Unciv:.apiHelpers.Jvm.constru....ui.utils.BaseScreen'].skin>)>)
	│ │ ├ .row
	│ │ │ ├ ()
	├ .open
	│ ├ (False)
"""

import sys, random, re

def rep(obj):
	if isinstance(obj, FakeApi):
		return str(obj)
	else:
		return repr(obj)

def strCall(a, kw):
	return ", ".join(str(p) for l in (a, (f"{k}={rep(v)}" for k, v in kw.items())) for p in l)


def doAction(self, action):
	res = self.__class__(preceding=self, action=action)
	self._following.append(res)
	return res


MagicNames = {'__getattr__', *(n for t in __builtins__.values() if isinstance(t, type) for n, m in t.__dict__.items() if n.startswith('__') and callable(m) and n not in object.__dict__)}

class FakeApiMetaclass(type):
	def __new__(meta, name, bases, namespace, **kwds):
		for n in MagicNames:
			if n not in namespace:
				namespace[n] = lambda self, *a, **kw: doAction(self, f".{n}({strCall(a, kw)})")
		return super(FakeApiMetaclass, meta).__new__(meta, name, bases, namespace, **kwds)


class FakeApi(str, metaclass=FakeApiMetaclass):
	__all__ = ('Unciv', 'apiExecutionContext', 'apiHelpers', 'civInfo', 'gameInfo', 'mapEditorScreen', 'modApiHelpers', 'toString', 'uncivGame', 'worldScreen')
	def _init(self, *, preceding=None, action=None):
		self._preceding = preceding
		self._action = action
		self._following = []
	def __new__(cls, preceding=None, action=""):
		self = str.__new__(cls, "")
		self._init(preceding=preceding, action=action)
		return self
	def __repr__(self):
		lines = [self.__str__(), "│"]
		def traverseChildren(node, depth):
			action = re.sub("\s+", " ", re.sub("[│├]", "", node._action)).strip()
			if len(action) > 65:
				action = action[:31] + "..." + action[-31:]
			lines.append("│ "*(depth-1) + "├ " + action)
			for child in node._following:
				traverseChildren(child, depth+1)
		for child in self._following:
			traverseChildren(child, 1)
		return "\n".join(lines)
	def __str__(self):
		def path():
			x = self
			while x is not None:
				yield str(x._action)
				x = x._preceding
		return f"<Unciv:{''.join(reversed(tuple(path())))}>"
	def __bool__(self):
		# doAction(".__bool__()")
		return True
	def __int__(self):
		# doAction(".__int__()")
		return random.getrandbits(5)
	def __getattr__(self, name):
		return doAction(self, f".{name}")
	def __getitem__(self, key):
		return doAction(self, f"[{rep(key)}]")
	def __call__(self, *a, **kw):
		return doAction(self, f"({strCall(a, kw)})")
	def __next__(self):
		if not random.getrandbits(2):
			raise StopIteration()
		return self
	def __iter__(self):
		yield self
		while not random.getrandbits(2):
			yield self
	def __length_hint__(self):
		return NotImplemented


FAKE_API = FakeApi()

if 'unciv' not in sys.modules:
	sys.modules['unciv'] = FAKE_API

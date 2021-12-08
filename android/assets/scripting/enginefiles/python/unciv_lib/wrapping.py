import json, sys, operator
stdout = sys.stdout

from . import ipc, api


class ForeignRequestMethod:
	"""Decorator and descriptor protocol implementation for methods of ForeignObject subclasses that return values from foreign requests."""
	def __init__(self, func):
		self.func = func
		try:
			self.__name__, self.__doc__ = func.__name__, func.__doc__
		except AttributeError:
			pass
	def __get__(self, obj, cls):
		def meth(*a, **kw):
			actionparams, responsetype, responseparser = self.func(obj, *a, **kw)
			response = obj._foreignrequester(actionparams, responsetype)
			if callable(responseparser):
				response = responseparser(response)
			return response
		try:
			meth.__name__, meth.__doc__ = self.func.__name__, self.func.__doc__
		except AttributeError:
			pass
		return meth


def resolvingFunction(op, *, allowforeigntokens=False):
	"""Return a function that passes its arguments through `api.real()`."""
	def _resolvingfunction(*arguments, **keywords):
		args = [api.real(a) for a in arguments]
		kwargs = {k:api.real(v) for k, v in keywords.items()}
		if not allowforeigntokens:
			# Forbid foreign token strings from being manipulated through this operation.
			for l in (args, kwargs.values()):
				for o in l:
					if api.isForeignToken(o):
						raise TypeError(f"Not allowed to call `{op.__name__}()` function with foreign object token: {o}")
		return op(*args, **kwargs)
	_resolvingfunction.__name__ = op.__name__
	_resolvingfunction.__doc__ = f"{op.__doc__ or name + ' operator.'}\n\nCalls `api.real()` on all arguments."
	return _resolvingfunction

def reversedMethod(func):
	"""Return a `.__rop__` version of an `.__op__` magic method function."""
	def _reversedop(a, b, *args, **kwargs):
		return func(b, a, *args, **kwargs)
	_reversedop.__name__ = func.__name__
	_reversedop.__doc__ = f"{func.__doc__ or name + ' operator.'}\n\nReversed version."
	return _reversedop

def inplaceMethod(func):
	"""Return a wrapped a function that calls ._setvalue_() on its first self argument with its original result."""
	def _inplacemethod(self, *args, **kwargs):
		self._setvalue_(func(self, *args, **kwargs))
		return self
	return _inplacemethod


def dummyForeignRequester(actionparams, responsetype):
	return actionparams, responsetype


def foreignValueParser(packet, *, raise_exceptions=True):
	"""Value parser that reads a foreign request packet fitting a common structure."""
	if 'Exception' in packet.flags and raise_exceptions:
		raise ipc.ForeignError(packet.data)
	return packet.data


def makePathElement(ttype='Property', name='', params=()):
	assert ttype in ('Property', 'Key', 'Call'), f"{repr(ttype)} not a valid path element type."
	return {'type': ttype, 'name': name, 'params': params}

def stringPathList(pathlist):
	items = []
	for p in pathlist:
		if p['type'] == 'Property':
			items.append(f".{p['name']}")
		if p['type'] == 'Key':
			items.append(f"[{json.dumps(p['params'][0], cls=ipc.IpcJsonEncoder)}]")
		if p['type'] == 'Call':
			items.append(f"({', '.join(p['params'])}])")
	return "".join(items)


_magicmeths = (
	'__lt__',
	'__le__',
	'__eq__', # Kinda undefined behaviour for comparison with Kotlin object tokens. Well, tokens are just strings that will always equal themselves, but multiple tokens can refer to the same Kotlin object. `ForeignObject()`s resolve to new tokens, that are currently uniquely generated in InstanceTokenizer.kt, on every `._getvalue_()`, so I think even the same `ForeignObject()` will never equal itself. # Actually, now raises exception when used with tokens, I think.
	'__ne__',
	'__ge__',
	'__gt__',
	'__not__',
	('__bool__', 'truth'),
#	@is # This could get messy. It's probably best to just not support identity comparison. What do you compare? JVM Kotlin value? Resolved Python value? Python data path? Token strings from InstanceTokenizer.kt— Which are currently randomly re-generated for multiple accesses to the same Kotlin object, and thus always unique, and which would require another protocol-level guarantee to not do that, in addition to being (kinda by design) procedurally indistinguishable from "real" resovled Python values?
#	@is_not # Also, these aren't even magic methods.
	'__abs__',
	'__add__',
	'__and__',
	'__floordiv__',
	'__index__',
	'__inv__',
	'__invert__',
	'__lshift__',
	'__mod__',
	'__mul__',
	'__matmul__',
	'__neg__',
	'__or__',
	'__pos__',
	'__pow__',
	'__rshift__',
	'__sub__',
	'__truediv__',
	'__xor__',
	'__concat__',
	'__contains__', # Implemented through foreign request.
	'__delitem__', # Should be implemented through foreign request if it is to be supported.
	'__getitem__', # Implemented through foreign request.
#	@indexOf # Not actually totally sure what this is. I thought it was implemented in lists and tuples as `.index()`?
#	'__setitem__', # Implemented through foreign request.
)

_rmagicmeths = (
	'__radd__',
	'__rsub__',
	'__rmul__',
	'__rmatmul__',
	'__rtruediv__',
	'__rfloordiv__',
	'__rmod__',
	'__rdivmod__',
	'__rpow__',
	'__rlshift__',
	'__rrshift__',
	'__rand__',
	'__rxor__',
	'__ror__',
)

_imagicmethods = (
	'__iadd__',
	'__isub__',
	'__imul__',
	'__imatmul__',
	'__itruediv__',
	'__ifloordiv__',
	'__imod__',
	'__ipow__',
	'__ilshift__',
	'__irshift__',
	'__iand__',
	'__ixor__',
	'__ior__'
)

def resolveForOperators(cls):
	"""Decorator. Adds missing magic methods to a class, which resolve their arguments with `api.real(a)`."""
	def alreadyhas(name):
		return (hasattr(cls, name) and getattr(cls, name) is not getattr(object, name, None))
	for meth in _magicmeths:
		if isinstance(meth, str):
			name = opname = meth
		else:
			name, opname = meth
		if not alreadyhas(name):
			# Set the magic method only if neither it nor any of its base classes have already defined a custom implementation.
			setattr(cls, name, resolvingFunction(getattr(operator, opname), allowforeigntokens=False))
	for rmeth in _rmagicmeths:
		normalname = rmeth.replace('__r', '__', 1)
		if not alreadyhas(rmeth) and hasattr(cls, normalname):
			setattr(cls, rmeth, reversedMethod(getattr(cls, normalname)))
	for imeth in _imagicmethods:
		normalname = imeth.replace('__i', '__', 1)
		if not alreadyhas(imeth) and hasattr(cls, normalname):
			normalfunc = getattr(cls, normalname)
			setattr(cls, imeth, inplaceMethod(normalfunc))
	return cls


# class ForeignToken(str):
	# __slots__ = ()
	# TODO: Could do this for more informative error messages, hidden magic methods that don't make sense.
	#  Would have to instantiate in the JSON decoder, though.
	#  I'm not sure it's necessary, since tokens will still have to be encoded as strings in JSON, which means you'd still need apiconstants['kotlinInstanceTokenPrefix'] and isForeignToken in api.py.


class AttributeProxy:
	def __init__(self, obj):
		object.__setattr__(self, 'obj', obj)
	def __getattribute__(self, name):
		return object.__getattribute__(object.__getattribute__(self, 'obj'), name)
	def __setattr__(self, name, value):
		return object.__setattr__(object.__getattribute__(self, 'obj'), name, value)
	# FIXME: Does this seem like a performance issue?


BIND_BY_REFERENCE = True
"""Early versions of this API bound Python objects to Kotlin/JVM instances by keeping track of paths and lazily evaluating them as needed. E.G. ".a.b[5].c" would create an internal tuple like `("a", "b", [5], "c")`, without actually accessing any Kotlin/JVM values at first. Benefits: Fewer IPC actions, lazy resolution of values only as they're used. Drawbacks: Deeper (slow) reflective loops per IPC action, scripting semantics not perfectly synced with JVM state, ugly tricks needed to deal with values that can't be safely accessed as paths from the same scope root, like the properties and methods of instances returned by function calls.

The current API instead keeps track of every """

# TODO: The more complicated tests are all significantly slower with bind-by-reference than with bind-by-path. Hopefully it will be fixed by plugging the leak in InstanceTokenizer.

# TODO: Maybe test to see if a path-based approach for keys and attributes might still be faster?

@resolveForOperators
class ForeignObject:
	"""Wrapper for a foreign object. Implements the specifications on IPC packet action types and data structures in Module.md."""
	def __init__(self, *, path, use_root=False, root=None, foreignrequester=dummyForeignRequester):
		object.__setattr__(self, '_attrs', AttributeProxy(self))
		self._attrs._isbaked = False
		self._attrs._unbaked = None # For in-place operations, a version should be kept that
		self._attrs._use_root = use_root
		self._attrs._root = root
		self._attrs._path = (makePathElement(name=path),) if isinstance(path, str) else tuple(path)
		self._attrs._foreignrequester = foreignrequester
	def __repr__(self):
		return f"{self.__class__.__name__}({self._root}, {stringPathList(self._getpath_())}):{self._getvalue_()}"
	def _clone_(self, **kwargs):
		return self.__class__(**{'path': self._path, 'use_root': self._use_root, 'root': self._root, 'foreignrequester': self._foreignrequester, **kwargs})
	def _ipcjson_(self):
		return self._getvalue_()
	def _getpath_(self):
		return tuple(self._path)
	def _bakereal_(self):
		assert not self._isbaked
		self._attrs._unbaked = self._clone_() # For in-place operations.
		self._attrs._root = self._getvalue_() # TODO: Would the fallback for inplaces go through __setattr__, and result in one fewer IPC call?
		self._attrs._use_root = True
		self._attrs._path = ()
		self._attrs._isbaked = True
	def __getattr__(self, name, *, do_bake=True):
		# Due to lazy IPC calling, hasattr will never work with this. Instead, check for in dir().
		# TODO: Shouldn't I special-casing get_help or _docstring_ here? Wait, no, I think I thought it would be accessed on the class.
		attr = self._clone_(path=(*self._path, makePathElement(name=name)))
		if BIND_BY_REFERENCE and do_bake:
			attr._bakereal_()
		return attr
	def __getattribute__(self, name, **kwargs):
		if name in ('values', 'keys', 'items'):
			# Don't expose real .keys, .values, or .items unless wrapping a foreign mapping. This prevents foreign attributes like TileMap.values from being blocked.
			if not self._ismapping_():
				raise AttributeError(name)
		return object.__getattribute__(self, name)
	def __getitem__(self, key, *, do_bake=True):
		item = self._clone_(path=(*self._path, makePathElement(ttype='Key', params=(key,))))
		if BIND_BY_REFERENCE and do_bake:
			item._bakereal_()
		return item
		# Indexing from end with negative numbers is not supported.
		# Mostly a complexity choice. Matching Kotlin semantics is better than doing an extra IPC call.
	def __iter__(self):
		try:
			return iter(self.keys())
		except:
			return (self[i] for i in range(0, len(self))) #TODO: Obviously this won't work for sets. Practical example why that's a problem: CityInfo stores HashSet()s of tiles. Workaround: Call real() on the whole set, and use the resulting values or foreign tokens. Unindexability/potential unorderedness of sets means that iteration would have to be handled from the Kotlin side, which means, at minimum implementing the BeginIteration and EndIteration flags, plus an entire new type of PassMic loop. Even then, without indexes, you'd only get the raw value or foreign token anyway
	def __setattr__(self, name, value):
		return self.__getattr__(name, do_bake=False)._setvalue_(value)
	def __setitem__(self, key, value):
		return self.__getitem__(key, do_bake=False)._setvalue_(value)
	def _getvalue_(self):
		if self._isbaked:
			return self._root
		else:
			return self._getvalueraw_()
	def _setvalue_(self, value):
		if self._isbaked:
			return self._unbaked._setvalue_(value)
		else:
			return self._setvalueraw_(value)
	def __call__(self, *args):
		result = self._clone_(path=(*self._getpath_(), makePathElement(ttype='Call', params=args)))
		if BIND_BY_REFERENCE:
			result._bakereal_()
			return result
		else:
			return result._getvalue_()
	@ForeignRequestMethod
	def _getvalueraw_(self):
		# Should never be called except for by _getvalue_.
		return ({
			'action': 'read',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'read_response',
		foreignValueParser)
	@ForeignRequestMethod
	def _setvalueraw_(self, value):
		# Should never be called except for by _setvalue_.
		return ({
			'action': 'assign',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_(),
				'value': value
			}
		},
		'assign_response',
		foreignValueParser)
	@ForeignRequestMethod
	def _ismapping_(self):
		return ({
			'action': 'ismapping',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'ismapping_response',
		foreignValueParser)
	@ForeignRequestMethod
	def _callable_(self, *, raise_exceptions=True):
		return ({
			'action': 'callable',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'callable_response',
		lambda packet: foreignValueParser(packet, raise_exceptions=raise_exceptions))
	@ForeignRequestMethod
	def _args_(self, *, raise_exceptions=True):
		return ({
			'action': 'args',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'args_response',
		lambda packet: foreignValueParser(packet, raise_exceptions=raise_exceptions))
	@ForeignRequestMethod
	def _docstring_(self, *, raise_exceptions=True):
		return ({
			'action': 'docstring',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'docstring_response',
		lambda packet: foreignValueParser(packet, raise_exceptions=raise_exceptions))
	@ForeignRequestMethod
	def __dir__(self):
		return ({
			'action': 'dir',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_()
			}
		},
		'dir_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __delitem__(self, key):
		return ({
			'action': 'delete',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self.__getitem__(key, do_bake=False)._getpath_()
			}
		},
		'delete_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __len__(self):
		return ({
			'action': 'length',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_(),
			}
		},
		'length_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __contains__(self, item):
		return ({
			'action': 'contains',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_(),
				'value': item
			}
		},
		'contains_response',
		foreignValueParser)
	@ForeignRequestMethod
	def keys(self):
		return ({
			'action': 'keys',
			'data': {
				'use_root': self._use_root,
				'root': self._root,
				'path': self._getpath_(),
			}
		},
		'keys_response',
		foreignValueParser)
	def values(self):
		return (self[k] for k in self.keys())
	def items(self):
		return ((k, self[k]) for k in self.keys())



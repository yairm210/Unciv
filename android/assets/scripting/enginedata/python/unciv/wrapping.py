import json, sys, operator
stdout = sys.stdout

from . import ipc, api

class ForeignRequestMethod:
	"""Decorator for methods that return values from foreign requests."""
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
			if responseparser and callable(responseparser):
				response = responseparser(response)
			return response
		try:
			meth.__name__, meth.__doc__ = self.func.__name__, self.func.__doc__
		except AttributeError:
			pass
		return meth

#class ForeignSelfMethod:
#	"""Decorator for methods that use foreign values for `self`."""
#	def __init__(self, func):
#		self.func = func
#	def __get__(self, obj, cls):
#		return lambda *a, **kw: self.func(obj._getvalue(), *a, **kw)

#def ForeignResolvingFunc(func):
#	"""Decorator for functions that resolve foreign objects as arguments."""
#	def _func(*args, **kwargs):
#		return f(
#			*[a._getvalue() if isinstance(ForeignObject) else a for a in args],
#			**{k: v._getvalue() if isinstance(ForeignObject) else v for k, v in kwargs.items()}
#		)
#	try:
#		_func.__name__, _func.__doc__ = func.__name__, func.__doc__
#	except AttributeError:
#		pass
#	return _func

def ResolvingFunction(op):
	"""Return a function that passes its arguments through `api.real()`."""
	def _resolvingfunction(*arguments, **keywords):
		args = [api.real(a) for a in arguments]
		kwargs = {k:api.real(v) for k, v in keywords.items()}
		return op(*args, **kwargs)
	_resolvingfunction.__name__ = op.__name__
	_resolvingfunction.__doc__ = f"{op.__doc__ or name + ' operator.'}\n\nCalls `api.real()` on all arguments."
	return _resolvingfunction

def ReversedMethod(func):
	"""Return a `.__rop__` version of an `.__op__` magic method function."""
	def _reversedop(a, b, *args, **kwargs):
		return func(b, a, *args, **kwargs)
	_reversedop.__name__ = func.__name__
	_reversedop.__doc__ = f"{func.__doc__ or name + ' operator.'}\n\nReversed version."
	return _reversedop


def dummyForeignRequester(actionparams, responsetype):
	return actionparams, responsetype
	
	
def foreignValueParser(packet):
	if packet.data["exception"] is not None:
		raise ipc.ForeignError(packet.data["exception"])
	return packet.data["value"]

def foreignErrmsgChecker(packet):
	if packet.data is not None:
		raise ipc.ForeignError(packet.data)


def makePathElement(ttype='Property', name='', params=()):
	assert ttype in ('Property', 'Key', 'Call'), f"{repr(ttype)} not a valid path element type."
	return {'type': ttype, 'name': name, 'params': params}

def stringPathList(pathlist):
	items = []
	for p in pathlist:
		if p['type'] == 'Property':
			items.append(f".{p['name']}")
		if p['type'] == 'Key':
			items.append(f"[{p['params'][0]}]")
		if p['type'] == 'Call':
			items.append(f"({', '.join(p['params'])}])")
	return "".join(items)


_magicmeths = (
	'__lt__',
	'__le__',
	'__eq__', # Kinda undefined behaviour for comparision with Kotlin object tokens. Well, tokens are just strings that will always equal themselves, but multiple tokens can refer to the same Kotlin object. `ForeignObject()`s resolve to new tokens, that are currently uniquely generated in ScriptingObjectIndex.kt, on every `._getvalue()`, so I think even the same `ForeignObject()` will never equal itself.
	'__ne__',
	'__ge__',
	'__gt__',
	'__not__',
	('__bool__', 'truth'),
#	@is # This could get messy. It's probably best to just not support identity comparison. What do you compare? JVM Kotlin value? Resolved Python value? Python data path? Token strings from ScriptingObjectIndex.kt— Which are currently randomly re-generated for multiple accesses to the same Kotlin object, and thus always unique, and which would require another protocol-level guarantee to not do that, in addition to being (kinda by design) procedurally indistinguishable from "real" resovled Python values?
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

def ResolveForOperators(cls):
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
			setattr(cls, name, ResolvingFunction(getattr(operator, opname)))
	for rmeth in _rmagicmeths:
		normalname = rmeth.replace('__r', '__', 1)
		if not alreadyhas(rmeth) and hasattr(cls, normalname):
			setattr(cls, rmeth, ReversedMethod(getattr(cls, normalname)))
	return cls


@ResolveForOperators
class ForeignObject:
	"""Wrapper for a forign object."""
	def __init__(self, path, foreignrequester=dummyForeignRequester):
		object.__setattr__(self, '_path', (makePathElement(name=path),) if isinstance(path, str) else tuple(path))
		object.__setattr__(self, '_foreignrequester', foreignrequester)
	def __repr__(self):
		return f"{self.__class__.__name__}({stringPathList(self._getpath())}):{self._getvalue()}"
	def __ipcjson__(self):
		return self._getvalue()
	def _getpath(self):
		return tuple(self._path)
		#return ''.join(self._path)
	def __getattr__(self, name):
		return self.__class__((*self._path, makePathElement(name=name)), self._foreignrequester)
	def __getitem__(self, key):
		return self.__class__((*self._path, makePathElement(ttype='Key', params=(key,))), self._foreignrequester)
#	def __hash__(self):
#		return hash(stringPathList(self._getpath()))
	def __iter__(self):
		try:
			return iter(self.keys())
		except:
			return (self[i] for i in range(0, len(self)))
	@ForeignRequestMethod
	def _getvalue(self):
		return ({
			'action': 'read',
			'data': {
				'path': self._getpath()
			}
		},
		'read_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __dir__(self):
		return ({
			'action': 'dir',
			'data': {
				'path': self._getpath()
			}
		},
		'dir_response',
		foreignValueParser)
#	@ForeignRequestMethod
#	@property
#	def __doc__(self):
#		return ({
#			'action': 'args',
#			'data': {
#				'path': self._getpath()
#			}
#		},
#		'args_response',
#		None)
	@ForeignRequestMethod
	def __setattr__(self, name, value):
		return ({
			'action': 'assign',
			'data': {
				'path': getattr(self, name)._getpath(),
				'value': value
			}
		},
		'assign_response',
		foreignErrmsgChecker)
	@ForeignRequestMethod
	def __call__(self, *args):
		return ({
			'action': 'read',
			'data': {
				'path': (*self._getpath(), makePathElement(ttype='Call', params=args)),
			}
		},
		'read_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __setitem__(self, key, value):
		return ({
			'action': 'assign',
			'data': {
				'path': self[key]._getpath(),
				'value': value
			}
		},
		'assign_response',
		foreignErrmsgChecker)
	@ForeignRequestMethod
	def __len__(self):
		return ({
			'action': 'length',
			'data': {
				'path': self._getpath(),
			}
		},
		'length_response',
		foreignValueParser)	
	@ForeignRequestMethod
	def __contains__(self, item):
		return ({
			'action': 'contains',
			'data': {
				'path': self._getpath(),
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
				'path': self._getpath(),
			}
		},
		'keys_response',
		foreignValueParser)	
	def values(self):
		return (self[k] for k in self.keys())
	def entries(self):
		return ((k, self[k]) for k in self.keys())

#class ForeignScope:
#	_path = ()
#	def __init__(self, attrcls=ForeignObject, foreignrequester=dummyForeignRequester):
#		self._attrcls = attrcls
#		self._foreignrequester = foreignrequester
#	def __getattr__(self, name):
#		return self._attrcls(name, self._foreignrequester)
#	__dir__ = ForeignObject.__dir__


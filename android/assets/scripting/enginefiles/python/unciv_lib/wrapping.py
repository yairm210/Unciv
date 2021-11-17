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


def ResolvingFunction(op, *, allowforeigntokens=False):
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

def ReversedMethod(func):
	"""Return a `.__rop__` version of an `.__op__` magic method function."""
	def _reversedop(a, b, *args, **kwargs):
		return func(b, a, *args, **kwargs)
	_reversedop.__name__ = func.__name__
	_reversedop.__doc__ = f"{func.__doc__ or name + ' operator.'}\n\nReversed version."
	return _reversedop

def InplaceMethod(func):
	"""Return a wrapped a function that calls ._setvalue_() on its first self argument with its original result."""
	def _inplacemethod(self, *args, **kwargs):
		self._setvalue_(func(self, *args, **kwargs))
		return self
	return _inplacemethod


def dummyForeignRequester(actionparams, responsetype):
	return actionparams, responsetype


def foreignValueParser(packet, *, raise_exceptions=True):
	"""Value parser that reads a foreign request packet fitting a common structure."""
	if packet.data["exception"] is not None and raise_exceptions:
		raise ipc.ForeignError(packet.data["exception"])
	return packet.data["value"]

def foreignErrmsgChecker(packet):
	"""Value parser that processes a foreign request packet fitting a simple structure."""
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
	'__eq__', # Kinda undefined behaviour for comparison with Kotlin object tokens. Well, tokens are just strings that will always equal themselves, but multiple tokens can refer to the same Kotlin object. `ForeignObject()`s resolve to new tokens, that are currently uniquely generated in InstanceTokenizer.kt, on every `._getvalue_()`, so I think even the same `ForeignObject()` will never equal itself.
	'__ne__',
	'__ge__',
	'__gt__',
	'__not__',
	('__bool__', 'truth'),#TODO: Allow foreign tokens for this, and other unary operators. # Wait, no. That wouldn't make any sense.
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
			setattr(cls, name, ResolvingFunction(getattr(operator, opname), allowforeigntokens=False))
	for rmeth in _rmagicmeths:
		normalname = rmeth.replace('__r', '__', 1)
		if not alreadyhas(rmeth) and hasattr(cls, normalname):
			setattr(cls, rmeth, ReversedMethod(getattr(cls, normalname)))
	for imeth in _imagicmethods:
		normalname = imeth.replace('__i', '__', 1)
		if not alreadyhas(imeth) and hasattr(cls, normalname):
			normalfunc = getattr(cls, normalname)
			setattr(cls, imeth, InplaceMethod(normalfunc))
	return cls


@ResolveForOperators
class ForeignObject:
	"""Wrapper for a foreign object Implements ScriptingProtocol."""
	def __init__(self, path, foreignrequester=dummyForeignRequester):
		object.__setattr__(self, '_path', (makePathElement(name=path),) if isinstance(path, str) else tuple(path))
		object.__setattr__(self, '_foreignrequester', foreignrequester)
	def __repr__(self):
		return f"{self.__class__.__name__}({stringPathList(self._getpath_())}):{self._getvalue_()}"
	def _ipcjson_(self):
		return self._getvalue_()
	def _getpath_(self):
		return tuple(self._path)
		#return ''.join(self._path)
	def __getattr__(self, name):
		# TODO: Shouldn't I special-casing get_doc or _docstring_ here? Wait, no, I think I thought it would be accessed on the class.
		return self.__class__((*self._path, makePathElement(name=name)), self._foreignrequester)
	def __getitem__(self, key):
		return self.__class__((*self._path, makePathElement(ttype='Key', params=(key,))), self._foreignrequester) #TODO: Should negative indexing from end be supported?
#	def __hash__(self):
#		return hash(stringPathList(self._getpath_()))
	def __iter__(self):
		try:
			return iter(self.keys())
		except:
			return (self[i] for i in range(0, len(self)))
	def __setattr__(self, name, value):
		return getattr(self, name)._setvalue_(value)
	def __setitem__(self, key, value):
		return self[key]._setvalue_(value)
	@ForeignRequestMethod
	def _getvalue_(self):
		return ({
			'action': 'read',
			'data': {
				'path': self._getpath_()
			}
		},
		'read_response',
		foreignValueParser)
	@ForeignRequestMethod
	def _setvalue_(self, value):
		return ({
			'action': 'assign',
			'data': {
				'path': self._getpath_(),
				'value': value
			}
		},
		'assign_response',
		foreignErrmsgChecker)
	@ForeignRequestMethod
	def _callable_(self, *, raise_exceptions=True):
		return ({
			'action': 'callable',
			'data': {
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
				'path': self._getpath_()
			}
		},
		'dir_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __call__(self, *args):
		return ({
			'action': 'read',
			'data': {
				'path': (*self._getpath_(), makePathElement(ttype='Call', params=args)),
			}
		},
		'read_response',
		foreignValueParser)
	@ForeignRequestMethod
	def __delitem__(self, key):
		return ({
			'action': 'delete',
			'data': {
				'path': self[key]._getpath_()
			}
		},
		'delete_response',
		foreignErrmsgChecker)
	@ForeignRequestMethod
	def __len__(self):
		return ({
			'action': 'length',
			'data': {
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
				'path': self._getpath_(),
			}
		},
		'keys_response',
		foreignValueParser)
	def values(self):
		return (self[k] for k in self.keys())
	def entries(self):
		return ((k, self[k]) for k in self.keys())



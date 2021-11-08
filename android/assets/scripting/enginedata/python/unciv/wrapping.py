import json, sys
stdout = sys.stdout

from . import ipc

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

def ForeignResolvingFunc(func):
	"""Decorator for functions that resolve foreign objects as arguments."""
	def _func(*args, **kwargs):
		return f(
			*[a._getvalue() if isinstance(ForeignObject) else a for a in args],
			**{k: v._getvalue() if isinstance(ForeignObject) else v for k, v in kwargs.items()}
		)
	try:
		_func.__name__, _func.__doc__ = func.__name__, func.__doc__
	except AttributeError:
		pass
	return _func


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


class ForeignObject:
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


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

def stdForeignRequester(actionparams, responsetype):
	request = ipc.ForeignPacket(**actionparams)
	requestjson = request.serialized()
	try:
		print(requestjson, file=stdout, flush=True)
	except TypeError:
		print(requestjson, file=stdout)
	response = ipc.ForeignPacket.deserialized(sys.stdin.readline())
	assert request.identifier == response.identifier, f"Mismatched identifier: {request.identifier} != {response.identifier}"
	assert response.action == responsetype, f"Mismatched response type: {response.action} != {responsetype}"
	return response
	
	

class ForeignObject:
	def __init__(self, path, foreignrequester=dummyForeignRequester):
		object.__setattr__(self, '_path', (*path,))
		object.__setattr__(self, '_foreignrequester', foreignrequester)
	def __repr__(self):
		return f"{self.__class__.__name__}({repr(self._getpath())}):{self._getvalue()}"
	def _getpath(self):
		return ''.join(self._path)
	def __getattr__(self, name):
		self.__class__
		self._path
		return self.__class__((*self._path, f".{name}"))
	def __getitem__(self, key):
		return self.__class__((*self._path, f"[{json.dumps(key)}]"))
	@ForeignRequestMethod
	def _getvalue(self):
		return ({
			'action': 'read',
			'data': {
				'path': self._getpath()
			}
		},
		'read_response',
		None)
	@ForeignRequestMethod
	def __dir__(self):
		return ({
			'action': 'dir',
			'data': {
				'path': self._getpath()
			}
		},
		'dir_response',
		None)
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
		None)
	@ForeignRequestMethod
	def __call__(self, *args, **kwargs):
		return ({
			'action': 'call',
			'data': {
				'args': args,
				'kwargs': kwargs
			}
		},
		'call_response',
		None)
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
		None)
	def keys(self):
		raise NotImplemented()
		return {
			''
		}

#class ForeignScope:
#	_path = ()
#	def __init__(self, attrcls=ForeignObject, foreignrequester=dummyForeignRequester):
#		self._attrcls = attrcls
#		self._foreignrequester = foreignrequester
#	def __getattr__(self, name):
#		return self._attrcls(name, self._foreignrequester)
#	__dir__ = ForeignObject.__dir__


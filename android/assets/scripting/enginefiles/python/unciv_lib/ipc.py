import sys, io, json, time, random

stdout = sys.stdout

#def ResolvePath(path, scope):
##	return eval(path, scope, scope)
#	raise NotImplementedError()

class IpcJsonEncoder(json.JSONEncoder):
	"""JSONEncoder that lets classes define a special ._ipcjson_() method to control how they'll be serialized. Used by ForeignObject to send its resolved value."""
	def default(self, obj):
		if hasattr(obj.__class__, '_ipcjson_'):
			return obj._ipcjson_()
		return json.JSONEncoder.default(self, obj)


def MakeUniqueId():
	"""Return a string that should never repeat or collide. Used for IPC packet identity fields."""
	return f"{time.time_ns()}-{random.getrandbits(30)}"

class ForeignError(RuntimeError):
	pass

class ForeignPacket:
	"""Class for IPC packet conforming to specification in Module.md and ScriptingProtocol.kt."""
	def __init__(self, action, identifier, data, flags=()):
		self.action = action
		self.identifier = identifier
		self.data = data
		self.flags = flags
	def __repr__(self):
		return self.__class__.__name__+"(**"+str(self.as_dict())+")"
	@classmethod
	def deserialized(cls, serialized):
		"""Return a packet object from a JSON string."""
		return cls(**json.loads(serialized))
	def enforce_type(self, expect_action=None, expect_identifier=None):
		if expect_action is not None and self.action != expect_action:
			raise ForeignError("Expected foreign data of action "+repr(expect_action)+", got "+repr(self)+".")
		if expect_identifier is not None and self.identifier != expect_identifier:
			raise ForeignError("Expected foreign data with identifier "+repr(expect_identifier)+", got "+repr(self)+".")
		return self
	def as_dict(self):
		return {
			'action': self.action,
			'identifier': self.identifier,
			'data': self.data,
			'flags': (*self.flags,)
		}
	def serialized(self):
		return json.dumps(self.as_dict(), cls=IpcJsonEncoder)


class ForeignActionManager:
	def __init__(self, sender=None, receiver=None):
		if sender is not None:
			self.sender = sender
		if receiver is not None:
			self.receiver = receiver
	def sender(self, message):
		try:
			print(message, file=stdout, flush=True)
		except TypeError:
			#No flush on `micropython`.
			print(message, file=stdout)
	def receiver(self):
		return sys.stdin.readline()


class ForeignActionSender(ForeignActionManager):
	def SendForeignAction(self, actionparams):
		self.sender(ForeignPacket(**actionparams).serialized())
	def GetForeignActionResponse(self, actionparams, responsetype):
		identifier = MakeUniqueId()
		self.SendForeignAction({**actionparams, 'identifier': identifier})
		return ForeignPacket.deserialized(self.receiver()).enforce_type(responsetype, identifier)


def receiverMethod(action, response):
	def receiverMethodDec(func):
		func.__foreignActionReceiver = (action, response)
		#Won't work on Upy, I think.
		return func
	return receiverMethodDec

class ForeignActionReceiver(ForeignActionManager):
	def __init__(self, sender=None, receiver=None, scope=None):
		ForeignActionManager.__init__(self, sender=sender, receiver=receiver)
		self.scope = globals() if scope is None else scope
		self._responders = {}
		for name in dir(self):
			value = getattr(self, name)
			action, response = getattr(value, '__foreignActionReceiver', (None, None))
			if action:
				self._responders[action] = (value, response)
	def RespondForeignAction(self, request):
		decoded = ForeignPacket.deserialized(request)
		action = decoded.action
		raction = None
		rdata = None
		if action in self._responders:
			method, raction = self._responders[action]
			rdata = method(decoded)
		else:
			raise ForeignError("Unknown action type for foreign action request: " + repr(decoded))
		self.sender(ForeignPacket(raction, decoded.identifier, rdata).serialized())
	def AwaitForeignAction(self):#, *, ignoreempty=True):
		self.RespondForeignAction(self.receiver())
#		while True:
#			line = self.receiver()
#			if line or not ignoreempty:
#				self.RespondForeignAction(line)
#				break
	def ForeignREPL(self):
		while True:
			self.AwaitForeignAction()


class FakeStdout:
	"""Context manager that returns a StringIO and sets sys.stdout to it on entrance, then restores it to its original value on exit."""
	def __init__(self):
		self.stdout = sys.stdout
	def __enter__(self):
		self.fakeout = sys.stdout = io.StringIO() # Won't work with MicroPython. I think it's slotted?
		return self.fakeout
	def __exit__(self, *exc):
		sys.stdout = self.stdout

#class ForeignActionBindingSender(ForeignActionSender):
#	#Probably easier to just define these as needed in the classes that call them.
#	def SendForeignCall(self, path, args, kwargs):
#		return self.GetForeignActionResponse({'action': ('call', 'data': {'path':path, 'args':args, 'kwargs':kwargs}}, 'call_response')
#	def SendForeignRead(self, path):
#		identifier = self.MakeUniqueID()
#		return self.GetForeignActionResponse({'action': 'read', 'data': {'path':path}}, 'read_response')
#	def SendForeignAssign(self, path, value):
#		return self.GetForeignActionResponse({'action': 'assign', 'data': {'path':path, 'value':value}})
#	def MakeForeignFunction(self, callpath, callargs):
#		pass

#class ForeignActionBindingReceiver(ForeignActionReceiver):
#	# This is nice to have, but basically useless, right? In the current model, there shouldn't be any circumstances where the Kotlin code explicitly changes or is even aware of the state of the script interpreter, since the Kotlin side has to deal with any number of languages, all the data lives on the Kotlin side and it's thus the script interpreter's job to request what it needs, and all communication is through standardized requests for either Kotlin-side reflection or a handful of REPL functions and raw code eval on the scripting side
#	def foreignCallEvaluator(func):
#		def _foreignCallEvaluator(self, *args, **kwargs):
#			try:
#				pass
#			except:
#				pass
#	def ResolvePath(self, path):
#		return eval(path, self.scope, self.scope)
#		# NOTE: This should be replaced with recursive parsing into `getattr()` and `[]`/`.__getitem__`/`.__getindex__` if arbitrary code has the chance to be passed.
#	def EvalForeignCall(self, packet):
#		packet.enforce_type('call')
#		return self.ResolvePath(packet.data['path'])(*packet.data['args'], **packet.data['kwargs'])
#	def EvalForeignRead(self, packet):
#		packet.enforce_type('read')
#		return self.ResolvePath(packet.data['path'])
#	def ExecForeignAssign(self, packet):
#		packet.enforce_type('assign')
#		path = packet.data['path']
#		value = packet.data['value']
#		if path[-1] == ']':
#			spath = path.rpartition('[')
#			self.ResolvePath(spath[0])[spath[-1][:-1]] = value
#		else:
#			spath = path.rpartition('.')
#			if spath[0]:
#				setattr(self.ResolvePath(spath[0]), spath[-1], value)
#			else:
#				self.scope[spath[-1]] = value




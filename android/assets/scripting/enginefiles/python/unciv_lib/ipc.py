import sys, io, json, time, random

stdout = sys.stdout

class IpcJsonEncoder(json.JSONEncoder):
	"""JSONEncoder that lets classes define a special ._ipcjson_() method to control how they'll be serialized. Used by ForeignObject to send its resolved value."""
	def default(self, obj):
		if hasattr(obj.__class__, '_ipcjson_'):
			return obj._ipcjson_()
		return json.JSONEncoder.default(self, obj)


def makeUniqueId():
	"""Return a string that should never repeat or collide. Used for IPC packet identity fields."""
	return f"{time.time_ns()}-{random.getrandbits(30)}"

class ForeignError(RuntimeError):
	pass

class ForeignPacket:
	"""Class for IPC packet conforming to specification in Module.md and ScriptingProtocol.kt."""
	# TODO: Speed? Well, I'll cProfile the whole thing eventaully I guess.
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
		identifier = makeUniqueId()
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
	def ForeignREPL(self):
		while True:
			self.AwaitForeignAction()


class FakeStdout:
	"""Context manager that returns a StringIO and sets sys.stdout to it on entrance, then restores sys.stdout to its original value on exit."""
	def __init__(self):
		self.stdout = sys.stdout
	def __enter__(self):
		self.fakeout = sys.stdout = io.StringIO() # Won't work with MicroPython. I think it's slotted?
		return self.fakeout
	def __exit__(self, *exc):
		sys.stdout = self.stdout

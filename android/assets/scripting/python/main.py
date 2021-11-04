import sys, io, json


def ResolvePath(path, scope):
	return eval(path, scope, scope)
	raise NotImplementedError()


class AutoCompleteManager:
	def __init__(self, scope=None):
		self.scope = globals() if scope is None else scope
	def Evaled(self, path):
		return eval(path, self.scope, self.scope)
	def GetAutocomplete(self, command):
		"""Return either a sequence of full autocomplete matches or a help string for a given command."""
		try:
			if ')' in command:
				return ()
			attrbase, attrjoin, attrleaf = command.rpartition('.')
			if '(' in  attrleaf:
				functionbase, functionjoin, functionleaf = attrleaf.rpartition('(')
				fbase = attrbase + attrjoin + functionbase
				fobj = self.Evaled(fbase)
				return '\n'.join((fbase+"(", str(type(fobj)), getattr(fobj, '__doc__', '') or "No documentation available."))
			elif '[' in attrleaf:
				keybase, keyjoin, keyleaf = attrleaf.rpartition('[')
				kbase = attrbase + attrjoin + keybase
				kbaseobj = self.Evaled(kbase)
				if hasattr(kbaseobj, 'keys'):
					if not keyleaf:
						return tuple(kbase+keyjoin+repr(k)+']' for k in kbaseobj.keys())
					if keyleaf[-1] == ']':
						return ()
					quote = keyleaf[0]
					kleaf = keyleaf[1:]
					if quote in '\'"':
						return tuple(kbase+keyjoin+quote+k+quote+']' for k in kbaseobj.keys() if k.startswith(kleaf))
				return tuple(kbase+keyjoin+n+']' for n in self.GetAutocomplete(keyleaf))
			else:
				return tuple([attrbase+attrjoin+a for a in (dir(self.Evaled(attrbase)) if attrbase else self.scope) if a.startswith(attrleaf)])
		except (NameError, AttributeError, KeyError, IndexError, SyntaxError) as e:
			return "No autocompletion found: "+repr(e)
		
		


class ForeignError(RuntimeError):
	pass

class ForeignPacket:
	def __init__(self, action, identifier, data):
		self.action = action
		self.identifier = identifier
		self.data = data
	def __repr__(self):
		return self.__class__.__name__+"(**"+str(self.as_dict())+")"
	@classmethod
	def deserialized(cls, serialized):
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
			'data': self.data
		}
	def serialized(self):
		return json.dumps(self.as_dict())


class ForeignActionManager:
	def __init__(self, sender=None, receiver=None):
		if sender is not None:
			self.sender = sender
		if receiver is not None:
			self.receiver = receiver
	def sender(self, message):
		try:
			print(message, file=sys.stdout, flush=True)
		except TypeError:
			#No flush on `micropython`.
			print(message, file=sys.stdout)
	def receiver(self):
		return sys.stdin.readline()
#	def EncodeForeignAction(self, action, identifier=None, **data):
#		return ForeignPacket(
#			action = action,
#			identifier = identifier,
#			data = data
#		).serialized()

class ForeignActionSender(ForeignActionManager):
	def MakeUniqueID(self):
		return 4 # Chosen by fair dice roll. Guaranteed to be random.
	def SendForeignCall(self, path, args, kwargs):
		identifier = self.MakeUniqueID()
		self.sender(ForeignPacket('call', identifier, {'path':path, 'args':args, 'kwargs':kwargs}).serialized())
		return ForeignPacket.deserialized(self.receiver()).enforce_type('call_response', identifier).data
	def SendForeignRead(self, path):
		identifier = self.MakeUniqueID()
		self.sender(ForeignPacket('read', identifier, {'path':path}).serialized())
		return ForeignPacket.deserialized(self.receiver()).enforce_type('read_response', identifier).data
	def SendForeignAssign(self, path, value):
		identifier = self.MakeUniqueID()
		self.sender(ForeignPacket('assign', identifier, {'path':path, 'value':value}).serialized())
		return ForeignPacket.deserialized(self.receiver()).enforce_type('assign_response', identifier).data
	def MakeForeignFunction(self, callpath, callargs):
		pass

class ForeignActionReceiver(ForeignActionManager):
	def __init__(self, sender=None, receiver=None, scope=None):
		ForeignActionManager.__init__(self, sender=sender, receiver=receiver)
		self.scope = globals() if scope is None else scope
	def foreignCallEvaluator(func):
		def _foreignCallEvaluator(self, *args, **kwargs):
			try:
				pass
			except:
				pass
	def ResolvePath(self, path):
		return eval(path, self.scope, self.scope)
		# NOTE: This should be replaced with recursive parsing into `getattr()` and `[]`/`.__getitem__`/`.__getindex__` if arbitrary code has the chance to be passed.
	def EvalForeignCall(self, packet):
		packet.enforce_type('call')
		return self.ResolvePath(packet.data['path'])(*packet.data['args'], **packet.data['kwargs'])
	def EvalForeignRead(self, packet):
		packet.enforce_type('read')
		return self.ResolvePath(packet.data['path'])
	def ExecForeignAssign(self, packet):
		packet.enforce_type('assign')
		path = packet.data['path']
		value = packet.data['value']
		if path[-1] == ']':
			spath = path.rpartition('[')
			self.ResolvePath(spath[0])[spath[-1][:-1]] = value
		else:
			spath = path.rpartition('.')
			if spath[0]:
				setattr(self.ResolvePath(spath[0]), spath[-1], value)
			else:
				self.scope[spath[-1]] = value
	def RespondForeignAction(self, request):
		decoded = ForeignPacket.deserialized(request)
		action = decoded.action
		raction = None
		rdata = None
		if action == 'call':
			rdata = self.EvalForeignCall(decoded)
			raction = 'call_response'
		elif action == 'read':
			rdata = self.EvalForeignRead(decoded)
			raction = 'read_response'
		elif action == 'assign':
			rdata = self.ExecForeignAssign(decoded)
			raction = 'assign_response'
		else:
			raise ForeignError("Unknown action type for foreign action request: " + repr(decoded))
		self.sender(ForeignPacket(raction, decoded.identifier, rdata).serialized())
	def AwaitForeignAction(self):
		self.RespondForeignAction(self.receiver())
	def ForeignREPL(self):
		while True:
			self.AwaitForeignAction()


class ForeignActionAutocompleter(ForeignActionManager):
	pass


class ForeignActionTransceiver(ForeignActionReceiver, ForeignActionSender):
	pass


#python | micropython -c "import sys; [print('FMF: '+sys.stdin.readline()) for i in range(10)]"
#python3 -ic 'from CommTest import *; t=ForeignActionTransceiver()' | micropython -i -c 'from CommTest import *; t=ForeignActionTransceiver(); t.ForeignREPL()'
#t.SendForeignRead("ForeignActionTransceiver.__name__")


print('sys.implementation == ' + str(sys.implementation))
stdout = sys.stdout
while True:
	line = sys.stdin.readline()
	out = sys.stdout = io.StringIO() # Won't work with MicroPython. I think it's slotted?
	print(">>> " + line)
	try:
		try:
			code = compile(line, 'STDIN', 'eval')
		except SyntaxError:
			exec(compile(line, 'STDIN', 'exec'))
		else:
			print(eval(code))
	except Exception as e:
		print(repr(e), file=stdout)
	else:
		print(out.getvalue(), file=stdout)

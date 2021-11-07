

try:
	import sys, json

	stdout = sys.stdout
	
	import unciv
	
	
	foreignActionSender = unciv.ipc.ForeignActionSender()
	
	foreignScope = {n: unciv.wrapping.ForeignObject(n, foreignrequester=foreignActionSender.GetForeignActionResponse) for n in ('civInfo', 'gameInfo', 'uncivGame', 'worldScreen', 'isInGame')}
	
	foreignAutocompleter = unciv.autocompletion.AutocompleteManager(foreignScope)
	
	class ForeignActionReplReceiver(unciv.ipc.ForeignActionReceiver):
		@unciv.ipc.receiverMethod('motd', 'motd_response')
		def EvalForeignMotd(self, packet):
			return f"""

Welcome to the CPython Unciv CLI. Currently, this backend relies on launching the system `python3` command.

sys.implementation == {str(sys.implementation)}

"""
		@unciv.ipc.receiverMethod('autocomplete', 'autocomplete_response')
		def EvalForeignAutocomplete(self, packet):
			assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
			res = foreignAutocompleter.GetAutocomplete(packet.data["command"])
			foreignActionSender.SendForeignAction({'action':None, 'identifier': None, 'data':None, 'flags':('PassMic',)})
			return res
		@unciv.ipc.receiverMethod('exec', 'exec_response')
		def EvalForeignExec(self, packet):
			line = packet.data
			assert 'PassMic' in packet.flags, f"Expected 'PassMic' in packet flags: {packet}"
			with unciv.ipc.FakeStdout() as fakeout:
				print(f">>> {str(line)}")
				try:
					try:
						code = compile(line, 'STDIN', 'eval')
					except SyntaxError:
						exec(compile(line, 'STDIN', 'exec'), self.scope, self.scope)
					else:
						print(eval(code, self.scope, self.scope))
				except Exception as e:
					return unciv.utils.formatException(e)
				else:
					return fakeout.getvalue()
				finally:
					foreignActionSender.SendForeignAction({'action':None, 'identifier': None, 'data':None, 'flags':('PassMic',)})
				
		@unciv.ipc.receiverMethod('terminate', 'terminate_response')
		def EvalForeignTerminate(self, packet):
			return None

	foreignActionReceiver = ForeignActionReplReceiver(scope=foreignScope)
	foreignActionReceiver.ForeignREPL()
	
except Exception as e:
	print(f"Fatal error in Python interepreter: {unciv.utils.formatException(e)}", file=stdout, flush=True)

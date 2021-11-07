

try:
	import sys, json

	stdout = sys.stdout
	
	import unciv
	
	foreignActionSender = unciv.ipc.ForeignActionSender()
	
	foreignScope = unciv.wrapping.ForeignObject('')
	
	class ForeignActionReplReceiver(unciv.ipc.ForeignActionReceiver):
		@unciv.ipc.receiverMethod('motd', 'motd_response')
		def EvalForeignMotd(self, packet):
			return f"""

Welcome to the CPython Unciv CLI. Currently, this backend relies on launching the system `python3` command.

sys.implementation == {str(sys.implementation)}

"""
		@unciv.ipc.receiverMethod('autocomplete', 'autocomplete_response')
		def EvalForeignAutocomplete(self, packet):
			return "AC."
		@unciv.ipc.receiverMethod('exec', 'exec_response')
		def EvalForeignExec(self, packet):
			line = packet.data
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
					return repr(e)
				else:
					return fakeout.getvalue()
				
		@unciv.ipc.receiverMethod('terminate', 'terminate_response')
		def EvalForeignTerminate(self, packet):
			return None

	foreignActionReceiver = ForeignActionReplReceiver(scope=globals())
	foreignActionReceiver.ForeignREPL()
	
except Exception as e:
	print(f"Fatal error in Python interepreter: {repr(e)}", file=stdout, flush=True)

import atexit

import unciv


RegistryKey = "package:unciv/unciv_scripting_examples.py"

unciv.apiHelpers.registeredInstances[RegistryKey] = {}


class TokensAsWrappers:
	pass


@atexit.register
def on_exit():
	# Don't think this actually works.
	del unciv.apiHelpers.instanceRegistry[RegistryKey]

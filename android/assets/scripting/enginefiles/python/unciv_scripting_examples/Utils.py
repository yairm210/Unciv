import atexit

import unciv


RegistryKey = "package:unciv/unciv_scripting_examples.py"

unciv.apiHelpers.instanceRegistry[RegistryKey] = {}


class TokensAsWrappers:
	pass


@atexit.register
def on_exit():
	del unciv.apiHelpers.instanceRegistry[RegistryKey]

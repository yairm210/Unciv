import rlcompleter

from . import utils


class AutocompleteManager:
	"""Advanced autocompleter. Returns keys when accessing mappings. Implements API that returns docstrings as help text for callables."""
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
			return "No autocompletion found: "+utils.formatException(e)

class RlAutocompleteManager:
	"""Wrapper for default Python autocompleter."""

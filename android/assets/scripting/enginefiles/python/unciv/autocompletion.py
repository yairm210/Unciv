import rlcompleter, itertools, keyword

from . import utils


class AutocompleteManager:
	def __init__(
		self,
		scope=None,
		*,
		get_keys=lambda o: o.keys() if hasattr(o, 'keys') else (),
		get_doc=lambda o: getattr(o, '__doc__', None),
		check_callable=lambda o: callable(o)
	):
		self.scope = globals() if scope is None else scope
		self.get_keys, self.get_doc, self.check_callable = get_keys, get_doc, check_callable
	def GetCommandComponents(self, command):
		"""Try to return the the last atomic evaluable expression in a statement, everything before it, and the token at the end of everything before it."""
		#Call recursively if you need to resolve multiple values. Test string:
		# abc.cde().fgh[0].ijk(lmn[1].opq["dea
		lasttoken = None
		prefixsplit = len(command)-1
		while prefixsplit >= 0:
			char = command[prefixsplit]
			if char in ')':
				#Don't mess with potential function calls.
				prefixsplit = 0
				lasttoken = char
				break
			if char in ']':
				_bdepth = 1
				prefixsplit -= 1
				while _bdepth and prefixsplit:
					# Skip over whole  blocks of open brackets.
					char = command[prefixsplit]
					if char == '[':
						_bdepth -= 1
					if char == ']':
						_bdepth += 1
					prefixsplit -= 1
				char = None
				continue
			if char in '([:,;+-*/|&<>=%{~^@':
				prefixsplit += 1
				lasttoken = char
				break
			prefixsplit -= 1
		else:
			prefixsplit = 0
			# I think this will happen anyway without the break, but do it explicitly.
		prefix, workingcode = command[:prefixsplit], command[prefixsplit:]
		assert (not (lasttoken or prefix)) or lasttoken in ')' or prefix[-1] == lasttoken, f"{prefix, workingcode, lasttoken}"
		return prefix, workingcode, lasttoken
	def GetAutocomplete(self, command):
		"""Return either a sequence of full autocomplete matches or a help string for a given command."""


class PyAutocompleteManager(AutocompleteManager):
	"""Advanced autocompleter. Returns keys when accessing mappings. Implements API that returns docstrings as help text for callables."""
	def Evaled(self, path):
		return eval(path, self.scope, self.scope)
		#Seems safe. Well, I'm checking before calling here that there's no closing brackets that could mean a function call. Let's check again, I guess.
		assert ')' not in path, f"Closing brackets not currently allowed in autocomplete eval: {path}"
	def GetAutocomplete(self, command, cursorpos=None):
		try:
			if cursorpos is None:
				cursorpos = len(command)
			(prefix, workingcode, lasttoken), suffix = self.GetCommandComponents(command[:cursorpos]), command[cursorpos:]
			if ')' in workingcode:
				# Avoid function calls.
				return ()
			if lasttoken in {*'[('}:# Compare to set because None can't be used in string containment check.
				prefix_prefix, prefix_workingcode, prefix_lasttoken = self.GetCommandComponents(prefix[:-1])
				assert prefix[-1] == lasttoken
				if ')' not in prefix_workingcode:
					# Avoid function calls.
					if lasttoken == '[' and ((not workingcode) or workingcode[0] in '\'"'):
#						return f"Return keys matching {workingcode} in {prefix_workingcode}."
						key_obj = self.Evaled(prefix_workingcode)
						if hasattr(key_obj, 'keys'):
							if not workingcode:
								return tuple(prefix+repr(k)+']' + suffix for k in self.get_keys(key_obj))
							quote = workingcode[0]
							key_current = workingcode[1:]
							return tuple(prefix + quote + k + quote + ']' + suffix for k in self.get_keys(key_obj) if k.startswith(key_current))
						return ()
					if lasttoken == '(' and (not workingcode):
#						return f"Show docstring of {prefix_workingcode}."
						func_obj = self.Evaled(prefix_workingcode)
						return (self.get_doc(func_obj) or "No documentation available.") + "\n"
#			return f"Return attributes to complete {workingcode}."
			whitespaceadjusted_workingcode = workingcode.lstrip()
			whitespaceadjusted_prefix = prefix + workingcode[:len(workingcode)-len(whitespaceadjusted_workingcode)]
			# Move leading whitespace onto prefix, so function arguments, list items, etc, resolve correctly.
			working_base, working_dot, working_leaf = whitespaceadjusted_workingcode.rpartition('.')
			if working_base:
				base_obj = self.Evaled(working_base)
				attrs = dir(base_obj)
				get_a = lambda a: getattr(base_obj, a, None)
			else:
				attrs = self.scope
				get_a = lambda a: self.scope[a]
			return tuple([
					whitespaceadjusted_prefix
					+ working_base
					+ working_dot
					+ (
						f"{a}[" 
							if self.get_keys(get_a(a)) else
						f"{a}("
							if self.check_callable(get_a(a)) else
						a
					)
					+ suffix
				for a in attrs
					if a.startswith(working_leaf)
			])
		except Exception as e:
#		except (NameError, AttributeError, KeyError, IndexError, SyntaxError, AssertionError) as e:
			return "No autocompletion found: "+utils.formatException(e)


class RlAutocompleteManager(AutocompleteManager):
	"""Autocompleter that uses the default Python autocompleter."""
	def GetAutocomplete(self, command, cursorpos=None):
		#Adds brackets to everything, due to presence of dynamic `.__call__` on `ForeignObject`.. Technically, I might be able to control `callable()` by implementing a metaclass with a custom `.__getattribute__` with custom descriptors on `ForeignObject`. Perhaps such a sin is still beyond even my bumbling arrogance, though.
		completer = rlcompleter.Completer(self.scope)
		(prefix, workingcode, lasttoken), suffix = self.GetCommandComponents(command[:cursorpos]), command[cursorpos:]
		if workingcode:
			matches = []
			for i in itertools.count():
				m = completer.complete(workingcode, i)
				if m is None:
					break
				else:
					matches.append(m)
		else:
			matches = [*self.scope.keys()]#, *keyword.kwlist]
		return tuple([prefix+m+suffix for m in matches])
		

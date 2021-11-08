

from . import wrapping

def real(obj):
	"""Evaluate a foreign object wrapper into a real Python value, or return a value unchanged if it is not a foreign object wrapper."""
	if isinstance(obj, wrapping.ForeignObject):
		return obj._getvalue()
	return obj

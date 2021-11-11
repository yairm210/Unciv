def formatException(exception):
	try:
		#Won't work on Upy.
		import traceback
		return "".join(traceback.format_exception(type(exception), exception, exception.__traceback__))
	except:
		return repr(exception)
		

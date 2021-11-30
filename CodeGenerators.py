import os, re, string, math


# TODO: Honestly, this has gotten to the point where it will be easier to rewrite in Kotlin and use an AST. Though, the Kotlin parser is apparently quite difficult and possibly fragile to use too.
# Kotlin/grammar-tools is official?
# https://jitinsharma.com/posts/parsing-kotlin-using-code-kotlin/

# Deprecated. Use reflection to get classes at runtime instead.


BASE_REPO_DIR = os.path.dirname(__file__)

ALPHANUMERIC = string.ascii_letters + string.digits

def relPath(path="core"):
	return os.path.join(BASE_REPO_DIR, path)

def starGlob(path="core"):
	for directory, subdirs, files in os.walk(relPath(path)):
		for d in subdirs:
			yield os.path.join(directory, d, "")
		for f in files:
			yield os.path.join(directory, f)

DEFAULT_SOURCE_FILES = tuple(starGlob("core"))


# Obviously a proper parser would be better than this.

def stripComment(code):
	stringed = code.split('"')
	for i in range(0, len(stringed), 2):
		if "//" in stringed[i]:
			stringed[i] = stringed[i].partition("//")[0]
			stringed = stringed[0:i+1]
			break
	return re.sub('/\*.*\*/', '', '"'.join(stringed))

def partitionNextName(code):
	code = code.strip()
	split = re.search(f"[^_{ALPHANUMERIC}]", code)
	if split is None:
		return code
	start = split.start()
	return code[:start], code[start:]

def partitionClassSig(code):
	# TODO: Strip keywords here?
	name, sig = partitionNextName(code)
	sig = sig.strip(' {}\n\t')
	typesig = sig.rpartition(':')
	#typesig_first = re.search(f'[^ \t{ALPHANUMERIC}\.]', typesig[2])
	#if typesig_first and typesig[2][typesig_first.start()] == '(':
		# I think round brackets in arguments should exist only in default values, so if the first strange character after the last colon is an opening round bracket, then the last colon probably means inheritance.
		# Could also check for equal number of closing and opening brackets. But that could break with specific string default arguments.
		# Right. Inheritance can have angular brackets from type parameters. Nvm.
		# Also, this would break with function types anyway.
	if typesig[2].count('(') == typesig[2].count(')'):
		sig, typesig = typesig[0].strip(), typesig[2].strip()
	if len(sig) > 2 and sig[0] == '(' and sig[-1] == ')':
		sigargs = []
		currarg = []
		bracklevel = 0
		last_c = None
		for c in sig[1:-1]:
			if (not bracklevel) and c == ',':
				sigargs.append(''.join(currarg).strip())
				currarg.clear()
				continue
			currarg.append(c)
			if c in '<([':
				bracklevel += 1
			elif c in ')]':
				bracklevel -= 1
			elif c == '>' and last_c != '-':
				# Function type arrows.
				bracklevel -=1
			last_c = c
		sigargs.append(''.join(currarg).strip())
		# Type params can have commas.
		del currarg, bracklevel
		sig = tuple((name.strip().rpartition(' ')[2], params.partition('=')[0].strip()) for arg in sigargs if arg for name, _, params in [arg.partition(':')])
		# Skip empties to handle trailing commas.
	else:
		sig = ()
	return name, sig, typesig

class KDef:
	def __init__(self, packagepath, basepath, name, sig):
		self.packagepath = packagepath
		self.basepath = basepath
		self.name = name
		self.sig = sig
	def __repr__(self):
		return f"{self.__class__.__name__!s}({self.basepath!r}, {self.name!r}, {self.sig!r})"
	def getQualifiedPath(self):
		return '.'.join(i for l in (self.packagepath, self.basepath, (self.name,)) for i in l)
	

def scrapeClassDefs(keyword='enum class ', filepaths=DEFAULT_SOURCE_FILES, *, sort=lambda d: d.getQualifiedPath()):
	assert keyword in {'enum class ', 'class '}
	defs = []
	for p in filepaths:
		if not p.endswith('.kt'):
			continue
		with open(p, 'r') as file:
			iscommented = False
			i = 0
			def nextl():
				nonlocal iscommented
				nonlocal i
				l = file.readline()
				i += 1
				if not l:
					raise StopIteration()
				l = stripComment(l)
				if '*/' in l:
					iscommented = False
					l = l.partition('*/')[2]
				if '/*' in l:
					iscommented = True
					l = l.partition('/*')[0]
				elif iscommented:
					return nextl()
				return l
			try:
				package = None
				while not package:
					# Find package name.
					package = nextl().partition('package ')[2].rstrip()
				basepath = []
				indent = 0
				privatelevel = math.inf
				setprivate = False
				while True:
					line = nextl()
					stripped = line.lstrip()
					newscope = None
					if 'private class ' in line:
						newscope = 'private class '
						setprivate = keyword == 'class '
					elif 'fun ' in line:
						newscope = 'fun '
						setprivate = keyword == 'class '
					elif 'class ' in line:
						newscope = 'class ' # Set keyword for scope's name.
					elif 'object ' in line:
						newscope = 'object '
						setprivate = keyword == 'class ' # Mostly broken because classes in _ScriptingConstantsClasses need each other, I think.
					if newscope:
						indent = (len(line)-len(stripped)) // 4 # Spaces 🙄.
						basepath = basepath[:indent] # Trim higher levels.
						basepath.append(partitionNextName(stripped.partition(newscope)[2])[0])
						if setprivate:
							privatelevel = min(privatelevel, indent)
						elif indent <= privatelevel:
							privatelevel = math.inf
						while line.count('(') != line.count(')'):
							line += ' ' + nextl().strip()
							# Horrible way of handling multi-line argument declarations.
							# I mean, continuing if the current expression is invalid is also how a lot of languages do it. The issue is failure to account for comments and strings, but comments are at least already implemented elsewhere.
					if privatelevel == math.inf and stripped.startswith(keyword): # Implies a newscope was also True.
						name, sig, typesig = partitionClassSig(line.partition(keyword)[2])
						defs.append(KDef(
							(package,), # Package name.
							tuple(p for p in basepath[:indent] if p), # Clip nesting level to indent level. Companions produce empty names, so skip those.
							name,
							sig
						))
			except StopIteration:
				pass
	return defs if sort is None else sorted(defs, key=sort)

def scrapeImportStatements(filepaths=DEFAULT_SOURCE_FILES):
	statements = set()
	for p in filepaths:
		if not p.endswith('.kt'):
			continue
		with open(p, 'r') as file:
			while line := file.readline():
				line = stripComment(line).strip()
				if line.startswith('import '):
					statements.add(line)
	return sorted(statements)


GENERATED_COMMENT_BEGIN = "// **THE BELOW CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**"
GENERATED_COMMENT_END = "// **THE ABOVE CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**"

def markGeneratedComment(func):
	def _markGeneratedComment(*args, **kwargs):
		return GENERATED_COMMENT_BEGIN+'\n'+func(*args, **kwargs)+'\n'+GENERATED_COMMENT_END
	return _markGeneratedComment

def copyToClipboard(text):
	import tkinter
	tk = tkinter.Tk()
	tk.withdraw()
	tk.clipboard_clear()
	tk.clipboard_append(text)


@markGeneratedComment
def genEnumImports(filepaths=DEFAULT_SOURCE_FILES):
	return "\n".join(f'import {e.getQualifiedPath()}' for e in scrapeClassDefs('enum class ', filepaths))

@markGeneratedComment
def genEnumMaps(filepaths=DEFAULT_SOURCE_FILES):
	return "\n".join(f'    val {e.name} = enumToMap<{e.getQualifiedPath()}>()' for e in scrapeClassDefs('enum class ', filepaths))


@markGeneratedComment
def genClassImports(filepaths=DEFAULT_SOURCE_FILES):
	classes = scrapeClassDefs('class ', filepaths)
	otherimports = scrapeImportStatements(filepaths)
	otherimportsmapping = {s.rpartition('.')[-1]: s for s in otherimports}
	basetypes = {kdef.name for kdef in classes}
	neededtypes = {a[1] for kdef in classes for a in kdef.sig}
	classpaths = [f'import {e.getQualifiedPath()}' for e in classes]
	argpaths = [otherimportsmapping[t] for t in neededtypes if t not in basetypes and t in otherimportsmapping]
	universals = {*(s for s in otherimports if s.endswith('.*')), *(''.join((*s.rpartition('.')[:2], '*')) for l in (classpaths, argpaths) for s in l)}
	return "\n".join(sorted(universals))

factory_bins = {
	'': "Rulesets",
	'com.unciv.ui': ""
}

factory_blacklist = {
	'UncivSound', # Private constructor.
	'TileGroupMap', # Type params.
	'UncivTooltip', # Type params.
	'Simulation', # Requires Experimental opt-in.
	'TechPickerScreen' # For some reason Technology? uniquely fails to resolve as an argument type. Could try qualified path, but not worth it.
}

@markGeneratedComment
def genClassFactories(filepaths=DEFAULT_SOURCE_FILES):
	return "\n".join(f'fun {e.name}({", ".join(": ".join(a) for a in e.sig)}) = {e.getQualifiedPath()}({", ".join(f"{a[0]}={a[0]}" for a in e.sig)})' for e in scrapeClassDefs('class ', filepaths) if e.name not in factory_blacklist)

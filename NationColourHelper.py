#!/usr/bin/env python3

"""
This script is intended to help detect nation colours that may be appear very similar depending on the viewer's colour vision.

Due to the extremely high number of nations included in the game, and due to the high reliance on hue to distinguish player and terrain data, full colourblindness support is not currently considered feasible in Unciv:

https://github.com/yairm210/Unciv/issues/231

However, my hope is that this script can still be useful.

Simply run this script from the command line to interactively print a report for similar colours between player civilizations that may affect more than 1% of the human population.

For more granular control, such as including city states, checking for rarer forms of colourblindness, loading nations from a different filepath, or using a more sensitive threshold for similiartiy, call testNations() with different arguments.
"""


import re, os, json, colorsys, math, types, io


def stripJsonComments(text):
	"""Strip JS-style comments from text."""
	return re.sub("//.*", "", re.sub('/\*.*\*/', "", text, flags=re.DOTALL))

def singleton(*args, **kwargs):
	"""Return a decorator to instantiate a class."""
	def _singleton(cls):
		return cls(*args, **kwargs)
	return _singleton

def staticMethods(obj):
	"""Replace an objects' bound non-magic methods with its classes' functions."""
	for name in dir(obj):
		if name.startswith('__') and name.endswith('__'):
			continue
		attr = getattr(obj, name)
		if isinstance(attr, types.MethodType):
			setattr(obj, name, next(cls for cls in obj.__class__.__mro__ if name in cls.__dict__).__dict__[name])
	return obj

DEFAULT_NATIONS_JSON = os.path.join(os.path.dirname(__file__), "android/assets/jsons/Civ V - Gods & Kings/Nations.json")
DEFAULT_NATIONS_JSON2 = os.path.join(os.path.dirname(__file__), "android/assets/jsons/Civ V - Vanilla/Nations.json")

class Indexable:
	"""Base class to let bind key access to attribute access."""
	def __getitem__(self, key):
		if isinstance(key, slice):
			return getattr(self, slice.start, slice.stop)
		else:
			return getattr(self, key)

def singletonKeys(obj):
	"""Return all non-magic attributes on an object."""
	return (name for name in dir(obj) if not (name.startswith('__') and name.endswith('__')))


@staticMethods
@singleton()
class ColourTransforms(Indexable):
	"""Miscellaneous colour transformation functions."""
	NormalizeRGB = lambda rgb: tuple([c/255 for c in rgb])

@staticMethods
@singleton()
class ColourSpaces(Indexable):
	"""Functions to transform RGB values into a colour space for distance comparison."""
	RGB = lambda rgb: (*rgb,)
	HSV = lambda rgb: colorsys.rgb_to_hsv(*rgb)
	HLS = lambda rgb: colorsys.rgb_to_hls(*rgb)
	YIQ = lambda rgb: colorsys.rgb_to_yiq(*rgb)
	HSV_V = lambda rgb: (colorsys.rgb_to_hsv(*rgb)[2],)
	HLS_L = lambda rgb: (colorsys.rgb_to_hls(*rgb)[1],)
	YIQ_Y = lambda rgb: (colorsys.rgb_to_yiq(*rgb)[0],)

@staticMethods
@singleton()
class ColourTests(Indexable):
	"""Functions that squeeze RGB values into subsets of the RGB gamut intended to roughly simulate different types of colourblindness."""
	Normal_Vision = ColourSpaces.RGB
	Protanomaly = lambda rgb: multVec(rgb, (0, 1, 1))
	Deuteranomaly = lambda rgb: multVec(rgb, (1, 0, 1))
	Tritanomaly = lambda rgb: multVec(rgb, (1, 1, 0))
	Monochromacy_R = lambda rgb: multVec(rgb, (1, 0, 0))
	Monochromacy_G = lambda rgb: multVec(rgb, (0, 1, 0))
	Monochromacy_B = lambda rgb: multVec(rgb, (0, 0, 1))
	Achromatopsia = lambda rgb: multVec(rgb, (0, 0.8, 1.0)) # Crude coefficients based on SRGB primaries and rod cell response graph.

@staticMethods
@singleton()
class ColourTestComparers(Indexable):
	"""Functions to convert RGB values into the colourspaces in which results from each function in ColourTests should be compared."""
	Normal_Vision = ColourSpaces.YIQ # LAB would obviously be ideal. But I don't really feel like either implementing it myself or bringing in a dependency.
	Protanomaly = ColourSpaces.YIQ
	Deuteranomaly = ColourSpaces.YIQ
	Tritanomaly = ColourSpaces.YIQ
	Monochromacy_R = ColourSpaces.YIQ_Y
	Monochromacy_G = ColourSpaces.YIQ_Y
	Monochromacy_B = ColourSpaces.YIQ_Y
	Achromatopsia = ColourSpaces.YIQ_Y # Crude coefficients based on SRGB primaries and rod cell response graph.

@singleton()
class ColourRates(Indexable):
	"""Rates of occurence for different types of colourblindness."""
	Normal_Vision = 1.0
	Protanomaly = 0.006 # Wikipedia, roughly.
	Deuteranomaly = 0.04
	Tritanomaly = 0.0001
	Monochromacy_R = Deuteranomaly*Tritanomaly # Since these are apparently combinations of dichromacy, very crude estimate by multiplying their probabilities.
	Monochromacy_G = Protanomaly*Tritanomaly
	Monochromacy_B = Protanomaly*Deuteranomaly
	Achromatopsia = 1/30000 # Wikipedia

def multVec(vec1, vec2):
	"""Multiply two n-dimensional vectors element-wise."""
	return tuple([a*b for a, b in zip(vec1, vec2)])

def pythagorean(vec):
	"""Return the Pythagorean sum of all the elements in a vector."""
	return math.sqrt(sum(v**2 for v in vec))

def distance(vec1, vec2):
	"""Return the distance between two n-dimensional vectors."""
	return pythagorean(a-b for a, b in zip(vec1, vec2))


def loadNations(fp=DEFAULT_NATIONS_JSON):
	"""Load a Nations file from a given or default JSON path."""
	with open(fp, 'r') as file:
		return json.loads(stripJsonComments(file.read()))

def colourDistance(rgb1, rgb2, *transforms):
	"""Return the distance between two colours, optionally applying transformation functions to them."""
	col1, col2 = rgb1, rgb2
	for transf in transforms:
		col1 = transf(col1)
		col2 = transf(col2)
	return distance(col1, col2)

def nationsDistance(nation1, nation2, *coltransforms):
	"""Return the distinguishability of two nations based on their colours, as the maximum of the differences between their respective inner and outer colours after normalizing their RGB values and applying any given colour transforms."""
	innerColours, outerColours = (tuple(nation.get(key, None) for nation in (nation1, nation2)) for key in ('innerColor', 'outerColor'))
	return max((colourDistance(*comp, ColourTransforms.NormalizeRGB, *coltransforms) if all(comp) else 1.0) for comp in (innerColours, outerColours))

def iterateNationPairs(*, nations=None):
	"""Yield all possible pairs of nations, from a given list of nations if provided or from the default Nations file otherwise."""
	if nations is None:
		nations = loadNations()
	nations = tuple(nations)
	for i, nation in enumerate(nations):
		for n in range(i+1, len(nations)):
			yield (nation, nations[n])

def sigFig(num, figs=1):
	"""Round a number to a given number of digits."""
	return round(num, -math.floor(math.log10(num))+figs-1) if num else 0.0

def isPlayableNation(nation):
	"""Return whether a nation is not a city state."""
	return 'cityStateType' not in nation


def testNations(mindifference=0.1, minprevalence=0.0, filternations=lambda n: True, *, nations=None, sort=-1):
	"""
	Find pairs of nations that may look very similar to each other with colourblindness.
	
	Arguments:
		mindifference: Threshold for detecting similar colours, as a distance in the comparison colour space.
		minprevalence: Skip all tests that are not estimated to affect at least this fraction of the human population.
		filternations: Run tests only for nations with which this callable returns true.
		
		nations: List of nations, or None to read from default file.
		sort: 0 for no sorting, -1 for most similar colours last, 1 for most similar colours first.
	
	Returns a dictionary of potentially problematic colours in the form: {(nation1, nation2): [(colourdifference, testname)]}.
	"""
	problems = {}
	for (nation, other) in iterateNationPairs(nations=nations):
		if all(filternations(n) for n in (nation, other)):
			for cb in singletonKeys(ColourTests):
				if ColourRates[cb] >= minprevalence:
					difference = nationsDistance(nation, other, ColourTests[cb], ColourTestComparers[cb])
					if difference < mindifference:
						key = (nation['name'], other['name'])
						if key not in problems:
							problems[key] = []
						problems[key].append((cb, difference))
	if sort:
		keyorder = sorted(problems.keys(), key=lambda k: min(diff for cb, diff in problems[k])*sort)
		problems = {k: problems[k] for k in keyorder}
	return problems

def formatNationTestResults(problems):
	"""Format results from testNations() into printable text."""
	lines = []
	for (nation, other), fails in problems.items():
		lines.append(f"{nation} and {other} may be hard to tell apart with:")
		for cb, diff in fails:
			lines.append(f"\t{cb}. (Diff: {sigFig(diff, 2)}, Affects: ~{sigFig(ColourRates[cb]*100,3)}%)")
	lines.append("")
	lines.append(f"Found {len(problems)} potentially problematic pairings.")
	return "\n".join(lines)


def __main__():
	print()
	for cb in singletonKeys(ColourRates):
		print(f"\t{cb}: {sigFig(ColourRates[cb]*100, 2)}%")
	prev = input("\nThe above are different types of colourblindness, along with (very) crude estimates of what percentage of the human population they affect.\n\nIn order to narrow down results, this tool can run checks only for types of colourblindness that affect at least a certain percentage of the human population.\n\nPlease enter a percentage (Default 1%): ")
	prev = float(prev.rstrip("%"))/100 if prev.strip() else 0.01
	diff = input("\n\nSimilar colours are detected by converting colours to a perceptually even colour space and then measuring the distance between them. A distance of 0.0 means that the colours will look identical, and a distance of 1.0 or greater means that they will look completely different.\n\nNations are flagged as potentially problematic if their colours don't meet a minimum distance requirement.\n\nPlease enter a minimum required colour distance (Default 0.1): ")
	diff = float(diff) if diff.strip() else 0.1
	cs = input("\n\nShould city state colours be included in the analysis? (Y/N, Default N): ").strip().lower()
	cs = (lambda n: True) if (cs and cs[0] == 'y') else isPlayableNation
	files = []
	for fp in (DEFAULT_NATIONS_JSON, DEFAULT_NATIONS_JSON2):
		r = input(f"\n\nShould nations from the following file be included in the analysis?\n{fp}\n(Y/N, Default Y): ").strip().lower()
		if not (r and r[0] == 'n'):
			files.append(fp)
	while True:
		ask = input("\n\nDo you wish to analyze nations from any other files?\nPlease enter a filepath (Leave blank to skip): ")
		if ask:
			files.append(ask)
		else:
			break
	for fp in files:
		try:
			print("\n")
			print(formatNationTestResults(testNations(mindifference=diff, minprevalence=prev, filternations=cs, nations=loadNations(fp))))
			input(f"\nFinished analyzing {fp}.\nPress [ENTER] to continue: ")
		except Exception as e:
			input(f"\nError analyzing {fp}: {repr(e)}\nPress [ENTER] to continue: ")
	print(f"\nCompleted nation colour similarity analysis for {len(files)} files.\n")
if __name__ == '__main__':
	import sys
	if not getattr(sys, 'ps1', sys.flags.interactive):
		__main__()
	else:
		print("Running in Python interactive mode. Execute __main__() to begin wizard, or call testNations() manually.")


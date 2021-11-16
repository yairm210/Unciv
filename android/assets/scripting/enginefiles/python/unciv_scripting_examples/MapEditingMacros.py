"""
Example scripted map editor tools.

Shows benefits of using the scripting language:
User modifiability, rapid development, idiomatic and expressive syntax, and dynamic access to system libraries.

These example features below would be a nightmare to implement and maintain in the Kotlin code.
But in Python, they can be drafted up fairly quickly, and it doesn't even matter if they break, because they can't really interfere with anything else.

And most importantly, they can be made and distributed by the user themselves as external files in their interpreter's library path. This lets very obscure and niche features be implemented and used by those who want them, without adding anything to the maintenance burden for the core Unciv codebase.
"""

# If you modify this file, please add any new functions to the build tests.

_defaultterrainsequence = ()

#[(setattr(t, "baseTerrain", "Mountain") if real(t) else None) for r in gameInfo.tileMap.tileMatrix for t in r]
#Apparently there's no distinction between base terrain types and terrain features.
#[([setattr(t, "baseTerrain", "Grassland" if t.position.x % 2 else "Coast"), setattr(t, "naturalWonder", None if t.position.y%3 else "Krakatoa")] if real(t) else None) for r in gameInfo.tileMap.tileMatrix for t in r]


def hexCoordToRectCoord(vector):
	pass

def rectCoordToHexCoord(vector):
	pass


def terrainAsString(tileInfo):
	s = real(t.baseTerrain)
	if len(t.terrainFeatures):
		s += "/" + ",".join()
	return s

def terrainFromString(terrainstring):
	return baseterrain, tuple(terrainfeatures)

def setTerrain(tileInfo, terrainstring):
	pass


def checkValidValue(value, checktype="TileType"):
	if value not in unciv.mapEditorScreen.ruleset.whatever:
		raise Exception()


def spreadResources(resourcetype="Horses", mode="random", restrictfrom=(), restrictto=None):
	if mode == "random":
		pass
	elif mode in ("jittered", "grid", "clustered", "bluenoise"):
		raise NotImplementedError(f"")
	else:
		raise TypeError()

def makeMandelbrot():
	pass

def graph2D(expr="sin(x/5)*5", north="Ocean", south="Desert"):
	pass

def graph3D(expr="sqrt(x**2+y**2)/5%5*len(terrains)", terrains=_defaultterrainsequence)
	pass

def loadImageHeightmap(imagepath=None, tiletypes("Ocean", "Coast")):
	import PIL
	pass

def dilateTileTypes(tiletypes=("Coast", "Flood Plains"), chance=1.0, forbidreplace=("Ocean", "Mountain"), dilateas=("Desert/Flood Plains", "Coast"), iterations=1):
	# .terrainFeatures and .baseTerrain
	pass

def erodeTileType(tiletypes=("Mountains", "Plains/Hill", "Grassland/Hill")):
	pass

def floodFillSelected(start=None, fillas=None, *, alsopropagateto=()):
	pass


def _computeTerrainColours(terraintypes)
	global _

def requireComputedColours(terraintypes, allowcompute=False):
	global _
	if not _ or not all(t in _ for t in terraintypes):
		if allow_compute_colours:
			pass
		else:
			print(f"This function requires the average colour to be computed for the following terrain types:\n{terraintypes}\n\nDoing so may take a long time. The interface will be unresponsive during the process.\nPass `allow_compute_colours=True` to this function, or run `_computeTerrainColours({terraintypes})`, in order to compute the required colours.")


def loadImageColours(imagepath=None, allowedterrains=(), allow_compute_colours=False, visualspace=True):
	global _
	requireComputedColours(allowedterrains, allow_compute_colours)

def makeImageFromTerrainColours(allowedterrains, allow_compute_colours, visualspace=True):
	requireComputedColours
	pass# Make a PIL image, but don't mess with the user's filesystem.
	#I guess inversing the output from this using loadImageColours could be a unit test.

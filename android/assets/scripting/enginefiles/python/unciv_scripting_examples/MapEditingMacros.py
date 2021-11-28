"""
Example scripted map editor tools.

Shows benefits of using the scripting language:
User modifiability, rapid development, idiomatic and expressive syntax, and dynamic access to system libraries.

These example features below would be a nightmare to implement and maintain in the Kotlin code.
But in Python, they can be drafted up fairly quickly, and it's not a critical issue if they break, because they can't really interfere with anything else.

And most importantly, they can be made and distributed by the user themselves as external files in their interpreter's library path. This lets very obscure and niche features be implemented and used by those who want them, without adding anything to the maintenance burden for the core Unciv codebase.
"""

import math, random, os, json, re, base64, io

import unciv
from unciv_pyhelpers import *

from . import Utils

# If you modify this file, please add any new functions to Tests.py.


t = re.sub("//.*", "", re.sub('/\*.*\*/', "", unciv.apiHelpers.assetFileString("jsons/Civ V - Gods & Kings/Terrains.json"), flags=re.DOTALL))
terrainsjson = json.loads(t)
# In an actual implementation, you would want to read from the ruleset instead of the JSON. But this is eaiser for me.
del t

terrainbases = {t['name']: t for t in terrainsjson if t['type'] in ('Water', 'Land')}
terrainfeatures = {t['name']: t for t in terrainsjson if t['type'] == 'TerrainFeature'}


def genValidTerrains(*, forbid=('Fallout',)):
	#Searches only two layers deep. I.E. Only combinations with at most two TerrainFeatures will be found.
	terrains = set()

	for terrain in terrainbases:
		if terrain in forbid:
			continue
		terrains.add(terrain)
		for feature, fparams in terrainfeatures.items():
			if feature not in forbid and terrain in fparams['occursOn']:
				terrains.add(f"{terrain}/{feature}")
				for otherfeature in fparams['occursOn']:
					if otherfeature not in forbid and otherfeature in terrainfeatures:
						otherparams = terrainfeatures[otherfeature]
						if terrain in otherparams['occursOn']:
							terrains.add(f"{terrain}/{otherfeature},{feature}")
	return terrains

naturalterrains = tuple(sorted(genValidTerrains()))


_altitudeterrainsequence = (
	"Ocean",
	("Ocean", "Coast"),
	(*("Coast",)*9, "Coast/Atoll"),
	("Desert/Flood plains", "Desert", "Desert/Marsh"),
	(*("Plains",)*5, "Plains/Forest"),
	(*("Grassland",)*3, "Grassland/Forest"),
	("Grassland/Hill", "Grassland/Hill,Forest"),
	("Plains/Hill,Forest", "Grassland/Hill,Jungle", "Plains/Hill,Jungle", "Desert/Hill", "Lakes"),
	("Mountain", "Tundra/Hill"),
	("Mountain", "Snow/Hill", "Mountain/Ice")
)

_hardterrainsequence = (
	"Ocean",
	"Coast",
	"Desert/Marsh",
	"Desert/Flood plains",
	"Desert",
	"Plains",
	"Grassland",
	"Plains/Forest",
	"Grassland/Forest",
	"Grassland/Hill"
	"Plains/Hill",
	"Desert/Hill",
	"Mountain",
	"Snow/Hill",
	"Tundra/Hill",
	"Tundra",
	"Snow",
	"Snow/Ice"
)


def indexClipped(items, index):
	return items[max(0, min(len(items)-1, index))]

def defaultArgTilemap(tileMap=None):
	if tileMap is None:
		if unciv.apiHelpers.isInGame:
			return unciv.gameInfo.tileMap
		else:
			return unciv.mapEditorScreen.tileMap
	else:
		return tileMap


def terrainAsString(tileInfo):
	s = real(tileInfo.baseTerrain)
	if len(tileInfo.terrainFeatures):
		s += "/" + ",".join(real(f) for f in tileInfo.terrainFeatures)
	return s

def terrainFromString(terrainstring):
	baseterrain, _, terrainfeatures = terrainstring.partition("/")
	terrainfeatures = terrainfeatures.split(",") if terrainfeatures else []
	return baseterrain, tuple(terrainfeatures)

def setTerrain(tileInfo, terraintype):
	if not isinstance(terraintype, str):
		terraintype = random.sample(terraintype, 1)[0]
	base, features = terrainFromString(terraintype)
	tileInfo.baseTerrain = base
	tileInfo.terrainFeatures.clear()
	for f in features:
		tileInfo.terrainFeatures.add(f)


def spreadResources(resourcetype="Horses", mode="random", restrictfrom=(), restrictto=None):
	if mode == "random":
		raise NotImplementedError()
	elif mode in ("jittered", "grid", "clustered", "bluenoise"):
		raise NotImplementedError(f"")
	else:
		raise TypeError()


def dilateTileTypes(tiletypes=("Coast", "Flood Plains"), chance=1.0, forbidreplace=("Ocean", "Mountain"), dilateas=("Desert/Flood Plains", "Coast"), iterations=1):
	raise NotImplementedError()

def erodeTileType(tiletypes=("Mountains", "Plains/Hill", "Grassland/Hill")):
	raise NotImplementedError()

def floodFillSelected(start=None, fillas=None, *, alsopropagateto=()):
	raise NotImplementedError()


mandlebrotpresets = {
	"Minibrot": {'center': (-0.105, -0.925), 'viewport': 0.006, 'indexer': "round((1/(i/8+1))*len(terrains))"},
	"Hat": {'center': (-1.301, -0.063), 'viewport': 2E-2, 'iterations': 300, 'indexer': "round(i/300*len(terrains))"},
	"TwinLakes": {'center': (-1.4476, -0.0048), 'viewport': 1E-3, 'iterations': 50, 'indexer': "round(i/200*len(terrains))"},
	"Curly": {'center': (-0.221, -0.651), 'viewport': 6E-3, 'iterations': 70, 'indexer': "round((1/(i/6+1))*len(terrains))"},
	"Crater": {'center': (-1.447858, -0.004673), 'viewport': 3.5E-5, 'iterations': 80, 'indexer': "round((1-1/(i+1))*len(terrains))"},
	"Rift": {'center': (-0.700, -0.295), 'viewport': 3E-3, 'iterations': 100},
	"Spiral": {'center': (-0.676, -0.362), 'viewport': 3E-3, 'iterations': 100, 'indexer': "round((1-1/(i+1))*len(terrains))"},
	"Pentabrot": {'expo': 6}
}

def _mandelbrot(x, y, iterations=100, *, expo=2, escaperadius=12, innervalue=None):
	c=complex(x,y)
	z = 0+0j
	dist = 0
	if innervalue is None:
		innervalue = iterations
	for i in range(iterations):
		dist = math.sqrt(z.real**2+z.imag**2)
		if dist > escaperadius:
			break
		z = z**expo+c
	return innervalue if dist <= escaperadius else i + 1 - math.log(math.log(dist), expo)


def makeMandelbrot(tileMap=None, *, viewport=4, center=(-0.5,0), iterations=100, expo=2, indexer="round((1/(i+1))*len(terrains))", terrains=_hardterrainsequence, innervalue=0):
	tileMap = defaultArgTilemap(tileMap)
	scalefac = viewport / max(tileMap.mapParameters.mapSize.width, tileMap.mapParameters.mapSize.height)
	offset_x, offset_y = center
	indexer = compile(indexer, filename="indexer", mode='eval')
	def coordsfromtile(tile):
		return -tile.longitude*scalefac+offset_x, -tile.latitude*scalefac+offset_y
	for tile in tileMap.values:
		setTerrain(
			tile,
			indexClipped(
				terrains,
				eval(
					indexer,
					{
						'i': _mandelbrot(*coordsfromtile(tile), iterations=iterations, expo=expo, innervalue=innervalue),
						'terrains': terrains,
						'iterations': iterations
					}
				)
			)
		)


def graph2D(tileMap=None, expr="sin(x/3)*5", north="Ocean", south="Desert"):
	tileMap = defaultArgTilemap(tileMap)
	expr = compile(expr, filename="expr", mode='eval')
	for tile in tileMap.values:
		setTerrain(
			tile,
			north if tile.latitude > eval(expr, {**math.__dict__, 'x': tile.longitude}) else south
		)

def graph3D(tileMap=None, expr="sqrt(x**2+y**2)/6%5/5*len(terrains)", terrains=_hardterrainsequence):
	tileMap = defaultArgTilemap(tileMap)
	expr = compile(expr, filename="expr", mode='eval')
	for tile in tileMap.values:
		setTerrain(
			tile,
			indexClipped(
				terrains,
				math.floor(eval(
					expr,
					{
						**math.__dict__,
						'x': real(tile.longitude),
						'y': real(tile.latitude),
						'terrains': terrains
					}
				))
			)
		)


def setMapFromImage(tileMap, image, pixelinterpreter=lambda pixel: "Ocean"):

	longitudes, latitudes = ([real(getattr(t, a)) for t in tileMap.values] for a in ('longitude', 'latitude'))
	min_long, max_long, min_lat, max_lat = (f(c) for c in (longitudes, latitudes) for f in (min, max))
	del longitudes, latitudes
	width = max_long - min_long
	height = max_lat - min_lat

	width_fac = (image.size[0] - 1) / width
	height_fac = (image.size[1] - 1) / height

	for tile in tileMap.values:
		# Since this just uses PIL images, we could also blur the image, or perhaps just jitter the sampled coordinates, by the projected tile radius here. Or could have earlier functions in the call stack wrap the image in a class that does that.
		# See if reducing the Kotlin/JVM reflection depth by assigning tile to apiHelpers.registeredInstances reduces run time here.
		setTerrain(
			tile,
			pixelinterpreter(image.getpixel((
				round((-tile.longitude + max_long) * width_fac),
				round((-tile.latitude + max_lat) * height_fac)
			)))
		)


def _imageFallbackPath(imagepath):
	if not os.path.exists(imagepath):
		_fallbackpath = Utils.exampleAssetPath(imagepath)
		if os.path.exists(_fallbackpath):
			imagepath = _fallbackpath
			print(f"Invalid image path given. Interpreting as example path at {repr(imagepath)}")
		del _fallbackpath
	return imagepath



def loadImageHeightmap(tileMap=None, imagepath="EarthTopography.png", transform="pixel*len(terrains)", terrains=_altitudeterrainsequence, normalizevalues=255):
	tileMap = defaultArgTilemap(tileMap)
	import PIL.Image
	transform = compile(transform, filename="transform", mode='eval')
	imagepath = _imageFallbackPath(imagepath)
	def pixinterp(pixel):
		if isinstance(pixel, tuple):
			pixel = sum(pixel)/len(pixel)
		pixel /= normalizevalues
		pixel = round(eval(transform, {'pixel': pixel, 'terrains': terrains}))
		return indexClipped(terrains, pixel)

	with PIL.Image.open(imagepath) as image:
		setMapFromImage(tileMap=tileMap, image=image, pixelinterpreter=pixinterp)



def terrainImagePath(feature):
	# Look in TileGroup.kt if you want to replace this with something that handles different tilesets.
	return f"TileSets/FantasyHex/Tiles/{feature}"

def compositedTerrainImage(terrain):
	import PIL.Image
	base, features = terrainFromString(terrain)
	image = PIL.Image.open(io.BytesIO(base64.b64decode(unciv.apiHelpers.assetImageB64(terrainImagePath(base)))))
	for feature in features:
		with PIL.Image.open(io.BytesIO(base64.b64decode(unciv.apiHelpers.assetImageB64(terrainImagePath(feature))))) as layer:
			image.alpha_composite(layer, (0, image.size[1]-layer.size[1]))
	return image

def getImageAverageRgb(image):
	#image.convert('P', palette=PIL.Image.ADAPTIVE, colors=1) (Doesn't work by nearest.)
	depth = 255
	assert image.mode in ("RGB", "RGBA")
	hasalpha = image.mode == "RGBA"
	r_sum = g_sum = b_sum = total_alpha = 0
	for x in range(image.size[0]):
		for y in range(image.size[1]):
			if hasalpha:
				r, g, b, a = image.getpixel((x, y))
				a /= depth
			else:
				r, g, b = image.getpixel((x, y))
				a = 1.0
			r_sum += r * a
			g_sum += g * a
			b_sum += b * a
			total_alpha += a
	return tuple(c/total_alpha for c in (r_sum, g_sum, b_sum))


def computeTerrainAverageColours(terrains=naturalterrains):
	def terraincol(terrain):
		with compositedTerrainImage(terrain) as i:
			return getImageAverageRgb(i)
	return {terrain: tuple(round(n) for n in terraincol(terrain)) for terrain in terrains}


class _TerrainColourInterpreter:
	# To actually look good, this should use CIE, YUV, or at least HSV with a compressed saturation axis.
	def __init__(self, terraincolours, maxdither=0):
		self.terraincolours = terraincolours
		self.maxdither = maxdither
		if self.maxdither:
			self.dithererror = [0, 0, 0]
	@classmethod
	def rgb_distance(cls, rgb1, rgb2):
		return math.sqrt(sum([(a-b)**2 for a, b in zip(rgb1, rgb2)]))
	def get_terrainandcolour(self, rgb):
		return min(self.terraincolours.items(), key=lambda item: self.rgb_distance(item[1], rgb))
	def get_terraindithered(self, rgb):
		rgb_compensated = tuple(c_target-c_error for c_target, c_error in zip(rgb, self.dithererror))
		terrain, rgb_final = self.get_terrainandcolour(rgb_compensated)
		for i, (c_target, c_final, error_current) in enumerate(zip(rgb, rgb_final, self.dithererror)):
			self.dithererror[i] = max(-self.maxdither*256, min(self.maxdither*256, error_current + (c_final - c_target)))
			#Because the "colour palette" is usually very limited in range, and particularly because it often doesn't have any low-green values to bring the green error down (I.E. the mean channel value over the whole image may well be darker than the minimum available colour), limiting the maximum accumulatable error is necessary to avoid it running away.
		# print(self.dithererror) # This should generally tend back towards [0,0,0].
		return terrain
	def __call__(self, pixel):
		if self.maxdither:
			return self.get_terraindithered(pixel)
		else:
			return self.get_terrainandcolour(pixel)[0]

def loadImageColours(tileMap=None, imagepath="EarthTerrainFantasyHex.jpg", terraincolours=None, maxdither=0, visualspace=True):
	"""
	Set a given tileMap or the active tileMap's terrain based on an image file and a mapping of terrain strings to RGB tuples.

	Recommended example values for imagepath: EarthTerrainFantasyHex.png, StarryNight.jpg, TurboRainbow.png (Try maxdither=0.5.), WheatField.jpg
	"""
	#https://visibleearth.nasa.gov/images/73801/september-blue-marble-next-generation-w-topography-and-bathymetry
	#Generate TurboRainbow.png: from matplotlib import cm; from PIL import Image, ImageDraw; width=512; image = Image.new('RGB', (width,1), "white"); draw=ImageDraw.Draw(image); [draw.point((x,0), tuple(int(c*256) for c in cm.turbo(x/width)[:3])) for x in range(width)]; image.show("TurboRainbow.png"); image.close()
	import PIL.Image
	assert visualspace
	tileMap = defaultArgTilemap(tileMap)
	imagepath = _imageFallbackPath(imagepath)
	if terraincolours is None:
		print(f"\nNo terrain colours given. Computing average tile colours based on FantasyHex tileset. This may take several seconds.")
		terraincolours = computeTerrainAverageColours(naturalterrains)
		print(f"\nTerrain colours computed:\n{repr(terraincolours)}")
	pixinterp = _TerrainColourInterpreter(terraincolours, maxdither=maxdither)
	with PIL.Image.open(imagepath) as image:
		setMapFromImage(tileMap, image, pixinterp)


def makeImageFromTerrainColours(allowedterrains, allow_compute_colours, visualspace=True):
	requireComputedColours
	pass# Make a PIL image, but don't mess with the user's filesystem.
	#I guess inversing the output from this using loadImageColours could be a unit test.

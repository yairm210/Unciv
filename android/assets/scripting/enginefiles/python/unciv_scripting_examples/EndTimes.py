"""
Example and developmental test case for potential future modding API.

Adds scripted events that dynamically modify map and civilization state.

Watch out for volcanoes, meteor strikes, wildfires, and alien invaders!


Call onNewTurn() on every new turn.

Call onUnitMove(worldScreen.bottomUnitTable.selectedUnit) every time a unit moves.
"""

import unciv


# What does it say about me that my go-to for a developmental test case is to implement an apocalypse mod?
# Then again, randomly flipping data values is naturally the easiest test case to code regardless of lore.
# But randomness is entropy, so random changes to the world around you are by definition apocalyptic.
# ...At which point, the lore writes itself.


# Could inject new tiles for wildfires, fissures, etc into the ruleset.
# Nah. Let's try to keep this and Merfolk.py save-compatible, and leave the ruleset modification to ProceduralTechTree.

# Midgame: Defanged unkillable Barbarian "alien" scouts.
# Late game: Barbarian GDS, Helis.
# I guess could also give positive bonuses from alien encounters.

turnNotifications = []


def gaussianTileSelector(focus):
	def _gaussianTileSelector(tile):
		return True
	return _gaussianTileSelector


def depopulateCity(city, migrationsuccess=0.5):
	pass


def scatterFallout(focus, improvementtype, maxdistance, tileselector=lambda t: True):
	pass

def spawnNewDisasters(naturalwonder, *falloutparams):
	pass

def spreadFalloutType(improvementtype, tilepermitter=lambda t: True):
	pass

#worldScreen.mapHolder.selectedTile.position in civInfo.exploredTiles
#civInfo.addNotification("A volcano has risen from the Earth!", worldScreen.mapHolder.selectedTile.position, apiHelpers.Jvm.arrayOfString(["TileSets/FantasyHex/Tiles/Krakatoa"]))
#civInfo.exploredTiles.add(worldScreen.mapHolder.selectedTile.position)

def eruptVolcanoes():
	pass

def damageBurningUnits():
	pass

def damageBurningCities():
	pass

def depopulateBurningCities():
	# Gods.. This sounds kinda... bad, when I write it down.
	# Meh. I'll comfort myself by telling myself I go to war less than most Civ players.
	pass

def landAlienInvaders():#TileMap.placeUnitNearTile
	pass

def erodeMountains():
	pass

def erodeShorelines():
	pass


def onNewTurn():
	""""""
	spawnNewDisasters('Krakatoa')
	spawnNewDisasters('Barringer Crater')



def ambushUnit(unit):
	pass

def onUnitMove(unit):
	pass

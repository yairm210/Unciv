"""
Example and developmental test case for potential future modding API.

Call onNewTurn() on every new turn.

Call onUnitMove(worldScreen.bottomUnitTable.selectedUnit) every time a unit moves.
"""

import unciv


# What does it say about me that my go-to for a developmental test case is to implement an apocalypse mod?
# Then again, randomly flipping data values is naturally the easiest test case to code regardless of lore.
# But randomness is entropy, so random changes to the world around you are by definition apocalyptic.
# ...At which point, the lore writes itself.

turnNotifications = []


def gaussianTileSelector(focus):
	def _gaussianTileSelector(tile):
		return True
	return _gaussianTileSelector


def scatterFallout(focus, improvementtype, maxdistance, tileselector=lambda t: True):
	pass

def spawnNewDisasters(naturalwonder, *falloutparams):
	pass

def spreadFalloutType(improvementtype, tilepermitter=lambda t: True):
	pass

#worldScreen.mapHolder.selectedTile.position in civInfo.exploredTiles
#civInfo.addNotification("A volcano has risen from the Earth!", worldScreen.mapHolder.selectedTile.position, apiHelpers.Factories.arrayOfString(["TileSets/FantasyHex/Tiles/Krakatoa"]))
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

def landAlienInvaders():
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

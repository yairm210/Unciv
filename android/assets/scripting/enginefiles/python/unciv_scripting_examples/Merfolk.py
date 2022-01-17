"""
Example and developmental test case for potential future modding API.

Adds peaceful, playful Merpeople communities.

But they won't hesitate to defend themselves with powerful magic if you bully them or others!


Call onGameStart() **once** at start of game.

Call onNewTurn() on every new turn.

Call onUnitMove(worldScreen.bottomUnitTable.selectedUnit) every time a unit moves.
"""


# Have them be like rubberbanding mobile city states in the early game, popping up in shorelines, lakes, rivers, and land to give gifts to the weakest players.
# Instantly declare war on nuclear weapon use, foreign capital city capture, or city state capture.
# Wage war by spawning units in water tiles, poisoning the water supply/charming the populace into resistance in landlocked cities.
# Stay at war until capital puppeted in case of nuclear weapon use, all foreign cities liberated in case of foreign capital/CS capture.
# Fight against mechanized barbarians (synergy with EndTimes.py alien invaders).
# In late game, cast spells to destroy volcanoes/craters (synergy with EndTimes.py natural disasters).
# Migratory cities.
# Seed the oceans with basic and luxury resources.
# If you really piss them off, flood/destroy your capital and displace its population.

#civInfo.cities[0].cityStats.cityInfo.resistanceCounter
#CityFlags.Resistance

#civInfo.addNotification("Test", civInfo.cities[0].location, apiHelpers.Jvm.arrayOfString(["StatIcons/Gold"]))

#import random; [random.sample([*gameInfo.civilizations], 1)[0].placeUnitNearTile(t.position, random.sample(gameInfo.ruleSet.units.keys(), 1)[0]) for t in gameInfo.tileMap.values]

# import random; [civInfo.placeUnitNearTile(t.position, unitgetter()) for unitgetter in (lambda: random.sample(gameInfo.ruleSet.units.keys(), 1)[0], lambda: random.sample(('Missile Cruiser', 'Nuclear Submarine'), 1)[0], lambda: 'Worker', lambda: 'Guided Missile') for t in gameInfo.tileMap.values]; ([setattr(unit, 'action', random.sample(('Sleep', 'Automate', 'Fortify 0', 'Explore'), 1)[0]) for unit in apiHelpers.Jvm.toList(civInfo.getCivUnits())], civInfo.addGold(99999))

# [setattr(unit, 'action', 'Sleep') for unit in apiHelpers.Jvm.toList(civInfo.getIdleUnits())]

def moveCity():
	tileInfo.owningCity = city #Seems to be used for rendering only.
	#city.tiles.add(tile) Causes exception in worker thread in next turn.
	cities.tiles.add(tileInfo.position) #Requires next turn for visual update.
	tileInfo.improvement = None #Requires next turn for visual update.
	tileInfo.improvement = "City center"
	city.location.x, city.location.y  = x, y


def hammerOfTheOceans():
	pass


#TileMap.placeUnitNearTile


def onGameStart():
	pass

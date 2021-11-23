"""
Example automations to quickly do repetitive in-game tasks.

Currently enables actions that break game rules to be done.

IMO, that should be changed at the Kotlin side, by adding some kind of `PlayerAPI` class that unifies what the GUI is allowed to do with what the CLI is allowed to do.
"""

from unciv import *
from unciv_pyhelpers import *

def gatherBiggestCities(cities, ratio, stat='production'):
	"""Return the biggest cities from given cities that collectively comprise at least a given ratio of the total stats of all the cities. E.G: `gatherBiggestCities(civInfo.cities, 0.25, 'production')` will return a list of cities that together produce 25% of your empire's production."""
	assert 0 < ratio <= 1
	getstat = lambda c: getattr(c.cityStats.currentCityStats, stat)
	total = sum(getstat(c) for c in cities)
	inorder = sorted(cities, key=getstat, reverse=True)
	currsum = 0
	threshold = total*ratio
	selected = []
	for c in cities:
		if currsum >= threshold:
			break
		selected.append(c)
		currsum += getstat(c)
	assert sum(getstat(c) for c in cities) >= threshold
	print(f"Chose {len(selected)}/{len(inorder)} cities, representing {str(currsum/total*100)[:5]}% of total {total} {stat}.")
	return selected

def clearCitiesProduction(cities):
	"""Clear given cities of all queued and current production, and return the iterable of cities."""
	for i, city in enumerate(cities):
		city.cityConstructions.constructionQueue.clear()
	print(f"Cleared production from {i+1} cities.")
	return cities

def addCitiesProduction(cities, queue=()):
	"""Add given construction items to the construction queues of given cities, and return the iterable of cities."""
	for i, city in enumerate(cities):
		for build in queue:
			city.cityConstructions.addToQueue(build)
	print(f"Set {i+1} cities to build {queue}.")
	return cities

def clearCitiesSpecialists(cities):
	"""Unassign all specialist jobs in given cities."""
	for city in cities:
		city.population.specialistAllocations.clear()
	return cities

def focusCitiesFood(cities):
	"""Assign all unassigned population in given cities to focus on food production."""
	for city in cities:
		city.population.autoAssignPopulation(999)
	return cities

def buildCitiesQueue(cities, order):
	"""Assign all given cities to follow a given build order after their current queue."""
	for city in cities:
		with TokensAsWrappers(city.cityConstructions.getBuildableBuildings()) as queue:
			pass
		#apiHelpers.registeredObjects["x"] = city.cityConstructions.getBuildableBuildings()
		#civInfo.cities[0].cityStats.cityInfo.cityConstructions.builtBuildings # HashSet(). Can do "in" via IPC magic, and made real(). But not iterable since __iter__ requires indexing.

def rebaseUnitsEvenly(units=('Guided Missile',), ):
	if isinstance(units, str):
		units = (units,)

#import os, sys; sys.path.append(os.path.join(os.getcwd(), "../..")); from democomms import *

#from unciv_scripting_examples.PlayerMacros import *
#[real(c.name) for c in addCitiesProduction(clearCitiesProduction(gatherBiggestCities(civInfo.cities, 0.5)), ('Missile Cruiser', 'Nuclear Submarine', 'Mobile SAM'))]

#focusCitiesFood(clearCitiesSpecialists(civInfo.cities))

#apiHelpers.toString(worldScreen.bottomUnitTable.selectedCity.cityConstructions.getBuildableBuildings())

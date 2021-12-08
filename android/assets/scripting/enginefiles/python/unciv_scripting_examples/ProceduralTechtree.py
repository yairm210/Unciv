"""
Proof-of-concept and developmental test case for potential future modding API.

Adds functions to extend the tech tree indefinitely, with randomly generated new buildings, wonders, and units.


Call extendTechTree() at any time to add a new tier of technology.

Call clearTechTree() and then extendTechTree(iterations=20) at any time to replace all undiscovered parts of the base tech tree with an entire new randomly generated one.

Call scrambleTechTree() to keep all current technologies but randomize the order in which they are unlocked.
"""

# from unciv_scripting_examples.ProceduralTechtree import *
# from unciv_scripting_examples.ProceduralTechtree import *; scrambleTechTree()
# tuple(real(t) for t in apiHelpers.instancesAsInstances[gameInfo.ruleSet.technologies['Civil Service'].prerequisites.toArray()])

# This means that some kind of handler for the modding API, once it's implemented, would have to be called before the JVM has a chance to crash, so the script can read its own serialized data out of GameInfo and inject items into the ruleset… You can already provably have a GameInfo with invalid values that doesn't crash until WorldScreen tries to render it, so running onGameLoad immediately after deserializing the save might be good enough.
# Or I guess there could be a Kotlin-side mechanism for serializing injected rules. But on the Kotlin side, I think it would be cleaner to just let the script handle everything. Such a mechanism still may not cover the entire range of wild behaviours that can be done by scripts, and the entire point of having a dynamic scripting API is to avoid having to statically hard-code niche or esoteric uses.


import random

from unciv import *
from unciv_pyhelpers import *


def techtree(): return gameInfo.ruleSet.technologies

name_parts = {
	"Building": (
		("Old ", "Earth ", "Alien ", "Feedsite ", "Holo-", "Cel ", "Xeno ", "Xeno-" "Terra ", "Thorium ", "Xenofuel ", "Biofuel ", "Gaian ", "Field ", "Tidal ", "Cloning ", "Mass ", "Grow-", "Molecular ", "Nano-", "Civil ", "Cyto-", "Pharma-", "Gene ", "Bionics ", "Optical ", "Soma ", "Progenitor ", "Neuro-", "Organ ", "Hyper-", "Trade ", "Auto-", "Bio-", "Alloy ", "Dry-", "Repair ", "LEV ", "Microbial ", "Bore-", "Bioglass ", "Sky-", "Warp ", "Ultrasonic ", "Rocket ", "Defence ", "Surveillance ", "Command ", "Node ", "Mosaic ", "Sonar ", "Torpedo ", "Frontier ", "Drone ", "Launch ", "Mind ", "Neo-", "Voice ", "Pan-Spectral ", "Petrochemical ", "Thermohaline ", "Water ", "Xenomass "),
		("Relic", "Preserve", "Hub", "Suite", "Cradle", "Sanctuary", "Vault", "Reactor", "Plant", "Well", "Turbine", "Vivarium", "Digester", "Lab", "Forge", "Pasture", "Crèche", "Clinic", "Nursery", "Garden", "Smelter", "Surgery", "Distillery", "Laboratory", "Observatory", "Network", "Institute", "Printer", "Mantle", "Core", "Depot", "Recycler", "Factory", "Foundry", "Dock", "Facility", "Mine", "Hole", "Furnace", "Crane", "Spire", "Fence", "Battery", "Perimeter", "Web", "Center", "Bank", "Hull", "Net", "Stadium", "Augmentery", "Command", "Complex", "Stem", "Planetarium", "Archives", "Rudder", "Refinery", "House")
	),
	"Unit": (
		("Combat ", "Missile ", "Patrol ", "Gun-", "Tac-", "Xeno ", "Rock-", "Battle-", "LEV ", "Drone ", "Auto-", "Nano-", "Gelio-", "All-", "Laser-", "Phasal ", "Solar ", "Wolf ", "Raptor ", "Siege ", "Sea ", "Hydra-", "Tide-", "Under-", "Needle-", "Evolved ", "True ", "Prime ", "First ", "Master ", "Elder "),
		("Explorer", "Soldier", "Ranger", "Rover", "Boat", "Submarine", "Carrier", "Jet", "Swarm", "Cavalry", "Octopus", "Titan", "Suit", "Aegis", "Tank", "Destroyer", "CNDR", "CARVR", "SABR", "ANGEL", "Immortal", "Architect", "Throne", "Cage", "Sled", "Golem", "Hive", "Pod", "Aquilon", "Seer", "Matrix", "Laser", "Carver", "Siren", "Batle", "Bug", "Worm", "Drones", "Manticore", "Dragon", "Kraken", "Coral", "Makara", "Ripper", "Scarab", "Marine", "Brawler", "Sentinel", "Disciple", "Maurauder", "Centurion", "Apostle", "Champion", "Eidolon", "Hellion", "Striker", "Guardian", "Overseer", "Shredder", "Warden", "Executor", "Kodiak", "Virtuoso", "Fury", "Armor", "Viper", "Lancer", "Prophet", "Cobra", "Dragoon", "Redeemer", "Gladiator", "Maestro", "Savage", "Artillery", "Centaur", "Punisher", "Educator", "Minotaur", "Devastator", "Ambassador", "Cutter", "Screamer", "Broadside", "Tenet", "Reaver", "Cannonade", "Edict", "Argo", "Baron", "Vortex", "Cruiser", "Triton", "Destroyer", "Arbiter", "Poseidon", "Dreadnought", "Vindicator", "Mako", "Countess", "Wrath", "Hunter", "Lurker", "Taker", "Whisper", "Leviathan", "Eradicator", "Shroud", "Hydra", "Bastion", "Shepherd", "Locust", "Raider", "Herald", "Shrike", "Predator", "Seraph")
	),
	"Wonder": (
		("Spy ", "Culper ", "Tessellation ", "Machine-Assisted ", "Dimensional ", "Folding ", "Dimensional Folding ", "Quantum ", "Temporal ", "Relativistic ", "Abyssal ", "Archimedes ", "Arma-", "Benthic ", "Byte-", "Daedaleus ", "Deep ", "Drone ", "Ecto-", "Genesis ", "Euphotic ", "Faraday ", "Gene ", "Guo Pu ", "Holon ", "Human ", "Markov ", "Mass ", "Master ", "Memet-", "Nano-", "New Terran ", "Pan-", "Precog ", "Promethean ", "Quantum ", "Resurrection ", "Stellar ", "Tectonic ", "The ", "Xeno-", "Emancipation ", "Exodus ", "Mind ", "Transcendental ", "Decode "),
		("Mirror", "Ansible", "Lever", "Sail", "Auger", "Geist", "Crawler", "Cynosure", "Ladder", "Memory", "Sphere", "Pod", "Genesis Pod", "Strand", "Gyre", "Vault", "Yaolan", "Chamber", "Hive", "Eclipse", "Driver", "Control", "Work", "Thermite", "Myth", "Opticon", "Project", "Promethean", "Computer", "Device", "Codex", "Anvil", "Akkorokamui", "Drome", "Malleum", "Nova", "Gate", "Flower", "Equation", "Signal", "Beacon")
	)
}


usedNames = set()

def genRandomName(nametype): #Mix and match from BE.
	prefixes, suffixes = name_parts[nametype]
	prefix, suffix = random.sample(prefixes, 1)[0], random.sample(suffixes, 1)[0]
	return prefix[:-1]+suffix[0].lower()+suffix[1:] if prefix[-1] == "-" else prefix+suffix
	# Could make first letter fit second letter's capitalization.

def genRandomNameUnused(nametype, *, maxattempts=1000):
	for i in range(maxattempts):
		name = genRandomName(nametype)
		if name not in usedNames:
			break
	else:
		raise Exception()
	usedNames.add(name)
	return name

def genRandomIcon():
	pass #Define randomized number of randomized shapes. Randomize order. Draw area union. Draw layer-occluded boundary lines separately. Subtract edges from area.



# def genRandomEra():
	# pass

def genRandomUnit():
	pass

def genRandomBuildingUnique():
	# See replaceExamples in UniqueDocsWriter.kt.
	pass

def genRandomBuildingStats(name, totalstats, statsskew, numuniques):
	assert 0 <= statsskew <= 1
	for x in x:
		x *= 1+(random.random()*2-1)*statsskew

def genRandomBuilding():
	pass

def genRandomWonder():
	pass


def genRandomTech(column, row):
	connections = random.sample((0, 0, -2, -1, 1, 2), random.randint(1,3))


def _getInvalidTechs():
	pass


#t=gameInfo.ruleSet.technologies['Mining']; t.row -= 1; t.column.techs.remove(t); b=gameInfo.ruleSet.technologies['Masonry']; b.column.techs.add(t); t.column=b.column


def extendTechTree(iterations=1):
	raise NotImplementedError()
	pass

def clearTechTree(*, safe=True):
	"""Clear all items on the tech tree that haven't yet been researched by any civilizations. Pass safe=False to also clear technologies that have already been researched."""
	for name in techtree().keys():
		if (not safe) or not any(name in civinfo.tech.techsResearched or name in civinfo.tech.techsInProgress or name == civinfo.tech.currentTechnologyName() for civinfo in gameInfo.civilizations):
			del techtree()[name]

def scrambleTechTree():
	"""Randomly shuffle the order of all items on the tech tree."""
	technames = [*techtree().keys()]
	random.shuffle(technames)
	techpositions = {n:n for n in technames}
	originalpreqs = {n:tuple(real(tname) for tname in apiHelpers.instancesAsInstances[techtree()[n].prerequisites.toArray()]) for n in technames} # .prerequisites is a HashSet that becomes inaccessible with always-tokenizing serialization.
	for tname in technames:
		oname = random.sample(technames, 1)[0]
		tech, other = (techtree()[n] for n in (tname, oname))
		for t in (tech, other):
			t.column.techs.remove(t)
		tech.column, other.column = real(other.column), real(tech.column)
		for t in (tech, other):
			t.column.techs.add(t)
		tech.row, other.row = real(other.row), real(tech.row)
		techpositions[tname], techpositions[oname] = techpositions[oname], techpositions[tname]
	techreplacements = {v:k for k, v in techpositions.items()}
	assert len(techreplacements) == len(techpositions)
	for tname in technames:
		tech = techtree()[tname]
		tech.prerequisites.clear()
		for ot in originalpreqs[techpositions[tname]]:
			try: techreplacements[ot]
			except: print(tname, ot)
		tech.prerequisites.addAll([techreplacements[ot] for ot in originalpreqs[techpositions[tname]]])
		# toprereqs, oprereqs = (real(t.prerequisites) for t in (tech, other))

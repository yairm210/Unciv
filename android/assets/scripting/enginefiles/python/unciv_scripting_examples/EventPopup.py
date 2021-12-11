"""
Examples for scripting simple, Paradox-style/Beyond Earth-style event popups.

"""

#from unciv_scripting_examples.EventPopup import *; r=showEventPopup(**EVENT_POPUP_DEMOARGS())

# modApiHelpers.lambdifyReadPathcode(None, 'apiHelpers.Jvm.constructorByQualname["com.unciv.ui.utils.ToastPopup"]("Test", uncivGame.getScreen(), 8000)')

#apiHelpers.Jvm.constructorByQualname["com.unciv.ui.utils.ToastPopup"]("Test", uncivGame.getScreen(), 8000)

#from . import Utils

from unciv import *

Constructors = apiHelpers.Jvm.constructorByQualname
ExtensionFunctions = apiHelpers.Jvm.functionByQualClassAndName['com.unciv.ui.utils.ExtensionFunctionsKt']
Singletons = apiHelpers.Jvm.singletonByQualname
Constants = Singletons['com.unciv.Constants'] # With bind-by-reference, doing fewer key and attribute accesses by doing them earlier should actually be faster.

def showPopup():
	p = Constructors['com.unciv.ui.utils.Popup'](uncivGame.getScreen())
	p.add(ExtensionFunctions['toLabel']("Test Text.")).row()
	p.add(ExtensionFunctions['toTextButton']("Test Button.")).row()
	closebutton = ExtensionFunctions['toTextButton'](Constants.close)
	ExtensionFunctions['onClick'](
		closebutton,
		modApiHelpers.lambdifyReadPathcode(p, '.close()')
	)
	p.add(closebutton)
	p.open(False)


def showPopup2():
	p = Constructors['com.unciv.ui.utils.Popup'](uncivGame.getScreen())
	p.add(ExtensionFunctions['toLabel']("Test Text.")).row()
	p.add(ExtensionFunctions['toTextButton']("Test Button.")).row()
	closebutton = ExtensionFunctions['toTextButton'](Constants.close)
	ExtensionFunctions['onClick'](
		closebutton,
		modApiHelpers.lambdifyReadPathcode(p, '.close()')
	)
	p.add(closebutton)
	p.open(False)
	pass # Make button show toast and do something else.


Companions = apiHelpers.Jvm.companionByQualClass
Enums = apiHelpers.Jvm.enumMapsByQualname
GdxColours = apiHelpers.Jvm.staticPropertyByQualClassAndName['com.badlogic.gdx.graphics.Color']
#StatColours = TODO
Fonts = Singletons['com.unciv.ui.utils.Fonts']


import math
from unciv_pyhelpers import *


def showEventPopup(title=None, image=None, text="No text event text provided!", options={}):
	assert apiHelpers.isInGame
	# uncivGame.getScreen().stage.width
	defaultcolour = GdxColours['WHITE']
	popup = Constructors['com.unciv.ui.utils.Popup'](uncivGame.getScreen())
	closeaction = modApiHelpers.lambdifyReadPathcode(popup, '.close()')
	if title is not None:
		popup.addGoodSizedLabel(title, 24).row()
		popup.addSeparator().row()
	popup.addGoodSizedLabel(text, 18).row()
	for labels, clickaction in options.items():
		button = Constructors['com.badlogic.gdx.scenes.scene2d.ui.Button'](Companions['com.unciv.ui.utils.BaseScreen'].skin)
		if isinstance(labels, str):
			labels = (labels,)
		elif isinstance(labels[0], str):
			labels = (labels,)
		for label in labels:
			buttontext, buttoncolour = (label, None) if isinstance(label, str) else label
			buttonlabel = ExtensionFunctions['toLabel'](buttontext, real(buttoncolour) or defaultcolour, 18)
			button.add(buttonlabel).row()
		ExtensionFunctions['onClick'](
			button,
			modApiHelpers.lambdifyCombine([
				*((clickaction,) if real(clickaction) else ()),
				closeaction
			])
		)
		popup.add(button).row()
	popup.open(False)
	return {**locals()}


def EVENT_POPUP_DEMOARGS():
	stats = civInfo.statsForNextTurn
	goldboost, cultureboost, scienceboost = int(50+stats.gold*10), int(50+stats.culture*10), int(50+stats.science*10)
	omniboost = 70 + (goldboost+cultureboost+scienceboost) // 2
	omniresistance = 20
	resistanceFlag = Enums["com.unciv.logic.city.CityFlags"]["Resistance"]
	cities = tuple(civInfo.cities)
	omniproductionboosts = tuple(int(real(min(production*10, max(production, cityconstructions.getRemainingWork(cityconstructions.getCurrentConstruction().name, True)+1)))) for city in cities for production, cityconstructions in [(city.cityStats.currentCityStats.production, city.cityConstructions)])
	return {
		'title': "Something has happened in your empire!",
		'image': "Generic And Dramatic Artwork!", # TODO # Note: Recommended method for mods is to ship file as internal image— Asynchronous/callback load from placeholder website?
		'text': """A societally and politically significant event has occurred in your empire!

A political factor has been invisibly building up over the last ten turns or so of gameplay, and it has finally reached a tipping point where we think it will be narratively compelling! Because of the old way things were, things happened. Because things happened, things have changed, and now things have to change some more. From now on, the new way your empire is will be different from the old way it was before!

Things can change in different ways. If we do one thing, things can change. If we do another thing, things can also change.

This is your chance to roleplay a political decision:
""",
		'options': { # TODO: Serialize Chars as string?
			(f"I'll take a Gold stat bonus. (+{goldboost} {real(Fonts.gold.toString())})", GdxColours['GOLD']):
				modApiHelpers.lambdifyReadPathcode(civInfo, f'.addGold({goldboost})'), # Can actually just read addGold for this.
			(f"I'll take a Culture stat bonus. (+{cultureboost} {real(Fonts.culture.toString())})", GdxColours['VIOLET']):
				modApiHelpers.lambdifyReadPathcode(civInfo, f'.policies.addCulture({cultureboost})'),
			(f"I'll take a Science stat bonus. (+{scienceboost} {real(Fonts.science.toString())})", GdxColours['CYAN']):
				modApiHelpers.lambdifyReadPathcode(civInfo, f'.tech.addScience({scienceboost})'),
			(
				(f"Let Chaos reign! (+{omniboost} {real(Fonts.gold.toString())}, {real(Fonts.culture.toString())}, {real(Fonts.science.toString())})", None),
				(f"(+{sum(omniproductionboosts)} {real(Fonts.production.toString())} spread across all your cities.)", None),
				(f"All cities enter resistance for +{omniresistance} turns.", GdxColours['SCARLET'])
			):
				modApiHelpers.lambdifyCombine([
					modApiHelpers.lambdifyReadPathcode(civInfo, f'.addGold({omniboost})'),
					modApiHelpers.lambdifyReadPathcode(civInfo, f'.policies.addCulture({omniboost})'),
					modApiHelpers.lambdifyReadPathcode(civInfo, f'.tech.addScience({omniboost})'),
					*(
						modApiHelpers.lambdifyReadPathcode(None, f'civInfo.cities[{i}].cityConstructions.addProductionPoints({p})') # Will be a wrong result if somthing else changes the resistance turns between the popup being spawned and being shown.
						for i, p in enumerate(omniproductionboosts)
					),
					*(
						modApiHelpers.lambdifyReadPathcode(None, f'''civInfo.cities[{
							i
						}].setFlag(apiHelpers.Jvm.enumMapsByQualname["com.unciv.logic.city.CityFlags"]["Resistance"], {
							int(
								(omniresistance + city.getFlag(resistanceFlag))
									if city.hasFlag(resistanceFlag) else
								omniresistance
							)
						})''') # Will be a wrong result if somthing else changes the resistance turns between the popup being spawned and being shown.
						for i, city in enumerate(civInfo.cities)
					),
					modApiHelpers.lambdifyReadPathcode(None, 'civInfo.addNotification("Your empire is FURIOUS!!!\nWhat did you even do???", civInfo.cities[0].location, apiHelpers.Jvm.arrayOfTyped1("StatIcons/Resistance"))')
				]),
			"Nah. I'm good.":
				None
		}
	}


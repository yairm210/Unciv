"""
Examples for scripting simple, Paradox-style event popups.

"""

#get apiHelpers.instancesAsInstances[apiHelpers.Jvm.constructorByQualname["com.unciv.ui.utils.Popup"](uncivGame.getScreen())].open(False)

#Constructors=apiHelpers.Jvm.constructorByQualname; ExtensionFunctions=apiHelpers.Jvm.functionByQualClassAndName["com.unciv.ui.utils.ExtensionFunctionsKt"]; Singletons=apiHelpers.Jvm.kotlinSingletonByQualname; p=Constructors["com.unciv.ui.utils.Popup"](uncivGame.getScreen()); p.add(ExtensionFunctions["toLabel"]("Test Text.")).row(); p.add(ExtensionFunctions["toTextButton"]("Test Button.")).row(); closebutton=ExtensionFunctions["toTextButton"](Singletons["com.unciv.Constants"].close); ExtensionFunctions["onClick"](closebutton, modApiHelpers.lambdifyIgnoreExceptions(modApiHelpers.lambdifyPathcode(p, ".close()"))); p.add(closebutton); p.open(False)

#from unciv_scripting_examples.EventPopup import *

#from . import Utils


from unciv import *

Constructors = apiHelpers.Jvm.constructorByQualname
ExtensionFunctions = apiHelpers.Jvm.functionByQualClassAndName["com.unciv.ui.utils.ExtensionFunctionsKt"]
Singletons = apiHelpers.Jvm.kotlinSingletonByQualname

def showPopup():
	p = Constructors["com.unciv.ui.utils.Popup"](uncivGame.getScreen())
	p.add(ExtensionFunctions["toLabel"]("Test Text.")).row()
	p.add(ExtensionFunctions["toTextButton"]("Test Button.")).row()
	closebutton = ExtensionFunctions["toTextButton"](Singletons["com.unciv.Constants"].close)
	ExtensionFunctions["onClick"](
		closebutton,
		modApiHelpers.lambdifyIgnoreExceptions(
			modApiHelpers.lambdifyReadPathcode(p, ".close()")
		)
	)
	p.add(closebutton)
	p.open(False)


def showPopup2():
	pass # Make button show toast and do something else.

def showEventPopup(title=None, image=None, text=None, options=None):
	pass

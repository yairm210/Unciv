# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)

## Triggerable uniques（Desencadenable）
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Treu [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Ven el(s) edifici(s) [buildingFilter] [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	/ Apareix un [unit] de franc

	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[positiveAmount] free [unit] units appear"
	/ Apareixen [positiveAmount] unitats [unit] de franc

	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "A [unit] rebels"
	/ Una unitat de [unit] es rebel·la

	Example: "A [Musketman] rebels"

	Applicable to: Triggerable

??? example  "[positiveAmount] [unit]s rebel"
	/ [positiveAmount] unitats de [unit] es rebel·len 

	Example: "[3] [Musketman]s rebel"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	/ Política social de franc

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Social Policies"
	/ [positiveAmount] polítiques socials gratuïtes

	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	/ La civilització entra en una edat d’or

	Applicable to: Triggerable

??? example  "Empire enters a [positiveAmount]-turn Golden Age"
	/ L’Imperi entra en una edat d’or durant [positiveAmount] torns

	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	/ Gran personatge de franc

	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	/ [amount] de població [cityFilter]

	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	/ [amount] de població en una ciutat aleatòria

	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	/ Descobreix [tech]

	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy/belief]"
	/ Adopta [policy/belief]

	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter]"
	/ Trau [policyFilter]

	Example: "Remove [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter] and refund [amount]% of its cost"
	/ Trau [policyFilter] i retorna un [amount] % del seu cost.

	Example: "Remove [Oligarchy] and refund [3]% of its cost"

	Applicable to: Triggerable

??? example  "Free Technology"
	/ Tecnologia de franc

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Technologies"
	/ [positiveAmount] tecnologies de franc

	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[positiveAmount] free random researchable Tech(s) from the [eraFilter]"
	/ [positiveAmount] tecnologies aleatòries de franc de l’[eraFilter]

	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	/ Revela el mapa sencer.

	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	/ Obtén una creença [beliefType]

	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	/ Desencadena la votació de victòria diplomàtica

	Applicable to: Triggerable

??? example  "Instantly consumes [positiveAmount] [stockpiledResource]"
	/ Consumeix [positiveAmount] [stockpiledResource] a l’instant

	Example: "Instantly consumes [3] [Mana]"

	Applicable to: Triggerable

??? example  "Instantly provides [positiveAmount] [stockpiledResource]"
	/ Proveeix [positiveAmount] [stockpiledResource] a l’instant

	Example: "Instantly provides [3] [Mana]"

	Applicable to: Triggerable

??? example  "Set [stockpile] to [countable]"
	/ Estableix [stockpile] a [countable]

	Example: "Set [Mana] to [1000]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Instantly gain [amount] [stockpile]"
	/ Guanya [amount] [stockpile] instantàniament

	Example: "Instantly gain [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	/ Guanya [amount] [stat]

	Example: "Gain [3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	/ Guanya prou fe per un panteó

	Applicable to: Triggerable

??? example  "Gain enough Faith for [positiveAmount]% of a Great Prophet"
	/ Guanya prou fe per [positiveAmount] % d’un gran profeta

	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Research [relativeAmount]% of [tech]"
	/ Recerca [relativeAmount] % de [tech]

	Example: "Research [+20]% of [Agriculture]"

	Applicable to: Triggerable

??? example  "Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius"
	/ Obté el control de caselles [tileFilter] en un radi de [nonNegativeAmount] caselles

	Example: "Gain control over [Farm] tiles in a [3]-tile radius"

	Applicable to: Triggerable

??? example  "Gain control over [positiveAmount] tiles [cityFilter]"
	/ Obtén el control de [positiveAmount] caselles [cityFilter]

	Example: "Gain control over [3] tiles [in all cities]"

	Applicable to: Triggerable

??? example  "Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius"
	/ Revela fins a [positiveAmount/'all'] [tileFilter] dins d’un radi de caselles de [positiveAmount].

	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	/ Provoca la següent alerta global: [comment]

	Supported on Policies and Technologies.

	For other targets, the generated Notification may not read nicely, and will likely not support translation. Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_, and that difference is currently handled by mapping text.

	Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts.

	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "Promotes all spies [positiveAmount] time(s)"
	/ Ascendeix tots els espies [positiveAmount] vegada/es

	Example: "Promotes all spies [3] time(s)"

	Applicable to: Triggerable

??? example  "Gain an extra spy"
	/ Obtén un espia addicional

	Applicable to: Triggerable

??? example  "Turn this tile into a [terrainName] tile"
	/ Converteix en [terrainName] aquesta casella

	Example: "Turn this tile into a [Forest] tile"

	Applicable to: Triggerable

??? example  "Remove [resourceFilter] resources from this tile"
	/ Trau els recursos [resourceFilter] d’aquesta casella

	Example: "Remove [Strategic] resources from this tile"

	Applicable to: Triggerable

??? example  "Remove [improvementFilter] improvements from this tile"
	/ Trau les millores [improvementFilter] d’aquesta casella

	Example: "Remove [All Road] improvements from this tile"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Works only with promotions that are valid for the unit's type - or for promotions that do not specify any.

	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [positiveAmount] cities for free"
	/ Aconsegueix de franc l’edifici [stat] més barat en les primeres [positiveAmount] ciutats de la civilització.

	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [positiveAmount] cities for free"
	/ S’obté un [buildingName] en les primeres [positiveAmount] ciutats de la civilització.

	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Triggers a [event] event"
	/ Dispara un esdeveniment [event]

	Example: "Triggers a [Inspiration] event"

	Applicable to: Triggerable

??? example  "Mark tutorial [comment] complete"
	Example: "Mark tutorial [comment] complete"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable

??? example  "Play [comment] sound"
	See [Images and Audio](Images-and-Audio.md#sounds) for a list of available sounds.

	Example: "Play [comment] sound"

	This unique is automatically hidden from users.

	Applicable to: Triggerable

??? example  "Get the leader title of [leaderTitle]"
	Example: "Get the leader title of [Sovereign [leaderName] the Great]"

	This unique is automatically hidden from users.

	Applicable to: Triggerable

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

## UnitTriggerable uniques（Unitat amb efecte desencadenable）
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "[unitTriggerTarget] heals [positiveAmount] HP"
	/ [unitTriggerTarget] es cura [positiveAmount] PV

	Example: "[This Unit] heals [3] HP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] takes [positiveAmount] damage"
	/ [unitTriggerTarget] rep [positiveAmount] punts de dany

	Example: "[This Unit] takes [3] damage"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [amount] XP"
	/ [unitTriggerTarget] obté [amount] punts d’experiència

	Example: "[This Unit] gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free"
	/ [unitTriggerTarget] es millora gratis

	Example: "[This Unit] upgrades for free"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free including special upgrades"
	/ [unitTriggerTarget] es millora gratis, incloent millores especials

	Example: "[This Unit] upgrades for free including special upgrades"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] promotion"
	/ [unitTriggerTarget] obté l’ascens [promotion]

	Example: "[This Unit] gains the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] promotion"
	/ [unitTriggerTarget] perd l’ascens [promotion]

	Example: "[This Unit] loses the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [positiveAmount] movement"
	/ [unitTriggerTarget] guanya [positiveAmount] de moviment

	Example: "[This Unit] gains [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses [positiveAmount] movement"
	/ [unitTriggerTarget] perd [positiveAmount] de moviment

	Example: "[This Unit] loses [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)"
	/ [unitTriggerTarget] obté l’estat [promotion] durant [positiveAmount] torn(s)

	Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns.

	Example: "[This Unit] gains the [Shock I] status for [3] turn(s)"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] status"
	/ [unitTriggerTarget] perd l’estat [promotion]

	Example: "[This Unit] loses the [Shock I] status"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] is destroyed"
	/ [unitTriggerTarget] ha estat destruït/da

	Example: "[This Unit] is destroyed"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gets a name from the [unitNameGroup] group"
	/ [unitTriggerTarget] rep el nom del grup [unitNameGroup]

	Example: "[This Unit] gets a name from the [Scientist] group"

	Applicable to: UnitTriggerable

## Global uniques
!!! note ""

    Uniques that apply globally. Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.

??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] de cada especialista [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] de població [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] social policies adopted"
	/ [stats] per cada [positiveAmount] polítiques socials adoptades

	Only works for civ-wide stats

	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [positiveAmount] [civWideStat]"
	/ [stats] per cada [positiveAmount] [civWideStat]

	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] en ciutats en caselles [terrainFilter]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] de tots els edificis [buildingFilter]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] de caselles de [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] per cada ruta comercial

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	/ [relativeAmount] % [stat]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	/ [relativeAmount] % [stat] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount] % [stat] de cada [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount] % de rendiment de cada [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	/ [relativeAmount] % [stat] de les ciutats-estat

	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	/ [relativeAmount] % [stat] de les rutes comercials

	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	/ Anul·la [stat] [cityFilter].

	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	/ Anul·la el creixement de [cityFilter].

	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] % de producció quan es construeixen edificis [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount] % de producció quan construeix unitats de tipus «[baseUnitFilter]» [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount] % de producció quan es construeixen meravelles [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ Qualsevol edifici que ja estigui a la capital rep una producció de [relativeAmount] %.

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount] % de producció per saquejar caselles

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount] % de salut per saquejar caselles

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "Military Units gifted from City-States start with [positiveAmount] XP"
	/ Les unitats militars que donin les ciutats-estats comencen amb [positiveAmount] d’experiència.

	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation"
	/ Les ciutats-estats militars donen unitats [positiveAmount] vegades més ràpid quan esteu en guerra amb una nació en comú.

	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	/ Els regals d’or a les ciutats-estat generen [relativeAmount] % més d’influència.

	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns"
	/ Pot gastar or per a annexionar o convertir en titella una ciutat-estat que hagi estat la vostra aliada durant [nonNegativeAmount] torns.

	Example: "Can spend Gold to annex or puppet a City-State that has been your Ally for [3] turns"

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	/ El territori de les ciutats-estat es considera sempre territori amic.

	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	/ Les ciutats-estat aliades regalaran grans personatges ocasionalment.

	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	/ [relativeAmount] % de degradació de la influència en ciutats-estat

	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	/ Els punts de descans per influència amb ciutats-estat augmenta en [amount].

	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	/ Les ciutats-estat aliades proporcionen [stat] igual al [relativeAmount] % del que produeixen per elles mateixes.

	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	/ Els regals de les ciutats estat augment en [relativeAmount] %.

	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	/ La felicitat per recursos de luxe donats per les ciutats estat augmenta un [relativeAmount] %.

	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	/ Es duplica la recuperació d’influència a les ciutats-estat.

	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount] % de creixement [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ Es conserva un [amount] % de menjar després d’augmentar la població [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount] % de consum de menjar de [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	/ [relativeAmount] % d’insatisfacció pel nombre de ciutats

	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount] % d’insatisfacció de [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	/ [amount] de felicitat de cada recurs de luxe

	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	/ Manté [relativeAmount] % de la felicitat de l’última còpia d’un recurs de luxe quan es comerciï amb ell.

	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	/ Es converteix un [relativeAmount] % més de l’excedent de felicitat a [stat].

	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	/ Les unitats [baseUnitFilter] no es poden construir.

	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	/ Permet la construcció de parts de naus espacials

	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Es poden comprar unitats «[baseUnitFilter]» per [amount] [stat] [cityFilter] amb un cost creixent ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Pot comprar edificis [buildingFilter] per [amount] [stat] [cityFilter] a un preu creixent ([nonNegativeAmount]).

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Pot comprar unitats [baseUnitFilter] per [nonNegativeAmount] [stat] [cityFilter].

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Pot comprar edificis [buildingFilter] per [nonNegativeAmount] [stat] [cityFilter].

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Pot comprar unitats [baseUnitFilter] amb [stat] [cityFilter].

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Els edificis «[buildingFilter]» es poden comprar amb [stat] [cityFilter].

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Es poden comprar unitats [baseUnitFilter] amb [stat] per [nonNegativeAmount] vegades el seu cost normal de producció.

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Pot comprar edificis [buildingFilter] amb [stat] per [nonNegativeAmount] vegades el seu cost normal de producció.

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ El preu en [stat] de compra en ciutats es modifica un [relativeAmount] %

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat] cost de compra d’edificis [buildingFilter] [relativeAmount] %.

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat] de cost de compra d’unitats [baseUnitFilter] [relativeAmount] %

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	/ Permet la conversió de la producció de la ciutat a [civWideStat].

	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	/ Millora la velocitat de moviment a les caselles amb carretera.

	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	/ Les carreteres també connecten caselles a través dels rius.

	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	/ [relativeAmount] % de manteniment de carreteres i ferrocarrils

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	/ Les millores en caselles [tileFilter] no tenen costos de manteniment.

	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount] % de temps de construcció per a millores [improvementFilter]

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Pot construir millores [improvementFilter] a un ritme [relativeAmount] %

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] % de cost de manteniment per als edificis [buildingFilter] [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Treu [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Ven el(s) edifici(s) [buildingFilter] [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount] % de cost cultural per al creixement de frontera natural [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount] % de cost d’or per adquirir caselles [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	/ Cada ciutat fundada augmenta el cost de cultura de les polítiques un [relativeAmount] % menys del normal.

	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	/ [relativeAmount] % de cost de cultura per adoptar polítiques noves

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "Each city founded increases Science cost of Technologies [relativeAmount]% less than normal"
	/ Cada ciutat fundada augmenta el cost en ciència de les tecnologies un [relativeAmount] % menys del normal

	Example: "Each city founded increases Science cost of Technologies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Science cost of researching new Technologies"
	/ [relativeAmount] % en cost de ciència per la recerca de tecnologies

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Science cost of researching new Technologies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	/ [stats] de cada meravella natural coneguda

	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)"
	Example: "[+1 Gold, +2 Production] for discovering a Natural Wonder (bonus enhanced to [+1 Gold, +2 Production] if first to discover it)"

	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount] % de generació de grans personatges [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount] % d’or de les missions comercials dels grans mercaders.

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Es dupliquen les bonificacions de combat que proporcionen els grans generals.

	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	/ Després de recercar [tech], rebeu un gran personatge de fran a l’inici de cada [comment] (cada 394 anys). Cada tipus de personatge només es pot triar una vegada.

	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	/ Un cop s’ha activat el compte llarg, l’any de la vista de pantalla mostra l’any del compte llarg maia.

	Applicable to: Global

??? example  "[amount] Unit Supply"
	/ [amount] subministraments d’unitats

	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [positiveAmount] population [cityFilter]"
	/ [amount] subministraments d’unitats per [positiveAmount] de població [cityFilter]

	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	/ [amount] subministraments d’unitats per ciutat

	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	/ [amount] unitats no costen manteniment.

	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	/ No es paga manteniment per les unitats que estan les ciutats.

	Applicable to: Global

??? example  "Enables embarkation for land units"
	/ Permet embarcar unitats terrestres

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	/ Permet entrar a caselles d’oceà a les unitats [mapUnitFilter]

	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	/ Les unitats terrestres poden creuar caselles de [terrainName] després que s’obtingui el primer [baseUnitFilter].

	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory"
	/ Les unitats de tipus «[mapUnitFilter]» enemigues han de fer servir [positiveAmount] punts de moviment addicionals quan estan a dins del vostre territori

	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Les unitats [baseUnitFilter] noves comencen amb [amount] PE [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Totes les noves unitats entrenades de tipus «[baseUnitFilter]» [cityFilter] reben l’ascens [promotion]

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ Les unitats [mapUnitFilter] adjacents a aquesta ciutat guanyen [amount] de vida per torn quan es curen.

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% XP required for promotions"
	/ [relativeAmount] % de PE per a aconseguir ascensos

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP required for promotions"

	Applicable to: Global

??? example  "[relativeAmount]% City Strength from defensive buildings"
	/ [relativeAmount] % de força de ciutat dels edificis defensius

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount] % de força per les ciutats

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Proporciona [amount] [resource].

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% [resourceFilter] resource production"
	/ [relativeAmount] % de producció de recursos [resourceFilter]

	Example: "[+20]% [Strategic] resource production"

	Applicable to: Global

??? example  "Enables establishment of embassies"
	/ Permet establir ambaixades

	Applicable to: Global

??? example  "Requires establishing embassies to conduct advanced diplomacy"
	/ Requereix que s’estableixin ambaixades per a diplomàcia avançada

	Applicable to: Global

??? example  "Enables Open Borders agreements"
	/ Permet acords de fronteres obertes

	Applicable to: Global

??? example  "Enables Research agreements"
	/ Permet acords de recerca

	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	/ [relativeAmount] % de ciència obtinguda per acords de recerca

	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	/ Habilita els pactes de defensa

	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	/ Quan es fa una declaració d’amistat, ambdues parts aconsegueixen [relativeAmount] % per la generació de grans personatges.

	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	/ La influència de totes les altres civilitzacions amb totes les ciutats-estat es degrada [relativeAmount] % més de pressa.

	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	/ Guanya [amount] d’influència donant a una ciutat-estat un [baseUnitFilter].

	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	/ [amount] punts de descans per a la influència de ciutats-estat que segueixen aquesta religió

	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	/ Se us avisarà quan hi hagi nous campaments bàrbars.

	Applicable to: Global

??? example  "Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities"
	/ Rep [relativeAmount] % d’or dels campaments bàrbars i saquejos de ciutats.

	Example: "Receive [+20]% Gold from Barbarian encampments and pillaging Cities"

	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	/ Quan es conquereix un campament, es guanya [amount] d’or i es recluta una unitat bàrbara

	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	/ Quan es derrota una unitat [mapUnitFilter], s’aconsegueix [amount] d’or i reclutar la unitat.

	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	/ Pot triar [amount] creences [beliefType] addicionals quan es [foundingOrEnhancing] una religió.

	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	/ Pot triar [amount] creences addicionals de qualssevol tipus quan [foundingOrEnhancing] una religió.

	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	/ [stats] quan una ciutat adopta aquesta religió per primera vegada

	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount] % de difusió natural de la religió [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ La religió es difon de manera natural a ciutats que estiguin fins a [amount] caselles de distància.

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	/ No es generaran equivalents dels grans profetes de manera natural.

	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	/ [relativeAmount] % de punts de fe per generar equivalents de grans profetes

	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "[relativeAmount]% spy effectiveness [cityFilter]"
	/ [relativeAmount] % d’efectivitat dels espies en [cityFilter]

	Example: "[+20]% spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% enemy spy effectiveness [cityFilter]"
	/ [relativeAmount] % d’efectivitat dels espies enemics en [cityFilter]

	Example: "[+20]% enemy spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "New spies start with [amount] level(s)"
	/ Els espies nous apareixen amb nivell [amount]

	Example: "New spies start with [3] level(s)"

	Applicable to: Global

??? example  "Triggers victory"
	/ Desencadena victòria

	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	/ Desencadena la victòria cultural quan es completa

	Applicable to: Global

??? example  "May buy items in puppet cities"
	/ Pot comprar elements en ciutats titella

	Applicable to: Global

??? example  "May not annex cities"
	/ No pot annexionar ciutats.

	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	/ Agafa «prestats» els noms de les seves ciutats d’altres civilitzacions.

	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	/ Les ciutats són arrasades [amount] vegades més ràpid.

	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	/ Quan es construeixen edificis científics i meravelles a la capital, la recerca tecnològica rep un impuls.

	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	/ La durada de les edats d’or augmenta un [relativeAmount] %

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	/ [relativeAmount] % [cityFilter] per la pèrdua de població per atacs nuclears.

	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	/ [relativeAmount] % [cityFilter] de dany a la guarnició per atacs nuclears

	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	/ Poden aparèixer unitats rebels.

	Applicable to: Global

??? example  "Cannot build [buildingFilter] buildings"
	Example: "Cannot build [Culture] buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount] % de força

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] de força

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount] % de força, percentatge que es redueix conforme augmenta la distància a la capital.

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount] % de bonificació a l’atac per flanqueig

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] atacs addicionals per torn

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] de moviment

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] de visió

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ Abast [amount]

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] d’abast d’intercepció aèrea

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] de vida quan es cura

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount] % de força de difusió de la religió

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Quan es difon la religió a una ciutat, guanyeu [amount] vegades la quantitat de seguidors d’altres religions com a [stat].

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Els atacs a distància es poden fer per damunt d’obstacles.

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	/ No obté bonificacions defensives pel terreny

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Sense penalitzacions defensives per terreny

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ No s’apliquen penalitzacions de dany per unitats ferides

	Applicable to: Global, Unit

??? example  "Unable to capture cities"
	/ No pot capturar ciutats

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ No pot saquejar caselles

	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	/ El saqueig no costa moviment

	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	/ Es pot curar fora del territori amic.

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Es dupliquen tots els efectes de curació.

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Cura [amount] de dany quan mata una unitat.

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Només poden curar-se quan saquegen.

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount] % de costos de manteniment

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount] % de cost d’or de la millora

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Aconsegueix [amount] % del dany fet a unitats [combatantFilter] com a [stockpile].

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Quan es captura una ciutat, es reben [amount] vegades els seus [stat] de producció com a [stockpile].

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Obteniu el [amount] % de [costOrStrength] de les unitats [mapUnitFilter] assassinades com a [stockpile].

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	/ [amount] d’experiència guanyada per combat

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ Es guanya [relativeAmount] % d’experiència pels combats

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ Cada «[greatPerson]» s’aconsegueix un [relativeAmount] % més de pressa

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] de cost de punts de moviment per desembarcar

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] de cost de punts de moviment per embarcar

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

## Nation uniques（Nació）
??? example  "Starts with [tech]"
	/ Comença amb [tech].

	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	/ Comença amb [policy] ja adoptada.

	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	/ Totes les unitats es mouen a través de les caselles de bosc i de jungla en territori amic com si tinguessin carreteres. Aquestes caselles es poden fer servir per a establir connexions entre ciutats quan es recerqui «La Roda».

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	/ Les unitats ignoren els costos de terreny quan es mouen a qualsevol casella amb turons.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Will not be chosen for new games"
	/ No es triarà per partides noves.

	Applicable to: Nation

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Personality uniques（Personalitat）
??? example  "Will not build [baseUnitFilter/buildingFilter]"
	/ No construirà [baseUnitFilter/buildingFilter]

	Example: "Will not build [Melee]"

	Applicable to: Personality

## Era uniques
??? example  "Starting in this era disables religion"
	/ Si es comença en aquesta era, es desactiva la religió.

	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	/ Quan una civilització entra a aquesta era, cada civilització principal obté un espia.

	Applicable to: Era

## Tech uniques（Tecnologia）
??? example  "Starting tech"
	/ Tecnologia inicial

	Applicable to: Tech

??? example  "Can be continually researched"
	/ Es pot investigar indefinidament

	Applicable to: Tech

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Cannot be hurried"
	/ No es pot accelerar

	Applicable to: Tech, Building

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Policy uniques（Política）
??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## FounderBelief uniques（Creença de fundador）
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	/ [stats] per cada ciutat que segueix aquesta religió

	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [positiveAmount] global followers [cityFilter]"
	/ [stats] de cada [positiveAmount] seguidors globals [cityFilter]

	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## FollowerBelief uniques（Creença de seguidor）
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] de cada especialista [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] de població [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] en ciutats en caselles [terrainFilter]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] de tots els edificis [buildingFilter]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] de caselles de [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] per cada ruta comercial

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	/ [relativeAmount] % [stat]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	/ [relativeAmount] % [stat] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount] % [stat] de cada [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount] % de rendiment de cada [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] % de producció quan es construeixen edificis [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount] % de producció quan construeix unitats de tipus «[baseUnitFilter]» [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount] % de producció quan es construeixen meravelles [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ Qualsevol edifici que ja estigui a la capital rep una producció de [relativeAmount] %.

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount] % de creixement [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ Es conserva un [amount] % de menjar després d’augmentar la població [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount] % de consum de menjar de [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount] % d’insatisfacció de [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Es poden comprar unitats «[baseUnitFilter]» per [amount] [stat] [cityFilter] amb un cost creixent ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Pot comprar edificis [buildingFilter] per [amount] [stat] [cityFilter] a un preu creixent ([nonNegativeAmount]).

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Pot comprar unitats [baseUnitFilter] per [nonNegativeAmount] [stat] [cityFilter].

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Pot comprar edificis [buildingFilter] per [nonNegativeAmount] [stat] [cityFilter].

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Pot comprar unitats [baseUnitFilter] amb [stat] [cityFilter].

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Els edificis «[buildingFilter]» es poden comprar amb [stat] [cityFilter].

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Es poden comprar unitats [baseUnitFilter] amb [stat] per [nonNegativeAmount] vegades el seu cost normal de producció.

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Pot comprar edificis [buildingFilter] amb [stat] per [nonNegativeAmount] vegades el seu cost normal de producció.

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ El preu en [stat] de compra en ciutats es modifica un [relativeAmount] %

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat] cost de compra d’edificis [buildingFilter] [relativeAmount] %.

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat] de cost de compra d’unitats [baseUnitFilter] [relativeAmount] %

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] % de cost de manteniment per als edificis [buildingFilter] [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount] % de cost cultural per al creixement de frontera natural [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount] % de cost d’or per adquirir caselles [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount] % de generació de grans personatges [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Les unitats [baseUnitFilter] noves comencen amb [amount] PE [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Totes les noves unitats entrenades de tipus «[baseUnitFilter]» [cityFilter] reben l’ascens [promotion]

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ Les unitats [mapUnitFilter] adjacents a aquesta ciutat guanyen [amount] de vida per torn quan es curen.

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount] % de força per les ciutats

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Proporciona [amount] [resource].

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount] % de difusió natural de la religió [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ La religió es difon de manera natural a ciutats que estiguin fins a [amount] caselles de distància.

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion"
	/ Guanya [amount] % de [costOrStrength] de la unitat [mapUnitFilter] com [stockpile] quan s’elimina a com a molt 4 caselles de la ciutat que segueix aquesta religió.

	Example: "Earn [3]% of [Wounded] unit's [Cost] as [Mana] when killed within 4 tiles of a city following this religion"

	Applicable to: FollowerBelief

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Building uniques（Edifici）
??? example  "[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]"
	/ Un [positiveAmount] % de [stat] de cada [improvementFilter/buildingFilter] de la ciutat s’afegeix a [resource]

	Example: "[3]% of [Culture] from every [All Road] in the city added to [Iron]"

	Applicable to: Building

??? example  "Consumes [amount] [resource]"
	/ Consumeix [amount] de [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Costa [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ No es pot construir

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ No es pot comprar

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Es pot comprar amb [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Es pot comprar per [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ [amount] com a màxim per civilització

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ L’excés de menjar es converteix en producció quan es construeix alguna cosa.

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Cal com a mínim una població de [amount] a la ciutat.

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Provoca una alerta global quan es comença a construir.

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Quan es completa, provoca una alerta mundial

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ El cost augmenta en [amount] per cada ciutat que tingui la civilització

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ El cost augmenta en [amount] quan es construeix

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount] % de cost de producció

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Només es pot construir

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	/ Cal tenir un [tileFilter] propi a com a molt [amount] caselles de distància

	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	/ Permet la construcció d’armes nuclears

	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	/ No pot estar en [tileFilter]

	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	/ No pot estar al costat de [tileFilter]

	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	/ No es pot vendre.

	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	/ Indica la ciutat capital de la civilització

	Applicable to: Building

??? example  "Moves to new capital when capital changes"
	/ Es mou a la nova capital quan es canvia la ubicació de la capital

	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	/ Proporciona 1 còpia addicional de cada recurs de luxe millorat a prop d’aquesta ciutat

	Applicable to: Building

??? example  "Destroyed when the city is captured"
	/ Es destrueix si la ciutat és capturada

	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	/ No es destrueix mai quan la ciutat és capturada.

	Applicable to: Building

??? example  "[relativeAmount]% Gold given to enemy if city is captured"
	/ L’enemic rep [relativeAmount] % d’or si captura la ciutat.

	Example: "[+20]% Gold given to enemy if city is captured"

	Applicable to: Building

??? example  "Removes extra unhappiness from annexed cities"
	/ Trau la insatisfacció addicional de les ciutats annexionades

	Applicable to: Building

??? example  "Connects trade routes over water"
	/ Crea rutes comercials a través de l’aigua

	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	/ Es construeix automàticament en totes les ciutats on es pugui construir.

	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	/ Crea una millora [improvementName] en una casella específica.

	When choosing to construct this building, the player must select a tile where the improvement can be built. Upon building completion, the tile will gain this improvement. Limited to one per building.

	Example: "Creates a [Trading Post] improvement on a specific tile"

	This unique does not support conditionals.

	Applicable to: Building

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Pot portar [amount] unitats [mapUnitFilter] addicionals

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Spaceship part"
	/ Part de la nau espacial

	Applicable to: Building, Unit

??? example  "Cannot be hurried"
	/ No es pot accelerar

	Applicable to: Tech, Building

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Shown while unbuilable"
	This unique is automatically hidden from users.

	Applicable to: Building, Unit

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## UnitAction uniques（Acció de la unitat）
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	/ Funda una ciutat nova

	Applicable to: UnitAction

??? example  "Founds a new puppet city"
	/ Funda una ciutat titella nova

	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	/ Pot construir una millora de [improvementFilter] immediatament

	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	/ Pot difondre una religió.

	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	/ Pot eliminar altres religions a les ciutats

	Applicable to: UnitAction

??? example  "May found a religion"
	/ Pot fundar una religió.

	Applicable to: UnitAction

??? example  "May enhance a religion"
	/ Pot magnificar una religió.

	Applicable to: UnitAction

??? example  "Can transform to [unit]"
	/ Es pot transformar a [unit]

	By default consumes all movement

	Example: "Can transform to [Musketman]"

	Applicable to: UnitAction

## Unit uniques（Unitat）
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount] % de producció per saquejar caselles

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount] % de salut per saquejar caselles

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount] % de temps de construcció per a millores [improvementFilter]

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Pot construir millores [improvementFilter] a un ritme [relativeAmount] %

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount] % d’or de les missions comercials dels grans mercaders.

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Es dupliquen les bonificacions de combat que proporcionen els grans generals.

	Applicable to: Global, Unit

??? example  "Consumes [amount] [resource]"
	/ Consumeix [amount] de [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Costa [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ No es pot construir

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ No es pot comprar

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Es pot comprar amb [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Es pot comprar per [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ [amount] com a màxim per civilització

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ L’excés de menjar es converteix en producció quan es construeix alguna cosa.

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Cal com a mínim una població de [amount] a la ciutat.

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Provoca una alerta global quan es comença a construir.

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Quan es completa, provoca una alerta mundial

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ El cost augmenta en [amount] per cada ciutat que tingui la civilització

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ El cost augmenta en [amount] quan es construeix

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount] % de cost de producció

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Només es pot construir

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "May create improvements on water resources"
	/ Crea millores en recursos aquàtics

	Applicable to: Unit

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	/ Pot construir millores de casella com ara [improvementFilter/terrainFilter].

	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	/ Es pot col·locar a [comment] a la capital

	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	/ Evita la difusió de la religió a les ciutats que tingui adjacents

	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	/ Treu les altres religions quan difon la seva

	Applicable to: Unit

??? example  "May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away"
	/ Es pot llançar en paracaigudes en caselles [tileFilter] fins a [positiveAmount] caselles de distància.

	Example: "May Paradrop to [Farm] tiles up to [3] tiles away"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	/ Pot realitzar escombrats aeris.

	Applicable to: Unit

??? example  "Can speed up construction of a building"
	/ Pot accelerar la construcció d’un edifici

	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	/ Pot accelerar la construcció d’una meravella

	Applicable to: Unit

??? example  "Can hurry technology research"
	/ Pot accelerar la recerca científica

	Applicable to: Unit

??? example  "Can generate a large amount of culture"
	/ Pot generar una gran quantitat de cultura

	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	/ Pot fer missions comercials amb ciutats-estat, donant una gran quantitat d’or i [amount] d’influència

	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Automation is a primary action"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount] % de força

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] de força

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount] % de força, percentatge que es redueix conforme augmenta la distància a la capital.

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount] % de bonificació a l’atac per flanqueig

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles"
	/ [relativeAmount] % de força per a unitats [mapUnitFilter] enemigues en caselles [tileFilter] adjacents

	Example: "[+20]% Strength for enemy [Wounded] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	/ [relativeAmount] % de força per unitats [mapUnitFilter] a una distància de com a molt [amount] caselles

	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] atacs addicionals per torn

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] de moviment

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] de visió

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ Abast [amount]

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] d’abast d’intercepció aèrea

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] de vida quan es cura

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount] % de força de difusió de la religió

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Quan es difon la religió a una ciutat, guanyeu [amount] vegades la quantitat de seguidors d’altres religions com a [stat].

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Can only attack [combatantFilter] units"
	/ Només pot atacar unitats de tipus «[combatantFilter]»

	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	/ Només pot atacar caselles de tipus «[tileFilter]»

	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	/ No pot atacar

	Applicable to: Unit

??? example  "Must set up to ranged attack"
	/ S’ha de muntar per a poder atacar a distància

	Applicable to: Unit

??? example  "Self-destructs when attacking"
	/ S’autodestrueix quan ataca

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	/ S’elimina la penalització per atacar per una costa.

	Applicable to: Unit

??? example  "May attack when embarked"
	/ Pot atacar quan embarca.

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	/ S’elimina la penalització de combat per atacar per sobre d’un riu.

	Applicable to: Unit

??? example  "Blast radius [amount]"
	/ [amount] de radi d’explosió

	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Els atacs a distància es poden fer per damunt d’obstacles.

	Applicable to: Global, Unit

??? example  "Nuclear weapon of Strength [amount]"
	/ Arma nuclear de força [amount]

	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "No defensive terrain bonus"
	/ No obté bonificacions defensives pel terreny

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Sense penalitzacions defensives per terreny

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ No s’apliquen penalitzacions de dany per unitats ferides

	Applicable to: Global, Unit

??? example  "Uncapturable"
	/ No es pot capturar

	Applicable to: Unit

??? example  "Withdraws before melee combat"
	/ Es retira abans dels combats cos a cos

	Applicable to: Unit

??? example  "Unable to capture cities"
	/ No pot capturar ciutats

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ No pot saquejar caselles

	Applicable to: Global, Unit

??? example  "Destroys [cityFilter] cities instead of capturing"
	/ Destrueix les ciutats [cityFilter] en lloc de capturar-les

	The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities.Capital cities (including city states) are immune to this effect.

	Example: "Destroys [in all cities] cities instead of capturing"

	Applicable to: Unit

??? example  "No movement cost to pillage"
	/ El saqueig no costa moviment

	Applicable to: Global, Unit

??? example  "Can move after attacking"
	/ Es pot moure després d’atacar

	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	/ Tranfereix moviment a [mapUnitFilter]

	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	/ Es pot moure immediatament després de comprar-lo

	Applicable to: Unit

??? example  "May heal outside of friendly territory"
	/ Es pot curar fora del territori amic.

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Es dupliquen tots els efectes de curació.

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Cura [amount] de dany quan mata una unitat.

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Només poden curar-se quan saquegen.

	Applicable to: Global, Unit

??? example  "Unit will heal every turn, even if it performs an action"
	/ La unitat es curarà cada torn, fins i tot si realitza alguna acció.

	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	/ La curació de cadascuna de les unitats adjacents augmenta en [amount] de vida.

	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	/ Sense visió

	Applicable to: Unit

??? example  "Can see over obstacles"
	/ Pot veure per damunt dels obstacles

	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	/ Pot portar [amount] unitats [mapUnitFilter]

	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Pot portar [amount] unitats [mapUnitFilter] addicionals

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	/ No es pot transportar en unitats [mapUnitFilter]

	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	/ [relativeAmount] % de probabilitat d’interceptar atacs aeris

	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	/ El dany rebut per intercepció es redueix un [relativeAmount] %

	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	/ [relativeAmount] % de dany quan s’intercepta

	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	/ Es poden fer [amount] intercepcions addicionals per torn.

	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	/ No es pot interceptar.

	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	/ No pot interceptar les unitats [mapUnitFilter].

	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	/ [relativeAmount] % de força quan realitza escombrats aeris

	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount] % de costos de manteniment

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount] % de cost d’or de la millora

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Aconsegueix [amount] % del dany fet a unitats [combatantFilter] com a [stockpile].

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Quan es captura una ciutat, es reben [amount] vegades els seus [stat] de producció com a [stockpile].

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Obteniu el [amount] % de [costOrStrength] de les unitats [mapUnitFilter] assassinades com a [stockpile].

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "May capture killed [mapUnitFilter] units"
	/ Poc capturar unitats [mapUnitFilter] eliminades

	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "[amount] XP gained from combat"
	/ [amount] d’experiència guanyada per combat

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ Es guanya [relativeAmount] % d’experiència pels combats

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "Can be earned through combat"
	/ Es pot obtenir a través del combat

	Applicable to: Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ Cada «[greatPerson]» s’aconsegueix un [relativeAmount] % més de pressa

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "Invisible to others"
	/ Invisible pels demés

	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	/ Invisible per a les unitats que no estiguin adjacents

	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	/ Pot veure unitats [mapUnitFilter] invisibles

	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [unit] through ruins-like effects"
	/ Es pot millorar a [unit] a través d’efectes de les ruïnes

	Example: "May upgrade to [Musketman] through ruins-like effects"

	Applicable to: Unit

??? example  "Can upgrade to [unit]"
	/ Es pot millorar a [unit]

	Example: "Can upgrade to [Musketman]"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	/ Destrueix les millores de la casella quan ataca

	Applicable to: Unit

??? example  "Cannot move"
	/ No es pot moure

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	/ Es duplica el moviment en caselles de tipus «[terrainFilter]»

	Example: "Double movement in [Fresh Water]"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	/ Totes les caselles tenen un cost de moviment d’1

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	/ Pot transitar caselles d'aigua sense embarcar

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	/ Pot passar per caselles intransitables

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores terrain cost"
	/ Ignora el cost de terreny

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores Zone of Control"
	/ Ignora la zona de control

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Rough terrain penalty"
	/ Se li apliquen penalitzacions en terrenys irregulars

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can enter ice tiles"
	/ Pot entrar a les caselles de gel.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Cannot embark"
	/ No pot embarcar

	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	/ No pot entrar en caselles d’oceà

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	/ Pot entrar en caselles estrangeres sense necessitar pactes de fronteres obertes.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	/ Pot entrar en caselles estrangeres sense pactes de fronteres obertes, però perd [amount] de força religiosa cada torn que hi acabi allí.

	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] de cost de punts de moviment per desembarcar

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] de cost de punts de moviment per embarcar

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "Never appears as a Barbarian unit"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "Religious Unit"
	/ Unitat religiosa

	Applicable to: Unit

??? example  "Spaceship part"
	/ Part de la nau espacial

	Applicable to: Building, Unit

??? example  "Takes your religion over the one in their birth city"
	/ Imposa la vostra religió per damunt de la religió de la ciutat de naixement

	Applicable to: Unit

??? example  "Great Person - [comment]"
	/ Gran personatge ‒ [comment]

	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
	/ Forma part del grup de Gran Personatge [comment]

	Great people in the same group increase teach other's costs when gained. Gaining one will make all others in the same group cost more GPP.

	Example: "Is part of Great Person group [comment]"

	Applicable to: Unit

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Shown while unbuilable"
	This unique is automatically hidden from users.

	Applicable to: Building, Unit

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## UnitType uniques（Tipus d’unitat）
??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Promotion uniques（Ascens）
??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Doing so will consume this opportunity to choose a Promotion"
	/ Si es tria, es perdrà aquesta oportunitat de triar un ascens.

	Applicable to: Promotion

??? example  "This Promotion is free"
	/ Aquest ascens és gratuït.

	Applicable to: Promotion

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Terrain uniques（Terreny）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[amount] Sight"
	/ [amount] de visió

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Must be adjacent to [amount] [simpleTerrain] tiles"
	Example: "Must be adjacent to [3] [Elevated] tiles"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Must be adjacent to [amount] to [amount] [simpleTerrain] tiles"
	Example: "Must be adjacent to [3] to [3] [Elevated] tiles"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Must not be on [amount] largest landmasses"
	Example: "Must not be on [3] largest landmasses"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Must be on [amount] largest landmasses"
	Example: "Must be on [3] largest landmasses"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Occurs on latitudes from [amount] to [amount] percent of distance equator to pole"
	Example: "Occurs on latitudes from [3] to [3] percent of distance equator to pole"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Occurs in groups of [amount] to [amount] tiles"
	Example: "Occurs in groups of [3] to [3] tiles"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Neighboring tiles will convert to [baseTerrain/terrainFeature]"
	Supports conditionals that need only a Tile as context and nothing else, like `<with [n]% chance>`, and applies them per neighbor.

	If your mod renames Coast or Lakes, do not use this with one of these as parameter, as the code preventing artifacts won't work.

	Example: "Neighboring tiles will convert to [Grassland]"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Grants [stats] to the first civilization to discover it"
	/ Atorga[stats] a la primera civilització a descobrir-ho

	Example: "Grants [+1 Gold, +2 Production] to the first civilization to discover it"

	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	/ Les unitats que acabin el torn en aquest terreny reben [amount] de dany.

	Example: "Units ending their turn on this terrain take [3] damage"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	This unique does not support conditionals.

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	/ Concedeix [promotion] ([comment]) a les unitats [mapUnitFilter] adjacents durant la resta de la partida.

	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	/ [amount] de força per les ciutats construïdes en aquest tipus de terreny

	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time bonus of [stats] to the closest city when cut down"
	/ Proporciona una bonificació de [stats] d’una sola vegada a la ciutat més propera quan es tala.

	Example: "Provides a one-time bonus of [+1 Gold, +2 Production] to the closest city when cut down"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Terrain

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ La casella produeix sense haver-li d’assignar població.

	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	/ Anul·la tots els altres atributs de la casella.

	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	/ En aquesta casella només s’hi poden construir les millores [improvementFilter].

	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	/ Bloca la línia de visió des de les caselles a la mateixa elevació.

	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	/ S’eleva [amount], per fer càlculs de visibilitat.

	Example: "Has an elevation of [3] for visibility calculations"

	Applicable to: Terrain

??? example  "Always Fertility [amount] for Map Generation"
	Example: "Always Fertility [3] for Map Generation"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "[amount] to Fertility for Map Generation"
	Example: "[3] to Fertility for Map Generation"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]"
	Example: "A Region is formed with at least [3]% [Elevated] tiles, with priority [3]"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]"
	Example: "A Region is formed with at least [3]% [Elevated] tiles and [Elevated] tiles, with priority [3]"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles"
	Example: "A Region can not contain more [Elevated] tiles than [Elevated] tiles"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Base Terrain on this tile is not counted for Region determination"
	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Starts in regions of this type receive an extra [resource]"
	Example: "Starts in regions of this type receive an extra [Iron]"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Never receives any resources"
	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Becomes [terrainName] when adjacent to [terrainFilter]"
	Example: "Becomes [Forest] when adjacent to [Fresh Water]"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Considered [terrainQuality] when determining start locations"
	Example: "Considered [Undesirable] when determining start locations"

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Doesn't generate naturally"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Occurs at temperature between [fraction] and [fraction] and humidity between [fraction] and [fraction]"
	Example: "Occurs at temperature between [0.5] and [0.5] and humidity between [0.5] and [0.5]"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Occurs in chains at high elevations"
	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Occurs in groups around high elevations"
	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Every [amount] tiles with this terrain will receive a major deposit of a strategic resource."
	Example: "Every [3] tiles with this terrain will receive a major deposit of a strategic resource."

	This unique is automatically hidden from users.

	Applicable to: Terrain

??? example  "Rare feature"
	/ Característica rara

	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	/ La probabilitat de ser destruït per armes nuclears és de [amount] %.

	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	/ Aigua dolça

	Applicable to: Terrain

??? example  "Rough terrain"
	/ Terreny irregular

	Applicable to: Terrain

??? example  "Coastal Water"
	Applicable to: Terrain

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

## Improvement uniques（Millora）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "Consumes [amount] [resource]"
	/ Consumeix [amount] de [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Provides [amount] [resource]"
	/ Proporciona [amount] [resource].

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Costa [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ No es pot construir

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "[amount] Sight"
	/ [amount] de visió

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ La casella produeix sense haver-li d’assignar població.

	Applicable to: Terrain, Improvement

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Can also be built on tiles adjacent to fresh water"
	/ També es pot construir en caselles adjacents a aigua dolça.

	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	/ [stats] de caselles [tileFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	/ Assegura una producció mínima de la casella de [stats]

	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	/ Es pot realitzar fora de les vostres fronteres.

	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	/ Es pot construir just a fora de la frontera.

	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	/ Només es pot construir en caselles de [tileFilter].

	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	/ No es pot construir en caselles de [tileFilter].

	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	/ Només es pot construir per millorar un recurs.

	Applicable to: Improvement

??? example  "Does not need removal of [terrainFeature]"
	Example: "Does not need removal of [Hill]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	/ Quan es construeix, es treuen les característiques que es pugui.

	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	/ Dóna una bonificació defensiva de [relativeAmount] %.

	Does not accept unit-based conditionals

	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	/ Costa [amount] [stat] per torn quan és dins al vostre territori

	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	/ Costa [amount] [stat] per torn

	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	/ Les unitats enemigues adjacents que acabin el torn reben [amount] de dany.

	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	/ Millora gran

	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	/ S’obté una bonificació aleatòria quan s’hi entra.

	Applicable to: Improvement

??? example  "Unpillagable"
	/ No es pot saquejar.

	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	/ El saqueig d’aquesta millora produeix [stats] aproximadament.

	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	/ El saqueig d’aquesta millora produeix [stats].

	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Destroyed when pillaged"
	/ Queda destruït quan es saqueja.

	Applicable to: Improvement

??? example  "Irremovable"
	/ No es pot treure.

	Applicable to: Improvement

??? example  "Will not be replaced by automated units"
	/ Les unitats automatizades no ho reemplaçaran

	Applicable to: Improvement

??? example  "Improves [resourceFilter] resource in this tile"
	/ Millora el recurs [resourceFilter] d’aquesta casella

	This is offered as an alternative to the improvedBy field of a resource. The result will be cached within the resource definition when loading a game, without knowledge about terrain, cities, civs, units or time. Therefore, most conditionals will not work, only those **not** dependent on game state.

	Example: "Improves [Strategic] resource in this tile"

	This unique does not support conditionals.

	Applicable to: Improvement

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Resource uniques（Recurs）
??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Must not be on [amount] largest landmasses"
	Example: "Must not be on [3] largest landmasses"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Must be on [amount] largest landmasses"
	Example: "Must be on [3] largest landmasses"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Doesn't generate naturally"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Occurs at temperature between [fraction] and [fraction] and humidity between [fraction] and [fraction]"
	Example: "Occurs at temperature between [0.5] and [0.5] and humidity between [0.5] and [0.5]"

	This unique is automatically hidden from users.

	Applicable to: Terrain, Resource

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Deposits in [tileFilter] tiles always provide [amount] resources"
	/ Els dipòsits en [tileFilter] sempre proporcionen [amount] de recursos.

	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	/ Només es pot crear en ciutats-estat mercantils.

	Applicable to: Resource

??? example  "Stockpiled"
	/ Reserves

	This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment.The current stockpiled amount can be affected with trigger uniques.

	Applicable to: Resource

??? example  "City-level resource"
	/ Recurs a nivell de ciutat

	This resource is calculated on a per-city level rather than a per-civ level

	Applicable to: Resource

??? example  "Cannot be traded"
	/ No es pot comerciar

	Applicable to: Resource

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Generated with weight [amount]"
	The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). Resources without a unique are given weight `1`

	Example: "Generated with weight [3]"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Minor deposits generated with weight [amount]"
	The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). Resources without a unique are not generated as minor deposits.

	Example: "Minor deposits generated with weight [3]"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Generated near City States with weight [amount]"
	The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). Only assignable to luxuries, resources without a unique are given weight `1`

	Example: "Generated near City States with weight [3]"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Special placement during map generation"
	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Generated on every [amount] tiles"
	Example: "Generated on every [3] tiles"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Guaranteed with Strategic Balance resource option"
	/ Garantits amb l’opció de generació de mapes «balanç estratègic».

	Applicable to: Resource

??? example  "AI will sell at [amount] Gold"
	Example: "AI will sell at [3] Gold"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "AI will buy at [amount] Gold"
	Example: "AI will buy at [3] Gold"

	This unique is automatically hidden from users.

	Applicable to: Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Ruins uniques（Ruïnes）
??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Free [unit] found in the ruins"
	/ s’ha trobat un [unit] de franc a les ruïnes.

	Example: "Free [Musketman] found in the ruins"

	Applicable to: Ruins

??? example  "From a randomly chosen tile [positiveAmount] tiles away from the ruins, reveal tiles up to [positiveAmount] tiles away with [positiveAmount]% chance"
	Example: "From a randomly chosen tile [3] tiles away from the ruins, reveal tiles up to [3] tiles away with [3]% chance"

	Applicable to: Ruins

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Speed uniques（Velocitat）
!!! note ""

    Speed uniques will be treated as part of GlobalUniques for the Speed selected in a game

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

## Difficulty uniques（Dificultat）
!!! note ""

    Difficulty uniques will be treated as part of GlobalUniques for the Difficulty selected in a game

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## CityState uniques（Ciutat-estat）
??? example  "Provides military units every ≈[positiveAmount] turns"
	/ Proporciona unitats militars més o menys cada [positiveAmount] torns.

	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	/ Proporciona un luxe exclusiu.

	Applicable to: CityState

## ModOptions uniques（Opcions del mod）
??? example  "Diplomatic relationships cannot change"
	/ Les relacions diplomàtiques no poden canviar

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can convert gold to science with sliders"
	/ Pot convertir l’or en ciència amb els lliscadors

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow City States to spawn with additional units"
	/ Permet que les ciutats-estat apareguin amb unitats addicionals

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can trade civilization introductions for [positiveAmount] Gold"
	/ Pot comerciar amb les presentacions de civilitzacions desconegudes a canvi de [positiveAmount] d’or

	Example: "Can trade civilization introductions for [3] Gold"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Disable religion"
	/ Desactiva la religió

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can only start games from the starting era"
	/ Només pot començar partides de de l’era inicial

	In this case, 'starting era' means the first defined Era in the entire ruleset.

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze capital"
	/ Permet que s’arrasi la capital

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze holy city"
	/ Permet arrasar la ciutat sagrada

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

??? example  "Mod is incompatible with [modFilter]"
	/ El mod no és compatible amb [modFilter]

	Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible.

	Example: "Mod is incompatible with [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod requires [modFilter]"
	/ El mod necessita [modFilter]

	Specifies that your Extension Mod is only available if any other Mod matching the filter is active.

	Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue.

	Example: "Mod requires [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	/ Només es pot fer servir com a mod audiovisual permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	/ Es pot fer servir com a mod audiovisual permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	/ No es pot fer servir com a mod audiovisual permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod preselects map [comment]"
	/ El mod preselecciona el mapa [comment]

	Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive.

	Example: "Mod preselects map [comment]"

	This unique does not support conditionals.

	Applicable to: ModOptions

## Event uniques（Esdeveniment）
??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

## EventChoice uniques（Tria per esdeveniment）
??? example  "Only available"
	/ Només disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ No disponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "[relativeAmount]% weight to this choice for AI decisions"
	Example: "[+20]% weight to this choice for AI decisions"

	This unique is automatically hidden from users.

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Promotion, EventChoice

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Conditional uniques（Condicional）
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;every [positiveAmount] turns&gt;"
	/ cada [positiveAmount] torns

	Example: "&lt;every [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;before turn number [nonNegativeAmount]&gt;"
	/ abans del torn [nonNegativeAmount]

	Example: "&lt;before turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;after turn number [nonNegativeAmount]&gt;"
	/ després del torn [nonNegativeAmount]

	Example: "&lt;after turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;on [speed] game speed&gt;"
	/ a velocitat de partida [speed]

	Example: "&lt;on [Quick] game speed&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty&gt;"
	/ amb dificultat [difficulty]

	Example: "&lt;on [Prince] difficulty&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or higher&gt;"
	/ amb dificultat [difficulty] o superior

	Example: "&lt;on [Prince] difficulty or higher&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or lower&gt;"
	/ amb dificultat [difficulty] o inferior

	Example: "&lt;on [Prince] difficulty or lower&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is enabled&gt;"
	/ quan la victòria per [victoryType] està activada

	Example: "&lt;when [Domination] Victory is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is disabled&gt;"
	/ quan la victòria per [victoryType] està desactivada

	Example: "&lt;when [Domination] Victory is disabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when religion is enabled&gt;"
	/ quan la religió està activada

	Applicable to: Conditional

??? example  "&lt;when religion is disabled&gt;"
	/ quan la religió està desactivada

	Applicable to: Conditional

??? example  "&lt;when espionage is enabled&gt;"
	/ quan l’espionatge està activat

	Applicable to: Conditional

??? example  "&lt;when espionage is disabled&gt;"
	/ quan l’espionatge està desactivat

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are enabled&gt;"
	/ quan les armes nuclears estan activades

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are disabled&gt;"
	/ wuan les armes nuclears estan desactivades

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount]% chance&gt;"
	/ amb una probabilitat de [nonNegativeAmount] %.

	Example: "&lt;with [3]% chance&gt;"

	Applicable to: Conditional

??? example  "&lt;if tutorials are enabled&gt;"
	This unique is automatically hidden from users.

	Applicable to: Conditional

??? example  "&lt;if tutorial [comment] is completed&gt;"
	Example: "&lt;if tutorial [comment] is completed&gt;"

	This unique is automatically hidden from users.

	Applicable to: Conditional

??? example  "&lt;for [civFilter] Civilizations&gt;"
	/ per a civilitzacions [civFilter]

	Example: "&lt;for [City-States] Civilizations&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	/ quan està en guerra

	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	/ quan no està en guerra

	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	/ durant una edat d’or

	Applicable to: Conditional

??? example  "&lt;when not in a Golden Age&gt;"
	/ quan no està en una edat d’or

	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	/ durant el Dia del Rei

	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	/ mentre l’imperi és feliç

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	/ durant l’[era]

	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	/ abans de l’[era]

	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	/ a partir de l’[era]

	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	/ si es comença a l’[era]

	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	/ si cap altra civilització l’ha recercat

	Applicable to: Conditional

??? example  "&lt;after discovering [techFilter]&gt;"
	/ després de descobrir «[techFilter]»

	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [techFilter]&gt;"
	/ abans de descobrir «[techFilter]»

	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;while researching [techFilter]&gt;"
	/ mentre investiga «[techFilter]»

	This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)

	Example: "&lt;while researching [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	/ si cap altra civilització l’ha adoptat

	Applicable to: Conditional

??? example  "&lt;if no Civilization has adopted [policy/belief]&gt;"
	/ si cap civilització ha adoptat «[policy/belief]»

	Example: "&lt;if no Civilization has adopted [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	/ després d’adoptar «[policy/belief]»

	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
	/ abans d’adoptar «[policy/belief]»

	Example: "&lt;before adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before founding a Pantheon&gt;"
	/ abans de fundar un panteó

	Applicable to: Conditional

??? example  "&lt;after founding a Pantheon&gt;"
	/ després de fundar un panteó

	Applicable to: Conditional

??? example  "&lt;before founding a religion&gt;"
	/ abans de fundar una religió

	Applicable to: Conditional

??? example  "&lt;after founding a religion&gt;"
	/ després de fundar una religió

	Applicable to: Conditional

??? example  "&lt;before enhancing a religion&gt;"
	/ abans de magnificar una religió

	Applicable to: Conditional

??? example  "&lt;after enhancing a religion&gt;"
	/ després de magnificar una religió

	Applicable to: Conditional

??? example  "&lt;after generating a Great Prophet&gt;"
	/ després de generar un gran profeta

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	/ si s’ha construït «[buildingFilter]»

	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed&gt;"
	/ si no s’ha construït «[buildingFilter]»

	Example: "&lt;if [Culture] is not constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in all [cityFilter] cities&gt;"
	/ si s’ha construït [buildingFilter] a totes les ciutats [cityFilter]

	Example: "&lt;if [Culture] is constructed in all [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities&gt;"
	/ si s’ha construït [buildingFilter] en almenys [positiveAmount] ciutats [cityFilter]

	Example: "&lt;if [Culture] is constructed in at least [3] of [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed by anybody&gt;"
	/ si algú ha construït «[buildingFilter]»

	Example: "&lt;if [Culture] is constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed by anybody&gt;"
	/ si ningú ha construït [buildingFilter]

	Example: "&lt;if [Culture] is not constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	/ amb [resource]

	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	/ sense [resource]

	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	/ quan s’estigui per damunt de [amount] [stat/resource]

	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'

	Example: "&lt;when above [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
	/ quan s’estigui per sota de [amount] [stat/resource]

	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when below [amount] Happiness'

	Example: "&lt;when below [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when between [amount] and [amount] [stat/resource]&gt;"
	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness. 'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;when between [3] and [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;in this city&gt;"
	/ en aquesta ciutat

	Applicable to: Conditional

??? example  "&lt;in [cityFilter] cities&gt;"
	/ en ciutats [cityFilter]

	Example: "&lt;in [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities connected to the capital&gt;"
	/ en ciutats connectades a la capital

	Applicable to: Conditional

??? example  "&lt;in cities with a [religionFilter] religion&gt;"
	/ en ciutats amb una religió [religionFilter]

	Example: "&lt;in cities with a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities not following a [religionFilter] religion&gt;"
	/ en ciutats que no segueixin una religió [religionFilter]

	Example: "&lt;in cities not following a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with a major religion&gt;"
	/ en ciutats amb una religió majoritària

	Applicable to: Conditional

??? example  "&lt;in cities with an enhanced religion&gt;"
	/ en ciutats amb una religió magnificada

	Applicable to: Conditional

??? example  "&lt;in cities following our religion&gt;"
	/ en ciutat que segueixin la nostra religió

	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	/ en ciutats amb un [buildingFilter]

	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	/ en ciutats sense [buildingFilter]

	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [positiveAmount] [populationFilter]&gt;"
	/ en ciutats amb almenys [positiveAmount] [populationFilter]

	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with [positiveAmount] [populationFilter]&gt;"
	/ en ciutats amb [positiveAmount] [populationFilter]

	Example: "&lt;in cities with [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with between [amount] and [amount] [populationFilter]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;in cities with between [3] and [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with less than [amount] [populationFilter]&gt;"
	/ en ciutats amb menys de [amount] [populationFilter]

	Example: "&lt;in cities with less than [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	/ amb guarnició

	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	/ per a unitats de tipus [mapUnitFilter]

	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	/ quan [mapUnitFilter]

	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	/ per les unitats amb [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	/ per les unitats sense [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	/ contra ciutats

	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	/ contra unitats [mapUnitFilter]

	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;vs [combatantFilter]&gt;"
	Example: "&lt;vs [City]&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	/ quan lluita contra unitats d’una civilització que tingui més ciutats

	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	/ quan ataca

	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	/ quan està defensant

	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	/ quan lluita en caselles de [tileFilter]

	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	/ en continents estrangers

	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	/ quan està adjacent a una unitat [mapUnitFilter]

	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] HP&gt;"
	/ quan té més de [positiveAmount] de vida

	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] HP&gt;"
	/ quan té menys de [positiveAmount] de vida

	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] movement&gt;"
	Example: "&lt;when below [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] movement&gt;"
	Example: "&lt;when above [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	/ Si encara no ha usat altres accions

	Applicable to: Conditional

??? example  "&lt;when stacked with a [mapUnitFilter] unit&gt;"
	/ quan està apilat amb una unitat [mapUnitFilter]

	Example: "&lt;when stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when not stacked with a [mapUnitFilter] unit&gt;"
	/ quan no està apilada amb una unitat [mapUnitFilter]

	Example: "&lt;when not stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	/ en caselles [tileFilter]

	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	/ amb caselles sense [tileFilter]

	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [positiveAmount] tiles of a [tileFilter]&gt;"
	/ en [positiveAmount] caselles de [tileFilter]

	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles adjacent to [tileFilter] tiles&gt;"
	/ en caselles adjacents a caselles de tipus «[tileFilter]»

	Example: "&lt;in tiles adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles not adjacent to [tileFilter] tiles&gt;"
	/ en caselles no adjacents a caselles de tipus «[tileFilter]»

	Example: "&lt;in tiles not adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	/ en mapes d’aigua

	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	/ en regions [regionType]

	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	/ en totes les regions excepte [regionType]

	Example: "&lt;in all except [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;when number of [countable] is equal to [countable]&gt;"
	Example: "&lt;when number of [1000] is equal to [1000]&gt;"

	Applicable to: Conditional

??? example  "&lt;when number of [countable] is different than [countable]&gt;"
	Example: "&lt;when number of [1000] is different than [1000]&gt;"

	Applicable to: Conditional

??? example  "&lt;when number of [countable] is more than [countable]&gt;"
	Example: "&lt;when number of [1000] is more than [1000]&gt;"

	Applicable to: Conditional

??? example  "&lt;when number of [countable] is less than [countable]&gt;"
	Example: "&lt;when number of [1000] is less than [1000]&gt;"

	Applicable to: Conditional

??? example  "&lt;when number of [countable] is between [countable] and [countable]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;when number of [1000] is between [1000] and [1000]&gt;"

	Applicable to: Conditional

??? example  "&lt;when carried by [mapUnitFilter] units&gt;"
	/ quan són transportats per unitats [mapUnitFilter]

	Example: "&lt;when carried by [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is enabled&gt;"
	/ si [modFilter] està actiu

	Example: "&lt;if [DeCiv Redux] is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is not enabled&gt;"
	/ si [modFilter] no està activat

	Example: "&lt;if [DeCiv Redux] is not enabled&gt;"

	Applicable to: Conditional

## TriggerCondition uniques（Condició de desencadenat）
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [techFilter] technology&gt;"
	/ quan es descobreix la tecnologia [techFilter]

	Example: "&lt;upon discovering [Agriculture] technology&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	/ quan s’entri a l’[era]

	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a new era&gt;"
	/ quan comença una nova era

	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	/ quan s’adopta [policy/belief]

	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war on [civFilter] Civilizations&gt;"
	/ quan es declara la guerra a les civilitzacions [civFilter]

	Example: "&lt;upon declaring war on [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon being declared war on by [civFilter] Civilizations&gt;"
	/ quan les civilitzacions [civFilter] li declaren la guerra

	Example: "&lt;upon being declared war on by [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a war with [civFilter] Civilizations&gt;"
	/ quan s’entra en guerra amb les civilitzacions [civFilter]

	Example: "&lt;upon entering a war with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon signing a peace treaty with [civFilter] Civilizations&gt;"
	Example: "&lt;upon signing a peace treaty with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	/ quan es declara amistat

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	/ quan es declara un pacte de defensa

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	/ quan comença una edat d’or

	Applicable to: TriggerCondition

??? example  "&lt;upon ending a Golden Age&gt;"
	/ quan acaba una edat d’or

	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	/ quan es conquereix una ciutat

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon losing a city&gt;"
	/ quan es perd una ciutat

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a city&gt;"
	/ quan es funda una ciutat

	Applicable to: TriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ quan es construeix una millora [improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon discovering a Natural Wonder&gt;"
	/ quan es descobreix una meravella de la naturalesa

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter]&gt;"
	/ quan es construeix [buildingFilter]

	Example: "&lt;upon constructing [Culture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter] [cityFilter]&gt;"
	/ quan es construeix [buildingFilter] [cityFilter]

	Example: "&lt;upon constructing [Culture] [in all cities]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon gaining a [baseUnitFilter] unit&gt;"
	/ quan s’obté una unitat [baseUnitFilter]

	Example: "&lt;upon gaining a [Melee] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon losing a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon losing a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ fins a la fi del torn

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ quan comença el torn

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a Pantheon&gt;"
	/ quan es funda un panteó

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	/ quan es funda una religió

	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	/ quan es magnifica una religió

	Applicable to: TriggerCondition

??? example  "&lt;upon expending a [mapUnitFilter] unit&gt;"
	/ quan es gasta una unitat [mapUnitFilter]

	Example: "&lt;upon expending a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

## UnitTriggerCondition uniques（Condició del desencadenant de la unitat）
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon conquering a city&gt;"
	/ quan es conquereix una ciutat

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ quan es construeix una millora [improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ fins a la fi del torn

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ quan comença el torn

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon entering combat&gt;"
	/ quan entra en combat

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon damaging a [mapUnitFilter] unit&gt;"
	/ quan danya una unitat [mapUnitFilter]

	Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'

	Example: "&lt;upon damaging a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	/ quan es derrota una unitat de tipus [mapUnitFilter]

	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	/ quan la unitat és derrotada

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	/ quan la unitat rep un ascens

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] promotion&gt;"
	/ quan s’obté l’ascens [promotion]

	Example: "&lt;upon gaining the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] promotion&gt;"
	/ quan es perd l’ascens [promotion]

	Example: "&lt;upon losing the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] status&gt;"
	/ quan s’obté l’estat [promotion]

	Example: "&lt;upon gaining the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] status&gt;"
	/ quan es perd l’estat [promotion]

	Example: "&lt;upon losing the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [positiveAmount] HP in a single attack&gt;"
	/ quan perd com a mínim [positiveAmount] PV en un sol atac

	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	/ quan acaba el torn en una casella de tipus [tileFilter]

	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	/ quan es descobreix una casella [tileFilter]

	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon entering a [tileFilter] tile&gt;"
	/ quan entra en una casella [tileFilter]

	Example: "&lt;upon entering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques（Modificador d’acció de la unitat）
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	/ per consumir aquesta unitat

	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	/ per [amount] de moviment

	Will consume up to [amount] of Movement to execute

	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;for all movement&gt;"
	/ per a tots els moviments

	Will consume all Movement to execute

	Applicable to: UnitActionModifier

??? example  "&lt;requires [nonNegativeAmount] movement&gt;"
	/ necessita [nonNegativeAmount] de moviment

	Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up

	Example: "&lt;requires [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [stats] stats&gt;"
	/ costa [stats]

	A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock

	Example: "&lt;costs [+1 Gold, +2 Production] stats&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [amount] [stockpiledResource]&gt;"
	/ costa [amount] [stockpiledResource]

	A positive Integer value will be subtracted from your stock. Do not confuse with "Costs [amount] [stockpiledResource]" (uppercase 'C') for Improvements, Buildings, and Units.

	Example: "&lt;costs [3] [Mana]&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;removing the [promotion] promotion/status&gt;"
	/ traient l’ascens/estat [promotion]

	Removes the promotion/status from the unit - this is not a cost, units will be able to activate the action even without the promotion/status. To limit, use &lt;with the [promotion] promotion&gt; conditional

	Example: "&lt;removing the [Shock I] promotion/status&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	/ una vegada

	Applicable to: UnitActionModifier

??? example  "&lt;[positiveAmount] times&gt;"
	/ [positiveAmount] vegades

	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[nonNegativeAmount] additional time(s)&gt;"
	/ [nonNegativeAmount] vegades més

	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
	/ i després la unitat desapareix

	Applicable to: UnitActionModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

## MetaModifier uniques（Modificador meta）
!!! note ""

    Modifiers that can be added to other uniques changing user experience, not their behavior

??? example  "&lt;for [nonNegativeAmount] turns&gt;"
	/ durant [nonNegativeAmount] torns

	Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns

	Example: "&lt;for [3] turns&gt;"

	Applicable to: MetaModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

??? example  "&lt;hidden from users&gt;"
	/ amagat als usuaris

	Applicable to: MetaModifier

??? example  "&lt;for every [countable]&gt;"
	/ per cada [countable]

	Works for positive numbers only

	Example: "&lt;for every [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every adjacent [tileFilter]&gt;"
	/ per cada casella de [tileFilter] adjacent

	Works for positive numbers only

	Example: "&lt;for every adjacent [Farm]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every [positiveAmount] [countable]&gt;"
	/ per cada [positiveAmount] de [countable]

	Works for positive numbers only

	Example: "&lt;for every [3] [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;(modified by game speed)&gt;"
	/ (modificat per la velocitat de partida)

	Can only be applied to certain uniques, see details of each unique for specifics

	Applicable to: MetaModifier

??? example  "&lt;(modified by game progress up to [relativeAmount]%)&gt;"
	/ (modificat pel progrés de la partida fins al [relativeAmount] %)

	Can only be applied to certain uniques, see details of each unique for specifics

	Example: "&lt;(modified by game progress up to [+20]%)&gt;"

	Applicable to: MetaModifier

??? example  "&lt;Civilopedia link [pediaLink]&gt;"
	Allows linking a unique to any Civilopedia page when it is listed in Civilopedia normally. This overrides automatic links to objects in the unique's parameters.

	Example: "&lt;Civilopedia link [Units/Settler]&gt;"

	This unique is automatically hidden from users.

	Applicable to: MetaModifier

??? example  "&lt;Suppress warning [validationWarning]&gt;"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "&lt;Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]&gt;"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier


*[amount]: This indicates a whole number, possibly with a + or - sign, such as `2`, `+13`, or `-3`.
*[baseTerrain]: The name of any terrain that is a base terrain according to the json file.
*[belief]: The name of any belief.
*[beliefType]: 'Pantheon', 'Follower', 'Founder' or 'Enhancer'
*[buildingName]: The name of any building.
*[civWideStat]: All the following stats have civ-wide fields: `Gold`, `Science`, `Culture`, `Faith`.
*[combatantFilter]: This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`.
*[costOrStrength]: `Cost` or `Strength`.
*[countable]: This indicates a number or a numeric variable.They can be tested in the developer console with `civ checkcountable` - for example, `civ checkcountable "[Iron]+2"`.
*[difficulty]: The name of any difficulty.
*[era]: The name of any era.
*[eraFilter]: The name of an era, `any era`, `Starting Era`, `pre-[era]`, `post-[era]`.
*[event]: The name of any event.
*[foundingOrEnhancing]: `founding` or `enhancing`.
*[fraction]: Indicates a fractional number, which can be negative.
*[improvementName]: The name of any improvement excluding 'Cancel improvement order'
*[leaderTitle]: Provides a leader title that includes the leader's name in parameters.
*[modFilter]: A Mod name, case-sensitive _or_ a simple wildcard filter beginning and ending in an Asterisk, case-insensitive.
Note that this must use the Mod name as Unciv displays it, not the Repository name.
There is a conversion affecting dashes and leading/trailing blanks. Please make sure not to get confused.
*[nonNegativeAmount]: This indicates a non-negative whole number, larger than or equal to zero, a '+' sign is optional.
*[pediaLink]: A Civilopedia link in the form category/entry.
*[policy]: The name of any policy.
*[policyFilter]: The name of any policy, a filtering Unique, any branch (matching only the branch itself), a branch name with " Completed" appended (matches if the branch is completed), or a policy branch as `[branchName] branch` (matching all policies in that branch).
*[positiveAmount]: This indicates a positive whole number, larger than zero, a '+' sign is optional.
*[promotion]: The name of any promotion.
*[relativeAmount]: This indicates a number, usually with a + or - sign, such as `+25` (this kind of parameter is often followed by '%' which is nevertheless not part of the value).
*[resource]: The name of any resource.
*[resourceFilter]: A resource name, type, 'all', or a Stat listed in the resource's improvementStats.
*[specialist]: The name of any specialist.
*[speed]: The name of any speed.
*[stat]: This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!
*[stats]: For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!
*[stockpile]: The name of any stockpiled resource.
*[stockpiledResource]: The name of any stockpiled resource.
*[tech]: The name of any tech.
*[terrainFeature]: The name of any terrain that is a terrain feature according to the json file.
*[tileFilter]: Anything that can be used either in an improvementFilter or in a terrainFilter can be used here, plus 'unimproved'
*[unitNameGroup]: The name of a unit name group found in UnitNameGroups.json, or one of their unique tags.
*[unitTriggerTarget]: `This Unit` or `Target Unit`.
*[unitType]: Can be 'Land', 'Water', 'Air', any unit type, a filtering Unique on a unit type, or a multi-filter of these.
*[validationWarning]: Suppresses one specific Ruleset validation warning. This can specify the full text verbatim including correct upper/lower case, or it can be a wildcard case-insensitive simple pattern starting and ending in an asterisk ('*'). If the suppression unique is used within an object or as modifier (not ModOptions), the wildcard symbols can be omitted, as selectivity is better due to the limited scope.
*[victoryType]: The name of any victory type: 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time' or one of your mod's VictoryTypes.json names.
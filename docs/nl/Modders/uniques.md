# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)

## Triggerable uniques（Activeerbare）
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Verwijder [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Verkoop [buildingFilter] gebouwen [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	/ Gratis [unit] verschijnt

	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[positiveAmount] free [unit] units appear"
	/ [positiveAmount] gratis [unit] eenheden verschijnen

	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "A [unit] rebels"
	/ Een [unit] is in opstand gekomen

	Example: "A [Musketman] rebels"

	Applicable to: Triggerable

??? example  "[positiveAmount] [unit]s rebel"
	/ [positiveAmount] [unit](en/s) zijn in opstand gekomen

	Example: "[3] [Musketman]s rebel"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	/ Gratis Sociaal Beleid

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Social Policies"
	/ [positiveAmount] gratis Sociale Beleiden

	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	/ Het rijk betreedt een Gouden Tijdperk

	Applicable to: Triggerable

??? example  "Empire enters a [positiveAmount]-turn Golden Age"
	/ Het rijk betreedt een Gouden Tijdperk van [positiveAmount] beurten

	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	/ Gratis Groot Persoon

	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	/ [amount] bevolking [cityFilter]

	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	/ [amount] bevolking in een willekeurige stad

	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	/ Ontdek [tech]

	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy/belief]"
	/ Neem [policy/belief] aan

	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter]"
	/ Verwijder [policyFilter]

	Example: "Remove [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter] and refund [amount]% of its cost"
	/ Verwijder [policyFilter] en restitueer [amount]% van de kosten

	Example: "Remove [Oligarchy] and refund [3]% of its cost"

	Applicable to: Triggerable

??? example  "Free Technology"
	/ Gratis Technologie

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Technologies"
	/ [positiveAmount] Gratis Technologieën

	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[positiveAmount] free random researchable Tech(s) from the [eraFilter]"
	/ [positiveAmount] gratis willekeurige onderzoekbare Technologie(ën) uit de [eraFilter]

	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	/ Onthult de volledige kaart

	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	/ Krijg een gratis [beliefType] geloof

	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	/ Activeert stemming voor de Diplomatieke Overwinning

	Applicable to: Triggerable

??? example  "Instantly consumes [positiveAmount] [stockpiledResource]"
	/ Verbruikt onmiddellijk [positiveAmount] [stockpiledResource]

	Example: "Instantly consumes [3] [Mana]"

	Applicable to: Triggerable

??? example  "Instantly provides [positiveAmount] [stockpiledResource]"
	/ Levert onmiddellijk [positiveAmount] [stockpiledResource]

	Example: "Instantly provides [3] [Mana]"

	Applicable to: Triggerable

??? example  "Set [stockpile] to [countable]"
	/ Stel [stockpile] in op [countable]

	Example: "Set [Mana] to [1000]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Instantly gain [amount] [stockpile]"
	/ Krijg onmiddellijk [amount] [stockpile]

	Example: "Instantly gain [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	/ Krijg [amount] [stat]

	Example: "Gain [3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	/ Verkrijg genoeg Geloof voor een Pantheon

	Applicable to: Triggerable

??? example  "Gain enough Faith for [positiveAmount]% of a Great Prophet"
	/ Verkrijg genoeg Geloof voor [positiveAmount]% van een Grote Profeet

	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Research [relativeAmount]% of [tech]"
	/ Onderzoek [relativeAmount]% van [tech]

	Example: "Research [+20]% of [Agriculture]"

	Applicable to: Triggerable

??? example  "Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius"
	/ Verkrijg controle over [tileFilter] tegels in een straal van [nonNegativeAmount] tegels

	Example: "Gain control over [Farm] tiles in a [3]-tile radius"

	Applicable to: Triggerable

??? example  "Gain control over [positiveAmount] tiles [cityFilter]"
	/ Verkrijg controle over [positiveAmount] tegels [cityFilter]

	Example: "Gain control over [3] tiles [in all cities]"

	Applicable to: Triggerable

??? example  "Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius"
	/ Onthul tot [positiveAmount/'all'] [tileFilter] binnen een straal van [positiveAmount] tegels

	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	/ Activeert de volgende wereldwijde waarschuwing: [comment]

	Supported on Policies and Technologies.

	For other targets, the generated Notification may not read nicely, and will likely not support translation. Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_, and that difference is currently handled by mapping text.

	Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts.

	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "Promotes all spies [positiveAmount] time(s)"
	/ Promoveert alle spionnen [positiveAmount] keer

	Example: "Promotes all spies [3] time(s)"

	Applicable to: Triggerable

??? example  "Gain an extra spy"
	/ Krijg een extra spion

	Applicable to: Triggerable

??? example  "Turn this tile into a [terrainName] tile"
	/ Verander deze tegel in een [terrainName] tegel

	Example: "Turn this tile into a [Forest] tile"

	Applicable to: Triggerable

??? example  "Remove [resourceFilter] resources from this tile"
	/ Verwijder [resourceFilter] grondstoffen van deze tegel

	Example: "Remove [Strategic] resources from this tile"

	Applicable to: Triggerable

??? example  "Remove [improvementFilter] improvements from this tile"
	/ Verwijder [improvementFilter] verbeteringen van deze tegel

	Example: "Remove [All Road] improvements from this tile"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Works only with promotions that are valid for the unit's type - or for promotions that do not specify any.

	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [positiveAmount] cities for free"
	/ Geeft het goedkoopste [stat] gebouw gratis in je eerste [positiveAmount] steden

	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [positiveAmount] cities for free"
	/ Geeft een gratis [buildingName] in je eerste [positiveAmount] steden

	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Triggers a [event] event"
	/ Activeert een [event] evenement

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

## UnitTriggerable uniques（EenheidActiveerbare）
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "[unitTriggerTarget] heals [positiveAmount] HP"
	/ [unitTriggerTarget] geneest [positiveAmount] HP

	Example: "[This Unit] heals [3] HP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] takes [positiveAmount] damage"
	/ [unitTriggerTarget] neemt [positiveAmount] schade

	Example: "[This Unit] takes [3] damage"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [amount] XP"
	/ [unitTriggerTarget] krijgt [amount] XP

	Example: "[This Unit] gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free"
	/ [unitTriggerTarget] krijgt gratis promotie

	Example: "[This Unit] upgrades for free"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free including special upgrades"
	/ [unitTriggerTarget] krijgt promotie gratis inclusief speciale promoties

	Example: "[This Unit] upgrades for free including special upgrades"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] promotion"
	/ [unitTriggerTarget] krijgt de [promotion] promotie

	Example: "[This Unit] gains the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] promotion"
	/ [unitTriggerTarget] verliest de [promotion] promotie

	Example: "[This Unit] loses the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [positiveAmount] movement"
	/ [unitTriggerTarget] krijgt [positiveAmount] beweging

	Example: "[This Unit] gains [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses [positiveAmount] movement"
	/ [unitTriggerTarget] verliest [positiveAmount] beweging

	Example: "[This Unit] loses [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)"
	/ [unitTriggerTarget] krijgt de [promotion] status voor [positiveAmount] beurt(en)

	Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns.

	Example: "[This Unit] gains the [Shock I] status for [3] turn(s)"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] status"
	/ [unitTriggerTarget] verliest de [promotion] status

	Example: "[This Unit] loses the [Shock I] status"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] is destroyed"
	/ [unitTriggerTarget] is vernietigd

	Example: "[This Unit] is destroyed"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gets a name from the [unitNameGroup] group"
	/ [unitTriggerTarget] krijgt een naam van de [unitNameGroup] groep

	Example: "[This Unit] gets a name from the [Scientist] group"

	Applicable to: UnitTriggerable

## Global uniques（Globaal）
!!! note ""

    Uniques that apply globally. Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.

??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] van elke specialist [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] populatie [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] social policies adopted"
	/ [stats] per [positiveAmount] aangenomen sociaal beleid

	Only works for civ-wide stats

	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [positiveAmount] [civWideStat]"
	/ [stats] per elke [positiveAmount] [civWideStat]

	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] in steden op [terrainFilter] tegels

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] van alle [buildingFilter] gebouwen

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] van [tileFilter] tegels [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] van elke handelsroute

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% [stat] van elke [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% opbrengst van elke [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	/ [relativeAmount]% [stat] van Stadstaten

	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	/ [relativeAmount]% [stat] van Handelsroutes

	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	/ Doet [stat] [cityFilter] te niet

	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	/ Doet groei [cityFilter] te niet

	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% productie wanneer je [buildingFilter] gebouwen bouwt [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% productie wanneer je [baseUnitFilter] eenheden aan het bouwen bent [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Productie wanneer je [buildingFilter] wonderen bouwt [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Productie voor alle gebouwen die al in de hoofdstad bestaan

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount]% opbrengst van het plunderen van tegels

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount]% Gezondheid van het plunderen van tegels

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "Military Units gifted from City-States start with [positiveAmount] XP"
	/ Militaire Eenheden gekregen van Stadstaten beginnen met [positiveAmount] XP

	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation"
	/ Militaristische Stadstaten geven eenheden [positiveAmount] keer sneller als je oorlog voert tegen een andere natie

	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	/ Giften van goud aan Stadstaten genereren [relativeAmount]% meer Invloed

	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns"
	/ Je kan goud gebruiken om een Stadstaat die een bondgenoot voor [nonNegativeAmount] beurten is te annexeren of een stadstaat te maken

	Example: "Can spend Gold to annex or puppet a City-State that has been your Ally for [3] turns"

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	/ Stadstaat-territorium geldt altijd als bevriend territorium

	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	/ Geallieerde Stadstaten schenken soms Grote Mensen

	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	/ [relativeAmount]% Stadstaat invloed degradatie

	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	/ Rustpunt van Invloed met Stadstaten is verhoogd met [amount]

	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	/ Geallieerde Stadstaten geven [stat] gelijk aan [relativeAmount]% van wat ze zelf produceren

	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	/ [relativeAmount]% grondstoffen geschonken door Stadstaten

	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	/ [relativeAmount]% Geluk van luxe grondstoffen geschonken door Stadstaten

	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	/ Stadstaat invloed herstelt zich met twee keer de normale snelheid

	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% groei [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% voedsel is overgebracht na bevolkingsgroei [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount]% voedsel consumptie door [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	/ [relativeAmount]% ongeluk van het aantal steden

	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Ongelukkigheid van [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	/ [amount] Geluk van elk soort luxe grondstof

	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	/ Behoud [relativeAmount]% van het geluk van een luxeproduct nadat de laatste kopie verhandeld is

	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	/ [relativeAmount]% van overtollig geluk omgezet in [stat]

	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	/ Kan [baseUnitFilter] eenheden niet maken

	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	/ Laat productie van ruimteschip-onderdelen toe

	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan [baseUnitFilter] eenheden kopen tegen [amount] [stat] [cityFilter] aan een verhogende prijs ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan [buildingFilter] gebouwen kopen tegen [amount] [stat] [cityFilter] aan een verhogende prijs ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Kan [baseUnitFilter] eenheden kopen tegen [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Kan [buildingFilter] gebouwen kopen tegen [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Kan [baseUnitFilter] eenheden kopen met [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Kan [buildingFilter] gebouwen kopen met [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan [baseUnitFilter] eenheden kopen met [stat] voor [nonNegativeAmount] maal hun normale Productiekost

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan [buildingFilter] gebouwen kopen met [stat] voor [nonNegativeAmount] maal hun normale Productiekost

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ [stat] kosten om items aan te schaffen in steden [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat] kosten om [buildingFilter] gebouwen te kopen [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat] kosten om [baseUnitFilter] eenheden te kopen [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	/ Maakt conversie mogelijk van stadsproductie naar [civWideStat]

	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	/ Verbetert bewegingssnelheid op wegen

	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	/ Wegen verbinden tegels over rivieren

	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	/ [relativeAmount]% onderhoud aan wegen en spoorwegen

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	/ Geen onderhoudskosten voor verbeteringen in [tileFilter] tegels

	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount]% bouwtijd voor [improvementFilter] verbeteringen

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Kan [improvementFilter] verbeteringen bouwen met een relatieve snelheid van [relativeAmount]%

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% onderhoudskosten voor [buildingFilter] gebouwen [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Verwijder [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Verkoop [buildingFilter] gebouwen [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% cultuurkosten voor natuurlijke grensuitbreiding [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% goudkosten voor het verkrijgen van tegels [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	/ Elke gestichte stad verhoogt de cultuurkosten van beleid [relativeAmount]% minder dan normaal

	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	/ [relativeAmount]% Cultuurkosten om nieuw beleid te adopteren

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "Each city founded increases Science cost of Technologies [relativeAmount]% less than normal"
	/ Elke gestichte stad verhoogt de wetenschapskosten van Technologieën [relativeAmount]% minder dan normaal

	Example: "Each city founded increases Science cost of Technologies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Science cost of researching new Technologies"
	/ [relativeAmount]% Wetenschapskosten om nieuwe Technologieën te onderzoeken

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Science cost of researching new Technologies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	/ [stats] voor elk gekend Natuurlijk Wonder

	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)"
	Example: "[+1 Gold, +2 Production] for discovering a Natural Wonder (bonus enhanced to [+1 Gold, +2 Production] if first to discover it)"

	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% Groot Persoon generatie [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Goud van Grote Handelaar handelsmissies

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Grote Generaal levert dubbele gevechtsbonus op

	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	/ Verkrijg een gratis Groot Persoon aan het einde van elke [comment] (elke 394 jaar), na het ontdekken van [tech]. Elke bonuspersoon kan slechts éénmaal gekozen worden.

	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	/ Als De Lange Telling activeert zal het jaar op het wereldscherm weergegeven worden als de traditionele Maya Lange Telling.

	Applicable to: Global

??? example  "[amount] Unit Supply"
	/ [amount] eenheidbevoorrading

	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [positiveAmount] population [cityFilter]"
	/ [amount] eenheidbevoorrading per [positiveAmount] bevolking [cityFilter]

	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	/ [amount] eenheidbevoorrading per stad

	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	/ [amount] eenheden kosten geen onderhoud

	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	/ Eenheden in steden kosten geen Onderhoud

	Applicable to: Global

??? example  "Enables embarkation for land units"
	/ Laat inschepen toe voor landeenheden

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	/ Laat [mapUnitFilter] eenheden toe oceaan tegels te betreden

	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	/ Landeenheden mogen [terrainName] tegels oversteken nadat de eerste [baseUnitFilter] verkregen is

	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory"
	/ Vijandige [mapUnitFilter] eenheden moeten [positiveAmount] extra bewegingspunten spenderen binnen jouw grondgebied

	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Nieuwe [baseUnitFilter] eenheden starten met [amount] XP [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Alle nieuwe [baseUnitFilter] eenheden [cityFilter] ontvangen de [promotion] promotie

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ [mapUnitFilter] Eenheden naast deze stad helen [amount] HP per beurt als ze aan het helen zijn.

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% XP required for promotions"
	/ [relativeAmount]% XP vereist voor promoties

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP required for promotions"

	Applicable to: Global

??? example  "[relativeAmount]% City Strength from defensive buildings"
	/ [relativeAmount]% Stadsterkte van defensieve gebouwen

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount]% Kracht voor steden

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Levert [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% [resourceFilter] resource production"
	/ [relativeAmount]% productie van [resourceFilter] grondstof

	Example: "[+20]% [Strategic] resource production"

	Applicable to: Global

??? example  "Enables establishment of embassies"
	/ Maakt het vestigen van ambassades mogelijk

	Applicable to: Global

??? example  "Requires establishing embassies to conduct advanced diplomacy"
	/ Vereist het vestigen van ambassades om geavanceerde diplomatie te voeren

	Applicable to: Global

??? example  "Enables Open Borders agreements"
	/ Maakt Vrije Grenzen overeenkomsten mogelijk

	Applicable to: Global

??? example  "Enables Research agreements"
	/ Maakt onderzoeksovereenkomsten mogelijk

	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	/ Wetenschap verkregen van onderzoeksovereenkomsten [relativeAmount]%

	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	/ Maakt Defensieve Verbonden mogelijk

	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	/ Bij het sluiten van een vriendschapspact krijgen beide partijen een [relativeAmount]% bonus bij grote persoon generatie

	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	/ Invloed van alle andere beschavingen bij alle Stadstaten neemt [relativeAmount]% sneller af

	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	/ Krijg [amount] Invloed bij een [baseUnitFilter] geschenk aan een Stadstaat

	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	/ Rustpunt voor invloed bij stadstaten met deze religie [amount]

	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	/ Gewaarschuwd voor nieuwe Barbarenkampen

	Applicable to: Global

??? example  "Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities"
	/ Krijg [relativeAmount]% Goud van Barbaren kampen en Steden plunderen

	Example: "Receive [+20]% Gold from Barbarian encampments and pillaging Cities"

	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	/ Als je een kamp veroverd, verdien [amount] Goud en recruteer een barbaareenheid

	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	/ Als je een [mapUnitFilter] verslaat, verdien [amount] Goud en recruteer het

	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	/ Kan [amount] extra [beliefType] geloven kiezen bij het [foundingOrEnhancing] van een religie

	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	/ Kan [amount] extra geloofsartikelen kiezen bij het [foundingOrEnhancing] van een religie

	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	/ [stats] als een stad voor het eerst deze religie aanneemt

	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% natuurlijke verspreiding van religie [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ Religie verspreidt zich vanzelf naar steden op [amount] tegels afstand

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	/ Kan de equivalenten van een grote profeet niet spontaan genereren

	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	/ [relativeAmount]% Geloofskosten voor het genereren van Groot Profeet-equivalenten

	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "[relativeAmount]% spy effectiveness [cityFilter]"
	/ [relativeAmount]% spion effectiviteit [cityFilter]

	Example: "[+20]% spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% enemy spy effectiveness [cityFilter]"
	/ [relativeAmount]% vijandige spion effectiviteit [cityFilter]

	Example: "[+20]% enemy spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "New spies start with [amount] level(s)"
	/ Nieuwe spionnen starten met [amount] level(s)

	Example: "New spies start with [3] level(s)"

	Applicable to: Global

??? example  "Triggers victory"
	/ Veroorzaakt een overwinning

	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	/ Veroorzaakt een Culturele overwinning bij voltooiing

	Applicable to: Global

??? example  "May buy items in puppet cities"
	/ Kan items kopen in vazalsteden

	Applicable to: Global

??? example  "May not annex cities"
	/ Mag geen steden annexeren

	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	/ "Leent" stadsnamen van andere beschavingen in de spelsessie

	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	/ Steden worden [amount] keer zo snel verwoest

	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	/ Ontvang een technologische boost wanneer wetenschappelijke gebouwen/wonderen in de hoofdstad worden gebouwd

	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	/ [relativeAmount]% Gouden Tijdperk lengte

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	/ Bevolkingsverlies door nucleaire aanvallen [relativeAmount]% [cityFilter]

	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	/ Schade aan het garnizoen door nucleaire aanvallen [relativeAmount]% [cityFilter]

	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	/ Rebelse eenheden kunnen verschijnen

	Applicable to: Global

??? example  "Cannot build [buildingFilter] buildings"
	Example: "Cannot build [Culture] buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Kracht

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] Kracht

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Kracht, afnemend met afstand van de hoofdstad

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% bij Flankaanval-bonussen

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] extra aanvallen per beurt

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Actie

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Zicht

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Afstand

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] Luchtonderscheppingsbereik

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] HP tijdens herstel

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Kracht bij religieverspreiding

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Bij het verspreiden van religie in een stad verkrijg je [amount] keer het aantal volgers van andere religies als [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Afstandsaanvallen mogen over obstakels worden uitgevoerd

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	/ Geen verdedigingsbonus van terrein

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Geen negatieve verdedigingspenalty van terrein

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ Geen schadestraf voor gewonde eenheden

	Applicable to: Global, Unit

??? example  "Unable to capture cities"
	/ Niet in staat steden te veroveren

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ Niet in staat tegels te plunderen

	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	/ Geen bewegingskost om te plunderen

	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	/ Kan genezen buiten bevriend gebied

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Alle genezingseffecten verdubbeld

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Geneest [amount] schade als het een eenheid doodt

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Kan alleen genezen door te plunderen

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% onderhoudskosten

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% Goudkosten voor upgrade

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Verdien [amount]% van de schade toegebracht aan [combatantFilter] eenheden als [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Bij het innemen van een stad ontvang je onmiddellijk [amount] maal haar [stat] productie als [stockpile]

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Verdien [amount]% van de [costOrStrength] van gedode [mapUnitFilter] eenheden als [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	/ [amount] XP verkregen door gevechten

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% XP verkregen door gevechten

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] wordt [relativeAmount]% sneller verkregen

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] Bewegingspunten om te ontschepen

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] Bewegingspunten om in te schepen

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

## Nation uniques（Natie）
??? example  "Starts with [tech]"
	/ Begint met [tech]

	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	/ Start met [policy] aangenomen

	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	/ Alle eenheden bewegen zich door Bos- en Jungle-tegels in bevriend gebied alsof daar wegen zijn. Deze tegels kunnen gebruikt worden om stadsverbindingen tot stand te brengen nadat het Wiel is ontdekt

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	/ Eenheden negeren terreinkosten bij het betreden van tegels met Heuvels

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Will not be chosen for new games"
	/ Zal niet gekozen worden voor nieuwe spelsessies

	Applicable to: Nation

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Personality uniques（Persoonlijkheid）
??? example  "Will not build [baseUnitFilter/buildingFilter]"
	/ Zal [baseUnitFilter/buildingFilter] niet bouwen

	Example: "Will not build [Melee]"

	Applicable to: Personality

## Era uniques（Tijdperk）
??? example  "Starting in this era disables religion"
	/ In dit tijdperk beginnen schakelt religie uit

	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	/ Elke grote Beschaving krijgt een spion zodra een beschaving dit tijdperk betreedt

	Applicable to: Era

## Tech uniques
??? example  "Starting tech"
	/ Starttechnologie

	Applicable to: Tech

??? example  "Can be continually researched"
	/ Kan voortdurend worden onderzocht

	Applicable to: Tech

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Cannot be hurried"
	/ Niet versnelbaar

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

## Policy uniques（Beleid）
??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

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

## FounderBelief uniques（OprichterGeloof）
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	/ [stats] voor elke wereldwijde stad met deze religie

	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [positiveAmount] global followers [cityFilter]"
	/ [stats] voor elke [positiveAmount] wereldwijde volgers [cityFilter]

	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

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

## FollowerBelief uniques（VolgerGeloof）
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] van elke specialist [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] populatie [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] in steden op [terrainFilter] tegels

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] van alle [buildingFilter] gebouwen

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] van [tileFilter] tegels [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] van elke handelsroute

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% [stat] van elke [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% opbrengst van elke [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% productie wanneer je [buildingFilter] gebouwen bouwt [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% productie wanneer je [baseUnitFilter] eenheden aan het bouwen bent [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Productie wanneer je [buildingFilter] wonderen bouwt [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Productie voor alle gebouwen die al in de hoofdstad bestaan

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% groei [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% voedsel is overgebracht na bevolkingsgroei [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount]% voedsel consumptie door [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Ongelukkigheid van [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan [baseUnitFilter] eenheden kopen tegen [amount] [stat] [cityFilter] aan een verhogende prijs ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan [buildingFilter] gebouwen kopen tegen [amount] [stat] [cityFilter] aan een verhogende prijs ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Kan [baseUnitFilter] eenheden kopen tegen [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Kan [buildingFilter] gebouwen kopen tegen [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Kan [baseUnitFilter] eenheden kopen met [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Kan [buildingFilter] gebouwen kopen met [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan [baseUnitFilter] eenheden kopen met [stat] voor [nonNegativeAmount] maal hun normale Productiekost

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan [buildingFilter] gebouwen kopen met [stat] voor [nonNegativeAmount] maal hun normale Productiekost

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ [stat] kosten om items aan te schaffen in steden [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat] kosten om [buildingFilter] gebouwen te kopen [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat] kosten om [baseUnitFilter] eenheden te kopen [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% onderhoudskosten voor [buildingFilter] gebouwen [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% cultuurkosten voor natuurlijke grensuitbreiding [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% goudkosten voor het verkrijgen van tegels [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% Groot Persoon generatie [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Nieuwe [baseUnitFilter] eenheden starten met [amount] XP [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Alle nieuwe [baseUnitFilter] eenheden [cityFilter] ontvangen de [promotion] promotie

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ [mapUnitFilter] Eenheden naast deze stad helen [amount] HP per beurt als ze aan het helen zijn.

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount]% Kracht voor steden

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Levert [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% natuurlijke verspreiding van religie [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ Religie verspreidt zich vanzelf naar steden op [amount] tegels afstand

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion"
	/ Verdien [amount]% van de [costOrStrength] van [mapUnitFilter] eenheden als [stockpile] wanneer ze worden gedood binnen 4 tegels van een stad die deze religie aanhangt

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

## Building uniques（Gebouw）
??? example  "[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]"
	/ [positiveAmount]% van [stat] van elke [improvementFilter/buildingFilter] in de stad toegevoegd aan [resource]

	Example: "[3]% of [Culture] from every [All Road] in the city added to [Iron]"

	Applicable to: Building

??? example  "Consumes [amount] [resource]"
	/ Gebruikt [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Kost [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Onbouwbaar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Kan niet gekocht worden

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Kan gekocht worden met [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Kan gekocht worden voor [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Beperkt tot [amount] per Beschaving

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ Overtollig voedsel wordt omgezet in Productie tijdens bouw

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Vereist minstens [amount] bevolking

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Veroorzaakt een wereldwijde waarschuwing bij het starten van de bouw

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Veroorzaakt een wereldwijde waarschuwing bij voltooiing

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Kosten nemen toe met [amount] per stad in bezit

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ Kosten nemen toe met [amount] wanneer het gebouwd isw

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount]% productie kosten

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Kan alleen gebouwd worden

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	/ Moet een [tileFilter] bezitten binnen [amount] tegels

	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	/ Maakt nucleair wapen mogelijk

	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	/ Mag niet op [tileFilter] zijn

	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	/ Mag niet naast [tileFilter] zijn

	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	/ Niet verkoopbaar

	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	/ Duidt de hoofdstad aan

	Applicable to: Building

??? example  "Moves to new capital when capital changes"
	/ Verplaatst naar de nieuwe hoofdstad wanneer de hoofdstad verandert

	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	/ Levert 1 extra exemplaar van elke verbeterde luxegrondstof bij deze stad

	Applicable to: Building

??? example  "Destroyed when the city is captured"
	/ Wordt vernietigd wanneer de stad wordt ingenomen

	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	/ Nooit vernietigd bij verovering van de stad

	Applicable to: Building

??? example  "[relativeAmount]% Gold given to enemy if city is captured"
	/ [relativeAmount]% Goud aan vijand gegeven als stad is veroverd

	Example: "[+20]% Gold given to enemy if city is captured"

	Applicable to: Building

??? example  "Removes extra unhappiness from annexed cities"
	/ Verwijdert extra ongelukkigheid van geannexeerde steden

	Applicable to: Building

??? example  "Connects trade routes over water"
	/ Verbindt handelsroutes over water

	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	/ Wordt automatisch gebouwd in alle steden waar dit mogelijk is

	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	/ Plaatst een [improvementName] verbetering op een specifieke tegel

	When choosing to construct this building, the player must select a tile where the improvement can be built. Upon building completion, the tile will gain this improvement. Limited to one per building.

	Example: "Creates a [Trading Post] improvement on a specific tile"

	This unique does not support conditionals.

	Applicable to: Building

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Kan [amount] extra [mapUnitFilter] eenheden meenemen

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Spaceship part"
	/ Ruimteschip onderdeel

	Applicable to: Building, Unit

??? example  "Cannot be hurried"
	/ Niet versnelbaar

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

## UnitAction uniques（EenheidActie）
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	/ Sticht een nieuwe stad

	Applicable to: UnitAction

??? example  "Founds a new puppet city"
	/ Sticht een nieuwe vazalstad

	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	/ Kan onmiddellijk een [improvementFilter] verbetering bouwen

	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	/ Kan religie verspreiden

	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	/ Kan andere religies uit steden verwijderen

	Applicable to: UnitAction

??? example  "May found a religion"
	/ Kan een religie stichten

	Applicable to: UnitAction

??? example  "May enhance a religion"
	/ Kan een religie uitbreiden

	Applicable to: UnitAction

??? example  "Can transform to [unit]"
	/ Kan transformeren naar [unit]

	By default consumes all movement

	Example: "Can transform to [Musketman]"

	Applicable to: UnitAction

## Unit uniques（Eenheid）
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount]% opbrengst van het plunderen van tegels

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount]% Gezondheid van het plunderen van tegels

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount]% bouwtijd voor [improvementFilter] verbeteringen

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Kan [improvementFilter] verbeteringen bouwen met een relatieve snelheid van [relativeAmount]%

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Goud van Grote Handelaar handelsmissies

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Grote Generaal levert dubbele gevechtsbonus op

	Applicable to: Global, Unit

??? example  "Consumes [amount] [resource]"
	/ Gebruikt [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Kost [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Onbouwbaar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Kan niet gekocht worden

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Kan gekocht worden met [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Kan gekocht worden voor [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Beperkt tot [amount] per Beschaving

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ Overtollig voedsel wordt omgezet in Productie tijdens bouw

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Vereist minstens [amount] bevolking

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Veroorzaakt een wereldwijde waarschuwing bij het starten van de bouw

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Veroorzaakt een wereldwijde waarschuwing bij voltooiing

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Kosten nemen toe met [amount] per stad in bezit

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ Kosten nemen toe met [amount] wanneer het gebouwd isw

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount]% productie kosten

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Kan alleen gebouwd worden

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "May create improvements on water resources"
	/ Kan verbeteringen op watergrondstoffen maken

	Applicable to: Unit

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	/ Kan [improvementFilter/terrainFilter] verbeteringen op tegels bouwen

	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	/ Kan worden toegevoegd aan [comment] in de hoofdstad

	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	/ Voorkomt de verspreiding van religie naar de stad waar het naast staat

	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	/ Verwijdert andere religies bij het verspreiden van een religie

	Applicable to: Unit

??? example  "May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away"
	/ Kan parachutespringen naar [tileFilter] tegels tot op [positiveAmount] tegels afstand

	Example: "May Paradrop to [Farm] tiles up to [3] tiles away"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	/ Kan een Luchtaanval uitvoeren

	Applicable to: Unit

??? example  "Can speed up construction of a building"
	/ Kan de bouw van een gebouw versnellen

	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	/ Kan de bouw van een wonder versnellen

	Applicable to: Unit

??? example  "Can hurry technology research"
	/ Kan technologisch onderzoek versnellen

	Applicable to: Unit

??? example  "Can generate a large amount of culture"
	/ Kan een grote hoeveelheid cultuur genereren

	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	/ Kan een handelsmissie ondernemen met een Stadstaat, wat een grote som Goud en [amount] Invloed oplevert

	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Automation is a primary action"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Kracht

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] Kracht

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Kracht, afnemend met afstand van de hoofdstad

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% bij Flankaanval-bonussen

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles"
	/ [relativeAmount]% Kracht voor vijandige [mapUnitFilter] eenheden in aangrenzende [tileFilter] tegels

	Example: "[+20]% Strength for enemy [Wounded] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	/ [relativeAmount]% bonus Kracht voor [mapUnitFilter] eenheden binnen [amount] tegels

	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] extra aanvallen per beurt

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Actie

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Zicht

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Afstand

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] Luchtonderscheppingsbereik

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] HP tijdens herstel

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Kracht bij religieverspreiding

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Bij het verspreiden van religie in een stad verkrijg je [amount] keer het aantal volgers van andere religies als [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Can only attack [combatantFilter] units"
	/ Kan alleen [combatantFilter] eenheden aanvallen

	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	/ Kan alleen [tileFilter] tegels aanvallen

	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	/ Kan niet aanvallen

	Applicable to: Unit

??? example  "Must set up to ranged attack"
	/ Moet zich opstellen voor een aanval op afstand

	Applicable to: Unit

??? example  "Self-destructs when attacking"
	/ Vernietigt zichzelf bij aanvallen

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	/ Elimineert de straf voor aanvallen vanaf een kust

	Applicable to: Unit

??? example  "May attack when embarked"
	/ Kan aanvallen tijdens inscheping

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	/ Elimineert de straf voor aanvallen over een rivier

	Applicable to: Unit

??? example  "Blast radius [amount]"
	/ Ontploffingsradius [amount]

	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Afstandsaanvallen mogen over obstakels worden uitgevoerd

	Applicable to: Global, Unit

??? example  "Nuclear weapon of Strength [amount]"
	/ Nucleair wapen met [amount] Kracht

	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "No defensive terrain bonus"
	/ Geen verdedigingsbonus van terrein

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Geen negatieve verdedigingspenalty van terrein

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ Geen schadestraf voor gewonde eenheden

	Applicable to: Global, Unit

??? example  "Uncapturable"
	/ Niet te veroveren

	Applicable to: Unit

??? example  "Withdraws before melee combat"
	/ Trekt zich terug voor mêlee-gevecht

	Applicable to: Unit

??? example  "Unable to capture cities"
	/ Niet in staat steden te veroveren

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ Niet in staat tegels te plunderen

	Applicable to: Global, Unit

??? example  "Destroys [cityFilter] cities instead of capturing"
	/ Vernietigd [cityFilter] steden in plaats van veroveren

	The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities.Capital cities (including city states) are immune to this effect.

	Example: "Destroys [in all cities] cities instead of capturing"

	Applicable to: Unit

??? example  "No movement cost to pillage"
	/ Geen bewegingskost om te plunderen

	Applicable to: Global, Unit

??? example  "Can move after attacking"
	/ Kan zich verplaatsen na aanvallen

	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	/ Draag Beweging over aan [mapUnitFilter]

	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	/ Kan onmiddellijk bewegen nadat het gekocht is

	Applicable to: Unit

??? example  "May heal outside of friendly territory"
	/ Kan genezen buiten bevriend gebied

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Alle genezingseffecten verdubbeld

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Geneest [amount] schade als het een eenheid doodt

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Kan alleen genezen door te plunderen

	Applicable to: Global, Unit

??? example  "Unit will heal every turn, even if it performs an action"
	/ Eenheid geneest elke beurt, zelfs als deze een actie onderneemt

	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	/ Alle aangrenzende eenheden genezen [amount] HP tijdens herstel

	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	/ Geen Zicht

	Applicable to: Unit

??? example  "Can see over obstacles"
	/ Kan over obstakels heen zien

	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	/ Kan [amount] [mapUnitFilter] eenheden vervoeren

	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Kan [amount] extra [mapUnitFilter] eenheden meenemen

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	/ Kan niet vervoerd worden door [mapUnitFilter] eenheden

	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	/ [relativeAmount]% kans om luchtaanvallen te onderscheppen

	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	/ Schade geleden door onderscheppingen verminderd met [relativeAmount]%

	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	/ [relativeAmount]% Schade bij onderschepping

	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	/ [amount] extra onderscheppingen mogen per beurt worden uitgevoerd

	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	/ Kan niet worden onderschept

	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	/ Kan [mapUnitFilter] eenheden niet onderscheppen

	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	/ [relativeAmount]% Kracht bij het uitvoeren van een Luchtaanval

	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% onderhoudskosten

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% Goudkosten voor upgrade

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Verdien [amount]% van de schade toegebracht aan [combatantFilter] eenheden als [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Bij het innemen van een stad ontvang je onmiddellijk [amount] maal haar [stat] productie als [stockpile]

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Verdien [amount]% van de [costOrStrength] van gedode [mapUnitFilter] eenheden als [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "May capture killed [mapUnitFilter] units"
	/ Kan gedode [mapUnitFilter] eenheden gevangen nemen

	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "[amount] XP gained from combat"
	/ [amount] XP verkregen door gevechten

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% XP verkregen door gevechten

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "Can be earned through combat"
	/ Kan verdiend worden door gevechten

	Applicable to: Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] wordt [relativeAmount]% sneller verkregen

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "Invisible to others"
	/ Onzichtbaar voor anderen

	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	/ Onzichtbaar voor niet-aangrenzende eenheden

	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	/ Kan onzichtbare [mapUnitFilter] eenheden zien

	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [unit] through ruins-like effects"
	/ Kan via ruïne-achtige effecten opwaarderen naar [unit]

	Example: "May upgrade to [Musketman] through ruins-like effects"

	Applicable to: Unit

??? example  "Can upgrade to [unit]"
	/ Kan opwaarderen naar [unit]

	Example: "Can upgrade to [Musketman]"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	/ Vernietigt tegelverbeteringen bij aanval

	Applicable to: Unit

??? example  "Cannot move"
	/ Kan niet bewegen

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	/ Dubbele beweging in [terrainFilter]

	Example: "Double movement in [Fresh Water]"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	/ Alle tegels kosten 1 beweging

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	/ Kan over watertegels reizen zonder in te schepen

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	/ Kan onbegaanbare tegels passeren

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores terrain cost"
	/ Negeert terreinkosten

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores Zone of Control"
	/ Negeert Zone van Invloed

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Rough terrain penalty"
	/ Straf in ruig terrein

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can enter ice tiles"
	/ Kan ijstegels betreden

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Cannot embark"
	/ Kan niet inschepen

	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	/ Kan geen oceaantegels betreden

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	/ Kan buitenlandse tegels betreden zonder Open Grenzen

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	/ Kan buitenlandse tegels betreden zonder Open Grenzen, maar verliest [amount] religieuze kracht elke beurt dat het daar eindigt

	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] Bewegingspunten om te ontschepen

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] Bewegingspunten om in te schepen

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "Never appears as a Barbarian unit"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "Religious Unit"
	/ Religieuze Eenheid

	Applicable to: Unit

??? example  "Spaceship part"
	/ Ruimteschip onderdeel

	Applicable to: Building, Unit

??? example  "Takes your religion over the one in their birth city"
	/ Neemt jouw religie aan boven die van hun geboortestad

	Applicable to: Unit

??? example  "Great Person - [comment]"
	/ Groot Persoon - [comment]

	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
	/ Is deel van Grote Persoon-groep [comment]

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

## UnitType uniques（EenheidType）
??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Promotion uniques（Promotie）
??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Doing so will consume this opportunity to choose a Promotion"
	/ Dit verbruikt de mogelijkheid om een promotie te kiezen

	Applicable to: Promotion

??? example  "This Promotion is free"
	/ Deze promotie is gratis

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

## Terrain uniques（Terrein）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[amount] Sight"
	/ [amount] Zicht

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
	/ Geeft [stats] aan de eerste beschaving die het ontdekt

	Example: "Grants [+1 Gold, +2 Production] to the first civilization to discover it"

	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	/ Eenheden die hun beurt eindigen op dit terrein krijgen [amount] schade

	Example: "Units ending their turn on this terrain take [3] damage"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	This unique does not support conditionals.

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	/ Geeft [promotion] ([comment]) aan aangrenzende [mapUnitFilter] eenheden voor de rest van het spel

	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	/ [amount] Kracht voor steden die op dit terrein zijn gebouwd

	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time bonus of [stats] to the closest city when cut down"
	/ Geeft een eenmalige bonus van [stats] aan de dichtstbijzijnde stad wanneer het wordt omgehakt

	Example: "Provides a one-time bonus of [+1 Gold, +2 Production] to the closest city when cut down"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Terrain

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ Tegel geeft opbrengst zonder toegewezen bevolking

	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	/ Dekt alle andere stat-bonussen van deze tegel teniet

	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	/ Alleen [improvementFilter] verbeteringen mogen op deze tegel gebouwd worden

	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	/ Blokkeert zicht vanaf tegels op dezelfde hoogte

	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	/ Heeft een hoogte van [amount] voor zichtberekeningen

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
	/ Zeldzame eigenschap

	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	/ [amount]% kans om door kernwapens te worden vernietigd

	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	/ Zoet water

	Applicable to: Terrain

??? example  "Rough terrain"
	/ Ruig terrein

	Applicable to: Terrain

??? example  "Coastal Water"
	/ Kustwater

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

## Improvement uniques
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "Consumes [amount] [resource]"
	/ Gebruikt [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Provides [amount] [resource]"
	/ Levert [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Kost [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Onbouwbaar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "[amount] Sight"
	/ [amount] Zicht

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ Tegel geeft opbrengst zonder toegewezen bevolking

	Applicable to: Terrain, Improvement

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Can also be built on tiles adjacent to fresh water"
	/ Kan ook gebouwd worden op tegels grenzend aan zoet water

	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	/ [stats] van [tileFilter] tegels

	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	/ Zorgt voor een minimale tegelopbrengst van [stats]

	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	/ Kan buiten jouw grenzen gebouwd worden

	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	/ Kan net buiten jouw grenzen gebouwd worden

	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	/ Kan alleen op [tileFilter] tegels gebouwd worden

	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	/ Kan niet op [tileFilter] tegels gebouwd worden

	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	/ Kan alleen gebouwd worden om een grondstof te verbeteren

	Applicable to: Improvement

??? example  "Does not need removal of [terrainFeature]"
	Example: "Does not need removal of [Hill]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	/ Verwijdert verwijderbare eigenschappen bij bouw

	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	/ Geeft een verdedigingsbonus van [relativeAmount]%

	Does not accept unit-based conditionals

	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	/ Kost [amount] [stat] per beurt in jouw grondgebied

	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	/ Kost [amount] [stat] per beurt

	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	/ Aangrenzende vijandelijke eenheden die hun beurt eindigen krijgen [amount] schade

	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	/ Grote Verbetering

	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	/ Geeft een willekeurige bonus bij betreden

	Applicable to: Improvement

??? example  "Unpillagable"
	/ Niet te plunderen

	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	/ Deze verbetering plunderen levert ongeveer [stats] op

	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	/ Deze verbetering plunderen levert [stats] op

	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Destroyed when pillaged"
	/ Vernietigd wanneer geplunderd

	Applicable to: Improvement

??? example  "Irremovable"
	/ Niet te verwijderen

	Applicable to: Improvement

??? example  "Will not be replaced by automated units"
	/ Wordt niet vervangen door geautomatiseerde eenheden

	Applicable to: Improvement

??? example  "Improves [resourceFilter] resource in this tile"
	/ Verbetert [resourceFilter] grondstof in deze tegel

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

## Resource uniques（Hulpbron）
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
	/ Afzettingen in [tileFilter] tegels geven altijd [amount] grondstoffen

	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	/ Kan alleen worden gegeven door Handel-Stadstaten

	Applicable to: Resource

??? example  "Stockpiled"
	/ Opgeslagen

	This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment.The current stockpiled amount can be affected with trigger uniques.

	Applicable to: Resource

??? example  "City-level resource"
	/ Stadniveau grondstof

	This resource is calculated on a per-city level rather than a per-civ level

	Applicable to: Resource

??? example  "Cannot be traded"
	/ Kan niet verhandeld worden

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
	/ Gegarandeerd met de Strategische Balans grondstofoptie

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
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Free [unit] found in the ruins"
	/ Gratis [unit] gevonden in de ruïnes

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

## Speed uniques（Snelheid）
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

## Difficulty uniques（Moeilijkheidsgraad）
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

## CityState uniques（StadStaat）
??? example  "Provides military units every ≈[positiveAmount] turns"
	/ Levert militaire eenheden elke ≈[positiveAmount] beurten

	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	/ Levert een uniek luxegoed

	Applicable to: CityState

## ModOptions uniques（ModOpties）
??? example  "Diplomatic relationships cannot change"
	/ Diplomatieke relaties kunnen niet veranderen

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can convert gold to science with sliders"
	/ Kan goud omzetten naar wetenschap via schuifregelaars

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow City States to spawn with additional units"
	/ Laat toe dat Stadstaten met extra eenheden beginnen

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can trade civilization introductions for [positiveAmount] Gold"
	/ Kan beschavingintroducties ruilen voor [positiveAmount] Goud

	Example: "Can trade civilization introductions for [3] Gold"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Disable religion"
	/ Schakel religie uit

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can only start games from the starting era"
	/ Kan alleen spellen starten vanuit het beginnende tijdperk

	In this case, 'starting era' means the first defined Era in the entire ruleset.

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze capital"
	/ Sta toe de hoofdstad te slopen

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze holy city"
	/ Sta toe de heilige stad te slopen

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

??? example  "Mod is incompatible with [modFilter]"
	/ Mod is niet compatibel met [modFilter]

	Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible.

	Example: "Mod is incompatible with [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod requires [modFilter]"
	/ Mod vereist [modFilter]

	Specifies that your Extension Mod is only available if any other Mod matching the filter is active.

	Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue.

	Example: "Mod requires [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	/ Wordt best enkel gebruikt als permanente audiovisuele mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	/ Kan gebruikt worden als permanente audiovisuele mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	/ Kan niet gebruikt worden als permanente audiovisuele mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod preselects map [comment]"
	/ Mod selecteert kaart [comment] vooraf

	Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive.

	Example: "Mod preselects map [comment]"

	This unique does not support conditionals.

	Applicable to: ModOptions

## Event uniques（Evenement）
??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

## EventChoice uniques（EvenementKeuze）
??? example  "Only available"
	/ Alleen beschikbaar

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Onbeschikbaar

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

## Conditional uniques（Conditioneel）
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;every [positiveAmount] turns&gt;"
	/ elke [positiveAmount] beurten

	Example: "&lt;every [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;before turn number [nonNegativeAmount]&gt;"
	/ voor beurt nummer [nonNegativeAmount]

	Example: "&lt;before turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;after turn number [nonNegativeAmount]&gt;"
	/ na beurt nummer [nonNegativeAmount]

	Example: "&lt;after turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;on [speed] game speed&gt;"
	/ op [speed] spelsnelheid

	Example: "&lt;on [Quick] game speed&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty&gt;"
	/ op [difficulty] moeilijkheidsgraad

	Example: "&lt;on [Prince] difficulty&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or higher&gt;"
	/ op [difficulty] moeilijkheidsgraad of hoger

	Example: "&lt;on [Prince] difficulty or higher&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or lower&gt;"
	/ op [difficulty] moeilijkheidsgraad of lager

	Example: "&lt;on [Prince] difficulty or lower&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is enabled&gt;"
	/ wanneer [victoryType] Overwinning is ingeschakeld

	Example: "&lt;when [Domination] Victory is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is disabled&gt;"
	/ wanneer [victoryType] Overwinning is uitgeschakeld

	Example: "&lt;when [Domination] Victory is disabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when religion is enabled&gt;"
	/ wanneer religie is ingeschakeld

	Applicable to: Conditional

??? example  "&lt;when religion is disabled&gt;"
	/ wanneer religie is uitgeschakeld

	Applicable to: Conditional

??? example  "&lt;when espionage is enabled&gt;"
	/ wanneer spionage is ingeschakeld

	Applicable to: Conditional

??? example  "&lt;when espionage is disabled&gt;"
	/ wanneer spionage is uitgeschakeld

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are enabled&gt;"
	/ wanneer nucleaire wapens zijn ingeschakeld

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are disabled&gt;"
	/  wanneer nucleaire wapens zijn uitgeschakeld

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount]% chance&gt;"
	/ met [nonNegativeAmount]% kans

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
	/ voor [civFilter] Beschavingen

	Example: "&lt;for [City-States] Civilizations&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	/ tijdens oorlog

	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	/ tijdens vrede

	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	/ tijdens een Gouden Tijdperk

	Applicable to: Conditional

??? example  "&lt;when not in a Golden Age&gt;"
	/ wanneer niet in een Gouden Tijdperk

	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	/ tijdens Wij Houden Van De Koning Dag

	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	/ terwijl het rijk gelukkig is

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	/ tijdens het [era]

	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	/ voor het [era]

	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	/ beginnend in het [era]

	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	/ indien gestart in het [era]

	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	/ als geen andere beschaving dit heeft ontdekt

	Applicable to: Conditional

??? example  "&lt;after discovering [techFilter]&gt;"
	/ na het ontdekken van [techFilter]

	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [techFilter]&gt;"
	/ voor het ontdekken van [techFilter]

	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;while researching [techFilter]&gt;"
	/ tijdens het onderzoeken van [techFilter]

	This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)

	Example: "&lt;while researching [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	/ als geen andere beschaving dit heeft aangenomen

	Applicable to: Conditional

??? example  "&lt;if no Civilization has adopted [policy/belief]&gt;"
	/ als geen enkele beschaving [policy/belief] heeft aangenomen

	Example: "&lt;if no Civilization has adopted [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	/ na het aannemen van [policy/belief]

	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
	/ voor het aannemen van [policy/belief]

	Example: "&lt;before adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before founding a Pantheon&gt;"
	/ voor het stichten van een Pantheon

	Applicable to: Conditional

??? example  "&lt;after founding a Pantheon&gt;"
	/ na het stichten van een Pantheon

	Applicable to: Conditional

??? example  "&lt;before founding a religion&gt;"
	/ voor het stichten van een religie

	Applicable to: Conditional

??? example  "&lt;after founding a religion&gt;"
	/ na het stichten van een religie

	Applicable to: Conditional

??? example  "&lt;before enhancing a religion&gt;"
	/ voor het verbeteren van een religie

	Applicable to: Conditional

??? example  "&lt;after enhancing a religion&gt;"
	/ na het verbeteren van een religie

	Applicable to: Conditional

??? example  "&lt;after generating a Great Prophet&gt;"
	/ na het genereren van een Grote Profeet

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	/ als [buildingFilter] is gebouwd

	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed&gt;"
	/ als [buildingFilter] niet is gebouwd

	Example: "&lt;if [Culture] is not constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in all [cityFilter] cities&gt;"
	/ als [buildingFilter] in alle [cityFilter] steden is gebouwd

	Example: "&lt;if [Culture] is constructed in all [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities&gt;"
	/ als [buildingFilter] in ten minste [positiveAmount] van de [cityFilter] steden is gebouwd

	Example: "&lt;if [Culture] is constructed in at least [3] of [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed by anybody&gt;"
	/ als [buildingFilter] door iemand is gebouwd

	Example: "&lt;if [Culture] is constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed by anybody&gt;"
	/ als [buildingFilter] door niemand is gebouwd

	Example: "&lt;if [Culture] is not constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	/ met [resource]

	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	/ zonder [resource]

	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	/ met meer dan [amount] [stat/resource]

	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'

	Example: "&lt;when above [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
	/ met minder dan [amount] [stat/resource]

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
	/ in deze stad

	Applicable to: Conditional

??? example  "&lt;in [cityFilter] cities&gt;"
	/ in [cityFilter] steden

	Example: "&lt;in [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities connected to the capital&gt;"
	/ in steden verbonden met de hoofdstad

	Applicable to: Conditional

??? example  "&lt;in cities with a [religionFilter] religion&gt;"
	/ in steden met een [religionFilter] religie

	Example: "&lt;in cities with a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities not following a [religionFilter] religion&gt;"
	/ in steden die geen [religionFilter] religie volgen

	Example: "&lt;in cities not following a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with a major religion&gt;"
	/ in steden met een belangrijke religie

	Applicable to: Conditional

??? example  "&lt;in cities with an enhanced religion&gt;"
	/ in steden met een verbeterde religie

	Applicable to: Conditional

??? example  "&lt;in cities following our religion&gt;"
	/ in steden die onze religie volgen

	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	/ in steden met een [buildingFilter]

	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	/ in steden zonder een [buildingFilter]

	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [positiveAmount] [populationFilter]&gt;"
	/ in steden met minstens [positiveAmount] [populationFilter]

	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with [positiveAmount] [populationFilter]&gt;"
	/ in steden met [positiveAmount] [populationFilter]

	Example: "&lt;in cities with [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with between [amount] and [amount] [populationFilter]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;in cities with between [3] and [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with less than [amount] [populationFilter]&gt;"
	/ in steden met minder dan [amount] [populationFilter]

	Example: "&lt;in cities with less than [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	/ met een garnizoen

	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	/ voor [mapUnitFilter] eenheden

	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	/ als [mapUnitFilter]

	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	/ voor eenheden met [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	/ voor eenheden zonder [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	/ tegen steden

	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	/ tegen [mapUnitFilter] eenheden

	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;vs [combatantFilter]&gt;"
	Example: "&lt;vs [City]&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	/ tijdens gevechten met eenheden van een Beschaving met meer Steden dan jij

	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	/ wanneer je aanvalt

	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	/ tijdens verdediging

	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	/ bij gevechten in [tileFilter] tegels

	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	/ op buitenlandse continenten

	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	/ wanneer aangrenzend aan een [mapUnitFilter] eenheid

	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] HP&gt;"
	/ wanneer boven [positiveAmount] HP

	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] HP&gt;"
	/ wanneer onder [positiveAmount] HP

	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] movement&gt;"
	Example: "&lt;when below [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] movement&gt;"
	Example: "&lt;when above [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	/ als het nog geen andere acties heeft gebruikt

	Applicable to: Conditional

??? example  "&lt;when stacked with a [mapUnitFilter] unit&gt;"
	/ wanneer gestapeld met een [mapUnitFilter] eenheid

	Example: "&lt;when stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when not stacked with a [mapUnitFilter] unit&gt;"
	/ Wanneer niet gestapeld met een [mapUnitFilter] eenheid

	Example: "&lt;when not stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	/ in [tileFilter] tegels

	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	/ in tegels zonder [tileFilter]

	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [positiveAmount] tiles of a [tileFilter]&gt;"
	/ binnen [positiveAmount] tegels van een [tileFilter]

	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles adjacent to [tileFilter] tiles&gt;"
	/ in tegels aangrenzend aan [tileFilter] tegels

	Example: "&lt;in tiles adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles not adjacent to [tileFilter] tiles&gt;"
	/ in tegels niet aangrenzend aan [tileFilter] tegels

	Example: "&lt;in tiles not adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	/ op waterkaarten

	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	/ in [regionType] Regionen

	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	/ in alle Regionen behalve [regionType]

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
	/ wanneer gedragen door [mapUnitFilter] eenheid

	Example: "&lt;when carried by [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is enabled&gt;"
	/ als [modFilter] is ingeschakeld

	Example: "&lt;if [DeCiv Redux] is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is not enabled&gt;"
	/ als [modFilter] niet is ingeschakeld

	Example: "&lt;if [DeCiv Redux] is not enabled&gt;"

	Applicable to: Conditional

## TriggerCondition uniques（ActiveerConditie）
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [techFilter] technology&gt;"
	/ bij het ontdekken van [techFilter] technologie

	Example: "&lt;upon discovering [Agriculture] technology&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	/ bij het betreden van het [era]

	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a new era&gt;"
	/ bij het betreden van een nieuw tijdperk

	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	/ bij het aannemen van [policy/belief]

	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war on [civFilter] Civilizations&gt;"
	/ bij het verklaren van oorlog aan [civFilter] beschavingen

	Example: "&lt;upon declaring war on [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon being declared war on by [civFilter] Civilizations&gt;"
	/ bij het worden verklaard van oorlog door [civFilter] beschavingen

	Example: "&lt;upon being declared war on by [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a war with [civFilter] Civilizations&gt;"
	/ bij het ingaan van een oorlog met [civFilter] beschavingen

	Example: "&lt;upon entering a war with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon signing a peace treaty with [civFilter] Civilizations&gt;"
	Example: "&lt;upon signing a peace treaty with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	/ bij het sluiten van een vriendschapspact

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	/ na het sluiten van een defensief verbond

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	/ na het betreden van een Gouden Tijdperk

	Applicable to: TriggerCondition

??? example  "&lt;upon ending a Golden Age&gt;"
	/ bij het beëindigen van een Gouden Tijdperk

	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	/ na het innemen van een stad

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon losing a city&gt;"
	/ bij het verliezen van een stad

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a city&gt;"
	/ na het stichten van een stad

	Applicable to: TriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ na het bouwen van een [improvementFilter] verbetering

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon discovering a Natural Wonder&gt;"
	/ bij het ontdekken van een Natuurlijk Wonder

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter]&gt;"
	/ na het bouwen van [buildingFilter]

	Example: "&lt;upon constructing [Culture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter] [cityFilter]&gt;"
	/ na het bouwen van [buildingFilter] [cityFilter]

	Example: "&lt;upon constructing [Culture] [in all cities]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon gaining a [baseUnitFilter] unit&gt;"
	/ na het verkrijgen van een [baseUnitFilter] eenheid

	Example: "&lt;upon gaining a [Melee] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon losing a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon losing a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ na het beëindigen van de beurt

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ bij het begin van de beurt

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a Pantheon&gt;"
	/ na het stichten van een Pantheon

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	/ na het stichten van een Religie

	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	/ na het uitbreiden van een Religie

	Applicable to: TriggerCondition

??? example  "&lt;upon expending a [mapUnitFilter] unit&gt;"
	/ na het inzetten/opbruiken van een [mapUnitFilter] eenheid

	Example: "&lt;upon expending a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

## UnitTriggerCondition uniques（EenheidActiveerConditie）
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon conquering a city&gt;"
	/ na het innemen van een stad

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ na het bouwen van een [improvementFilter] verbetering

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ na het beëindigen van de beurt

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ bij het begin van de beurt

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon entering combat&gt;"
	/ bij het ingaan van een gevecht

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon damaging a [mapUnitFilter] unit&gt;"
	/ na het beschadigen van een [mapUnitFilter] eenheid

	Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'

	Example: "&lt;upon damaging a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	/ na het verslaan van een [mapUnitFilter] eenheid

	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	/ bij het verslagen worden

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	/ bij promotie

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] promotion&gt;"
	/ bij het verkrijgen van de [promotion] promotie

	Example: "&lt;upon gaining the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] promotion&gt;"
	/ bij het verliezen van de [promotion] promotie

	Example: "&lt;upon losing the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] status&gt;"
	/ bij het verkrijgen van de [promotion] status

	Example: "&lt;upon gaining the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] status&gt;"
	/ bij het verliezen van de [promotion] status

	Example: "&lt;upon losing the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [positiveAmount] HP in a single attack&gt;"
	/ na het verliezen van minstens [positiveAmount] HP in één enkele aanval

	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	/ na het beëindigen van een beurt in een [tileFilter] tegel

	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	/ na het ontdekken van een [tileFilter] tegel

	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon entering a [tileFilter] tile&gt;"
	/ bij het betreden van een [tileFilter] tegel

	Example: "&lt;upon entering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques（EenheidActieModifier）
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	/ door deze eenheid op te gebruiken

	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	/ voor [amount] beweging

	Will consume up to [amount] of Movement to execute

	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;for all movement&gt;"
	/ voor alle beweging

	Will consume all Movement to execute

	Applicable to: UnitActionModifier

??? example  "&lt;requires [nonNegativeAmount] movement&gt;"
	/ vereist [nonNegativeAmount] beweging

	Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up

	Example: "&lt;requires [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [stats] stats&gt;"
	/ kost [stats] stats

	A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock

	Example: "&lt;costs [+1 Gold, +2 Production] stats&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [amount] [stockpiledResource]&gt;"
	/ kost [amount] [stockpiledResource]

	A positive Integer value will be subtracted from your stock. Do not confuse with "Costs [amount] [stockpiledResource]" (uppercase 'C') for Improvements, Buildings, and Units.

	Example: "&lt;costs [3] [Mana]&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;removing the [promotion] promotion/status&gt;"
	/ het verwijderen van de [promotion] promotie/status

	Removes the promotion/status from the unit - this is not a cost, units will be able to activate the action even without the promotion/status. To limit, use &lt;with the [promotion] promotion&gt; conditional

	Example: "&lt;removing the [Shock I] promotion/status&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	/ eenmaal

	Applicable to: UnitActionModifier

??? example  "&lt;[positiveAmount] times&gt;"
	/ [positiveAmount] keer

	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[nonNegativeAmount] additional time(s)&gt;"
	/ [nonNegativeAmount] extra ke(e)r(en)

	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
	/ waarna deze eenheid wordt opgebruikt

	Applicable to: UnitActionModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

## MetaModifier uniques
!!! note ""

    Modifiers that can be added to other uniques changing user experience, not their behavior

??? example  "&lt;for [nonNegativeAmount] turns&gt;"
	/ voor [nonNegativeAmount] beurten

	Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns

	Example: "&lt;for [3] turns&gt;"

	Applicable to: MetaModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

??? example  "&lt;hidden from users&gt;"
	/ Verborgen voor gebruikers

	Applicable to: MetaModifier

??? example  "&lt;for every [countable]&gt;"
	/ voor elke [countable]

	Works for positive numbers only

	Example: "&lt;for every [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every adjacent [tileFilter]&gt;"
	/ voor elke aangrenzende [tileFilter]

	Works for positive numbers only

	Example: "&lt;for every adjacent [Farm]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every [positiveAmount] [countable]&gt;"
	/ voor elke [positiveAmount] [countable]

	Works for positive numbers only

	Example: "&lt;for every [3] [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;(modified by game speed)&gt;"
	/ (aangepast door spelsnelheid)

	Can only be applied to certain uniques, see details of each unique for specifics

	Applicable to: MetaModifier

??? example  "&lt;(modified by game progress up to [relativeAmount]%)&gt;"
	/ (aangepast door spelvoortgang tot [relativeAmount]%)

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
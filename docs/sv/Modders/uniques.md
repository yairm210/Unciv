# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)

## Triggerable uniques（Aktiverbar）
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	/ Gratis [unit] dyker upp

	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[positiveAmount] free [unit] units appear"
	/ [positiveAmount] gratis [unit]-enheter dyker upp

	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "A [unit] rebels"
	Example: "A [Musketman] rebels"

	Applicable to: Triggerable

??? example  "[positiveAmount] [unit]s rebel"
	Example: "[3] [Musketman]s rebel"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	/ Gratis socialpolicy

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Social Policies"
	/ [positiveAmount] Gratis Socialpolicys

	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	/ Riket går in i en guldålder

	Applicable to: Triggerable

??? example  "Empire enters a [positiveAmount]-turn Golden Age"
	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	/ Gratis Stor Person

	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	/ [amount] befolkning [cityFilter]

	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	/ [amount] befolkning i en slumpmässig stad

	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy/belief]"
	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter]"
	Example: "Remove [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter] and refund [amount]% of its cost"
	Example: "Remove [Oligarchy] and refund [3]% of its cost"

	Applicable to: Triggerable

??? example  "Free Technology"
	/ Gratis Teknologi

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Technologies"
	/ [positiveAmount] Gratisteknologier

	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[positiveAmount] free random researchable Tech(s) from the [eraFilter]"
	/ [positiveAmount] gratis slumpmässiga forskningsbara Teknologi(er) från [eraFilter]

	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	/ Visar hela kartan

	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	/ Utlöser röstning för Diplomatisegern

	Applicable to: Triggerable

??? example  "Instantly consumes [positiveAmount] [stockpiledResource]"
	Example: "Instantly consumes [3] [Mana]"

	Applicable to: Triggerable

??? example  "Instantly provides [positiveAmount] [stockpiledResource]"
	Example: "Instantly provides [3] [Mana]"

	Applicable to: Triggerable

??? example  "Set [stockpile] to [countable]"
	Example: "Set [Mana] to [1000]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Instantly gain [amount] [stockpile]"
	Example: "Instantly gain [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	/ Få [amount] [stat]

	Example: "Gain [3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	/ Få tillräckligt med Tro för ett Panteon

	Applicable to: Triggerable

??? example  "Gain enough Faith for [positiveAmount]% of a Great Prophet"
	/ Få tillräckligt med Tro för [positiveAmount]% av en Stor Profet

	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Research [relativeAmount]% of [tech]"
	Example: "Research [+20]% of [Agriculture]"

	Applicable to: Triggerable

??? example  "Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius"
	Example: "Gain control over [Farm] tiles in a [3]-tile radius"

	Applicable to: Triggerable

??? example  "Gain control over [positiveAmount] tiles [cityFilter]"
	Example: "Gain control over [3] tiles [in all cities]"

	Applicable to: Triggerable

??? example  "Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius"
	/ Visa upp till [positiveAmount/'all'] [tileFilter] inom [positiveAmount] rutors radie

	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	/ Orsakar följande globala varning: [comment]

	Supported on Policies and Technologies.

	For other targets, the generated Notification may not read nicely, and will likely not support translation. Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_, and that difference is currently handled by mapping text.

	Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts.

	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "Promotes all spies [positiveAmount] time(s)"
	Example: "Promotes all spies [3] time(s)"

	Applicable to: Triggerable

??? example  "Gain an extra spy"
	Applicable to: Triggerable

??? example  "Turn this tile into a [terrainName] tile"
	Example: "Turn this tile into a [Forest] tile"

	Applicable to: Triggerable

??? example  "Remove [resourceFilter] resources from this tile"
	Example: "Remove [Strategic] resources from this tile"

	Applicable to: Triggerable

??? example  "Remove [improvementFilter] improvements from this tile"
	Example: "Remove [All Road] improvements from this tile"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Works only with promotions that are valid for the unit's type - or for promotions that do not specify any.

	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [positiveAmount] cities for free"
	/ Ger den billigaste [stat]-byggnaden gratis i dina första [positiveAmount] städer

	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [positiveAmount] cities for free"
	/ Ger en [buildingName] gratis i dina första [positiveAmount] städer

	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Triggers a [event] event"
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

## UnitTriggerable uniques
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "[unitTriggerTarget] heals [positiveAmount] HP"
	Example: "[This Unit] heals [3] HP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] takes [positiveAmount] damage"
	Example: "[This Unit] takes [3] damage"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [amount] XP"
	Example: "[This Unit] gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free"
	Example: "[This Unit] upgrades for free"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free including special upgrades"
	Example: "[This Unit] upgrades for free including special upgrades"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] promotion"
	Example: "[This Unit] gains the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] promotion"
	Example: "[This Unit] loses the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [positiveAmount] movement"
	Example: "[This Unit] gains [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses [positiveAmount] movement"
	Example: "[This Unit] loses [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)"
	Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns.

	Example: "[This Unit] gains the [Shock I] status for [3] turn(s)"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] status"
	Example: "[This Unit] loses the [Shock I] status"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] is destroyed"
	Example: "[This Unit] is destroyed"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gets a name from the [unitNameGroup] group"
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
	/ [stats] från varje specialist [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] befolkning [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] social policies adopted"
	Only works for civ-wide stats

	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [positiveAmount] [civWideStat]"
	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] i städer på [terrainFilter]-rutor

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] från alla [buildingFilter]-byggnader

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] från rutor med [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] från varje Handelsrutt

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
	/ [relativeAmount]% [stat] från varje [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% Avkastning från varje [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	/ [relativeAmount]% [stat] från Stadsstater

	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	/ [relativeAmount]% [stat] från Handelsrutter

	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	/ Omintetgör [stat] [cityFilter]

	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	/ Omintetgör Tillväxt [cityFilter]

	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] Produktion vid bygge av [buildingFilter]-byggnader [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% Produktion vid bygge av [baseUnitFilter]-enheter [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Produktion vid bygge av [buildingFilter] underverk [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Produktion mot byggnader som redan finns i Huvudstaden

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from pillaging tiles"
	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "Military Units gifted from City-States start with [positiveAmount] XP"
	/ Militära Enheter som ges av Stadsstater börjar med [positiveAmount] EP

	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation"
	/ Militaristiska Stadsstater ger enheter [positiveAmount] gånger snabbare då du befinner dig i krig med en gemensam fiende

	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	/ Guldgåvor till Stadsstater genererar [relativeAmount]% mer Inflytande

	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns"
	Example: "Can spend Gold to annex or puppet a City-State that has been your Ally for [3] turns"

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	/ Stadsstats territorium räknas alltid som vänligt territorium

	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	/ Allierade Stadsstater ger då och då Stora Personer i gåva

	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	/ [relativeAmount]% försämring av Stadsstatsinflytande

	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	/ Viloläget för Inflytande med Stadsstater ökas med [amount]

	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	/ Allierade Stadsstater bidrar med [stat] motsvarande [relativeAmount]% av vad de producerar för sig själva

	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	/ [relativeAmount]% resurser som ges av Stadsstater

	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	/ [relativeAmount]% Lycka från lyxresurser som ges av Stadsstater

	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	/ Stadsstadsinflytande återhämtas dubbelt så fort som vanligt

	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% tillväxt [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% Mat behålls efter att befolkningen ökar [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Olycklighet från [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	/ [amount] Lycka från varje slags lyxresurs

	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	/ Behåll [relativeAmount]% av lyckan från en lyxresurs efter att den sista kopian handlats bort

	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	/ [relativeAmount]% av extra lycka omvandlas till [stat]

	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	/ Kan inte bygga [baseUnitFilter]-enheter

	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	/ Möjliggör konstruktion av Rymdskeppsdelar

	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan köpa [baseUnitFilter] enheter för [amount] [stat] [cityFilter] till ett stigande pris ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Får köpa [buildingFilter]-byggnader för [amount] [stat] [cityFilter] till en ökande kostnad ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Får köpa [baseUnitFilter]-enheter för [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Får köpa [buildingFilter]-byggnader för [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Får köpa [baseUnitFilter]-enheter med [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Kan köpa [buildingFilter]-byggnader med [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan köpa [baseUnitFilter]-enheter med [stat] för [nonNegativeAmount] gånger deras normala Produktionskostnad

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Får köpa [buildingFilter]-byggnader med [stat] för [nonNegativeAmount] gånger deras vanliga Produktionskostnad

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ [stat]-kostnad för att köpa saker i städer [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat]-kostnad för att köpa [buildingFilter]-byggnader [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat]-kostnad för att köpa [baseUnitFilter]-enheter [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	/ Möjliggör omvandling av stadens produktion till [civWideStat]

	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	/ Ökar förflyttningshastighet på vägar

	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	/ Vägar sammanlänkar rutor tvärs över floder

	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	/ [relativeAmount]% driftkostnad för vägar och järnvägar

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	/ Ingen Driftkostnad för förbättringar i [tileFilter]-rutor.

	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Remove [buildingFilter] [cityFilter]"
	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% Kulturkostnad för vanlig gränstillväxt [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% Guldkostnad för att köpa rutor [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	/ Varje stad som grundas ökar kulturkostnaden för policys [relativeAmount]% mindre än vanligt

	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	/ [relativeAmount]% Kulturkostnad för att anamma nya Policyer

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "Each city founded increases Science cost of Technologies [relativeAmount]% less than normal"
	Example: "Each city founded increases Science cost of Technologies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Science cost of researching new Technologies"
	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Science cost of researching new Technologies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)"
	Example: "[+1 Gold, +2 Production] for discovering a Natural Wonder (bonus enhanced to [+1 Gold, +2 Production] if first to discover it)"

	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% generation av Stora Personer [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Guld från Stora Köpmäns handelsuppdrag

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Stora Generaler ger dubbel stridsbonus

	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	/ Få en gratis Stor Person vid slutet av varje [comment] (vart 394:e år), efter att ha upptäckt [tech].

	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	/ När den Långa Räkningen aktiveras visas årtalet på världsskärmen som i den traditionella Mayakalenderns Långa Räkning.

	Applicable to: Global

??? example  "[amount] Unit Supply"
	/ [amount] Enhetsförnödenheter

	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [positiveAmount] population [cityFilter]"
	/ [amount] Enhetsförnödenheter per [positiveAmount] befolkning [cityFilter]

	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	/ [amount] Enhetsförnödenheter per stad

	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	/ [amount] enheter kostar ingen driftkostnad

	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	/ Enheter i städer har ingen Driftkostnad

	Applicable to: Global

??? example  "Enables embarkation for land units"
	/ Möjliggör ombordstigning för landenheter

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	/ Möjliggör [mapUnitFilter]-enheter att gå in i havsrutor

	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	/ Landenheter kan gå igenom [terrainName]-rutor efter att den första [baseUnitFilter] tjänats in

	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory"
	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Alla nytränade [baseUnitFilter]-enheter [cityFilter] får [promotion]-befordringen

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ [mapUnitFilter]-enheter bredvid denna stad läker [amount] KP per drag då de läker

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% XP required for promotions"
	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP required for promotions"

	Applicable to: Global

??? example  "[relativeAmount]% City Strength from defensive buildings"
	/ [relativeAmount]% Stadsstyrka från försvarsbyggnader

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Ger [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% [resourceFilter] resource production"
	Example: "[+20]% [Strategic] resource production"

	Applicable to: Global

??? example  "Enables establishment of embassies"
	Applicable to: Global

??? example  "Requires establishing embassies to conduct advanced diplomacy"
	Applicable to: Global

??? example  "Enables Open Borders agreements"
	/ Möjliggör avtal om öppna gränser

	Applicable to: Global

??? example  "Enables Research agreements"
	/ Möjliggör forskningsavtal

	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	/ Vetenskap som fås från forskningsavtal [relativeAmount]%

	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	/ Vid en vänskapsförklaring får båda parter en [relativeAmount]% bonus för generation av stora personer

	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	/ Alla andra civilisationers inflytande med stadsstater minskar [relativeAmount]% snabbare

	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	/ Få [amount] Inflytande då du ger en [baseUnitFilter] till en Stadsstat

	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	/ Viloläget för Inflytande med Stadsstater som följer denna religion [amount]

	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	/ Signaleras om nya Barbarläger

	Applicable to: Global

??? example  "Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities"
	Example: "Receive [+20]% Gold from Barbarian encampments and pillaging Cities"

	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	/ Då ett läger erövras, få [amount] Guld och rekrytera en Barbarenhet

	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	/ Då en [mapUnitFilter]-enhet besegras, få [amount] Guld och rekrytera den

	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	/ Får välja [amount] extra [beliefType]-trossatser vid [foundingOrEnhancing] av en religion

	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	/ Får välja ytterligare [amount] trossats(er) av valfritt slag vid [foundingOrEnhancing] av en religion

	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	/ [stats] när en stad adopterar denna religionen för första gången

	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% Naturlig religion spridning [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ Religion sprids naturligt till städer [amount] rutor bort

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	/ Kanske inte genererar stora profet motsvarigheter naturligt

	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	/ [relativeAmount]% Trokostnad för att generera Stor Profet motsvarigheter

	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "[relativeAmount]% spy effectiveness [cityFilter]"
	Example: "[+20]% spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% enemy spy effectiveness [cityFilter]"
	Example: "[+20]% enemy spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "New spies start with [amount] level(s)"
	Example: "New spies start with [3] level(s)"

	Applicable to: Global

??? example  "Triggers victory"
	/ Leder till seger

	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	/ Utlöser en kulturell vinst när den färdigställs

	Applicable to: Global

??? example  "May buy items in puppet cities"
	Applicable to: Global

??? example  "May not annex cities"
	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	/ "Lånar" stadsnamn från andra civilisationer i spelet

	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	/ Städer jämnas med marken [amount] gånger snabbare

	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	/ Få en teknologiboost när vetenskapsbyggnader/-underverk byggs i huvudstaden

	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	/ [relativeAmount]% Guldålderslängd

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	/ Befolkningsförlust från kärnvapenattacker [relativeAmount]% [cityFilter]

	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	/ Rebellenheter kan dyka upp

	Applicable to: Global

??? example  "Cannot build [buildingFilter] buildings"
	Example: "Cannot build [Culture] buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Styrka

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Styrka vilket avtar med avståndet från huvudstaden

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% till Flankeringsbonusar

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] extra anfall per drag

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Förflyttning

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Sikt

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Räckvidd

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] KP vid läkning

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Styrka för Religionsspridning

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Vid spridning av religion till en stad, få [amount] gånger antalet anhängare av de andra religionerna som [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Räckviddsattacker kan utföras över hinder

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	/ Ingen försvarsbonus från terräng

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Inget försvarsavdrag för terräng

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	Applicable to: Global, Unit

??? example  "Unable to capture cities"
	/ Kan inte inta städer

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	/ Ingen förflyttningskostnad för att plundra

	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	/ Kan läka utanför vänligt territorium

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Alla läkeeffekter dubbleras

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Läker [amount] skada om den dödar en enhet

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Kan bara läkas genom plundring

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% driftkostnad

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% Guldkostnad för att uppgradera

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Få [amount]% av skadan som åsamkas [combatantFilter]-enheter som [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Då en stad erövras, få [amount] gånger dess [stat]produktion som [stockpile] omedelbart

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Tjäna [amount]% av dödade [mapUnitFilter]-enheters [costOrStrength] som [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	/ [amount] EP från strid

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% EP från strider

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] tjänas in [relativeAmount]% snabbare

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ Kostar [nonNegativeAmount] Förflyttningspoäng för att landstiga

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ Kostar [nonNegativeAmount] Förflyttningspoäng för att ombordstiga

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

## Nation uniques
??? example  "Starts with [tech]"
	/ Börjar med [tech]

	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	/ Startar med [policy] anammad

	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	/ Alla enheter går genom Skogs- och Jungelrutor i vänligt territorium som om de hade vägar. Dessa rutor kan användas för att koppla samman Städer vid upptäckt av Hjulet.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	/ Enheter ignorerar terrängkostnader då de flyttar till en ruta med Kullar.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Will not be chosen for new games"
	/ Kommer inte väljas för nya spel

	Applicable to: Nation

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Personality uniques（Personlighet）
??? example  "Will not build [baseUnitFilter/buildingFilter]"
	Example: "Will not build [Melee]"

	Applicable to: Personality

## Era uniques
??? example  "Starting in this era disables religion"
	/ Att starta i denna era kommer att stänga av religion

	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	Applicable to: Era

## Tech uniques（Teknologi）
??? example  "Starting tech"
	/ Startteknologi

	Applicable to: Tech

??? example  "Can be continually researched"
	/ Kan forskas fram kontinuerligt

	Applicable to: Tech

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Cannot be hurried"
	/ Kan inte snabbas på

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

## Policy uniques
??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
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

## FounderBelief uniques
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	/ [stats] för varje global stad som följer denna religion

	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [positiveAmount] global followers [cityFilter]"
	/ [stats] från vart [positiveAmount]:e global följeslagare [cityFilter]

	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
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

## FollowerBelief uniques
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] från varje specialist [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] per [positiveAmount] befolkning [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] i städer på [terrainFilter]-rutor

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] från alla [buildingFilter]-byggnader

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] från rutor med [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] från varje Handelsrutt

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
	/ [relativeAmount]% [stat] från varje [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% Avkastning från varje [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount] Produktion vid bygge av [buildingFilter]-byggnader [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% Produktion vid bygge av [baseUnitFilter]-enheter [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Produktion vid bygge av [buildingFilter] underverk [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Produktion mot byggnader som redan finns i Huvudstaden

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% tillväxt [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% Mat behålls efter att befolkningen ökar [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Olycklighet från [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Kan köpa [baseUnitFilter] enheter för [amount] [stat] [cityFilter] till ett stigande pris ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Får köpa [buildingFilter]-byggnader för [amount] [stat] [cityFilter] till en ökande kostnad ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Får köpa [baseUnitFilter]-enheter för [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Får köpa [buildingFilter]-byggnader för [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Får köpa [baseUnitFilter]-enheter med [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Kan köpa [buildingFilter]-byggnader med [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Kan köpa [baseUnitFilter]-enheter med [stat] för [nonNegativeAmount] gånger deras normala Produktionskostnad

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Får köpa [buildingFilter]-byggnader med [stat] för [nonNegativeAmount] gånger deras vanliga Produktionskostnad

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ [stat]-kostnad för att köpa saker i städer [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ [stat]-kostnad för att köpa [buildingFilter]-byggnader [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [stat]-kostnad för att köpa [baseUnitFilter]-enheter [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% Kulturkostnad för vanlig gränstillväxt [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% Guldkostnad för att köpa rutor [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% generation av Stora Personer [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Alla nytränade [baseUnitFilter]-enheter [cityFilter] får [promotion]-befordringen

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ [mapUnitFilter]-enheter bredvid denna stad läker [amount] KP per drag då de läker

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Strength for cities"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Ger [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% Naturlig religion spridning [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ Religion sprids naturligt till städer [amount] rutor bort

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion"
	/ Få [amount]% av [mapUnitFilter]-enheters [costOrStrength] som [stockpile] när de utplånas inom 4 rutor från en stad som följer denna religion

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

## Building uniques（Byggnad）
??? example  "[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]"
	Example: "[3]% of [Culture] from every [All Road] in the city added to [Iron]"

	Applicable to: Building

??? example  "Consumes [amount] [resource]"
	/ Konsumerar [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Obyggbar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Kan inte köpas

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Kan köpas med [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Kan köpas för [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Begränsas till [amount] per Civilisation

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ Extra mat omvandlas till Produktion under bygget

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Kräver minst [amount] befolkning

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Utlöser en global varning när byggnation börjar

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Orsakar en global varning vid fullbordning

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Kostnad ökar med [amount] per ägd stad

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	/ Måste ha en ägd [tileFilter] inom [amount] rutor

	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	/ Möjliggör kärnvapen

	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	/ Får inte vara på [tileFilter]

	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	/ Får inte vara bredvid [tileFilter]

	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	/ Osäljbar

	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	/ Indikerar huvudstaden

	Applicable to: Building

??? example  "Moves to new capital when capital changes"
	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	/ Ger 1 extra kopia av varje förbättrad lyxresurs nära denna Stad

	Applicable to: Building

??? example  "Destroyed when the city is captured"
	/ Förstörs när staden blir erövrad

	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	/ Förstörs aldrig när staden blir erövrad

	Applicable to: Building

??? example  "[relativeAmount]% Gold given to enemy if city is captured"
	Example: "[+20]% Gold given to enemy if city is captured"

	Applicable to: Building

??? example  "Removes extra unhappiness from annexed cities"
	Applicable to: Building

??? example  "Connects trade routes over water"
	/ Sammanlänkar handelsrutter över vatten

	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	/ Skapar en [improvementName]-förbättring på en anvisad ruta

	When choosing to construct this building, the player must select a tile where the improvement can be built. Upon building completion, the tile will gain this improvement. Limited to one per building.

	Example: "Creates a [Trading Post] improvement on a specific tile"

	This unique does not support conditionals.

	Applicable to: Building

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Kan bära [amount] extra [mapUnitFilter]-enheter

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Spaceship part"
	/ Rymdskeppsdel

	Applicable to: Building, Unit

??? example  "Cannot be hurried"
	/ Kan inte snabbas på

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

## UnitAction uniques
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	/ Grundar en ny stad

	Applicable to: UnitAction

??? example  "Founds a new puppet city"
	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	Applicable to: UnitAction

??? example  "May found a religion"
	/ Kan stifta en religion

	Applicable to: UnitAction

??? example  "May enhance a religion"
	/ Kan förbättra en religion

	Applicable to: UnitAction

??? example  "Can transform to [unit]"
	By default consumes all movement

	Example: "Can transform to [Musketman]"

	Applicable to: UnitAction

## Unit uniques（Enhet）
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "[relativeAmount]% Yield from pillaging tiles"
	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Guld från Stora Köpmäns handelsuppdrag

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Stora Generaler ger dubbel stridsbonus

	Applicable to: Global, Unit

??? example  "Consumes [amount] [resource]"
	/ Konsumerar [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Obyggbar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Kan inte köpas

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Kan köpas med [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Kan köpas för [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Begränsas till [amount] per Civilisation

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ Extra mat omvandlas till Produktion under bygget

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Kräver minst [amount] befolkning

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Utlöser en global varning när byggnation börjar

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Orsakar en global varning vid fullbordning

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Kostnad ökar med [amount] per ägd stad

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "May create improvements on water resources"
	/ Kan skapa förbättringar på vattenresurser

	Applicable to: Unit

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	/ Kan bygga [improvementFilter/terrainFilter]-förbättringar på rutor

	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	/ Kan läggas till [comment] i Huvudstaden

	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	/ Förhindrar spridandet av religion till angränsande stad

	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	/ Tar bort andra religioner vid spridning av religion

	Applicable to: Unit

??? example  "May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away"
	Example: "May Paradrop to [Farm] tiles up to [3] tiles away"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	Applicable to: Unit

??? example  "Can speed up construction of a building"
	/ Kan skynda på bygget av en byggnad

	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	/ Kan snabba på bygget av ett underverk

	Applicable to: Unit

??? example  "Can hurry technology research"
	/ Kan påskynda forskning

	Applicable to: Unit

??? example  "Can generate a large amount of culture"
	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	/ Kan göra en handelsresa till en Stadsstat, vilket ger en stor summa pengar och [amount] Inflytande

	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Automation is a primary action"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Styrka

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Styrka vilket avtar med avståndet från huvudstaden

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% till Flankeringsbonusar

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles"
	/ [relativeAmount]% Styrka för fientliga [mapUnitFilter]-enheter i angränsande [tileFilter]-rutor

	Example: "[+20]% Strength for enemy [Wounded] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	/ [relativeAmount]% Styrkebonus för [mapUnitFilter]-enheter inom [amount] rutor

	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] extra anfall per drag

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Förflyttning

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Sikt

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Räckvidd

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] KP vid läkning

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Styrka för Religionsspridning

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ Vid spridning av religion till en stad, få [amount] gånger antalet anhängare av de andra religionerna som [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Can only attack [combatantFilter] units"
	/ Kan endast anfalla [combatantFilter]-enheter

	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	/ Kan endast anfalla [tileFilter]-rutor

	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	/ Kan inte anfalla

	Applicable to: Unit

??? example  "Must set up to ranged attack"
	/ Måste ställas upp för att anfalla med räckvidd

	Applicable to: Unit

??? example  "Self-destructs when attacking"
	/ Självdestruerar vid anfall

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	/ Tar bort styrkeavdraget för anfall över en kust

	Applicable to: Unit

??? example  "May attack when embarked"
	/ Kan anfalla medan ombordstigen

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	/ Tar bort styrkeavdraget för anfall över floder

	Applicable to: Unit

??? example  "Blast radius [amount]"
	/ Sprängradie [amount]

	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Räckviddsattacker kan utföras över hinder

	Applicable to: Global, Unit

??? example  "Nuclear weapon of Strength [amount]"
	/ Kärnvapen av Styrka [amount]

	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "No defensive terrain bonus"
	/ Ingen försvarsbonus från terräng

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Inget försvarsavdrag för terräng

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	Applicable to: Global, Unit

??? example  "Uncapturable"
	/ Ofångbar

	Applicable to: Unit

??? example  "Withdraws before melee combat"
	Applicable to: Unit

??? example  "Unable to capture cities"
	/ Kan inte inta städer

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	Applicable to: Global, Unit

??? example  "Destroys [cityFilter] cities instead of capturing"
	The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities.Capital cities (including city states) are immune to this effect.

	Example: "Destroys [in all cities] cities instead of capturing"

	Applicable to: Unit

??? example  "No movement cost to pillage"
	/ Ingen förflyttningskostnad för att plundra

	Applicable to: Global, Unit

??? example  "Can move after attacking"
	/ Kan flytta efter anfall

	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	/ Överför Förflyttning till [mapUnitFilter]

	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	/ Kan flytta omedelbart efter köp

	Applicable to: Unit

??? example  "May heal outside of friendly territory"
	/ Kan läka utanför vänligt territorium

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Alla läkeeffekter dubbleras

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Läker [amount] skada om den dödar en enhet

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Kan bara läkas genom plundring

	Applicable to: Global, Unit

??? example  "Unit will heal every turn, even if it performs an action"
	/ Enheten kommer läka varje drag, oavsett om den utför en handling

	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	/ Alla intilliggande enheter läker [amount] KP då de läker

	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	/ Ingen syn

	Applicable to: Unit

??? example  "Can see over obstacles"
	/ Kan se över hinder

	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	/ Kan bära [amount] [mapUnitFilter]-enheter

	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Kan bära [amount] extra [mapUnitFilter]-enheter

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	/ Kan inte bäras av [mapUnitFilter]-enheter

	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	/ [relativeAmount]% chans för att genskjuta luftanfall

	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	/ Skada från genskjutningar minskas med [relativeAmount]%

	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	/ [relativeAmount]% skada vid genskjutning

	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	/ [amount] extra genskjutningar kan göras per drag

	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	/ Kan inte bli stoppad

	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	/ Kan inte genskjuta [mapUnitFilter]-enheter

	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% driftkostnad

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% Guldkostnad för att uppgradera

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Få [amount]% av skadan som åsamkas [combatantFilter]-enheter som [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ Då en stad erövras, få [amount] gånger dess [stat]produktion som [stockpile] omedelbart

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Tjäna [amount]% av dödade [mapUnitFilter]-enheters [costOrStrength] som [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "May capture killed [mapUnitFilter] units"
	/ Kan tillfångata besegrade [mapUnitFilter]-enheter

	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "[amount] XP gained from combat"
	/ [amount] EP från strid

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% EP från strider

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "Can be earned through combat"
	Applicable to: Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] tjänas in [relativeAmount]% snabbare

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "Invisible to others"
	/ Osynlig för andra

	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	/ Osynlig för icke-angränsande enheter

	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	/ Kan se osynliga [mapUnitFilter] enheter

	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [unit] through ruins-like effects"
	/ Kan uppgraderas till [unit] genom ruin-liknande effekter

	Example: "May upgrade to [Musketman] through ruins-like effects"

	Applicable to: Unit

??? example  "Can upgrade to [unit]"
	Example: "Can upgrade to [Musketman]"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	Applicable to: Unit

??? example  "Cannot move"
	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	/ Dubbel förflyttning i [terrainFilter]

	Example: "Double movement in [Fresh Water]"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	/ Alla rutor kostar 1 förflyttning

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	/ Kan gå igenom opasserbara rutor

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores terrain cost"
	/ Bortser från terrängkostnad

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores Zone of Control"
	/ Bortser från Kontrollzon

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Rough terrain penalty"
	/ Avdrag för kuperad terräng

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can enter ice tiles"
	/ Kan gå in i isrutor

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Cannot embark"
	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	/ Kan inte gå in i havsrutor

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	/ Kan gå in i utländska rutor utan öppna gränser

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	/ Kan gå in i utländska rutor utan öppna gränser, men förlorar [amount] religiös styrka varje drag som den slutför där

	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ Kostar [nonNegativeAmount] Förflyttningspoäng för att landstiga

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ Kostar [nonNegativeAmount] Förflyttningspoäng för att ombordstiga

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "Never appears as a Barbarian unit"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "Religious Unit"
	/ Religiös Enhet

	Applicable to: Unit

??? example  "Spaceship part"
	/ Rymdskeppsdel

	Applicable to: Building, Unit

??? example  "Takes your religion over the one in their birth city"
	/ Tar din religion över den i deras födelsestad

	Applicable to: Unit

??? example  "Great Person - [comment]"
	/ Stor Person - [comment]

	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
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

## UnitType uniques
??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Promotion uniques（Befordran）
??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Doing so will consume this opportunity to choose a Promotion"
	/ Genom att göra detta förbrukas detta tillfälle att välja en Befordring

	Applicable to: Promotion

??? example  "This Promotion is free"
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

## Terrain uniques（Terräng）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[amount] Sight"
	/ [amount] Sikt

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
	Example: "Grants [+1 Gold, +2 Production] to the first civilization to discover it"

	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	/ Enheter som slutar sitt drag på denna terräng tar [amount] skada

	Example: "Units ending their turn on this terrain take [3] damage"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	This unique does not support conditionals.

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	/ Ger [promotion] ([comment]) till angränsande [mapUnitFilter]-enheter för resten av spelet

	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	/ [amount] styrka för städer som byggs på denna terräng

	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time bonus of [stats] to the closest city when cut down"
	Example: "Provides a one-time bonus of [+1 Gold, +2 Production] to the closest city when cut down"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Terrain

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ Denna ruta ger avkastning utan tilldelad befolkning

	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	/ Nollar all andra avkastningar denna ruta ger

	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	/ Endast [improvementFilter]-förbättringar kan byggas på denna ruta

	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	/ Stoppar siktlinjer från rutor i samma höjd

	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	/ Har en höjd på [amount] för synlighetsberäkningar

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
	/ Ovanligt drag

	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	/ [amount]% Sannolikhet att förstöras av atombomber

	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	/ Färskvatten

	Applicable to: Terrain

??? example  "Rough terrain"
	/ Kuperad terräng

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

## Improvement uniques（Förbättring）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "Consumes [amount] [resource]"
	/ Konsumerar [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Provides [amount] [resource]"
	/ Ger [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Obyggbar

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "[amount] Sight"
	/ [amount] Sikt

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ Denna ruta ger avkastning utan tilldelad befolkning

	Applicable to: Terrain, Improvement

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Can also be built on tiles adjacent to fresh water"
	/ Kan även byggas på rutor angränsande färskvatten

	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	/ [stats] från [tileFilter]-rutor

	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	/ Kan byggas utanför dina gränser

	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	/ Kan byggas precis utanför dina gränser

	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	/ Kan bara byggas på [tileFilter]-rutor

	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	/ Kan inte byggas på [tileFilter]-rutor

	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	/ Kan bara byggas för att förbättra en resurs

	Applicable to: Improvement

??? example  "Does not need removal of [terrainFeature]"
	Example: "Does not need removal of [Hill]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	/ Tar bort terrängdrag som kan tas bort vid bygge

	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	/ Ger en försvarsbonus på [relativeAmount]%

	Does not accept unit-based conditionals

	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	/ Angränsande fientliga enheter som avslutar sitt drag tar [amount] skada

	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	/ Stor Förbättring

	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	/ Ger en slumpmässig bonus då man går in i den

	Applicable to: Improvement

??? example  "Unpillagable"
	/ Oplundringsbar

	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	/ Att plundra denna förbättring ger ungefär [stats]

	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	/ Att plundra denna förbättring ger [stats]

	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Destroyed when pillaged"
	Applicable to: Improvement

??? example  "Irremovable"
	/ Oborttaglig

	Applicable to: Improvement

??? example  "Will not be replaced by automated units"
	Applicable to: Improvement

??? example  "Improves [resourceFilter] resource in this tile"
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

## Resource uniques（Resurs）
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
	/ Fyndigheter i [tileFilter]-rutor ger alltid [amount] resurser

	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	/ Kan endast skapas av Merkantil Statsstater

	Applicable to: Resource

??? example  "Stockpiled"
	This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment.The current stockpiled amount can be affected with trigger uniques.

	Applicable to: Resource

??? example  "City-level resource"
	This resource is calculated on a per-city level rather than a per-civ level

	Applicable to: Resource

??? example  "Cannot be traded"
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
	/ Garanteras vid Strategisk Balans-resursalternativet

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

## Ruins uniques（Ruiner）
??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Free [unit] found in the ruins"
	/ Gratis [unit] hittad i ruinerna

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

## Speed uniques（Hastighet）
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

## Difficulty uniques（Svårighetsgrad）
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

## CityState uniques
??? example  "Provides military units every ≈[positiveAmount] turns"
	/ Ger militära enheter vart ≈[positiveAmount] drag

	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	/ Ger en unik lyxresurs

	Applicable to: CityState

## ModOptions uniques
??? example  "Diplomatic relationships cannot change"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can convert gold to science with sliders"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow City States to spawn with additional units"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can trade civilization introductions for [positiveAmount] Gold"
	Example: "Can trade civilization introductions for [3] Gold"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Disable religion"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can only start games from the starting era"
	In this case, 'starting era' means the first defined Era in the entire ruleset.

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze capital"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze holy city"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

??? example  "Mod is incompatible with [modFilter]"
	Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible.

	Example: "Mod is incompatible with [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod requires [modFilter]"
	Specifies that your Extension Mod is only available if any other Mod matching the filter is active.

	Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue.

	Example: "Mod requires [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod preselects map [comment]"
	Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive.

	Example: "Mod preselects map [comment]"

	This unique does not support conditionals.

	Applicable to: ModOptions

## Event uniques
??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

## EventChoice uniques
??? example  "Only available"
	/ Endast tillgänglig

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
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

## Conditional uniques（Villkorlig）
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;every [positiveAmount] turns&gt;"
	Example: "&lt;every [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;before turn number [nonNegativeAmount]&gt;"
	Example: "&lt;before turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;after turn number [nonNegativeAmount]&gt;"
	Example: "&lt;after turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;on [speed] game speed&gt;"
	Example: "&lt;on [Quick] game speed&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty&gt;"
	Example: "&lt;on [Prince] difficulty&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or higher&gt;"
	Example: "&lt;on [Prince] difficulty or higher&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or lower&gt;"
	Example: "&lt;on [Prince] difficulty or lower&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is enabled&gt;"
	Example: "&lt;when [Domination] Victory is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is disabled&gt;"
	Example: "&lt;when [Domination] Victory is disabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when religion is enabled&gt;"
	Applicable to: Conditional

??? example  "&lt;when religion is disabled&gt;"
	Applicable to: Conditional

??? example  "&lt;when espionage is enabled&gt;"
	Applicable to: Conditional

??? example  "&lt;when espionage is disabled&gt;"
	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are enabled&gt;"
	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are disabled&gt;"
	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount]% chance&gt;"
	/ med [nonNegativeAmount]% chans

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
	Example: "&lt;for [City-States] Civilizations&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	/ medan i krig

	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	/ medan ej i krig

	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	/ under en Guldålder

	Applicable to: Conditional

??? example  "&lt;when not in a Golden Age&gt;"
	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	/ medan riket är lyckligt

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	/ under [era]

	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	/ innan [era]

	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	/ med början i [era]

	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	/ om ingen annan Civilisation har upptäckt detta

	Applicable to: Conditional

??? example  "&lt;after discovering [techFilter]&gt;"
	/ efter upptäckten av [techFilter]

	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [techFilter]&gt;"
	/ innan upptäckten av [techFilter]

	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;while researching [techFilter]&gt;"
	This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)

	Example: "&lt;while researching [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	Applicable to: Conditional

??? example  "&lt;if no Civilization has adopted [policy/belief]&gt;"
	Example: "&lt;if no Civilization has adopted [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	/ efter att [policy/belief] anammats

	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
	/ innan [policy/belief] anammats

	Example: "&lt;before adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before founding a Pantheon&gt;"
	Applicable to: Conditional

??? example  "&lt;after founding a Pantheon&gt;"
	Applicable to: Conditional

??? example  "&lt;before founding a religion&gt;"
	Applicable to: Conditional

??? example  "&lt;after founding a religion&gt;"
	Applicable to: Conditional

??? example  "&lt;before enhancing a religion&gt;"
	Applicable to: Conditional

??? example  "&lt;after enhancing a religion&gt;"
	Applicable to: Conditional

??? example  "&lt;after generating a Great Prophet&gt;"
	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	/ om [buildingFilter] är byggt

	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed&gt;"
	Example: "&lt;if [Culture] is not constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in all [cityFilter] cities&gt;"
	Example: "&lt;if [Culture] is constructed in all [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities&gt;"
	Example: "&lt;if [Culture] is constructed in at least [3] of [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed by anybody&gt;"
	Example: "&lt;if [Culture] is constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed by anybody&gt;"
	Example: "&lt;if [Culture] is not constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	/ med [resource]

	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'

	Example: "&lt;when above [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
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
	/ i denna stad

	Applicable to: Conditional

??? example  "&lt;in [cityFilter] cities&gt;"
	Example: "&lt;in [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities connected to the capital&gt;"
	Applicable to: Conditional

??? example  "&lt;in cities with a [religionFilter] religion&gt;"
	Example: "&lt;in cities with a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities not following a [religionFilter] religion&gt;"
	Example: "&lt;in cities not following a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with a major religion&gt;"
	Applicable to: Conditional

??? example  "&lt;in cities with an enhanced religion&gt;"
	Applicable to: Conditional

??? example  "&lt;in cities following our religion&gt;"
	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	/ i städer med en [buildingFilter]

	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	/ i städer utan [buildingFilter]

	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [positiveAmount] [populationFilter]&gt;"
	/ i städer med minst [positiveAmount] [populationFilter]

	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with [positiveAmount] [populationFilter]&gt;"
	Example: "&lt;in cities with [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with between [amount] and [amount] [populationFilter]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;in cities with between [3] and [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with less than [amount] [populationFilter]&gt;"
	Example: "&lt;in cities with less than [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	/ med en garnison

	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	/ för [mapUnitFilter]-enheter

	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	/ för enheter med [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	/ för enheter utan [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	/ mot städer

	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	/ mot [mapUnitFilter]-enheter

	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;vs [combatantFilter]&gt;"
	Example: "&lt;vs [City]&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	/ vid strid mot enheter från en Civilisation med fler Städer än du

	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	/ vid anfall

	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	/ vid försvar

	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	/ vid strid i [tileFilter]-rutor

	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	/ på främmande kontinenter

	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	/ i angränsning till en [mapUnitFilter]-enhet

	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] HP&gt;"
	/ medan över [positiveAmount] KP

	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] HP&gt;"
	/ medan under [positiveAmount] KP

	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] movement&gt;"
	Example: "&lt;when below [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] movement&gt;"
	Example: "&lt;when above [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	/ om den inte gjort andra handlingar än

	Applicable to: Conditional

??? example  "&lt;when stacked with a [mapUnitFilter] unit&gt;"
	Example: "&lt;when stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when not stacked with a [mapUnitFilter] unit&gt;"
	Example: "&lt;when not stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	/ i [tileFilter]-rutor

	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	/ i rutor utan [tileFilter]

	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [positiveAmount] tiles of a [tileFilter]&gt;"
	/ inom [positiveAmount] rutor från en [tileFilter]

	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles adjacent to [tileFilter] tiles&gt;"
	Example: "&lt;in tiles adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles not adjacent to [tileFilter] tiles&gt;"
	Example: "&lt;in tiles not adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	/ på vattenkartor

	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	/ i [regionType]-regioner

	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	/ i alla utom [regionType]-regioner

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
	Example: "&lt;when carried by [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is enabled&gt;"
	Example: "&lt;if [DeCiv Redux] is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is not enabled&gt;"
	Example: "&lt;if [DeCiv Redux] is not enabled&gt;"

	Applicable to: Conditional

## TriggerCondition uniques
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [techFilter] technology&gt;"
	Example: "&lt;upon discovering [Agriculture] technology&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a new era&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war on [civFilter] Civilizations&gt;"
	Example: "&lt;upon declaring war on [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon being declared war on by [civFilter] Civilizations&gt;"
	Example: "&lt;upon being declared war on by [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a war with [civFilter] Civilizations&gt;"
	Example: "&lt;upon entering a war with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon signing a peace treaty with [civFilter] Civilizations&gt;"
	Example: "&lt;upon signing a peace treaty with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon ending a Golden Age&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon losing a city&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon founding a city&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon discovering a Natural Wonder&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter]&gt;"
	Example: "&lt;upon constructing [Culture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter] [cityFilter]&gt;"
	Example: "&lt;upon constructing [Culture] [in all cities]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon gaining a [baseUnitFilter] unit&gt;"
	Example: "&lt;upon gaining a [Melee] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon losing a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon losing a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon turn end&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a Pantheon&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon expending a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon expending a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

## UnitTriggerCondition uniques
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon conquering a city&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn end&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon entering combat&gt;"
	Applicable to: UnitTriggerCondition

??? example  "&lt;upon damaging a [mapUnitFilter] unit&gt;"
	Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'

	Example: "&lt;upon damaging a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] promotion&gt;"
	Example: "&lt;upon gaining the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] promotion&gt;"
	Example: "&lt;upon losing the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] status&gt;"
	Example: "&lt;upon gaining the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] status&gt;"
	Example: "&lt;upon losing the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [positiveAmount] HP in a single attack&gt;"
	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon entering a [tileFilter] tile&gt;"
	Example: "&lt;upon entering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	/ genom att förbruka denna enhet

	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	Will consume up to [amount] of Movement to execute

	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;for all movement&gt;"
	Will consume all Movement to execute

	Applicable to: UnitActionModifier

??? example  "&lt;requires [nonNegativeAmount] movement&gt;"
	Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up

	Example: "&lt;requires [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [stats] stats&gt;"
	A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock

	Example: "&lt;costs [+1 Gold, +2 Production] stats&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [amount] [stockpiledResource]&gt;"
	A positive Integer value will be subtracted from your stock. Do not confuse with "Costs [amount] [stockpiledResource]" (uppercase 'C') for Improvements, Buildings, and Units.

	Example: "&lt;costs [3] [Mana]&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;removing the [promotion] promotion/status&gt;"
	Removes the promotion/status from the unit - this is not a cost, units will be able to activate the action even without the promotion/status. To limit, use &lt;with the [promotion] promotion&gt; conditional

	Example: "&lt;removing the [Shock I] promotion/status&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	Applicable to: UnitActionModifier

??? example  "&lt;[positiveAmount] times&gt;"
	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[nonNegativeAmount] additional time(s)&gt;"
	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
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
	/ i [nonNegativeAmount] drag

	Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns

	Example: "&lt;for [3] turns&gt;"

	Applicable to: MetaModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

??? example  "&lt;hidden from users&gt;"
	Applicable to: MetaModifier

??? example  "&lt;for every [countable]&gt;"
	Works for positive numbers only

	Example: "&lt;for every [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every adjacent [tileFilter]&gt;"
	Works for positive numbers only

	Example: "&lt;for every adjacent [Farm]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every [positiveAmount] [countable]&gt;"
	Works for positive numbers only

	Example: "&lt;for every [3] [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;(modified by game speed)&gt;"
	Can only be applied to certain uniques, see details of each unique for specifics

	Applicable to: MetaModifier

??? example  "&lt;(modified by game progress up to [relativeAmount]%)&gt;"
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
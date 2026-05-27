# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)

## Triggerable uniques（Déclenchable）
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Supprime [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Vendre les bâtiments [buildingFilter] [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	/ Un(e) [unit] apparaît gratuitement

	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[positiveAmount] free [unit] units appear"
	/ [positiveAmount] unités [unit] apparaissent gratuitement

	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "A [unit] rebels"
	/ Un(e) [unit] se rebelle

	Example: "A [Musketman] rebels"

	Applicable to: Triggerable

??? example  "[positiveAmount] [unit]s rebel"
	/ [positiveAmount] unités [unit] se rebellent

	Example: "[3] [Musketman]s rebel"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	/ Doctrine gratuite

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Social Policies"
	/ [positiveAmount] Doctrine(s) gratuite(s)

	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	/ L'Empire entre dans un Âge d'Or

	Applicable to: Triggerable

??? example  "Empire enters a [positiveAmount]-turn Golden Age"
	/ L'Empire entre dans un Âge d'Or de [positiveAmount] tours

	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	/ Personnage Illustre gratuit

	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	/ [amount] Population [cityFilter]

	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	/ [amount] Population dans une ville aléatoire

	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	/ Découvrir [tech]

	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy/belief]"
	/ Adopter [policy/belief]

	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter]"
	/ Abandonner [policyFilter]

	Example: "Remove [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter] and refund [amount]% of its cost"
	/ Abandonner [policyFilter] et récupérer [amount]% de son coût

	Example: "Remove [Oligarchy] and refund [3]% of its cost"

	Applicable to: Triggerable

??? example  "Free Technology"
	/ Technologie gratuite

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Technologies"
	/ [positiveAmount] technologie(s) gratuite(s)

	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[positiveAmount] free random researchable Tech(s) from the [eraFilter]"
	/ [positiveAmount] technologie(s) gratuite(s) aléatoire(s) disponible(s) dans [eraFilter]

	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	/ Révèle toute la carte

	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	/ Gagner une croyance [beliefType] gratuite

	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	/ Déclenche le vote pour la Victoire Diplomatique

	Applicable to: Triggerable

??? example  "Instantly consumes [positiveAmount] [stockpiledResource]"
	/ Consomme instantanément [positiveAmount] [stockpiledResource]

	Example: "Instantly consumes [3] [Mana]"

	Applicable to: Triggerable

??? example  "Instantly provides [positiveAmount] [stockpiledResource]"
	/ Fournit instantanément [positiveAmount] [stockpiledResource]

	Example: "Instantly provides [3] [Mana]"

	Applicable to: Triggerable

??? example  "Set [stockpile] to [countable]"
	/ Passe [stockpile] à [countable]

	Example: "Set [Mana] to [1000]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Instantly gain [amount] [stockpile]"
	/ Gagne instantanément [amount] [stockpile]

	Example: "Instantly gain [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	/ Gagne [amount] [stat]

	Example: "Gain [3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	/ Octroie assez de Foi pour un Panthéon

	Applicable to: Triggerable

??? example  "Gain enough Faith for [positiveAmount]% of a Great Prophet"
	/ Octroie assez de Foi pour [positiveAmount]% d'un Grand Prophète

	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Research [relativeAmount]% of [tech]"
	/ Recherche [relativeAmount]% de [tech]

	Example: "Research [+20]% of [Agriculture]"

	Applicable to: Triggerable

??? example  "Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius"
	/ Gagne le contrôle des cases [tileFilter] dans un rayon de [nonNegativeAmount] case(s)

	Example: "Gain control over [Farm] tiles in a [3]-tile radius"

	Applicable to: Triggerable

??? example  "Gain control over [positiveAmount] tiles [cityFilter]"
	/ Gagne le contrôle de [positiveAmount] cases [cityFilter]

	Example: "Gain control over [3] tiles [in all cities]"

	Applicable to: Triggerable

??? example  "Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius"
	/ Révéler jusqu'à [positiveAmount/'all'] [tileFilter] dans un rayon de [positiveAmount] case(s)

	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	/ Déclenche l'alerte générale suivante : [comment]

	Supported on Policies and Technologies.

	For other targets, the generated Notification may not read nicely, and will likely not support translation. Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_, and that difference is currently handled by mapping text.

	Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts.

	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "Promotes all spies [positiveAmount] time(s)"
	/ Améliore tous les espions [positiveAmount] fois

	Example: "Promotes all spies [3] time(s)"

	Applicable to: Triggerable

??? example  "Gain an extra spy"
	/ Obtenir un espion supplémentaire

	Applicable to: Triggerable

??? example  "Turn this tile into a [terrainName] tile"
	/ Transformer cette case en case [terrainName]

	Example: "Turn this tile into a [Forest] tile"

	Applicable to: Triggerable

??? example  "Remove [resourceFilter] resources from this tile"
	/ Retire les ressources [resourceFilter] de cette case

	Example: "Remove [Strategic] resources from this tile"

	Applicable to: Triggerable

??? example  "Remove [improvementFilter] improvements from this tile"
	/ Retire les aménagements [improvementFilter] de cette case

	Example: "Remove [All Road] improvements from this tile"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Works only with promotions that are valid for the unit's type - or for promotions that do not specify any.

	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [positiveAmount] cities for free"
	/ Fournit gratuitement le bâtiment [stat] le moins cher dans vos [positiveAmount] premières villes

	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [positiveAmount] cities for free"
	/ Fournit gratuitement un(e) [buildingName] dans vos [positiveAmount] premières villes

	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Triggers a [event] event"
	/ Déclenche un événement [event]

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

## UnitTriggerable uniques（UnitéDéclenchable）
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "[unitTriggerTarget] heals [positiveAmount] HP"
	/ [unitTriggerTarget] récupère [positiveAmount] PV

	Example: "[This Unit] heals [3] HP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] takes [positiveAmount] damage"
	/ [unitTriggerTarget] subit [positiveAmount] dégâts

	Example: "[This Unit] takes [3] damage"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [amount] XP"
	/ [unitTriggerTarget] gagne [amount] XP

	Example: "[This Unit] gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free"
	/ [unitTriggerTarget] s'améliore gratuitement

	Example: "[This Unit] upgrades for free"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free including special upgrades"
	/ [unitTriggerTarget] s'améliore gratuitement, améliorations spéciales comprises

	Example: "[This Unit] upgrades for free including special upgrades"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] promotion"
	/ [unitTriggerTarget] gagne la promotion [promotion]

	Example: "[This Unit] gains the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] promotion"
	/ [unitTriggerTarget] perd la promotion [promotion]

	Example: "[This Unit] loses the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [positiveAmount] movement"
	/ [unitTriggerTarget] gagne [positiveAmount] Mouvement(s)

	Example: "[This Unit] gains [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses [positiveAmount] movement"
	/ [unitTriggerTarget] perd [positiveAmount] Mouvement(s)

	Example: "[This Unit] loses [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)"
	/ [unitTriggerTarget] gagne le statut [promotion] pour [positiveAmount] tour(s)

	Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns.

	Example: "[This Unit] gains the [Shock I] status for [3] turn(s)"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] status"
	/ [unitTriggerTarget] perd le statut [promotion]

	Example: "[This Unit] loses the [Shock I] status"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] is destroyed"
	/ [unitTriggerTarget] est détruit(e)

	Example: "[This Unit] is destroyed"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gets a name from the [unitNameGroup] group"
	/ [unitTriggerTarget] obtient un nom du groupe [unitNameGroup]

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
	/ [stats] pour chaque spécialiste [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] tous les [positiveAmount] citoyens [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] social policies adopted"
	/ [stats] toutes les [positiveAmount] Doctrines adoptées

	Only works for civ-wide stats

	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [positiveAmount] [civWideStat]"
	/ [stats] pour chaque [positiveAmount] [civWideStat]

	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] dans les villes sur les cases [terrainFilter]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] pour tous les bâtiments [buildingFilter]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] pour les cases [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] pour chaque route commerciale

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
	/ [relativeAmount]% [stat] pour chaque [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% Rendement pour chaque [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	/ [relativeAmount]% [stat] de la part des Cités-États

	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	/ [relativeAmount]% [stat] pour les routes commerciales

	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	/ Annule [stat] [cityFilter]

	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	/ Annule Croissance [cityFilter]

	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des bâtiments [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des unités [baseUnitFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des merveilles de l'ère [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Production pour tout bâtiment déjà présent dans la capitale

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount]% Rendement en pillant des cases

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount]% Santé en pillant des cases

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "Military Units gifted from City-States start with [positiveAmount] XP"
	/ Les unités militaires offertes par les Cités-États débutent avec [positiveAmount] XP

	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation"
	/ Les Cités-États Militaristes fournissent des unités [positiveAmount] fois plus vite lorsque vous êtes en guerre contre une nation commune

	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	/ Les présents en Or aux Cités-États génèrent [relativeAmount]% plus d'Influence

	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns"
	/ Peut dépenser de l'Or pour annexer ou fantochiser une Cité-État ayant été votre alliée pendant [nonNegativeAmount] tours

	Example: "Can spend Gold to annex or puppet a City-State that has been your Ally for [3] turns"

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	/ Le territoire des Cités-États compte toujours comme un territoire amical

	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	/ Les Cités-États alliées offrent occasionnellement des Personnages Illustres

	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	/ [relativeAmount]% de dégradation d'Influence sur les Cités-États

	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	/ Le point d'équilibre de l'Influence sur les Cités-États augmente de [amount]

	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	/ Les Cités-États alliées offrent un bonus [stat] égal à [relativeAmount]% de ce qu'elles produisent pour elles-mêmes

	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	/ [relativeAmount]% ressources offertes par les Cités-États

	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	/ [relativeAmount]% Bonheur pour les ressources de luxe offertes par les Cités-États

	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	/ L'influence sur les Cités-États remonte deux fois plus vite que la normale

	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% croissance [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% de la nourriture est conservée après augmentation de la population [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount]% Nourriture consommée par les [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	/ [relativeAmount]% Mécontentement dû au nombre de villes

	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Mécontentement de [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	/ [amount] Bonheur pour chaque type de ressource de luxe

	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	/ Conserver [relativeAmount]% du Bonheur d'une ressource de luxe après que le dernier exemplaire ait été échangé

	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	/ [relativeAmount]% du Bonheur en surplus est converti en [stat]

	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	/ Ne peut pas construire les unités [baseUnitFilter]

	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	/ Permet la construction des composants du Vaisseau Spatial

	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Peut acheter des unités [baseUnitFilter] pour [amount] [stat] [cityFilter] avec hausse graduelle du prix ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Peut acheter des bâtiments [buildingFilter] pour [amount] [stat] [cityFilter] avec hausse graduelle du prix ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Peut acheter des unités [baseUnitFilter] pour [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Peut acheter des bâtiments [buildingFilter] pour [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Peut acheter des unités [baseUnitFilter] avec [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Peut acheter les bâtiments [buildingFilter] avec [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Peut acheter des unités [baseUnitFilter] avec [stat] pour [nonNegativeAmount] fois leur coût normal en Production

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Peut acheter les bâtiments [buildingFilter] avec [stat] pour [nonNegativeAmount] fois leur coût normal en Production

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ Coût en [stat] pour l'achat d'objets dans les villes [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ Coût en [stat] pour l'achat des bâtiments [buildingFilter] [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ Coût en [stat] pour l'achat des unités [baseUnitFilter] [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	/ Permet de convertir la production des villes en [civWideStat]

	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	/ Améliore la vitesse de déplacement sur les routes

	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	/ Les routes relient les cases par-delà les rivières

	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	/ [relativeAmount]% coût d'entretien des routes & voies ferrées

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	/ Pas de coûts d'entretien pour les aménagements sur les cases [tileFilter]

	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount]% temps nécessaire pour construire les aménagements [improvementFilter]

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Peut construire les aménagements [improvementFilter] à une vitesse de [relativeAmount]%

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% coût d'entretien pour les bâtiments [buildingFilter] [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Remove [buildingFilter] [cityFilter]"
	/ Supprime [buildingFilter] [cityFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ Vendre les bâtiments [buildingFilter] [cityFilter]

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% coût en Culture pour l'expansion des frontières [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% coût en Or pour l'achat des cases [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	/ Chaque ville fondée augmente le coût en Culture des doctrines de [relativeAmount]% de moins que la normale

	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	/ [relativeAmount]% coût en Culture pour l'adoption de nouvelles Doctrines

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "Each city founded increases Science cost of Technologies [relativeAmount]% less than normal"
	/ Chaque ville fondée augmente le coût en Science des technologies de [relativeAmount]% de moins que la normale

	Example: "Each city founded increases Science cost of Technologies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Science cost of researching new Technologies"
	/ [relativeAmount]% coût en Science pour la recherche de nouvelles Technologies

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Science cost of researching new Technologies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	/ [stats] pour chaque Merveille Naturelle connue

	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)"
	Example: "[+1 Gold, +2 Production] for discovering a Natural Wonder (bonus enhanced to [+1 Gold, +2 Production] if first to discover it)"

	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% en génération de Personnage Illustre [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Or pour les missions commerciales des Grands Marchands

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Un Grand Général fournit un bonus de combat doublé

	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	/ Reçoit un Personnage Illustre gratuit à la fin de chaque [comment] (tous les 394 ans), après avoir recherché [tech]. Chaque personnage bonus ne peut être choisi qu'une seule fois.

	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	/ Une fois que le Compte Long est activé, l'année indiquée sur l'écran est celle du Compte Long Maya traditionnel.

	Applicable to: Global

??? example  "[amount] Unit Supply"
	/ [amount] Appro. Unités

	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [positiveAmount] population [cityFilter]"
	/ [amount] Appro. Unités tous les [positiveAmount] citoyens [cityFilter]

	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	/ [amount] Appro. Unités par ville

	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	/ [amount] unités ne coûtent aucune maintenance

	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	/ Les unités dans les villes ne coûtent aucune maintenance

	Applicable to: Global

??? example  "Enables embarkation for land units"
	/ Permet d'embarquer les unités terrestres

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	/ Permet aux unités [mapUnitFilter] de traverser les cases Océan

	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	/ Les unités terrestres peuvent franchir les cases [terrainName] après obtention du premier [baseUnitFilter]

	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory"
	/ Les unités [mapUnitFilter] ennemies dépensent [positiveAmount] point(s) de mouvement supplémentaire(s) sur votre territoire

	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Les nouvelles unités [baseUnitFilter] débutent avec [amount] XP [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Toutes les unités [baseUnitFilter] nouvellement formées [cityFilter] reçoivent la promotion [promotion]

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ Les unités [mapUnitFilter] adjacentes à cette ville récupèrent [amount] PV en phase de soin

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% XP required for promotions"
	/ [relativeAmount]% XP requis pour les promotions

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP required for promotions"

	Applicable to: Global

??? example  "[relativeAmount]% City Strength from defensive buildings"
	/ [relativeAmount]% aux bonus Puissance de la ville procurés par les bâtiments défensifs

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount]% Puissance pour les villes

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Fournit [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% [resourceFilter] resource production"
	/ [relativeAmount]% production de la ressource [resourceFilter]

	Example: "[+20]% [Strategic] resource production"

	Applicable to: Global

??? example  "Enables establishment of embassies"
	/ Permet d'établir des ambassades

	Applicable to: Global

??? example  "Requires establishing embassies to conduct advanced diplomacy"
	/ Nécessite d'établir des ambassades afin de mener une diplomatie avancée

	Applicable to: Global

??? example  "Enables Open Borders agreements"
	/ Permet les Droits de Libre Passage

	Applicable to: Global

??? example  "Enables Research agreements"
	/ Permet les Accords de Recherche

	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	/ Science gagnée suite aux accords de recherche [relativeAmount]%

	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	/ Permet les Pactes de Défense

	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	/ Lors d'une déclaration d'amitié, les deux parties reçoivent un bonus de [relativeAmount]% en génération de personnage illustre

	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	/ L'Influence de toutes les autres civilisations sur toutes les cités-états se dégrade [relativeAmount]% plus vite

	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	/ Gagne [amount] Influence en offrant un(e) [baseUnitFilter] à une Cité-État

	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	/ Point d'équilibre de l'Influence pour les Cités-États pratiquant cette religion [amount]

	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	/ Notification pour les nouveaux campements Barbares

	Applicable to: Global

??? example  "Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities"
	/ Recevoir [relativeAmount]% d'Or des campements barbares et du pillage des villes

	Example: "Receive [+20]% Gold from Barbarian encampments and pillaging Cities"

	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	/ Lors de la conquête d'un campement, gagne [amount] Or et recrute une unité Barbare

	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	/ Une unité [mapUnitFilter] vaincue rapporte [amount] Or et recrute l'unité

	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	/ Peut choisir [amount] croyance(s) [beliefType] supplémentaire(s) en [foundingOrEnhancing] une religion

	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	/ Peut choisir [amount] croyance(s) supplémentaire(s) de tout type en [foundingOrEnhancing] une religion

	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	/ [stats] quand une ville adopte cette religion pour la première fois

	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% Diffusion naturelle de la religion [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ La religion se diffuse naturellement aux villes à [amount] cases que la normale

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	/ Ne peut pas générer l'équivalent des grands prophètes naturellement

	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	/ [relativeAmount]% coût en Foi pour générer l'équivalent de Grand Prophète

	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "[relativeAmount]% spy effectiveness [cityFilter]"
	/ [relativeAmount]% efficacité des espions [cityFilter]

	Example: "[+20]% spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% enemy spy effectiveness [cityFilter]"
	/ [relativeAmount]% efficacité des espions ennemis [cityFilter]

	Example: "[+20]% enemy spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "New spies start with [amount] level(s)"
	/ Les nouveaux espions débutent au niveau [amount]

	Example: "New spies start with [3] level(s)"

	Applicable to: Global

??? example  "Triggers victory"
	/ Déclenche la victoire

	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	/ Déclenche une Victoire Culturelle une fois achevé

	Applicable to: Global

??? example  "May buy items in puppet cities"
	/ Peut acheter des objets dans les villes fantoches

	Applicable to: Global

??? example  "May not annex cities"
	/ Ne peut pas annexer les villes

	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	/ "Emprunte" les noms des villes aux autres civilisations de la partie

	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	/ Les villes sont rasées [amount] fois plus vite

	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	/ Reçoit un bonus technologique lors de la construction de bâtiments/merveilles scientifiques dans la capitale

	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	/ [relativeAmount]% durée d'un Âge d'Or

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	/ Population décimée par les attaques nucléaires [relativeAmount]% [cityFilter]

	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	/ Dégâts infligés aux garnisons par les attaques nucléaires [relativeAmount]% [cityFilter]

	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	/ Des unités rebelles peuvent apparaître

	Applicable to: Global

??? example  "Cannot build [buildingFilter] buildings"
	/ Ne peut pas construire les bâtiments [buildingFilter]

	Example: "Cannot build [Culture] buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Puissance

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] Puissance

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Puissance, diminuant avec la distance à la capitale

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% Bonus d'attaque par contournement

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] attaque(s) supplémentaire(s) par tour

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Mouvement

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Vision

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Portée

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] Portée Interception Aérienne

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] PV en phase de soin

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Puissance de Diffusion de la Religion

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ En diffusant la religion dans une ville, recevoir [amount] fois le nombre des fidèles d'autres religions en [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Les attaques à distance ignorent les obstacles

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	/ Aucun bonus défensif de terrain

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Aucune pénalité défensive de terrain

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ Aucune pénalité de dégâts pour les unités blessées

	Applicable to: Global, Unit

??? example  "Unable to capture cities"
	/ Ne peut pas conquérir les villes

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ Ne peut pas piller les cases

	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	/ Le pillage ne coûte pas de point de mouvement

	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	/ Peut se soigner en dehors d'un territoire amical

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Tous les effets de soins sont doublés

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Se soigne de [amount] dégâts s'il tue une unité

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Ne peut se soigner qu'en pillant

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% coût de maintenance

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% coût en Or de l'amélioration

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Recevoir [amount]% des dégâts infligés aux unités [combatantFilter] en [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ En capturant une ville, recevoir [amount] fois sa valeur de [stat] en [stockpile] immédiatement

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Recevoir [amount]% de [costOrStrength] des unités [mapUnitFilter] vaincues en [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	/ [amount] XP gagné après combat

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% XP gagné après combat

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] est obtenu [relativeAmount]% plus vite

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] point(s) de Mouvement pour débarquer

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] point(s) de Mouvement pour embarquer

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

## Nation uniques
??? example  "Starts with [tech]"
	/ Débute avec [tech]

	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	/ Démarre avec la doctrine [policy] adoptée

	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	/ Toutes les unités se déplacent à travers les cases Forêt et Jungle en territoire amical comme s'il s'agissait de routes. Ces cases peuvent être utilisées pour connecter des villes une fois la technologie de La Roue découverte.

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	/ Les unités ignorent les coûts de mouvement lorsqu'elles se déplacent sur des cases avec Collines

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Will not be chosen for new games"
	/ Ne sera pas choisi pour une nouvelle partie

	Applicable to: Nation

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Personality uniques（Tempérament）
??? example  "Will not build [baseUnitFilter/buildingFilter]"
	/ Ne construira pas [baseUnitFilter/buildingFilter]

	Example: "Will not build [Melee]"

	Applicable to: Personality

## Era uniques（Ère）
??? example  "Starting in this era disables religion"
	/ Démarrer durant cette ère désactive la religion

	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	/ Chaque civilisation majeure gagne un espion lorsque qu'une civilisation entre dans cette ère

	Applicable to: Era

## Tech uniques（Technologie）
??? example  "Starting tech"
	/ Technologie initiale

	Applicable to: Tech

??? example  "Can be continually researched"
	/ Peut être continuellement recherché

	Applicable to: Tech

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Cannot be hurried"
	/ Ne peut pas être accéléré

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

## Policy uniques（Doctrine）
??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

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

## FounderBelief uniques（Croyance Fondateur）
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	/ [stats] pour chaque ville au global pratiquant cette religion

	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [positiveAmount] global followers [cityFilter]"
	/ [stats] tous les [positiveAmount] fidèles au global [cityFilter]

	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

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

## FollowerBelief uniques（Croyance Fidèle）
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "[stats] [cityFilter]"
	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [stats] pour chaque spécialiste [cityFilter]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [stats] tous les [positiveAmount] citoyens [cityFilter]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ [stats] dans les villes sur les cases [terrainFilter]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ [stats] pour tous les bâtiments [buildingFilter]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [stats] pour les cases [tileFilter] [cityFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ [stats] pour chaque route commerciale

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
	/ [relativeAmount]% [stat] pour chaque [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% Rendement pour chaque [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des bâtiments [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des unités [baseUnitFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [relativeAmount]% Production pendant la construction des merveilles de l'ère [buildingFilter] [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ [relativeAmount]% Production pour tout bâtiment déjà présent dans la capitale

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [relativeAmount]% croissance [cityFilter]

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [amount]% de la nourriture est conservée après augmentation de la population [cityFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [relativeAmount]% Nourriture consommée par les [populationFilter] [cityFilter]

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [relativeAmount]% Mécontentement de [populationFilter] [cityFilter]

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Peut acheter des unités [baseUnitFilter] pour [amount] [stat] [cityFilter] avec hausse graduelle du prix ([nonNegativeAmount])

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ Peut acheter des bâtiments [buildingFilter] pour [amount] [stat] [cityFilter] avec hausse graduelle du prix ([nonNegativeAmount])

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ Peut acheter des unités [baseUnitFilter] pour [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ Peut acheter des bâtiments [buildingFilter] pour [nonNegativeAmount] [stat] [cityFilter]

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ Peut acheter des unités [baseUnitFilter] avec [stat] [cityFilter]

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ Peut acheter les bâtiments [buildingFilter] avec [stat] [cityFilter]

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Peut acheter des unités [baseUnitFilter] avec [stat] pour [nonNegativeAmount] fois leur coût normal en Production

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ Peut acheter les bâtiments [buildingFilter] avec [stat] pour [nonNegativeAmount] fois leur coût normal en Production

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ Coût en [stat] pour l'achat d'objets dans les villes [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ Coût en [stat] pour l'achat des bâtiments [buildingFilter] [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ Coût en [stat] pour l'achat des unités [baseUnitFilter] [relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [relativeAmount]% coût d'entretien pour les bâtiments [buildingFilter] [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [relativeAmount]% coût en Culture pour l'expansion des frontières [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [relativeAmount]% coût en Or pour l'achat des cases [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [relativeAmount]% en génération de Personnage Illustre [cityFilter]

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ Les nouvelles unités [baseUnitFilter] débutent avec [amount] XP [cityFilter]

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ Toutes les unités [baseUnitFilter] nouvellement formées [cityFilter] reçoivent la promotion [promotion]

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ Les unités [mapUnitFilter] adjacentes à cette ville récupèrent [amount] PV en phase de soin

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Strength for cities"
	/ [relativeAmount]% Puissance pour les villes

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ Fournit [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [relativeAmount]% Diffusion naturelle de la religion [cityFilter]

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ La religion se diffuse naturellement aux villes à [amount] cases que la normale

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion"
	/ Recevoir [amount]% de [costOrStrength] des unités [mapUnitFilter] en [stockpile] quand vaincues à 4 cases ou moins d'une ville pratiquant cette religion

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

## Building uniques（Bâtiment）
??? example  "[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]"
	/ [positiveAmount]% de [stat] de chaque [improvementFilter/buildingFilter] dans la ville ajouté à [resource]

	Example: "[3]% of [Culture] from every [All Road] in the city added to [Iron]"

	Applicable to: Building

??? example  "Consumes [amount] [resource]"
	/ Consomme [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Coûte [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Non-constructible

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Ne peut pas être acheté

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Peut être acheté avec [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Peut être acheté pour [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Limité à [amount] par Civilisation

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ La Nourriture en surplus est convertie en Production durant la construction

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Nécessite au moins [amount] en population

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Déclenche une alerte générale au démarrage de la construction

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Déclenche une alerte générale une fois achevé

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Le coût augmente de [amount] par ville possédée

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ Le coût augmente de [amount] quand construit

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount]% coût de production

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Peut être construit uniquement

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	/ Vous devez posséder une case [tileFilter] dans un rayon de [amount] cases

	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	/ Permet la création d'armes nucléaires

	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	/ Ne doit pas être sur [tileFilter]

	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	/ Ne doit pas être à côté de [tileFilter]

	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	/ Invendable

	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	/ Indique la capitale

	Applicable to: Building

??? example  "Moves to new capital when capital changes"
	/ Est transféré(e) vers la nouvelle capitale lors d'un changement de capitale

	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	/ Fournit un exemplaire supplémentaire de chaque ressource de luxe aménagée près de cette ville

	Applicable to: Building

??? example  "Destroyed when the city is captured"
	/ Détruit(e) quand la ville est conquise

	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	/ Jamais détruit(e) quand la ville est conquise

	Applicable to: Building

??? example  "[relativeAmount]% Gold given to enemy if city is captured"
	/ [relativeAmount]% d'Or donné à l'ennemi si la ville est conquise

	Example: "[+20]% Gold given to enemy if city is captured"

	Applicable to: Building

??? example  "Removes extra unhappiness from annexed cities"
	/ Supprime le mécontentement supplémentaire des villes annexées

	Applicable to: Building

??? example  "Connects trade routes over water"
	/ Connecte les routes commerciales maritimes

	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	/ Construit(e) automatiquement dans toutes les villes où cela est possible

	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	/ Crée un aménagement [improvementName] sur une case spécifique

	When choosing to construct this building, the player must select a tile where the improvement can be built. Upon building completion, the tile will gain this improvement. Limited to one per building.

	Example: "Creates a [Trading Post] improvement on a specific tile"

	This unique does not support conditionals.

	Applicable to: Building

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Peut transporter [amount] unité(s) [mapUnitFilter] supplémentaire(s)

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Spaceship part"
	/ Composant de Vaisseau Spatial

	Applicable to: Building, Unit

??? example  "Cannot be hurried"
	/ Ne peut pas être accéléré

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

## UnitAction uniques（Action unité）
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	/ Fonde une nouvelle ville

	Applicable to: UnitAction

??? example  "Founds a new puppet city"
	/ Fonde une nouvelle ville fantoche

	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	/ Peut construire instantanément un(e) [improvementFilter]

	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	/ Peut diffuser la religion

	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	/ Peut supprimer les autres religions dans les villes

	Applicable to: UnitAction

??? example  "May found a religion"
	/ Peut fonder une religion

	Applicable to: UnitAction

??? example  "May enhance a religion"
	/ Peut renforcer une religion

	Applicable to: UnitAction

??? example  "Can transform to [unit]"
	/ Transformable en [unit]

	By default consumes all movement

	Example: "Can transform to [Musketman]"

	Applicable to: UnitAction

## Unit uniques（Unité）
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ [relativeAmount]% Rendement en pillant des cases

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ [relativeAmount]% Santé en pillant des cases

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ [relativeAmount]% temps nécessaire pour construire les aménagements [improvementFilter]

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ Peut construire les aménagements [improvementFilter] à une vitesse de [relativeAmount]%

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]% Or pour les missions commerciales des Grands Marchands

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ Un Grand Général fournit un bonus de combat doublé

	Applicable to: Global, Unit

??? example  "Consumes [amount] [resource]"
	/ Consomme [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Coûte [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Non-constructible

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ Ne peut pas être acheté

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ Peut être acheté avec [stat] [cityFilter]

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ Peut être acheté pour [amount] [stat] [cityFilter]

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ Limité à [amount] par Civilisation

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ La Nourriture en surplus est convertie en Production durant la construction

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ Nécessite au moins [amount] en population

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ Déclenche une alerte générale au démarrage de la construction

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ Déclenche une alerte générale une fois achevé

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ Le coût augmente de [amount] par ville possédée

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ Le coût augmente de [amount] quand construit

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ [amount]% coût de production

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ Peut être construit uniquement

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "May create improvements on water resources"
	/ Peut créer des aménagements de ressources aquatiques

	Applicable to: Unit

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	/ Peut bâtir des aménagements [improvementFilter/terrainFilter] sur les cases

	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	/ Peut être ajouté à [comment] dans la Capitale

	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	/ Empêche la diffusion d'une religion dans la ville où il se trouve

	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	/ Supprime les autres religions en diffusant la sienne

	Applicable to: Unit

??? example  "May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away"
	/ Peut être parachuté sur les cases [tileFilter] jusqu'à [positiveAmount] cases de distance s'il se trouve

	Example: "May Paradrop to [Farm] tiles up to [3] tiles away"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	/ Peut effectuer un Balayage aérien

	Applicable to: Unit

??? example  "Can speed up construction of a building"
	/ Peut accélérer la construction d'un bâtiment

	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	/ Peut accélérer la construction d'une merveille

	Applicable to: Unit

??? example  "Can hurry technology research"
	/ Peut accélérer une recherche technologique

	Applicable to: Unit

??? example  "Can generate a large amount of culture"
	/ Peut générer une grande quantité de Culture

	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	/ Peut entreprendre une mission commerciale avec une Cité-État, rapportant une grande quantité d'or et [amount] Influence

	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Automation is a primary action"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "[relativeAmount]% Strength"
	/ [relativeAmount]% Puissance

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ [relativeAmount] Puissance

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ [relativeAmount]% Puissance, diminuant avec la distance à la capitale

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ [relativeAmount]% Bonus d'attaque par contournement

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles"
	/ [relativeAmount]% Puissance pour les unités [mapUnitFilter] ennemies sur les cases [tileFilter] adjacentes

	Example: "[+20]% Strength for enemy [Wounded] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	/ [relativeAmount]% bonus de Puissance pour les unités [mapUnitFilter] dans un rayon de [amount] cases

	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "[amount] additional attacks per turn"
	/ [amount] attaque(s) supplémentaire(s) par tour

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ [amount] Mouvement

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ [amount] Vision

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ [amount] Portée

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ [relativeAmount] Portée Interception Aérienne

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ [amount] PV en phase de soin

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ [relativeAmount]% Puissance de Diffusion de la Religion

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ En diffusant la religion dans une ville, recevoir [amount] fois le nombre des fidèles d'autres religions en [stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Can only attack [combatantFilter] units"
	/ Peut seulement attaquer les unités [combatantFilter]

	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	/ Peut seulement attaquer les cases [tileFilter]

	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	/ Ne peut pas attaquer

	Applicable to: Unit

??? example  "Must set up to ranged attack"
	/ Doit être installé pour attaquer à distance

	Applicable to: Unit

??? example  "Self-destructs when attacking"
	/ S'autodétruit lors de l'attaque

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	/ Supprime la pénalité de combat en attaquant sur une côte

	Applicable to: Unit

??? example  "May attack when embarked"
	/ Peut attaquer quand embarqué(e)

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	/ Supprime la pénalité de combat en attaquant par-delà une rivière

	Applicable to: Unit

??? example  "Blast radius [amount]"
	/ Rayon de déflagration [amount]

	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ Les attaques à distance ignorent les obstacles

	Applicable to: Global, Unit

??? example  "Nuclear weapon of Strength [amount]"
	/ Arme nucléaire de Puissance [amount]

	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "No defensive terrain bonus"
	/ Aucun bonus défensif de terrain

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ Aucune pénalité défensive de terrain

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ Aucune pénalité de dégâts pour les unités blessées

	Applicable to: Global, Unit

??? example  "Uncapturable"
	/ Non-capturable

	Applicable to: Unit

??? example  "Withdraws before melee combat"
	/ Se replie avant le combat rapproché

	Applicable to: Unit

??? example  "Unable to capture cities"
	/ Ne peut pas conquérir les villes

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ Ne peut pas piller les cases

	Applicable to: Global, Unit

??? example  "Destroys [cityFilter] cities instead of capturing"
	/ Détruit les villes [cityFilter] au lieu de les conquérir

	The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities.Capital cities (including city states) are immune to this effect.

	Example: "Destroys [in all cities] cities instead of capturing"

	Applicable to: Unit

??? example  "No movement cost to pillage"
	/ Le pillage ne coûte pas de point de mouvement

	Applicable to: Global, Unit

??? example  "Can move after attacking"
	/ Peut se déplacer après avoir attaqué

	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	/ Transfère les points de Mouvement à [mapUnitFilter]

	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	/ Peut se déplacer immédiatement après achat

	Applicable to: Unit

??? example  "May heal outside of friendly territory"
	/ Peut se soigner en dehors d'un territoire amical

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ Tous les effets de soins sont doublés

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ Se soigne de [amount] dégâts s'il tue une unité

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ Ne peut se soigner qu'en pillant

	Applicable to: Global, Unit

??? example  "Unit will heal every turn, even if it performs an action"
	/ L'unité se soignera à chaque tour, même si elle effectue une action

	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	/ Toutes les unités adjacentes récupèrent [amount] PV en phase de soin

	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	/ Pas de Vision

	Applicable to: Unit

??? example  "Can see over obstacles"
	/ Peut voir par-delà les obstacles

	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	/ Peut transporter [amount] unités [mapUnitFilter]

	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ Peut transporter [amount] unité(s) [mapUnitFilter] supplémentaire(s)

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	/ Ne peut pas être transporté par les unités [mapUnitFilter]

	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	/ [relativeAmount]% de chances d'intercepter les attaques aériennes

	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	/ Les dégâts subis suite à une interception sont réduits de [relativeAmount]%

	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	/ [relativeAmount]% Dégâts en phase d'interception

	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	/ [amount] interception(s) supplémentaire(s) pouvant être effectuée(s) par tour

	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	/ Ne peut pas être intercepté

	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	/ Ne peut pas intercepter les unités [mapUnitFilter]

	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	/ [relativeAmount]% Puissance en effectuant un Balayage aérien

	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "[relativeAmount]% maintenance costs"
	/ [relativeAmount]% coût de maintenance

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ [relativeAmount]% coût en Or de l'amélioration

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ Recevoir [amount]% des dégâts infligés aux unités [combatantFilter] en [stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ En capturant une ville, recevoir [amount] fois sa valeur de [stat] en [stockpile] immédiatement

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ Recevoir [amount]% de [costOrStrength] des unités [mapUnitFilter] vaincues en [stockpile]

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "May capture killed [mapUnitFilter] units"
	/ Peut capturer des unités [mapUnitFilter] vaincues

	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "[amount] XP gained from combat"
	/ [amount] XP gagné après combat

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ [relativeAmount]% XP gagné après combat

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "Can be earned through combat"
	/ Peut être obtenu en combattant

	Applicable to: Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson] est obtenu [relativeAmount]% plus vite

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "Invisible to others"
	/ Invisible aux autres

	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	/ Invisible pour les unités non-adjacentes

	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	/ Peut voir les unités [mapUnitFilter] invisibles

	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [unit] through ruins-like effects"
	/ Peut s'améliorer en [unit] via les bonus de ruines

	Example: "May upgrade to [Musketman] through ruins-like effects"

	Applicable to: Unit

??? example  "Can upgrade to [unit]"
	/ Peut être amélioré en [unit]

	Example: "Can upgrade to [Musketman]"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	/ Détruit les aménagements de case en attaquant

	Applicable to: Unit

??? example  "Cannot move"
	/ Ne peut pas se déplacer

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	/ Mouvement doublé sur [terrainFilter]

	Example: "Double movement in [Fresh Water]"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	/ Toutes les cases coûtent 1 mouvement

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	/ Peut se déplacer sur les cases Eau sans embarquer

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	/ Peut passer les cases infranchissables

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores terrain cost"
	/ Ignore le coût du terrain

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores Zone of Control"
	/ Ignore la Zone de Contrôle

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Rough terrain penalty"
	/ Pénalité sur terrain accidenté

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can enter ice tiles"
	/ Peut traverser les cases Glace

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Cannot embark"
	/ Ne peut pas embarquer

	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	/ Ne peut pas traverser les cases Océan

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	/ Peut pénétrer sur les cases étrangères sans libre passage

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	/ Peut pénétrer sur les cases étrangères sans libre passage, mais perd [amount] puissance religieuse à chaque tour terminé sur celles-ci

	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ [nonNegativeAmount] point(s) de Mouvement pour débarquer

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ [nonNegativeAmount] point(s) de Mouvement pour embarquer

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "Never appears as a Barbarian unit"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "Religious Unit"
	/ Unité religieuse

	Applicable to: Unit

??? example  "Spaceship part"
	/ Composant de Vaisseau Spatial

	Applicable to: Building, Unit

??? example  "Takes your religion over the one in their birth city"
	/ Impose votre religion sur celle actuelle dans sa ville de naissance

	Applicable to: Unit

??? example  "Great Person - [comment]"
	/ Personnage Illustre - [comment]

	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
	/ Fait partie du groupe Personnages Illustres [comment]

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

## UnitType uniques（Type Unité）
??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Promotion uniques
??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Doing so will consume this opportunity to choose a Promotion"
	/ Ce choix se substitue à la possibilité actuelle d'attribuer une Promotion

	Applicable to: Promotion

??? example  "This Promotion is free"
	/ Cette Promotion est gratuite

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

## Terrain uniques
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[amount] Sight"
	/ [amount] Vision

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
	/ Accorde [stats] à la première civilisation réalisant sa découverte

	Example: "Grants [+1 Gold, +2 Production] to the first civilization to discover it"

	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	/ Les unités terminant leur tour sur ce terrain subissent [amount] dégâts

	Example: "Units ending their turn on this terrain take [3] damage"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	This unique does not support conditionals.

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	/ Attribue [promotion] ([comment]) aux unités [mapUnitFilter] adjacentes pour le reste de la partie

	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	/ [amount] Puissance pour les villes bâties sur ce terrain

	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time bonus of [stats] to the closest city when cut down"
	/ Fournit un bonus unique de [stats] à la ville la plus proche quand défrichée

	Example: "Provides a one-time bonus of [+1 Gold, +2 Production] to the closest city when cut down"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Terrain

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ La case fournit un rendement sans population assignée

	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	/ Annule toutes les autres stats que fournit cette case

	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	/ Seuls les aménagements [improvementFilter] peuvent être bâtis sur cette case

	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	/ Bloque le champ de vision pour les cases à la même élévation

	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	/ A une élévation de [amount] pour le calcul de la vision

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
	/ Caractéristique rare

	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	/ [amount]% de chances d'être détruit(e) par les bombes nucléaires

	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	/ Eau douce

	Applicable to: Terrain

??? example  "Rough terrain"
	/ Terrain accidenté

	Applicable to: Terrain

??? example  "Coastal Water"
	/ Eaux côtières

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

## Improvement uniques（Aménagement）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "Consumes [amount] [resource]"
	/ Consomme [amount] [resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Provides [amount] [resource]"
	/ Fournit [amount] [resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ Coûte [amount] [stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ Non-constructible

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "[amount] Sight"
	/ [amount] Vision

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ La case fournit un rendement sans population assignée

	Applicable to: Terrain, Improvement

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Can also be built on tiles adjacent to fresh water"
	/ Peut également être construit sur des cases adjacentes à Eau douce

	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	/ [stats] pour les cases [tileFilter]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	/ Garantit un rendement de case minimum de [stats]

	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	/ Peut être construit hors de vos frontières

	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	/ Peut être construit sur le bord externe de vos frontières

	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	/ Peut être construit uniquement sur des cases [tileFilter]

	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	/ Ne peut pas être construit sur des cases [tileFilter]

	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	/ Peut être construit uniquement pour aménager une ressource

	Applicable to: Improvement

??? example  "Does not need removal of [terrainFeature]"
	Example: "Does not need removal of [Hill]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	/ Détruit les caractéristiques supprimables quand construit

	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	/ Accorde un bonus défensif de [relativeAmount]%

	Does not accept unit-based conditionals

	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	/ Coûte [amount] [stat] par tour quand construit sur votre territoire

	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	/ Coûte [amount] [stat] par tour

	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	/ Les unités ennemies adjacentes terminant leur tour subissent [amount] dégâts

	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	/ Grand Aménagement

	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	/ Offre un bonus aléatoire lorsque visité

	Applicable to: Improvement

??? example  "Unpillagable"
	/ Non-pillable

	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	/ Piller cet aménagement rapporte environ [stats]

	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	/ Piller cet aménagement rapporte [stats]

	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Destroyed when pillaged"
	/ Détruit(e) quand pillé(e)

	Applicable to: Improvement

??? example  "Irremovable"
	/ Insupprimable

	Applicable to: Improvement

??? example  "Will not be replaced by automated units"
	/ Ne sera pas remplacé par les unités automatisées

	Applicable to: Improvement

??? example  "Improves [resourceFilter] resource in this tile"
	/ Aménage la ressource [resourceFilter] sur cette case

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

## Resource uniques（Ressource）
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
	/ Les gisements sur les cases [tileFilter] fournissent toujours [amount] ressources

	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	/ Peut seulement être créé par les Cités-États Mercantiles

	Applicable to: Resource

??? example  "Stockpiled"
	/ Stocké

	This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment.The current stockpiled amount can be affected with trigger uniques.

	Applicable to: Resource

??? example  "City-level resource"
	/ Ressource de ville

	This resource is calculated on a per-city level rather than a per-civ level

	Applicable to: Resource

??? example  "Cannot be traded"
	/ Ne peut pas être échangé

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
	/ Garanti avec l'option Équilibre Stratégique pour les ressources

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

## Ruins uniques（Ruines）
??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Free [unit] found in the ruins"
	/ [unit] gratuit(e) trouvé(e) dans les ruines

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

## Speed uniques（Vitesse）
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

## Difficulty uniques（Difficulté）
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

## CityState uniques（Cité-État）
??? example  "Provides military units every ≈[positiveAmount] turns"
	/ Fournit des unités militaires tous les ≈[positiveAmount] tours

	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	/ Fournit une ressource de luxe unique

	Applicable to: CityState

## ModOptions uniques（Options Mod）
??? example  "Diplomatic relationships cannot change"
	/ Les relations diplomatiques ne peuvent pas changer

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can convert gold to science with sliders"
	/ Possibilité de convertir l'Or en Science via un slider

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow City States to spawn with additional units"
	/ Autoriser la génération des Cités-États avec des unités additionnelles

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can trade civilization introductions for [positiveAmount] Gold"
	/ Possibilité d'échanger la rencontre d'une civilisation pour [positiveAmount] Or

	Example: "Can trade civilization introductions for [3] Gold"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Disable religion"
	/ Désactiver la religion

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can only start games from the starting era"
	/ Peut uniquement débuter la partie à l'ère de départ

	In this case, 'starting era' means the first defined Era in the entire ruleset.

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze capital"
	/ Autorisation de raser les capitales

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze holy city"
	/ Autorisation de raser les villes saintes

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

??? example  "Mod is incompatible with [modFilter]"
	/ Le mod est incompatible avec [modFilter]

	Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible.

	Example: "Mod is incompatible with [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod requires [modFilter]"
	/ Le mod nécessite [modFilter]

	Specifies that your Extension Mod is only available if any other Mod matching the filter is active.

	Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue.

	Example: "Mod requires [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	/ Devrait être activé uniquement comme mod audiovisuel permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	/ Peut être activé comme mod audiovisuel permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	/ Ne peut pas être activé comme mod audiovisuel permanent

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod preselects map [comment]"
	/ Le mod utilise la carte [comment]

	Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive.

	Example: "Mod preselects map [comment]"

	This unique does not support conditionals.

	Applicable to: ModOptions

## Event uniques（Événement）
??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

## EventChoice uniques（ÉvénementChoix）
??? example  "Only available"
	/ Uniquement disponible

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ Indisponible

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

## Conditional uniques（Conditionnel）
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;every [positiveAmount] turns&gt;"
	/ tous les [positiveAmount] tours

	Example: "&lt;every [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;before turn number [nonNegativeAmount]&gt;"
	/ avant le tour [nonNegativeAmount]

	Example: "&lt;before turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;after turn number [nonNegativeAmount]&gt;"
	/ après le tour [nonNegativeAmount]

	Example: "&lt;after turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;on [speed] game speed&gt;"
	/ en vitesse de jeu [speed]

	Example: "&lt;on [Quick] game speed&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty&gt;"
	/ en difficulté [difficulty]

	Example: "&lt;on [Prince] difficulty&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or higher&gt;"
	/ en difficulté [difficulty] ou plus

	Example: "&lt;on [Prince] difficulty or higher&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or lower&gt;"
	/ en difficulté [difficulty] ou moins

	Example: "&lt;on [Prince] difficulty or lower&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is enabled&gt;"
	/ quand la Victoire [victoryType] est activée

	Example: "&lt;when [Domination] Victory is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is disabled&gt;"
	/ quand la Victoire [victoryType] est désactivée

	Example: "&lt;when [Domination] Victory is disabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when religion is enabled&gt;"
	/ quand la religion est activée

	Applicable to: Conditional

??? example  "&lt;when religion is disabled&gt;"
	/ quand la religion est désactivée

	Applicable to: Conditional

??? example  "&lt;when espionage is enabled&gt;"
	/ quand l'espionnage est activé

	Applicable to: Conditional

??? example  "&lt;when espionage is disabled&gt;"
	/ quand l'espionnage est désactivé

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are enabled&gt;"
	/ quand les armes nucléaires sont activées

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are disabled&gt;"
	/ quand les armes nucléaires sont désactivées

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount]% chance&gt;"
	/ avec [nonNegativeAmount]% de chances

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
	/ pour les civilisations [civFilter]

	Example: "&lt;for [City-States] Civilizations&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	/ quand en guerre

	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	/ quand en paix

	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	/ durant un Âge d'Or

	Applicable to: Conditional

??? example  "&lt;when not in a Golden Age&gt;"
	/ en n'étant pas dans un Âge d'Or

	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	/ pendant la Fête du Roi

	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	/ quand l'empire est heureux

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	/ pendant [era]

	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	/ avant [era]

	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	/ à partir de [era]

	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	/ si débute à [era]

	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	/ si aucune autre Civilisation n'a recherché ceci

	Applicable to: Conditional

??? example  "&lt;after discovering [techFilter]&gt;"
	/ après avoir découvert [techFilter]

	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [techFilter]&gt;"
	/ avant de découvrir [techFilter]

	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;while researching [techFilter]&gt;"
	/ pendant la recherche de [techFilter]

	This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)

	Example: "&lt;while researching [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	/ si aucune autre Civilisation n'a adopté ceci

	Applicable to: Conditional

??? example  "&lt;if no Civilization has adopted [policy/belief]&gt;"
	/ si aucune Civilisation n'a adopté [policy/belief]

	Example: "&lt;if no Civilization has adopted [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	/ après avoir adopté [policy/belief]

	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
	/ avant d'adopter [policy/belief]

	Example: "&lt;before adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before founding a Pantheon&gt;"
	/ avant de fonder un Panthéon

	Applicable to: Conditional

??? example  "&lt;after founding a Pantheon&gt;"
	/ après avoir fondé un Panthéon

	Applicable to: Conditional

??? example  "&lt;before founding a religion&gt;"
	/ avant de fonder une religion

	Applicable to: Conditional

??? example  "&lt;after founding a religion&gt;"
	/ après avoir fondé une religion

	Applicable to: Conditional

??? example  "&lt;before enhancing a religion&gt;"
	/ avant de renforcer une religion

	Applicable to: Conditional

??? example  "&lt;after enhancing a religion&gt;"
	/ après avoir renforcé une religion

	Applicable to: Conditional

??? example  "&lt;after generating a Great Prophet&gt;"
	/ après avoir généré un Grand Prophète

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	/ si un(e) [buildingFilter] est construit(e)

	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed&gt;"
	/ si un(e) [buildingFilter] n'est pas construit(e)

	Example: "&lt;if [Culture] is not constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in all [cityFilter] cities&gt;"
	/ si un(e) [buildingFilter] est construit(e) dans toutes les villes [cityFilter]

	Example: "&lt;if [Culture] is constructed in all [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities&gt;"
	/ si un(e) [buildingFilter] est construit(e) dans au moins [positiveAmount] villes [cityFilter]

	Example: "&lt;if [Culture] is constructed in at least [3] of [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed by anybody&gt;"
	/ si un(e) [buildingFilter] est construit(e) par quiconque

	Example: "&lt;if [Culture] is constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed by anybody&gt;"
	/ si un(e) [buildingFilter] n'est pas construit(e) par quiconque

	Example: "&lt;if [Culture] is not constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	/ avec [resource]

	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	/ sans [resource]

	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	/ quand à plus de [amount] [stat/resource]

	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'

	Example: "&lt;when above [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
	/ quand à moins de [amount] [stat/resource]

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
	/ dans cette ville

	Applicable to: Conditional

??? example  "&lt;in [cityFilter] cities&gt;"
	/ dans les villes [cityFilter]

	Example: "&lt;in [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities connected to the capital&gt;"
	/ dans les villes connectées à la capitale

	Applicable to: Conditional

??? example  "&lt;in cities with a [religionFilter] religion&gt;"
	/ dans les villes avec une religion [religionFilter]

	Example: "&lt;in cities with a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities not following a [religionFilter] religion&gt;"
	/ dans les villes ne pratiquant pas une religion [religionFilter]

	Example: "&lt;in cities not following a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with a major religion&gt;"
	/ dans les villes avec une religion majeure

	Applicable to: Conditional

??? example  "&lt;in cities with an enhanced religion&gt;"
	/ dans les villes avec une religion renforcée

	Applicable to: Conditional

??? example  "&lt;in cities following our religion&gt;"
	/ dans les villes pratiquant votre religion

	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	/ dans les villes avec [buildingFilter]

	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	/ dans les villes sans [buildingFilter]

	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [positiveAmount] [populationFilter]&gt;"
	/ dans les villes avec au moins [positiveAmount] en [populationFilter]

	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with [positiveAmount] [populationFilter]&gt;"
	/ dans les villes avec [positiveAmount] en [populationFilter]

	Example: "&lt;in cities with [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with between [amount] and [amount] [populationFilter]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;in cities with between [3] and [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with less than [amount] [populationFilter]&gt;"
	/ dans les villes avec moins de [amount] en [populationFilter]

	Example: "&lt;in cities with less than [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	/ avec une garnison

	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	/ pour les unités [mapUnitFilter]

	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	/ quand [mapUnitFilter]

	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	/ pour les unités avec [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	/ pour les unités sans [promotion]

	Also applies to units with temporary status

	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	/ contre les villes

	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	/ contre les unités [mapUnitFilter]

	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;vs [combatantFilter]&gt;"
	Example: "&lt;vs [City]&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	/ en combat contre les unités d'une Civilisation ayant plus de Villes que vous

	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	/ en phase offensive

	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	/ en phase défensive

	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	/ en combat sur les cases [tileFilter]

	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	/ sur les continents étrangers

	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	/ adjacente(s) à une unité [mapUnitFilter]

	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] HP&gt;"
	/ quand à plus de [positiveAmount] PV

	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] HP&gt;"
	/ quand à moins de [positiveAmount] PV

	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] movement&gt;"
	/ quand à moins de [positiveAmount] Mouvements

	Example: "&lt;when below [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] movement&gt;"
	/ quand à plus de [positiveAmount] Mouvements

	Example: "&lt;when above [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	/ si aucune autre action n'a été utilisée auparavant

	Applicable to: Conditional

??? example  "&lt;when stacked with a [mapUnitFilter] unit&gt;"
	/ quand empilé avec une unité [mapUnitFilter]

	Example: "&lt;when stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when not stacked with a [mapUnitFilter] unit&gt;"
	/ quand non empilé avec une unité [mapUnitFilter]

	Example: "&lt;when not stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	/ sur les cases [tileFilter]

	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	/ sur les cases sans [tileFilter]

	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [positiveAmount] tiles of a [tileFilter]&gt;"
	/ à [positiveAmount] cases ou moins de [tileFilter]

	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles adjacent to [tileFilter] tiles&gt;"
	/ sur les cases adjacentes à des cases [tileFilter]

	Example: "&lt;in tiles adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles not adjacent to [tileFilter] tiles&gt;"
	/ sur les cases non adjacentes à des cases [tileFilter]

	Example: "&lt;in tiles not adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	/ sur les cartes maritimes

	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	/ dans les Régions [regionType]

	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	/ partout sauf les Régions [regionType]

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
	/ quand transporté par les unités [mapUnitFilter]

	Example: "&lt;when carried by [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is enabled&gt;"
	/ si [modFilter] est activé

	Example: "&lt;if [DeCiv Redux] is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is not enabled&gt;"
	/ si [modFilter] n'est pas activé

	Example: "&lt;if [DeCiv Redux] is not enabled&gt;"

	Applicable to: Conditional

## TriggerCondition uniques（DéclencherCondition）
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [techFilter] technology&gt;"
	/ en découvrant la technologie [techFilter]

	Example: "&lt;upon discovering [Agriculture] technology&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	/ en entrant dans [era]

	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a new era&gt;"
	/ en entrant dans une nouvelle ère

	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	/ en adoptant [policy/belief]

	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war on [civFilter] Civilizations&gt;"
	/ en déclarant la guerre à des civilisations [civFilter]

	Example: "&lt;upon declaring war on [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon being declared war on by [civFilter] Civilizations&gt;"
	/ en s'étant fait déclarer la guerre par des civilisations [civFilter]

	Example: "&lt;upon being declared war on by [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a war with [civFilter] Civilizations&gt;"
	/ en participant à une guerre avec des civilisations [civFilter]

	Example: "&lt;upon entering a war with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon signing a peace treaty with [civFilter] Civilizations&gt;"
	/ en signant un traité de paix avec les civilisations [civFilter]

	Example: "&lt;upon signing a peace treaty with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	/ en signant une déclaration d'amitié

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	/ en signant un pacte de défense

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	/ en entrant dans un Âge d'Or

	Applicable to: TriggerCondition

??? example  "&lt;upon ending a Golden Age&gt;"
	/ en terminant un Âge d'Or

	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	/ en conquérant une ville

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon losing a city&gt;"
	/ en perdant une ville

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a city&gt;"
	/ en fondant une ville

	Applicable to: TriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ en bâtissant un aménagement [improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon discovering a Natural Wonder&gt;"
	/ en découvrant une Merveille Naturelle

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter]&gt;"
	/ en construisant [buildingFilter]

	Example: "&lt;upon constructing [Culture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter] [cityFilter]&gt;"
	/ en construisant [buildingFilter] [cityFilter]

	Example: "&lt;upon constructing [Culture] [in all cities]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon gaining a [baseUnitFilter] unit&gt;"
	/ en obtenant une unité [baseUnitFilter]

	Example: "&lt;upon gaining a [Melee] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon losing a [mapUnitFilter] unit&gt;"
	/ en perdant une unité [mapUnitFilter]

	Example: "&lt;upon losing a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ à la fin d'un tour

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ au début d'un tour

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a Pantheon&gt;"
	/ en fondant un Panthéon

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	/ en fondant une Religion

	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	/ en renforçant une Religion

	Applicable to: TriggerCondition

??? example  "&lt;upon expending a [mapUnitFilter] unit&gt;"
	/ en consommant une unité [mapUnitFilter]

	Example: "&lt;upon expending a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

## UnitTriggerCondition uniques（UnitéDéclencherCondition）
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon conquering a city&gt;"
	/ en conquérant une ville

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ en bâtissant un aménagement [improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ à la fin d'un tour

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ au début d'un tour

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon entering combat&gt;"
	/ en participant à un combat

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon damaging a [mapUnitFilter] unit&gt;"
	/ en infligeant des dégâts à une unité [mapUnitFilter]

	Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'

	Example: "&lt;upon damaging a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	/ en vainquant une unité [mapUnitFilter]

	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	/ en étant vaincu(e)

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	/ en étant promu(e)

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] promotion&gt;"
	/ en obtenant la promotion [promotion]

	Example: "&lt;upon gaining the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] promotion&gt;"
	/ en perdant la promotion [promotion]

	Example: "&lt;upon losing the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] status&gt;"
	/ en obtenant le statut [promotion]

	Example: "&lt;upon gaining the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] status&gt;"
	/ en perdant le statut [promotion]

	Example: "&lt;upon losing the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [positiveAmount] HP in a single attack&gt;"
	/ en perdant au moins [positiveAmount] PV en une seule attaque

	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	/ en terminant un tour sur une case [tileFilter]

	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	/ en découvrant une case [tileFilter]

	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon entering a [tileFilter] tile&gt;"
	/ en entrant sur une case [tileFilter]

	Example: "&lt;upon entering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques（UnitéActionModificateur）
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	/ en consommant cette unité

	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	/ pour [amount] mouvement(s)

	Will consume up to [amount] of Movement to execute

	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;for all movement&gt;"
	/ pour tous les mouvements

	Will consume all Movement to execute

	Applicable to: UnitActionModifier

??? example  "&lt;requires [nonNegativeAmount] movement&gt;"
	/ nécessite [nonNegativeAmount] mouvement(s)

	Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up

	Example: "&lt;requires [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [stats] stats&gt;"
	/ coûte des points de [stats]

	A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock

	Example: "&lt;costs [+1 Gold, +2 Production] stats&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [amount] [stockpiledResource]&gt;"
	/ coûte [amount] [stockpiledResource]

	A positive Integer value will be subtracted from your stock. Do not confuse with "Costs [amount] [stockpiledResource]" (uppercase 'C') for Improvements, Buildings, and Units.

	Example: "&lt;costs [3] [Mana]&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;removing the [promotion] promotion/status&gt;"
	/ supprimant la promotion/statut [promotion]

	Removes the promotion/status from the unit - this is not a cost, units will be able to activate the action even without the promotion/status. To limit, use &lt;with the [promotion] promotion&gt; conditional

	Example: "&lt;removing the [Shock I] promotion/status&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	/ une fois

	Applicable to: UnitActionModifier

??? example  "&lt;[positiveAmount] times&gt;"
	/ [positiveAmount] fois

	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[nonNegativeAmount] additional time(s)&gt;"
	/ [nonNegativeAmount] fois de plus

	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
	/ puis cette unité est consommée

	Applicable to: UnitActionModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

## MetaModifier uniques（MétaModificateur）
!!! note ""

    Modifiers that can be added to other uniques changing user experience, not their behavior

??? example  "&lt;for [nonNegativeAmount] turns&gt;"
	/ pour [nonNegativeAmount] tours

	Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns

	Example: "&lt;for [3] turns&gt;"

	Applicable to: MetaModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

??? example  "&lt;hidden from users&gt;"
	/ Caché aux joueurs

	Applicable to: MetaModifier

??? example  "&lt;for every [countable]&gt;"
	/ pour chaque [countable]

	Works for positive numbers only

	Example: "&lt;for every [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every adjacent [tileFilter]&gt;"
	/ pour chaque case [tileFilter] adjacente

	Works for positive numbers only

	Example: "&lt;for every adjacent [Farm]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every [positiveAmount] [countable]&gt;"
	/ tous les [positiveAmount] [countable]

	Works for positive numbers only

	Example: "&lt;for every [3] [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;(modified by game speed)&gt;"
	/ (modifié par vitesse de jeu)

	Can only be applied to certain uniques, see details of each unique for specifics

	Applicable to: MetaModifier

??? example  "&lt;(modified by game progress up to [relativeAmount]%)&gt;"
	/ (modifié par avancement de la partie jusqu'à [relativeAmount]%) 

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
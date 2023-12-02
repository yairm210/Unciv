# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](../Unique-parameters)

## Triggerable uniques
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[amount] free [unit] units appear"
	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	Applicable to: Triggerable

??? example  "[amount] Free Social Policies"
	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	Applicable to: Triggerable

??? example  "Empire enters a [amount]-turn Golden Age"
	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy]"
	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Free Technology"
	Applicable to: Triggerable

??? example  "[amount] Free Technologies"
	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[amount] free random researchable Tech(s) from the [era]"
	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	Applicable to: Triggerable

??? example  "Instantly consumes [amount] [stockpiledResource]"
	Example: "Instantly consumes [3] [StockpiledResource]"

	Applicable to: Triggerable

??? example  "Instantly provides [amount] [stockpiledResource]"
	Example: "Instantly provides [3] [StockpiledResource]"

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	Example: "Gain [3] [Culture]"

	Applicable to: Triggerable

??? example  "Gain [amount] [stat] (modified by game speed)"
	Example: "Gain [3] [Culture] (modified by game speed)"

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	Applicable to: Triggerable

??? example  "Gain enough Faith for [amount]% of a Great Prophet"
	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius"
	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [amount] cities for free"
	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [amount] cities for free"
	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

## UnitTriggerable uniques
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Heal this unit by [amount] HP"
	Example: "Heal this unit by [3] HP"

	Applicable to: UnitTriggerable

??? example  "This Unit gains [amount] XP"
	Example: "This Unit gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "This Unit upgrades for free"
	Applicable to: UnitTriggerable

??? example  "This Unit upgrades for free including special upgrades"
	Applicable to: UnitTriggerable

??? example  "This Unit gains the [promotion] promotion"
	Example: "This Unit gains the [Shock I] promotion"

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
	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [amount] population [cityFilter]"
	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [amount] social policies adopted"
	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [amount] [civWideStat]"
	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "Military Units gifted from City-States start with [amount] XP"
	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [amount] times as fast when you are at war with a common nation"
	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns."
	Example: "Can spend Gold to annex or puppet a City-State that has been your ally for [3] turns."

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by specialists [cityFilter]"
	Example: "[+20]% Food consumption by specialists [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])"
	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])"
	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]"
	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]"
	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost"
	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost"
	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance cost for buildings [cityFilter]"
	Example: "[+20]% maintenance cost for buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it)"
	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Provides a sum of gold each time you spend a Great Person"
	Applicable to: Global

??? example  "[stats] whenever a Great Person is expended"
	Example: "[+1 Gold, +2 Production] whenever a Great Person is expended"

	Applicable to: Global

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global

??? example  "Great General provides double combat bonus"
	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	Applicable to: Global

??? example  "[amount] Unit Supply"
	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [amount] population [cityFilter]"
	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	Applicable to: Global

??? example  "Enables embarkation for land units"
	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [amount] extra movement points when inside your territory"
	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] Experience [cityFilter]"
	Example: "New [Melee] units start with [3] Experience [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% City Strength from defensive buildings"
	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Quantity of strategic resources produced by the empire +[relativeAmount]%"
	Example: "Quantity of strategic resources produced by the empire +[+20]%"

	Applicable to: Global

??? example  "Double quantity of [resource] produced"
	Example: "Double quantity of [Iron] produced"

	Applicable to: Global

??? example  "Enables Open Borders agreements"
	Applicable to: Global

??? example  "Enables Research agreements"
	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	Applicable to: Global

??? example  "Receive triple Gold from Barbarian encampments and pillaging Cities"
	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time (modified by game speed)"
	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time (modified by game speed)"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "Triggers victory"
	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	Applicable to: Global

??? example  "May not annex cities"
	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "+30% Strength when fighting City-State units and cities"
	Applicable to: Global

??? example  "[amount] additional attacks per turn"
	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain

??? example  "[amount] Range"
	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	Applicable to: Global, Unit

??? example  "Damage is ignored when determining unit Strength"
	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	Applicable to: Global, Unit

??? example  "Defense bonus when embarked"
	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [civWideStat]"
	Example: "Earn [3]% of the damage done to [City] units as [Gold]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [civWideStat] immediately"
	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Gold] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [civWideStat]"
	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Gold]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[amount] Movement point cost to disembark"
	Example: "[3] Movement point cost to disembark"

	Applicable to: Global, Unit

??? example  "[amount] Movement point cost to embark"
	Example: "[3] Movement point cost to embark"

	Applicable to: Global, Unit

## Nation uniques
??? example  "Will not be chosen for new games"
	Applicable to: Nation

??? example  "Starts with [tech]"
	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	Applicable to: Nation

??? example  "Excluded from map editor"
	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	Applicable to: Nation, Tech, Policy, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed

??? example  "Comment [comment]"
	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.
	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed

## Era uniques
??? example  "Starting in this era disables religion"
	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	Applicable to: Era

## Tech uniques
??? example  "Starting tech"
	Applicable to: Tech

??? example  "Can be continually researched"
	Applicable to: Tech

??? example  "Only available"
	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins

??? example  "Cannot be hurried"
	Applicable to: Tech, Building

## FounderBelief uniques
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [amount] global followers [cityFilter]"
	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

## FollowerBelief uniques
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [civWideStat] when killed within 4 tiles of a city following this religion"
	Example: "Earn [3]% of [Wounded] unit's [Cost] as [Gold] when killed within 4 tiles of a city following this religion"

	Applicable to: FollowerBelief

## Building uniques
??? example  "Consumes [amount] [resource]"
	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	Example: "Costs [3] [StockpiledResource]"

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Hidden until [amount] social policy branches have been completed"
	Example: "Hidden until [3] social policy branches have been completed"

	Applicable to: Building, Unit

??? example  "Excess Food converted to Production when under construction"
	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	Example: "Cost increases by [3] per owned city"

	Applicable to: Building

??? example  "Requires a [buildingFilter] in all cities"
	Example: "Requires a [Culture] in all cities"

	Applicable to: Building

??? example  "Requires a [buildingFilter] in at least [amount] cities"
	Example: "Requires a [Culture] in at least [3] cities"

	Applicable to: Building

??? example  "Can only be built [cityFilter]"
	Example: "Can only be built [in all cities]"

	Applicable to: Building

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	Applicable to: Building

??? example  "Destroyed when the city is captured"
	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	Applicable to: Building

??? example  "Doubles Gold given to enemy if city is captured"
	Applicable to: Building

??? example  "Remove extra unhappiness from annexed cities"
	Applicable to: Building

??? example  "Connects trade routes over water"
	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	Example: "Creates a [Trading Post] improvement on a specific tile"

	Applicable to: Building

??? example  "Spaceship part"
	Applicable to: Building, Unit

??? example  "Hidden when religion is disabled"
	Applicable to: Building, Unit, Ruins, Tutorial

??? example  "Hidden when [victoryType] Victory is disabled"
	Example: "Hidden when [Domination] Victory is disabled"

	Applicable to: Building, Unit

## UnitAction uniques
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	Applicable to: UnitAction

??? example  "May found a religion"
	Applicable to: UnitAction

??? example  "May enhance a religion"
	Applicable to: UnitAction

## Unit uniques
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "May create improvements on water resources"
	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	Applicable to: Unit

??? example  "May Paradrop up to [amount] tiles from inside friendly territory"
	Example: "May Paradrop up to [3] tiles from inside friendly territory"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	Applicable to: Unit

??? example  "Can speed up construction of a building"
	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	Applicable to: Unit

??? example  "Can hurry technology research"
	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Can transform to [unit]"
	Example: "Can transform to [Musketman]"

	Applicable to: Unit

??? example  "Automation is a primary action"
	Applicable to: Unit

??? example  "[relativeAmount]% Strength for enemy [combatantFilter] units in adjacent [tileFilter] tiles"
	Example: "[+20]% Strength for enemy [City] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when stacked with [mapUnitFilter]"
	Example: "[+20]% Strength when stacked with [Wounded]"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "Can only attack [combatantFilter] units"
	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	Applicable to: Unit

??? example  "Must set up to ranged attack"
	Applicable to: Unit

??? example  "Self-destructs when attacking"
	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	Applicable to: Unit

??? example  "May attack when embarked"
	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	Applicable to: Unit

??? example  "Blast radius [amount]"
	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	Applicable to: Unit

??? example  "Nuclear weapon of Strength [amount]"
	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "Uncapturable"
	Applicable to: Unit

??? example  "May withdraw before melee ([amount]%)"
	Example: "May withdraw before melee ([3]%)"

	Applicable to: Unit

??? example  "Unable to capture cities"
	Applicable to: Unit

??? example  "Unable to pillage tiles"
	Applicable to: Unit

??? example  "Can move after attacking"
	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	Applicable to: Unit

??? example  "Unit will heal every turn, even if it performs an action"
	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	Applicable to: Unit

??? example  "Can see over obstacles"
	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "May capture killed [mapUnitFilter] units"
	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "Invisible to others"
	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [baseUnitFilter] through ruins-like effects"
	Example: "May upgrade to [Melee] through ruins-like effects"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	Applicable to: Unit

??? example  "Cannot move"
	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	Example: "Double movement in [Fresh Water]"

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	Applicable to: Unit

??? example  "Ignores terrain cost"
	Applicable to: Unit

??? example  "Ignores Zone of Control"
	Applicable to: Unit

??? example  "Rough terrain penalty"
	Applicable to: Unit

??? example  "Can enter ice tiles"
	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Applicable to: Unit

??? example  "Never appears as a Barbarian unit"
	Applicable to: Unit

??? example  "Religious Unit"
	Applicable to: Unit

??? example  "Takes your religion over the one in their birth city"
	Applicable to: Unit

??? example  "Great Person - [comment]"
	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
	Example: "Is part of Great Person group [comment]"

	Applicable to: Unit

## Promotion uniques
??? example  "Doing so will consume this opportunity to choose a Promotion"
	Applicable to: Promotion

??? example  "This Promotion is free"
	Applicable to: Promotion

## Terrain uniques
??? example  "Must be adjacent to [amount] [simpleTerrain] tiles"
	Example: "Must be adjacent to [3] [Elevated] tiles"

	Applicable to: Terrain

??? example  "Must be adjacent to [amount] to [amount] [simpleTerrain] tiles"
	Example: "Must be adjacent to [3] to [3] [Elevated] tiles"

	Applicable to: Terrain

??? example  "Must not be on [amount] largest landmasses"
	Example: "Must not be on [3] largest landmasses"

	Applicable to: Terrain

??? example  "Must be on [amount] largest landmasses"
	Example: "Must be on [3] largest landmasses"

	Applicable to: Terrain

??? example  "Occurs on latitudes from [amount] to [amount] percent of distance equator to pole"
	Example: "Occurs on latitudes from [3] to [3] percent of distance equator to pole"

	Applicable to: Terrain

??? example  "Occurs in groups of [amount] to [amount] tiles"
	Example: "Occurs in groups of [3] to [3] tiles"

	Applicable to: Terrain

??? example  "Neighboring tiles will convert to [baseTerrain]"
	Example: "Neighboring tiles will convert to [Grassland]"

	Applicable to: Terrain

??? example  "Neighboring tiles except [baseTerrain] will convert to [baseTerrain]"
	Example: "Neighboring tiles except [Grassland] will convert to [Grassland]"

	Applicable to: Terrain

??? example  "Grants 500 Gold to the first civilization to discover it"
	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	Example: "Units ending their turn on this terrain take [3] damage"

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time Production bonus to the closest city when cut down"
	Applicable to: Terrain

??? example  "Vegetation"
	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	Example: "Has an elevation of [3] for visibility calculations"

	Applicable to: Terrain

??? example  "Always Fertility [amount] for Map Generation"
	Example: "Always Fertility [3] for Map Generation"

	Applicable to: Terrain

??? example  "[amount] to Fertility for Map Generation"
	Example: "[3] to Fertility for Map Generation"

	Applicable to: Terrain

??? example  "A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]"
	Example: "A Region is formed with at least [3]% [Elevated] tiles, with priority [3]"

	Applicable to: Terrain

??? example  "A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]"
	Example: "A Region is formed with at least [3]% [Elevated] tiles and [Elevated] tiles, with priority [3]"

	Applicable to: Terrain

??? example  "A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles"
	Example: "A Region can not contain more [Elevated] tiles than [Elevated] tiles"

	Applicable to: Terrain

??? example  "Base Terrain on this tile is not counted for Region determination"
	Applicable to: Terrain

??? example  "Starts in regions of this type receive an extra [resource]"
	Example: "Starts in regions of this type receive an extra [Iron]"

	Applicable to: Terrain

??? example  "Never receives any resources"
	Applicable to: Terrain

??? example  "Becomes [terrainName] when adjacent to [terrainFilter]"
	Example: "Becomes [Forest] when adjacent to [Fresh Water]"

	Applicable to: Terrain

??? example  "Considered [terrainQuality] when determining start locations"
	Example: "Considered [Undesirable] when determining start locations"

	Applicable to: Terrain

??? example  "Doesn't generate naturally"
	Applicable to: Terrain, Resource

??? example  "Occurs at temperature between [fraction] and [fraction] and humidity between [fraction] and [fraction]"
	Example: "Occurs at temperature between [0.5] and [0.5] and humidity between [0.5] and [0.5]"

	Applicable to: Terrain, Resource

??? example  "Occurs in chains at high elevations"
	Applicable to: Terrain

??? example  "Occurs in groups around high elevations"
	Applicable to: Terrain

??? example  "Every [amount] tiles with this terrain will receive a major deposit of a strategic resource."
	Example: "Every [3] tiles with this terrain will receive a major deposit of a strategic resource."

	Applicable to: Terrain

??? example  "Rare feature"
	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	Applicable to: Terrain

??? example  "Rough terrain"
	Applicable to: Terrain

## Improvement uniques
??? example  "Can also be built on tiles adjacent to fresh water"
	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	Applicable to: Improvement

??? example  "Does not need removal of [tileFilter]"
	Example: "Does not need removal of [Farm]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	Applicable to: Improvement

??? example  "Constructing it will take over the tiles around it and assign them to your closest city"
	Applicable to: Improvement

??? example  "Unpillagable"
	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Irremovable"
	Applicable to: Improvement

??? example  "Will be replaced by automated workers"
	Applicable to: Improvement

## Resource uniques
??? example  "Deposits in [tileFilter] tiles always provide [amount] resources"
	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	Applicable to: Resource

??? example  "Stockpiled"
	Applicable to: Resource

??? example  "City-level resource"
	Applicable to: Resource

??? example  "Cannot be traded"
	Applicable to: Resource

??? example  "Not shown on world screen"
	Applicable to: Resource

??? example  "Generated with weight [amount]"
	Example: "Generated with weight [3]"

	Applicable to: Resource

??? example  "Minor deposits generated with weight [amount]"
	Example: "Minor deposits generated with weight [3]"

	Applicable to: Resource

??? example  "Generated near City States with weight [amount]"
	Example: "Generated near City States with weight [3]"

	Applicable to: Resource

??? example  "Special placement during map generation"
	Applicable to: Resource

??? example  "Generated on every [amount] tiles"
	Example: "Generated on every [3] tiles"

	Applicable to: Resource

??? example  "Guaranteed with Strategic Balance resource option"
	Applicable to: Resource

## Ruins uniques
??? example  "Free [unit] found in the ruins"
	Example: "Free [Musketman] found in the ruins"

	Applicable to: Ruins

??? example  "From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance"
	Example: "From a randomly chosen tile [3] tiles away from the ruins, reveal tiles up to [3] tiles away with [3]% chance"

	Applicable to: Ruins

??? example  "Hidden after generating a Great Prophet"
	Applicable to: Ruins

## CityState uniques
??? example  "Provides military units every ≈[amount] turns"
	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	Applicable to: CityState

## ModOptions uniques
??? example  "Mod is incompatible with [modFilter]"
	Example: "Mod is incompatible with [DeCiv Redux]"

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	Applicable to: ModOptions

## Conditional uniques
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;for [amount] turns&gt;"
	Example: "&lt;for [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;with [amount]% chance&gt;"
	Example: "&lt;with [3]% chance&gt;"

	Applicable to: Conditional

??? example  "&lt;before [amount] turns&gt;"
	Example: "&lt;before [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;after [amount] turns&gt;"
	Example: "&lt;after [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;for [civFilter]&gt;"
	Example: "&lt;for [City-States]&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	Applicable to: Conditional

??? example  "&lt;when between [amount] and [amount] Happiness&gt;"
	Example: "&lt;when between [3] and [3] Happiness&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [amount] Happiness&gt;"
	Example: "&lt;when below [3] Happiness&gt;"

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	Applicable to: Conditional

??? example  "&lt;after discovering [tech]&gt;"
	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [tech]&gt;"
	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
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

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	Example: "&lt;when above [3] [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
	Example: "&lt;when below [3] [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat] (modified by game speed)&gt;"
	Example: "&lt;when above [3] [Culture] (modified by game speed)&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat] (modified by game speed)&gt;"
	Example: "&lt;when below [3] [Culture] (modified by game speed)&gt;"

	Applicable to: Conditional

??? example  "&lt;in this city&gt;"
	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [amount] [populationFilter]&gt;"
	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] HP&gt;"
	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [amount] HP&gt;"
	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	Applicable to: Conditional

??? example  "&lt;with [amount] to [amount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] [tileFilter] tiles&gt;"
	Example: "&lt;in [Farm] [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [amount] tiles of a [tileFilter]&gt;"
	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	Example: "&lt;in all except [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;hidden from users&gt;"
	Applicable to: Conditional

## TriggerCondition uniques
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [tech]&gt;"
	Example: "&lt;upon discovering [Agriculture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war with a major Civilization&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a city&gt;"
	Applicable to: TriggerCondition

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

??? example  "&lt;upon founding a Pantheon&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	Applicable to: TriggerCondition

## UnitTriggerCondition uniques
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [amount] HP in a single attack&gt;"
	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	Applicable to: UnitActionModifier

??? example  "&lt;[amount] times&gt;"
	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[amount] additional time(s)&gt;"
	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
	Applicable to: UnitActionModifier


*[action]: An action that a unit can perform. Currently, there are only two actions part of this: 'Spread Religion' and 'Remove Foreign religions from your own cities'
*[amount]: This indicates a whole number, possibly with a + or - sign, such as `2`, `+13`, or `-3`.
*[baseTerrain]: The name of any terrain that is a base terrain according to the json file.
*[belief]: The name of any belief.
*[beliefType]: 'Pantheon', 'Follower', 'Founder' or 'Enhancer'
*[buildingName]: The name of any building.
*[civWideStat]: All the following stats have civ-wide fields: `Gold`, `Science`, `Culture`, `Faith`.
*[combatantFilter]: This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`.
*[costOrStrength]: `Cost` or `Strength`.
*[era]: The name of any era.
*[foundingOrEnhancing]: `founding` or `enhancing`.
*[fraction]: Indicates a fractional number, which can be negative.
*[improvementName]: The name of any improvement.
*[modFilter]: A Mod name, case-sensitive _or_ a simple wildcard filter beginning and ending in an Asterisk, case-insensitive.
*[policy]: The name of any policy.
*[promotion]: The name of any promotion.
*[relativeAmount]: This indicates a number, usually with a + or - sign, such as `+25` (this kind of parameter is often followed by '%' which is nevertheless not part of the value).
*[resource]: The name of any resource.
*[specialist]: The name of any specialist.
*[stat]: This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!
*[stats]: For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!
*[stockpiledResource]: The name of any stockpiled.
*[tech]: The name of any tech.
*[tileFilter]: Anything that can be used either in an improvementFilter or in a terrainFilter can be used here, plus 'unimproved'
*[victoryType]: The name of any victory type: 'Neutral', 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time'
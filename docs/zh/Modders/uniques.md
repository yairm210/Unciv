# Uniques
An overview of uniques can be found [here](../Developers/Uniques.md)

Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)

## Triggerable uniques（触发型）
!!! note ""

    Uniques that have immediate, one-time effects. These can be added to techs to trigger when researched, to policies to trigger when adopted, to eras to trigger when reached, to buildings to trigger when built. Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event.They can also be added to units to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Remove [buildingFilter] [cityFilter]"
	/ 移除[cityFilter]城市的[buildingFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ 拆毁[cityFilter]的[buildingFilter](返还少量金钱)

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Free [unit] appears"
	/ 免费的[unit]出现

	Example: "Free [Musketman] appears"

	Applicable to: Triggerable

??? example  "[positiveAmount] free [unit] units appear"
	/ [positiveAmount]个免费的[unit]出现

	Example: "[3] free [Musketman] units appear"

	Applicable to: Triggerable

??? example  "A [unit] rebels"
	/ 一个[unit]反叛

	Example: "A [Musketman] rebels"

	Applicable to: Triggerable

??? example  "[positiveAmount] [unit]s rebel"
	/ [positiveAmount][unit]反叛

	Example: "[3] [Musketman]s rebel"

	Applicable to: Triggerable

??? example  "Free Social Policy"
	/ 推行1项免费社会政策

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Social Policies"
	/ 免费获得[positiveAmount]个社会政策

	Example: "[3] Free Social Policies"

	Applicable to: Triggerable

??? example  "Empire enters golden age"
	/ 帝国进入黄金时代

	Applicable to: Triggerable

??? example  "Empire enters a [positiveAmount]-turn Golden Age"
	/  帝国进入[positiveAmount]回合的黄金时代 

	Example: "Empire enters a [3]-turn Golden Age"

	Applicable to: Triggerable

??? example  "Free Great Person"
	/ 免费的伟人

	Applicable to: Triggerable

??? example  "[amount] population [cityFilter]"
	/ [cityFilter][amount]人口

	Example: "[3] population [in all cities]"

	Applicable to: Triggerable

??? example  "[amount] population in a random city"
	/ 在一个随机城市中增加[amount]人口

	Example: "[3] population in a random city"

	Applicable to: Triggerable

??? example  "Discover [tech]"
	/ 发现[tech]

	Example: "Discover [Agriculture]"

	Applicable to: Triggerable

??? example  "Adopt [policy/belief]"
	/ 立刻推行[policy/belief]

	Example: "Adopt [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter]"
	/ 取消推行[policyFilter]

	Example: "Remove [Oligarchy]"

	Applicable to: Triggerable

??? example  "Remove [policyFilter] and refund [amount]% of its cost"
	/ 取消推行[policyFilter]并返还[amount]%的文化花费

	Example: "Remove [Oligarchy] and refund [3]% of its cost"

	Applicable to: Triggerable

??? example  "Free Technology"
	/ 获得1项免费科技

	Applicable to: Triggerable

??? example  "[positiveAmount] Free Technologies"
	/ 获得[positiveAmount]项免费科技

	Example: "[3] Free Technologies"

	Applicable to: Triggerable

??? example  "[positiveAmount] free random researchable Tech(s) from the [eraFilter]"
	/ 免费获得[eraFilter]的[positiveAmount]个可研究科技

	Example: "[3] free random researchable Tech(s) from the [Ancient era]"

	Applicable to: Triggerable

??? example  "Reveals the entire map"
	/ 揭示全图

	Applicable to: Triggerable

??? example  "Gain a free [beliefType] belief"
	/ 获得免费的[beliefType]信仰

	Example: "Gain a free [Follower] belief"

	Applicable to: Triggerable

??? example  "Triggers voting for the Diplomatic Victory"
	/ 发起外交胜利投票

	Applicable to: Triggerable

??? example  "Instantly consumes [positiveAmount] [stockpiledResource]"
	/ 立即消耗[positiveAmount][stockpiledResource]

	Example: "Instantly consumes [3] [Mana]"

	Applicable to: Triggerable

??? example  "Instantly provides [positiveAmount] [stockpiledResource]"
	/ 立即提供[positiveAmount][stockpiledResource]

	Example: "Instantly provides [3] [Mana]"

	Applicable to: Triggerable

??? example  "Set [stockpile] to [countable]"
	/ 设定[stockpile]为[countable]

	Example: "Set [Mana] to [1000]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Instantly gain [amount] [stockpile]"
	/  立即获得 [amount] [stockpile]

	Example: "Instantly gain [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount] [stat]"
	/ 获得[amount][stat]

	Example: "Gain [3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain [amount]-[amount] [stat]"
	Example: "Gain [3]-[3] [Culture]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Triggerable

??? example  "Gain enough Faith for a Pantheon"
	/ 为万神殿获得足够的信仰

	Applicable to: Triggerable

??? example  "Gain enough Faith for [positiveAmount]% of a Great Prophet"
	/ 获得足够一个大先知的[positiveAmount]%的信仰

	Example: "Gain enough Faith for [3]% of a Great Prophet"

	Applicable to: Triggerable

??? example  "Research [relativeAmount]% of [tech]"
	/ 获得[tech][relativeAmount]%的进度

	Example: "Research [+20]% of [Agriculture]"

	Applicable to: Triggerable

??? example  "Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius"
	/ 获得以[tileFilter]为中心[nonNegativeAmount]格半径的地块

	Example: "Gain control over [Farm] tiles in a [3]-tile radius"

	Applicable to: Triggerable

??? example  "Gain control over [positiveAmount] tiles [cityFilter]"
	/ 获得对[cityFilter][positiveAmount]格领土的控制权

	Example: "Gain control over [3] tiles [in all cities]"

	Applicable to: Triggerable

??? example  "Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius"
	/ 在[positiveAmount]格半径内最多显示[positiveAmount/'all'][tileFilter]

	Example: "Reveal up to [3] [Farm] within a [3] tile radius"

	Applicable to: Triggerable

??? example  "Triggers the following global alert: [comment]"
	/ 触发下列全局警报：[comment]

	Supported on Policies and Technologies.

	For other targets, the generated Notification may not read nicely, and will likely not support translation. Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_, and that difference is currently handled by mapping text.

	Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts.

	Example: "Triggers the following global alert: [comment]"

	Applicable to: Triggerable

??? example  "Promotes all spies [positiveAmount] time(s)"
	/ 令所有间谍升[positiveAmount]级

	Example: "Promotes all spies [3] time(s)"

	Applicable to: Triggerable

??? example  "Gain an extra spy"
	/ 获得1个间谍

	Applicable to: Triggerable

??? example  "Turn this tile into a [terrainName] tile"
	/ 将此地块转变为[terrainName]

	Example: "Turn this tile into a [Forest] tile"

	Applicable to: Triggerable

??? example  "Remove [resourceFilter] resources from this tile"
	/ 从该地块移除[resourceFilter]资源

	Example: "Remove [Strategic] resources from this tile"

	Applicable to: Triggerable

??? example  "Remove [improvementFilter] improvements from this tile"
	/ 从该地块移除[improvementFilter]地块改良

	Example: "Remove [All Road] improvements from this tile"

	Applicable to: Triggerable

??? example  "[mapUnitFilter] units gain the [promotion] promotion"
	Works only with promotions that are valid for the unit's type - or for promotions that do not specify any.

	Example: "[Wounded] units gain the [Shock I] promotion"

	Applicable to: Triggerable

??? example  "Provides the cheapest [stat] building in your first [positiveAmount] cities for free"
	/ 前[positiveAmount]座城市免费获得最便宜的[stat]建筑

	Example: "Provides the cheapest [Culture] building in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Provides a [buildingName] in your first [positiveAmount] cities for free"
	/ 前[positiveAmount]座城市免费获得一座[buildingName]

	Example: "Provides a [Library] in your first [3] cities for free"

	Applicable to: Triggerable

??? example  "Triggers a [event] event"
	/ 触发事件：[event]

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

## UnitTriggerable uniques（单位触发型）
!!! note ""

    Uniques that have immediate, one-time effects on a unit.They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.

??? example  "[unitTriggerTarget] heals [positiveAmount] HP"
	/ [unitTriggerTarget]恢复[positiveAmount]生命值

	Example: "[This Unit] heals [3] HP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] takes [positiveAmount] damage"
	/ [unitTriggerTarget]受到[positiveAmount]伤害

	Example: "[This Unit] takes [3] damage"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [amount] XP"
	/ [unitTriggerTarget]获得[amount]经验值

	Example: "[This Unit] gains [3] XP"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free"
	/ [unitTriggerTarget]免费升级

	Example: "[This Unit] upgrades for free"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] upgrades for free including special upgrades"
	/ [unitTriggerTarget]免费升级(含特色单位升级)

	Example: "[This Unit] upgrades for free including special upgrades"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] promotion"
	/ [unitTriggerTarget]获得[promotion]晋升项

	Example: "[This Unit] gains the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] promotion"
	/ [unitTriggerTarget]失去[promotion]晋升项

	Example: "[This Unit] loses the [Shock I] promotion"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains [positiveAmount] movement"
	/ [unitTriggerTarget]获得[positiveAmount]移动力

	Example: "[This Unit] gains [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses [positiveAmount] movement"
	/ [unitTriggerTarget]失去[positiveAmount]移动力

	Example: "[This Unit] loses [3] movement"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)"
	/ [unitTriggerTarget]获得[promotion]临时晋升项(持续[positiveAmount]回合)

	Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns.

	Example: "[This Unit] gains the [Shock I] status for [3] turn(s)"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] loses the [promotion] status"
	/ [unitTriggerTarget]失去[promotion]临时晋升项

	Example: "[This Unit] loses the [Shock I] status"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] is destroyed"
	/ [unitTriggerTarget]被消灭

	Example: "[This Unit] is destroyed"

	Applicable to: UnitTriggerable

??? example  "[unitTriggerTarget] gets a name from the [unitNameGroup] group"
	/ [unitTriggerTarget]从[unitNameGroup]名称库中获得一个名字

	Example: "[This Unit] gets a name from the [Scientist] group"

	Applicable to: UnitTriggerable

## Global uniques（全球）
!!! note ""

    Uniques that apply globally. Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.

??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[stats] [cityFilter]"
	/ [cityFilter][stats]

	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [cityFilter]中的每个专业人员[stats]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [cityFilter]每[positiveAmount]人口[stats]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] social policies adopted"
	/ 每推行[positiveAmount]个政策[stats]

	Only works for civ-wide stats

	Example: "[+1 Gold, +2 Production] per [3] social policies adopted"

	Applicable to: Global

??? example  "[stats] per every [positiveAmount] [civWideStat]"
	/  每[positiveAmount][civWideStat][stats]

	Example: "[+1 Gold, +2 Production] per every [3] [Gold]"

	Applicable to: Global

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ 坐落于[terrainFilter]的城市[stats]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ 所有[buildingFilter]建筑[stats]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [cityFilter]的[tileFilter]地块[stats]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ 每条贸易路线 [stats]

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	/ [cityFilter][relativeAmount]%[stat]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% [stat] 来自每个 [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]%来自每个[tileFilter/buildingFilter]的产出

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from City-States"
	/ 来自城邦的[stat][relativeAmount]%

	Example: "[+20]% [Culture] from City-States"

	Applicable to: Global

??? example  "[relativeAmount]% [stat] from Trade Routes"
	/ 来自贸易路线的[stat][relativeAmount]%

	Example: "[+20]% [Culture] from Trade Routes"

	Applicable to: Global

??? example  "Nullifies [stat] [cityFilter]"
	/ 消除[cityFilter]的[stat]

	Example: "Nullifies [Culture] [in all cities]"

	Applicable to: Global

??? example  "Nullifies Growth [cityFilter]"
	/ 停止[cityFilter]中的人口增长

	Example: "Nullifies Growth [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [cityFilter] 建造[buildingFilter]建筑 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [cityFilter] 建造[baseUnitFilter]单位 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [cityFilter] 建造[buildingFilter]奇观 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ 当所有在首都已建成的建筑在其他城市建造时 [relativeAmount]% 产能

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ 掠夺地块的收益[relativeAmount]%

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ 掠夺地块的血量恢复[relativeAmount]%

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "Military Units gifted from City-States start with [positiveAmount] XP"
	/ 城邦赠送的军事单位起始拥有[positiveAmount]经验

	Example: "Military Units gifted from City-States start with [3] XP"

	Applicable to: Global

??? example  "Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation"
	/ 与共同的敌人交战时，军事型城邦提供单位的频率为平时的[positiveAmount]倍。

	Example: "Militaristic City-States grant units [3] times as fast when you are at war with a common nation"

	Applicable to: Global

??? example  "Gifts of Gold to City-States generate [relativeAmount]% more Influence"
	/ 赠与城邦金钱提升的影响力+[relativeAmount]%

	Example: "Gifts of Gold to City-States generate [+20]% more Influence"

	Applicable to: Global

??? example  "Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns"
	/ 可以花费金钱来吞并或傀儡一个已经连续[nonNegativeAmount]回合成为你盟友的城邦

	Example: "Can spend Gold to annex or puppet a City-State that has been your Ally for [3] turns"

	Applicable to: Global

??? example  "City-State territory always counts as friendly territory"
	/ 城邦领土始终视为友好领土

	Applicable to: Global

??? example  "Allied City-States will occasionally gift Great People"
	/ 附属城邦随机赠送伟人

	Applicable to: Global

??? example  "[relativeAmount]% City-State Influence degradation"
	/ 对城邦影响减少成度增加[relativeAmount]% 

	Example: "[+20]% City-State Influence degradation"

	Applicable to: Global

??? example  "Resting point for Influence with City-States is increased by [amount]"
	/ 对所有城邦的影响力的基准值[amount]

	Example: "Resting point for Influence with City-States is increased by [3]"

	Applicable to: Global

??? example  "Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves"
	/ 附属城邦提供自身[stat]的[relativeAmount]%

	Example: "Allied City-States provide [Culture] equal to [+20]% of what they produce for themselves"

	Applicable to: Global

??? example  "[relativeAmount]% resources gifted by City-States"
	/ [relativeAmount]%城邦赠送的资源

	Example: "[+20]% resources gifted by City-States"

	Applicable to: Global

??? example  "[relativeAmount]% Happiness from luxury resources gifted by City-States"
	/ [relativeAmount]% 城邦赠送的奢侈资源带来的快乐

	Example: "[+20]% Happiness from luxury resources gifted by City-States"

	Applicable to: Global

??? example  "City-State Influence recovers at twice the normal rate"
	/ 对城邦的影响力恢复速度是正常水平的两倍

	Applicable to: Global

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [cityFilter][relativeAmount]%人口增长

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [cityFilter]在人口增长后人口增长所需食物减少[amount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [cityFilter]中[populationFilter]消耗的食物[relativeAmount]%

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% unhappiness from the number of cities"
	/ 城市数量造成的不快乐[relativeAmount]%

	Example: "[+20]% unhappiness from the number of cities"

	Applicable to: Global

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [cityFilter][populationFilter]造成的不快乐[relativeAmount]%

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount] Happiness from each type of luxury resource"
	/ [amount]幸福度来源于每一种奢侈品资源

	Example: "[3] Happiness from each type of luxury resource"

	Applicable to: Global

??? example  "Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away"
	/ 每种奢侈资源即使全部交易出去仍可保留 [relativeAmount]% 快乐奖励

	Example: "Retain [+20]% of the happiness from a luxury after the last copy has been traded away"

	Applicable to: Global

??? example  "[relativeAmount]% of excess happiness converted to [stat]"
	/ [relativeAmount]%富余的快乐转化为[stat]

	Example: "[+20]% of excess happiness converted to [Culture]"

	Applicable to: Global

??? example  "Cannot build [baseUnitFilter] units"
	/ 无法建造[baseUnitFilter]单位

	Example: "Cannot build [Melee] units"

	Applicable to: Global

??? example  "Enables construction of Spaceship parts"
	/ 允许建造太空飞船的部件

	Applicable to: Global

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ [cityFilter]可以以不断上涨的价格([nonNegativeAmount])用[amount][stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ [cityFilter]可以以不断上涨的价格([nonNegativeAmount])用[amount][stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ [cityFilter]可以用[nonNegativeAmount][stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ [cityFilter]中可以用[nonNegativeAmount][stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ [cityFilter]可以用[stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ [cityFilter]可以用[stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ 可以用[stat]购买[baseUnitFilter]单位，价格为其正常生产成本的[nonNegativeAmount]倍

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ 可以用[nonNegativeAmount]倍于其正常的生产花费的[stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ 在城市建造/组建单位时的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ 购买[buildingFilter]建筑的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [baseUnitFilter]单位的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "Enables conversion of city production to [civWideStat]"
	/ 允许在城市中将产能转化成[civWideStat]

	Example: "Enables conversion of city production to [Gold]"

	Applicable to: Global

??? example  "Production to [civWideStat] conversion in cities changed by [relativeAmount]%"
	Example: "Production to [Gold] conversion in cities changed by [+20]%"

	Applicable to: Global

??? example  "Improves movement speed on roads"
	/ 提高道路上的移动速度

	Applicable to: Global

??? example  "Roads connect tiles across rivers"
	/ 道路/铁路可跨河建造

	Applicable to: Global

??? example  "[relativeAmount]% maintenance on road & railroads"
	/ 道路/铁路维护费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance on road & railroads"

	Applicable to: Global

??? example  "No Maintenance costs for improvements in [tileFilter] tiles"
	/ 在[tileFilter]地块的改良无维修费用

	Example: "No Maintenance costs for improvements in [Farm] tiles"

	Applicable to: Global

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ 对 [improvementFilter] 地块改良 [relativeAmount]% 的建造时间

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ 可以以[relativeAmount]%的速度建造[improvementFilter]地块改进改良。

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "Gain a free [buildingName] [cityFilter]"
	Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building

	Example: "Gain a free [Library] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [cityFilter]中[buildingFilter]建筑维护费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Remove [buildingFilter] [cityFilter]"
	/ 移除[cityFilter]城市的[buildingFilter]

	Example: "Remove [Culture] [in all cities]"

	Applicable to: Triggerable, Global

??? example  "Sell [buildingFilter] buildings [cityFilter]"
	/ 拆毁[cityFilter]的[buildingFilter](返还少量金钱)

	Example: "Sell [Culture] buildings [in all cities]"

	Applicable to: Triggerable, Global

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [cityFilter]自然扩张文化需求[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [cityFilter]购买地块金钱需求[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Each city founded increases culture cost of policies [relativeAmount]% less than normal"
	/ 建立新城市引起的政策文化费用增幅-[relativeAmount]%

	Example: "Each city founded increases culture cost of policies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Culture cost of adopting new Policies"
	/ 推行新政策的文化花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of adopting new Policies"

	Applicable to: Global

??? example  "Each city founded increases Science cost of Technologies [relativeAmount]% less than normal"
	/ 建立新城市引起的科学研究费用增幅-[relativeAmount]%

	Example: "Each city founded increases Science cost of Technologies [+20]% less than normal"

	Applicable to: Global

??? example  "[relativeAmount]% Science cost of researching new Technologies"
	/ 研究新科技的科技花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Science cost of researching new Technologies"

	Applicable to: Global

??? example  "[stats] for every known Natural Wonder"
	/ 每发现一个自然奇观就[stats]

	Example: "[+1 Gold, +2 Production] for every known Natural Wonder"

	Applicable to: Global

??? example  "[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)"
	Example: "[+1 Gold, +2 Production] for discovering a Natural Wonder (bonus enhanced to [+1 Gold, +2 Production] if first to discover it)"

	Applicable to: Global

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [cityFilter]伟人点数生成速度[relativeAmount]%

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]%来源于大商人贸易任务的金钱

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ 大军事家提供双倍战斗力加成

	Applicable to: Global, Unit

??? example  "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."
	/ 研究[tech]后，每[comment]结束时(每394年)得到一个免费的伟人，每种伟人只能选择一次。

	Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

	Applicable to: Global

??? example  "Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count."
	/ 一旦长历法激活，世界屏幕上的年份将显示为传统的玛雅长计数。

	Applicable to: Global

??? example  "[amount] Unit Supply"
	/ [amount]单位补给

	Example: "[3] Unit Supply"

	Applicable to: Global

??? example  "[amount] Unit Supply per [positiveAmount] population [cityFilter]"
	/ [cityFilter]毎[positiveAmount]人口[amount]单位补给

	Example: "[3] Unit Supply per [3] population [in all cities]"

	Applicable to: Global

??? example  "[amount] Unit Supply per city"
	/ 每座城市[amount]单位补给

	Example: "[3] Unit Supply per city"

	Applicable to: Global

??? example  "[amount] units cost no maintenance"
	/ 免除[amount]个单位的维护费

	Example: "[3] units cost no maintenance"

	Applicable to: Global

??? example  "Units in cities cost no Maintenance"
	/ 免除市中心驻军的维护费

	Applicable to: Global

??? example  "Enables embarkation for land units"
	/ 陆军单位拥有船运能力

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enables [mapUnitFilter] units to enter ocean tiles"
	/ 允许[mapUnitFilter]单位进入海洋

	Example: "Enables [Wounded] units to enter ocean tiles"

	Applicable to: Global

??? example  "Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned"
	/ 获得首个[baseUnitFilter]以后，陆地单位能穿过[terrainName]地块

	Example: "Land units may cross [Forest] tiles after the first [Melee] is earned"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory"
	/ 敌方的[mapUnitFilter]必须在你的土地上花费额外的[positiveAmount]点行动力

	Example: "Enemy [Wounded] units must spend [3] extra movement points when inside your territory"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ [cityFilter]新训练的[baseUnitFilter]单位初始拥有[amount]XP

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ [cityFilter]新训练的[baseUnitFilter]单位初始拥有[promotion]晋升

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ 毗邻城市中心的[mapUnitFilter]单位自愈时额外[amount]HP

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% XP required for promotions"
	/ 晋升所需XP[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP required for promotions"

	Applicable to: Global

??? example  "[relativeAmount]% City Strength from defensive buildings"
	/ 建筑提供的防御力[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% City Strength from defensive buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength for cities"
	/ 城市战斗力[relativeAmount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ 获得[amount]单位[resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% [resourceFilter] resource production"
	/ [resourceFilter]资源产量[relativeAmount]% 

	Example: "[+20]% [Strategic] resource production"

	Applicable to: Global

??? example  "Enables establishment of embassies"
	/ 启用大使馆功能

	Applicable to: Global

??? example  "Requires establishing embassies to conduct advanced diplomacy"
	/ 需先建立大使馆才能开展高级外交

	Applicable to: Global

??? example  "Enables Open Borders agreements"
	/ 允许开放边境

	Applicable to: Global

??? example  "Enables Research agreements"
	/ 允许签订科研协定

	Applicable to: Global

??? example  "Science gained from research agreements [relativeAmount]%"
	/ 从科研协定中获得的科技值[relativeAmount]%

	Example: "Science gained from research agreements [+20]%"

	Applicable to: Global

??? example  "Enables Defensive Pacts"
	/ 允许签订共同防御条约

	Applicable to: Global

??? example  "When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation"
	/ 当宣布友谊宣言时，双方都获得[relativeAmount]%的伟人产生速率。

	Example: "When declaring friendship, both parties gain a [+20]% boost to great person generation"

	Applicable to: Global

??? example  "Influence of all other civilizations with all city-states degrades [relativeAmount]% faster"
	/ 其他文明对城邦的影响力下降速度+[relativeAmount]%

	Example: "Influence of all other civilizations with all city-states degrades [+20]% faster"

	Applicable to: Global

??? example  "Gain [amount] Influence with a [baseUnitFilter] gift to a City-State"
	/ 向城邦送予[baseUnitFilter]礼物时[amount]影响力

	Example: "Gain [3] Influence with a [Melee] gift to a City-State"

	Applicable to: Global

??? example  "Resting point for Influence with City-States following this religion [amount]"
	/ 对信奉该宗教的城邦影响力点数[amount]

	Example: "Resting point for Influence with City-States following this religion [3]"

	Applicable to: Global

??? example  "Notified of new Barbarian encampments"
	/ 标记新蛮族营地的位置

	Applicable to: Global

??? example  "Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities"
	/ 从掠夺城市和摧毁蛮族营地中获得[relativeAmount]%金钱

	Example: "Receive [+20]% Gold from Barbarian encampments and pillaging Cities"

	Applicable to: Global

??? example  "When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit"
	/ 摧毁蛮族营地时俘获一个蛮族并获得[amount]金钱

	Example: "When conquering an encampment, earn [3] Gold and recruit a Barbarian unit"

	Applicable to: Global

??? example  "When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it"
	/ 击败[mapUnitFilter]单位时获得[amount]金钱并将其俘获

	Example: "When defeating a [Wounded] unit, earn [3] Gold and recruit it"

	Applicable to: Global

??? example  "May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion"
	/ 当[foundingOrEnhancing]一个宗教时，可以额外选择[amount]个[beliefType]信条

	Example: "May choose [3] additional [Follower] beliefs when [founding] a religion"

	Applicable to: Global

??? example  "May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion"
	/ 当[foundingOrEnhancing]一个宗教时，可以选择[amount]个任意类型的额外信条

	Example: "May choose [3] additional belief(s) of any type when [founding] a religion"

	Applicable to: Global

??? example  "[stats] when a city adopts this religion for the first time"
	/ 城市首次接受宗教的时间[stats]

	Example: "[+1 Gold, +2 Production] when a city adopts this religion for the first time"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Global

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [cityFilter]宗教自然传播[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ 宗教自然传播距离扩大[amount]地块

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "May not generate great prophet equivalents naturally"
	/ 不能自然产生大先知

	Applicable to: Global

??? example  "[relativeAmount]% Faith cost of generating Great Prophet equivalents"
	/ 获得大先知必须的信仰[relativeAmount]%

	Example: "[+20]% Faith cost of generating Great Prophet equivalents"

	Applicable to: Global

??? example  "[relativeAmount]% spy effectiveness [cityFilter]"
	/ [cityFilter]的我方间谍工作效率[relativeAmount]%

	Example: "[+20]% spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "[relativeAmount]% enemy spy effectiveness [cityFilter]"
	/  [cityFilter]的敌方间谍工作效率[relativeAmount]%

	Example: "[+20]% enemy spy effectiveness [in all cities]"

	Applicable to: Global

??? example  "New spies start with [amount] level(s)"
	/ 新间谍等级提高为[amount]级

	Example: "New spies start with [3] level(s)"

	Applicable to: Global

??? example  "Triggers victory"
	/ 触发胜利

	Applicable to: Global

??? example  "Triggers a Cultural Victory upon completion"
	/ 完成后触发文化胜利

	Applicable to: Global

??? example  "May buy items in puppet cities"
	/ 可以在傀儡城市中购买建筑和单位

	Applicable to: Global

??? example  "May not annex cities"
	/ 可能不会吞并城市

	Applicable to: Global

??? example  ""Borrows" city names from other civilizations in the game"
	/ 从其他文明处借用城市名

	Applicable to: Global

??? example  "Cities are razed [amount] times as fast"
	/ [amount] 倍城市拆除速度

	Example: "Cities are razed [3] times as fast"

	Applicable to: Global

??? example  "Receive a tech boost when scientific buildings/wonders are built in capital"
	/ 每当在首都建成科研建筑 / 奇观时立刻获得一次科研点数奖励

	Applicable to: Global

??? example  "[relativeAmount]% Golden Age length"
	/ 黄金时代长度[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Golden Age length"

	Applicable to: Global

??? example  "Population loss from nuclear attacks [relativeAmount]% [cityFilter]"
	/ [cityFilter]核武器造成的人口损失[relativeAmount]%

	Example: "Population loss from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]"
	/ [cityFilter]核武器造成的防御损失[relativeAmount]%

	Example: "Damage to garrison from nuclear attacks [+20]% [in all cities]"

	Applicable to: Global

??? example  "Rebel units may spawn"
	/ 可能会出现反叛单位

	Applicable to: Global

??? example  "Cannot build [buildingFilter] buildings"
	Example: "Cannot build [Culture] buildings"

	Applicable to: Global

??? example  "[relativeAmount]% Strength"
	/ 战斗力[relativeAmount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ 战斗力[relativeAmount]

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ 战斗力加成随与首都的距离减小而增大,最高为[relativeAmount]%

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ 侧翼攻击加成[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[amount] additional attacks per turn"
	/ 在每回合可以额外攻击[amount]次

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ 移动力[amount]

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ 视野[amount]

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ 射程[amount]

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ 航空器拦截范围[relativeAmount]

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ 恢复时额外恢复[amount]点生命值

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ 宗教传播力量[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ 将新城传教成功时获得此城其他宗教教徒数量[amount]倍的[stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ 远程攻击能越过障碍

	Applicable to: Global, Unit

??? example  "No defensive terrain bonus"
	/ 不受正面防御地形的影响

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ 不受负面防御地形的影响

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ 受伤单位免受伤害惩罚

	Applicable to: Global, Unit

??? example  "Unable to capture cities"
	/ 不能攻陷城市

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ 不能劫掠地块

	Applicable to: Global, Unit

??? example  "No movement cost to pillage"
	/ 劫掠不消耗移动力

	Applicable to: Global, Unit

??? example  "May heal outside of friendly territory"
	/ 可以在友好领土之外自愈

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ 所有生命回复效果翻倍

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ 消灭敌方单位时恢复[amount]生命值

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ 只能通过劫掠恢复生命值

	Applicable to: Global, Unit

??? example  "[relativeAmount]% maintenance costs"
	/ 维护费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ 升级单位的金钱花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ 对[combatantFilter]单位造成时获得伤害[amount]%的[stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ 攻陷城市时获得它[stat]产出[amount]倍的[stockpile]

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ 击杀敌方[mapUnitFilter]单位时获得[stockpile](≈已击杀单位的[costOrStrength]×[amount]%)

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "[amount] XP gained from combat"
	/ 从每次战斗中额外获得[amount]XP

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ 从战斗中获得的经验[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson]的招募速率[relativeAmount]%

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ 登陆时消耗[nonNegativeAmount]移动力

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ 下水时消耗[nonNegativeAmount]行动力

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

## Nation uniques（国家）
??? example  "Starts with [tech]"
	/ 初始即拥有科技：[tech]

	Example: "Starts with [Agriculture]"

	Applicable to: Nation

??? example  "Starts with [policy] adopted"
	/ 游戏开始时就推行[policy]

	Example: "Starts with [Oligarchy] adopted"

	Applicable to: Nation

??? example  "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel."
	/ 单位在己方森林和丛林地块上移动时视同在道路上移动，此类地块在研究轮子科技后可建立城市连接

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Units ignore terrain costs when moving into any tile with Hills"
	/ 进行移动力损耗计算时忽略丘陵的影响

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Nation

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Will not be chosen for new games"
	/ 不能在新游戏中选择

	Applicable to: Nation

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Personality uniques（个性）
??? example  "Will not build [baseUnitFilter/buildingFilter]"
	/ 禁止建造[baseUnitFilter/buildingFilter]

	Example: "Will not build [Melee]"

	Applicable to: Personality

## Era uniques（时代）
??? example  "Starting in this era disables religion"
	/ 在此时代开始游戏将会禁用宗教

	Applicable to: Era

??? example  "Every major Civilization gains a spy once a civilization enters this era"
	/ 当第一个文明进入此时代后，每个文明(城邦除外)都会获得一个间谍

	Applicable to: Era

## Tech uniques（科技）
??? example  "Starting tech"
	/ 初始科技

	Applicable to: Tech

??? example  "Can be continually researched"
	/ 可以持续研究

	Applicable to: Tech

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Cannot be hurried"
	/ 不可加速建造

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

## Policy uniques（政策）
??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

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

## FounderBelief uniques（创始人信仰）
!!! note ""

    Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion

??? example  "[stats] for each global city following this religion"
	/ 所有信教的城市[stats]

	Example: "[+1 Gold, +2 Production] for each global city following this religion"

	Applicable to: FounderBelief

??? example  "[stats] from every [positiveAmount] global followers [cityFilter]"
	/ [cityFilter]中每拥有[positiveAmount]单位全球教徒每回合[stats]

	Example: "[+1 Gold, +2 Production] from every [3] global followers [in all cities]"

	Applicable to: FounderBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

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

## FollowerBelief uniques（追随者信仰）
!!! note ""

    Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion

??? example  "[stats] [cityFilter]"
	/ [cityFilter][stats]

	Example: "[+1 Gold, +2 Production] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every specialist [cityFilter]"
	/ [cityFilter]中的每个专业人员[stats]

	Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] per [positiveAmount] population [cityFilter]"
	/ [cityFilter]每[positiveAmount]人口[stats]

	Example: "[+1 Gold, +2 Production] per [3] population [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] in cities on [terrainFilter] tiles"
	/ 坐落于[terrainFilter]的城市[stats]

	Example: "[+1 Gold, +2 Production] in cities on [Fresh Water] tiles"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from all [buildingFilter] buildings"
	/ 所有[buildingFilter]建筑[stats]

	Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles [cityFilter]"
	/ [cityFilter]的[tileFilter]地块[stats]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"
	Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from every [tileFilter/specialist/buildingFilter]"
	Example: "[+1 Gold, +2 Production] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[stats] from each Trade Route"
	/ 每条贸易路线 [stats]

	Example: "[+1 Gold, +2 Production] from each Trade Route"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat]"
	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] [cityFilter]"
	/ [cityFilter][relativeAmount]%[stat]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every [tileFilter/buildingFilter]"
	/ [relativeAmount]% [stat] 来自每个 [tileFilter/buildingFilter]

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% [Culture] from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Yield from every [tileFilter/buildingFilter]"
	/ [relativeAmount]%来自每个[tileFilter/buildingFilter]的产出

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Yield from every [Farm]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% [stat] from every follower, up to [relativeAmount]%"
	Example: "[+20]% [Culture] from every follower, up to [+20]%"

	Applicable to: FounderBelief, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]"
	/ [cityFilter] 建造[buildingFilter]建筑 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]"
	/ [cityFilter] 建造[baseUnitFilter]单位 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Melee] units [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]"
	/ [cityFilter] 建造[buildingFilter]奇观 时 [relativeAmount]%产能

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Production when constructing [Culture] wonders [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Production towards any buildings that already exist in the Capital"
	/ 当所有在首都已建成的建筑在其他城市建造时 [relativeAmount]% 产能

	Example: "[+20]% Production towards any buildings that already exist in the Capital"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% growth [cityFilter]"
	/ [cityFilter][relativeAmount]%人口增长

	Example: "[+20]% growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[amount]% Food is carried over after population increases [cityFilter]"
	/ [cityFilter]在人口增长后人口增长所需食物减少[amount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[3]% Food is carried over after population increases [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Food consumption by [populationFilter] [cityFilter]"
	/ [cityFilter]中[populationFilter]消耗的食物[relativeAmount]%

	Example: "[+20]% Food consumption by [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]"
	/ [cityFilter][populationFilter]造成的不快乐[relativeAmount]%

	Example: "[+20]% Unhappiness from [Followers of this Religion] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ [cityFilter]可以以不断上涨的价格([nonNegativeAmount])用[amount][stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])"
	/ [cityFilter]可以以不断上涨的价格([nonNegativeAmount])用[amount][stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities] at an increasing price ([3])"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]"
	/ [cityFilter]可以用[nonNegativeAmount][stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]"
	/ [cityFilter]中可以用[nonNegativeAmount][stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings for [3] [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] [cityFilter]"
	/ [cityFilter]可以用[stat]购买[baseUnitFilter]单位

	Example: "May buy [Melee] units with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] [cityFilter]"
	/ [cityFilter]可以用[stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings with [Culture] [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ 可以用[stat]购买[baseUnitFilter]单位，价格为其正常生产成本的[nonNegativeAmount]倍

	Example: "May buy [Melee] units with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost"
	/ 可以用[nonNegativeAmount]倍于其正常的生产花费的[stat]购买[buildingFilter]建筑

	Example: "May buy [Culture] buildings with [Culture] for [3] times their normal Production cost"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing items in cities [relativeAmount]%"
	/ 在城市建造/组建单位时的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing items in cities [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%"
	/ 购买[buildingFilter]建筑的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Culture] buildings [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%"
	/ [baseUnitFilter]单位的[stat]花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[Culture] cost of purchasing [Melee] units [+20]%"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]"
	/ [cityFilter]中[buildingFilter]建筑维护费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance cost for [Culture] buildings [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Culture cost of natural border growth [cityFilter]"
	/ [cityFilter]自然扩张文化需求[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Culture cost of natural border growth [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Gold cost of acquiring tiles [cityFilter]"
	/ [cityFilter]购买地块金钱需求[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of acquiring tiles [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Great Person generation [cityFilter]"
	/ [cityFilter]伟人点数生成速度[relativeAmount]%

	Example: "[+20]% Great Person generation [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "New [baseUnitFilter] units start with [amount] XP [cityFilter]"
	/ [cityFilter]新训练的[baseUnitFilter]单位初始拥有[amount]XP

	Example: "New [Melee] units start with [3] XP [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion"
	/ [cityFilter]新训练的[baseUnitFilter]单位初始拥有[promotion]晋升

	Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

	Applicable to: Global, FollowerBelief

??? example  "[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing"
	/ 毗邻城市中心的[mapUnitFilter]单位自愈时额外[amount]HP

	Example: "[Wounded] Units adjacent to this city heal [3] HP per turn when healing"

	Applicable to: Global, FollowerBelief

??? example  "[relativeAmount]% Strength for cities"
	/ 城市战斗力[relativeAmount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength for cities"

	Applicable to: Global, FollowerBelief

??? example  "Provides [amount] [resource]"
	/ 获得[amount]单位[resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "[relativeAmount]% Natural religion spread [cityFilter]"
	/ [cityFilter]宗教自然传播[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Natural religion spread [in all cities]"

	Applicable to: Global, FollowerBelief

??? example  "Religion naturally spreads to cities [amount] tiles away"
	/ 宗教自然传播距离扩大[amount]地块

	Example: "Religion naturally spreads to cities [3] tiles away"

	Applicable to: Global, FollowerBelief

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion"
	/ 当[mapUnitFilter]单位中的[costOrStrength]在信仰该宗教的城市中4格范围内被击杀时获得其[stockpile]的[amount]%

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

## Building uniques（建筑）
??? example  "[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]"
	/ 此城市中来自每个 [improvementFilter/buildingFilter] 中 [positiveAmount]% 的 [stat] 增添到 [resource] 中

	Example: "[3]% of [Culture] from every [All Road] in the city added to [Iron]"

	Applicable to: Building

??? example  "Consumes [amount] [resource]"
	/ 消耗[amount]单位[resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ 消耗[amount][stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ 不可建造或训练

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ 不可购买获得

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ [cityFilter]可以使用[stat]购买

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ [cityFilter]可以用[amount][stat]购买

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ 每个文明限于[amount]个

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ 组建过程中富余的食物将转化为产能

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ 至少需要[amount]人口

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ 在建造开始时触发全局警报

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ 完成后触发全局警报

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ 每座我方城市使建造时花费增加[amount]

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ 每次重复建造使再次建造时花费增加[amount]

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ 造价[amount]%

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ 只能建造

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "Must have an owned [tileFilter] within [amount] tiles"
	/ (城市)[amount]格内的己方地块必须有[tileFilter]

	Example: "Must have an owned [Farm] within [3] tiles"

	Applicable to: Building

??? example  "Enables nuclear weapon"
	/ 允许建造核武器

	Applicable to: Building

??? example  "Must be on [tileFilter]"
	Example: "Must be on [Farm]"

	Applicable to: Building

??? example  "Must not be on [tileFilter]"
	/ (城市)不能位于[tileFilter]

	Example: "Must not be on [Farm]"

	Applicable to: Building

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Must not be next to [tileFilter]"
	/ (城市)不能建立在[tileFilter]旁边

	Example: "Must not be next to [Farm]"

	Applicable to: Building

??? example  "Unsellable"
	/ 不可售卖

	Applicable to: Building

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "Indicates the capital city"
	/ 标记此城市为首都

	Applicable to: Building

??? example  "Moves to new capital when capital changes"
	/ 首都更迭时迁都

	Applicable to: Building

??? example  "Provides 1 extra copy of each improved luxury resource near this City"
	/ 此城开发的奢侈资源数量+1

	Applicable to: Building

??? example  "Destroyed when the city is captured"
	/ 城市沦陷时摧毁

	Applicable to: Building

??? example  "Never destroyed when the city is captured"
	/ 城市沦陷时不会摧毁

	Applicable to: Building

??? example  "[relativeAmount]% Gold given to enemy if city is captured"
	/ 该城市被敌人占领时，敌人额外获得[relativeAmount]%金钱。

	Example: "[+20]% Gold given to enemy if city is captured"

	Applicable to: Building

??? example  "Removes extra unhappiness from annexed cities"
	/ 消除吞并城市带来的额外不满

	Applicable to: Building

??? example  "Connects trade routes over water"
	/ 通过海路建立贸易路线

	Applicable to: Building

??? example  "Automatically built in all cities where it is buildable"
	/ 在所有可能的城市中自动建造

	Applicable to: Building

??? example  "Creates a [improvementName] improvement on a specific tile"
	/ 可以在一个特定的地块上建造[improvementName]

	When choosing to construct this building, the player must select a tile where the improvement can be built. Upon building completion, the tile will gain this improvement. Limited to one per building.

	Example: "Creates a [Trading Post] improvement on a specific tile"

	This unique does not support conditionals.

	Applicable to: Building

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ 可装载[amount]个额外的[mapUnitFilter]单位

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Spaceship part"
	/ 太空飞船部件

	Applicable to: Building, Unit

??? example  "Cannot be hurried"
	/ 不可加速建造

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

## UnitAction uniques（单位行动）
!!! note ""

    Uniques that affect a unit's actions, and can be modified by UnitActionModifiers

??? example  "Founds a new city"
	/ 建立新城市

	Applicable to: UnitAction

??? example  "Founds a new puppet city"
	/ 建立新傀儡城市

	Applicable to: UnitAction

??? example  "Can instantly construct a [improvementFilter] improvement"
	/ 可立即建造[improvementFilter]地块改良

	Example: "Can instantly construct a [All Road] improvement"

	Applicable to: UnitAction

??? example  "Can Spread Religion"
	/ 可以传播宗教

	Applicable to: UnitAction

??? example  "Can remove other religions from cities"
	/ 可以驱除城市异教

	Applicable to: UnitAction

??? example  "May found a religion"
	/ 可创建一个宗教

	Applicable to: UnitAction

??? example  "May enhance a religion"
	/ 可增强一个宗教

	Applicable to: UnitAction

??? example  "Can transform to [unit]"
	/ 可以转换为[unit]

	By default consumes all movement

	Example: "Can transform to [Musketman]"

	Applicable to: UnitAction

## Unit uniques（单位）
!!! note ""

    Uniques that can be added to units, unit types, or promotions

??? example  "[relativeAmount]% Yield from pillaging tiles"
	/ 掠夺地块的收益[relativeAmount]%

	Example: "[+20]% Yield from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Health from pillaging tiles"
	/ 掠夺地块的血量恢复[relativeAmount]%

	Example: "[+20]% Health from pillaging tiles"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% construction time for [improvementFilter] improvements"
	/ 对 [improvementFilter] 地块改良 [relativeAmount]% 的建造时间

	Example: "[+20]% construction time for [All Road] improvements"

	Applicable to: Global, Unit

??? example  "Can build [improvementFilter] improvements at a [relativeAmount]% rate"
	/ 可以以[relativeAmount]%的速度建造[improvementFilter]地块改进改良。

	Example: "Can build [All Road] improvements at a [+20]% rate"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold from Great Merchant trade missions"
	/ [relativeAmount]%来源于大商人贸易任务的金钱

	Example: "[+20]% Gold from Great Merchant trade missions"

	Applicable to: Global, Unit

??? example  "Great General provides double combat bonus"
	/ 大军事家提供双倍战斗力加成

	Applicable to: Global, Unit

??? example  "Consumes [amount] [resource]"
	/ 消耗[amount]单位[resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ 消耗[amount][stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ 不可建造或训练

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Cannot be purchased"
	/ 不可购买获得

	Applicable to: Building, Unit

??? example  "Can be purchased with [stat] [cityFilter]"
	/ [cityFilter]可以使用[stat]购买

	Example: "Can be purchased with [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Can be purchased for [amount] [stat] [cityFilter]"
	/ [cityFilter]可以用[amount][stat]购买

	Example: "Can be purchased for [3] [Culture] [in all cities]"

	Applicable to: Building, Unit

??? example  "Limited to [amount] per Civilization"
	/ 每个文明限于[amount]个

	Example: "Limited to [3] per Civilization"

	Applicable to: Building, Unit

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Excess Food converted to Production when under construction"
	/ 组建过程中富余的食物将转化为产能

	Applicable to: Building, Unit

??? example  "Requires at least [amount] population"
	/ 至少需要[amount]人口

	Example: "Requires at least [3] population"

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon build start"
	/ 在建造开始时触发全局警报

	Applicable to: Building, Unit

??? example  "Triggers a global alert upon completion"
	/ 完成后触发全局警报

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] per owned city"
	/ 每座我方城市使建造时花费增加[amount]

	Example: "Cost increases by [3] per owned city"

	Applicable to: Building, Unit

??? example  "Cost increases by [amount] when built"
	/ 每次重复建造使再次建造时花费增加[amount]

	Example: "Cost increases by [3] when built"

	Applicable to: Building, Unit

??? example  "[amount]% production cost"
	/ 造价[amount]%

	Intended to be used with conditionals to dynamically alter construction costs. Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[3]% production cost"

	Applicable to: Building, Unit

??? example  "Can only be built"
	/ 只能建造

	Meant to be used together with conditionals, like "Can only be built &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable.

	Applicable to: Building, Unit

??? example  "May create improvements on water resources"
	/ 可在水上资源建造改良

	Applicable to: Unit

??? example  "Can build [improvementFilter/terrainFilter] improvements on tiles"
	/ 可以建造地块改良：[improvementFilter/terrainFilter]

	Example: "Can build [All Road] improvements on tiles"

	Applicable to: Unit

??? example  "Can be added to [comment] in the Capital"
	/ 可以被加入在首都的[comment]

	Example: "Can be added to [comment] in the Capital"

	Applicable to: Unit

??? example  "Prevents spreading of religion to the city it is next to"
	/ 阻止紧邻城市的宗教传播

	Applicable to: Unit

??? example  "Removes other religions when spreading religion"
	/  传播宗教时消除异端

	Applicable to: Unit

??? example  "May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away"
	/ 可向[tileFilter]区域空投，最远[positiveAmount]格

	Example: "May Paradrop to [Farm] tiles up to [3] tiles away"

	Applicable to: Unit

??? example  "Can perform Air Sweep"
	/ 可以进行空中扫荡

	Applicable to: Unit

??? example  "Can speed up construction of a building"
	/ 可以加速建筑物的建造

	Applicable to: Unit

??? example  "Can speed up the construction of a wonder"
	/ 可以加速奇观的建造

	Applicable to: Unit

??? example  "Can hurry technology research"
	/ 可以加速科技研究

	Applicable to: Unit

??? example  "Can generate a large amount of culture"
	/ 可产生大量文化

	Applicable to: Unit

??? example  "Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"
	/ 可以拓展与城邦的贸易，获得大笔金钱和[amount]影响力

	Example: "Can undertake a trade mission with City-State, giving a large sum of gold and [3] Influence"

	Applicable to: Unit

??? example  "Automation is a primary action"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "[relativeAmount]% Strength"
	/ 战斗力[relativeAmount]%

	Multiple bonuses stack additively: +50% + +50% = +100%

	Example: "[+20]% Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Strength"
	/ 战斗力[relativeAmount]

	Example: "[+20] Strength"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength decreasing with distance from the capital"
	/ 战斗力加成随与首都的距离减小而增大,最高为[relativeAmount]%

	Example: "[+20]% Strength decreasing with distance from the capital"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% to Flank Attack bonuses"
	/ 侧翼攻击加成[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% to Flank Attack bonuses"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles"
	/ 位于相邻的[tileFilter]地块中的敌方[mapUnitFilter]单位[relativeAmount]%战斗力

	Example: "[+20]% Strength for enemy [Wounded] units in adjacent [Farm] tiles"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles"
	/ 在[amount]格内[mapUnitFilter]单位[relativeAmount]%战斗力

	Example: "[+20]% Strength bonus for [Wounded] units within [3] tiles"

	Applicable to: Unit

??? example  "[amount] additional attacks per turn"
	/ 在每回合可以额外攻击[amount]次

	Example: "[3] additional attacks per turn"

	Applicable to: Global, Unit

??? example  "[amount] Movement"
	/ 移动力[amount]

	Example: "[3] Movement"

	Applicable to: Global, Unit

??? example  "[amount] Sight"
	/ 视野[amount]

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "[amount] Range"
	/ 射程[amount]

	Example: "[3] Range"

	Applicable to: Global, Unit

??? example  "[relativeAmount] Air Interception Range"
	/ 航空器拦截范围[relativeAmount]

	Example: "[+20] Air Interception Range"

	Applicable to: Global, Unit

??? example  "[amount] HP when healing"
	/ 恢复时额外恢复[amount]点生命值

	Example: "[3] HP when healing"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Spread Religion Strength"
	/ 宗教传播力量[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Spread Religion Strength"

	Applicable to: Global, Unit

??? example  "When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]"
	/ 将新城传教成功时获得此城其他宗教教徒数量[amount]倍的[stat]

	Example: "When spreading religion to a city, gain [3] times the amount of followers of other religions as [Culture]"

	Applicable to: Global, Unit

??? example  "Can only attack [combatantFilter] units"
	/ 只能攻击[combatantFilter]单位

	Example: "Can only attack [City] units"

	Applicable to: Unit

??? example  "Can only attack [tileFilter] tiles"
	/ 只能攻击[tileFilter]地块

	Example: "Can only attack [Farm] tiles"

	Applicable to: Unit

??? example  "Cannot attack"
	/ 不可攻击

	Applicable to: Unit

??? example  "Must set up to ranged attack"
	/ 必须架设才能远程攻击

	Applicable to: Unit

??? example  "Self-destructs when attacking"
	/ 攻击时自毁

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking across a coast"
	/ 消除跨海攻击的地形影响

	Applicable to: Unit

??? example  "May attack when embarked"
	/ 陆军单位可以在船载时攻击

	Applicable to: Unit

??? example  "Eliminates combat penalty for attacking over a river"
	/ 无视跨河攻击时的战斗力减益

	Applicable to: Unit

??? example  "Blast radius [amount]"
	/ 爆炸半径：[amount]

	Example: "Blast radius [3]"

	Applicable to: Unit

??? example  "Ranged attacks may be performed over obstacles"
	/ 远程攻击能越过障碍

	Applicable to: Global, Unit

??? example  "Nuclear weapon of Strength [amount]"
	/ 核武器威力：[amount]

	Example: "Nuclear weapon of Strength [3]"

	Applicable to: Unit

??? example  "No defensive terrain bonus"
	/ 不受正面防御地形的影响

	Applicable to: Global, Unit

??? example  "No defensive terrain penalty"
	/ 不受负面防御地形的影响

	Applicable to: Global, Unit

??? example  "No damage penalty for wounded units"
	/ 受伤单位免受伤害惩罚

	Applicable to: Global, Unit

??? example  "Uncapturable"
	/ 不可捕获

	Applicable to: Unit

??? example  "Withdraws before melee combat"
	/ 在近战前撤退

	Applicable to: Unit

??? example  "Unable to capture cities"
	/ 不能攻陷城市

	Applicable to: Global, Unit

??? example  "Unable to pillage tiles"
	/ 不能劫掠地块

	Applicable to: Global, Unit

??? example  "Destroys [cityFilter] cities instead of capturing"
	/ [cityFilter]类型城市无法被占领，只能被摧毁 

	The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities.Capital cities (including city states) are immune to this effect.

	Example: "Destroys [in all cities] cities instead of capturing"

	Applicable to: Unit

??? example  "No movement cost to pillage"
	/ 劫掠不消耗移动力

	Applicable to: Global, Unit

??? example  "Can move after attacking"
	/ 攻击后可移动

	Applicable to: Unit

??? example  "Transfer Movement to [mapUnitFilter]"
	/ 将移动力转移至[mapUnitFilter]单位

	Example: "Transfer Movement to [Wounded]"

	Applicable to: Unit

??? example  "Can move immediately once bought"
	/ 购买后无需等待一回合而可立即行动

	Applicable to: Unit

??? example  "May heal outside of friendly territory"
	/ 可以在友好领土之外自愈

	Applicable to: Global, Unit

??? example  "All healing effects doubled"
	/ 所有生命回复效果翻倍

	Applicable to: Global, Unit

??? example  "Heals [amount] damage if it kills a unit"
	/ 消灭敌方单位时恢复[amount]生命值

	Example: "Heals [3] damage if it kills a unit"

	Applicable to: Global, Unit

??? example  "Can only heal by pillaging"
	/ 只能通过劫掠恢复生命值

	Applicable to: Global, Unit

??? example  "Unit will heal every turn, even if it performs an action"
	/ 回合结束时自动恢复一定生命值

	Applicable to: Unit

??? example  "All adjacent units heal [amount] HP when healing"
	/ 所有相邻单位在恢复时额外[amount]生命值

	Example: "All adjacent units heal [3] HP when healing"

	Applicable to: Unit

??? example  "No Sight"
	/ 无视野

	Applicable to: Unit

??? example  "Can see over obstacles"
	/ 视野可以跨过障碍

	Applicable to: Unit

??? example  "Can carry [amount] [mapUnitFilter] units"
	/ 可装载[amount]个[mapUnitFilter]单位

	Example: "Can carry [3] [Wounded] units"

	Applicable to: Unit

??? example  "Can carry [amount] extra [mapUnitFilter] units"
	/ 可装载[amount]个额外的[mapUnitFilter]单位

	For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity.

	Example: "Can carry [3] extra [Wounded] units"

	Applicable to: Building, Unit

??? example  "Cannot be carried by [mapUnitFilter] units"
	/ [mapUnitFilter]单位无法装载

	Example: "Cannot be carried by [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% chance to intercept air attacks"
	/ [relativeAmount]%几率拦截来袭的敌军飞机

	Example: "[+20]% chance to intercept air attacks"

	Applicable to: Unit

??? example  "Damage taken from interception reduced by [relativeAmount]%"
	/ 遭受拦截时的损伤减少[relativeAmount]%

	Example: "Damage taken from interception reduced by [+20]%"

	Applicable to: Unit

??? example  "[relativeAmount]% Damage when intercepting"
	/ 拦截敌方飞机时伤害[relativeAmount]%

	Example: "[+20]% Damage when intercepting"

	Applicable to: Unit

??? example  "[amount] extra interceptions may be made per turn"
	/ 每回合拦截次数额外[amount]

	Example: "[3] extra interceptions may be made per turn"

	Applicable to: Unit

??? example  "Cannot be intercepted"
	/ 不可拦截

	Applicable to: Unit

??? example  "Cannot intercept [mapUnitFilter] units"
	/ 无法拦截[mapUnitFilter]单位！

	Example: "Cannot intercept [Wounded] units"

	Applicable to: Unit

??? example  "[relativeAmount]% Strength when performing Air Sweep"
	/ 进行空中扫荡时力度[relativeAmount]%

	Example: "[+20]% Strength when performing Air Sweep"

	Applicable to: Unit

??? example  "[relativeAmount]% maintenance costs"
	/ 维护费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% maintenance costs"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% Gold cost of upgrading"
	/ 升级单位的金钱花费[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% Gold cost of upgrading"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]"
	/ 对[combatantFilter]单位造成时获得伤害[amount]%的[stockpile]

	Example: "Earn [3]% of the damage done to [City] units as [Mana]"

	Applicable to: Global, Unit

??? example  "Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately"
	/ 攻陷城市时获得它[stat]产出[amount]倍的[stockpile]

	Example: "Upon capturing a city, receive [3] times its [Culture] production as [Mana] immediately"

	Applicable to: Global, Unit

??? example  "Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]"
	/ 击杀敌方[mapUnitFilter]单位时获得[stockpile](≈已击杀单位的[costOrStrength]×[amount]%)

	Example: "Earn [3]% of killed [Wounded] unit's [Cost] as [Mana]"

	Applicable to: Global, Unit

??? example  "May capture killed [mapUnitFilter] units"
	/ 能俘虏敌方被打败的[mapUnitFilter]单位

	Example: "May capture killed [Wounded] units"

	Applicable to: Unit

??? example  "[amount] XP gained from combat"
	/ 从每次战斗中额外获得[amount]XP

	Example: "[3] XP gained from combat"

	Applicable to: Global, Unit

??? example  "[relativeAmount]% XP gained from combat"
	/ 从战斗中获得的经验[relativeAmount]%

	Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%

	Example: "[+20]% XP gained from combat"

	Applicable to: Global, Unit

??? example  "Can be earned through combat"
	/ 可通过战斗获得

	Applicable to: Unit

??? example  "[greatPerson] is earned [relativeAmount]% faster"
	/ [greatPerson]的招募速率[relativeAmount]%

	Example: "[Great General] is earned [+20]% faster"

	Applicable to: Global, Unit

??? example  "Invisible to others"
	/ 对其他单位隐形

	Applicable to: Unit

??? example  "Invisible to non-adjacent units"
	/ 对非相邻单位隐身

	Applicable to: Unit

??? example  "Can see invisible [mapUnitFilter] units"
	/ 能发现隐形的[mapUnitFilter]单位

	Example: "Can see invisible [Wounded] units"

	Applicable to: Unit

??? example  "May upgrade to [unit] through ruins-like effects"
	/ 可在废墟中升级为[unit]

	Example: "May upgrade to [Musketman] through ruins-like effects"

	Applicable to: Unit

??? example  "Can upgrade to [unit]"
	/ 可升级为[unit]

	Example: "Can upgrade to [Musketman]"

	Applicable to: Unit

??? example  "Destroys tile improvements when attacking"
	/ 攻击时摧毁地块改良

	Applicable to: Unit

??? example  "Cannot move"
	/ 不可移动

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Double movement in [terrainFilter]"
	/ 在[terrainFilter]中拥有双倍移动力

	Example: "Double movement in [Fresh Water]"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "All tiles cost 1 movement"
	/ 进行移动力损耗计算时忽略所有影响

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May travel on Water tiles without embarking"
	/ 下水时不处于船运状态

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can pass through impassable tiles"
	/ 可以穿过无法通行的地块

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores terrain cost"
	/ 进行移动力损耗计算时忽略负面影响

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Ignores Zone of Control"
	/ 忽视地块控制权

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Rough terrain penalty"
	/ 进行移动力损耗计算时强调崎岖地形的影响

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Can enter ice tiles"
	/ 可进入冰区

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "Cannot embark"
	/ 不能船运

	Applicable to: Unit

??? example  "Cannot enter ocean tiles"
	/ 不能进入海洋地块

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders"
	/ 可进入外国未开放边界的领土

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"
	/ 可进入外国未开放边界的领土，在外国领土结束回合时失去[amount]宗教力量

	Example: "May enter foreign tiles without open borders, but loses [3] religious strength each turn it ends there"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Unit

??? example  "[nonNegativeAmount] Movement point cost to disembark"
	/ 登陆时消耗[nonNegativeAmount]移动力

	Example: "[3] Movement point cost to disembark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "[nonNegativeAmount] Movement point cost to embark"
	/ 下水时消耗[nonNegativeAmount]行动力

	Example: "[3] Movement point cost to embark"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	Applicable to: Global, Unit

??? example  "Never appears as a Barbarian unit"
	This unique is automatically hidden from users.

	Applicable to: Unit

??? example  "Religious Unit"
	/ 宗教单位

	Applicable to: Unit

??? example  "Spaceship part"
	/ 太空飞船部件

	Applicable to: Building, Unit

??? example  "Takes your religion over the one in their birth city"
	/ 使其他文明的城市信仰你的宗教，甚至消灭其他宗教

	Applicable to: Unit

??? example  "Great Person - [comment]"
	/ [comment]类伟人

	Example: "Great Person - [comment]"

	Applicable to: Unit

??? example  "Is part of Great Person group [comment]"
	/ 是[comment]类伟人的一部分

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

## UnitType uniques（单位类别）
??? example  "Will not be displayed in Civilopedia"
	This unique is automatically hidden from users.

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

??? example  "Comment [comment]"
	/ [comment]

	Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent.

	Example: "Comment [comment]"

	Applicable to: Nation, Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, Speed, Difficulty, EventChoice

## Promotion uniques（晋升项）
??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Not shown on world screen"
	This unique is automatically hidden from users.

	Applicable to: Promotion, Resource

??? example  "Doing so will consume this opportunity to choose a Promotion"
	/ 这样做将消耗掉这次晋升机会

	Applicable to: Promotion

??? example  "This Promotion is free"
	/ 免费晋升

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

## Terrain uniques（地形修正）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "[amount] Sight"
	/ 视野[amount]

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
	/ 赠与首个发现的文明[stats]

	Example: "Grants [+1 Gold, +2 Production] to the first civilization to discover it"

	Applicable to: Terrain

??? example  "Units ending their turn on this terrain take [amount] damage"
	/ 单位在此地形结束回合时将受到[amount]伤害

	Example: "Units ending their turn on this terrain take [3] damage"

	Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work.

	This unique does not support conditionals.

	Applicable to: Terrain

??? example  "Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game"
	/ 向相邻的[mapUnitFilter]单位授予[promotion]([comment])

	Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

	Applicable to: Terrain

??? example  "[amount] Strength for cities built on this terrain"
	/ 在此地形上建立的城市[amount]战斗力

	Example: "[3] Strength for cities built on this terrain"

	Applicable to: Terrain

??? example  "Provides a one-time bonus of [stats] to the closest city when cut down"
	/ 在移除后一次性为最近的城市产出[stats]

	Example: "Provides a one-time bonus of [+1 Gold, +2 Production] to the closest city when cut down"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Terrain

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ 地块提供产量无需指定人口

	Applicable to: Terrain, Improvement

??? example  "Nullifies all other stats this tile provides"
	/ 取消此地块的所有产出加成

	Applicable to: Terrain

??? example  "Only [improvementFilter] improvements may be built on this tile"
	/ 只有[improvementFilter]能被建造在此地块上

	Example: "Only [All Road] improvements may be built on this tile"

	Applicable to: Terrain

??? example  "Blocks line-of-sight from tiles at same elevation"
	/ 可挡住来自同海拔地块的视线

	Applicable to: Terrain

??? example  "Has an elevation of [amount] for visibility calculations"
	/ 计算单位的视野范围时按高程[amount]计算

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
	/ 稀有地貌

	Applicable to: Terrain

??? example  "[amount]% Chance to be destroyed by nukes"
	/ 被核武器攻击时有[amount]%概率毁灭

	Example: "[3]% Chance to be destroyed by nukes"

	Applicable to: Terrain

??? example  "Fresh water"
	/ 淡水

	Applicable to: Terrain

??? example  "Rough terrain"
	/ 崎岖地形

	Applicable to: Terrain

??? example  "Coastal Water"
	/ 沿海

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

## Improvement uniques（地块改良）
??? example  "[stats]"
	Example: "[+1 Gold, +2 Production]"

	Applicable to: Global, Terrain, Improvement

??? example  "Consumes [amount] [resource]"
	/ 消耗[amount]单位[resource]

	Example: "Consumes [3] [Iron]"

	Applicable to: Building, Unit, Improvement

??? example  "Provides [amount] [resource]"
	/ 获得[amount]单位[resource]

	Example: "Provides [3] [Iron]"

	Applicable to: Global, FollowerBelief, Improvement

??? example  "Costs [amount] [stockpiledResource]"
	/ 消耗[amount][stockpiledResource]

	These resources are removed *when work begins* on the construction. Do not confuse with "costs [amount] [stockpiledResource]" (lowercase 'c'), the Unit Action Modifier.

	Example: "Costs [3] [Mana]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Building, Unit, Improvement

??? example  "Unbuildable"
	/ 不可建造或训练

	Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith

	Applicable to: Building, Unit, Improvement

??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Must be next to [tileFilter]"
	Example: "Must be next to [Farm]"

	Applicable to: Building, Improvement

??? example  "Obsolete with [tech]"
	Example: "Obsolete with [Agriculture]"

	Applicable to: Building, Improvement, Resource

??? example  "[amount] Sight"
	/ 视野[amount]

	Example: "[3] Sight"

	Applicable to: Global, Unit, Terrain, Improvement

??? example  "Vegetation"
	This unique is automatically hidden from users.

	Applicable to: Terrain, Improvement

??? example  "Tile provides yield without assigned population"
	/ 地块提供产量无需指定人口

	Applicable to: Terrain, Improvement

??? example  "Excluded from map editor"
	This unique is automatically hidden from users.

	Applicable to: Nation, Terrain, Improvement, Resource

??? example  "Can also be built on tiles adjacent to fresh water"
	/ 也能建造在毗邻淡水的地块

	Applicable to: Improvement

??? example  "[stats] from [tileFilter] tiles"
	/ 使[tileFilter]地块[stats]

	Example: "[+1 Gold, +2 Production] from [Farm] tiles"

	Applicable to: Improvement

??? example  "[stats] for each adjacent [tileFilter]"
	Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

	Applicable to: Improvement

??? example  "Ensures a minimum tile yield of [stats]"
	/ 保底产出[stats]

	Example: "Ensures a minimum tile yield of [+1 Gold, +2 Production]"

	Applicable to: Improvement

??? example  "Can be built outside your borders"
	/ 可在你的国土外建造

	Applicable to: Improvement

??? example  "Can be built just outside your borders"
	/ 可在与己方地块相邻的非己方地块上建造

	Applicable to: Improvement

??? example  "Can only be built on [tileFilter] tiles"
	/ 只能建在[tileFilter]地块上

	Example: "Can only be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Cannot be built on [tileFilter] tiles"
	/ 无法建在[tileFilter]地块上

	Example: "Cannot be built on [Farm] tiles"

	Applicable to: Improvement

??? example  "Can only be built to improve a resource"
	/ 只能在可以获得资源的情况下建造

	Applicable to: Improvement

??? example  "Does not need removal of [terrainFeature]"
	Example: "Does not need removal of [Hill]"

	Applicable to: Improvement

??? example  "Removes removable features when built"
	/ 在建造时移除可移除地貌

	Applicable to: Improvement

??? example  "Gives a defensive bonus of [relativeAmount]%"
	/ 提供[relativeAmount]%防御力加成

	Does not accept unit-based conditionals

	Example: "Gives a defensive bonus of [+20]%"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn when in your territory"
	/ 建在你的领土内时每回合扣除[amount][stat] 

	Example: "Costs [3] [Culture] per turn when in your territory"

	Applicable to: Improvement

??? example  "Costs [amount] [stat] per turn"
	/ 每回合扣除[amount][stat] 

	Example: "Costs [3] [Culture] per turn"

	Applicable to: Improvement

??? example  "Adjacent enemy units ending their turn take [amount] damage"
	/ 相邻的敌方单位结束回合时受到[amount]伤害

	Example: "Adjacent enemy units ending their turn take [3] damage"

	Applicable to: Improvement

??? example  "Great Improvement"
	/ 伟人改良

	Applicable to: Improvement

??? example  "Provides a random bonus when entered"
	/ 有单位进入则提供随机奖励

	Applicable to: Improvement

??? example  "Unpillagable"
	/ 不可劫掠

	Applicable to: Improvement

??? example  "Pillaging this improvement yields approximately [stats]"
	/ 劫掠该地块改良可获得约[stats]

	Example: "Pillaging this improvement yields approximately [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Pillaging this improvement yields [stats]"
	/ 劫掠该地块改良可获得[stats]

	Example: "Pillaging this improvement yields [+1 Gold, +2 Production]"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	This unique's effect can be modified with &lt;(modified by game progress up to [relativeAmount]%)&gt;

	Applicable to: Improvement

??? example  "Destroyed when pillaged"
	/ 劫掠后被摧毁

	Applicable to: Improvement

??? example  "Irremovable"
	/ 不可劫掠且不可摧毁

	Applicable to: Improvement

??? example  "Will not be replaced by automated units"
	/ 将不会自动交换单位

	Applicable to: Improvement

??? example  "Improves [resourceFilter] resource in this tile"
	/ 改良此地块的[resourceFilter]资源

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

## Resource uniques（资源）
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
	/ [tileFilter]地块始终提供[amount]资源

	Example: "Deposits in [Farm] tiles always provide [3] resources"

	Applicable to: Resource

??? example  "Can only be created by Mercantile City-States"
	/ 只能由商业城邦产出

	Applicable to: Resource

??? example  "Stockpiled"
	/ 具有可堆叠性

	This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment.The current stockpiled amount can be affected with trigger uniques.

	Applicable to: Resource

??? example  "City-level resource"
	/ 城市级别资源

	This resource is calculated on a per-city level rather than a per-civ level

	Applicable to: Resource

??? example  "Cannot be traded"
	/ 无法交易

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
	/ 开启战略平衡选项时一定出现在出生点附近

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

## Ruins uniques（遗迹）
??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Free [unit] found in the ruins"
	/ 在遗迹中发现[unit]

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

## Speed uniques（游戏速度）
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

## Difficulty uniques（难度）
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

## CityState uniques（城邦）
??? example  "Provides military units every ≈[positiveAmount] turns"
	/ 每经过约[positiveAmount]回合提供一个军事单位

	Example: "Provides military units every ≈[3] turns"

	Applicable to: CityState

??? example  "Provides a unique luxury"
	/ 提供一种独特的奢侈资源

	Applicable to: CityState

## ModOptions uniques（模组选项）
??? example  "Diplomatic relationships cannot change"
	/ 暂不能改变外交关系

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can convert gold to science with sliders"
	/ 可通过滑块将金币转换为科学值

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow City States to spawn with additional units"
	/ 允许开局时城邦带有额外单位

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can trade civilization introductions for [positiveAmount] Gold"
	/ 可以用[positiveAmount]金币认识第三方文明

	Example: "Can trade civilization introductions for [3] Gold"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Disable religion"
	/ 禁用宗教

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can only start games from the starting era"
	/ 只能从起始时代开始游戏

	In this case, 'starting era' means the first defined Era in the entire ruleset.

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze capital"
	/ 允许摧毁首都

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Allow raze holy city"
	/ 允许摧毁圣城

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Suppress warning [validationWarning]"
	Allows suppressing specific validation warnings. Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted. Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning - but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter.

	Example: "Suppress warning [Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched! -or- *is supposed to automatically upgrade*]"

	This unique does not support conditionals.

	This unique is automatically hidden from users.

	Applicable to: Triggerable, Terrain, Speed, ModOptions, MetaModifier

??? example  "Mod is incompatible with [modFilter]"
	/ Mod与[modFilter]不兼容

	Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible.

	Example: "Mod is incompatible with [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod requires [modFilter]"
	/ Mod 需要 [modFilter]

	Specifies that your Extension Mod is only available if any other Mod matching the filter is active.

	Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue.

	Example: "Mod requires [DeCiv Redux]"

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Should only be used as permanent audiovisual mod"
	/ 只能用作永久视听Mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Can be used as permanent audiovisual mod"
	/ 可作为永久视听Mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Cannot be used as permanent audiovisual mod"
	/ 不能用作永久视听Mod

	This unique does not support conditionals.

	Applicable to: ModOptions

??? example  "Mod preselects map [comment]"
	/ 模组预选地图：[comment]

	Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive.

	Example: "Mod preselects map [comment]"

	This unique does not support conditionals.

	Applicable to: ModOptions

## Event uniques（事件）
??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

	Meant to be used together with conditionals, like "Unavailable &lt;after generating a Great Prophet&gt;".

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

## EventChoice uniques（事件选择）
??? example  "Only available"
	/ 可用

	Meant to be used together with conditionals, like "Only available &lt;after adopting [policy]&gt; &lt;while the empire is happy&gt;". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen

	Applicable to: Tech, Policy, FounderBelief, FollowerBelief, Building, Unit, Promotion, Improvement, Ruins, Event, EventChoice

??? example  "Unavailable"
	/ 不可用

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

## Conditional uniques（有前提的）
!!! note ""

    Modifiers that can be added to other uniques to limit when they will be active

??? example  "&lt;every [positiveAmount] turns&gt;"
	/ 每[positiveAmount]回合

	Example: "&lt;every [3] turns&gt;"

	Applicable to: Conditional

??? example  "&lt;before turn number [nonNegativeAmount]&gt;"
	/ [nonNegativeAmount]回合之前

	Example: "&lt;before turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;after turn number [nonNegativeAmount]&gt;"
	/ [nonNegativeAmount]回合之后

	Example: "&lt;after turn number [3]&gt;"

	Applicable to: Conditional

??? example  "&lt;on [speed] game speed&gt;"
	/ 以[speed]速度游玩时

	Example: "&lt;on [Quick] game speed&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty&gt;"
	/ 在[difficulty]难度下

	Example: "&lt;on [Prince] difficulty&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or higher&gt;"
	/ 在[difficulty]或更高的难度下

	Example: "&lt;on [Prince] difficulty or higher&gt;"

	Applicable to: Conditional

??? example  "&lt;on [difficulty] difficulty or lower&gt;"
	/ 在[difficulty]或更低的难度下

	Example: "&lt;on [Prince] difficulty or lower&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is enabled&gt;"
	/ 若[victoryType]胜利方式可用

	Example: "&lt;when [Domination] Victory is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when [victoryType] Victory is disabled&gt;"
	/ 若[victoryType]胜利方式禁用

	Example: "&lt;when [Domination] Victory is disabled&gt;"

	Applicable to: Conditional

??? example  "&lt;when religion is enabled&gt;"
	/ 若启用宗教

	Applicable to: Conditional

??? example  "&lt;when religion is disabled&gt;"
	/ 若禁用宗教

	Applicable to: Conditional

??? example  "&lt;when espionage is enabled&gt;"
	/ 若启用间谍

	Applicable to: Conditional

??? example  "&lt;when espionage is disabled&gt;"
	/ 若禁用间谍

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are enabled&gt;"
	/ 若启用核武器

	Applicable to: Conditional

??? example  "&lt;when nuclear weapons are disabled&gt;"
	/ 若禁用核武器

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount]% chance&gt;"
	/ 有[nonNegativeAmount]%概率

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
	/ 令[civFilter]

	Example: "&lt;for [City-States] Civilizations&gt;"

	Applicable to: Conditional

??? example  "&lt;when at war&gt;"
	/ 交战时

	Applicable to: Conditional

??? example  "&lt;when not at war&gt;"
	/ 和平时

	Applicable to: Conditional

??? example  "&lt;during a Golden Age&gt;"
	/ 黄金时代期间

	Applicable to: Conditional

??? example  "&lt;when not in a Golden Age&gt;"
	/ 非黄金时代期间

	Applicable to: Conditional

??? example  "&lt;during We Love The King Day&gt;"
	/ “我们爱戴领袖日”期间

	Applicable to: Conditional

??? example  "&lt;while the empire is happy&gt;"
	/ 处于快乐时

	Applicable to: Conditional

??? example  "&lt;during the [era]&gt;"
	/ 处于[era]时

	Example: "&lt;during the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;before the [era]&gt;"
	/ 在[era]前

	Example: "&lt;before the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;starting from the [era]&gt;"
	/ 从[era]开始

	Example: "&lt;starting from the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if starting in the [era]&gt;"
	/ 若以[era]开始

	Example: "&lt;if starting in the [Ancient era]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has researched this&gt;"
	/ 若其他文明尚未研究这个科技

	Applicable to: Conditional

??? example  "&lt;after discovering [techFilter]&gt;"
	/ 发现[techFilter]后

	Example: "&lt;after discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;before discovering [techFilter]&gt;"
	/ 发现[techFilter]前

	Example: "&lt;before discovering [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;while researching [techFilter]&gt;"
	/ 研究[techFilter]期间

	This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)

	Example: "&lt;while researching [Agriculture]&gt;"

	Applicable to: Conditional

??? example  "&lt;if no other Civilization has adopted this&gt;"
	/ 若没有其他文明推行此政策/信条

	Applicable to: Conditional

??? example  "&lt;if no Civilization has adopted [policy/belief]&gt;"
	/ 若还没有文明推行此[policy/belief]

	Example: "&lt;if no Civilization has adopted [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;after adopting [policy/belief]&gt;"
	/ 在推行[policy/belief]后

	Example: "&lt;after adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before adopting [policy/belief]&gt;"
	/ 在推行[policy/belief]前

	Example: "&lt;before adopting [Oligarchy]&gt;"

	Applicable to: Conditional

??? example  "&lt;before founding a Pantheon&gt;"
	/ 在建立万神殿之后

	Applicable to: Conditional

??? example  "&lt;after founding a Pantheon&gt;"
	/ 在建立万神殿之前

	Applicable to: Conditional

??? example  "&lt;before founding a religion&gt;"
	/ 在创立宗教之前

	Applicable to: Conditional

??? example  "&lt;after founding a religion&gt;"
	/ 在创立宗教之后

	Applicable to: Conditional

??? example  "&lt;before enhancing a religion&gt;"
	/ 在加强宗教信仰之前

	Applicable to: Conditional

??? example  "&lt;after enhancing a religion&gt;"
	/ 在加强宗教信仰之后

	Applicable to: Conditional

??? example  "&lt;after generating a Great Prophet&gt;"
	/ 在出现大先知后

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed&gt;"
	/ 如果已建造[buildingFilter]

	Example: "&lt;if [Culture] is constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed&gt;"
	/ 如果未建造[buildingFilter]

	Example: "&lt;if [Culture] is not constructed&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in all [cityFilter] cities&gt;"
	/ 如果在所有[cityFilter]城市已建造[buildingFilter]

	Example: "&lt;if [Culture] is constructed in all [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities&gt;"
	/ 如果在至少[positiveAmount]个[cityFilter]城市已建造[buildingFilter]

	Example: "&lt;if [Culture] is constructed in at least [3] of [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is constructed by anybody&gt;"
	/ 如果任何人建造了[buildingFilter]

	Example: "&lt;if [Culture] is constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;if [buildingFilter] is not constructed by anybody&gt;"
	/ 如果还没有人建造过[buildingFilter]

	Example: "&lt;if [Culture] is not constructed by anybody&gt;"

	Applicable to: Conditional

??? example  "&lt;with [resource]&gt;"
	/ 拥有[resource]的

	Example: "&lt;with [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;without [resource]&gt;"
	/ 未拥有[resource]的

	Example: "&lt;without [Iron]&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [amount] [stat/resource]&gt;"
	/ 当[stat/resource]超过[amount]时

	Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'

	Example: "&lt;when above [3] [Culture]&gt;"

	This unique's effect can be modified with &lt;(modified by game speed)&gt;

	Applicable to: Conditional

??? example  "&lt;when below [amount] [stat/resource]&gt;"
	/ 当[stat/resource]低于[amount]时

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
	/ 在这个城市中

	Applicable to: Conditional

??? example  "&lt;in [cityFilter] cities&gt;"
	/ 在[cityFilter]城市中

	Example: "&lt;in [in all cities] cities&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities connected to the capital&gt;"
	/ 在连接至首都的城市中

	Applicable to: Conditional

??? example  "&lt;in cities with a [religionFilter] religion&gt;"
	/ 在信奉[religionFilter]宗教的城市中

	Example: "&lt;in cities with a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities not following a [religionFilter] religion&gt;"
	/ 在不信奉[religionFilter]宗教的城市中

	Example: "&lt;in cities not following a [major] religion&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with a major religion&gt;"
	/ 在存在主要宗教的城市中

	Applicable to: Conditional

??? example  "&lt;in cities with an enhanced religion&gt;"
	/ 在存在强化信仰后的宗教的城市中

	Applicable to: Conditional

??? example  "&lt;in cities following our religion&gt;"
	/ 在信仰我们宗教城市中

	Applicable to: Conditional

??? example  "&lt;in cities with a [buildingFilter]&gt;"
	/ 在建有[buildingFilter]的城市中

	Example: "&lt;in cities with a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities without a [buildingFilter]&gt;"
	/ 在未建造[buildingFilter]的城市中

	Example: "&lt;in cities without a [Culture]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with at least [positiveAmount] [populationFilter]&gt;"
	/ 在至少有[positiveAmount][populationFilter]的城市中

	Example: "&lt;in cities with at least [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with [positiveAmount] [populationFilter]&gt;"
	/ 在有[positiveAmount][populationFilter]的城市中

	Example: "&lt;in cities with [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with between [amount] and [amount] [populationFilter]&gt;"
	'Between' is inclusive - so 'between 1 and 5' includes 1 and 5.

	Example: "&lt;in cities with between [3] and [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;in cities with less than [amount] [populationFilter]&gt;"
	/ 在少于[amount][populationFilter]的城市中

	Example: "&lt;in cities with less than [3] [Followers of this Religion]&gt;"

	Applicable to: Conditional

??? example  "&lt;with a garrison&gt;"
	/ 如果城市有军队驻扎

	Applicable to: Conditional

??? example  "&lt;for [mapUnitFilter] units&gt;"
	/ 令[mapUnitFilter]单位

	Example: "&lt;for [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;when [mapUnitFilter]&gt;"
	/ 当处于[mapUnitFilter]状态

	Example: "&lt;when [Wounded]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units with [promotion]&gt;"
	/ 令拥有[promotion]的单位

	Also applies to units with temporary status

	Example: "&lt;for units with [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;for units without [promotion]&gt;"
	/ 令没有[promotion]的单位

	Also applies to units with temporary status

	Example: "&lt;for units without [Shock I]&gt;"

	Applicable to: Conditional

??? example  "&lt;vs cities&gt;"
	/ 对战城市

	Applicable to: Conditional

??? example  "&lt;vs [mapUnitFilter] units&gt;"
	/ 对战[mapUnitFilter]单位

	Example: "&lt;vs [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;vs [combatantFilter]&gt;"
	Example: "&lt;vs [City]&gt;"

	Applicable to: Conditional

??? example  "&lt;when fighting units from a Civilization with more Cities than you&gt;"
	/ 当与拥有较你更多城市的文明作战时

	Applicable to: Conditional

??? example  "&lt;when attacking&gt;"
	/ 攻击时

	Applicable to: Conditional

??? example  "&lt;when defending&gt;"
	/ 防御时

	Applicable to: Conditional

??? example  "&lt;when fighting in [tileFilter] tiles&gt;"
	/ 在[tileFilter]地块上作战时

	Example: "&lt;when fighting in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on foreign continents&gt;"
	/ 在异国大陆上时

	Applicable to: Conditional

??? example  "&lt;when adjacent to a [mapUnitFilter] unit&gt;"
	/ 当和[mapUnitFilter]单位相邻时

	Example: "&lt;when adjacent to a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] HP&gt;"
	/ 当超过[positiveAmount]生命值时

	Example: "&lt;when above [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] HP&gt;"
	/ 当低于[positiveAmount]生命值时

	Example: "&lt;when below [3] HP&gt;"

	Applicable to: Conditional

??? example  "&lt;when below [positiveAmount] movement&gt;"
	/ 当低于[positiveAmount]移动力时

	Example: "&lt;when below [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;when above [positiveAmount] movement&gt;"
	/ 当高于[positiveAmount]移动力时

	Example: "&lt;when above [3] movement&gt;"

	Applicable to: Conditional

??? example  "&lt;if it hasn't used other actions yet&gt;"
	/ 当未进行执行其他行动时

	Applicable to: Conditional

??? example  "&lt;when stacked with a [mapUnitFilter] unit&gt;"
	/ 当与[mapUnitFilter]单位叠加时 

	Example: "&lt;when stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;when not stacked with a [mapUnitFilter] unit&gt;"
	/ 当没有与[mapUnitFilter]单位叠加时 

	Example: "&lt;when not stacked with a [Wounded] unit&gt;"

	Applicable to: Conditional

??? example  "&lt;with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles&gt;"
	Example: "&lt;with [3] to [3] neighboring [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in [tileFilter] tiles&gt;"
	/ 在[tileFilter]地块上

	Example: "&lt;in [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles without [tileFilter]&gt;"
	/ 在非[tileFilter]地块上

	Example: "&lt;in tiles without [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;within [positiveAmount] tiles of a [tileFilter]&gt;"
	/ 在[tileFilter][positiveAmount]格范围内

	Example: "&lt;within [3] tiles of a [Farm]&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles adjacent to [tileFilter] tiles&gt;"
	/ 在与[tileFilter]相邻的地块

	Example: "&lt;in tiles adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;in tiles not adjacent to [tileFilter] tiles&gt;"
	/ 在与[tileFilter]不相邻的地块

	Example: "&lt;in tiles not adjacent to [Farm] tiles&gt;"

	Applicable to: Conditional

??? example  "&lt;on water maps&gt;"
	/ 海洋地图

	Applicable to: Conditional

??? example  "&lt;in [regionType] Regions&gt;"
	/ [regionType]宗教中

	Example: "&lt;in [Hybrid] Regions&gt;"

	Applicable to: Conditional

??? example  "&lt;in all except [regionType] Regions&gt;"
	/ 除[regionType]之外的的所有宗教中

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
	/ 当被[mapUnitFilter]单位搭载时 

	Example: "&lt;when carried by [Wounded] units&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is enabled&gt;"
	/ 如果模组[modFilter]已启用

	Example: "&lt;if [DeCiv Redux] is enabled&gt;"

	Applicable to: Conditional

??? example  "&lt;if [modFilter] is not enabled&gt;"
	/ 如果模组[modFilter]未启用

	Example: "&lt;if [DeCiv Redux] is not enabled&gt;"

	Applicable to: Conditional

## TriggerCondition uniques（触发条件）
!!! note ""

    Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon discovering [techFilter] technology&gt;"
	/ 一旦研究[techFilter]

	Example: "&lt;upon discovering [Agriculture] technology&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering the [era]&gt;"
	/ 一旦进入[era]

	Example: "&lt;upon entering the [Ancient era]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a new era&gt;"
	/ 一旦进入新时代

	Applicable to: TriggerCondition

??? example  "&lt;upon adopting [policy/belief]&gt;"
	/ 一旦推行[policy/belief]

	Example: "&lt;upon adopting [Oligarchy]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring war on [civFilter] Civilizations&gt;"
	/ 一旦和[civFilter]文明宣战时

	Example: "&lt;upon declaring war on [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon being declared war on by [civFilter] Civilizations&gt;"
	/ 一旦被[civFilter]文明宣战时

	Example: "&lt;upon being declared war on by [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a war with [civFilter] Civilizations&gt;"
	/ 一旦和[civFilter]文明交战时

	Example: "&lt;upon entering a war with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon signing a peace treaty with [civFilter] Civilizations&gt;"
	/ 一旦与[civFilter]文明达成停战协议时

	Example: "&lt;upon signing a peace treaty with [City-States] Civilizations&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring friendship&gt;"
	/ 一旦宣布友谊宣言

	Applicable to: TriggerCondition

??? example  "&lt;upon declaring a defensive pact&gt;"
	/ 一旦宣布共同防御条约

	Applicable to: TriggerCondition

??? example  "&lt;upon entering a Golden Age&gt;"
	/ 一旦进入黄金时代

	Applicable to: TriggerCondition

??? example  "&lt;upon ending a Golden Age&gt;"
	/ 一旦结束黄金时代

	Applicable to: TriggerCondition

??? example  "&lt;upon conquering a city&gt;"
	/ 一旦占领城市

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon losing a city&gt;"
	/ 一旦失去城市

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a city&gt;"
	/ 一旦建立城市

	Applicable to: TriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ 一旦修建[improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon discovering a Natural Wonder&gt;"
	/ 一旦发现自然奇观

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter]&gt;"
	/ 一旦建造[buildingFilter]

	Example: "&lt;upon constructing [Culture]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon constructing [buildingFilter] [cityFilter]&gt;"
	/ 一旦[cityFilter]建造[buildingFilter]

	Example: "&lt;upon constructing [Culture] [in all cities]&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon gaining a [baseUnitFilter] unit&gt;"
	/ 一旦获得[baseUnitFilter]单位

	Example: "&lt;upon gaining a [Melee] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon losing a [mapUnitFilter] unit&gt;"
	/ 一旦失去[mapUnitFilter]单位时

	Example: "&lt;upon losing a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ 一旦回合结束

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ 一旦回合开始

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon founding a Pantheon&gt;"
	/ 一旦创立万神殿

	Applicable to: TriggerCondition

??? example  "&lt;upon founding a Religion&gt;"
	/ 一旦创立宗教

	Applicable to: TriggerCondition

??? example  "&lt;upon enhancing a Religion&gt;"
	/ 一旦强化宗教

	Applicable to: TriggerCondition

??? example  "&lt;upon expending a [mapUnitFilter] unit&gt;"
	/ 一旦消耗[mapUnitFilter]单位

	Example: "&lt;upon expending a [Wounded] unit&gt;"

	Applicable to: TriggerCondition

## UnitTriggerCondition uniques（单位触发条件）
!!! note ""

    Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.

??? example  "&lt;upon conquering a city&gt;"
	/ 一旦占领城市

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon building a [improvementFilter] improvement&gt;"
	/ 一旦修建[improvementFilter]

	Example: "&lt;upon building a [All Road] improvement&gt;"

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn end&gt;"
	/ 一旦回合结束

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon turn start&gt;"
	/ 一旦回合开始

	Applicable to: TriggerCondition, UnitTriggerCondition

??? example  "&lt;upon entering combat&gt;"
	/ 每次战斗

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon damaging a [mapUnitFilter] unit&gt;"
	/ 一旦攻击[mapUnitFilter]单位

	Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'

	Example: "&lt;upon damaging a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon defeating a [mapUnitFilter] unit&gt;"
	/ 一旦击败[mapUnitFilter]单位

	Example: "&lt;upon defeating a [Wounded] unit&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being defeated&gt;"
	/ 一旦死亡

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon being promoted&gt;"
	/ 一旦晋升

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] promotion&gt;"
	/ 一旦获得[promotion]

	Example: "&lt;upon gaining the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] promotion&gt;"
	/ 一旦失去[promotion]

	Example: "&lt;upon losing the [Shock I] promotion&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon gaining the [promotion] status&gt;"
	/ 一旦获得[promotion]

	Example: "&lt;upon gaining the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing the [promotion] status&gt;"
	/ 一旦失去[promotion]

	Example: "&lt;upon losing the [Shock I] status&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon losing at least [positiveAmount] HP in a single attack&gt;"
	/ 一旦在单次战斗中丢失至少[positiveAmount]HP

	Example: "&lt;upon losing at least [3] HP in a single attack&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon ending a turn in a [tileFilter] tile&gt;"
	/ 一旦在[tileFilter]地块上结束回合

	Example: "&lt;upon ending a turn in a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon discovering a [tileFilter] tile&gt;"
	/ 一旦发现[tileFilter]地块

	Example: "&lt;upon discovering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

??? example  "&lt;upon entering a [tileFilter] tile&gt;"
	/ 一旦踏入[tileFilter]

	Example: "&lt;upon entering a [Farm] tile&gt;"

	Applicable to: UnitTriggerCondition

## UnitActionModifier uniques（单位行为修饰）
!!! note ""

    Modifiers that can be added to UnitAction uniques as conditionals

??? example  "&lt;by consuming this unit&gt;"
	/ 通过消耗此单位

	Applicable to: UnitActionModifier

??? example  "&lt;for [amount] movement&gt;"
	/ 消耗[amount]移动力

	Will consume up to [amount] of Movement to execute

	Example: "&lt;for [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;for all movement&gt;"
	/ 耗尽移动力

	Will consume all Movement to execute

	Applicable to: UnitActionModifier

??? example  "&lt;requires [nonNegativeAmount] movement&gt;"
	/ 消耗[nonNegativeAmount]移动力

	Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up

	Example: "&lt;requires [3] movement&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [stats] stats&gt;"
	/ 花费[stats]

	A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock

	Example: "&lt;costs [+1 Gold, +2 Production] stats&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;costs [amount] [stockpiledResource]&gt;"
	/ 消耗[amount]单位[stockpiledResource]

	A positive Integer value will be subtracted from your stock. Do not confuse with "Costs [amount] [stockpiledResource]" (uppercase 'C') for Improvements, Buildings, and Units.

	Example: "&lt;costs [3] [Mana]&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;removing the [promotion] promotion/status&gt;"
	/ 移除[promotion]

	Removes the promotion/status from the unit - this is not a cost, units will be able to activate the action even without the promotion/status. To limit, use &lt;with the [promotion] promotion&gt; conditional

	Example: "&lt;removing the [Shock I] promotion/status&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;once&gt;"
	/ 一次

	Applicable to: UnitActionModifier

??? example  "&lt;[positiveAmount] times&gt;"
	/ [positiveAmount]次

	Example: "&lt;[3] times&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;[nonNegativeAmount] additional time(s)&gt;"
	/ 额外[nonNegativeAmount]次

	Example: "&lt;[3] additional time(s)&gt;"

	Applicable to: UnitActionModifier

??? example  "&lt;after which this unit is consumed&gt;"
	/ 在单位被消耗后

	Applicable to: UnitActionModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

## MetaModifier uniques（元修饰）
!!! note ""

    Modifiers that can be added to other uniques changing user experience, not their behavior

??? example  "&lt;for [nonNegativeAmount] turns&gt;"
	/ 持续[nonNegativeAmount]回合

	Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns

	Example: "&lt;for [3] turns&gt;"

	Applicable to: MetaModifier

??? example  "&lt;with [amount] priority&gt;"
	How often this action is used, a higher value means more often and that it should be on an earlier page. 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. A Rare case is &gt; 100 if a button is something like add in capital, promote or something, we need to inform the player that taking the action is an option.

	Example: "&lt;with [3] priority&gt;"

	This unique is automatically hidden from users.

	Applicable to: UnitActionModifier, MetaModifier

??? example  "&lt;hidden from users&gt;"
	/ 对玩家隐藏

	Applicable to: MetaModifier

??? example  "&lt;for every [countable]&gt;"
	/ 每有1个[countable]

	Works for positive numbers only

	Example: "&lt;for every [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every adjacent [tileFilter]&gt;"
	/ 每个相邻[tileFilter]

	Works for positive numbers only

	Example: "&lt;for every adjacent [Farm]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;for every [positiveAmount] [countable]&gt;"
	/ 每有[positiveAmount]个[countable]

	Works for positive numbers only

	Example: "&lt;for every [3] [1000]&gt;"

	Applicable to: MetaModifier

??? example  "&lt;(modified by game speed)&gt;"
	/ (受游戏速度影响)

	Can only be applied to certain uniques, see details of each unique for specifics

	Applicable to: MetaModifier

??? example  "&lt;(modified by game progress up to [relativeAmount]%)&gt;"
	/ (受游戏进程影响，且至多到[relativeAmount]%)

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
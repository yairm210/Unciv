package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache

import com.unciv.ui.screens.worldscreen.WorldScreen


typealias NotificationCategory = Notification.NotificationCategory

open class Notification() : IsPartOfGameInfoSerialization {
    /** Category - UI grouping, within a Category the most recent Notification will be shown on top */
    var category: NotificationCategory = NotificationCategory.General
        private set

    /** The notification text, untranslated - will be translated on the fly */
    var text: String = ""
        private set

    /** Icons to be shown */
    var icons: ArrayList<INotificationIcon> = ArrayList() // Must be ArrayList and not List so it can be deserialized
        private set

    /** Actions on clicking a Notification - will be activated round-robin style */
    var actions: ArrayList<NotificationAction> = ArrayList()
        private set

    constructor(text: String, notificationIcons: ArrayList<INotificationIcon>?, actions: Iterable<NotificationAction>?, category: NotificationCategory) : this() {
        this.category = category
        this.text = text
        if (notificationIcons != null)
            this.icons = notificationIcons
        actions?.toCollection(this.actions)
    }

    enum class NotificationCategory {
        General,
        Trade,
        Diplomacy,
        Production,
        Units,
        War,
        Religion,
        Cities
        ;
        companion object {
            fun safeValueOf(name: String): NotificationCategory? =
                values().firstOrNull { it.name == name }
        }
    }

    @Transient
    private var index = 0

    fun addNotificationIconsTo(table: Table, iconSize: Float) {
        if (icons.isEmpty()) return
        for (icon in icons.reversed()) {
            val image = icon.getImage(iconSize)
            table.add(image).size(iconSize).padRight(5f)
        }
    }

    fun execute(worldScreen: WorldScreen) {
        if (actions.isEmpty()) return
        actions[index].execute(worldScreen)
        index = ++index % actions.size // cycle through tiles
    }

    class Serializer : Json.Serializer<Notification> {
        val ruleset by lazy {
            UncivGame.Current.gameInfo?.ruleset
                ?: RulesetCache[BaseRuleset.Civ_V_GnK.fullName]
        }

        override fun write(json: Json, notification: Notification, knownType: Class<*>?) {
            json.writeObjectStart()
            if (notification.category != NotificationCategory.General)
                json.writeValue("category", notification.category)
            if (notification.text.isNotEmpty())
                json.writeValue("text", notification.text)
            if (notification.icons.isNotEmpty()) {
                json.writeArrayStart("icons")
                for (icon in notification.icons) {
                    json.writeObjectStart()
                    json.writer.name(icon.type.name)
                    json.writeValue(icon.getValueForJson() as Any, String::class.java)
                    json.writeObjectEnd()
                }
                json.writeArrayEnd()
            }
            if (notification.actions.isNotEmpty()) {
                json.writeArrayStart("actions")
                for (action in notification.actions) {
                    json.writeObjectStart()
                    json.writeObjectStart(action::class.java.simpleName)
                    json.writeFields(action)
                    json.writeObjectEnd()
                    json.writeObjectEnd()
                }
                json.writeArrayEnd()
            }
            json.writeObjectEnd()
        }

        override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): Notification {
            // **Cannot** be distinguished by field name action exists / actions doesn't alone
            val hasIcons = jsonData.hasChild("icons")
            val hasAction = jsonData.hasChild("action")
            val hasActions = jsonData.hasChild("actions")
            val iconIsString = hasIcons && jsonData.get("icons").run { isArray && child.isString }
            if (hasAction || !hasActions && hasIcons && iconIsString)
                return readOldFormat(json, jsonData)
            return readNewFormat(json, jsonData)
        }

        private fun readNewFormat(json: Json, jsonData: JsonValue) = Notification().apply {
            // New format looks like this: "notifications":[
            //      {"category":"Cities","text":"[Stockholm] has expanded its borders!","icons":[{"Other":"Culture"}],"actions":[{"LocationAction":{"location":{"x":7,"y":1}}},{"LocationAction":{"location":{"x":9,"y":3}}}]},
            //      {"category":"Production","text":"[Nobel Foundation] has been built in [Stockholm]","icons":[{"Other":"BuildingIcons/Nobel Foundation"}],"actions":[{"LocationAction":{"location":{"x":9,"y":3}}}]}
            //  ]
            json.readField(this, "category", jsonData)
            json.readField(this, "text", jsonData)
            if (jsonData.hasChild("actions")) {
                var entry = jsonData.get("actions").child
                while (entry != null) {
                    actions.addAll(NotificationActionsDeserializer().read(json, entry))
                    entry = entry.next
                }
            }
            if (jsonData.hasChild("icons")) {
                var entry = jsonData.get("icons").child
                while (entry != null) {
                    val icon = NotificationIcon.parseJson(entry) ?: continue
                    icons += icon
                    entry = entry.next
                }
            }
        }

        private fun readOldFormat(json: Json, jsonData: JsonValue) = Notification().apply {
            // Old format looks like: "notifications":[
            //      {"text":"[Stockholm] has expanded its borders!","icons":["StatIcons/Culture"],"action":{"class":"com.unciv.logic.civilization.LocationAction","locations":[{"x":7,"y":1},{"x":9,"y":3}]},"category":"Cities"},
            //      {"text":"[Nobel Foundation] has been built in [Stockholm]","icons":["BuildingIcons/Nobel Foundation"],"action":{"class":"com.unciv.logic.civilization.LocationAction","locations":[{"x":9,"y":3}]},"category":"Production"}
            //  ]

            json.readField(this, "category", jsonData)
            json.readField(this, "text", jsonData)
            val actionData = jsonData.get("action")
            if (actionData != null) {
                val actionClass = actionData.getString("class")
                when (actionClass.substring(actionClass.lastIndexOf('.') + 1)) {
                    "LocationAction" -> actions += getOldFormatLocations(json, actionData)
                    "TechAction" -> actions += json.readValue(TechAction::class.java, actionData)
                    "CityAction" -> actions += json.readValue(CityAction::class.java, actionData)
                    "DiplomacyAction" -> actions += json.readValue(DiplomacyAction::class.java, actionData)
                    "MayaLongCountAction" -> actions += MayaLongCountAction()
                }
            }
            if (jsonData.hasChild("icons")) {
                for (icon in json.readValue("icons", Array<String>::class.java, jsonData)) {
                    val notificationIcon = NotificationIcon.parseOldFormat(ruleset, icon)
                        ?: continue
                    icons += notificationIcon
                }
            }
        }

        private fun getOldFormatLocations(json: Json, actionData: JsonValue): Sequence<LocationAction> {
            val locations = json.readValue("locations", Array<Vector2>::class.java, actionData)
            return locations.asSequence().map { LocationAction(it) }
        }
    }
}

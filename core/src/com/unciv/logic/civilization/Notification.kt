package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.images.ImageGetter
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
    var icons: ArrayList<String> = ArrayList() // Must be ArrayList and not List so it can be deserialized
        private set

    /** Actions on clicking a Notification - will be activated round-robin style */
    var actions: ArrayList<NotificationAction> = ArrayList()
        private set

    constructor(
        text: String,
        notificationIcons: Array<out String>,
        actions: Iterable<NotificationAction>?,
        category: NotificationCategory = NotificationCategory.General
    ) : this() {
        this.category = category
        this.text = text
        if (notificationIcons.isNotEmpty()) {
            this.icons = notificationIcons.toCollection(ArrayList())
        }
        actions?.toCollection(this.actions)
    }

    enum class NotificationCategory {
        // These names are displayed, so remember to add a translation template
        // - if there's no other source for one.
        General,
        Trade,
        Diplomacy,
        Production,
        Units,
        War,
        Religion,
        Espionage,
        Cities
        ;

        companion object {
            fun safeValueOf(name: String): NotificationCategory? =
                values().firstOrNull { it.name == name }
        }
    }

    @Transient
    /** For round-robin activation in [execute] */
    private var index = 0

    fun addNotificationIconsTo(table: Table, ruleset: Ruleset, iconSize: Float) {
        if (icons.isEmpty()) return
        for (icon in icons.reversed()) {
            val image: Actor = when {
                ruleset.technologies.containsKey(icon) ->
                    ImageGetter.getTechIconPortrait(icon, iconSize)
                ruleset.nations.containsKey(icon) ->
                    ImageGetter.getNationPortrait(ruleset.nations[icon]!!, iconSize)
                ruleset.units.containsKey(icon) ->
                    ImageGetter.getUnitIcon(icon)
                else ->
                    ImageGetter.getImage(icon)
            }
            table.add(image).size(iconSize).padRight(5f)
        }
    }

    fun execute(worldScreen: WorldScreen) {
        if (actions.isEmpty()) return
        actions[index].execute(worldScreen)
        index = ++index % actions.size // cycle through tiles
    }

    /**
     *  Custom [Gdx.Json][Json] serializer/deserializer for one [Notification].
     *
     *  Migration roadmap:
     *
     *      1.) Change internal structures but write old json format
     *      2.) Wait for good distribution in multiplayer user base
     *      3.) Switch to writing new format
     *      4.) Wait for Versions prior to Step 3 to fade out, keep switch for quick revert
     *      5.) Remove Switch, old format routines and this comment
     *
     *  Caveats:
     *
     *      * New format can express Notifications the old can't.
     *        In that case, in Phase 1, reduce to first action and throw away the rest.
     */
    class Serializer : Json.Serializer<Notification> {
        companion object {
            /** The switch that starts Phase III and dies with Phase V
            *   @see Serializer */
            private const val compatibilityMode = true
        }

        override fun write(json: Json, notification: Notification, knownType: Class<*>?) {
            json.writeObjectStart()
            if (notification.category != NotificationCategory.General)
                json.writeValue("category", notification.category)
            if (notification.text.isNotEmpty())
                json.writeValue("text", notification.text)
            if (notification.icons.isNotEmpty())
                json.writeValue("icons", notification.icons, null, String::class.java)

            if (compatibilityMode) writeOldFormatAction(json, notification)
            else writeNewFormatActions(json, notification)

            json.writeObjectEnd()
        }

        private fun writeNewFormatActions(json: Json, notification: Notification) {
            if (notification.actions.isEmpty()) return
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

        private fun writeOldFormatAction(json: Json, notification: Notification) {
            if (notification.actions.isEmpty()) return
            val firstAction = notification.actions.first()
            if (firstAction !is LocationAction) {
                json.writeValue("action", firstAction, null)
                return
            }
            val locations = notification.actions.filterIsInstance<LocationAction>()
                .map { it.location }.toTypedArray()
            json.writeObjectStart("action")
            json.writeValue("class", "com.unciv.logic.civilization.LocationAction")
            json.writeValue("locations", locations, Array<Vector2>::class.java, Vector2::class.java)
            json.writeObjectEnd()
        }

        override fun read(json: Json, jsonData: JsonValue, type: Class<*>?) = Notification().apply {
            // Cannot be distinguished 100% certain by field names but if neither action / actions exist then both formats are compatible
            json.readField(this, "category", jsonData)
            json.readField(this, "text", jsonData)
            readOldFormatAction(json, jsonData)
            readNewFormatActions(json, jsonData)
            json.readField(this, "icons", jsonData)
        }

        private fun Notification.readNewFormatActions(json: Json, jsonData: JsonValue) {
            // New format looks like this: "notifications":[
            //      {"category":"Cities","text":"[Stockholm] has expanded its borders!","icons":["StatIcons/Culture"],"actions":[{"LocationAction":{"location":{"x":7,"y":1}}},{"LocationAction":{"location":{"x":9,"y":3}}}]},
            //      {"category":"Production","text":"[Nobel Foundation] has been built in [Stockholm]","icons":["BuildingIcons/Nobel Foundation"],"actions":[{"LocationAction":{"location":{"x":9,"y":3}}}]}
            //  ]
            if (!jsonData.hasChild("actions")) return
            var entry = jsonData.get("actions").child
            while (entry != null) {
                actions.addAll(NotificationActionsDeserializer().read(json, entry))
                entry = entry.next
            }
        }

        private fun Notification.readOldFormatAction(json: Json, jsonData: JsonValue) {
            // Old format looks like: "notifications":[
            //      {"text":"[Stockholm] has expanded its borders!","icons":["StatIcons/Culture"],"action":{"class":"com.unciv.logic.civilization.LocationAction","locations":[{"x":7,"y":1},{"x":9,"y":3}]},"category":"Cities"},
            //      {"text":"[Nobel Foundation] has been built in [Stockholm]","icons":["BuildingIcons/Nobel Foundation"],"action":{"class":"com.unciv.logic.civilization.LocationAction","locations":[{"x":9,"y":3}]},"category":"Production"}
            //  ]
            val actionData = jsonData.get("action") ?: return
            val actionClass = actionData.getString("class")
            when (actionClass.substring(actionClass.lastIndexOf('.') + 1)) {
                "LocationAction" -> actions += getOldFormatLocations(json, actionData)
                "TechAction" -> actions += json.readValue(TechAction::class.java, actionData)
                "CityAction" -> actions += json.readValue(CityAction::class.java, actionData)
                "DiplomacyAction" -> actions += json.readValue(DiplomacyAction::class.java, actionData)
                "MayaLongCountAction" -> actions += MayaLongCountAction()
            }
        }

        private fun getOldFormatLocations(json: Json, actionData: JsonValue): Sequence<LocationAction> {
            val locations = json.readValue("locations", Array<Vector2>::class.java, actionData)
            return locations.asSequence().map { LocationAction(it) }
        }
    }
}

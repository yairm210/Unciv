package com.unciv.logic.civilization

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.screens.worldscreen.WorldScreen
import yairm210.purity.annotations.Pure


typealias NotificationCategory = Notification.NotificationCategory

class Notification() : IsPartOfGameInfoSerialization, Json.Serializable {
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
        notificationIcons: Array<out String>,  // `out` needed so we can pass a vararg directly
        actions: Iterable<NotificationAction>?,
        category: NotificationCategory = NotificationCategory.General
    ) : this() {
        this.category = category
        this.text = text
        notificationIcons.toCollection(this.icons)
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
            @Pure fun safeValueOf(name: String): NotificationCategory? =
                entries.firstOrNull { it.name == name }
        }
    }

    @Transient
    /** For round-robin activation in [execute] */
    private var index = 0

    fun addNotificationIconsTo(table: Table, ruleset: Ruleset, iconSize: Float) {
        if (icons.isEmpty()) return
        for (icon in icons.reversed())
            table.add(NotificationIcon.getImage(icon, ruleset, iconSize)).size(iconSize).padRight(5f)
    }

    fun execute(worldScreen: WorldScreen) {
        if (actions.isEmpty()) return
        actions[index].execute(worldScreen)
        index = ++index % actions.size // cycle through tiles
    }

    fun resetExecuteRoundRobin() {
        index = 0
    }

    /**
     *  Custom [Gdx.Json][Json] serialization by impementing Json.Serializable
     *
     *  Example of the serialized format:
     *  ```json
     *  "notifications":[
     *      {
     *          "category":"Production",
     *          "text":"[Nobel Foundation] has been built in [Stockholm]",
     *          "icons":["BuildingIcons/Nobel Foundation"],
     *          "actions":[
     *              {"LocationAction":{"location":{"x":9,"y":3}}},
     *              {"CivilopediaAction":{"link":"Wonder/Nobel Foundation"}},
     *              {"CityAction":{"city":{"x":9,"y":3}}}
     *          ]
     *      }
     *  ]
     *  ```
     */

    override fun write(json: Json) {
        if (category != NotificationCategory.General)
            json.writeValue("category", category)
        if (text.isNotEmpty())
            json.writeValue("text", text)
        if (icons.isNotEmpty())
            json.writeValue("icons", icons, null, String::class.java)
        writeActions(json)
    }
    private fun writeActions(json: Json) {
        if (actions.isEmpty()) return
        json.writeArrayStart("actions")
        for (action in actions) {
            json.writeObjectStart()
            json.writeObjectStart(action::class.java.simpleName)
            json.writeFields(action)
            json.writeObjectEnd()
            json.writeObjectEnd()
        }
        json.writeArrayEnd()
    }

    override fun read(json: Json, jsonData: JsonValue) {
        json.readField(this, "category", jsonData)
        json.readField(this, "text", jsonData)
        readActions(json, jsonData)
        json.readField(this, "icons", jsonData)
    }
    private fun readActions(json: Json, jsonData: JsonValue) {
        if (!jsonData.hasChild("actions")) return
        var entry = jsonData.get("actions").child
        while (entry != null) {
            actions.addAll(NotificationActionsDeserializer().read(json, entry))
            entry = entry.next
        }
    }
}

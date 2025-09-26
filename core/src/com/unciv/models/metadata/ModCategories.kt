package com.unciv.models.metadata

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.github.GithubAPI
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.logic.github.Github


class ModCategories : ArrayList<ModCategories.Category>() {

    class Category(
        val label: String,
        val topic: String,
        val hidden: Boolean,
        /** copy of github created_at, no function except help evaluate */
        @Suppress("unused")
        val createDate: String,
        /** copy of github updated_at, no function except help evaluate */
        var modifyDate: String
    ) {
        constructor() :
            this("", "", false, "", "")

        constructor(topic: GithubAPI.TopicSearchResponse.Topic) :
            this(labelSuggestion(topic), topic.name, true, topic.created_at, topic.updated_at)

        companion object {
            val All = Category("All mods", "unciv-mod", false, "", "")
            fun labelSuggestion(topic: GithubAPI.TopicSearchResponse.Topic) =
                topic.display_name?.takeUnless { it.isBlank() }
                    ?: topic.name.removePrefix("unciv-mod-").replaceFirstChar(Char::titlecase)
        }

        override fun equals(other: Any?) = this === other || other is Category && topic == other.topic
        override fun hashCode() = topic.hashCode()
        override fun toString() = label
    }

    companion object {
        private const val fileLocation = "jsons/ModCategories.json"

        private val INSTANCE: ModCategories

        init {
            val file = Gdx.files.internal(fileLocation)
            INSTANCE = if (file.exists())
                json().fromJson(ModCategories::class.java, Category::class.java, file)
                else ModCategories().apply { add(default()) }
        }

        fun default() = Category.All
        suspend fun mergeOnline() = INSTANCE.mergeOnline()
        fun fromSelectBox(selectBox: TranslatedSelectBox) = INSTANCE.fromSelectBox(selectBox)
        fun asSequence() = INSTANCE.asSequence().filter { !it.hidden }
        operator fun iterator() = asSequence().iterator()
    }

    private fun save() {
        val json = json()
        val compact = json.toJson(this, ModCategories::class.java, Category::class.java)
        val verbose = json.prettyPrint(compact)
        UncivGame.Current.files.getLocalFile(fileLocation).writeString(verbose, false, Charsets.UTF_8.name())
    }

    fun fromSelectBox(selectBox: TranslatedSelectBox): Category {
        val selected = selectBox.selected.value
        return firstOrNull { it.label == selected } ?: Category.All
    }

    suspend fun mergeOnline(): String {
        val topics = Github.tryGetGithubTopics() ?: return "Failed"
        var newCount = 0
        for (topic in topics.items.sortedBy { it.name }) {
            val existing = firstOrNull { it.topic == topic.name }
            if (existing != null) {
                existing.modifyDate = topic.updated_at
            } else {
                add(Category(topic))
                newCount++
            }
        }
        save()
        return "$newCount new categories"
    }
}

package com.unciv.ui.utils

// Platform specific! This is the common interface to be implemented per platform
interface IImportExport {
    fun isSupported(): Boolean
    fun requestExport (params: ImportExportParameters, notify: ((status:ImportExportStatus, msg:String)->Unit)? )
    fun requestImport (params: ImportExportParameters, notify: ((status:ImportExportStatus, msg:String)->Unit)? )
    fun isInProgress(): Boolean
}

// Enum to be passed by the callback
enum class ImportExportStatus {
    Progress, ChosenFolder, Failure, ExportSuccess, ImportSuccess
}

// Utility class to hold and pass parameters for the Import/Export feature
class ImportExportParameters (
    val config: Boolean = true,
    val saves: Boolean = true,
    val autosave: Boolean = true,
    val maps: Boolean = true,
    val mods: Boolean =  false,
    val music: Boolean = false,
    var suggestFolder: String = ""
) {
    fun any() = config || saves || autosave || maps || mods || music
    fun all() = config && saves && autosave && maps && mods && music
    fun isDefault() = config && saves && autosave && maps && !mods && !music && suggestFolder.isEmpty()
    fun nullIfDefault(): ImportExportParameters? = if (isDefault()) null else this

    companion object {
        fun defaultedClone(template: ImportExportParameters?): ImportExportParameters {
            return if (template != null)
                ImportExportParameters(template.config, template.saves, template.autosave, template.maps, template.mods, template.music, template.suggestFolder)
            else
                ImportExportParameters()
        }
    }
}

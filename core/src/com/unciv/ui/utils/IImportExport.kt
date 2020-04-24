package com.unciv.ui.utils

data class ImportExportParameters (
        val config: Boolean = true,
        val saves: Boolean = true,
        val autosave: Boolean = true,
        val maps: Boolean = true
)

// Platform specific! This is the common interface to be implemented per platform
interface IImportExport {
    fun isSupported(): Boolean
    fun requestExport (params: ImportExportParameters, notify: ((success:Boolean, msg:String)->Unit)? )
    fun requestImport (params: ImportExportParameters, notify: ((success:Boolean, msg:String)->Unit)? )
}
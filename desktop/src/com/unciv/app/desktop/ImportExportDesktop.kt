package com.unciv.app.desktop

import com.unciv.ui.utils.IImportExport
import com.unciv.ui.utils.ImportExportParameters

class ImportExportDesktop(private val debug: Boolean): IImportExport {
    override fun isSupported(): Boolean {
        return debug
    }

    override fun requestExport(params: ImportExportParameters, notify: ((success: Boolean, msg: String) -> Unit)?) {
        notify?.invoke(true,"Export-test")
    }

    override fun requestImport(params: ImportExportParameters, notify: ((success: Boolean, msg: String) -> Unit)?) {
        notify?.invoke(true,"Import-test")
    }
}
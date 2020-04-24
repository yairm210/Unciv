package com.unciv.app.desktop

import com.unciv.ui.utils.IImportExport
import com.unciv.ui.utils.ImportExportParameters
import com.unciv.ui.utils.ImportExportStatus

class ImportExportDesktop(private val debug: Boolean): IImportExport {
    override fun isSupported(): Boolean {
        return debug
    }

    override fun requestExport(params: ImportExportParameters, notify: ((status: ImportExportStatus, msg: String) -> Unit)?) {
        notify?.invoke(ImportExportStatus.Success,"Export-test")
    }

    override fun requestImport(params: ImportExportParameters, notify: ((status:ImportExportStatus, msg: String) -> Unit)?) {
        notify?.invoke(ImportExportStatus.Success,"Import-test")
    }
}

package com.unciv.app.web

import com.unciv.utils.LogBackend
import com.unciv.utils.Tag
import java.time.Instant

class WebLogBackend : LogBackend {
    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        println("${Instant.now()} [$curThreadName] [${tag.name}] $msg")
    }

    override fun error(tag: Tag, curThreadName: String, msg: String) {
        println("${Instant.now()} [$curThreadName] [${tag.name}] [ERROR] $msg")
    }

    override fun isRelease(): Boolean = false

    override fun getSystemInfo(): String = "TeaVM web runtime"
}

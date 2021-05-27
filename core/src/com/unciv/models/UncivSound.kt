package com.unciv.models

enum class UncivSound(
    val value: String,
    var custom: String? = null
) {
    Click("click"),
    Fortify("fortify"),
    Promote("promote"),
    Upgrade("upgrade"),
    Setup("setup"),
    Chimes("chimes"),
    Coin("coin"),
    Choir("choir"),
    Policy("policy"),
    Paper("paper"),
    Whoosh("whoosh"),
    Silent(""),
    Custom("");
    companion object {
        fun getCustom(name: String?): UncivSound {
            if (name == null || name.isEmpty()) return Click
            val item = Custom
            item.custom = name
            return item
        }
    }
}
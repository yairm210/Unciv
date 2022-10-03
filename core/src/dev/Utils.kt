package dev

public fun time(function: () -> Any) : Long {
    val start = System.currentTimeMillis()
    function()
    val duration = System.currentTimeMillis() - start
    println("TIMING: $duration")
    return duration
}

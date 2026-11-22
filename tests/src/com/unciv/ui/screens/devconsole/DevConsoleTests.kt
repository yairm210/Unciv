package com.unciv.ui.screens.devconsole

import com.unciv.testing.BaseTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class DevConsoleTests {
    /** Tool: List all commands */
    @Test
    @Ignore("Testing tool without asserts")
    @RedirectOutput(RedirectPolicy.Show)
    fun listAllCommands() {
        val commands = DevConsolePopup.enumerateCommands()
        for ((_, format) in commands) {
            println(format)
        }
    }

    /** Catches issue #15765 */
    @Test
    fun allFunctionDescriptionsMatch() {
        val commands = DevConsolePopup.enumerateCommands()
        var failures = 0
        for ((nodes, format) in commands) {
            val prefix = nodes.joinToString(" ")
            if (format == prefix || format.startsWith("$prefix ")) continue
            failures++
            println("DevConsole command $format does not match the subnode keys of the parent branch ($nodes).")
        }
        Assert.assertEquals(0, failures)
    }

    //TODO Autocompletion unit test - DevConsolePopup is too tightly integrated with WorldScreen at the moment to allow that.
    //     If autocomplete were solely a function of text and ruleset - but it isn't, e.g. unit addpromotion autocomplete looks into the actual MapUnit.
}

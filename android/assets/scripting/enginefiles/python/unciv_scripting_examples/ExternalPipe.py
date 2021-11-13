"""
Demo for using the scripting API to get and return data from the main Unciv process's STDIN and STDOUT.

Could potentially be useful for "AI/ML" applications and the like.


It's a bit weird, because it means that every call basically goes through two different REPLs and at least six back and forth IPC packets:

Kotlin/JVM:Unciv
—> CPython:EmulatedREPL
—> Kotlin/JVM:Unciv
—> ExternalProcess:REPLOrAutomation
—> Kotlin/JVM:Unciv
—> CPython:EmulatedREPL
—> Kotlin/JVM:Unciv

But I think letting the script have control of the process's STDIN and STDOUT is preferable to hardcoding something into the Kotlin/JVM Unciv code.
"""

def readUncivSdtOn():
	pass

def printUncivStdOut():
	pass

def evalFromUncivStdIn():
	pass

def execFromUncivStdIn():
	pass

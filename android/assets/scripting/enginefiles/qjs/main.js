function motd() {
	return "\nThis backend is HIGHLY EXPERIMENTAL. It does not implement any API bindings yet, and it may not be stable. Use it at your own risk!\n\n"
}


`
Due to the basic design of Python, any Python environment that you lock down enough to be safe will also be nearly useless.

So, I'm thinking that the two or three different backend languages can be specialized for different uses.

With the lightweight nature and easy sandboxing of JS and Lua, plus the ready availability of JS engines on Android and the easy embedability of Lua, JS and Lua are well-suited for running mods.

Python should be disabled for downloaded mods, I think. CPython's big and porous, PyPy has a sandbox but it's complicated, and MicroPython's just a smaller and less compatible reimplementation of CPython— At which point, you as well just use JS/Lua.

Instead, CPython can be the favoured interpreter for developer tools and user script macros. Debug inspection, map editor tools, prototype features and research projects, player-written automation, etc. Because it wouldn't need to be sandboxed in these types of uses, this would let Python's massive library ecosystem and high extensibility shine. Numpy, Cython modules, C extensions and CTypes, PIL, Tensorflow, etc would all be possible to use, as would the user's filesystem and their own modules.

So JS and Lua can be made highly portable/lightweight, and safely sandboxed to run mods. Meanwhile, CPython, if it's installed on the user's system, can be used as a richer scripting environment for developer/modder tools and user customization.
`


try {
	while (true) {
		let line = std.in.getline();
		if (line === null) {
			// std.in.getline() returns null in case of IOError, broken pipes. So this check prevents it from eating 100% CPU in a loop, which could previously happen if you closed Unciv with the window button.
			throw Error("Null on STDIN.")
		}
		let out = `qjs > ${line}\n`;
		try {
			out += String(eval(line));
		} catch (e) {
			out += String(e)
		}
		out += "\n"
		std.out.puts(out)
		std.out.flush()
	}
} catch (e) {
} finally {
	std.exit()
}

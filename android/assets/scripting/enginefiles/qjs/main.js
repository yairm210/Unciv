`The recommended use case of the JS backend is for modding. For user automation, debug, custom tools, prototyping, or experimentation, the Python backend may provide more features.

However, JS is the only scripting backend planned to be supported on mobile platforms.`


function motd() {
	return "\nThis backend is HIGHLY EXPERIMENTAL. It does not implement any API bindings yet, and it may not be stable. Use it at your own risk!\n\n"
}


// So... cashapp/zipline is clearly the best JS library to use for this (which in turn means that embedded QuickJS, and not Webview V8 or anything will indeed be the engine).
// Maybe LiquidCore? But I assume that's way heavier.


// QuickJS, like CPython, has a C API for native modules. But that probably shouldn't be used here.


`
Due to the basic design of Python, any Python environment that you lock down enough to be safe will also be nearly useless.

So, I'm thinking that the two or three different backend languages can be specialized for different uses.

With the lightweight nature and easy sandboxing of JS and Lua, plus the ready availability of JS engines on Android and the easy embedability of Lua, JS and Lua are well-suited for running mods.

Python should be disabled for downloaded mods, I think. CPython's big and porous, PyPy has a sandbox but it's complicated, and MicroPython's just a smaller and less compatible reimplementation of CPython— At which point, you as well just use JS/Lua.

Instead, CPython can be the favoured interpreter for developer tools and user script macros. Debug inspection, map editor tools, prototype features and research projects, player-written automation, etc. Because it wouldn't need to be sandboxed in these types of uses, this would let Python's massive library ecosystem and high extensibility shine. Numpy, Cython modules, C extensions and CTypes, PIL, Tensorflow, etc would all be possible to use, as would the user's filesystem and their own modules.

So JS and Lua can be made highly portable/lightweight, and safely sandboxed to run mods. Meanwhile, CPython, if it's installed on the user's system, can be used as a richer scripting environment for developer/modder tools and user customization.
`

function ContextManager() {
}
Object.assign(ContextManager.prototype, {
	enter: function() {
		return this;
	},
	exit: function(exception) {
		return false;
	},
	withRun: function(callfunc) {
		let value = this.enter();
		let error = null;
		let result = undefined;
		try {
			result = callfunc(value);
		} catch (e) {
			error = e;
		}
		if (this.exit(error)) {
			throw error;
		}
		return result;
	}
});

function FakeStdOut() {
}
FakeStdOut.prototype = Object.assign(Object.create(ContextManager.prototype), {
	enter: function() {
		this.fakeout = []
	},
	exit: function() {
	}
});

function makeScopeProxy() {

}

//let handlers={get: (target, prop, receiver) => prop == 'real' ? target : new Proxy([...target, prop], handlers)}; let p=new Proxy([], handlers)
// Chrome, Node, and SpiderMonkey can print this fine. QuickJS and Deno use inspection in their REPL.
// TODO: Implement JS bindings.

// https://stackoverflow.com/questions/9781285/specify-scope-for-eval-in-javascript
// https://www.figma.com/blog/how-we-built-the-figma-plugin-system/#attempt-3-realms
// https://stackoverflow.com/questions/37010237/android-how-to-use-isolatedprocess

try {
	while (false) {
		let line = std.in.getline();
		if (line === null) {
			// std.in.getline() returns null in case of error. So this check prevents it from eating 100% CPU in a loop, which could previously happen if you closed Unciv with the window button.
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
	//std.exit()
}

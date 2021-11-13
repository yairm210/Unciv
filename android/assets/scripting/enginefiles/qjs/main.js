function motd() {
	return "\nThis backend is HIGHLY EXPERIMENTAL. It does not implement any API bindings yet, and it may not be stable. Use it at your own risk!\n\n"
}


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

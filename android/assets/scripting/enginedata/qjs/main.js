//import("std");

std.out.puts("\n\nWelcome to the QuickJS Unciv CLI. Currently, this backend relies on launching the system `qjs` command.\n\n")
std.out.flush()

while (true) {
	let line = std.in.getline();
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

//import("std");

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
}

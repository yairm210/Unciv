
io.stdout:setvbuf('full')


function motd () 
	return "\n\nWelcome to the Lua Unciv CLI. Currently, this backend relies on launching the system `lua` command.\n\nRunning ".._VERSION..".\n\n"
end

while true do
	_in = io.stdin:read()
	io.stdout:write("> ".._in.."\n")
	_, _out = pcall(load("return ".._in))
	if not _ then
		_, _out = pcall(load(_in))
	end
	io.stdout:write((_out or "").."\n")
	io.stdout:flush()
end

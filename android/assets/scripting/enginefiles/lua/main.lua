
io.stdout:setvbuf('full')


function motd ()
	return "\nRunning ".._VERSION..".\n\nThis backend is HIGHLY EXPERIMENTAL. It does not implement any API bindings yet, and it may not be stable. Use it at your own risk!\n\n"
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

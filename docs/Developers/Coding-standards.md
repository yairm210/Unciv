# Coding standards

As an open-source project, there will be a lot of eyes on our code.

The main purpose of having a coding standard is for the code to be as immediately readable as possible to as many potential contributors, and hence most of it focuses on defaulting to coding structures that exist in other similar languages (Java, C#) when possible.

## Exit Early

Check conditions where the function should exit at the top, and just `return`.

This both reduces nesting and clarifies the flow of the function - "if we got this far we don't need to worry about X"

## Don't use `.let{}` and `?:` in multiline functions

Kotlin is made greater for being strict with nullability. Don't let this fact confuse people new to it. These can be simply replaced by `if(x!=null)` which is much more readable. They all probably compile to the same bytecode anyway, so when in doubt - readability.

If it's a single-line function anyway, then fine, I won't be pedantic

## `for (item in list)` and not `list.forEach{}`

For loops go waaaay back, forEach doesn't.

As above, if it's a single-line function I'm less pedantic.

## Avoid premature abstraction

There's no need to create an interface if there is only one implementation of that interface. Doing so obfuscates the actual code that's running and increases the Time To Relevant Code. If abstraction becomes necessary later, we can always do it later.

## Remove trailing space before commit (non-Translation)

Trailing spaces are indent characters represented as `..` at the start of line. Android Studio always defaults to create similar indents as previous lines. Thus on attempts to create a blank line (in a code block), it produces trailing space character. Unchecking `Remove trailing spaces on [..]` is necessary for translation part of project but these characters creates annoying code merges.

Settings > Editor > General > Appearence > Check `Show whitespaces` and remove the `..` characters.
![image](../assets/show_whitespace.png)
![gif](../assets/trailing_space.gif)

Often we want to rephrase a Unique, or add a parameter in it that extends the use-case for that unique. 

However, we need to allow modders some time to replace the old unique with the new one. 
This means there will be some overlap - usually around an "entire minor version", which is 20 versions, which is ~2.5 months.
During this time, both the old and new uniques need to work, and after this time, we should be able to find the old unique and properly kill it.

Here's how we go about it.

- Rename the old unique to "<old-unique-name>Old", including uniques (shift+f6) 
- Create the new unique directly above the old one, using the old name
- Wherever the old unique is used **add in the new one** rather than replacing it.
- Add a @Deprecated annotation to the old unique, with the replacement text - this allows modders to auto-replace the old unique with the new one
  - The deprecation level must be `DeprecationLevel.WARNING` - or do away with the deprecation level entirely since that's the default

After the time has passed, we will

- Move the old unique with the deprecation annotation to the bery top of "deprecated and removed" section, so it's sorted by recency
- Remove all usages of the old unique from the code
- Change the deprecation level to ERROR (which means we can't reintroduce usages)

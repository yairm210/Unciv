# Mod Source Updates

This fork tracks bundled mods as source repositories pinned in `mods-manifest.json`.
Each mod should use a public repository URL and an exact branch, tag, or ref to watch.

Example entry:

```json
{
  "name": "5Hex Tileset",
  "slug": "5hex-tileset",
  "source": {
    "type": "repo",
    "url": "https://github.com/ravignir/5Hex-Tileset.git",
    "ref": "master",
    "pinnedCommit": ""
  },
  "includeInRecivVanilla": true,
  "integrationRole": "extension",
  "integrationOrder": 20,
  "dependsOn": [],
  "license": {
    "status": "pending",
    "files": [],
    "readmes": [],
    "credits": []
  }
}
```

Set `license.status` to `approved` or `compatible` only after reviewing the mod's
license, README, and credits. The importer refuses to bundle a repo-backed mod
while the status is still `pending`.

Useful commands:

```powershell
python tools\mods\mod_repo_manager.py validate-manifest
python tools\mods\mod_repo_manager.py check-updates
python tools\mods\mod_repo_manager.py update-one --mod 5hex-tileset --dry-run
python tools\mods\mod_repo_manager.py update-one --mod 5hex-tileset
```

The scheduled `Mod updates` workflow checks each repo-backed mod and opens a
separate pull request per updated mod. Unciv upstream updates remain separate.

For the DeCiv fork direction, use `integrationRole: "base-ruleset"` for the
primary DeCiv ruleset and `integrationRole: "extension"` for additional faction,
rebalance, or content mods. `integrationOrder` is the deterministic merge order
for generated base-game content: lower numbers merge first.

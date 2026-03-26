# Upstream Diff Audit

Scope: `upstream/master...origin/master`

Audit artifact: `ai_stuffs/upstream_diff_audit.csv`

What I treated as actual regressions and fixed:

- `core/src/com/unciv/logic/GameInfo.kt`
  - Restored upstream checksum behavior when SHA-1 is available.
  - Removed the branch-wide save-load normalization that silently rewrote missing base rulesets and mods on every platform.
  - Kept the narrower web fallback path for constrained runtimes.

- `core/src/com/unciv/models/metadata/GameSettings.kt`
  - Restored locale-aware `Collator` ordering on normal threaded platforms.
  - Kept the simpler comparator fallback only for constrained web runtime paths.

- `core/src/com/unciv/ui/audio/SoundPlayer.kt`
  - Restored delayed Android retry behavior instead of tight immediate replay loops.
  - Restored preload pacing so startup does not hammer the GL/runtime path.

- `core/src/com/unciv/ui/screens/worldscreen/WorldScreen.kt`
  - Restored the autosave gate so next-turn remains blocked until autosave completion.

Things that look non-essential to the upstream PR but were kept because you explicitly said not to remove them:

- `AGENTS.md`
- `ai_stuffs/**`
- `.devcontainer/devcontainer.json`
- `.github/workflows/web-war-deep-nightly.yml`

General audit rule used:

- `web/` changes were treated as legitimate by instruction.
- Outside `web/`, I reviewed the fork diff and only reverted changes that clearly altered shared cross-platform behavior without a strong web-only justification.
- Everything else is recorded in the CSV as kept, fixed, or kept-per-user.

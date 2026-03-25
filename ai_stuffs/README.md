# AI Stuffs

This directory contains fork-only process/meta artifacts that looked like AI-generated or AI-maintained project clutter compared to `upstream/master`.

What got moved here:

- `planning/`
  - phase plans
  - feature/status matrix CSVs
  - one-off execution-plan CSVs
- `logs/`
  - append-only progress journals
- `docs/`
  - architecture/status notes that describe the AI-led web rollout rather than the game itself
  - meta explanation docs such as `strange_stuffs.md`

Why these were treated as junk:

- They do not participate in runtime behavior.
- They are not required for the current build/test pipeline.
- They mostly record implementation process, rollout phases, or AI reasoning history.
- They add a lot of repo noise when comparing this fork to upstream.

What was intentionally not moved:

- actual web/runtime/source code
- build scripts needed by Gradle
- CI workflows and test harness code
- platform shims that are required to compile or run the fork

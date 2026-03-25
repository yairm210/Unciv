# Web Capability Staging (Phase 3)

## Purpose
Track rollout stage and blockers for features that were disabled in web phase 1.

## Stage Definitions
1. `DISABLED`: not available in production web build.
2. `ALPHA`: hidden behind internal toggle; non-blocking failures expected.
3. `BETA`: user-facing for selected users; must pass dedicated E2E suite.
4. `ENABLED`: default-on; must pass full regression and release gates.

## Current Staging
1. `onlineMultiplayer`: `ALPHA`
2. `customFileChooser`: `BETA`
3. `onlineModDownloads`: `ALPHA`
4. `systemFontEnumeration`: `DISABLED`

## Promotion Criteria
1. `ALPHA -> BETA`
   - Feature-specific browser E2E suite passes on Chromium.
   - No new critical crashes in runtime validation.
2. `BETA -> ENABLED`
   - Feature-specific browser E2E suite passes on required browser matrix.
   - No regression vs committed baseline.
   - Release workflow stays green with feature enabled.

## Blockers
1. `onlineMultiplayer`
   - Production-safe web networking implementation and reconnect flow.
2. `customFileChooser`
   - Browser API compatibility layer and deterministic fallback behavior.
3. `onlineModDownloads`
   - Download/update storage lifecycle and integrity validation.
4. `systemFontEnumeration`
   - Deterministic cross-browser font metrics and fallback ordering.

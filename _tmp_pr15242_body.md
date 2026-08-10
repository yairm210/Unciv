## Motivation

Part of #15241 (Phase 2). LekMOD Police Station: when a steal **succeeds** and a defending counter-intelligence spy is present, stolen science is reduced (halved in LekMOD). Unciv currently always grants the full technology.

## Summary
- Effect unique: `Enemy spies steal [relativeAmount]% of a technology in [cityFilter]`
- Conditional: `when counter-intelligence is active` (defending spy on Counter-intelligence in that city)
- Police Station example: `Enemy spies steal [50]% of a technology in [in this city] <when counter-intelligence is active>`
- Stacks multiplicatively; without matching uniques, steals remain full technology

## Test plan
- [ ] City with Police Station unique + CI spy: successful steal → research progress = 50% of tech cost (not full tech)
- [ ] Same unique **without** CI spy / conditional fails → full tech steal
- [ ] Effect unique with no conditional → reduced steal even without CI (modder choice)
- [ ] Constabulary rank unique (#15233) still stacks independently
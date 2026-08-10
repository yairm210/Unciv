## Summary
- New trigger conditional: `upon gifting a [mapUnitFilter] unit to a City-State` (`UniqueTarget.TriggerCondition`).
- Fired from the City-State gift action (before destroy/gift), so Sweden-style rewards compose as `Gain [200] [Gold] <upon gifting a [Great Person] unit to a City-State>`.
- Non-military units that match such a trigger are giftable to City-States (same allowance path as the Influence gift unique).
- Removed the dedicated `Gain [amount] [stat] with a … gift` UniqueType — effect should be a normal triggerable + this condition.

## Test plan
- [ ] Civ with `Gain [200] [Gold] <upon gifting a [Great Person] unit to a City-State>`: gift GP to CS → +200 Gold; Influence gift unique still applies if present.
- [ ] Without matching filter: no Gold from this trigger.
- [ ] Military gift path unchanged.
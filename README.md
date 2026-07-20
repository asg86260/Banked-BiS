# Bank BiS

Tells you the best gear **you actually own** for the content you're about to do.

Pick an activity (a raid room, a God Wars boss, your slayer task, ...) and Bank BiS
searches everything in your bank, group storage, inventory, and worn equipment for
the highest-DPS setup per combat style, using the same combat formulas as the
OSRS Wiki DPS calculator.

## Usage

1. Enable the plugin and open the side panel (gold "BiS" icon).
2. Open your bank once so the plugin knows what you own. It keeps itself up to
   date from then on, even across client restarts.
3. Pick a category and activity, then hit **Find my best gear**.

You'll get a loadout per combat style (melee / ranged / magic), ranked by DPS
against that activity's monster, computed with your current stats and boosts.
Styles the target is immune to (e.g. melee at Zulrah) are omitted.

## What it accounts for

- Full accuracy/max-hit/DPS formulas, including gear special cases: scythe
  multi-hits, Tumeken's shadow scaling, twisted bow scaling, slayer helmet and
  salve stacking, demonbane, dragonhunter weapons, Keris, leafy immunity,
  vampyre tiers, ToB party scaling, and more.
- 2h vs. one-hand + shield trade-offs, matching ammo for bows/crossbows, and
  your best darts for the blowpipe.
- Slayer task presets assume you are on-task (slayer helmet bonuses apply).

## Current limitations

- Magic recommendations only consider powered staves (trident/sang/shadow
  etc.); autocast spell selection is not implemented yet.
- Set effects that only pay off when complete (Void, Dharok's) can be
  under-recommended.
- Items are matched by exact id, so an uncharged/broken variant of an item is
  treated as a different item than its usable form.
- Special attacks are not modeled; recommendations are sustained DPS only.

## Data sources & credits

- DPS engine adapted from [LlemonDuck/dps-calculator](https://github.com/LlemonDuck/dps-calculator)
  (BSD-2, see `LICENSE-dps-calculator`) - thank you Paul Norton.
- Equipment and monster stats from the OSRS Wiki team's
  [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) data,
  fetched at startup (cached for 24h under `.runelite/bank-bis/`).

Item ownership snapshots are stored locally per account under
`.runelite/bank-bis/` and never leave your machine.
